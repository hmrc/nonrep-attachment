package uk.gov.hmrc.nonrep.attachment
package stream

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes.{BadRequest, InternalServerError}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.ByteString
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import spray.json._
import uk.gov.hmrc.nonrep.attachment.metrics.Prometheus.esCounter
import uk.gov.hmrc.nonrep.attachment.models.{AttachmentRequest, AttachmentRequestKey, IncomingRequest}

import scala.util.Try

class AttachmentFlowSpec extends BaseSpec with ScalaFutures with ScalatestRouteTest with MockFactory {

  import TestServices._

  "attachments flow" should {
    import TestServices.success._

    "validate incoming request" in {
      val source = TestSource.probe[EitherErr[IncomingRequest]]
      val sink = TestSink.probe[EitherErr[AttachmentRequestKey]]
      val (pub, sub) = source.via(flow.validateRequest).toMat(sink)(Keep.both).run()
      pub
        .sendNext(Right[ErrorMessage, IncomingRequest](IncomingRequest(apiKey, validAttachmentRequestJson().parseJson)))
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()
      result.isRight shouldBe true
    }

    "create ES request" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[EitherErr[AttachmentRequestKey]]
      val sink = TestSink.probe[(HttpRequest, EitherErr[AttachmentRequestKey])]
      val (pub, sub) = source.via(flow.createEsRequest).toMat(sink)(Keep.both).run()
      pub
        .sendNext(Right(AttachmentRequestKey(apiKey, validAttachmentRequest(attachmentId, submissionId))))
        .sendComplete()
      val (httpRequest, entity) = sub
        .request(1)
        .expectNext()

      entity.isRight shouldBe true
      httpRequest.method shouldBe HttpMethods.POST
      httpRequest.uri.toString shouldBe "/vat-registration-attachments/_search"
      whenReady(entityToString(httpRequest.entity)) { res =>
        res should include(attachmentId)
        res should include(submissionId)
      }
    }

    "parse ES response" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[(Try[HttpResponse], EitherErr[AttachmentRequestKey])]
      val sink = TestSink.probe[EitherErr[AttachmentRequestKey]]
      val (pub, sub) = source.via(flow.parseEsResponse).toMat(sink)(Keep.both).run()
      pub
        .sendNext((Try(HttpResponse()), Right(AttachmentRequestKey(apiKey, validAttachmentRequest(attachmentId, submissionId)))))
        .sendComplete()
      val response = sub
        .request(1)
        .expectNext()
      response.isRight shouldBe true
    }

    "remap AttachmentRequestKey case class into AttachmentRequest" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[EitherErr[AttachmentRequestKey]]
      val sink = TestSink.probe[EitherErr[AttachmentRequest]]
      val (pub, sub) = source.via(flow.remapAttachmentRequestKey).toMat(sink)(Keep.both).run()
      pub
        .sendNext(Right(AttachmentRequestKey(apiKey, validAttachmentRequest(attachmentId, submissionId))))
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()

      result.isRight shouldBe true
      result.toOption.get.attachmentId shouldBe attachmentId
      result.toOption.get.nrSubmissionId shouldBe submissionId
    }

    "collect Es metrics" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[EitherErr[AttachmentRequestKey]]
      val sink = TestSink.probe[EitherErr[AttachmentRequestKey]]
      val (pub, sub) = source.via(flow.collectEsMetrics).toMat(sink)(Keep.both).run()
      pub
        .sendNext(Right(AttachmentRequestKey(apiKey, validAttachmentRequest(attachmentId, submissionId))))
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()

      result.isRight shouldBe true
      result.toOption.get.request.attachmentId shouldBe attachmentId
      result.toOption.get.request.nrSubmissionId shouldBe submissionId
      esCounter.labels("2xx").get() shouldBe 1.0d
    }

    "collect Es metrics histogram" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[(HttpRequest, EitherErr[AttachmentRequestKey])]
      val sink = TestSink.probe[Double]
      val (pub, sub) = source.via(flow.startEsMetrics).via(flow.finishEsMetrics).toMat(sink)(Keep.both).run()
      pub
        .sendNext((HttpRequest(), Right(AttachmentRequestKey(apiKey, validAttachmentRequest(attachmentId, submissionId)))))
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()

      result should be > 0d
    }

    "validate the whole application flow" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[IncomingRequest]
      val sink = TestSink.probe[Either[ErrorMessage, AttachmentRequest]]
      val (pub, sub) = source.via(flow.applicationFlow).toMat(sink)(Keep.both).run()
      pub
        .sendNext(IncomingRequest(apiKey, validAttachmentRequestJson(attachmentId, submissionId).parseJson))
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()

      result.isRight shouldBe true
      result.toOption.get.attachmentId shouldBe attachmentId
      result.toOption.get.nrSubmissionId shouldBe submissionId
    }

    "get attachment (from upscan)" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[Either[ErrorMessage, AttachmentRequestKey]]
      val sink = TestSink.probe[Either[ErrorMessage, (AttachmentRequestKey, ByteString)]]

      val (pub, sub) = source.via(flow.getAttachmentMaterial).toMat(sink)(Keep.both).run()
      pub
        .sendNext(Right(AttachmentRequestKey(apiKey, validAttachmentRequest(attachmentId, submissionId))))
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()

      result.isRight shouldBe true
      result.map {
        case (attachment, file) =>
          attachment.request.attachmentId shouldBe attachmentId
          attachment.request.nrSubmissionId shouldBe submissionId
          file shouldBe sampleAttachment
      }
    }

    "validate attachment checksum" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[Either[ErrorMessage, (AttachmentRequestKey, ByteString)]]
      val sink = TestSink.probe[Either[ErrorMessage, (AttachmentRequestKey, ByteString)]]

      val (pub, sub) = source.via(flow.validateAttachmentChecksum).toMat(sink)(Keep.both).run()
      pub
        .sendNext(Right(AttachmentRequestKey(apiKey, validAttachmentRequest(attachmentId, submissionId)), ByteString(sampleAttachment)))
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()

      result.isRight shouldBe true
      result.map {
        case (attachment, file) =>
          attachment.request.attachmentId shouldBe attachmentId
          attachment.request.nrSubmissionId shouldBe submissionId
          file shouldBe sampleAttachment
      }
    }

    "put attachment for processing" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[Either[ErrorMessage, (AttachmentRequestKey, ByteString)]]
      val sink = TestSink.probe[Either[ErrorMessage, AttachmentRequestKey]]

      val (pub, sub) = source.via(flow.putAttachmentForProcessing).toMat(sink)(Keep.both).run()
      pub
        .sendNext(Right(AttachmentRequestKey(apiKey, validAttachmentRequest(attachmentId, submissionId)), ByteString(sampleAttachment))
          .withLeft[ErrorMessage])
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()

      result.isRight shouldBe true
      result.map { attachment =>
        attachment.request.attachmentId shouldBe attachmentId
        attachment.request.nrSubmissionId shouldBe submissionId
      }
    }
  }

  "for negative scenario" should {
    import TestServices.failure._

    "collect Es metrics for failing response" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[EitherErr[AttachmentRequestKey]]
      val sink = TestSink.probe[EitherErr[AttachmentRequestKey]]
      val (pub, sub) = source.via(flow.collectEsMetrics).toMat(sink)(Keep.both).run()
      pub
        .sendNext(Left(ErrorMessage("error")))
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()

      result.isLeft shouldBe true
      esCounter.labels("5xx").get() shouldBe 1.0d
    }

    "fail on ES upstream failure" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[(Try[HttpResponse], EitherErr[AttachmentRequestKey])]
      val sink = TestSink.probe[Either[ErrorMessage, AttachmentRequestKey]]
      val (pub, sub) = source.via(flow.parseEsResponse).toMat(sink)(Keep.both).run()
      pub
        .sendNext((Try(HttpResponse()), Right(AttachmentRequestKey(apiKey, validAttachmentRequest(attachmentId, submissionId)))))
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()

      result.isLeft shouldBe true
      result.left.map { error =>
        error.message should include("nrSubmissionId validation error")
      }
    }

    "get attachment with invalid pre-signed uri" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[Either[ErrorMessage, AttachmentRequestKey]]
      val sink = TestSink.probe[Either[ErrorMessage, (AttachmentRequestKey, ByteString)]]

      val (pub, sub) = source.via(flow.getAttachmentMaterial).toMat(sink)(Keep.both).run()
      pub
        .sendNext(Right(AttachmentRequestKey(apiKey, validAttachmentRequest(attachmentId, submissionId))))
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()

      result.isLeft shouldBe true
      result.left.map { error =>
        error.code shouldBe BadRequest
      }
    }

    "incorrect attachment checksum" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[Either[ErrorMessage, (AttachmentRequestKey, ByteString)]]
      val sink = TestSink.probe[Either[ErrorMessage, (AttachmentRequestKey, ByteString)]]

      val (pub, sub) = source.via(flow.validateAttachmentChecksum).toMat(sink)(Keep.both).run()
      pub
        .sendNext(
          Right(
            AttachmentRequestKey(apiKey, validAttachmentRequest(attachmentId, submissionId, "wrong-checksum")),
            ByteString(sampleAttachment)))
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()

      result.isLeft shouldBe true
      result.left.map { error =>
        error.code.intValue() shouldBe 419
      }
    }

    "transient store failure for putting attachment" in {
      val attachmentId = UUID.randomUUID().toString
      val submissionId = UUID.randomUUID().toString
      val source = TestSource.probe[Either[ErrorMessage, (AttachmentRequestKey, ByteString)]]
      val sink = TestSink.probe[Either[ErrorMessage, AttachmentRequestKey]]

      val (pub, sub) = source.via(flow.putAttachmentForProcessing).toMat(sink)(Keep.both).run()
      pub
        .sendNext(Right(AttachmentRequestKey(apiKey, validAttachmentRequest(attachmentId, submissionId)), ByteString(sampleAttachment)))
        .sendComplete()
      val result = sub
        .request(1)
        .expectNext()

      result.isLeft shouldBe true
      result.left.map { error =>
        error.code shouldBe InternalServerError
      }
    }
  }
}
