package uk.gov.hmrc.nonrep

import akka.http.scaladsl.model.{StatusCode, StatusCodes}

package object attachment {
  val METADATA_FILE = "metadata.json"
  val ATTACHMENT_FILE = "attachment.data"

  type Id = String
  type EitherErr[T] = Either[ErrorMessage, T]

  case class BuildVersion(version: String) extends AnyVal

  case class ErrorMessage(message: String, code: StatusCode = StatusCodes.BadRequest, error: Option[Throwable] = None)

  object ErrorMessage {
    def apply(message: String, code: Int): ErrorMessage = ErrorMessage(message, StatusCode.int2StatusCode(code))
  }

  case class ErrorHttpJson(errorMessage: String)
}
