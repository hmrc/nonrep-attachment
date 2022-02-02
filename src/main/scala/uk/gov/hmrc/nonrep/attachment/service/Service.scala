package uk.gov.hmrc.nonrep.attachment
package service

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig

import scala.concurrent.Future
import scala.util.Try

trait Service[A] {
  def request(data: EitherErr[A])(implicit config: ServiceConfig, system: ActorSystem[_]): HttpRequest

  def call()(
    implicit system: ActorSystem[_],
    config: ServiceConfig): Flow[(HttpRequest, EitherErr[A]), (Try[HttpResponse], EitherErr[A]), Any]

  def response(value: EitherErr[A], response: HttpResponse)(implicit system: ActorSystem[_]): Future[EitherErr[(A, ByteString)]]
}
