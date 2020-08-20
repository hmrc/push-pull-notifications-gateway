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

import java.util.UUID.randomUUID

import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR, NO_CONTENT, OK, UNPROCESSABLE_ENTITY, UNSUPPORTED_MEDIA_TYPE}
import uk.gov.hmrc.pushpullnotificationsgateway.models.RequestJsonFormats._
import uk.gov.hmrc.pushpullnotificationsgateway.models.{BoxId, MessageContentType, NotificationId, NotificationResponse}
import uk.gov.hmrc.pushpullnotificationsgateway.services.ChallengeGenerator
import uk.gov.hmrc.pushpullnotificationsgateway.support.{DestinationService, ServerBaseISpec}

class OutboundNotificationControllerISpec extends ServerBaseISpec with DestinationService {

  val authToken: String = "authtoken"

  val expectedChallenge = randomUUID.toString
  val stubbedChallengeGenerator: ChallengeGenerator = new ChallengeGenerator {
    override def generateChallenge: String = expectedChallenge
  }

  protected override def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled" -> true,
        "auditing.enabled" -> false,
        "validateHttpsCallbackUrl" -> false,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort,
        "authorizationKey"  -> authToken
      )
      .overrides(bind[ChallengeGenerator].to(stubbedChallengeGenerator))

  val url = s"http://localhost:$port"

  val notificationResponse =
    NotificationResponse(
      NotificationId(randomUUID),
      BoxId(randomUUID),
      MessageContentType.APPLICATION_XML,
      "<xml><content>This is a well-formed XML</content></xml>")

  val notificationResponseAsJsonString = Json.toJson(notificationResponse).toString

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def doPost(path: String, jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url$path")
      .withHttpHeaders(headers: _*)
      .post(jsonBody)
      .futureValue

  "OutBoundNotificationController" when {

    "POST /notify" should {
      val validJsonBody =
        s"""{
           |   "destinationUrl":"http://$wireMockHost:$wireMockPort$destinationUrl",
           |   "payload":$notificationResponseAsJsonString
           |}
           |""".stripMargin

      val invalidJsonBody =
        s"""{
           |   "destinationUrl":"",
           |   "payload":$notificationResponseAsJsonString
           |}
           |""".stripMargin

      "respond with OK and {successful:true} when valid notification is received" in {
        primeDestinationService(OK)

        val result =
          doPost(
            "/notify",
            validJsonBody,
            List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken))

        result.status shouldBe OK
        result.body shouldBe "{\"successful\":true}"
      }

      "respond with OK and {successful:false} when third part responds with non-200 success status" in {
        primeDestinationService(NO_CONTENT)

        val result =
          doPost(
            "/notify",
            validJsonBody,
            List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken))

        result.status shouldBe OK
        result.body shouldBe "{\"successful\":false}"
      }

      "respond with {successful:false} when call to thrid party fails" in {
        primeDestinationService(UNPROCESSABLE_ENTITY)

        val result =
          doPost(
            "/notify",
            validJsonBody,
            List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken))

        result.status shouldBe OK
        result.body shouldBe "{\"successful\":false}"
      }


     "respond with 403 when auth token is invalid" in {
        val result =
          doPost(
            "/notify",
            validJsonBody,
            List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> "IamInvalid"))
        result.status shouldBe FORBIDDEN
        result.body shouldBe "{\"code\":\"FORBIDDEN\",\"message\":\"Authorisation failed\"}"
      }

      "respond with 403 when auth header is missing" in {
        val result = doPost("/notify", validJsonBody, List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api"))
        result.status shouldBe FORBIDDEN
        result.body shouldBe "{\"code\":\"FORBIDDEN\",\"message\":\"Authorisation failed\"}"
      }

      "respond with 200 when valid notification but missing destinationUrl Value is received" in {
        val result =
          doPost(
            "/notify",
            invalidJsonBody,
            List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken))
        result.status shouldBe BAD_REQUEST
        result.body shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
      }

      "respond with 400 when invalid json is sent" in {
        val result =
          doPost(
            "/notify",
            "{}",
            List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken))
        result.status shouldBe BAD_REQUEST
        result.body shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
      }

      "respond with 400 when incorrect content type is received" in {
        val result =
          doPost(
            "/notify",
            validJsonBody,
            List("Content-Type" -> "application/xml", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken))
        result.status shouldBe UNSUPPORTED_MEDIA_TYPE
        result.body shouldBe "{\"statusCode\":415,\"message\":\"Expecting text/json or application/json body\"}"
      }

      "respond with 403 when non whitelisted useragent is sent" in {
        val result =
          doPost(
            "/notify",
            validJsonBody,
            List("Content-Type" -> "application/json", "User-Agent" -> "not-in-whitelist", "Authorization" -> authToken))
        result.status shouldBe FORBIDDEN
        result.body shouldBe "{\"code\":\"FORBIDDEN\",\"message\":\"Authorisation failed\"}"
      }

      "respond with 403 when user agent header is missing" in {
        val result = doPost("/notify", validJsonBody, List("Content-Type" -> "application/json", "Authorization" -> authToken))
        result.status shouldBe FORBIDDEN
        result.body shouldBe "{\"code\":\"FORBIDDEN\",\"message\":\"Authorisation failed\"}"
      }
    }

    "POST /validate-callback" should {
      val validJsonBody: String =
        s"""{
           |   "callbackUrl": "http://$wireMockHost:$wireMockPort$destinationUrl"
           |}
           |""".stripMargin

      val validJsonBodyWithQueryParams: String =
        s"""{
           |   "callbackUrl": "http://$wireMockHost:$wireMockPort$destinationUrl?param=value"
           |}
           |""".stripMargin

      val invalidJsonBody: String =
        s"""{
           |   "callback": "http://$wireMockHost:$wireMockPort$destinationUrl"
           |}
           |""".stripMargin

      "respond with OK and {successful:true} when validation is successful" in {
        primeDestinationServiceForValidation(Seq("challenge" -> expectedChallenge), OK, Some(Json.obj("challenge" -> expectedChallenge)))

        val result =
          doPost(
            "/validate-callback",
            validJsonBody,
            List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken))

        result.status shouldBe OK
        result.body shouldBe """{"successful":true}"""
      }

      "respond with OK and {successful:true} when validation is successful for a callback URL with query params" in {
        primeDestinationServiceForValidation(Seq("param" -> "value", "challenge" -> expectedChallenge), OK, Some(Json.obj("challenge" -> expectedChallenge)))

        val result =
          doPost(
            "/validate-callback",
            validJsonBodyWithQueryParams,
            List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken))

        result.status shouldBe OK
        result.body shouldBe """{"successful":true}"""
      }

      "respond with OK and error message when validation is not successful" in {
        primeDestinationServiceForValidation(Seq("challenge" -> expectedChallenge), OK, Some(Json.obj("challenge" -> "incorrectChallenge")))

        val result =
          doPost(
            "/validate-callback",
            validJsonBody,
            List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken))

        result.status shouldBe OK
        result.body shouldBe """{"successful":false,"errorMessage":"Returned challenge did not match"}"""
      }

      "respond with OK and error message when returned payload does not contain the challenge property" in {
        primeDestinationServiceForValidation(Seq("challenge" -> expectedChallenge), OK, Some(Json.obj("random_property" -> "foo")))

        val result =
          doPost(
            "/validate-callback",
            validJsonBody,
            List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken))

        result.status shouldBe OK
        result.body shouldBe """{"successful":false,"errorMessage":"Invalid callback URL. Check the information you have provided is correct."}"""
      }

      "respond with OK and error message when the destination endpoint returns 400" in {
        primeDestinationServiceForValidation(Seq("challenge" -> expectedChallenge), BAD_REQUEST, Some(Json.obj("challenge" -> expectedChallenge)))

        val result =
          doPost(
            "/validate-callback",
            validJsonBody,
            List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken))

        result.status shouldBe OK
        result.body shouldBe """{"successful":false,"errorMessage":"Invalid callback URL. Check the information you have provided is correct."}"""
      }

      "respond with OK and error message when the destination endpoint returns 500" in {
        primeDestinationServiceForValidation(Seq("challenge" -> expectedChallenge), INTERNAL_SERVER_ERROR, Some(Json.obj("challenge" -> expectedChallenge)))

        val result =
          doPost(
            "/validate-callback",
            validJsonBody,
            List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken))

        result.status shouldBe OK
        result.body shouldBe """{"successful":false,"errorMessage":"Invalid callback URL. Check the information you have provided is correct."}"""
      }

      "respond with 400 when invalid json is sent" in {
        val result =
          doPost(
            "/validate-callback",
            invalidJsonBody,
            List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken))
        result.status shouldBe BAD_REQUEST
        result.body shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
      }

      "respond with 400 when incorrect content type is received" in {
        val result =
          doPost(
            "/validate-callback",
            validJsonBody,
            List("Content-Type" -> "application/xml", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> authToken))
        result.status shouldBe UNSUPPORTED_MEDIA_TYPE
        result.body shouldBe "{\"statusCode\":415,\"message\":\"Expecting text/json or application/json body\"}"
      }

      "respond with 403 when auth token is invalid" in {
        val result =
          doPost(
            "/validate-callback",
            validJsonBody,
            List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api", "Authorization" -> "IamInvalid"))
        result.status shouldBe FORBIDDEN
        result.body shouldBe "{\"code\":\"FORBIDDEN\",\"message\":\"Authorisation failed\"}"
      }

      "respond with 403 when auth header is missing" in {
        val result = doPost("/validate-callback", validJsonBody, List("Content-Type" -> "application/json", "User-Agent" -> "push-pull-notifications-api"))
        result.status shouldBe FORBIDDEN
        result.body shouldBe "{\"code\":\"FORBIDDEN\",\"message\":\"Authorisation failed\"}"
      }

      "respond with 403 when non whitelisted useragent is sent" in {
        val result =
          doPost(
            "/validate-callback",
            validJsonBody,
            List("Content-Type" -> "application/json", "User-Agent" -> "not-in-whitelist", "Authorization" -> authToken))
        result.status shouldBe FORBIDDEN
        result.body shouldBe "{\"code\":\"FORBIDDEN\",\"message\":\"Authorisation failed\"}"
      }

      "respond with 403 when user agent header is missing" in {
        val result = doPost("/validate-callback", validJsonBody, List("Content-Type" -> "application/json", "Authorization" -> authToken))
        result.status shouldBe FORBIDDEN
        result.body shouldBe "{\"code\":\"FORBIDDEN\",\"message\":\"Authorisation failed\"}"
      }
    }
  }
}
