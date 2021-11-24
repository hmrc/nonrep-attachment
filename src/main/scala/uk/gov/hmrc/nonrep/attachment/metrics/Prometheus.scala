package uk.gov.hmrc.nonrep.attachment.metrics

import com.codahale.metrics.jvm.{GarbageCollectorMetricSet, MemoryUsageGaugeSet, ThreadStatesGaugeSet}
import com.codahale.metrics.{JvmAttributeGaugeSet, SharedMetricRegistries}
import fr.davit.akka.http.metrics.prometheus.{Buckets, PrometheusRegistry, PrometheusSettings, Quantiles}
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.dropwizard.DropwizardExports
import io.prometheus.client.hotspot.DefaultExports
import uk.gov.hmrc.nonrep.attachment.server.Main.config
import io.prometheus.client.CollectorRegistry.defaultRegistry
import io.prometheus.client.{Counter, Histogram}

object Prometheus {

  private val prometheus = CollectorRegistry.defaultRegistry

  private val settings = PrometheusSettings.default
    .withNamespace(config.appName)
    .withIncludePathDimension(true)
    .withIncludeMethodDimension(true)
    .withIncludeStatusDimension(true)
    .withDurationConfig(Buckets(.1, .2, .3, .5, .8, 1, 1.5, 2, 2.5, 3, 5, 8, 13, 21))
    .withReceivedBytesConfig(Quantiles(0.5, 0.75, 0.9, 0.95, 0.99))
    .withSentBytesConfig(PrometheusSettings.DefaultQuantiles)
    .withDefineError(_.status.isFailure)

  val registry: PrometheusRegistry = {
    DefaultExports.initialize()
    val registry = SharedMetricRegistries.getOrCreate(config.appName)
    registry.register("jvm.attribute", new JvmAttributeGaugeSet())
    registry.register("jvm.gc", new GarbageCollectorMetricSet())
    registry.register("jvm.memory", new MemoryUsageGaugeSet())
    registry.register("jvm.threads", new ThreadStatesGaugeSet())
    prometheus.register(new DropwizardExports(registry))
    PrometheusRegistry(prometheus, settings)
  }

  private val defaultHistogramBuckets =
    List(.1, .2, .3, .4, .5, .6, .7, .8, .9, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0, 20.0)

  val esDuration: Histogram = Histogram
    .build()
    .name(s"${config.appName}_es_requests_processing_time")
    .help("Time spent processing ES requests")
    .labelNames("es_requests_processing")
    .buckets(defaultHistogramBuckets: _*)
    .register()

  val esCounter: Counter = Counter
    .build()
    .name(s"${config.appName}_es_requests_total")
    .help("Total number of ES requests")
    .labelNames("status_code")
    .register()

}
