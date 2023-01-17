import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import sbt.Tests.Group
import sbt.Tests.SubProcess
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

val appName = "push-pull-notifications-gateway"

Global / bloopAggregateSourceDependencies := true

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0"
inThisBuild(
  List(
    scalaVersion := "2.12.15",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*Filters?;MicroserviceAuditConnector;Module;GraphiteStartUp;.*\.Reverse[^.]*""",
    ScoverageKeys.coverageMinimumStmtTotal := 95,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution := false
  )
}
 
  lazy val root = Project(appName, file("."))
  .settings(
    name := appName,
    organization := "uk.gov.hmrc",
    scalaVersion := "2.12.15",
    PlayKeys.playDefaultPort := 6702,
    majorVersion := 0,
    libraryDependencies ++= AppDependencies(),
    publishingSettings,
    scoverageSettings,
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
    .settings(
      IntegrationTest / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-eT"),
      IntegrationTest / unmanagedSourceDirectories ++= Seq(baseDirectory.value / "it", baseDirectory.value / "test-common"),
      inConfig(IntegrationTest)(scalafixConfigSettings(IntegrationTest))
      )
  .settings(scalacOptions ++= Seq("-deprecation", "-feature", "-Ypartial-unification"))
  .disablePlugins(JUnitXmlReportPlugin)
  .enablePlugins(PlayScala, SbtDistributablesPlugin)

def oneForkedJvmPerTest(tests: Seq[TestDefinition]) = {
  tests.map { test =>
    new Group(test.name, Seq(test), SubProcess(ForkOptions().withRunJVMOptions(Vector(s"-Dtest.name=${test.name}"))))
  }
}