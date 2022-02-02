package uk.gov.hmrc.nonrep.attachment

import java.util.UUID

import uk.gov.hmrc.nonrep.attachment.models.AttachmentRequest

trait DataSamples {

  def validAttachmentRequest(
    attachmentId: String = UUID.randomUUID().toString,
    nrSubmissionId: String = UUID.randomUUID().toString,
    checksum: String = "9d963647c09ce5b31d55bb6b2f0c887b280320e7a92e064fbea1c1326c0f82ac") =
    AttachmentRequest(
      "https://non-repudiation-resources-artifacts.s3.eu-west-2.amazonaws.com/attachments/public/0f0d6508-7f9f-11ec-b1fb-a732847931b5",
      attachmentId,
      checksum,
      "application/json",
      nrSubmissionId
    )

  def validAttachmentRequestJson(attachmentId: String = UUID.randomUUID().toString, nrSubmissionId: String = UUID.randomUUID().toString) =
    s"""
      {
        "attachmentUrl": "https://non-repudiation-resources-artifacts.s3.eu-west-2.amazonaws.com/attachments/public/0f0d6508-7f9f-11ec-b1fb-a732847931b5",
        "attachmentId": "$attachmentId",
        "attachmentSha256Checksum": "9d963647c09ce5b31d55bb6b2f0c887b280320e7a92e064fbea1c1326c0f82ac",
        "attachmentContentType": "application/json",
        "nrSubmissionId": "$nrSubmissionId"
      }
    """

  val invalidAttachmentRequestJson =
    """{
        "attachmentUrl": "https://presignedurl.s3.eu-west-2.amazonaws.com/...",
        "attachmentId": "4b46c86f-30ff-420f-b13f-e4e8b988c08f",
        "attachmentSha256Checksum": "426a1c28<snip>d6d363",
        "attachmentContentType": "image/jpeg"
        }
    """
}
