import sbt._

object AppDependencies {
  lazy val bootstrapVersion = "8.4.0"

  def apply() = compileDeps ++ testDeps("test") ++ testDeps("it")

  private val compileDeps = Seq(
    "uk.gov.hmrc"  %% "bootstrap-backend-play-30" % bootstrapVersion
  )

  private def testDeps(scope: String) = Seq(
    "uk.gov.hmrc"           %% "bootstrap-test-play-30"   % bootstrapVersion,
    "org.mockito"           %% "mockito-scala-scalatest"  % "1.17.29"
  ).map(_ % scope)
}
