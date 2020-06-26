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

import org.mockito.Mockito.{reset, when}
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
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

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class OutboundNotificationControllerSpec extends WordSpec with Matchers with MockitoSugar with BeforeAndAfterEach with GuiceOneAppPerSuite {

  val mockAppConfig: AppConfig = mock[AppConfig]

  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AppConfig].to(mockAppConfig))
    .build()

  val validJsonBody: String =
    raw"""{
         |   "destinationUrl":"https://somedomain.com/post-handler",
         |   "forwardedHeaders": [
         |      {"key": "Content-Type", "value": "application/xml"},
         |      {"key": "User-Agent", "value": "header-2-value"}
         |   ],
         |   "payload":"<xml>\n <content>This is a well-formed XML</content>\n</xml>"
         |}
         |""".stripMargin

  override def beforeEach(): Unit = {
    reset(mockAppConfig)
  }

  private def setUpAppConfig(userAgents: List[String]): Unit = {
    when(mockAppConfig.whitelistedUserAgentList).thenReturn(userAgents)
  }

  "GET /notify" should {
    "return 200 when valid request and whitelisted useragent are sent" in {
      setUpAppConfig(List("push-pull-notifications-api"))
      val headers=  Map("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api")
      val result = doPost("/push-pull-notifications-gateway/notify", headers, validJsonBody)
      status(result) shouldBe Status.OK
    }

    "return 400 when useragent whitelist is empty" in {
      setUpAppConfig(List.empty)
      val headers=  Map("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api")
      val result = doPost("/push-pull-notifications-gateway/notify", headers, validJsonBody)
      status(result) shouldBe Status.BAD_REQUEST
      Helpers.contentAsString(result) shouldBe ""
    }


    "return 403 when  useragent is not sent" in {
      setUpAppConfig(List("push-pull-notifications-api"))
      val result = doPost("/push-pull-notifications-gateway/notify", Map.empty, validJsonBody)
      status(result) shouldBe Status.FORBIDDEN
      Helpers.contentAsString(result) shouldBe "{\"code\":\"FORBIDDEN\",\"message\":\"Authorisation failed\"}"
    }

    "return 403 when non whitelisted useragent is sent" in {
      setUpAppConfig(List("push-pull-notifications-api"))
      val headers=  Map("Content-Type" -> "application/json", "User-Agent" -> "not in white list")
      val result = doPost("/push-pull-notifications-gateway/notify", headers, validJsonBody)
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
