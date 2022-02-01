package uk.gov.hmrc.nonrep.attachment.utils

import uk.gov.hmrc.nonrep.attachment.{BaseSpec, EitherErr, TestServices}

class CryptoUtilsSpec extends BaseSpec {
  import CryptoUtils._

  "crypto utility" should {
    "calculate sha256 for input byte array" in {
      calculateSha256("FooBar1".getBytes("utf-8")) shouldBe "26a9f5a12e997b4dcfaefa87378e2a84500991a9befc13c774466415005182ca"
      calculateSha256("Foobar1".getBytes("utf-8")) shouldBe "ee16369f2e7f155a12a83c72c237e46ca3cb5ce068a339f2889835eb08448d76"
    }
    "calculate md5 for input byte array" in {
      calculateMD5("FooBar1".getBytes("utf-8")) shouldBe "Bh6Qx4bNw0vYa35BL2G0xQ=="
      calculateMD5("Foobar1".getBytes("utf-8")) shouldBe "uxp2pOPxhWBChksOXGPeSQ=="
    }
  }
}
