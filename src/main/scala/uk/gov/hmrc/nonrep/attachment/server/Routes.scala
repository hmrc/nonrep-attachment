package uk.gov.hmrc.nonrep.attachment
package server

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes.{Accepted, InternalServerError, OK, Unauthorized}
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.MethodDirectives.{get, post}
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.stream.Attributes.LogLevels.{Error, Info, Off}
import akka.stream.Attributes.logLevels
import akka.stream.scaladsl.{Keep, Sink}
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsDirectives.{pathLabeled, _}
import fr.davit.akka.http.metrics.prometheus.marshalling.PrometheusMarshallers._
import spray.json._
import uk.gov.hmrc.nonrep.BuildInfo
import uk.gov.hmrc.nonrep.attachment.metrics.Prometheus._
import uk.gov.hmrc.nonrep.attachment.models.{AttachmentResponse, ApiKey, IncomingRequest}
import uk.gov.hmrc.nonrep.attachment.service.ResponseService
import uk.gov.hmrc.nonrep.attachment.stream.AttachmentFlow
import uk.gov.hmrc.nonrep.attachment.utils.JsonFormats._

import scala.util.{Failure, Success}

object Routes {
  def apply(flow: AttachmentFlow)(implicit system: ActorSystem[_], config: ServiceConfig) = new Routes(flow)
}

class Routes(flow: AttachmentFlow)(implicit val system: ActorSystem[_], config: ServiceConfig) {

  import ResponseService.ops._

  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()
  private val headerApiKey = "x-api-key"

  private val log = system.log
  private[server] val exceptionHandler = ExceptionHandler {
    case x =>
      log.error("Internal server error", x)
      complete(HttpResponse(InternalServerError, entity = "Internal NRS attachments API error"))
  }

  private[server] val missingXApiHeaderRejectionHandler: RejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case MissingHeaderRejection(`headerApiKey`) => complete(HttpResponse(Unauthorized))
    }
    .result()

  lazy val serviceRoutes: Route =
    handleExceptions(exceptionHandler) {
      handleRejections(missingXApiHeaderRejectionHandler) {
        pathPrefixLabeled("attachment") {
          pathEndOrSingleSlash {
            withoutSizeLimit {
              post {
                headerValueByName(headerApiKey) { apiKey =>

                  entity(asSourceOf[JsValue]) { request =>
                    val stream = request
                      .log(name = "attachmentFlow")
                      .addAttributes(logLevels(onElement = Off, onFinish = Info, onFailure = Error))
                      .map(IncomingRequest(ApiKey(apiKey), _))
                      .via(flow.applicationFlow)
                      .toMat(Sink.head)(Keep.right)
                      .run()

                    onComplete(stream) {
                      case Success(result) =>
                        result.fold[StandardRoute](
                          err => {
                            log.error("Attachment service error {}", err)
                            err.completeAsJson()
                          },
                          res => {
                            complete {
                              HttpResponse(Accepted, entity = HttpEntity(AttachmentResponse(res.attachmentId).toJson.toString))
                            }
                          }
                        )
                      case Failure(x) =>
                        val message = "Internal NRS error"
                        log.error(s"$message, caused by ${x.getCause}", x)
                        ErrorMessage(message, InternalServerError).completeAsJson()
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
}
