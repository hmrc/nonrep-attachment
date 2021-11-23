package uk.gov.hmrc.nonrep.attachment.models

import io.prometheus.client.Histogram.Timer
import spray.json.{JsObject, JsValue}
import uk.gov.hmrc.nonrep.attachment.{ApiKey, Id}

case class AttachmentRequest(attachmentUrl: Id, attachmentId: Id, payloadSha256Checksum: Id, attachmentContentType: Id, nrSubmissionId: Id)

case class IncomingRequest(apiKey: ApiKey, request: JsValue)

case class AttachmentRequestKey(apiKey: ApiKey, request: AttachmentRequest, maybeTimer: Option[Timer] = None) {
  def withTimer(timer: Timer): AttachmentRequestKey = AttachmentRequestKey(apiKey, request, Some(timer))
}

case class AttachmentResponse(attachmentId: Id)

case class SearchHits(total: Int, hits: Seq[JsObject])

case class SearchResponse(took: Int, timed_out: Boolean, hits: SearchHits)
