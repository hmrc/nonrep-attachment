package uk.gov.hmrc.nonrep.attachment
package server

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.nonrep.BuildInfo
import org.scalatest.matchers.should.Matchers._
import scala.concurrent.duration._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import uk.gov.hmrc.nonrep.attachment.utils.JsonFormats._
import akka.http.scaladsl.server.Directives._

class RoutesSpec extends AnyWordSpec with ScalaFutures with ScalatestRouteTest {

  lazy val testKit = ActorTestKit()
  implicit def typedSystem = testKit.system
  override def createActorSystem(): akka.actor.ActorSystem = testKit.system.toClassic
  implicit val timeout = RouteTestTimeout(10 second span)

  implicit val config: ServiceConfig = new ServiceConfig()

  val routes = new Routes()

  "Service routes for attachment service" should {

    "catch exception" in {
      val request = Get(s"/${config.appName}/version") ~> handleExceptions(routes.exceptionHandler) {
        _.complete((1 / 0).toString)
      } ~> check {
        responseAs[String] shouldEqual "Internal NRS attachments API error"
      }
    }

    "return version information" in {
      Get(s"/${config.appName}/version") ~> routes.serviceRoutes ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[BuildVersion].version shouldBe BuildInfo.version
      }
    }

    "reply to ping request on service url" in {
      Get(s"/${config.appName}/ping") ~> routes.serviceRoutes ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`text/plain(UTF-8)`
        responseAs[String] shouldBe "pong"
      }
    }

    "reply to ping request" in {
      Get(s"/ping") ~> routes.serviceRoutes ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`text/plain(UTF-8)`
        responseAs[String] shouldBe "pong"
      }
    }

  }
}
