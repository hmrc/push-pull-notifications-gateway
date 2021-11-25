package uk.gov.hmrc.pushpullnotificationsgateway.support

import scala.collection.JavaConverters
import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import org.scalatest.Suite
import org.scalatest.matchers.should.Matchers
import play.api.Application

@SuppressWarnings(Array(
  "scalafix:DisableSyntax.var"
))
trait MetricsTestSupport {
  self: Suite with Matchers =>

  def app: Application

  private var metricsRegistry: MetricRegistry = _


  def givenCleanMetricRegistry(): Unit = {
    val registry = app.injector.instanceOf[Metrics].defaultRegistry
    for (metric <- JavaConverters
                    .asScalaIterator[String](registry.getMetrics.keySet().iterator())) {
      registry.remove(metric)
    }
    metricsRegistry = registry
  }

  def verifyTimerExistsAndBeenUpdated(metric: String): Unit = {
    val timers = metricsRegistry.getTimers
    val metrics = Option(timers.get(s"Timer-$metric"))
    if (metrics.isEmpty) {
      throw new Exception(s"Metric [$metric] not found, try one of ${timers.keySet()}")
    }
    (metrics.get.getCount) should be >=(1L)
  }

}
