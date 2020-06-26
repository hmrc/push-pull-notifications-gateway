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
import play.api.libs.json.{JsError, JsSuccess, JsValue, Reads}
import play.api.mvc.{Action, AnyContent, ControllerComponents, PlayBodyParsers, Request, Result}
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.pushpullnotificationsgateway.models.RequestJsonFormats._
import uk.gov.hmrc.pushpullnotificationsgateway.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsgateway.controllers.actionbuilders.ValidateUserAgentHeaderAction
import uk.gov.hmrc.pushpullnotificationsgateway.models.{ErrorCode, JsErrorResponse, OutboundNotification}

import scala.concurrent.Future

@Singleton()
class OutboundNotificationController @Inject()(appConfig: AppConfig,
                                               validateUserAgentHeaderAction: ValidateUserAgentHeaderAction,
                                               cc: ControllerComponents,
                                               playBodyParsers: PlayBodyParsers)
  extends BackendController(cc) {

  def handleNotification() =
    (Action andThen
      validateUserAgentHeaderAction)
      .async(playBodyParsers.json) { implicit request =>
    withJsonBody[OutboundNotification] {
      notification => {
        Logger.info(notification.toString)
        Future.successful(Ok("Hello world"))
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
      case JsError(errs) => {
        errs.foreach(error => {
          Logger.info(error._1.path.head.toJsonString )
          error._2.foreach(e=> Logger.info(""+e.message))
        })
        Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "JSON body is invalid against expected format")))
      }
    }
  }
}

