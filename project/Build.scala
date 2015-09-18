import sbt.Keys._
import sbt._
import sbtrelease.ReleasePlugin
import sbtrelease.ReleasePlugin.ReleaseKeys
import com.typesafe.sbt.pgp.PgpKeys

object PluginBuild extends Build {

  val Org = "canve"
  val Scala = "2.11.4"
  val MockitoVersion = "1.9.5"
  val ScalatestVersion = "2.2.2"

  lazy val LocalTest = config("local") extend Test

  val appSettings = Seq(
    organization := Org,
    scalaVersion := Scala,
    crossScalaVersions := Seq("2.10.4", "2.11.7"),
    fork in Test := false,
    publishMavenStyle := false,
    parallelExecution in Test := false,
    scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8"),
    resolvers := ("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2") +: resolvers.value,
    concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
    javacOptions := Seq("-source", "1.6", "-target", "1.6"),
    libraryDependencies ++= Seq(
      "org.mockito" % "mockito-all" % MockitoVersion % "test",
      "org.scalatest" %% "scalatest" % ScalatestVersion % "test"
    )
  ) ++ ReleasePlugin.releaseSettings ++ Seq(
    ReleaseKeys.crossBuild := true,
    ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value
  )

  lazy val root = Project("compiler-plugin-unit-test-lib", file("."))
    .settings(name := "compiler-plugin-unit-test-lib")
    .settings(appSettings: _*)
    .settings(libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
    "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
    "org.joda" % "joda-convert" % "1.6" % "test",
    "joda-time" % "joda-time" % "2.3" % "test",
    "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2" % "test"
  )).settings(libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, scalaMajor)) if scalaMajor == 11 =>
        EnvSupport.setEnv("CrossBuildScalaVersion", "2.11.4")
        Seq("org.scala-lang.modules" %% "scala-xml" % "1.0.1")
      case _ =>
        EnvSupport.setEnv("CrossBuildScalaVersion", "2.10.4")
        Nil
    }
  })
}