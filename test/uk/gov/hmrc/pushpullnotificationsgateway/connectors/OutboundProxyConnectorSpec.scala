/*
 * Copyright 2020 HM Revenue & Customs
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

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.{Matchers, WordSpec}
import play.api.LoggerLike
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.pushpullnotificationsgateway.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsgateway.models.OutboundNotification

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

class OutboundProxyConnectorSpec extends WordSpec with Matchers with MockitoSugar with ArgumentMatchersSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup {
    val mockAppConfig: AppConfig = mock[AppConfig]
    val mockDefaultHttpClient: HttpClient = mock[HttpClient]
    val mockProxiedHttpClient: ProxiedHttpClient = mock[ProxiedHttpClient]
    val mockLogger: LoggerLike = mock[LoggerLike]

    when(mockAppConfig.allowedHostList).thenReturn(List.empty)

    val underTest = new OutboundProxyConnector(mockAppConfig, mockDefaultHttpClient, mockProxiedHttpClient) {
      override val logger: LoggerLike = mockLogger
    }
  }

  "postNotification" should {
    val destinationUrl = "http://localhost"
    val notification: OutboundNotification = OutboundNotification(destinationUrl, List.empty, """{"key": "value"}""")

    "use the default http client when not configured to use proxy" in new Setup {
      when(mockAppConfig.useProxy).thenReturn(false)
      when(mockDefaultHttpClient.POST[String, HttpResponse](*, *, *)(*, *, *, *)).thenReturn(successful(HttpResponse(OK)))

      val result: Int = await(underTest.postNotification(notification))

      result shouldBe OK
      verify(mockDefaultHttpClient).POST(*, *, *)(*, *, *, *)
      verifyZeroInteractions(mockProxiedHttpClient)
    }

    "use the proxied http client when configured to use proxy" in new Setup {
      when(mockAppConfig.useProxy).thenReturn(true)
      when(mockProxiedHttpClient.POST[String, HttpResponse](*, *, *)(*, *, *, *)).thenReturn(successful(HttpResponse(OK)))

      val result: Int = await(underTest.postNotification(notification))

      result shouldBe OK
      verify(mockProxiedHttpClient).POST(*, *, *)(*, *, *, *)
      verifyZeroInteractions(mockDefaultHttpClient)
    }

    "recover HttpException to return the error code" in new Setup {
      when(mockAppConfig.useProxy).thenReturn(false)
      when(mockDefaultHttpClient.POST[String, HttpResponse](*, *, *)(*, *, *, *)).thenReturn(failed(new NotFoundException("not found")))

      val result: Int = await(underTest.postNotification(notification))

      result shouldBe NOT_FOUND
      verify(mockLogger).warn(s"Attempted request to $destinationUrl responded with HTTP response code 404")
    }

    "recover UpstreamErrorResponse to return the error code" in new Setup {
      when(mockAppConfig.useProxy).thenReturn(false)
      when(mockDefaultHttpClient.POST[String, HttpResponse](*, *, *)(*, *, *, *)).thenReturn(failed(UpstreamErrorResponse("not found", BAD_GATEWAY)))

      val result: Int = await(underTest.postNotification(notification))

      result shouldBe BAD_GATEWAY
      verify(mockLogger).warn(s"Attempted request to $destinationUrl responded with HTTP response code 502")
    }

    "fail when the destination URL does not use https and configured to validate that" in new Setup {
      when(mockAppConfig.validateHttpsCallbackUrl).thenReturn(true)
      when(mockAppConfig.useProxy).thenReturn(false)
      when(mockDefaultHttpClient.POST[String, HttpResponse](*, *, *)(*, *, *, *)).thenReturn(successful(HttpResponse(OK)))

      val exception = intercept[IllegalArgumentException] {
        await(underTest.postNotification(notification))
      }

      exception.getMessage shouldBe "Invalid destination URL http://localhost"
      verifyZeroInteractions(mockDefaultHttpClient, mockProxiedHttpClient)
    }

    "not fail when the destination URL does use https and configured to validate that" in new Setup {
      when(mockAppConfig.validateHttpsCallbackUrl).thenReturn(true)
      when(mockAppConfig.useProxy).thenReturn(false)
      when(mockDefaultHttpClient.POST[String, HttpResponse](*, *, *)(*, *, *, *)).thenReturn(successful(HttpResponse(OK)))

      val result: Int = await(underTest.postNotification(notification.copy(destinationUrl = "https://localhost")))

      result shouldBe OK
    }

    "make a successful request when the host matches a host in the list" in new Setup {
      val host = "example.com"
      when(mockAppConfig.allowedHostList).thenReturn(List(host))
      when(mockDefaultHttpClient.POST[String, HttpResponse](*, *, *)(*, *, *, *)).thenReturn(successful(HttpResponse(OK)))

      val result:Int = await(underTest.postNotification(notification.copy(destinationUrl = "https://example.com/callback")))

      result shouldBe OK
    }

    "fail when the host does not match any of the hosts in the list" in new Setup {
      val host = "example.com"
      when(mockAppConfig.allowedHostList).thenReturn(List(host))

      val exception = intercept[IllegalArgumentException] {
        await(underTest.postNotification(notification.copy(destinationUrl = "https://badexample.com/callback")))
      }
      exception.getMessage shouldBe "Invalid host badexample.com"
      verifyZeroInteractions(mockProxiedHttpClient, mockDefaultHttpClient)
    }

  }
}
