package uk.gov.hmrc.nonrep.attachment.models

import uk.gov.hmrc.nonrep.attachment.BaseSpec

class ApiKeySpec extends BaseSpec {
  "hashedKey" should {
    "return the SHA 256 hash of a the api key" in {
      ApiKey("vrsApiKey123").hashedKey shouldBe "d862c21194db7f5d9351e585bf10461fa1fe0a95ce764e918ac1c6d8133bd922"
    }
  }
}
