package uk.gov.hmrc.nonrep.attachment
package server

import uk.gov.hmrc.nonrep.attachment.models.ApiKey

class ServiceConfigSpec extends BaseSpec {

  private val config: ServiceConfig = new ServiceConfig()

  "ServiceConfig" should {
    "specify app name" in {
      config.appName shouldBe "attachment"
    }
    "specify environment" in {
      config.env should not be empty
    }
    "be able to use default service port" in {
      config.port shouldBe config.servicePort
    }
    "build config object for notable events" in {
      config.notableEvents.isEmpty shouldBe false
      config.notableEvents(apiKey.hashedKey).head shouldBe "vat-registration"
    }
  }

  "maybeNotableEvent" should {
    "return a notable event" when {
      "a matching api key is supplied" in {
        config.maybeNotableEvents(apiKey) shouldBe Some(Set("vat-registration"))
      }
    }

    "return None" when {
      "a matching api key is not supplied" in {
        config.maybeNotableEvents(ApiKey("oops")) shouldBe None
      }
    }
  }
}
