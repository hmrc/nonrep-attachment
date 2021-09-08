package uk.gov.hmrc.nonrep.attachment
package server

class ServiceConfig(val servicePort: Int = 8000) {

  val appName = "attachment"
  val port: Int = sys.env.get("REST_PORT").map(_.toInt).getOrElse(servicePort)
  val env: String = sys.env.getOrElse("ENV", "local")

}
