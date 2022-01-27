package uk.gov.hmrc.nonrep.attachment.utils

import uk.gov.hmrc.nonrep.attachment.BaseSpec
import uk.gov.hmrc.nonrep.attachment.utils.CryptoUtils.calculateSha256

class CryptoUtilsSpec extends BaseSpec {
  "calculateSha256" should {
    "calculate the SHA 256 hash of a string" in {
      calculateSha256("vrsApiKey123".getBytes("UTF-8")) shouldBe "d862c21194db7f5d9351e585bf10461fa1fe0a95ce764e918ac1c6d8133bd922"
    }
  }
}
