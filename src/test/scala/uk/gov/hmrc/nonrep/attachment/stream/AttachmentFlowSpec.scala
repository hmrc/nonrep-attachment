package uk.gov.hmrc.nonrep.attachment
package stream

import java.util.UUID

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import org.scalatest.concurrent.ScalaFutures
import spray.json._
import uk.gov.hmrc.nonrep.attachment.TestServices.testKit
import uk.gov.hmrc.nonrep.attachment.models.{AttachmentRequest, AttachmentRequestKey, IncomingRequest}
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig

import scala.util.Try

class AttachmentFlowSpec extends BaseSpec with ScalaFutures with ScalatestRouteTest {

  private implicit val typedSystem: ActorSystem[Nothing] = testKit.system

  "attachments flow" should {
    import TestServices._
    import TestServices.success._

    "validate incoming request" in {
      val source = TestSource.probe[IncomingRequest]
      val sink = TestSink.probe[EitherErr[AttachmentRequestKey]]
      val (pub, sub) = source.via(flow.validateRequest).toMat(sink)(Keep.both).run()
      pub
        .sendNext(IncomingRequest(apiKey, validAttachmentRequestJson().parseJson))
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()
      result.isRight shouldBe true
    }

    "create ES request" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[EitherErr[AttachmentRequestKey]]
      val sink = TestSink.probe[(HttpRequest, EitherErr[AttachmentRequestKey])]
      val (pub, sub) = source.via(flow.createEsRequest).toMat(sink)(Keep.both).run()
      pub
        .sendNext(Right(AttachmentRequestKey(apiKey, validAttachmentRequest(attachmentId, submissionId))))
        .sendComplete()
      val (httpRequest, entity) = sub
        .request(1)
        .expectNext()

      entity.isRight shouldBe true
      httpRequest.method shouldBe HttpMethods.POST
      httpRequest.uri.toString shouldBe "/vat-registration-attachments/_search"
      whenReady(entityToString(httpRequest.entity)) { res =>
        res should include(attachmentId)
        res should include(submissionId)
      }
    }

    "parse ES response" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[(Try[HttpResponse], EitherErr[AttachmentRequestKey])]
      val sink = TestSink.probe[EitherErr[AttachmentRequestKey]]
      val (pub, sub) = source.via(flow.parseEsResponse).toMat(sink)(Keep.both).run()
      pub
        .sendNext((Try(HttpResponse()), Right(AttachmentRequestKey(apiKey, validAttachmentRequest(attachmentId, submissionId)))))
        .sendComplete()
      val response = sub
        .request(1)
        .expectNext()
      response.isRight shouldBe true
    }

    "remap AttachmentRequestKey case class into AttachmentRequest" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[EitherErr[AttachmentRequestKey]]
      val sink = TestSink.probe[EitherErr[AttachmentRequest]]
      val (pub, sub) = source.via(flow.remapAttachmentRequestKey).toMat(sink)(Keep.both).run()
      pub
        .sendNext(Right(AttachmentRequestKey(apiKey, validAttachmentRequest(attachmentId, submissionId))))
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()

      result.isRight shouldBe true
      result.toOption.get.attachmentId shouldBe attachmentId
      result.toOption.get.nrSubmissionId shouldBe submissionId
    }

    "validate attachments flow" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[IncomingRequest]
      val sink = TestSink.probe[Either[ErrorMessage, AttachmentRequest]]
      val (pub, sub) = source.via(flow.validationFlow).toMat(sink)(Keep.both).run()
      pub
        .sendNext(IncomingRequest(apiKey, validAttachmentRequestJson(attachmentId, submissionId).parseJson))
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()

      result.isRight shouldBe true
      result.toOption.get.attachmentId shouldBe attachmentId
      result.toOption.get.nrSubmissionId shouldBe submissionId
    }

  }

  "for negative scenario" should {
    import TestServices._
    import TestServices.failure._

    "fail on ES upstream failure" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[IncomingRequest]
      val sink = TestSink.probe[Either[ErrorMessage, AttachmentRequest]]
      val (pub, sub) = source.via(flow.validationFlow).toMat(sink)(Keep.both).run()
      pub
        .sendNext(IncomingRequest(apiKey, validAttachmentRequestJson(attachmentId, submissionId).parseJson))
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()

      result.isLeft shouldBe true
      result.left.map { error =>
        error.message should include("nrSubmissionId validation error")
      }
    }
  }
}
