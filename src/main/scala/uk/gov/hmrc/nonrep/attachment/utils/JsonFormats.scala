package uk.gov.hmrc.nonrep.attachment
package utils

import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import uk.gov.hmrc.nonrep.attachment.models._

object JsonFormats extends DefaultJsonProtocol {

  implicit val buildVersionJsonFormat: RootJsonFormat[BuildVersion] = jsonFormat1(BuildVersion)
  implicit val attachmentRequestJsonFormat: RootJsonFormat[AttachmentRequest] = jsonFormat5(AttachmentRequest)
  implicit val searchHitsJsonFormat: RootJsonFormat[SearchHits] = jsonFormat2(SearchHits)
  implicit val SearchResponseJsonFormat: RootJsonFormat[SearchResponse] = jsonFormat3(SearchResponse)
  implicit val attachmentResponseJsonFormat: RootJsonFormat[AttachmentResponse] = jsonFormat1(AttachmentResponse)
  implicit val incomingRequestJsonFormat: RootJsonFormat[IncomingRequest] = jsonFormat2(IncomingRequest)
}