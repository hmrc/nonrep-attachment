package uk.gov.hmrc.nonrep.attachment
package service

import akka.http.scaladsl.model.HttpMethods
import org.scalatest.concurrent.ScalaFutures
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}

import scala.concurrent.ExecutionContext.Implicits.global

class RequestsSignerSpec extends BaseSpec with ScalaFutures {

  "request signer" should {
    import Indexing._
    import RequestsSigner._
    import TestServices._

    "create signed http request" in {
      val creds = StaticCredentialsProvider.create(AwsBasicCredentials.create("ASIAXXX", "xxx"))
      val path = buildPath(notableEventsOrEmpty(config, apiKey))
      val body = """{"query": {"match_all":{}}"""
      val request = createSignedRequest(HttpMethods.POST, config.elasticSearchUri, path, body, creds)
      request.method shouldBe HttpMethods.POST
      request.uri.toString shouldBe path
      whenReady(entityToString(request.entity)) { res =>
        res shouldBe body
      }
      request.headers.map(_.name()).contains("X-Amz-Date") shouldBe true
      request.headers.map(_.name()).contains("Authorization") shouldBe true
    }
  }

}
