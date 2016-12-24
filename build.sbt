name := "dutchman-sample"

version := "1.0"
cancelable in Global := true
connectInput in run := true
outputStrategy := Some(StdoutOutput)
fork in run := true
scalaVersion := "2.12.1"

resolvers += "caliberweb repo" at "https://s3-us-west-2.amazonaws.com/repo.caliberweb.com/release"

libraryDependencies ++= Seq("core", "akka", "aws", "circe"
) map (x ⇒ "com.caliberweb" %% s"dutchman-$x" % "0.5.1")

libraryDependencies ++= Seq("ch.qos.logback" % "logback-classic" % "1.1.7")