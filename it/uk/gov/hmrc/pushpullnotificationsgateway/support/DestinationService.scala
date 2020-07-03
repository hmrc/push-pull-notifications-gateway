package uk.gov.hmrc.pushpullnotificationsgateway.support

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status

trait DestinationService {
  val destinationUrl = "/destination-service/post-handler"
  private val destinationUrlMatcher = urlEqualTo(destinationUrl)

  def primeDestinationService()= {
    stubFor(post(destinationUrlMatcher)
      .willReturn(
        aResponse()
          .withStatus(Status.NO_CONTENT)
      )
    )
  }

  def primeDestinationToReturn422()= {
    stubFor(post(destinationUrlMatcher)
      .willReturn(
        aResponse()
          .withStatus(Status.UNPROCESSABLE_ENTITY)
      )
    )
  }
}
