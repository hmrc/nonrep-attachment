package uk.gov.hmrc.nonrep.attachment
package service

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, FileInputStream}
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipInputStream

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import spray.json._
import uk.gov.hmrc.nonrep.attachment.models.{AttachmentRequest, AttachmentRequestKey}
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig
import uk.gov.hmrc.nonrep.attachment.utils.JsonFormats._

class StorageSpec extends BaseSpec with ScalaFutures with ScalatestRouteTest {

  private implicit val config: ServiceConfig = new ServiceConfig()
  private lazy val testKit = ActorTestKit()
  private implicit val typedSystem: ActorSystem[Nothing] = testKit.system

  "For attachment api storage service" should {

    val attachmentId = UUID.randomUUID().toString
    val submissionId = UUID.randomUUID().toString
    val request = AttachmentRequest("", attachmentId, "", "", submissionId)
    val attachmentRequestKey = AttachmentRequestKey(apiKey, request)
    val attachmentRequest: EitherErr[AttachmentRequestKey] = Right(attachmentRequestKey)

    "create a bundle with metadata and attachment inside a zip file" in {

      val storage = TestServices.success.successfulStorage

      val result = storage.createBundle(attachmentRequestKey, ByteString(TestServices.sampleAttachment))
      val zipFile = Files.createTempFile(attachmentId, "zip")
      Files.write(zipFile, result.toArray[Byte])
      val zip = new ZipInputStream(new FileInputStream(zipFile.toFile))

      val content = LazyList
        .continually(zip.getNextEntry)
        .takeWhile(_ != null)
        .filter(!_.isDirectory)
        .foldLeft(Seq[(String, Array[Byte])]())((seq, entry) => {
          val output = new ByteArrayOutputStream()
          zip.transferTo(output)
          seq :+ (entry.getName, output.toByteArray)
        })

      content.find(_._1 == METADATA_FILE) should not be empty
      content.find(_._1 == ATTACHMENT_FILE) should not be empty

      val metadata = new String(content.find(_._1 == METADATA_FILE).head._2, "utf-8").parseJson.convertTo[AttachmentRequest]

      metadata shouldBe request
      content.find(_._1 == ATTACHMENT_FILE).head._2 shouldBe TestServices.sampleAttachment
    }

    "make communication with S3 via request-call-response pattern" in {
      val storage = TestServices.success.successfulStorage

      val httpRequest = storage.request(attachmentRequest)
      val s3Call = storage.call()
      whenReady(Source.single((httpRequest, attachmentRequest)).via(s3Call).toMat(Sink.head)(Keep.right).run()) {
        case (tryResponse, entity) =>
          tryResponse.isSuccess shouldBe true
          entity.toOption.get.request.attachmentId shouldBe attachmentId
          entity.toOption.get.request.nrSubmissionId shouldBe submissionId
          storage.response(attachmentRequest, tryResponse.get).futureValue.isRight shouldBe true
      }
    }

    "fail on S3 upstream failure" in {
      val storage = TestServices.failure.failingStorage

      val httpRequest = storage.request(attachmentRequest)
      val s3Call = storage.call()
      whenReady(Source.single((httpRequest, attachmentRequest)).via(s3Call).toMat(Sink.head)(Keep.right).run()) {
        case (tryResponse, _) =>
          tryResponse.isSuccess shouldBe true
          tryResponse.get.status shouldBe StatusCodes.InternalServerError
      }
    }
  }
}
