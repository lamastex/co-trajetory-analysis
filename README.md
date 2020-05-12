# Mob-Spark
This is project aimed at analysing mobility data over Apache Spark.
This project extends from [Joel Dahne](https://github.com/Joel-Dahne/co-trajetory-analysis)'s 
**Co-Trajectory Analysis** package.

## Co-Trajectory Analysis
This is a package for performing analysis on collection of
trajectories, co-trajectories. This was started by [Joel Dahne](https://github.com/Joel-Dahne/co-trajetory-analysis)

## Quick Start
Make sure that the following is installed
* [sbt](https://www.scala-sbt.org/)
* [scala](https://www.scala-lang.org/)
* [spark](https://spark.apache.org/)

From the project root build it with

``` shell
sbt package
```

The first time this is done it downloads a lot of packages and can
thus take quite some time. You can perform the, very few, tests with

``` shell
sbt test
```

Most of the tests are in pure Scala and should most likely work. Some
of the tests are using Spark and if these do not work it could be a
problem with you local Spark installation.

To get it running in a local spark-shell use the command

``` shell
spark-shell --jars target/scala-2.11/co-traj_2.11-1.0.jar --packages com.graphhopper:map-matching:0.7.0,io.spray:spray-json_2.11:1.3.5,org.locationtech.geotrellis:geotrellis-gdal_2.11:3.3.0
```

You should then be able to run the first example

```
scala> Examples.example1
Number of trajectories: 5
Number of measurements: 60
Number of possible swaps: 6
Number of possible paths in the DAG: 49
Precomputing data
Computing number of paths for the whole graph
Computing chain of vertices
Computing number of paths trough measurements
Output data about number of paths through measurements to output/example1-1.csv
Number of paths trough the measurement m = MeasurementID(2,Measurement(13,[2.5])): 12
Precomputing data
Computing number of paths between start and end vertex
Number of steps to compute: 5
Every dot is 20 steps, 50 per line
Output data about number of paths starting and ending at a the same vertices as trajectory i to output/example1-2.csv
id = 0: 2
id = 1: 3
id = 2: 2
id = 3: 2
id = 4: 1
```

The other examples will not run since they need access to external
datasets, more information about how to get these can be found further
down.

## File structure
It is structured as a typical Scala project with the general code in
`src/main/scala/` and the test code in `src/test/scala/`. It follows
the Scala style of files starting with a capital letter corresponding
to a single class, or object, with the same name and files starting
with a lower-case letter corresponding to multiple, related, classes
or objects. The `data/` directory contains data for some
co-trajectories used in tests and examples. Output to files is in
general done to the `output/` directory.

## Class structure
The project consists of Classes, which hold data and methods working
on that data, and Objects, which only holds methods.

### Classes
All the classes are in project are either case classes, essentially
tuples with methods on, or implicit classes, renaming of other types
with added methods.

The case classes are
* Location(x: Array[Double])
* LocationPartition(x: Array[Int])
* Measurement(time: Long, location: Location)
* MeasurementID(id: Int, measurement: Measurement)
* MeasurementPartition(time: Long, location: LocationPartition)
* MeasurementPartitionID(id: Int, partition: MeasurementPartition)
* Trajectory(id: Int, measurements: Array[Measurement])
* TrajectoryPartition(id: Int, partitions: Array[MeasurementPartition])
* Swap(time: Long, ids: Array[Int])
* LonLatJson(`type`: String = "MultiPoint", coordinates: Array[(Double, Double)])

The implicit classes are
* CoTrajectory(cotraj: Dataset[Trajectory])
* CoTrajectoryPartition(cotraj: Dataset[TrajectoryPartition])
* Swaps(swaps: Dataset[Swap])
### Objects
The objects contain general methods, some of the are helper objects
mainly meant for implementing internal methods whereas other for use
by the end user. The objects mainly meant for internal use are
* CoTrajectoryUtils
* GraphHopperHelper
* TrajectoryHelper
* SparkSessionHolder

And the ones for general use are
* Parse
* Swapmob
* Examples
* Visualize

