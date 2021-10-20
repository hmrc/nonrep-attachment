package uk.gov.hmrc.nonrep.attachment

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.ResponseEntity
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future}

object TestServices {
  lazy val testKit: ActorTestKit = ActorTestKit()

  def entityToString(entity: ResponseEntity)(implicit ec: ExecutionContext): Future[String] = {
    implicit val typedSystem: ActorSystem[Nothing] = testKit.system

    entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)
  }

  object success {

  }

  object failure {

  }
}
