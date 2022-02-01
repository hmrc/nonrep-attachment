package uk.gov.hmrc.nonrep.attachment.service

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.{Keep, Sink, Source}
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.nonrep.attachment.models.{AttachmentRequest, AttachmentRequestKey}
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig
import uk.gov.hmrc.nonrep.attachment.{BaseSpec, EitherErr, TestServices}

class StorageSpec extends BaseSpec with ScalaFutures with ScalatestRouteTest {

  private implicit val config: ServiceConfig = new ServiceConfig()
  private lazy val testKit = ActorTestKit()
  private implicit val typedSystem: ActorSystem[Nothing] = testKit.system

  "For attachment api storage service" should {

    "make communication with S3 via request-call-response pattern" in {
      val storage = TestServices.success.successfulStorage
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val request = AttachmentRequest("", attachmentId, "", "", submissionId)
      val attachmentRequest: EitherErr[AttachmentRequestKey] = Right(AttachmentRequestKey(apiKey, request))
      val httpRequest = storage.request(attachmentRequest)
      val s3Call = storage.call()
      whenReady(Source.single((httpRequest, attachmentRequest)).via(s3Call).toMat(Sink.head)(Keep.right).run()) {
        case (tryResponse, entity) =>
          tryResponse.isSuccess shouldBe true
          entity.toOption.get.request.attachmentId shouldBe attachmentId
          entity.toOption.get.request.nrSubmissionId shouldBe submissionId
          storage.response(attachmentRequest, tryResponse.get).futureValue.isRight shouldBe true
      }
    }

    "fail on S3 upstream failure" in {
      val storage = TestServices.failure.failingStorage
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val request = AttachmentRequest("", attachmentId, "", "", submissionId)
      val attachmentRequest: EitherErr[AttachmentRequestKey] = Right(AttachmentRequestKey(apiKey, request))
      val httpRequest = storage.request(attachmentRequest)
      val s3Call = storage.call()
      whenReady(Source.single((httpRequest, attachmentRequest)).via(s3Call).toMat(Sink.head)(Keep.right).run()) {
        case (tryResponse, _) =>
          tryResponse.isSuccess shouldBe true
          tryResponse.get.status shouldBe StatusCodes.InternalServerError
      }
    }
  }
}
