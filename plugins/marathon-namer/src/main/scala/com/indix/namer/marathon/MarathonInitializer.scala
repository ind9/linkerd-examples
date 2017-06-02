package com.indix.namer.marathon

import com.fasterxml.jackson.annotation.JsonIgnore
import com.twitter.conversions.time._
import com.twitter.finagle._
import com.twitter.finagle.param.Label
import com.twitter.finagle.tracing.NullTracer
import io.buoyant.config.types.Port
import com.twitter.util.{NonFatal => _, _}
import scala.util.control.NonFatal
import io.buoyant.namer.{NamerConfig, NamerInitializer}

/**
  * Supports namer configurations in the form:
  *
  * <pre>
  * namers:
  * - kind:           com.indix.namer.marathon.marathonHostNamer
  *   prefix:         /com.indix.namer.marathon.marathonHostNamer
  *   host:           marathon.mesos
  *   port:           80
  *   uriPrefix:      /marathon
  *   ttlMs:          5000
  *   useHealthCheck: false
  * </pre>
  */
class MarathonInitializer extends NamerInitializer {
  val configClass = classOf[MarathonConfig]
  override def configId = "com.indix.namer.marathon.marathonHostNamer"
}

object MarathonInitializer extends MarathonInitializer

object MarathonConfig {
  private val DefaultHost = "marathon.mesos"
  private val DefaultPrefix = Path.read("/io.l5d.marathon")

  private case class SetHost(host: String) extends SimpleFilter[http.Request, http.Response] {
    def apply(req: http.Request, service: Service[http.Request, http.Response]) = {
      req.host = host
      service(req)
    }
  }

}

case class MarathonConfig(
                           host: Option[String],
                           port: Option[Port],
                           dst: Option[String],
                           uriPrefix: Option[String],
                           ttlMs: Option[Int],
                           useHealthCheck: Option[Boolean]
                         ) extends NamerConfig {
  import MarathonConfig._

  @JsonIgnore
  override def defaultPrefix: Path = DefaultPrefix

  override def newNamer(params: Stack.Params) = {
    val host0 = host.getOrElse(DefaultHost)
    val port0 = port.map(_.port).getOrElse(80)
    val dst0 = dst.getOrElse(s"/$$/inet/$host0/$port0")

    val service = Http.client
      .withParams(params)
      .configured(Label("namer" + prefix.show))
      .withTracer(NullTracer)
      .filtered(SetHost(host0))
      .newService(dst0)

    val uriPrefix0 = uriPrefix.getOrElse("")
    val useHealthCheck0 = useHealthCheck.getOrElse(false)
    val api = Api(service, uriPrefix0, useHealthCheck0)
    val ttl = ttlMs.getOrElse(5000).millis

    new AppHostNamer(api, prefix, ttl)
  }
}
