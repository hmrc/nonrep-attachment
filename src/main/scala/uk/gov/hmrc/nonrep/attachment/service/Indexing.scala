package uk.gov.hmrc.nonrep.attachment
package service

import java.io.ByteArrayInputStream
import java.net.URI

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import org.apache.http.client.utils.URIBuilder
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProvider, DefaultCredentialsProvider}
import software.amazon.awssdk.auth.signer.{Aws4Signer, AwsSignerExecutionAttribute}
import software.amazon.awssdk.core.interceptor.ExecutionAttributes
import software.amazon.awssdk.http.{SdkHttpFullRequest, SdkHttpMethod}
import software.amazon.awssdk.regions.Region
import spray.json._
import uk.gov.hmrc.nonrep.attachment.models.{AttachmentRequestKey, SearchResponse}
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig
import uk.gov.hmrc.nonrep.attachment.utils.JsonFormats._

import scala.concurrent.Future
import scala.util.Try

trait Request[A] {
  def query(data: EitherErr[A])(implicit config: ServiceConfig, system: ActorSystem[_]): HttpRequest
}

trait Call[A] {
  def run()(
    implicit system: ActorSystem[_],
    config: ServiceConfig): Flow[(HttpRequest, EitherErr[A]), (Try[HttpResponse], EitherErr[A]), Any]
}

trait Response[A] {
  def parse(value: EitherErr[A], response: HttpResponse)(implicit system: ActorSystem[_]): Future[EitherErr[A]]
}

trait Indexing[A] extends Request[A] with Call[A] with Response[A] {}

object Indexing {
  object Request {
    def apply[A](implicit service: Request[A]): Request[A] = service
  }

  object Call {
    def apply[A](implicit service: Call[A]): Call[A] = service
  }

  object Response {
    def apply[A](implicit service: Response[A]): Response[A] = service
  }

  object ops {

    implicit class RequestOps[A: Request](value: EitherErr[A]) {
      def query()(implicit config: ServiceConfig, system: ActorSystem[_]): HttpRequest = Request[A].query(value)
    }

    implicit class CallOps[A: Call](value: EitherErr[A]) {
      def flow()(
        implicit system: ActorSystem[_],
        config: ServiceConfig): Flow[(HttpRequest, EitherErr[A]), (Try[HttpResponse], EitherErr[A]), Any] = Call[A].run()
    }

    implicit class ResponseOps[A: Response](value: EitherErr[A]) {
      def parse(response: HttpResponse)(implicit system: ActorSystem[_]): Future[EitherErr[A]] =
        Response[A].parse(value, response)
    }

  }

  implicit val defaultIndexingService: Indexing[AttachmentRequestKey] = new Indexing[AttachmentRequestKey] {
    override def run()(implicit system: ActorSystem[_], config: ServiceConfig)
      : Flow[(HttpRequest, EitherErr[AttachmentRequestKey]), (Try[HttpResponse], EitherErr[AttachmentRequestKey]), Any] =
      if (config.isElasticSearchProtocolSecure)
        Http().cachedHostConnectionPoolHttps[EitherErr[AttachmentRequestKey]](config.elasticSearchHost)
      else
        Http().cachedHostConnectionPool[EitherErr[AttachmentRequestKey]](config.elasticSearchHost)

    override def query(data: EitherErr[AttachmentRequestKey])(implicit config: ServiceConfig, system: ActorSystem[_]): HttpRequest =
      data.toOption
        .flatMap(value => config.maybeNotableEvents(value.apiKey).map(notableEvents => (value, notableEvents)))
        .map {
          case (attachmentRequestKey: AttachmentRequestKey, notableEvents) =>
            import RequestsSigner._

            val path = buildPath(notableEvents)
            val body =
              s"""{"query": {"bool":{"must":[{"match":{"attachmentIds.keyword":"${attachmentRequestKey.request.attachmentId}"}},{"ids":{"values":"${attachmentRequestKey.request.nrSubmissionId}"}}]}}}"""
            val request = createSignedRequest(HttpMethods.POST, config.elasticSearchUri, path, body)

            system.log.info(
              s"query for attachmentRequestKey: [$attachmentRequestKey] and notableEvents: [$notableEvents] produced path: [$path], body: [$body] and request: [$request]")

            request
        }
        .getOrElse(HttpRequest())

    override def parse(value: EitherErr[AttachmentRequestKey], response: HttpResponse)(
      implicit system: ActorSystem[_]): Future[EitherErr[AttachmentRequestKey]] =
      if (response.status == StatusCodes.OK) {
        import system.executionContext
        response.entity.dataBytes
          .runFold(ByteString.empty)(_ ++ _)
          .map(_.utf8String)
          .map(_.parseJson)
          .map(_.convertTo[SearchResponse])
          .map(Right(_).withLeft[ErrorMessage])
          .map(_.filterOrElse(_.hits.total == 1, ErrorMessage("Invalid nrSubmissionId")).flatMap(_ => value))
      } else {
        system.log.error(s"Response status ${response.status} received rom ES server for request: [$value]. Full response is: [$response]")

        val error = ErrorMessage(s"Response status ${response.status} from ES server", StatusCodes.InternalServerError)

        response.discardEntityBytes()
        Future.successful(Left(error))
      }
  }

  def buildPath(notableEvent: Set[String]) = s"/${notableEvent.map(_.concat("-attachments")).mkString(",")}/_search"
}

object RequestsSigner {
  private lazy val signer = Aws4Signer.create()

  def createSignedRequest(
    method: HttpMethod,
    uri: URI,
    path: String,
    body: String,
    credsProvider: AwsCredentialsProvider = DefaultCredentialsProvider.create()): HttpRequest = {

    import scala.jdk.CollectionConverters._

    val attributes = new ExecutionAttributes()
    attributes.putAttribute(AwsSignerExecutionAttribute.SERVICE_SIGNING_NAME, "es")
    attributes.putAttribute(AwsSignerExecutionAttribute.SIGNING_REGION, Region.of("eu-west-2"))
    attributes.putAttribute(AwsSignerExecutionAttribute.AWS_CREDENTIALS, credsProvider.resolveCredentials)
    val uriBuilder = new URIBuilder(path)
    val httpMethod = SdkHttpMethod.fromValue(method.value)
    val builder = SdkHttpFullRequest
      .builder()
      .uri(uri)
      .encodedPath(uriBuilder.build().getRawPath)
      .method(httpMethod)

    uriBuilder.getQueryParams.asScala.foreach(param => builder.putRawQueryParameter(param.getName, param.getValue))

    val request = HttpRequest(method, path)
    request.headers.foreach(header => builder.putHeader(header.name(), header.value()))
    builder.contentStreamProvider(() => new ByteArrayInputStream(body.getBytes))

    val signedRequest = signer.sign(builder.build(), attributes)

    val headers = signedRequest.headers.asScala.map {
      case (name, values) => RawHeader(name, values.asScala.mkString(","))
    }.toList

    val is = signedRequest.contentStreamProvider().orElseGet(() => () => new ByteArrayInputStream(Array[Byte]())).newStream()
    request.withHeadersAndEntity(headers, HttpEntity(ContentTypes.`application/json`, scala.io.Source.fromInputStream(is).mkString))

  }
}
