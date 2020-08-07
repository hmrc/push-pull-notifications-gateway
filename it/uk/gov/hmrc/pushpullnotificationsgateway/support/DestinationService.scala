package uk.gov.hmrc.pushpullnotificationsgateway.support

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.JsValue

trait DestinationService {
  val destinationUrl = "/destination-service/post-handler"
  private val destinationUrlMatcher = urlEqualTo(destinationUrl)

  def primeDestinationService(status: Int): StubMapping = {
    stubFor(post(destinationUrlMatcher)
      .willReturn(
        aResponse()
          .withStatus(status)
      )
    )
  }

  def primeDestinationServiceForValidation(challenge: String, status: Int, responseBody: Option[JsValue]): StubMapping = {
    val response: ResponseDefinitionBuilder = responseBody
      .fold(aResponse().withStatus(status))(body => aResponse().withStatus(status).withBody(body.toString()))

    stubFor(
      get(urlEqualTo(s"$destinationUrl?challenge=$challenge"))
        .willReturn(response)
    )
  }
}
