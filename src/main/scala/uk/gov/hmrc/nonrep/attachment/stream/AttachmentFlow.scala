package uk.gov.hmrc.nonrep.attachment
package stream

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes.Unauthorized
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Supervision._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Partition, Sink, ZipWith}
import akka.stream.{ActorAttributes, FlowShape}
import io.prometheus.client.Histogram
import uk.gov.hmrc.nonrep.attachment.metrics.Prometheus.{esCounter, esDuration}
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

  val validateXApiHeader:  Flow[IncomingRequest, EitherErr[IncomingRequest], NotUsed] =
    Flow[IncomingRequest].map { incomingRequest =>
      config.maybeNotableEvents(incomingRequest.apiKey) match {
        case Some(_) => Right(incomingRequest)
        case None => Left(ErrorMessage("Unauthorised access: Invalid X-API-Key", Unauthorized))
      }
    }

  val validateRequest: Flow[EitherErr[IncomingRequest], EitherErr[AttachmentRequestKey], NotUsed] =
    Flow[EitherErr[IncomingRequest]].map { incomingRequestOrError =>
      incomingRequestOrError.flatMap{ incomingRequest =>
        Try(incomingRequest.request.convertTo[AttachmentRequest]).toEither.left
          .map(t => ErrorMessage("JSON parsing error", error = Some(t)))
          .map(AttachmentRequestKey(incomingRequest.apiKey, _))
      }
    }

  val createEsRequest: Flow[EitherErr[AttachmentRequestKey], (HttpRequest, EitherErr[AttachmentRequestKey]), NotUsed] =
    Flow[EitherErr[AttachmentRequestKey]].map { data =>
      (data.query(), data)
    }

  val parseEsResponse: Flow[(Try[HttpResponse], EitherErr[AttachmentRequestKey]), EitherErr[AttachmentRequestKey], NotUsed] =
    Flow[(Try[HttpResponse], EitherErr[AttachmentRequestKey])]
      .mapAsyncUnordered(8) {
        case (httpResponse, request) =>
          httpResponse match {
            case Success(response)  => request.parse(response)
            case Failure(exception) => Future.failed(exception)
          }
      }
      .withAttributes(ActorAttributes.supervisionStrategy(stoppingDecider))

  val remapAttachmentRequestKey: Flow[EitherErr[AttachmentRequestKey], EitherErr[AttachmentRequest], NotUsed] =
    Flow[EitherErr[AttachmentRequestKey]].map { value =>
      value.map(_.request)
    }

  val startEsMetrics: Flow[(HttpRequest, EitherErr[AttachmentRequestKey]), Histogram.Timer, NotUsed] =
    Flow[(HttpRequest, EitherErr[AttachmentRequestKey])].map { _ =>
      esDuration.labels("es_processing").startTimer()
    }

  val finishEsMetrics: Flow[Histogram.Timer, Double, NotUsed] =
    Flow[Histogram.Timer].map {
      _.observeDuration()
    }

  val collectEsMetrics: Flow[EitherErr[AttachmentRequestKey], EitherErr[AttachmentRequestKey], NotUsed] =
    Flow[EitherErr[AttachmentRequestKey]].map { value =>
      {
        esCounter.labels(value.fold(_ => "5xx", _ => "2xx")).inc()
        value
      }
    }

  private def partitionRequests[A]() =
    Partition[EitherErr[A]](2, {
      case Left(_)  => 0
      case Right(_) => 1
    })

  /**
  partitionEsCalls |~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~>|
			             |~> prepareEsCall ~> broadcastEsRequest ~> | ~> esCall ~> broadcastEsResponse  | ~> merge ~> responseShape.in
									                                                                                | ~> zipEsCalls.in1
                                                                                                  | ~> startMeasureEsCall ~> zipEsCalls.in0 ~> zipEsCalls.out ~> Sink.ignore
								                                                                                  | ~> parseEsResponse  ~> collectEsMetrics ~> out
    */
  val validationFlow: Flow[IncomingRequest, EitherErr[AttachmentRequest], NotUsed] =
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val merge = builder.add(Merge[EitherErr[AttachmentRequestKey]](2))
        val partitionEsCalls = builder.add(partitionRequests[AttachmentRequestKey]())
        val broadcastEsRequest = builder.add(Broadcast[(HttpRequest, EitherErr[AttachmentRequestKey])](2))
        val broadcastEsResponse = builder.add(Broadcast[(Try[HttpResponse], EitherErr[AttachmentRequestKey])](2))
        val zipEsCalls =
          builder.add(ZipWith[Histogram.Timer, (Try[HttpResponse], EitherErr[AttachmentRequestKey]), Histogram.Timer]((a, _) => a))

        val validateXApiHeaderShape = builder.add(validateXApiHeader)
        val validationShape = builder.add(validateRequest)
        val responseShape = builder.add(remapAttachmentRequestKey)

        validateXApiHeaderShape ~> validationShape
        validationShape ~> partitionEsCalls
        partitionEsCalls ~> merge
        partitionEsCalls ~> createEsRequest ~> broadcastEsRequest
        broadcastEsRequest ~> startEsMetrics ~> zipEsCalls.in0
        broadcastEsRequest ~> validateAttachmentRequest ~> broadcastEsResponse
        broadcastEsResponse ~> zipEsCalls.in1
        zipEsCalls.out ~> finishEsMetrics ~> Sink.ignore
        broadcastEsResponse ~> parseEsResponse ~> merge
        merge ~> collectEsMetrics ~> responseShape.in

        FlowShape(validateXApiHeaderShape.in, responseShape.out)
      }
    )
}
