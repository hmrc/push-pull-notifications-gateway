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

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.pushpullnotificationsgateway.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsgateway.connectors.OutboundProxyConnector
import uk.gov.hmrc.pushpullnotificationsgateway.controllers.actionbuilders.{ValidateAuthorizationHeaderAction, ValidateUserAgentHeaderAction}
import uk.gov.hmrc.pushpullnotificationsgateway.models.RequestJsonFormats._
import uk.gov.hmrc.pushpullnotificationsgateway.models.ResponseFormats._
import uk.gov.hmrc.pushpullnotificationsgateway.models._
import uk.gov.hmrc.pushpullnotificationsgateway.services.CallbackValidator

import scala.concurrent.{ExecutionContext, Future}


@Singleton()
class OutboundNotificationController @Inject()(appConfig: AppConfig,
                                               validateUserAgentHeaderAction: ValidateUserAgentHeaderAction,
                                               validateAuthorizationHeaderAction: ValidateAuthorizationHeaderAction,
                                               cc: ControllerComponents,
                                               playBodyParsers: PlayBodyParsers,
                                               outboundProxyConnector: OutboundProxyConnector,
                                               callbackValidator: CallbackValidator)
                                              (implicit ec: ExecutionContext)
  extends BackendController(cc) {



  def validateNotification(notification: OutboundNotification): Boolean = !notification.destinationUrl.isEmpty

  def handleNotification(): Action[JsValue] =
    (Action andThen
      validateAuthorizationHeaderAction andThen
      validateUserAgentHeaderAction)
      .async(playBodyParsers.json) { implicit request =>
    withJsonBody[OutboundNotification] {
      notification => {
        if(validateNotification(notification)) {
          Logger.info(notification.toString)
          outboundProxyConnector
            .postNotification(notification)
            .map(statusCode => {
              val successful = statusCode == 200 // We only accept HTTP 200 as being successful response
              if (!successful) {
                Logger.warn(s"Call to ${notification.destinationUrl} returned HTTP Status Code $statusCode - treating notification as unsuccessful")
              }
              Ok(Json.toJson(OutboundNotificationResponse(successful)))
            }) recover {
            case e: IllegalArgumentException => UnprocessableEntity(JsErrorResponse(ErrorCode.UNPROCESSABLE_ENTITY, e.getMessage))
          }
        } else {
          Logger.error(s"Invalid notification ${notification.toString}")
          Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "JSON body is invalid against expected format")))
        }
      }
    }
  }

  def validateCallback(): Action[JsValue] =
    (Action andThen
      validateAuthorizationHeaderAction andThen
      validateUserAgentHeaderAction)
      .async(playBodyParsers.json) { implicit request =>
        withJsonBody[CallbackValidation] { callbackValidation =>
          callbackValidator.validateCallback(callbackValidation) map { validationResult =>
            Ok(Json.toJson(validationResult))
          } recover {
            case e: IllegalArgumentException => UnprocessableEntity(JsErrorResponse(ErrorCode.UNPROCESSABLE_ENTITY, e.getMessage))
          }
        }
      }

  override protected def withJsonBody[T]
  (f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result]
  = {
    withJson(request.body)(f)
  }

  private def withJson[T](json: JsValue)(f: T => Future[Result])(implicit reads: Reads[T]): Future[Result] = {
    json.validate[T] match {
      case JsSuccess(payload, _) => f(payload)
      case JsError(errs) =>
        Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "JSON body is invalid against expected format")))
    }
  }
}
