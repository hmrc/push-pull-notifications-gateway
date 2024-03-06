import sbt._

object AppDependencies {
  lazy val bootstrapVersion = "8.4.0"

  def apply(): Seq[ModuleID] = compileDeps ++ testDeps

  private val compileDeps = Seq(
    "uk.gov.hmrc"  %% "bootstrap-backend-play-30" % bootstrapVersion
  )

  private def testDeps = Seq(
    "uk.gov.hmrc"           %% "bootstrap-test-play-30"   % bootstrapVersion,
    "org.mockito"           %% "mockito-scala-scalatest"  % "1.17.30"
  ).map(_ % "test")
}
