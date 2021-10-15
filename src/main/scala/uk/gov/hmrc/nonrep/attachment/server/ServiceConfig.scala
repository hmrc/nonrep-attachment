package uk.gov.hmrc.nonrep.attachment
package server

import java.net.URI

class ServiceConfig(val servicePort: Int = 8000) {

  val appName = "attachment"
  val port: Int = sys.env.get("REST_PORT").map(_.toInt).getOrElse(servicePort)
  val env: String = sys.env.getOrElse("ENV", "local")

  val elasticSearchUri: URI = URI.create(sys.env.getOrElse("ELASTICSEARCH", "http://elasticsearch.nrs"))
  val isElasticSearchProtocolSecure: Boolean = elasticSearchUri.toURL.getProtocol == "https"
  val elasticSearchHost: String = elasticSearchUri.getHost

}
