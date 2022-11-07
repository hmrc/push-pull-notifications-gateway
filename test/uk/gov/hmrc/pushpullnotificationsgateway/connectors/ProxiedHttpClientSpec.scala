/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.pushpullnotificationsgateway.connectors

import akka.actor.ActorSystem
import org.scalatest.OptionValues
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.{Application, Configuration}
import uk.gov.hmrc.play.audit.http.HttpAuditing
import util.HmrcSpec

class ProxiedHttpClientSpec extends HmrcSpec with GuiceOneAppPerSuite with OptionValues {

  private val actorSystem = ActorSystem("test-actor-system")
  val proxyHost = "localhost"
  val proxyPort = 8080

  override lazy val app: Application = GuiceApplicationBuilder()
    .configure(
      "proxy.protocol" -> "http",
             "proxy.host" -> proxyHost,
             "proxy.port" -> proxyPort
    )
    .build()

  trait Setup {
    val url = "http://example.com"
    val config: Configuration = app.injector.instanceOf[Configuration]
    val httpAuditing: HttpAuditing = app.injector.instanceOf[HttpAuditing]
    val wsClient: WSClient = app.injector.instanceOf[WSClient]

    val underTest = new ProxiedHttpClient(config, httpAuditing, wsClient, actorSystem)
  }

  "buildRequest" should {
    "build request with the configured proxy" in new Setup {
      val result: WSRequest = underTest.buildRequest(url, Seq.empty)

      val proxyServer = result.proxyServer.value
      proxyServer.host shouldBe proxyHost
      proxyServer.port shouldBe proxyPort
    }
  }
}
