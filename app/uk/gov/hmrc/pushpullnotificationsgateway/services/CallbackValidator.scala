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

package uk.gov.hmrc.pushpullnotificationsgateway.services

import java.util.UUID.randomUUID

import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.http.{HttpException, JsValidationException, UpstreamErrorResponse}
import uk.gov.hmrc.pushpullnotificationsgateway.connectors.OutboundProxyConnector
import uk.gov.hmrc.pushpullnotificationsgateway.models.{CallbackValidation, CallbackValidationResult, ErrorCode, JsErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CallbackValidator @Inject()(outboundProxyConnector: OutboundProxyConnector, challengeGenerator: ChallengeGenerator)
                                 (implicit ec: ExecutionContext) {

  def validateCallback(callbackValidation: CallbackValidation): Future[CallbackValidationResult] = {
    def failedRequestLogMessage(statusCode: Int) = s"Attempted validation of URL ${callbackValidation.callbackUrl} responded with HTTP response code $statusCode"

    val challenge = challengeGenerator.generateChallenge
    outboundProxyConnector.validateCallback(callbackValidation, challenge) map { returnedChallenge =>
      if (returnedChallenge == challenge) {
        CallbackValidationResult(successful = true)
      } else {
        CallbackValidationResult(successful = false, Some("Returned challenge did not match"))
      }
    } recover {
      case e: JsValidationException =>
        Logger.warn(s"Attempted validation of URL ${callbackValidation.callbackUrl} failed with error ${e.getMessage}")
        CallbackValidationResult(successful = false, Some("Invalid callback URL. Check the information you have provided is correct."))
      case httpException: HttpException =>
        Logger.warn(failedRequestLogMessage(httpException.responseCode))
        CallbackValidationResult(successful = false, Some("Invalid callback URL. Check the information you have provided is correct."))
      case upstreamErrorResponse: UpstreamErrorResponse =>
        Logger.warn(failedRequestLogMessage(upstreamErrorResponse.statusCode))
        CallbackValidationResult(successful = false, Some("Invalid callback URL. Check the information you have provided is correct."))
      case e: IllegalArgumentException =>
        Logger.warn(e.getMessage)
        CallbackValidationResult(successful = false, Some("Invalid callback URL. Check the information you have provided is correct."))
    }
  }
}
