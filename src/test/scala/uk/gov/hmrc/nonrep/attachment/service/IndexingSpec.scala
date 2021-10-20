package uk.gov.hmrc.nonrep.attachment
package service

import java.util.UUID

import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.nonrep.attachment.models.{AttachmentRequest, AttachmentRequestKey}
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig

class IndexingSpec extends BaseSpec with ScalaFutures {

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
      import TestServices._
      import Indexing.ops._

      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val req = AttachmentRequest("", attachmentId, "", "", "")
      val areq: EitherErr[AttachmentRequestKey] = Right(AttachmentRequestKey("66975df1e55c4bb9c7dcb4313e5514c234f071b1199efd455695fefb3e54bbf2", req))
      val hreq = areq.query()

      //hreq - method POST
      //path - vat-registration-attachments
      //entity.body - query should include attachmentId and submissionId
    }
  }
}
