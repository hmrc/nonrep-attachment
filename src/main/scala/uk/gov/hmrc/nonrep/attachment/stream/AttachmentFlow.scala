package uk.gov.hmrc.nonrep.attachment
package stream

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.Flow
import uk.gov.hmrc.nonrep.attachment.models.{AttachmentRequest, AttachmentRequestKey}
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig
import uk.gov.hmrc.nonrep.attachment.service.Indexing

object AttachmentFlow {
  def apply()
           (implicit system: ActorSystem[_],
            config: ServiceConfig,
            es: Indexing[AttachmentRequestKey]) = new AttachmentFlow()
}

class AttachmentFlow()(implicit val system: ActorSystem[_],
                       config: ServiceConfig,
                       es: Indexing[AttachmentRequestKey]) {

  import Indexing.ops._

  val validateAttachmentRequest = es.flow()

  val validation: Flow[AttachmentRequestKey, EitherErr[AttachmentRequestKey], NotUsed] = Flow[AttachmentRequestKey].map {
    Right(_)
  }

}

