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

package uk.gov.hmrc.pushpullnotificationsgateway.services

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.{Matchers, WordSpec}

class ChallengeGeneratorSpec extends WordSpec with Matchers with MockitoSugar with ArgumentMatchersSugar {

  trait Setup {
    val underTest = new ChallengeGenerator()
  }

  "generateChallenge" should {
    "generate a new random challenge every time it is invoked" in new Setup {
      val firstResult: String = underTest.generateChallenge
      val secondResult: String = underTest.generateChallenge

      firstResult should not be secondResult
    }
  }
}
