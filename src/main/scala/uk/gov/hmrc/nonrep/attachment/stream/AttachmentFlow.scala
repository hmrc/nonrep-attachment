package uk.gov.hmrc.nonrep.attachment
package stream

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Supervision._
import akka.stream.scaladsl.{Flow, GraphDSL}
import akka.stream.{ActorAttributes, FlowShape}
import uk.gov.hmrc.nonrep.attachment.metrics.Prometheus.elasticSearchQueryResponseTimesHistogram
import uk.gov.hmrc.nonrep.attachment.models.{AttachmentRequest, AttachmentRequestKey, IncomingRequest}
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig
import uk.gov.hmrc.nonrep.attachment.service.Indexing
import uk.gov.hmrc.nonrep.attachment.utils.JsonFormats._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object AttachmentFlow {
  def apply()(implicit system: ActorSystem[_], config: ServiceConfig, es: Indexing[AttachmentRequestKey]) = new AttachmentFlow()
}

class AttachmentFlow()(implicit val system: ActorSystem[_], config: ServiceConfig, es: Indexing[AttachmentRequestKey]) {

  import Indexing.ops._

  val validateAttachmentRequest
    : Flow[(HttpRequest, EitherErr[AttachmentRequestKey]), (Try[HttpResponse], EitherErr[AttachmentRequestKey]), Any] = es.run()

  val validateRequest: Flow[IncomingRequest, EitherErr[AttachmentRequestKey], NotUsed] =
    Flow[IncomingRequest].map { data =>
      Try(data.request.convertTo[AttachmentRequest]).toEither.left
        .map(t => ErrorMessage("JSON parsing error", error = Some(t)))
        .map(AttachmentRequestKey(data.apiKey, _))
    }

  val createEsRequest: Flow[EitherErr[AttachmentRequestKey], (HttpRequest, EitherErr[AttachmentRequestKey]), NotUsed] =
    Flow[EitherErr[AttachmentRequestKey]].map { attachmentRequestKeyOrError =>
      (
        attachmentRequestKeyOrError.query(),
        attachmentRequestKeyOrError.map(_.withTimer(elasticSearchQueryResponseTimesHistogram.startTimer()))
      )
    }

  val parseEsResponse: Flow[(Try[HttpResponse], EitherErr[AttachmentRequestKey]), EitherErr[AttachmentRequestKey], NotUsed] =
    Flow[(Try[HttpResponse], EitherErr[AttachmentRequestKey])]
      .mapAsyncUnordered(8) {
        case (httpResponse, attachmentRequestKeyOrError) =>
          attachmentRequestKeyOrError.map(_.maybeTimer.map(_.observeDuration()))

          httpResponse match {
            case Success(response)  => attachmentRequestKeyOrError.parse(response)
            case Failure(exception) => Future.failed(exception)
          }
      }
      .withAttributes(ActorAttributes.supervisionStrategy(stoppingDecider))

  val remapAttachmentRequestKey: Flow[EitherErr[AttachmentRequestKey], EitherErr[AttachmentRequest], NotUsed] =
    Flow[EitherErr[AttachmentRequestKey]].map { attachmentRequestKeyOrError =>
      attachmentRequestKeyOrError.map(_.request)
    }

  val validationFlow: Flow[IncomingRequest, EitherErr[AttachmentRequest], NotUsed] =
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val validationShape = builder.add(validateRequest)
        val responseShape = builder.add(remapAttachmentRequestKey)

        validationShape ~> createEsRequest ~> validateAttachmentRequest ~> parseEsResponse ~> responseShape.in

        FlowShape(validationShape.in, responseShape.out)
      }
    )
}
