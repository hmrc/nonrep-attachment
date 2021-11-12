package uk.gov.hmrc.nonrep.attachment
package server

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.ContentTypes.{`application/json`, `text/plain(UTF-8)`}
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.StatusCodes.{Accepted, BadRequest, OK}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import org.scalatest.concurrent.ScalaFutures
import spray.json._
import uk.gov.hmrc.nonrep.BuildInfo
import uk.gov.hmrc.nonrep.attachment.TestServices.{notFound, parseFailure, success}
import uk.gov.hmrc.nonrep.attachment.models.AttachmentResponse
import uk.gov.hmrc.nonrep.attachment.stream.AttachmentFlow
import uk.gov.hmrc.nonrep.attachment.utils.JsonFormats._

import java.util.UUID.randomUUID
import scala.concurrent.duration._

class RoutesSpec extends BaseSpec with ScalaFutures with ScalatestRouteTest {

  private lazy val testKit = ActorTestKit()

  private implicit def typedSystem: ActorSystem[Nothing] = testKit.system

  override def createActorSystem(): akka.actor.ActorSystem = testKit.system.toClassic

  private implicit val timeout: RouteTestTimeout = RouteTestTimeout(10 second span)

  private val apiKeyHeader = RawHeader("x-api-Key", apiKey)

  private implicit val config: ServiceConfig = new ServiceConfig()

  "Service routes for attachment service" should {
    val routes = new Routes(TestServices.success.flow)

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
  }

  "POST to /attachments" should {
    "return 202 Accepted and the attachment id" when {
      "the attachment request is valid and the attachment is found in ElasticSearch" in {
        val attachmentId = randomUUID().toString

        attachmentsRequest(validAttachmentRequestJson(attachmentId)) ~> new Routes(success.flow).serviceRoutes ~> check {
          status shouldBe Accepted
          responseAs[String] shouldBe AttachmentResponse(attachmentId).toJson.toString
        }
      }
    }

    "return 400 BadRequest" when {
      "the attachment request is malformed" in {
        ensureBadRequestIsReturned("JSON parsing error", success.flow, attachmentsRequest(invalidAttachmentRequestJson))
      }

      "the ElasticSearch result cannot be parsed" in {
        ensureBadRequestIsReturned("nrSubmissionId validation error", parseFailure.flow)
      }

      "the attachment id is not found in ElasticSearch" in {
        ensureBadRequestIsReturned("Response status 404 Not Found from ES server", notFound.flow)
      }

      def ensureBadRequestIsReturned(
        message: String,
        flow: AttachmentFlow,
        request: HttpRequest = attachmentsRequest(validAttachmentRequestJson())) =
        request ~> new Routes(flow).serviceRoutes ~> check {
          status shouldBe BadRequest
          responseAs[String] should include(message)
        }
    }

    def attachmentsRequest(json: String) =
      Post("/attachment").withEntity(`application/json`, json).withHeaders(apiKeyHeader)
  }
}
