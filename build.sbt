name := "scratch"

version := "1.0"
cancelable in Global := true
connectInput in run := true
fork in run := true
scalaVersion := "2.12.1"
libraryDependencies ++= Seq("core", "akka", "aws", "circe"
) map (x ⇒ "com.linktargeting.elasticsearch" %% s"elasticsearch-$x" % "0.5.53")