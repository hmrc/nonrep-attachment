package uk.gov.hmrc.nonrep.attachment

import java.io.File
import java.nio.file.Files

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes.{InternalServerError, OK}
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import uk.gov.hmrc.nonrep.attachment.models.AttachmentRequestKey
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig
import uk.gov.hmrc.nonrep.attachment.service.{Indexing, Storage}
import uk.gov.hmrc.nonrep.attachment.stream.AttachmentFlow

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object TestServices extends TestConfigUtils {
  lazy val testKit: ActorTestKit = ActorTestKit()

  implicit def typedSystem: ActorSystem[Nothing] = testKit.system

  implicit val config: ServiceConfig = new ServiceConfig()

  def entityToString(entity: ResponseEntity)(implicit ec: ExecutionContext): Future[String] = {
    implicit val typedSystem: ActorSystem[Nothing] = testKit.system

    entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)
  }

  val sampleAttachment: Array[Byte] =
    Files.readAllBytes(new File(getClass.getClassLoader.getResource("0f0d6508-7f9f-11ec-b1fb-a732847931b5").getFile).toPath)
  val sampleAttachmentBundle: Array[Byte] =
    Files.readAllBytes(new File(getClass.getClassLoader.getResource("0f0d6508-7f9f-11ec-b1fb-a732847931b5.zip").getFile).toPath)

  object success {

    implicit val successfulStorage: Storage[AttachmentRequestKey] = new Storage[AttachmentRequestKey]() {
      override def call()(implicit system: ActorSystem[_], config: ServiceConfig)
        : Flow[(HttpRequest, EitherErr[AttachmentRequestKey]), (Try[HttpResponse], EitherErr[AttachmentRequestKey]), Any] =
        Flow[(HttpRequest, EitherErr[AttachmentRequestKey])].map {
          case (_, request) => (Try(HttpResponse(OK)), request)
        }

      override def request(data: EitherErr[AttachmentRequestKey])(implicit config: ServiceConfig, system: ActorSystem[_]): HttpRequest =
        HttpRequest()

      override def response(request: EitherErr[AttachmentRequestKey], response: HttpResponse)(
        implicit system: ActorSystem[_]): Future[EitherErr[(AttachmentRequestKey, ByteString)]] =
        Future.successful(request.map((_, ByteString(sampleAttachment))))

      override def uploadBundle(attachment: AttachmentRequestKey, file: ByteString)(
        implicit system: ActorSystem[_],
        config: ServiceConfig): Future[EitherErr[AttachmentRequestKey]] = Future.successful(Right(attachment))

      override def createBundle(data: AttachmentRequestKey, file: ByteString)(
        implicit system: ActorSystem[_],
        config: ServiceConfig): ByteString = Storage.defaultStorageService.createBundle(data, file)(system, config)
    }

    implicit val successfulIndexing: Indexing[AttachmentRequestKey] = new Indexing[AttachmentRequestKey]() {
      override def request(data: EitherErr[AttachmentRequestKey])(implicit config: ServiceConfig, system: ActorSystem[_]): HttpRequest =
        data.toOption
          .map { value =>
            val path = Indexing.buildPath(notableEventsOrEmpty(config, value.apiKey))
            val body =
              s"""{"query": {"bool":{"must":[{"match":{"attachmentIds":"${value.request.attachmentId}"}},{"ids":{"values":"${value.request.nrSubmissionId}"}}]}}}"""
            HttpRequest(HttpMethods.POST, Uri(path), Nil, HttpEntity(ContentTypes.`application/json`, body))
          }
          .getOrElse(HttpRequest())

      override def call()(implicit system: ActorSystem[_], config: ServiceConfig)
        : Flow[(HttpRequest, EitherErr[AttachmentRequestKey]), (Try[HttpResponse], EitherErr[AttachmentRequestKey]), Any] =
        Flow[(HttpRequest, EitherErr[AttachmentRequestKey])].map {
          case (_, request) => (Try(HttpResponse(OK)), request)
        }

      override def response(value: EitherErr[AttachmentRequestKey], response: HttpResponse)(
        implicit system: ActorSystem[_]): Future[EitherErr[(AttachmentRequestKey, ByteString)]] =
        Future.successful(value.map((_, ByteString.empty)))
    }
    val flow: AttachmentFlow = new AttachmentFlow() {}
  }

  object failure {
    implicit val failingStorage: Storage[AttachmentRequestKey] = new Storage[AttachmentRequestKey]() {
      override def call()(implicit system: ActorSystem[_], config: ServiceConfig)
        : Flow[(HttpRequest, EitherErr[AttachmentRequestKey]), (Try[HttpResponse], EitherErr[AttachmentRequestKey]), Any] =
        Flow[(HttpRequest, EitherErr[AttachmentRequestKey])].map {
          case (_, request) => (Try(HttpResponse(InternalServerError)), request)
        }

      override def request(data: EitherErr[AttachmentRequestKey])(implicit config: ServiceConfig, system: ActorSystem[_]): HttpRequest =
        HttpRequest()

      override def response(request: EitherErr[AttachmentRequestKey], response: HttpResponse)(
        implicit system: ActorSystem[_]): Future[EitherErr[(AttachmentRequestKey, ByteString)]] =
        Future.successful(Left(ErrorMessage("S3 download error")))

      override def uploadBundle(attachment: AttachmentRequestKey, file: ByteString)(
        implicit system: ActorSystem[_],
        config: ServiceConfig): Future[EitherErr[AttachmentRequestKey]] =
        Future.successful(Left(ErrorMessage("S3 upload error", InternalServerError)))

      override def createBundle(data: AttachmentRequestKey, file: ByteString)(
        implicit system: ActorSystem[_],
        config: ServiceConfig): ByteString =
        ByteString(sampleAttachmentBundle)
    }

    implicit val indexingWithUpstreamFailureAndParsingError: Indexing[AttachmentRequestKey] = new Indexing[AttachmentRequestKey]() {
      override def request(data: EitherErr[AttachmentRequestKey])(implicit config: ServiceConfig, system: ActorSystem[_]): HttpRequest =
        data.toOption
          .map { value =>
            val path = Indexing.buildPath(notableEventsOrEmpty(config, value.apiKey))
            val body =
              s"""{"query": {"bool":{"must":[{"match":{"attachmentIds":"${value.request.attachmentId}"}},{"ids":{"values":"${value.request.nrSubmissionId}"}}]}}}"""
            HttpRequest(HttpMethods.POST, Uri(path), Nil, HttpEntity(ContentTypes.`application/json`, body))
          }
          .getOrElse(HttpRequest())

      override def call()(implicit system: ActorSystem[_], config: ServiceConfig)
        : Flow[(HttpRequest, EitherErr[AttachmentRequestKey]), (Try[HttpResponse], EitherErr[AttachmentRequestKey]), Any] =
        Flow[(HttpRequest, EitherErr[AttachmentRequestKey])].map {
          case (_, request) => (Try(HttpResponse(InternalServerError)), request)
        }

      override def response(value: EitherErr[AttachmentRequestKey], response: HttpResponse)(
        implicit system: ActorSystem[_]): Future[EitherErr[(AttachmentRequestKey, ByteString)]] =
        Future.successful(Left(ErrorMessage("nrSubmissionId validation error")))
    }
    val flow: AttachmentFlow = new AttachmentFlow() {}
  }

}
