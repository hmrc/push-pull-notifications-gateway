#!/usr/bin/env bash
export SBT_OPTS="-XX:MaxMetaspaceSize=1G"
sbt clean compile coverage scalastyle scalafmtAll scalafixAll test it:test scalastyle coverageReport
unset SBT_OPTS
