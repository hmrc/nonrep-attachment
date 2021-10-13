package uk.gov.hmrc.nonrep.attachment
package service

import akka.NotUsed
import akka.stream.scaladsl.Source

trait IndexService[A] {
  def query(data: A): Source[A, NotUsed]
}

//implicit val defaultIndexService: IndexService[SubmissionMetadata]
