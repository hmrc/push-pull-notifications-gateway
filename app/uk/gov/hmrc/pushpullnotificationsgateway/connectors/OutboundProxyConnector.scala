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

import java.net.URL
import java.util.regex.Pattern

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Json, OFormat}
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpException, _}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.pushpullnotificationsgateway.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsgateway.connectors.OutboundProxyConnector.CallbackValidationResponse
import uk.gov.hmrc.pushpullnotificationsgateway.models.{CallbackValidation, OutboundNotification}

import scala.concurrent.Future.{failed, successful}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OutboundProxyConnector @Inject()(appConfig: AppConfig,
                                       defaultHttpClient: HttpClient,
                                       proxiedHttpClient: ProxiedHttpClient)
                                      (implicit ec: ExecutionContext) {

  val logger: LoggerLike = Logger

  def httpClient: HttpClient = if (appConfig.useProxy) proxiedHttpClient else defaultHttpClient
  val destinationUrlPattern: Pattern = "^https.*".r.pattern

  private def validateDestinationUrl(destinationUrl: String): Future[String] = {
    validateUrlProtocol(destinationUrl).flatMap(validateAgainstAllowedHostList)
  }

  private def validateUrlProtocol(destinationUrl: String): Future[String] = {
    if (appConfig.validateHttpsCallbackUrl) {
      if (destinationUrlPattern.matcher(destinationUrl).matches()) {
        successful(destinationUrl)
      } else {
        Logger.error(s"Invalid destination URL $destinationUrl")
        failed(new IllegalArgumentException(s"Invalid destination URL $destinationUrl"))
      }
    } else {
      successful(destinationUrl)
    }
  }

  private def validateAgainstAllowedHostList(destinationUrl: String): Future[String] = {
    if (appConfig.allowedHostList.nonEmpty) {
      val host = new URL(destinationUrl).getHost
      if(appConfig.allowedHostList.contains(host)) {
        successful(destinationUrl)
      } else {
        Logger.error(s"Invalid host $host")
        failed(new IllegalArgumentException(s"Invalid host $host"))
      }
    } else {
      successful(destinationUrl)
    }
  }

  def postNotification(notification: OutboundNotification): Future[Int] = {
    def failedRequestLogMessage(statusCode: Int) = s"Attempted request to ${notification.destinationUrl} responded with HTTP response code $statusCode"
    implicit val hc: HeaderCarrier =  HeaderCarrier()
    validateDestinationUrl(notification.destinationUrl) flatMap { validatedDestinationUrl =>
      httpClient.POST[String, HttpResponse](validatedDestinationUrl, notification.payload)
        .map(_.status)
        .recover {
          case httpException: HttpException =>
            logger.warn(failedRequestLogMessage(httpException.responseCode))
            httpException.responseCode
          case upstreamErrorResponse: UpstreamErrorResponse =>
            logger.warn(failedRequestLogMessage(upstreamErrorResponse.statusCode))
            upstreamErrorResponse.statusCode
        }
    }
  }

  def validateCallback(callbackValidation: CallbackValidation, challenge: String): Future[String] = {
    implicit val hc: HeaderCarrier =  HeaderCarrier()
    validateDestinationUrl(callbackValidation.callbackUrl) flatMap { validatedCallbackUrl =>
      val callbackUrlWithChallenge = Option(new URL(validatedCallbackUrl).getQuery)
        .fold(s"$validatedCallbackUrl?challenge=$challenge")(_ => s"$validatedCallbackUrl&challenge=$challenge")
      httpClient.GET[CallbackValidationResponse](callbackUrlWithChallenge).map(_.challenge)
    }
  }
}

object OutboundProxyConnector {
  implicit val callbackValidationResponseFormat: OFormat[CallbackValidationResponse] = Json.format[CallbackValidationResponse]
  private[connectors] case class CallbackValidationResponse(challenge: String)
}
