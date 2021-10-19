package uk.gov.hmrc.nonrep.attachment
package server

import java.net.URI

import akka.stream.TLSRole.client
import com.typesafe.config.{Config, ConfigFactory}
import uk.gov.hmrc.nonrep.attachment.models.AttachmentRequest

import scala.Predef.->

class ServiceConfig(val servicePort: Int = 8000) {

  val projectName = "nonrep"
  val appName = "attachment"
  val port: Int = sys.env.get("REST_PORT").map(_.toInt).getOrElse(servicePort)
  val env: String = sys.env.getOrElse("ENV", "local")

  val elasticSearchUri: URI = URI.create(sys.env.getOrElse("ELASTICSEARCH", "http://elasticsearch.nrs"))
  val isElasticSearchProtocolSecure: Boolean = elasticSearchUri.toURL.getProtocol == "https"
  val elasticSearchHost: String = elasticSearchUri.getHost

  private val configFile = new java.io.File(s"/etc/config/CONFIG_FILE")

  val config = if(configFile.exists()) {
    ConfigFactory.parseFile(configFile)
  } else {
    ConfigFactory.load("application.conf")
  }

  private val clientsConfig: Config = config.getConfig(s"$projectName-$appName.clients-config")

  val notableEvents: Map[ApiKey, Set[String]] = Map(clientsConfig => client.ApiKey -> client)

}
