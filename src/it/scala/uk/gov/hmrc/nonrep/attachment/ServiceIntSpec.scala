package uk.gov.hmrc.nonrep.attachment

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.Inside
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.time.{Millis, Span}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import uk.gov.hmrc.nonrep.attachment.utils.JsonFormats._
import uk.gov.hmrc.nonrep.BuildInfo
import uk.gov.hmrc.nonrep.attachment.server.{NonrepMicroservice, Routes, ServiceConfig}

import scala.concurrent.Future

class ServiceIntSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with ScalaFutures with Inside {
  import TestServices._

  var server: NonrepMicroservice = null
  implicit val config: ServiceConfig = new ServiceConfig(servicePort = 9000)
  val hostUrl = s"http://localhost:${config.port}"
  val service = config.appName

  lazy val testKit = ActorTestKit()
  implicit val typedSystem = testKit.system

  override def createActorSystem(): akka.actor.ActorSystem = testKit.system.toClassic

  implicit val patience: PatienceConfig = PatienceConfig(Span(5000, Millis), Span(100, Millis))

  override def beforeAll() = {
    server = NonrepMicroservice(Routes())
  }

  override def afterAll(): Unit = {
    whenReady(server.serverBinding) {
      _.unbind()
    }
  }

  "attachment service service" should {

    "return version information for GET request to service /version endpoint" in {
      val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = s"$hostUrl/${config.appName}/version"))
      whenReady(responseFuture) { res =>
        res.status shouldBe StatusCodes.OK
        whenReady(entityToString(res.entity)) { body =>
          body shouldBe buildVersionJsonFormat.write(BuildVersion(version = BuildInfo.version)).toString
        }
      }
    }

    "return a 'pong' response for GET requests to service /ping endpoint" in {
      val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = s"$hostUrl/${config.appName}/ping"))
      whenReady(responseFuture) { res =>
        res.status shouldBe StatusCodes.OK
        whenReady(entityToString(res.entity)) { body =>
          body shouldBe "pong"
        }
      }
    }

    "return a 'pong' response for GET requests to /ping endpoint" in {
      val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = s"$hostUrl/ping"))
      whenReady(responseFuture) { res =>
        res.status shouldBe StatusCodes.OK
        whenReady(entityToString(res.entity)) { body =>
          body shouldBe "pong"
        }
      }
    }

  }
}
