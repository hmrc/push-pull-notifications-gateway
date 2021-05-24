package uk.gov.hmrc.pushpullnotificationsgateway.support

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping

import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.libs.json.JsValue

trait DestinationService {
  val destinationUrl = "/destination-service/post-handler"
  private val destinationUrlMatcher = urlEqualTo(destinationUrl)

  def primeDestinationService(status: Int): StubMapping = {
    stubFor(post(destinationUrlMatcher)
      .withHeader(CONTENT_TYPE, containing("application/json"))
      .withHeader("Forwarded-Header", containing("foobar"))
      .willReturn(
        aResponse()
          .withStatus(status)
      )
    )
  }

  def primeDestinationServiceForValidation(queryParams: Seq[(String, String)], status: Int, responseBody: Option[JsValue]): StubMapping = {
    val response: ResponseDefinitionBuilder = responseBody
      .fold(aResponse().withStatus(status))(body => aResponse().withStatus(status).withBody(body.toString()))
    val params = queryParams.map { case (k, v) => s"$k=$v" }.mkString("?", "&", "")

    stubFor(
      get(urlEqualTo(s"$destinationUrl$params"))
        .willReturn(response)
    )
  }
}
