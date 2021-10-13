package uk.gov.hmrc.nonrep.attachment

import java.util.UUID

trait DataSamples {

  def validAttachmentRequest(attachmentId: String = UUID.randomUUID().toString, nrSubmissionId: String = UUID.randomUUID().toString) =
    s"""
      {
        "attachmentUrl": "https://presignedurl.s3.eu-west-2.amazonaws.com/...",
        "attachmentId": "$attachmentId",
        "payloadSha256Checksum": "426a1c28<snip>d6d363",
        "attachmentContentType": "image/jpeg",
        "nrSubmissionId": "$nrSubmissionId"
      }
    """

  val invalidAttachmentRequest =
    """
        "attachmentUrl": "https://presignedurl.s3.eu-west-2.amazonaws.com/...",
        "attachmentId": "4b46c86f-30ff-420f-b13f-e4e8b988c08f",
        "payloadSha256Checksum": "426a1c28<snip>d6d363",
        "attachmentContentType": "image/jpeg"
    """
}
