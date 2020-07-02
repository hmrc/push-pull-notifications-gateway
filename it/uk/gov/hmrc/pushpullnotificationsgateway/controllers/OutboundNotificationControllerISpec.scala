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

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{OK, BAD_REQUEST, UNSUPPORTED_MEDIA_TYPE, FORBIDDEN}
import uk.gov.hmrc.pushpullnotificationsgateway.support.ServerBaseISpec

class OutboundNotificationControllerISpec extends ServerBaseISpec {

  protected override def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled" -> true,
        "auditing.enabled" -> false,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort
      )

  val url = s"http://localhost:$port"

  val validJsonBody =
    raw"""{
         |   "destinationUrl":"https://somedomain.com/post-handler",
         |   "forwardedHeaders": [
         |      {"key": "Content-Type", "value": "application/xml"},
         |      {"key": "User-Agent", "value": "header-2-value"}
         |   ],
         |   "payload":"<xml>\n <content>This is a well-formed XML</content>\n</xml>"
         |}
         |""".stripMargin

  val invalidJsonBody =
    raw"""{
         |   "destinationUrl":"",
         |   "forwardedHeaders": [
         |      {"key": "Content-Type", "value": "application/xml"},
         |      {"key": "User-Agent", "value": "header-2-value"}
         |   ],
         |   "payload":"<xml>\n <content>This is a well-formed XML</content>\n</xml>"
         |}
         |""".stripMargin

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def doPost(path: String, jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url$path")
      .withHttpHeaders(headers: _*)
      .post(jsonBody)
      .futureValue

  "OutBoundNotificationController" when {

    "POST /notify" should {
      "respond with 200 when valid notification is received" in {
        val result = doPost("/notify", validJsonBody, List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api"))
        result.status shouldBe OK
        result.body shouldBe ""
      }

      "respond with 200 when valid notification but missing destinationUrl Value is received" in {
        val result = doPost("/notify", invalidJsonBody, List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api"))
        result.status shouldBe BAD_REQUEST
        result.body shouldBe ""
      }

      "respond with 400 when invalid json is sent" in {
        val result = doPost("/notify", "{}", List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api"))
        result.status shouldBe BAD_REQUEST
        result.body shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
      }

      "respond with 400 when incorrect content type is received" in {
        val result = doPost("/notify", validJsonBody, List("Content-Type" -> "application/xml", "User-Agent" -> "push-pull-notifications-api"))
        result.status shouldBe UNSUPPORTED_MEDIA_TYPE
        result.body shouldBe "{\"statusCode\":415,\"message\":\"Expecting text/json or application/json body\"}"
      }

      "respond with 403 when non whitelisted useragent is sent" in {
        val result = doPost("/notify", validJsonBody, List("Content-Type" -> "application/json", "User-Agent" -> "not-in-whitelist"))
        result.status shouldBe FORBIDDEN
        result.body shouldBe "{\"code\":\"FORBIDDEN\",\"message\":\"Authorisation failed\"}"
      }

      "respond with 403 when user agent header is missing" in {
        val result = doPost("/notify", validJsonBody, List("Content-Type" -> "application/json"))
        result.status shouldBe FORBIDDEN
        result.body shouldBe "{\"code\":\"FORBIDDEN\",\"message\":\"Authorisation failed\"}"
      }
    }
  }
}
