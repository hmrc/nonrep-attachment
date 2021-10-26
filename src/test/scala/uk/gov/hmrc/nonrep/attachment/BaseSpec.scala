package uk.gov.hmrc.nonrep.attachment

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

trait BaseSpec extends AnyWordSpec with Matchers with DataSamples {
  val apiKey = "66975df1e55c4bb9c7dcb4313e5514c234f071b1199efd455695fefb3e54bbf2"
}
