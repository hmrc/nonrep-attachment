package uk.gov.hmrc.nonrep.attachment
package service

import java.util.UUID

import akka.http.javadsl.model.HttpMethods
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.nonrep.attachment.TestServices.entityToString
import uk.gov.hmrc.nonrep.attachment.models.{AttachmentRequest, AttachmentRequestKey}
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig

class IndexingSpec extends BaseSpec with ScalaFutures with ScalatestRouteTest {

  private implicit val config: ServiceConfig = new ServiceConfig()

  "For attachment api index service" should {

    "verify ES build path" in {
      Indexing.buildPath(Set("vat-registration")) shouldBe
        "/vat-registration-attachments/_search"
      //for any future attachments support
      Indexing.buildPath(Set("itsa-eops", "itsa-annual-adjustments", "itsa-crystallisation"))shouldBe
        "/itsa-eops-attachments,itsa-annual-adjustments-attachments,itsa-crystallisation-attachments/_search"
    }

    "execute ES read query" in {

    }

    "create http request" in {
      import Indexing.ops._
      import TestServices.success._

      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val request = AttachmentRequest("", attachmentId, "", "", submissionId)
      val attachmentRequest: EitherErr[AttachmentRequestKey] = Right(AttachmentRequestKey("66975df1e55c4bb9c7dcb4313e5514c234f071b1199efd455695fefb3e54bbf2", request))
      val httpRequest = attachmentRequest.query()

      httpRequest.method shouldBe HttpMethods.POST
      httpRequest.uri.toString shouldBe "/vat-registration-attachments/_search"
      whenReady(entityToString(httpRequest.entity)) { res =>
        res should include (attachmentId)
        res should include (submissionId)
      }
    }
  }
}
