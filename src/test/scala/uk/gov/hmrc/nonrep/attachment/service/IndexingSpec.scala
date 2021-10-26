package uk.gov.hmrc.nonrep.attachment
package service

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.http.javadsl.model.HttpMethods
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.{Keep, Sink, Source}
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.nonrep.attachment.TestServices.entityToString
import uk.gov.hmrc.nonrep.attachment.models.{AttachmentRequest, AttachmentRequestKey}
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig


class IndexingSpec extends BaseSpec with ScalaFutures with ScalatestRouteTest {

  private implicit val config: ServiceConfig = new ServiceConfig()
  private lazy val testKit = ActorTestKit()
  private implicit val typedSystem: ActorSystem[Nothing] = testKit.system

  "For attachment api index service" should {

    "verify ES build path" in {
      Indexing.buildPath(Set("vat-registration")) shouldBe "/vat-registration-attachments/_search"
      //for any future attachments support
      Indexing.buildPath(Set("itsa-eops", "itsa-annual-adjustments", "itsa-crystallisation")) shouldBe
        "/itsa-eops-attachments,itsa-annual-adjustments-attachments,itsa-crystallisation-attachments/_search"
    }

    "execute ES read query" in {
      import Indexing.ops._
      import TestServices.success._
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val request = AttachmentRequest("", attachmentId, "", "", submissionId)
      val attachmentRequest: EitherErr[AttachmentRequestKey] = Right(AttachmentRequestKey(apiKey, request))
      val httpRequest = attachmentRequest.query()
      val esValidation = attachmentRequest.flow()
      whenReady(Source.single((httpRequest, attachmentRequest)).via(esValidation).toMat(Sink.head)(Keep.right).run()) {
        case (tryResponse, entity) => {
          tryResponse.isSuccess shouldBe true
          entity.toOption.get.request.attachmentId shouldBe attachmentId
          entity.toOption.get.request.nrSubmissionId shouldBe submissionId
        }
      }
    }

    "fail on ES upstream failure" in {
      import Indexing.ops._
      import TestServices.failure._
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val request = AttachmentRequest("", attachmentId, "", "", submissionId)
      val attachmentRequest: EitherErr[AttachmentRequestKey] = Right(AttachmentRequestKey(apiKey, request))
      val httpRequest = attachmentRequest.query()
      val esValidation = attachmentRequest.flow()
      whenReady(Source.single((httpRequest, attachmentRequest)).via(esValidation).toMat(Sink.head)(Keep.right).run()) {
        case (tryResponse, entity) => {
          tryResponse.isSuccess shouldBe true
          tryResponse.get.status shouldBe StatusCodes.InternalServerError
        }
      }
    }

    "create http request" in {
      import Indexing.ops._
      import TestServices.success._

      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val request = AttachmentRequest("", attachmentId, "", "", submissionId)
      val attachmentRequest: EitherErr[AttachmentRequestKey] = Right(AttachmentRequestKey(apiKey, request))
      val httpRequest = attachmentRequest.query()

      httpRequest.method shouldBe HttpMethods.POST
      httpRequest.uri.toString shouldBe "/vat-registration-attachments/_search"
      whenReady(entityToString(httpRequest.entity)) { res =>
        res should include(attachmentId)
        res should include(submissionId)
      }
    }
  }
}
