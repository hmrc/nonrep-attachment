package uk.gov.hmrc.nonrep.attachment
package service

import org.scalatest.concurrent.ScalaFutures

class IndexingSpec extends BaseSpec with ScalaFutures {

  "For attachment api index service" should {

    "verify ES build path" in {
      Indexing.buildPath(Set("vat-registration"), SubmissionMetadata("")) shouldBe "/vat-registration-attachments/"
    }

    "execute ES read query" in {

    }

  }
}
