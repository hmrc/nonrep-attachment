package uk.gov.hmrc.nonrep.attachment
package service

import java.io.ByteArrayInputStream
import java.net.URI

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.scaladsl.Flow
import org.apache.http.client.utils.URIBuilder
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.signer.{Aws4Signer, AwsSignerExecutionAttribute}
import software.amazon.awssdk.core.interceptor.ExecutionAttributes
import software.amazon.awssdk.http.{SdkHttpFullRequest, SdkHttpMethod}
import software.amazon.awssdk.regions.Region
import uk.gov.hmrc.nonrep.attachment.models.AttachmentRequestKey
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig
import scala.util.Try

trait Indexing[A] {
  def query(data: EitherErr[A])(implicit config: ServiceConfig): HttpRequest

  def flow()(implicit system: ActorSystem[_], config: ServiceConfig)
  : Flow[(HttpRequest, EitherErr[A]), (Try[HttpResponse], EitherErr[A]), Any]

  def response:Unit =  ???
}

object Indexing {
  def apply[A](implicit service: Indexing[A]): Indexing[A] = service

  object ops {

    implicit class IndexingOps[A: Indexing](value: EitherErr[A]) {
      def query()(implicit config: ServiceConfig): HttpRequest = Indexing[A].query(value)

      def flow()(implicit system: ActorSystem[_], config: ServiceConfig)
      : Flow[(HttpRequest, EitherErr[A]), (Try[HttpResponse], EitherErr[A]), Any] =
        Indexing[A].flow()
    }

  }

  implicit val defaultQueryForAttachments: Indexing[AttachmentRequestKey] = new Indexing[AttachmentRequestKey]() {

    override def flow()(implicit system: ActorSystem[_], config: ServiceConfig):
    Flow[(HttpRequest, EitherErr[AttachmentRequestKey]), (Try[HttpResponse], EitherErr[AttachmentRequestKey]), Http.HostConnectionPool] =
      if (config.isElasticSearchProtocolSecure)
        Http().cachedHostConnectionPoolHttps[EitherErr[AttachmentRequestKey]](config.elasticSearchHost)
      else
        Http().cachedHostConnectionPool[EitherErr[AttachmentRequestKey]](config.elasticSearchHost)

    override def query(data: EitherErr[AttachmentRequestKey])(implicit config: ServiceConfig): HttpRequest = {
      data
        .toOption
        .flatMap(value => config.notableEvents.get(value.apiKey).map(notableEvents => (value, notableEvents)))
        .map {
          case (value, notableEvents) => {
            val path = buildPath(notableEvents)
            val body = s"""{"query": {"bool":{"must":[{"match":{"attachmentIds":"${value.request.attachmentId}"}},{"ids":{"values":"${value.request.nrSubmissionId}"}}]}}}"""
            createSignedRequest(HttpMethods.POST, config.elasticSearchUri, path, body)
          }
        }.getOrElse(HttpRequest())
    }
  }

  private lazy val signer = Aws4Signer.create()

  def buildPath(notableEvent: Set[String]) = s"/${notableEvent.map(_.concat("-attachments")).mkString(",")}/_search"

  def createSignedRequest: HttpRequest => HttpRequest = ???

  private[service] def createSignedRequest(method: HttpMethod, uri: URI, path: String, body: String): HttpRequest = {

    import scala.jdk.CollectionConverters._

    val attributes = new ExecutionAttributes()
    attributes.putAttribute(AwsSignerExecutionAttribute.SERVICE_SIGNING_NAME, "es")
    attributes.putAttribute(AwsSignerExecutionAttribute.SIGNING_REGION, Region.of("eu-west-2"))
    attributes.putAttribute(AwsSignerExecutionAttribute.AWS_CREDENTIALS, DefaultCredentialsProvider.create().resolveCredentials)
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