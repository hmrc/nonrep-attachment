package uk.gov.hmrc.nonrep.attachment
package stream


import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.impl.CancelledSubscription.request
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import uk.gov.hmrc.nonrep.attachment.TestServices.testKit
import uk.gov.hmrc.nonrep.attachment.models.{AttachmentRequest, AttachmentRequestKey}
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig

class AttachmentFlowSpec extends BaseSpec with ScalatestRouteTest {

  private implicit val typedSystem: ActorSystem[Nothing] = testKit.system
  private val config = new ServiceConfig()

  private val ApiKey = ApiKey("x-api-Key", "66975df1e55c4bb9c7dcb4313e5514c234f071b1199efd455695fefb3e54bbf2")


  "attachments flow" should {
    import TestServices.success._

    "return error for invalid request" in {
      val source = TestSource.probe[AttachmentRequest]
      val sink = TestSink.probe[EitherErr[HttpResponse]]
      val (pub, sub) = source.via(flow.validation).via(flow.attachment).toMat(sink)(Keep.both).run()
      pub
        .sendNext(AttachmentRequest(ApiKey, invalidAttachmentRequest))
        .sendComplete()
      sub
        .request(1)
        .expectNextPF {
                case Left(NonEmptyList(em, Nil)) => em.asInstanceOf[ErrorMessage].message
              } shouldBe "Searching attachments index error"
    }
    "return attachment id for valid request" in {
      val request = validAttachmentRequest(nrSubmissionId = List.fill(config.maxNoOfAttachments)(UUIDService.ops.generateUUID))
      val source = TestSource.probe[AttachmentRequest]
      val sink = TestSink.probe[EitherErr[HttpResponse]]
      val (pub, sub) = source.via(flow.validateAttachmentRequest).via(flow.validation.toMat(sink)(Keep.both).run()
      pub
        .sendNext(AttachmentRequest(ApiKey, validAttachmentRequest())
        .sendComplete()
      sub
        .request(1)
        .expectNext()
        .isRight shouldBe true
    }
  }
}
