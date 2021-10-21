package uk.gov.hmrc.nonrep.attachment
package server

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.ContentTypes.{`application/json`, `text/plain(UTF-8)`}
import akka.http.scaladsl.model.StatusCodes.{Accepted, InternalServerError, OK}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.nonrep.BuildInfo
import uk.gov.hmrc.nonrep.attachment.stream.AttachmentFlow
import uk.gov.hmrc.nonrep.attachment.utils.JsonFormats._

import scala.concurrent.duration._

class RoutesSpec extends BaseSpec with ScalaFutures with ScalatestRouteTest {

  import TestServices.success._

  private lazy val testKit = ActorTestKit()

  private implicit def typedSystem: ActorSystem[Nothing] = testKit.system

  override def createActorSystem(): akka.actor.ActorSystem = testKit.system.toClassic

  private implicit val timeout: RouteTestTimeout = RouteTestTimeout(10 second span)

  private val apiKeyHeader = RawHeader("x-api-Key", "66975df1e55c4bb9c7dcb4313e5514c234f071b1199efd455695fefb3e54bbf2")

  private implicit val config: ServiceConfig = new ServiceConfig()

  private val routes = new Routes(flow)

  "Service routes for attachment service" should {

    "catch exception" in {
      Get(s"/${config.appName}/version") ~> handleExceptions(routes.exceptionHandler) {
        _.complete((1 / 0).toString)
      } ~> check {
        responseAs[String] shouldEqual "Internal NRS attachments API error"
      }
    }

    "return version information" in {
      Get(s"/${config.appName}/version") ~> routes.serviceRoutes ~> check {
        status shouldBe OK
        contentType shouldBe `application/json`
        responseAs[BuildVersion].version shouldBe BuildInfo.version
      }
    }

    "reply to ping request on service url" in {
      Get(s"/${config.appName}/ping") ~> routes.serviceRoutes ~> check {
        status shouldBe OK
        contentType shouldBe `text/plain(UTF-8)`
        responseAs[String] shouldBe "pong"
      }
    }

    "reply to ping request" in {
      Get(s"/ping") ~> routes.serviceRoutes ~> check {
        status shouldBe OK
        contentType shouldBe `text/plain(UTF-8)`
        responseAs[String] shouldBe "pong"
      }
    }

    "return 202 code for valid attachment request" in {
      val attachmentId = UUID.randomUUID().toString
      val attachmentRequest = validAttachmentRequest(attachmentId)
      val request = Post("/attachment").withEntity(`application/json`, attachmentRequest).withHeaders(apiKeyHeader)
      request ~> routes.serviceRoutes ~> check {
        status shouldBe Accepted
        responseAs[String] shouldBe attachmentId
      }
    }

    "return 5xx code for malformed attachment request" in {
      val attachmentRequest = invalidAttachmentRequest
      val request = Post("/attachment").withEntity(`application/json`, attachmentRequest).withHeaders(apiKeyHeader)
      request ~> routes.serviceRoutes ~> check {
        status shouldBe InternalServerError
      }
    }

    "return 400 code for invalid attachment request (doesn't have corresponding data in meta-store)" in {

    }
  }
}
