package uk.gov.hmrc.nonrep.attachment.models

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.stream.StreamRefMessages.Payload
import io.circe.Json
import uk.gov.hmrc.nonrep.attachment.{ApiKey, AttachmentId, BusinessId, NrSubmissionId}

case class IncomingRequest(apiKey: ApiKey, payload: Payload)

case class SubmissionRequest(
  payload: String,
  metadata: SubmissionMetadata
)

case class SubmissionMetadata(
  businessId: String,
  notableEvent: String,
  payloadContentType: String,
  userSubmissionTimestamp: String,
  identityData: Json,
  userAuthToken: String,
  headerData: Map[String, String],
  searchKeys: Map[String, String],
  payloadSha256Checksum: String,
  receiptData: Option[Json] = None,
  nrSubmissionId: Option[NrSubmissionId] = None,
  attachmentIds: Option[List[AttachmentId]] = None
)

case class BuildVersion(version: String) extends AnyVal

case class SubmissionResponse(nrSubmissionId: NrSubmissionId) extends AnyVal

case class EnrichedMetadata(metadataJsonString: Array[Byte], nrSubmissionId: NrSubmissionId)

case class Bundle(bucket: String, nrSubmissionId: NrSubmissionId, payload: Array[Byte], metadata: Array[Byte])
case class BundleEntry(name: String, content: Array[Byte])
case class BundleEntries(entries: BundleEntry*) {
  def head: BundleEntry = entries.head
  def last: BundleEntry = entries.last
}

case class CompleteSubmission(metadata: EnrichedMetadata, bundle: Array[Byte], request: SubmissionRequest)

case class ClientSettings(
  apiKeySha256: String,
  businessId: BusinessId,
  notableEvents: List[String],
  searchKeys: List[String] = List.empty[String])

case class ErrorMessage(message: String, code: StatusCode = StatusCodes.BadRequest, error: Option[Throwable] = None)

object ErrorMessage {
  def apply(message: String, code: Int): ErrorMessage = ErrorMessage(message, StatusCode.int2StatusCode(code))
}

case class ErrorHttpJson(errorMessage: String) extends AnyVal

case class ReceiptData(language: String) extends AnyVal
