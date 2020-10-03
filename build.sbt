organization := "todo.yaroot"
scalaVersion := "2.13.2"

fork in run := true
addCompilerPlugin("org.typelevel"    % "kind-projector"     % "0.11.0" cross CrossVersion.full)
addCompilerPlugin("com.olegpy"      %% "better-monadic-for" % "0.3.1" cross CrossVersion.binary)
addCompilerPlugin("com.github.cb372" % "scala-typed-holes"  % "0.1.5" cross CrossVersion.full)

testFrameworks += new TestFramework("munit.Framework")
scalafmtOnCompile := true
cancelable in Global := true

libraryDependencies ++= Seq(
  "org.typelevel"                %% "cats-effect" % "2.1.4",
  "com.github.ben-manes.caffeine" % "caffeine"    % "2.8.5",
  "org.scalameta"                %% "munit"       % "0.7.12" % Test
)
