package uk.gov.hmrc.nonrep.attachment
package service

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig

import scala.concurrent.Future
import scala.util.Try

trait Request[A] {
  def request(data: EitherErr[A])(implicit config: ServiceConfig, system: ActorSystem[_]): HttpRequest
}

trait Call[A] {
  def call()(
    implicit system: ActorSystem[_],
    config: ServiceConfig): Flow[(HttpRequest, EitherErr[A]), (Try[HttpResponse], EitherErr[A]), Any]
}

trait Response[A] {
  def response(value: EitherErr[A], response: HttpResponse)(implicit system: ActorSystem[_]): Future[EitherErr[(A, ByteString)]]
}
