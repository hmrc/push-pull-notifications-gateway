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

import java.util.regex.Pattern

import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.http.{HttpException, _}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.pushpullnotificationsgateway.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsgateway.models.OutboundNotification

import scala.concurrent.Future.{failed, successful}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OutboundProxyConnector @Inject()(appConfig: AppConfig,
                                       defaultHttpClient: HttpClient,
                                       proxiedHttpClient: ProxiedHttpClient)
                                      (implicit ec: ExecutionContext) {

  def httpClient: HttpClient = if (appConfig.useProxy) proxiedHttpClient else defaultHttpClient
  val destinationUrlPattern: Pattern = "^https.*".r.pattern

  private def validateDestinationUrl(destinationUrl: String): Future[String] = {
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

  def postNotification(notification: OutboundNotification)(implicit hc: HeaderCarrier): Future[Int] = {
    validateDestinationUrl(notification.destinationUrl) flatMap { validatedDestinationUrl =>
      httpClient.POST[String, HttpResponse](validatedDestinationUrl, notification.payload, notification.forwardedHeaders.map(fh => (fh.key, fh.value)))
        .map(_.status)
        .recover {
          case httpException: HttpException =>
            Logger.error(s"POST ${notification.destinationUrl} failed", httpException)
            httpException.responseCode
          case upstreamErrorResponse: UpstreamErrorResponse =>
            Logger.error(s"POST ${notification.destinationUrl} failed", upstreamErrorResponse)
            upstreamErrorResponse.statusCode
        }
    }
  }
}
