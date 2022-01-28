package uk.gov.hmrc.nonrep.attachment

import uk.gov.hmrc.nonrep.attachment.models.ApiKey
import uk.gov.hmrc.nonrep.attachment.server.ServiceConfig

trait TestConfigUtils {
  def notableEventsOrEmpty(config: ServiceConfig, apiKey: ApiKey): Set[String] =
    config.maybeNotableEvents(apiKey).getOrElse(Set.empty)
}
