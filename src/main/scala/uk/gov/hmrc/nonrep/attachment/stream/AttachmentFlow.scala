package uk.gov.hmrc.nonrep.attachment
package stream

import akka.NotUsed
import akka.stream.scaladsl.Flow

object AttachmentFlow {
  def apply() = new AttachmentFlow()
}

class AttachmentFlow() {


  val validation: Flow[AttachmentRequest, EitherErr[AttachmentRequest], NotUsed] = Flow[AttachmentRequest].map {
    Right(_)
  }

}
