package uk.gov.hmrc.nonrep.attachment
package service

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.alpakka.s3.MetaHeaders
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import uk.gov.hmrc.nonrep.attachment.models.AttachmentRequestKey
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig
import uk.gov.hmrc.nonrep.attachment.utils.CryptoUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

trait Storage[A] extends Request[A] with Call[A] with Response[A] {
  def upload(data: A, file: ByteString)(implicit system: ActorSystem[_], config: ServiceConfig): Future[EitherErr[A]]
}

class StorageService extends Storage[AttachmentRequestKey] {
  import Storage._

  override def upload(attachment: AttachmentRequestKey, file: ByteString)(
    implicit system: ActorSystem[_],
    config: ServiceConfig): Future[EitherErr[AttachmentRequestKey]] =
    Source
      .single(file)
      .runWith(
        S3.multipartUpload(
            config.attachmentsBucket,
            attachment.request.attachmentId,
            metaHeaders = MetaHeaders(Map("Content-MD5" -> file.toArray[Byte].calculateMD5)))
          .mapMaterializedValue(_.map(_ => Right(attachment).withLeft[ErrorMessage])))

  override def response(request: EitherErr[AttachmentRequestKey], response: HttpResponse)(
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

  override def call()(implicit system: ActorSystem[_], config: ServiceConfig)
    : Flow[(HttpRequest, EitherErr[AttachmentRequestKey]), (Try[HttpResponse], EitherErr[AttachmentRequestKey]), Any] =
    Http().superPool[EitherErr[AttachmentRequestKey]]()

  override def request(data: EitherErr[AttachmentRequestKey])(implicit config: ServiceConfig, system: ActorSystem[_]): HttpRequest =
    data
      .map(attachment => Get(Uri(attachment.request.attachmentUrl)))
      .toOption
      .getOrElse(throw new RuntimeException("Error creating S3 request"))
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
