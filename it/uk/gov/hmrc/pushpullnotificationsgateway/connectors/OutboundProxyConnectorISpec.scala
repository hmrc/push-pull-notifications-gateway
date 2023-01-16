/*
 * Copyright 2023 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.Mockito
import play.api.Logger
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.pushpullnotificationsgateway.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsgateway.connectors.OutboundProxyConnector.CallbackValidationResponse
import uk.gov.hmrc.pushpullnotificationsgateway.models._

import java.util.regex.Pattern
import scala.concurrent.ExecutionContext.Implicits.global

class OutboundProxyConnectorISpec extends ConnectorSpec {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup {
    val mockAppConfig: AppConfig = mock[AppConfig]
    val mockLogger: Logger       = mock[Logger]

    when(mockAppConfig.allowedHostList).thenReturn(List.empty)
    when(mockAppConfig.useProxy).thenReturn(false)

    val proxiedHttpClient = app.injector.instanceOf[ProxiedHttpClient]
    val httpClient        = app.injector.instanceOf[HttpClient]

    val underTest = new OutboundProxyConnector(mockAppConfig, httpClient, proxiedHttpClient) {
      override lazy val logger: Logger = mockLogger

      override val destinationUrlPattern: Pattern = """.*""".r.pattern
    }
  }

  "postNotification" should {
    val url                                = "/destination"
    val destinationUrl                     = wireMockUrl + url
    val notification: OutboundNotification = OutboundNotification(destinationUrl, List.empty, """{"key": "value"}""")

    "recover NOT_FOUND to return the error code" in new Setup {
      stubFor(
        post(urlEqualTo(url)).willReturn(aResponse().withStatus(NOT_FOUND))
      )

      await(underTest.postNotification(notification)) shouldBe NOT_FOUND

      Mockito.verify(mockLogger).warn(s"Attempted request to $destinationUrl responded with HTTP response code $NOT_FOUND")
    }

    "recover BAD_GATEWAY to return the error code" in new Setup {
      stubFor(
        post(urlEqualTo(url)).willReturn(aResponse().withStatus(BAD_GATEWAY))
      )

      val result: Int = await(underTest.postNotification(notification))

      result shouldBe BAD_GATEWAY
      Mockito.verify(mockLogger).warn(s"Attempted request to $destinationUrl responded with HTTP response code $BAD_GATEWAY")
    }

    "make a successful request when the host matches a host in the list" in new Setup {
      val host = "localhost"
      when(mockAppConfig.allowedHostList).thenReturn(List(host))

      stubFor(
        post(urlEqualTo(url)).willReturn(aResponse().withStatus(OK))
      )

      await(underTest.postNotification(notification)) shouldBe OK
    }

    "fail when the host does not match any of the hosts in the list" in new Setup {
      val host = "localhost"
      when(mockAppConfig.allowedHostList).thenReturn(List(host))

      val exception = intercept[IllegalArgumentException] {
        await(underTest.postNotification(notification.copy(destinationUrl = "https://badexample.com/callback")))
      }
      exception.getMessage shouldBe "Invalid host badexample.com"
    }

  }

  "validateCallback" should {
    val challenge         = "foobar"
    val returnedChallenge = CallbackValidationResponse(challenge)

    "respond with challenge" in new Setup {
      val callbackUrlPath    = "/callback"
      val callbackValidation = CallbackValidation(wireMockUrl + callbackUrlPath)

      stubFor(
        get(urlPathEqualTo(callbackUrlPath))
          .withQueryParam("challenge", equalTo(challenge))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(returnedChallenge)
          )
      )

      await(underTest.validateCallback(callbackValidation, challenge)) shouldBe challenge
    }

    "passes query params" in new Setup {
      val callbackUrlPath    = "/callback"
      val callbackValidation = CallbackValidation(wireMockUrl + callbackUrlPath + "?param1=value1")

      stubFor(
        get(urlPathEqualTo(callbackUrlPath))
          .withQueryParam("challenge", equalTo(challenge))
          .withQueryParam("param1", equalTo("value1"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(returnedChallenge)
          )
      )

      await(underTest.validateCallback(callbackValidation, challenge)) shouldBe challenge
    }
  }
}
