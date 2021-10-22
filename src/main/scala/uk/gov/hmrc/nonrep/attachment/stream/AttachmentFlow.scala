package uk.gov.hmrc.nonrep.attachment
package stream

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL}
import akka.util.ByteString
import uk.gov.hmrc.nonrep.attachment.models.{AttachmentRequest, AttachmentRequestKey, IncomingRequest, SearchResponse}
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig
import uk.gov.hmrc.nonrep.attachment.service.Indexing
import uk.gov.hmrc.nonrep.attachment.utils.JsonFormats._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

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

  private val log = system.log
  val validateAttachmentRequest = es.flow()

  val validateRequest: Flow[IncomingRequest, EitherErr[AttachmentRequestKey], NotUsed] = Flow[IncomingRequest].map {
    case data =>
      Try(data.request.convertTo[AttachmentRequest])
        .toEither.left.map(t => ErrorMessage("JSON parsing error", error = Some(t)))
      .map(AttachmentRequestKey(data.apiKey, _))
  }

  val createEsRequest = Flow[EitherErr[AttachmentRequestKey]].map {
    case data => (data.query(), data)
  }

  val parseEsResponse = Flow[(Try[HttpResponse], EitherErr[AttachmentRequestKey])].map {
    case (tryResponse, entity) => {
      tryResponse match {
        case Success(response) =>
          if (response.status == StatusCodes.OK) {
            log.info(s"ES RESPONSE $response")

            
//            import spray.json._
//            import uk.gov.hmrc.nonrep.attachment.utils.JsonFormats._
//            import DefaultJsonProtocol._
//
//            import scala.concurrent.ExecutionContext.Implicits.global
//            val result = response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map(_.utf8String)
//            val resultEntity = Await.result(result, 1.second)
//
//            val json = resultEntity.parseJson
//            val sr = json.convertTo[SearchResponse]
//
//            log.info(s"ES RESPONSE ENTITY $resultEntity")
//            log.info(s"ES RESPONSE ENTITY JSON $sr")


            Right(entity)
          } else {
            response.entity.discardBytes()
            val message = s"Searching attachments index error for with status ${response.status.intValue()}"
            log.error(message)
            Left(ErrorMessage(message, StatusCodes.InternalServerError))
          }

        case Failure(error) =>
          val message = "Searching attachments index error"
          log.error(message, error)
          Left(ErrorMessage(message, StatusCodes.InternalServerError))
      }
    }.flatten
  }

  val remapAttachmentRequestKey = Flow[EitherErr[AttachmentRequestKey]].map {
    case value => value.map(_.request)
  }


  val validationFlow = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val validationShape = builder.add(validateRequest)
      val responseShape = builder.add(remapAttachmentRequestKey)

      validationShape ~> createEsRequest ~> validateAttachmentRequest ~> parseEsResponse ~> responseShape.in

      FlowShape(validationShape.in, responseShape.out)
    }
  )


}

