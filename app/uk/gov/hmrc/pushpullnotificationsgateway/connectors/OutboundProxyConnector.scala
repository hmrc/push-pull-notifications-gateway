/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.Logger
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.pushpullnotificationsgateway.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsgateway.models.{CallbackValidation, OutboundNotification}
import uk.gov.hmrc.pushpullnotificationsgateway.util.ApplicationLogger

import java.net.URL
import java.util.regex.Pattern
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.{failed, successful}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class OutboundProxyConnector @Inject()(appConfig: AppConfig,
                                       defaultHttpClient: HttpClient,
                                       proxiedHttpClient: ProxiedHttpClient)
                                      (implicit ec: ExecutionContext) extends ApplicationLogger {

  import OutboundProxyConnector._

  lazy val httpClient: HttpClient = if (appConfig.useProxy) proxiedHttpClient else defaultHttpClient
  
  val destinationUrlPattern: Pattern = "^https.*".r.pattern

  private def validate(destinationUrl: String): Future[String] = {
    val optionalPattern = Some(destinationUrlPattern).filter(_ => appConfig.validateHttpsCallbackUrl)
    
    validateDestinationUrl(optionalPattern, appConfig.allowedHostList)(destinationUrl)
    .fold(
      err => failed(new IllegalArgumentException(err)),
      ok => successful(ok)
    )
  }

  def postNotification(notification: OutboundNotification): Future[Int] = {
    
    def failWith(statusCode: Int): Int = {
      def message = s"Attempted request to ${notification.destinationUrl} responded with HTTP response code $statusCode"
      logger.warn(message)
      statusCode
    }
    
    def failWithThrowable(t: Throwable): Int = {
      val message: String = s"Attempted request to ${notification.destinationUrl} responded caused ${t.getMessage()}"
      logger.warn(message)
      500
    }

    implicit val irrelevantHc: HeaderCarrier =  HeaderCarrier()
    
    validate(notification.destinationUrl) flatMap { url =>
      val extraHeaders = (CONTENT_TYPE -> "application/json") :: notification.forwardedHeaders.map(fh => (fh.key, fh.value))

      httpClient.POSTString[Either[UpstreamErrorResponse,HttpResponse]](url, notification.payload, extraHeaders)
        .map( _ match {
          case Left(UpstreamErrorResponse(_, statusCode, _, _)) =>
            failWith(statusCode)
          case Right(r: HttpResponse) => r.status
        })
        .recover {
          case _: GatewayTimeoutException => failWith(504)
          case _: BadGatewayException     => failWith(502)
          case NonFatal(e)                => failWithThrowable(e)
        }
    }
  }

  def validateCallback(callbackValidation: CallbackValidation, challenge: String): Future[String] = {
    implicit val hc: HeaderCarrier =  HeaderCarrier()
    validate(callbackValidation.callbackUrl) flatMap { validatedCallbackUrl =>
      val callbackUrlWithChallenge = Option(new URL(validatedCallbackUrl).getQuery)
        .fold(s"$validatedCallbackUrl?challenge=$challenge")(_ => s"$validatedCallbackUrl&challenge=$challenge")
      httpClient.GET[CallbackValidationResponse](callbackUrlWithChallenge).map(_.challenge)
    }
  }
}

object OutboundProxyConnector {
  implicit val callbackValidationResponseFormat: OFormat[CallbackValidationResponse] = Json.format[CallbackValidationResponse]
  private[connectors] case class CallbackValidationResponse(challenge: String)
  val objLogger = Logger("outboundProxyConnector")

  def validateUrlProtocol(destinationUrlPattern: Option[Pattern])(destinationUrl: String): Either[String, String] = {
    destinationUrlPattern match {
      case None => Right(destinationUrl)
      case Some(pattern) => 
        if (pattern.matcher(destinationUrl).matches()) {
          Right(destinationUrl)
        } else {
          objLogger.error(s"Invalid destination URL $destinationUrl")
          Left(s"Invalid destination URL $destinationUrl")
        }
    }
  }

  def validateAgainstAllowedHostList(allowedHostList: List[String])(destinationUrl: String): Either[String, String] = {
    if (allowedHostList.nonEmpty) {
      val host = new URL(destinationUrl).getHost
      if(allowedHostList.contains(host)) {
        Right(destinationUrl)
      } else {
        objLogger.error(s"Invalid host $host")
        Left(s"Invalid host $host")
      }
    } else {
      Right(destinationUrl)
    }
  }

  def validateDestinationUrl(destinationUrlPattern: Option[Pattern], allowedHostList: List[String])(destinationUrl: String): Either[String, String] = {
    // This could use validated to sum up the errors but that would affect the error text which is used in tests and perhaps in other callers of this functionality
    for {
      protocolValidated <- validateUrlProtocol(destinationUrlPattern)(destinationUrl)
      hostValidated <- validateAgainstAllowedHostList(allowedHostList)(destinationUrl)
    } yield destinationUrl
  }

}
