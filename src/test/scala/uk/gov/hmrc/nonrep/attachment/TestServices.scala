package uk.gov.hmrc.nonrep.attachment

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import uk.gov.hmrc.nonrep.attachment.models.AttachmentRequestKey
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig
import uk.gov.hmrc.nonrep.attachment.service.Indexing
import uk.gov.hmrc.nonrep.attachment.stream.AttachmentFlow

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object TestServices {
  lazy val testKit: ActorTestKit = ActorTestKit()
  implicit def typedSystem: ActorSystem[Nothing] = testKit.system
  implicit val config: ServiceConfig = new ServiceConfig()

  def entityToString(entity: ResponseEntity)(implicit ec: ExecutionContext): Future[String] = {
    implicit val typedSystem: ActorSystem[Nothing] = testKit.system

    entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)
  }

  object success {
    implicit val queryForAttachments: Indexing[AttachmentRequestKey] = new Indexing[AttachmentRequestKey]() {
      override def query(data: EitherErr[AttachmentRequestKey])(implicit config: ServiceConfig): HttpRequest =
        data.toOption.map { value =>
          val path = Indexing.buildPath(config.notableEvents(value.apiKey))
          val body = s"""{"query": {"bool":{"must":[{"match":{"attachmentIds":"${value.request.attachmentId}"}},{"ids":{"values":"${value.request.nrSubmissionId}"}}]}}}"""
          HttpRequest(HttpMethods.POST, Uri(path), Nil, HttpEntity(ContentTypes.`application/json`, body))
        }.getOrElse(HttpRequest())

      override def run()(implicit system: ActorSystem[_], config: ServiceConfig)
      : Flow[(HttpRequest, EitherErr[AttachmentRequestKey]), (Try[HttpResponse], EitherErr[AttachmentRequestKey]), Any] =
        Flow[(HttpRequest, EitherErr[AttachmentRequestKey])].map {
          case (_, request) => (Try(HttpResponse(StatusCodes.OK)), request)
        }

      override def parse(value: EitherErr[AttachmentRequestKey], response: HttpResponse)(implicit system: ActorSystem[_]): Future[EitherErr[AttachmentRequestKey]] =
        Future.successful(value)
    }
    val flow: AttachmentFlow = new AttachmentFlow() {}
  }

  object failure {
    implicit val queryForAttachments: Indexing[AttachmentRequestKey] = new Indexing[AttachmentRequestKey]() {
      override def query(data: EitherErr[AttachmentRequestKey])(implicit config: ServiceConfig): HttpRequest =
        data.toOption.map { value =>
          val path = Indexing.buildPath(config.notableEvents(value.apiKey))
          val body = s"""{"query": {"bool":{"must":[{"match":{"attachmentIds":"${value.request.attachmentId}"}},{"ids":{"values":"${value.request.nrSubmissionId}"}}]}}}"""
          HttpRequest(HttpMethods.POST, Uri(path), Nil, HttpEntity(ContentTypes.`application/json`, body))
        }.getOrElse(HttpRequest())

      override def run()(implicit system: ActorSystem[_], config: ServiceConfig)
      : Flow[(HttpRequest, EitherErr[AttachmentRequestKey]), (Try[HttpResponse], EitherErr[AttachmentRequestKey]), Any] =
        Flow[(HttpRequest, EitherErr[AttachmentRequestKey])].map {
          case (_, request) => (Try(HttpResponse(StatusCodes.InternalServerError)), request)
        }

      override def parse(value: EitherErr[AttachmentRequestKey], response: HttpResponse)(implicit system: ActorSystem[_]): Future[EitherErr[AttachmentRequestKey]] =
        Future.successful(Left(ErrorMessage("Invalid nrSubmissionId")))
    }
    val flow: AttachmentFlow = new AttachmentFlow() {}
  }
}