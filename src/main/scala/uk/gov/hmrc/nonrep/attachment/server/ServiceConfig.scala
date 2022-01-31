package uk.gov.hmrc.nonrep.attachment
package server

import com.typesafe.config.{Config, ConfigFactory}
import uk.gov.hmrc.nonrep.attachment.models.ApiKey

import java.net.URI
import scala.jdk.CollectionConverters._

class ServiceConfig(val servicePort: Int = 8000) {

  val projectName = "nonrep"
  val appName = "attachment"
  val port: Int = sys.env.get("REST_PORT").map(_.toInt).getOrElse(servicePort)
  val env: String = sys.env.getOrElse("ENV", "local")

  val elasticSearchUri: URI = URI.create(sys.env.getOrElse("ELASTICSEARCH", "http://elasticsearch.nrs"))
  val isElasticSearchProtocolSecure: Boolean = elasticSearchUri.toURL.getProtocol == "https"
  val elasticSearchHost: String = elasticSearchUri.getHost

  private val configFile = new java.io.File(s"/etc/config/CONFIG_FILE")

  val config: Config =
    if (configFile.exists()) ConfigFactory.parseFile(configFile)
    else ConfigFactory.load("application.conf")

  private val clientsConfig: Config = config.getConfig(s"$projectName-$appName.clients-config")

  val businessIds: Set[String] = clientsConfig.root().keySet().asScala.toSet
  protected[server] val notableEvents: Map[String, Set[String]] = businessIds.map(c => (clientsConfig.getString(s"$c.apiKey"), clientsConfig.getStringList(s"$c.notableEvents").asScala.toSet)).toMap

  def maybeNotableEvents(apiKey: ApiKey): Option[Set[String]] = notableEvents.get(apiKey.hashedKey)

  val attachmentsBucket = s"$env-nonrep-attachment-data"
}
