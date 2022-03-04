package uk.gov.hmrc.nonrep.attachment
package service

import java.io.ByteArrayOutputStream
import java.util.zip.{ZipEntry, ZipOutputStream}

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.StatusCodes.{BadRequest, InternalServerError}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.alpakka.s3.{MetaHeaders, MultipartUploadResult}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import spray.json._
import uk.gov.hmrc.nonrep.attachment.models.AttachmentRequestKey
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig
import uk.gov.hmrc.nonrep.attachment.utils.CryptoUtils
import uk.gov.hmrc.nonrep.attachment.utils.JsonFormats._

import scala.concurrent.Future
import scala.util.Try

trait Storage[A] extends Service[A] {
  def createBundle(data: A, file: ByteString)(implicit system: ActorSystem[_], config: ServiceConfig): ByteString
  def uploadBundle(data: A, file: ByteString)(implicit system: ActorSystem[_], config: ServiceConfig): Future[EitherErr[A]]
}

class StorageService extends Storage[AttachmentRequestKey] {
  import Storage._

  def uploadSink(attachmentId: String, checksum: String)(
    implicit system: ActorSystem[_],
    config: ServiceConfig): Sink[ByteString, Future[MultipartUploadResult]] =
    S3.multipartUpload(config.attachmentsBucket, s"$attachmentId.zip", metaHeaders = MetaHeaders(Map("Content-MD5" -> checksum)))

  override def createBundle(data: AttachmentRequestKey, file: ByteString)(
    implicit system: ActorSystem[_],
    config: ServiceConfig): ByteString = {
    val bytes = new ByteArrayOutputStream()
    val zip = new ZipOutputStream(bytes)
    zip.putNextEntry(new ZipEntry(METADATA_FILE))
    zip.write(data.request.toJson.prettyPrint.getBytes("utf-8"))
    zip.closeEntry()
    zip.putNextEntry(new ZipEntry(ATTACHMENT_FILE))
    zip.write(file.toArray[Byte])
    zip.closeEntry()
    zip.close()
    ByteString(bytes.toByteArray)
  }

  override def uploadBundle(attachment: AttachmentRequestKey, file: ByteString)(
    implicit system: ActorSystem[_],
    config: ServiceConfig): Future[EitherErr[AttachmentRequestKey]] = {
    implicit val ec = system.executionContext
    /**
      * https://docs.aws.amazon.com/AmazonS3/latest/userguide/checking-object-integrity.html
      */
    Source
      .single(createBundle(attachment, file))
      .runWith(uploadSink(attachment.request.attachmentId, file.toArray[Byte].calculateMD5))
      .filter(_.bucket == config.attachmentsBucket)
      .filter(_.key == s"${attachment.request.attachmentId}.zip")
      .filter(!_.eTag.isEmpty)
      .map(_ => Right(attachment))
      .recover {
        case e: Exception =>
          system.log.error(s"Error [${e.getMessage}] received from S3 downstream service, with exception cause: [${e.getCause}]")
          val error = ErrorMessage(s"Error '${e.getMessage}' received from S3 downstream service", InternalServerError)
          Left(error)
      }
  }

  override def request(data: EitherErr[AttachmentRequestKey])(implicit config: ServiceConfig, system: ActorSystem[_]): HttpRequest =
    data
      .map { attachment =>
        val request = Get(Uri(attachment.request.attachmentUrl))
        system.log.info(s"Storage request for attachmentRequestKey: [$data] and attachmentUrl: [${attachment.request.attachmentUrl}]")
        request
      }
      .toOption
      .getOrElse(throw new RuntimeException("Error creating S3 upstream request"))

  override def call()(implicit system: ActorSystem[_], config: ServiceConfig)
    : Flow[(HttpRequest, EitherErr[AttachmentRequestKey]), (Try[HttpResponse], EitherErr[AttachmentRequestKey]), Any] =
    Http().superPool[EitherErr[AttachmentRequestKey]]()

  override def response(request: EitherErr[AttachmentRequestKey], response: HttpResponse)(
    implicit system: ActorSystem[_]): Future[EitherErr[(AttachmentRequestKey, ByteString)]] =
    if (response.status == StatusCodes.OK) {
      import system.executionContext
      response.entity.dataBytes
        .runFold(ByteString.empty)(_ ++ _)
        .map(bytes => request.map(attachment => (attachment, bytes)))
    } else {
      system.log.error(s"Response status [${response.status}] received from S3 upstream service. Full response is: [$response]")
      val error = ErrorMessage(s"Response status '${response.status}' received from S3 upstream service", BadRequest)
      response.discardEntityBytes()
      Future.successful(Left(error))
    }
}

object Storage {

  implicit val defaultStorageService: StorageService = new StorageService()

  implicit class ArrayOfBytesWithSha256(input: Array[Byte]) {
    def calculateSha256: String = CryptoUtils.calculateSha256(input)
  }

  implicit class ArrayOfBytesWithMD5(input: Array[Byte]) {
    def calculateMD5: String = CryptoUtils.calculateMD5(input)
  }

}
