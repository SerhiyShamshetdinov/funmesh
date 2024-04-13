/*
 * Usage of sbt-header plugin for the code license:
 *  sbt> headerCheckAll  // to check all configurations (Compile & Test) source headers (including non active Scala version sources)
 *  sbt> headerCreateAll // to update or create all configurations (Compile & Test) sources headers (including non active Scala version sources)
 *
 * Usage of sbt-license-report plugin for the code license:
 *  sbt> dumpLicenseReport // This dumps a report of all the licenses used in a project, with an attempt to organize them.
 *                         // These are dumped, by default, to the target/license-reports directory
 *
 * Usage of assembly plugin to package all libs into resulting "uber jar"
 *  sbt>assembly
 */

import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.headerLicense
import sbt.Keys.{crossScalaVersions, homepage, mappings, packageDoc, testOptions}
import sbt.io.Path.basic

val scala213 = "2.13.13"
lazy val supportedScalaVersions = List(scala213)

ThisBuild / scalaVersion := supportedScalaVersions.last
ThisBuild / version := "0.1.1"
ThisBuild / description := "funmesh - Function Mesh, the application to play with the service mesh"
ThisBuild / homepage := Some(url("https://github.com/SerhiyShamshetdinov/funmesh"))
ThisBuild / licenses += "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
ThisBuild / startYear := Some(2024)
ThisBuild / organization := "ua.org.sands"
ThisBuild / organizationName := "PE Shamshetdinov Serhiy (Kyiv, Ukraine)"
ThisBuild / organizationHomepage := Some(url("http://www.sands.org.ua/"))
ThisBuild / developers := List(Developer("SerhiyShamshetdinov", "Serhiy Shamshetdinov", "serhiy@sands.org.ua", url("https://github.com/SerhiyShamshetdinov")))
//ThisBuild / resolvers += "Sonatype S01 OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"

lazy val commonSettings = Seq[Setting[?]](
  crossScalaVersions := supportedScalaVersions,
  //autoScalaLibrary := false,
  //  Compile / unmanagedSourceDirectories ++= versionedSourceDirs((Compile / sourceDirectory).value, scalaVersion.value),
  //  Test / unmanagedSourceDirectories ++= versionedSourceDirs((Test / sourceDirectory).value, scalaVersion.value),
  scalacOptions := allVersionsScalacOptions,
  Test / fork := true, // default is false
  Test / testForkedParallel := true, // required to run tests in the 1 TestGroup in parallel in 1 forked JVM
  Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"), // -Pn does not work with SBT
  // for sbt-header plugin to add all Scala version source files
  Compile / headerSources := ((Compile / sourceDirectory).value ** "*.scala").get,
  Test / headerSources := ((Test / sourceDirectory).value ** "*.scala").get,
  headerLicense := Some(HeaderLicense.Custom( // don't move to ThisBuild! it'll not work
    """funmesh - Function Mesh, the application to play with the service mesh
      |
      |Copyright (c) 2024 Serhiy Shamshetdinov (Kyiv, Ukraine)
      |
      |Licensed under the Apache License, Version 2.0 (the "License");
      |you may not use this file except in compliance with the License.
      |You may obtain a copy of the License at
      |
      |    http://www.apache.org/licenses/LICENSE-2.0
      |
      |Unless required by applicable law or agreed to in writing, software
      |distributed under the License is distributed on an "AS IS" BASIS,
      |WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
      |See the License for the specific language governing permissions and
      |limitations under the License.
      |
      |See the NOTICE.md file distributed with this work for
      |additional information regarding copyright ownership and used works.
      |""".stripMargin
  ))
)

def getSysPropOrEnvVal(key: String): Option[String] = scala.sys.props.get(key).orElse(scala.sys.env.get(key))

def scalaCompiler(version: String) = "org.scala-lang" % "scala-compiler" % version

def scalaCompilerDependencies(configurations: String*) = libraryDependencies ++= Seq(
  scalaCompiler(scalaVersion.value)
).map(configurations.foldLeft(_)(_ % _))

def scalaTestDependencies(configurations: String*) = libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.18", // 3.2.9 fails with strange error for 2.12.12 in sbt +sugar-bms/test: framework does not start due to an "illegal override" error
  scalaCompiler(scalaVersion.value) // for the scala.tools.reflect.{ToolBox, ...}
).map(configurations.foldLeft(_)(_ % _))

val tapirVersion = "1.10.0"

// primary project
lazy val root = Project("funmesh", file("."))
    .settings(commonSettings)
    .settings(
      scalaTestDependencies(/*"compile-internal,test-internal"*/),
//      publish / skip := true,
//      Test / aggregate := false,
//      publish / aggregate := false,

      // include files in the `funmesh` jar  // it is commented due to assembly plugin: this files are added by Compile / unmanagedResources later here vvv
//      Compile / packageBin / mappings ++= packageFileMappings,
      // include files in the `funmesh` source jar
      Compile / packageSrc / mappings ++= packageFileMappings,
      // include files in the `funmesh` java-doc jar
      Compile / packageDoc / mappings ++= packageFileMappings,
      libraryDependencies ++= Seq(
        "com.softwaremill.sttp.tapir" %% "tapir-netty-server" % tapirVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-prometheus-metrics" % tapirVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-sttp-client" % tapirVersion,
        "ch.qos.logback" % "logback-classic" % "1.5.3",
        "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % tapirVersion % Test,
        "com.softwaremill.sttp.client3" %% "circe" % "3.9.5" % Test,
        "org.scalatest" %% "scalatest" % "3.2.18" % Test,
        "org.scalatestplus" %% "mockito-5-10" % "3.2.18.0" % Test
      ),
      assembly / assemblyCacheOutput := false,
      assembly / assemblyOutputPath := baseDirectory.value / "bin" / s"${name.value}.jar", // !!! with file name otherwise it does not work at least at Windows
      assembly / mainClass := Some("ua.org.sands.funmesh.FunMesh"),
      assembly / test := (Test / test).value,
      assembly / assemblyPackageScala / assembleArtifact := true,
      assembly / assemblyPackageDependency / assembleArtifact := true,
      Compile / unmanagedResources ++= packageFileMappings.map(_._1),

      assembly / assemblyMergeStrategy := {
        // required by tapir, see https://tapir.softwaremill.com/en/latest/docs/openapi.html#using-swaggerui-with-sbt-assembly
        case PathList("META-INF", "maven", "org.webjars", "swagger-ui", "pom.properties") => MergeStrategy.singleOrError
        case PathList("META-INF", "resources", "webjars", "swagger-ui", _*)               => MergeStrategy.singleOrError

        // libs conflicts
        case PathList("META-INF", "io.netty.versions.properties")       => MergeStrategy.concat("\n--- merged by assembly plugin ---\n")
        case PathList("module-info.class")                              => MergeStrategy.discard // safely discard both: runtime error when ones required but no non determinate result
        case PathList("META-INF", "versions", "9", "module-info.class") => MergeStrategy.discard // rename is not permitted for classes
        //    case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
        //    case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
        //    case "application.conf"                            => MergeStrategy.concat
        //    case "unwanted.txt"                                => MergeStrategy.discard
        case x => // following is to use default strategy function of the plugin:
          val oldStrategy = (assembly / assemblyMergeStrategy).value
          oldStrategy(x)
      }
    )

lazy val packageFileMappings: Seq[(File, String)] = List(
  file("LICENSE"),
  file("NOTICE.md"),
  file("readme.md")/*,
  file("CONTRIBUTING.md")*/
).pair(basic, errorIfNone = true)

val allVersionsScalacOptions = Seq(
  "-encoding", "UTF-8",
  //"-target:jvm-1.8", // jvm-1.8 is default for 2.11-2.13: for jar compatibility with all JDK starting 8. Keep in mind that util.Properties.javaVersion returns version of JVM run, not this target version. So used Java methods set depends on run JDK, not on this target version :)
  "-unchecked",
  // "-Ymacro-debug-lite",
  "-deprecation",
  "-language:_"
)
