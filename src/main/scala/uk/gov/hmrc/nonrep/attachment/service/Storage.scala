package uk.gov.hmrc.nonrep.attachment
package service

import java.security.MessageDigest

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import org.apache.commons.codec.digest.DigestUtils
import uk.gov.hmrc.nonrep.attachment.models.AttachmentRequestKey
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

trait Storage[A] {
  def s3Call(implicit system: ActorSystem[_])
  : Flow[(HttpRequest, EitherErr[A]), (Try[HttpResponse], EitherErr[A]), Any]

  def s3Request(data: EitherErr[A]): HttpRequest

  def s3Response(request: EitherErr[A], response: HttpResponse)(
    implicit system: ActorSystem[_]): Future[EitherErr[(A, ByteString)]]

  def putS3Object(attachment: A, file: ByteString)
                 (implicit system: ActorSystem[_], config: ServiceConfig): Future[EitherErr[A]]
}

class StorageService extends Storage[AttachmentRequestKey] {
  import Storage._

  def s3Call(implicit system: ActorSystem[_])
  : Flow[(HttpRequest, EitherErr[AttachmentRequestKey]), (Try[HttpResponse], EitherErr[AttachmentRequestKey]), Any] =
    Http().superPool[EitherErr[AttachmentRequestKey]]()

  def s3Request(data: EitherErr[AttachmentRequestKey]): HttpRequest =
    data.map(attachment => Get(Uri(attachment.request.attachmentUrl))).toOption.getOrElse(HttpRequest())

  def s3Response(request: EitherErr[AttachmentRequestKey], response: HttpResponse)(
    implicit system: ActorSystem[_]): Future[EitherErr[(AttachmentRequestKey, ByteString)]] =
    if (response.status == StatusCodes.OK) {
      import system.executionContext
      response.entity.dataBytes
        .runFold(ByteString.empty)(_ ++ _)
        .map(bytes => request.map(attachment => (attachment, bytes)))
    } else {
      system.log.error(s"Response status ${response.status} received rom ES server. Full response is: [$response]")
      val error = ErrorMessage(s"Response status ${response.status} from ES server", BadRequest)
      response.discardEntityBytes()
      Future.successful(Left(error))
    }

  def putS3Object(attachment: AttachmentRequestKey, file: ByteString)
                 (implicit system: ActorSystem[_], config: ServiceConfig): Future[EitherErr[AttachmentRequestKey]] = Source
    .single(file)
    .runWith(S3
      .multipartUpload(config.attachmentsBucket, attachment.request.attachmentId)
      .mapMaterializedValue(_.filter(_.eTag == file.toArray[Byte].calculateMD5)
        .map(_ => Right(attachment).withLeft[ErrorMessage])))
}

object Storage {

  implicit val defaultStorageService: StorageService = new StorageService()

  implicit class ArrayOfBytesWithSha256(input: Array[Byte]) {
    def calculateSha256: String = MessageDigest.getInstance("SHA-256").digest(input).map("%02x".format(_)).mkString
  }

  implicit class ArrayOfBytesWithMD5(input: Array[Byte]) {
    def calculateMD5: String = DigestUtils.md5(input).mkString
  }

}
