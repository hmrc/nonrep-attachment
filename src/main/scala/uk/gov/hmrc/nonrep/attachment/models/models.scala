package uk.gov.hmrc.nonrep.attachment.models

import uk.gov.hmrc.nonrep.attachment.{ApiKey, Id}

case class AttachmentRequest(attachmentUrl: Id,
                             attachmentId: Id,
                             payloadSha256Checksum: Id,
                             attachmentContentType: Id,
                             nrSubmissionId: Id)

case class AttachmentRequestKey(apiKey: ApiKey, request: AttachmentRequest)
