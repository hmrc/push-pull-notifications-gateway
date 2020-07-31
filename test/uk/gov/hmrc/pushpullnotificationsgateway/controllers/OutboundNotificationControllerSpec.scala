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

package uk.gov.hmrc.pushpullnotificationsgateway.controllers

import java.util.UUID

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.pushpullnotificationsgateway.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsgateway.connectors.OutboundProxyConnector
import uk.gov.hmrc.pushpullnotificationsgateway.models.{BoxId, MessageContentType, NotificationId, NotificationResponse}
import uk.gov.hmrc.pushpullnotificationsgateway.models.RequestJsonFormats._

import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}
import scala.util.{Failure, Success, Try}

class OutboundNotificationControllerSpec
  extends WordSpec with Matchers with MockitoSugar with ArgumentMatchersSugar with BeforeAndAfterEach with GuiceOneAppPerSuite {

  val mockAppConfig: AppConfig = mock[AppConfig]
  val mockOutboundProxyConnector: OutboundProxyConnector = mock[OutboundProxyConnector]

  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AppConfig].to(mockAppConfig))
    .overrides(bind[OutboundProxyConnector].to(mockOutboundProxyConnector))
    .build()

  val notificationResponse =
    NotificationResponse(
      NotificationId(UUID.randomUUID),
      BoxId(UUID.randomUUID),
      MessageContentType.APPLICATION_XML,
      "<xml><content>This is a well-formed XML</content></xml>")

  val notificationResponseAsJsonString = Json.toJson(notificationResponse).toString

  val validJsonBody: String =
    s"""{
         |   "destinationUrl":"https://example.com/post-handler",
         |   "payload":$notificationResponseAsJsonString
         |}
         |""".stripMargin

  val invalidJsonBodyMissingUrl: String =
    s"""{
         |   "destinationUrl":"",
         |   "payload":$notificationResponseAsJsonString
         |}
         |""".stripMargin

  val invalidJsonBodyMissingPayload: String =
    raw"""{
         |   "destinationUrl":"https://example.com/post-handler",
         |   "payload":""
         |}
         |""".stripMargin

  override def beforeEach(): Unit = {
    reset(mockAppConfig)
    reset(mockOutboundProxyConnector)
  }

 val authToken = "iampushpullapi"
  private def setUpAppConfig(userAgents: List[String], authHeaderValue: Option[String] = None): Unit = {
    when(mockAppConfig.allowedUserAgentList).thenReturn(userAgents)
    authHeaderValue match {
      case Some(value) =>
      when(mockAppConfig.authorizationToken).thenReturn(value)
      ()
      case None => ()
    }
  }

  "GET /notify" should {
    "respond with OK when valid request and whitelisted useragent are sent and notification is successful" in {
      setUpAppConfig(List("push-pull-notifications-api"), Some(authToken))
      val headers=  Map("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken)
      when(mockOutboundProxyConnector.postNotification(*)).thenReturn(successful(Status.OK))

      val result = doPost("/notify", headers, validJsonBody)

      status(result) shouldBe Status.OK
      Helpers.contentAsString(result) shouldBe "{\"successful\":true}"
    }

    "respond with {successful:false} when third party system returns success response other than 200" in {
      setUpAppConfig(List("push-pull-notifications-api"), Some(authToken))
      val headers=  Map("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken)
      when(mockOutboundProxyConnector.postNotification(*)).thenReturn(successful(Status.NO_CONTENT))

      val result = doPost("/notify", headers, validJsonBody)

      status(result) shouldBe Status.OK
      Helpers.contentAsString(result) shouldBe "{\"successful\":false}"
    }

    "send the notification to the outbound proxy" in {
      setUpAppConfig(List("push-pull-notifications-api"), Some(authToken))
      val headers=  Map("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken)
      when(mockOutboundProxyConnector.postNotification(*)).thenReturn(successful(Status.NO_CONTENT))

      await(doPost("/notify", headers, validJsonBody))

      verify(mockOutboundProxyConnector, times(1)).postNotification(*)
    }

    "return 422 when the outbound proxy connector fails with IllegalArgumentException" in {
      setUpAppConfig(List("push-pull-notifications-api"), Some(authToken))
      val headers=  Map("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken)
      when(mockOutboundProxyConnector.postNotification(*)).thenReturn(failed(new IllegalArgumentException("Invalid destination URL")))

      val result = doPost("/notify", headers, validJsonBody)

      status(result) shouldBe Status.UNPROCESSABLE_ENTITY
    }

    "return 400 when invalid request and whitelisted useragent are sent" in {
      setUpAppConfig(List("push-pull-notifications-api"), Some(authToken))
      val headers=  Map("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken)
      val result = doPost("/notify", headers, invalidJsonBodyMissingUrl)
      status(result) shouldBe Status.BAD_REQUEST
      Helpers.contentAsString(result) shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }

    "return 400 when invalid request with missing url and whitelisted useragent are sent" in {
      setUpAppConfig(List("push-pull-notifications-api"), Some(authToken))
      val headers=  Map("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken)
      val result = doPost("/notify", headers, invalidJsonBodyMissingPayload)
      status(result) shouldBe Status.BAD_REQUEST
      Helpers.contentAsString(result) shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }

    "return 400 when invalid request with missing payload and whitelisted useragent are sent" in {
      setUpAppConfig(List.empty, Some(authToken))
      val headers=  Map("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken)
      val result = doPost("/notify", headers, validJsonBody)
      status(result) shouldBe Status.BAD_REQUEST
      Helpers.contentAsString(result) shouldBe ""
    }

    "return 403 when  useragent is not sent" in {
      setUpAppConfig(List("push-pull-notifications-api"), Some(authToken))
      val result = doPost("/notify", Map("Content-Type" -> "application/json", "Authorization" -> authToken), validJsonBody)
      status(result) shouldBe Status.FORBIDDEN
      Helpers.contentAsString(result) shouldBe "{\"code\":\"FORBIDDEN\",\"message\":\"Authorisation failed\"}"
    }

    "return 403 when non whitelisted useragent is sent" in {
      setUpAppConfig(List("push-pull-notifications-api"), Some(authToken))
      val headers=  Map("Content-Type" -> "application/json", "User-Agent" -> "not in white list", "Authorization" -> authToken)
      val result = doPost("/notify", headers, validJsonBody)
      status(result) shouldBe Status.FORBIDDEN
    }

    "return 403 when Authorization is not sent" in {
      setUpAppConfig(List("push-pull-notifications-api"), Some(authToken))
      val result = doPost("/notify", Map("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api"), validJsonBody)
      status(result) shouldBe Status.FORBIDDEN
      Helpers.contentAsString(result) shouldBe "{\"code\":\"FORBIDDEN\",\"message\":\"Authorisation failed\"}"
    }

    "return 403 when invalid Authorization is sent" in {
      setUpAppConfig(List("push-pull-notifications-api"), Some(authToken))
      val headers=  Map("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> "invalidAuthToken")
      val result = doPost("/notify", headers, validJsonBody)
      status(result) shouldBe Status.FORBIDDEN
    }
  }

  private def doPost(uri: String, headers: Map[String, String], bodyValue: String): Future[Result] = {
    val maybeBody: Option[JsValue] = Try {
      Json.parse(bodyValue)
    } match {
      case Success(value) => Some(value)
      case Failure(_) => None
    }

    val fakeRequest = FakeRequest(POST, uri).withHeaders(headers.toSeq: _*)
    maybeBody
      .fold(route(app, fakeRequest.withBody(bodyValue)).get)(jsonBody => route(app, fakeRequest.withJsonBody(jsonBody)).get)

  }
}
