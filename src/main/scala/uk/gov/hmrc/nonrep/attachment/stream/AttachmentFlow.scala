package uk.gov.hmrc.nonrep.attachment
package stream

import akka.NotUsed
import akka.stream.scaladsl.Flow
import uk.gov.hmrc.nonrep.attachment.models.{AttachmentRequest, AttachmentRequestKey}

object AttachmentFlow {
  def apply() = new AttachmentFlow()
}

class AttachmentFlow() {

  val validation: Flow[AttachmentRequestKey, EitherErr[AttachmentRequestKey], NotUsed] = Flow[AttachmentRequestKey].map {
    Right(_)
  }

}

