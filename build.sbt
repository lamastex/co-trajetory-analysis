name := "Co-traj"

version := "1.0"

sparkVersion := "2.4.0"
scalaVersion := "2.11.12"

sparkComponents += "sql"

libraryDependencies += "com.graphhopper" % "map-matching" % "0.7.0"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test
