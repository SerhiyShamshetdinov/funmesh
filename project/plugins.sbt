addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.10.0") // https://github.com/sbt/sbt-header
// In order to create or update file headers, execute the >headerCreate
// In order to check whether all files have headers (for example for CI), execute the >headerCheck

addSbtPlugin("com.github.sbt" % "sbt-license-report" % "1.6.1") // https://github.com/sbt/sbt-license-report
// >dumpLicenseReport
// This dumps a report of all the licenses used in a project, with an attempt to organize them. These are dumped, by default, to the target/license-reports directory

// see also https://github.com/retronym/sbt-onejar for whole jars packaging, or others for WAR
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.2.0") // https://github.com/sbt/sbt-assembly
// >assembly
// to package all libs into resulting "uber jar"
