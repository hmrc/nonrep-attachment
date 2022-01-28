package uk.gov.hmrc.nonrep.attachment

import akka.http.scaladsl.model.headers.RawHeader
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.nonrep.attachment.models.ApiKey

trait BaseSpec extends AnyWordSpec with Matchers with DataSamples {
  val apiKey: ApiKey = ApiKey("vrsApiKey123")

  val apiKeyHeader: RawHeader = RawHeader("x-api-Key", apiKey.key)
}
