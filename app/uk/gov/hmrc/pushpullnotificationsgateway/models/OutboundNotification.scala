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


case class ForwardedHeader(key: String, value: String)
case class OutboundNotification(destinationUrl: String, forwardedHeaders: List[ForwardedHeader], payload: String)

//{
//   "destinationUrl":"https://somedomain.com/post-handler",
//   "forwardedHeaders": [
//      {"key": "header-1", "value": "header-1-value"},
//      {"key": "header-2", "value": "header-2-value"}
//   ],
//   "payload":"<xml>\n <content>This is a well-formed XML</content>\n</xml>"
//}