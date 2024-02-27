name := "Purple-Sapphier"

scalaVersion := "2.12.13"

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots")
)

libraryDependencies ++= Seq(
    "edu.berkeley.cs" %% "chisel3" % "3.4.3",
    "edu.berkeley.cs" %% "chiseltest" % "0.3.3" % "test"
)

scalacOptions ++= Seq(
      "-Xsource:2.11",
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit"
      // Enables autoclonetype2 in 3.4.x (on by default in 3.5)
    //   "-P:chiselplugin:useBundlePlugin"
    )