package uk.gov.hmrc.nonrep.attachment.models

import spray.json.{JsObject, JsValue}
import uk.gov.hmrc.nonrep.attachment.Id
import uk.gov.hmrc.nonrep.attachment.utils.CryptoUtils.calculateSha256

case class AttachmentRequest(attachmentUrl: Id,
                             attachmentId: Id,
                             payloadSha256Checksum: Id,
                             attachmentContentType: Id,
                             nrSubmissionId: Id)

case class HashableApiKey(key: String) {
  def hashedKey: String = calculateSha256(key.getBytes("UTF-8"))
}

case class IncomingRequest(apiKey: HashableApiKey, request: JsValue)

case class AttachmentRequestKey(apiKey: HashableApiKey, request: AttachmentRequest)

case class AttachmentResponse(attachmentId: Id)

case class SearchHits(total: Int, hits: Seq[JsObject])

case class SearchResponse(took: Int, timed_out: Boolean, hits: SearchHits)
