package uk.gov.hmrc.nonrep.attachment
package server

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

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
      config.notableEvents(apiKey).head shouldBe "vat-registration"
    }
  }
}
