package uk.gov.hmrc.nonrep.attachment
package utils

import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import uk.gov.hmrc.nonrep.attachment.models.AttachmentRequest

object JsonFormats extends DefaultJsonProtocol {

  implicit val buildVersionJsonFormat: RootJsonFormat[BuildVersion] = jsonFormat1(BuildVersion)
  implicit val attachmentRequestFormat: RootJsonFormat[AttachmentRequest] = jsonFormat5(AttachmentRequest)
}