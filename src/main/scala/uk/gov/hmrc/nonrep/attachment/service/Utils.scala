package uk.gov.hmrc.nonrep.attachment
package service

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.StandardRoute
import spray.json._
import uk.gov.hmrc.nonrep.attachment.utils.JsonFormats._

trait ResponseService[A] {
  def completeAsJson(message: A): StandardRoute
}

object ResponseService {

  def apply[A](implicit service: ResponseService[A]): ResponseService[A] = service

  object ops {

    implicit class MessageServiceOps[A: ResponseService](message: A) {
      def completeAsJson(): StandardRoute = ResponseService[A].completeAsJson(message)
    }

  }

  implicit val defaultErrorMessageService: ResponseService[ErrorMessage] = completeAsJson(_)

  def completeAsJson(em: ErrorMessage) = complete(
    HttpResponse(
      em.code,
      entity = HttpEntity(ContentTypes.`application/json`, ErrorHttpJson(em.message).toJson.toString())
    )
  )

}
