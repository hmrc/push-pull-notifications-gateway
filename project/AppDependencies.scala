import sbt._

object AppDependencies {
  def apply() = compileDeps ++ testDeps("test") ++ testDeps("it")

  
  private val compileDeps = Seq(
    "uk.gov.hmrc" %% "bootstrap-play-26" % "4.0.0",
    "com.beachape" %% "enumeratum-play-json" % "1.6.0"
  )

  private def testDeps(scope: String) = Seq(
    "org.mockito" %% "mockito-scala-scalatest" % "1.14.4" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.3" % scope,
    "uk.gov.hmrc" %% "reactivemongo-test" % "4.21.0-play-26" % scope,
    "com.github.tomakehurst" % "wiremock" % "2.25.1" % scope
  )
}
