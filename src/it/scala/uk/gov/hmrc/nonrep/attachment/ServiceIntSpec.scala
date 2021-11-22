package uk.gov.hmrc.nonrep.attachment

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.StatusCodes.{Accepted, BadRequest, OK}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.Inside
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import spray.json._
import uk.gov.hmrc.nonrep.BuildInfo
import uk.gov.hmrc.nonrep.attachment.models.AttachmentResponse
import uk.gov.hmrc.nonrep.attachment.server.{NonrepMicroservice, Routes, ServiceConfig}
import uk.gov.hmrc.nonrep.attachment.utils.JsonFormats._

import java.util.UUID
import scala.concurrent.Future

class ServiceIntSpec extends BaseSpec with ScalatestRouteTest with ScalaFutures with Inside {

  import TestServices._

  private lazy val testKit = ActorTestKit()
  private implicit val typedSystem: ActorSystem[Nothing] = testKit.system
  private implicit val config: ServiceConfig = new ServiceConfig(servicePort = 9000)

  private val routes = Routes(success.flow)
  private lazy val service: NonrepMicroservice = NonrepMicroservice(routes)(typedSystem, config)

  private val hostUrl = s"http://localhost:${config.port}"

  override def createActorSystem(): akka.actor.ActorSystem = testKit.system.toClassic

  implicit val patience: PatienceConfig = PatienceConfig(Span(5000, Millis), Span(100, Millis))

  private def initializeService(): Unit = service

  override def beforeAll(): Unit = initializeService()

  override def afterAll(): Unit =
    whenReady(service.serverBinding) {
      _.unbind()
    }

  private val apiKeyHeader = RawHeader("x-api-Key", "66975df1e55c4bb9c7dcb4313e5514c234f071b1199efd455695fefb3e54bbf2")

  private val versionRequest = HttpRequest(uri = s"$hostUrl/${config.appName}/version")
  private val metricsRequest = HttpRequest(uri = s"$hostUrl/metrics")
  private val metricPrefix = "# TYPE"
  private val serviceMetricPrefix = s"$metricPrefix attachment"

  private def metricsFrom(body: String) = body.split('\n').filter(_.startsWith(metricPrefix))

  "attachment service service" should {

    "return version information for GET request to service /version endpoint" in {
      whenReady(Http().singleRequest(versionRequest)) { res =>
        res.status shouldBe OK
        whenReady(entityToString(res.entity)) { body =>
          body shouldBe buildVersionJsonFormat.write(BuildVersion(version = BuildInfo.version)).toString
        }
      }
    }

    "return a 'pong' response for GET requests to service /ping endpoint" in {
      val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = s"$hostUrl/${config.appName}/ping"))
      whenReady(responseFuture) { res =>
        res.status shouldBe OK
        whenReady(entityToString(res.entity)) { body =>
          body shouldBe "pong"
        }
      }
    }

    "return a 'pong' response for GET requests to /ping endpoint" in {
      val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = s"$hostUrl/ping"))
      whenReady(responseFuture) { res =>
        res.status shouldBe OK
        whenReady(entityToString(res.entity)) { body =>
          body shouldBe "pong"
        }
      }
    }

    "return 202 status code for POST request to /attachment endpoint" in {
      val attachmentId = UUID.randomUUID().toString
      val request = HttpRequest(POST, uri = s"$hostUrl/attachment")
        .withEntity(`application/json`, validAttachmentRequestJson(attachmentId))
        .withHeaders(apiKeyHeader)

      val responseFuture: Future[HttpResponse] = Http().singleRequest(request)
      whenReady(responseFuture) { res =>
        res.status shouldBe Accepted
        whenReady(entityToString(res.entity)) { body =>
          body shouldBe AttachmentResponse(attachmentId).toJson.toString
        }
      }
    }

    "return 400 status code for POST request to /attachment with lack of attachments data in meta-store" in {
      val request = HttpRequest(POST, uri = s"$hostUrl/attachment")
        .withHeaders(apiKeyHeader)
        .withEntity(`application/json`, invalidAttachmentRequestJson)

      val responseFuture: Future[HttpResponse] = Http().singleRequest(request)
      whenReady(responseFuture) { res =>
        res.status shouldBe BadRequest
      }
    }

    "return jvm metrics" in {
      whenReady(Http().singleRequest(metricsRequest)) { response =>
        response.status shouldBe OK

        whenReady(entityToString(response.entity)) { body =>
          val jvmMetrics = metricsFrom(body).filterNot(_.startsWith(serviceMetricPrefix))

          //jvmMetrics can vary slightly between executions
          jvmMetrics should contain allElementsOf Seq(
            s"$metricPrefix jvm_memory_objects_pending_finalization gauge",
            s"$metricPrefix jvm_memory_bytes_used gauge",
            s"$metricPrefix jvm_memory_bytes_committed gauge",
            s"$metricPrefix jvm_memory_bytes_max gauge",
            s"$metricPrefix jvm_memory_bytes_init gauge",
            s"$metricPrefix jvm_memory_pool_bytes_used gauge",
            s"$metricPrefix jvm_memory_pool_bytes_committed gauge",
            s"$metricPrefix jvm_memory_pool_bytes_max gauge",
            s"$metricPrefix jvm_memory_pool_bytes_init gauge",
            s"$metricPrefix jvm_memory_pool_collection_used_bytes gauge",
            s"$metricPrefix jvm_memory_pool_collection_committed_bytes gauge",
            s"$metricPrefix jvm_memory_pool_collection_max_bytes gauge",
            s"$metricPrefix jvm_memory_pool_collection_init_bytes gauge",
            s"$metricPrefix jvm_gc_collection_seconds summary",
            s"$metricPrefix jvm_memory_heap_committed gauge",
            s"$metricPrefix jvm_memory_non_heap_used gauge",
            s"$metricPrefix jvm_memory_pools_Compressed_Class_Space_usage gauge",
            s"$metricPrefix jvm_threads_waiting_count gauge",
            s"$metricPrefix jvm_memory_total_committed gauge",
            s"$metricPrefix jvm_memory_heap_usage gauge",
            s"$metricPrefix jvm_attribute_uptime gauge",
            s"$metricPrefix jvm_memory_total_used gauge",
            s"$metricPrefix jvm_threads_timed_waiting_count gauge",
            s"$metricPrefix jvm_memory_heap_used gauge",
            s"$metricPrefix jvm_memory_non_heap_committed gauge",
            s"$metricPrefix jvm_memory_non_heap_usage gauge",
            s"$metricPrefix jvm_memory_heap_init gauge",
            s"$metricPrefix jvm_memory_pools_Metaspace_usage gauge",
            s"$metricPrefix jvm_threads_count gauge",
            s"$metricPrefix jvm_threads_new_count gauge",
            s"$metricPrefix jvm_memory_non_heap_init gauge",
            s"$metricPrefix jvm_memory_total_max gauge",
            s"$metricPrefix jvm_threads_runnable_count gauge",
            s"$metricPrefix jvm_threads_terminated_count gauge",
            s"$metricPrefix jvm_memory_heap_max gauge",
            s"$metricPrefix jvm_memory_non_heap_max gauge",
            s"$metricPrefix jvm_memory_total_init gauge",
            s"$metricPrefix jvm_threads_daemon_count gauge",
            s"$metricPrefix jvm_threads_blocked_count gauge",
            s"$metricPrefix jvm_buffer_pool_used_bytes gauge",
            s"$metricPrefix jvm_buffer_pool_capacity_bytes gauge",
            s"$metricPrefix jvm_buffer_pool_used_buffers gauge",
            s"$metricPrefix jvm_classes_loaded gauge",
            s"$metricPrefix jvm_classes_loaded_total counter",
            s"$metricPrefix jvm_classes_unloaded_total counter",
            s"$metricPrefix jvm_memory_pool_allocated_bytes_total counter",
            s"$metricPrefix jvm_threads_current gauge",
            s"$metricPrefix jvm_threads_daemon gauge",
            s"$metricPrefix jvm_threads_peak gauge",
            s"$metricPrefix jvm_threads_started_total counter",
            s"$metricPrefix jvm_threads_deadlocked gauge",
            s"$metricPrefix jvm_threads_deadlocked_monitor gauge",
            s"$metricPrefix jvm_threads_state gauge",
            s"$metricPrefix process_cpu_seconds_total counter",
            s"$metricPrefix process_start_time_seconds gauge",
            s"$metricPrefix process_open_fds gauge",
            s"$metricPrefix process_max_fds gauge",
            s"$metricPrefix jvm_info gauge"
          )
        }
      }
    }

    "return implicit and explicit service metrics" when {
      "a service request has been made" in {
        whenReady(Http().singleRequest(versionRequest)) { versionResponse =>
          versionResponse.status shouldBe OK

          whenReady(Http().singleRequest(metricsRequest)) { metricsResponse =>
            metricsResponse.status shouldBe OK

            whenReady(entityToString(metricsResponse.entity)) { body: String =>
              val serviceMetrics = metricsFrom(body).filter(_.startsWith(serviceMetricPrefix))

              serviceMetrics.toSet shouldBe Set(
                s"${serviceMetricPrefix}_success_created gauge",
                s"${serviceMetricPrefix}_success_total counter",
                s"${serviceMetricPrefix}_failure_created gauge",
                s"${serviceMetricPrefix}_failure_total counter",
//                s"${serviceMetricPrefix}_response_times histogram",
//                s"${serviceMetricPrefix}_elastic_search_query_response_times histogram",
                s"${serviceMetricPrefix}_requests_active gauge",
                s"${serviceMetricPrefix}_requests_created gauge",
                s"${serviceMetricPrefix}_requests_size_bytes_created gauge",
                s"${serviceMetricPrefix}_requests_size_bytes summary",
                s"${serviceMetricPrefix}_requests_total counter",
                s"${serviceMetricPrefix}_responses_created gauge",
                s"${serviceMetricPrefix}_responses_duration_seconds histogram",
                s"${serviceMetricPrefix}_responses_duration_seconds_created gauge",
                s"${serviceMetricPrefix}_responses_size_bytes summary",
                s"${serviceMetricPrefix}_responses_size_bytes_created gauge",
                s"${serviceMetricPrefix}_responses_total counter"
              )
            }
          }
        }
      }
    }
  }
}
