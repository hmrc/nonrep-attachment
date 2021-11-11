package uk.gov.hmrc.nonrep.attachment

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.Inside
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import uk.gov.hmrc.nonrep.attachment.server.{NonrepMicroservice, Routes, ServiceConfig}

import java.util.UUID.randomUUID

class AttachmentNotFoundInElasticSearchIntSpec extends BaseSpec with ScalatestRouteTest with ScalaFutures with Inside {
  import TestServices._

  private lazy val testKit = ActorTestKit()
  private implicit val typedSystem: ActorSystem[Nothing] = testKit.system
  private implicit val config: ServiceConfig = new ServiceConfig(servicePort = 9000)

  private val routes = Routes(notFound.flow)
  private lazy val service: NonrepMicroservice = NonrepMicroservice(routes)(typedSystem, config)

  private val hostUrl = s"http://localhost:${config.port}"

  override def createActorSystem(): akka.actor.ActorSystem = testKit.system.toClassic

  implicit val patience: PatienceConfig = PatienceConfig(Span(5000, Millis), Span(100, Millis))

  private def initializeService(): Unit = service

  override def beforeAll(): Unit = initializeService()

  override def afterAll(): Unit =
    whenReady(service.serverBinding) {
      _.unbind()
    }

  private val apiKeyHeader = RawHeader("x-api-Key", "66975df1e55c4bb9c7dcb4313e5514c234f071b1199efd455695fefb3e54bbf2")

  "POST /attachment" should {
    "return 400 BAD_REQUEST" when {
      "ElasticSearch returns 404 NOT_FOUND" in {
        whenReady(
          Http().singleRequest(
            HttpRequest(POST, uri = s"$hostUrl/attachment")
              .withEntity(`application/json`, validAttachmentRequestJson(randomUUID().toString))
              .withHeaders(apiKeyHeader))) { res =>
          res.status shouldBe BadRequest
        }
      }
    }
  }
}
