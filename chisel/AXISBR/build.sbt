name := "Purple-Sapphier"

scalaVersion := "2.13.8"

addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % "3.5.4" cross CrossVersion.full)

resolvers ++= Resolver.sonatypeOssRepos("releases")

libraryDependencies ++= Seq(
    "edu.berkeley.cs" %% "chisel3" % "3.5.4",
    "edu.berkeley.cs" %% "chiseltest" % "0.5.4"
)

scalacOptions ++= Seq(
      "-Xsource:2.13",
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit"
      // Enables autoclonetype2 in 3.4.x (on by default in 3.5)
    //   "-P:chiselplugin:useBundlePlugin"
    )