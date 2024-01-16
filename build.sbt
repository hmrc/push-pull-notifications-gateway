import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import sbt.Tests.Group
import sbt.Tests.SubProcess

val appName = "push-pull-notifications-gateway"

Global / bloopAggregateSourceDependencies := true

scalaVersion := "2.13.12"

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    name := appName,
    organization := "uk.gov.hmrc",
    PlayKeys.playDefaultPort := 6702,
    majorVersion := 0,
    libraryDependencies ++= AppDependencies(),
    ScoverageSettings(),
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources"
  )
  .settings(
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-eT"),
    Test / unmanagedSourceDirectories ++= Seq(baseDirectory.value / "test", baseDirectory.value / "test-common"),
    Test / fork := false,
    Test / parallelExecution := false
  )
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(scalafixConfigSettings(IntegrationTest))
  .settings(
    IntegrationTest / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-eT"),
    IntegrationTest / unmanagedSourceDirectories ++= Seq(baseDirectory.value / "it", baseDirectory.value / "test-common"),
    inConfig(IntegrationTest)(scalafixConfigSettings(IntegrationTest))
  )
  .settings(scalacOptions ++= Seq("-deprecation", "-feature"))
  .settings(
    scalacOptions ++= Seq(
      "-Wconf:cat=unused&src=views/.*\\.scala:s",
      "-Wconf:cat=unused&src=.*RoutesPrefix\\.scala:s",
      "-Wconf:cat=unused&src=.*Routes\\.scala:s",
      "-Wconf:cat=unused&src=.*ReverseRoutes\\.scala:s"
    )
  )
def oneForkedJvmPerTest(tests: Seq[TestDefinition]) = {
  tests.map { test =>
    new Group(test.name, Seq(test), SubProcess(ForkOptions().withRunJVMOptions(Vector(s"-Dtest.name=${test.name}"))))
  }
}

commands ++= Seq(
  Command.command("run-all-tests") { state => "test" :: "it:test" :: state },
  Command.command("clean-and-test") { state => "clean" :: "compile" :: "run-all-tests" :: state },

  // Coverage does not need compile !
  Command.command("pre-commit") { state => "clean" :: "scalafmtAll" :: "scalafixAll" :: "coverage" :: "run-all-tests" :: "coverageOff" :: "coverageAggregate" :: state }
)
