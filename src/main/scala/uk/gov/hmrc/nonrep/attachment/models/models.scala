package uk.gov.hmrc.nonrep.attachment.models

import spray.json.{JsObject, JsValue}
import uk.gov.hmrc.nonrep.attachment.Id

import java.security.MessageDigest

case class AttachmentRequest(attachmentUrl: Id,
                             attachmentId: Id,
                             payloadSha256Checksum: Id,
                             attachmentContentType: Id,
                             nrSubmissionId: Id)

case class ApiKey(key: String) {
  def hashedKey: String =
    MessageDigest.getInstance("SHA-256").digest(key.getBytes("UTF-8")).map("%02x".format(_)).mkString
}

case class IncomingRequest(apiKey: ApiKey, request: JsValue)

case class AttachmentRequestKey(apiKey: ApiKey, request: AttachmentRequest)

case class AttachmentResponse(attachmentId: Id)

case class SearchHits(total: Int, hits: Seq[JsObject])

case class SearchResponse(took: Int, timed_out: Boolean, hits: SearchHits)
