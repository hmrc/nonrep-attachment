package uk.gov.hmrc.nonrep

import akka.http.scaladsl.model.{StatusCode, StatusCodes}

package object attachment {

  type NrSubmissionId = String
  type BusinessId = String
  type AttachmentId = String
  type ApiKey = String
  type EitherErr[T] = Either[ErrorMessage, T]

  case class BuildVersion(version: String) extends AnyVal

  case class ErrorMessage(message: String, code: StatusCode = StatusCodes.BadRequest, error: Option[Throwable] = None)

  object ErrorMessage {
    def apply(message: String, code: Int): ErrorMessage = ErrorMessage(message, StatusCode.int2StatusCode(code))
  }

  case class AttachmentRequest(attachmentUrl: String,
                               attachmentId: String,
                               payloadSha256Checksum: String,
                               attachmentContentType: String,
                               nrSubmissionId: String)

  case class SubmissionMetadata(nrSubmissionId: String,
                                attachmentIds: Option[List[String]] = None
                               )

}
