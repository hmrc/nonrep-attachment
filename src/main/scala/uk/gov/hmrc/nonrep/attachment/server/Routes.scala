package uk.gov.hmrc.nonrep.attachment
package server

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import akka.http.scaladsl.model.StatusCodes.{Accepted, BadRequest, InternalServerError, OK}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.MethodDirectives.{get, post}
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.{ExceptionHandler, Route, StandardRoute}
import akka.stream.Attributes.LogLevels.{Error, Info, Off}
import akka.stream.Attributes.logLevels
import akka.stream.scaladsl.{Framing, Keep, Sink}
import akka.stream.scaladsl.Framing.FramingException
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsDirectives.{pathLabeled, _}
import fr.davit.akka.http.metrics.prometheus.marshalling.PrometheusMarshallers._
import uk.gov.hmrc.nonrep.BuildInfo
import uk.gov.hmrc.nonrep.attachment.metrics.Prometheus._
import uk.gov.hmrc.nonrep.attachment.stream.AttachmentFlow
import uk.gov.hmrc.nonrep.attachment.utils.JsonFormats._

import scala.util.{Failure, Success}

object Routes {
  def apply(flow: AttachmentFlow)(implicit system: ActorSystem[_], config: ServiceConfig) = new Routes(flow)
}

class Routes(flow: AttachmentFlow)(implicit val system: ActorSystem[_], config: ServiceConfig) {

  implicit val jsonStreamingSupport = EntityStreamingSupport.json()
  private val headerApiKey = "x-api-key"

  private val log = system.log
  private[server] val exceptionHandler = ExceptionHandler {
    case x =>
      log.error("Internal server error", x)
      complete(HttpResponse(InternalServerError, entity = "Internal NRS attachments API error"))
  }

  lazy val serviceRoutes: Route =
    handleExceptions(exceptionHandler) {
      pathPrefixLabeled("attachment") {
        pathEndOrSingleSlash {
          withoutSizeLimit {
            post {
              headerValueByName(headerApiKey) { apiKey =>

                entity(asSourceOf[AttachmentRequest]) { request =>
                  val stream = request
                    .log(name = "attachmentFlow")
                    .addAttributes(logLevels(onElement = Off, onFinish = Info, onFailure = Error))
                    .via(flow.validation)
                    .toMat(Sink.head)(Keep.right)
                    .run()

                  onComplete(stream) {
                    case Success(result) =>
                      result.fold[StandardRoute](
                        err => {
                          log.error("Submission error {}", err)
                          complete(HttpResponse(BadRequest))
                        },
                        res => {
                          complete {
                            HttpResponse(Accepted, entity = HttpEntity(res.attachmentId))
                          }
                        }
                      )
                    case Failure(x) =>
                      log.error(s"Internal NRS error, caused by ${x.getCause}", x)
                      complete(HttpResponse(InternalServerError))
                  }
                }
              }
            }
          }
        } ~ pathLabeled("ping") {
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
