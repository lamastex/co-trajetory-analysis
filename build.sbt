name := "Co-traj"

version := "1.0"

sparkVersion := "2.4.4"
scalaVersion := "2.11.12"

sparkComponents += "sql"
sparkComponents += "graphx"

libraryDependencies += "io.spray" %% "spray-json" % "1.3.5"
libraryDependencies += "com.graphhopper" % "map-matching" % "0.7.0"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test
libraryDependencies += "org.locationtech.geotrellis" %% "geotrellis-gdal" % "3.3.0"
