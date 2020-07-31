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

package uk.gov.hmrc.pushpullnotificationsgateway.models

import org.joda.time.DateTime
import play.api.libs.json._

object RequestJsonFormats {
 val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
 implicit val JodaDateReads: Reads[org.joda.time.DateTime] = JodaReads.jodaDateReads(dateFormat)
 implicit val JodaDateWrites: Writes[org.joda.time.DateTime] = JodaWrites.jodaDateWrites(dateFormat)
 implicit val JodaDateTimeFormat: Format[DateTime] = Format(JodaDateReads, JodaDateWrites)
 implicit val notificationIdFormatter: Format[NotificationId] = Json.valueFormat[NotificationId]
 implicit val boxIdFormatter: Format[BoxId] = Json.valueFormat[BoxId]
 implicit val notificationResponseFormatter: OFormat[NotificationResponse] = Json.format[NotificationResponse]

 implicit val outboundNotificationformats = Json.format[OutboundNotification]
}

object ResponseFormats {
 implicit val formatOutboundNotificationResponse  = Json.format[OutboundNotificationResponse]
}
