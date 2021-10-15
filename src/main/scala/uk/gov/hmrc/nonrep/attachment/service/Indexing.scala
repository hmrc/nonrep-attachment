package uk.gov.hmrc.nonrep.attachment
package service

import java.io.ByteArrayInputStream
import java.net.URI

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpEntity.LastChunk.data
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.scaladsl.{Flow, Source}
import org.apache.http.client.utils.URIBuilder
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.signer.{Aws4Signer, AwsSignerExecutionAttribute}
import software.amazon.awssdk.core.interceptor.ExecutionAttributes
import software.amazon.awssdk.http.{SdkHttpFullRequest, SdkHttpMethod}
import software.amazon.awssdk.regions.Region
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig

import scala.util.Try

trait Indexing[A] {
  def query(data: A): Source[A, NotUsed]

  def flow()(implicit system: ActorSystem, config: ServiceConfig)
  :Flow[(HttpRequest, EitherErr[A]), (Try[HttpResponse], EitherErr[A]), Any]
}
object Indexing {
  def apply[A](implicit service: Indexing[A]): Indexing[A] = service

  object ops {

    implicit class IndexingOps[A: Indexing](value: EitherErr[A]) {
      def query()(implicit config: ServiceConfig): HttpRequest = Indexing[A].query(data: A): Source[A, NotUsed]

      def flow()(implicit system: ActorSystem, config: ServiceConfig)
      : Flow[(HttpRequest, EitherErr[A]), (Try[HttpResponse], EitherErr[A]), Any] =
        Indexing[A].flow()
  }
}
  //not sure if this will be needed and how we connect it
    implicit val defaultIndexing: Indexing[SubmissionMetadata] = new Indexing[SubmissionMetadata]() {
      override def query(value: EitherErr[SubmissionMetadata])(implicit config: ServiceConfig): HttpRequest = {
        value.toOption.flatMap {
          submission => {
            val metadata = submission.request.metadata
            metadata.nrSubmissionId.zip(metadata.attachmentIds).map {
              case (nrSubmissionId, attachmentIds) =>
                val path = buildPath(metadata.notableEvent, nrSubmissionId)
                val body = s"""{ "attachmentIds": ${attachmentIds.map(id => s"\"$id\"").mkString("[", ",", "]")} }"""
                createSignedRequest(HttpMethods.POST, config.elasticSearchUri, path, body)
            }
          }
        }.getOrElse(HttpRequest())
      }
      override def flow()(implicit system: ActorSystem, config: ServiceConfig):
      Flow[(HttpRequest, EitherErr[SubmissionMetadata]), (Try[HttpResponse], EitherErr[SubmissionMetadata]), Http.HostConnectionPool] =
        if (config.isElasticSearchProtocolSecure)
          Http().cachedHostConnectionPoolHttps[EitherErr[SubmissionMetadata]](config.elasticSearchHost)
        else
          Http().cachedHostConnectionPool[EitherErr[SubmissionMetadata]](config.elasticSearchHost)
    }

  private lazy val signer = Aws4Signer.create()

  private[submission] def buildPath(notableEvent: String, nrSubmissionId: String) = s"/$notableEvent-attachments/index/$nrSubmissionId"

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