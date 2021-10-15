package uk.gov.hmrc.nonrep.attachment
package service

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpEntity.LastChunk.data
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{Flow, Source}
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig

import scala.util.Try

trait Indexing[A] {
  def query(data: A): Source[A, NotUsed]

  def flow()(implicit system: ActorSystem, config: ServiceConfig)
  :Flow[(HttpRequest, EitherErr[A]), (Try[HttpResponse], EitherErr[A]), Any]
}
object Indexing {
  def apply[A](implicit service: Indexing[A]): Indexing[A] = service

  object ops {

    implicit class IndexingOps[A: Indexing](value: EitherErr[A]) {
      def query()(implicit config: ServiceConfig): HttpRequest = Indexing[A].query(data: A): Source[A, NotUsed]

      def flow()(implicit system: ActorSystem, config: ServiceConfig)
      : Flow[(HttpRequest, EitherErr[A]), (Try[HttpResponse], EitherErr[A]), Any] =
        Indexing[A].flow()
  }
}
  //not sure if this will be needed and how we connect it
//    implicit val defaultIndexing: Indexing[SubmissionMetadata] = new Indexing[SubmissionMetadata]() {
//      override def query(value: EitherErr[SubmissionMetadata])(implicit config: ServiceConfig): HttpRequest = {
//        value.toOption.flatMap {
//          submission => {
//            val metadata = submission.request.metadata
//            metadata.nrSubmissionId.zip(metadata.attachmentIds).map {
//              case (nrSubmissionId, attachmentIds) =>
//                val path = buildPath(metadata.notableEvent, nrSubmissionId)
//                val body = s"""{ "attachmentIds": ${attachmentIds.map(id => s"\"$id\"").mkString("[", ",", "]")} }"""
//                createSignedRequest(HttpMethods.POST, config.elasticSearchUri, path, body)
//            }
//          }
//        }.getOrElse(HttpRequest())
//      }
//    }
}