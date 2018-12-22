lazy val V = new {
  val Http4s = "0.18.21"
  val Specs2 = "4.1.0"
  val Logback = "1.2.3"
  val Lucene = "7.6.0"
}

lazy val root = (project in file("."))
  .settings(
    organization := "com.example",
    name := "subscription-switcher",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.12.7",
    libraryDependencies ++= Seq(
      "org.http4s"      %% "http4s-blaze-server" % V.Http4s,
      "org.http4s"      %% "http4s-circe"        % V.Http4s,
      "org.http4s"      %% "http4s-dsl"          % V.Http4s,
      "org.specs2"     %% "specs2-core"          % V.Specs2 % "test",
      "ch.qos.logback"  %  "logback-classic"     % V.Logback,
      "org.apache.lucene" % "lucene-core"        % V.Lucene,
    ),
    addCompilerPlugin("org.spire-math" %% "kind-projector"     % "0.9.9"),
    addCompilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.2.4")
  )

