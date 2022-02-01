package uk.gov.hmrc.nonrep.attachment
package stream

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes.{BadRequest, InternalServerError, Unauthorized}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.Supervision._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Partition, Sink, ZipWith}
import akka.stream.{ActorAttributes, FlowShape}
import akka.util.ByteString
import io.prometheus.client.Histogram
import uk.gov.hmrc.nonrep.attachment.metrics.Prometheus.{esCounter, esDuration}
import uk.gov.hmrc.nonrep.attachment.models.{AttachmentRequest, AttachmentRequestKey, IncomingRequest}
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig
import uk.gov.hmrc.nonrep.attachment.service.Storage._
import uk.gov.hmrc.nonrep.attachment.service.{Indexing, Storage}
import uk.gov.hmrc.nonrep.attachment.utils.JsonFormats._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object AttachmentFlow {
  def apply()(
    implicit system: ActorSystem[_],
    config: ServiceConfig,
    index: Indexing[AttachmentRequestKey],
    storage: Storage[AttachmentRequestKey]) =
    new AttachmentFlow()
}

class AttachmentFlow()(
  implicit val system: ActorSystem[_],
  config: ServiceConfig,
  index: Indexing[AttachmentRequestKey],
  storage: Storage[AttachmentRequestKey]) {

  val validateAttachmentRequest
    : Flow[(HttpRequest, EitherErr[AttachmentRequestKey]), (Try[HttpResponse], EitherErr[AttachmentRequestKey]), Any] = index.call()

  val validateXApiHeader: Flow[IncomingRequest, EitherErr[IncomingRequest], NotUsed] =
    Flow[IncomingRequest].map { incomingRequest =>
      config.maybeNotableEvents(incomingRequest.apiKey) match {
        case Some(_) => Right(incomingRequest)
        case None    => Left(ErrorMessage("Unauthorised access: Invalid X-API-Key", Unauthorized))
      }
    }

  val validateRequest: Flow[EitherErr[IncomingRequest], EitherErr[AttachmentRequestKey], NotUsed] =
    Flow[EitherErr[IncomingRequest]].map { incomingRequestOrError =>
      incomingRequestOrError.flatMap { incomingRequest =>
        Try(incomingRequest.request.convertTo[AttachmentRequest]).toEither.left
          .map(t => ErrorMessage("JSON parsing error", error = Some(t)))
          .map(AttachmentRequestKey(incomingRequest.apiKey, _))
      }
    }

  val createEsRequest: Flow[EitherErr[AttachmentRequestKey], (HttpRequest, EitherErr[AttachmentRequestKey]), NotUsed] =
    Flow[EitherErr[AttachmentRequestKey]].map { data =>
      (index.request(data), data)
    }

  val parseEsResponse: Flow[(Try[HttpResponse], EitherErr[AttachmentRequestKey]), EitherErr[(AttachmentRequestKey, ByteString)], NotUsed] =
    Flow[(Try[HttpResponse], EitherErr[AttachmentRequestKey])]
      .mapAsyncUnordered(8) {
        case (httpResponse, request) =>
          httpResponse match {
            case Success(response)  => index.response(request, response)
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

  val presignedS3URLs: Flow[(HttpRequest, EitherErr[AttachmentRequestKey]), (Try[HttpResponse], EitherErr[AttachmentRequestKey]), Any] =
    storage.call()

  val createS3Request: Flow[EitherErr[AttachmentRequestKey], (HttpRequest, EitherErr[AttachmentRequestKey]), NotUsed] = {
    Flow[EitherErr[AttachmentRequestKey]].map { data =>
      (storage.request(data), data)
    }
  }

  val parseS3Response: Flow[(Try[HttpResponse], EitherErr[AttachmentRequestKey]), EitherErr[(AttachmentRequestKey, ByteString)], NotUsed] =
    Flow[(Try[HttpResponse], EitherErr[AttachmentRequestKey])]
      .mapAsyncUnordered(8) {
        case (httpResponse, request) =>
          httpResponse match {
            case Success(response)  => storage.response(request, response)
            case Failure(exception) => Future.failed(exception)
          }
      }
      .map(_.left.map(error => ErrorMessage(error.message, BadRequest)))
      .withAttributes(ActorAttributes.supervisionStrategy(stoppingDecider))

  val getAttachmentMaterial: Flow[EitherErr[AttachmentRequestKey], EitherErr[(AttachmentRequestKey, ByteString)], NotUsed] = {
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val requestShape = builder.add(createS3Request)
      val responseShape = builder.add(parseS3Response)

      requestShape ~> presignedS3URLs ~> responseShape

      FlowShape(requestShape.in, responseShape.out)
    })
  }

  val validateAttachmentChecksum
    : Flow[EitherErr[(AttachmentRequestKey, ByteString)], EitherErr[(AttachmentRequestKey, ByteString)], NotUsed] =
    Flow[EitherErr[(AttachmentRequestKey, ByteString)]].map { data =>
      data
        .filterOrElse(
          entry => {
            val (attachment, file) = entry
            attachment.request.payloadSha256Checksum == file.toArray[Byte].calculateSha256
          }, {
            val error419 = StatusCodes.custom(419, "Checksum Failed")
            ErrorMessage(error419.reason(), error419)
          }
        )
        .map { case (attachment, file) => (attachment, file) }
    }

  val putAttachmentForProcessing: Flow[EitherErr[(AttachmentRequestKey, ByteString)], EitherErr[AttachmentRequestKey], NotUsed] =
    Flow[EitherErr[(AttachmentRequestKey, ByteString)]]
      .mapAsyncUnordered(8) {
        case Right((attachment, file)) => storage.upload(attachment, file)
        case Left(error)               => Future.failed(new RuntimeException(error.message))
      }
      .map(_.left.map(error => ErrorMessage(error.message, InternalServerError)))
      .withAttributes(ActorAttributes.supervisionStrategy(stoppingDecider))

  private def partitionRequests[A]() =
    Partition[EitherErr[A]](2, {
      case Left(_)  => 0
      case Right(_) => 1
    })

  val extractRequest: Flow[EitherErr[(AttachmentRequestKey, ByteString)], EitherErr[AttachmentRequestKey], NotUsed] =
    Flow[EitherErr[(AttachmentRequestKey, ByteString)]].map {
      _.map(_._1)
    }

  /**
  partitionEsCalls |~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~>|
			             |~> prepareEsCall ~> broadcastEsRequest ~> | ~> esCall ~> broadcastEsResponse  | ~> merge ~> responseShape.in
									                                                                                | ~> zipEsCalls.in1
                                                                                                  | ~> startMeasureEsCall ~> zipEsCalls.in0 ~> zipEsCalls.out ~> Sink.ignore
								                                                                                  | ~> parseEsResponse  ~> collectEsMetrics ~> out
    */
  val applicationFlow: Flow[IncomingRequest, EitherErr[AttachmentRequest], NotUsed] =
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val partitionEsCalls = builder.add(partitionRequests[AttachmentRequestKey]())
        val mergeEsCalls = builder.add(Merge[EitherErr[AttachmentRequestKey]](2))
        val broadcastEsRequest = builder.add(Broadcast[(HttpRequest, EitherErr[AttachmentRequestKey])](2))
        val broadcastEsResponse = builder.add(Broadcast[(Try[HttpResponse], EitherErr[AttachmentRequestKey])](2))
        val zipEsCalls =
          builder.add(ZipWith[Histogram.Timer, (Try[HttpResponse], EitherErr[AttachmentRequestKey]), Histogram.Timer]((a, _) => a))
        val partitionS3Calls = builder.add(partitionRequests[AttachmentRequestKey]())
        val mergeS3Calls = builder.add(Merge[EitherErr[AttachmentRequestKey]](2))

        val validateXApiHeaderShape = builder.add(validateXApiHeader)
        val responseRemappedShape = builder.add(remapAttachmentRequestKey)

        validateXApiHeaderShape ~> validateRequest ~> partitionEsCalls
        partitionEsCalls ~> mergeEsCalls
        partitionEsCalls ~> createEsRequest ~> broadcastEsRequest
        broadcastEsRequest ~> startEsMetrics ~> zipEsCalls.in0
        broadcastEsRequest ~> validateAttachmentRequest ~> broadcastEsResponse
        broadcastEsResponse ~> zipEsCalls.in1
        zipEsCalls.out ~> finishEsMetrics ~> Sink.ignore
        broadcastEsResponse ~> parseEsResponse ~> extractRequest ~> mergeEsCalls
        mergeEsCalls ~> collectEsMetrics ~> partitionS3Calls
        partitionS3Calls ~> mergeS3Calls
        partitionS3Calls ~> getAttachmentMaterial ~> validateAttachmentChecksum ~> putAttachmentForProcessing ~> mergeS3Calls
        mergeS3Calls ~> responseRemappedShape.in

        FlowShape(validateXApiHeaderShape.in, responseRemappedShape.out)
      }
    )
}
