package uk.gov.hmrc.nonrep.attachment
package service

import org.scalatest.concurrent.ScalaFutures

class IndexingSpec extends BaseSpec with ScalaFutures {

  "For attachment api index service" should {

    "verify ES build path" in {
      Indexing.buildPath(Set("vat-registration")) shouldBe
        "/vat-registration-attachments/"
      //for any future attachments support
      Indexing.buildPath(Set("itsa-eops", "itsa-annual-adjustments", "itsa-crystallisation"))shouldBe
        "/itsa-eops-attachments,itsa-annual-adjustments-attachments,itsa-crystallisation-attachments/"
    }

    "execute ES read query" in {

    }

    "create http request" in {

    }
  }
}
