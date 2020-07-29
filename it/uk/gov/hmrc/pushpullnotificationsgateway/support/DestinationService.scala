package uk.gov.hmrc.pushpullnotificationsgateway.support

import com.github.tomakehurst.wiremock.client.WireMock._

trait DestinationService {
  val destinationUrl = "/destination-service/post-handler"
  private val destinationUrlMatcher = urlEqualTo(destinationUrl)

  def primeDestinationService(status: Int)= {
    stubFor(post(destinationUrlMatcher)
      .willReturn(
        aResponse()
          .withStatus(status)
      )
    )
  }
}
