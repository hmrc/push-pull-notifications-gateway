import sbt._

object AppDependencies {
  lazy val bootstrapVersion = "7.12.0"

  def apply() = compileDeps ++ testDeps("test") ++ testDeps("it")

  private val compileDeps = Seq(
    "uk.gov.hmrc"               %% "bootstrap-backend-play-28"  %  bootstrapVersion,
    "com.beachape"              %% "enumeratum-play-json"       % "1.6.0"
  )

  private def testDeps(scope: String) = Seq(
    "uk.gov.hmrc"               %% "bootstrap-test-play-28"     % bootstrapVersion  % scope,
    "org.mockito"               %% "mockito-scala-scalatest"    % "1.16.46"         % scope,
    "com.typesafe.play"         %% "play-akka-http-server"      % "2.8.7"           % scope,
    "com.github.tomakehurst"    %  "wiremock-jre8-standalone"   % "2.27.2"          % scope
  )
}
