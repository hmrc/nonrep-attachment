package uk.gov.hmrc.nonrep.attachment
package server

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.{InternalServerError, OK}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsDirectives.{pathLabeled, _}
import fr.davit.akka.http.metrics.prometheus.marshalling.PrometheusMarshallers._
import uk.gov.hmrc.nonrep.BuildInfo
import uk.gov.hmrc.nonrep.attachment.metrics.Prometheus._
import uk.gov.hmrc.nonrep.attachment.utils.JsonFormats._

object Routes {
  def apply()(implicit system: ActorSystem[_], config: ServiceConfig) = new Routes()
}

class Routes()(implicit val system: ActorSystem[_], config: ServiceConfig) {

  private val log = system.log
  private[server] val exceptionHandler = ExceptionHandler {
    case x =>
      log.error("Internal server error", x)
      complete(HttpResponse(InternalServerError, entity = "Internal NRS attachments API error"))
  }

  lazy val serviceRoutes: Route =
    handleExceptions(exceptionHandler) {
      pathPrefix("attachment") {
        pathLabeled("ping") {
          get {
            complete(HttpResponse(OK, entity = "pong"))
          }
        } ~ pathLabeled("version") {
          pathEndOrSingleSlash {
            get {
              complete(OK, BuildVersion(version = BuildInfo.version))
            }
          }
        }
      } ~ pathLabeled("ping") {
        get {
          complete(HttpResponse(OK, entity = "pong"))
        }
      } ~ pathLabeled("metrics") {
        get {
          metrics(registry)
        }
      }
    }
}
