package uk.gov.hmrc.nonrep.attachment
package service

import org.scalatest.concurrent.ScalaFutures

class IndexingSpec extends BaseSpec with ScalaFutures {

  "For attachment api index service" should {

    "verify ES build path" in {
      Indexing.buildPath(Set("vat-registration"), SubmissionMetadata("")) shouldBe
        "/vat-registration-attachments/"
      Indexing.buildPath(Set("itsa-eops", "itsa-annual-adjustments", "itsa-crystallisation"), SubmissionMetadata(""))shouldBe
        "/itsa-eops-attachments,itsa-annual-adjustments-attachments,itsa-crystallisation-attachments/"
      Indexing.buildPath(Set("entry-declaration"), SubmissionMetadata("")) shouldBe
        "/entry-declaration-attachments/"
    }

    "execute ES read query" in {

    }

  }
}
