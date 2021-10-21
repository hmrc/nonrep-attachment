package uk.gov.hmrc.nonrep.attachment
package stream


import java.util.UUID

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.nonrep.attachment.TestServices.testKit
import uk.gov.hmrc.nonrep.attachment.models.{AttachmentRequest, AttachmentRequestKey}
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig

class AttachmentFlowSpec extends BaseSpec with ScalaFutures with ScalatestRouteTest {

  private implicit val typedSystem: ActorSystem[Nothing] = testKit.system
  private val config = new ServiceConfig()

  "attachments flow" should {
    import TestServices._
    import TestServices.success._

    "validate incoming request" in {
      val source = TestSource.probe[AttachmentRequestKey]
      val sink = TestSink.probe[EitherErr[AttachmentRequestKey]]
      val (pub, sub) = source.via(flow.validateRequest).toMat(sink)(Keep.both).run()
      pub
        .sendNext(AttachmentRequestKey(apiKey, validAttachmentRequest()))
        .sendComplete()
      sub
        .request(1)
        .expectNext().isRight shouldBe true
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
        res should include (attachmentId)
        res should include (submissionId)
      }
    }

    "parse ES response" in {

    }

    "remap AttachmentRequestKey case class into AttachmentRequest" in {

    }

    "validate attachments flow" in {

    }

  }
}
