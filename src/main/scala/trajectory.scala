import com.graphhopper.util.GPXEntry

import spray.json._

import scala.collection.JavaConverters._
import scala.util.{Try, Success, Failure}

import java.text.SimpleDateFormat
import java.util.Date

import org.apache.spark.sql.functions._

object TrajectoryHelper {
  /* All of the below methods assume that the given list is sorted in
   * time */

  /* Return the jumpchain for a list of locations. This is the chain of
   * locations removing any succesive duplicates. */
  def jumpchain(locations: Array[LocationPartition]):
      Array[LocationPartition] =
    if (locations.isEmpty)
      Array()
    else
      locations(0) +: locations.sliding(2)
        .collect{ case Array(a, b) if a != b => b }.toArray

  /* Return the jumpchain times of a list of partitioned measurements.
   * This is a list of times where each time represents the time it
   * stays in one particular location. */
  def jumpchainTimes(measurementPartitions: Array[MeasurementPartition]):
      Array[Long] =
    if (measurementPartitions.isEmpty)
      Array()
    else
      measurementPartitions
        .tail
        .foldLeft((Array(): Array[Long], measurementPartitions.head)){
          case ((ts: Array[Long], m1:MeasurementPartition),
            m2:MeasurementPartition) =>
            if (m1.location != m2.location)
              (ts :+ (m2.time - m1.time), m2)
            else
              (ts, m1)
        }._1

  /* Return the list of transitions for a list of partitioned
   * measurements. The list of transition consists of pairs of
   * locations together with the time spent in the first location
   * before going to the second. */
  def transitions(measurementPartitions: Array[MeasurementPartition]):
      Array[(LocationPartition, LocationPartition, Long)] =
    if (measurementPartitions.length < 2)
      Array()
    else
      measurementPartitions
        .tail
        .foldLeft((Array(): Array[(LocationPartition,
          LocationPartition, Long)], measurementPartitions.head)) {
          case ((list: Array[(LocationPartition, LocationPartition, Long)],
            m1: MeasurementPartition), m2: MeasurementPartition) =>
            if (m1.location != m2.location)
              (list :+ (m1.location, m2.location, m2.time - m1.time), m2)
            else
              (list, m1)
        }._1
}

/* Holds a trajectory represented by an id and an array of measurements */
case class Trajectory(id: Int, measurements: Array[Measurement]) {
  override def equals(that: Any): Boolean =
    that match {
      case that: Trajectory => id == that.id &&
        measurements.length == that.measurements.length &&
        measurements.zip(that.measurements).forall{
          case (i, j) => i == j
        }
      case _ => false
    }

  /* The methods for Trajectory assumes that the array of Measurements
   * is sorted with respect to time. This method makes sure that this
   * is the case.*/
  def normalize(): Trajectory = Trajectory(id, measurements.sortBy(_.time))

  /* Return the partitioned trajectory given by getting the partition
   * for all measurements in the trajectory. */
  def partition(partitioning: (Long, Double)): TrajectoryPartition =
    TrajectoryPartition(id, measurements.map(_.partition(partitioning)))

  /* Return the partitioned trajectory given by getting the partition
   * for all measurements in the trajectory. For each time partition
   * it only keeps one measurement, the first one in the list. */
  def partitionDistinct(partitioning: (Long, Double)): TrajectoryPartition = {
    val measurementPartitions = measurements.map(_.partition(partitioning))

    if (measurementPartitions.isEmpty)
      TrajectoryPartition(id, measurementPartitions)
    else
      TrajectoryPartition(id, measurementPartitions.head
        +: measurementPartitions
        .sliding(2)
        .collect{case Array(m1, m2) if m1.time != m2.time => m2}
        .toArray)
  }

  /* Keep only the measurements occurring on the given date. The date
   * should be given as a string in the format yyyy-MM-dd. This method
   * assumes that the time for the measurements is Unix-time. */
  def filterDate(dateStr: String): Trajectory = {
    val format = new SimpleDateFormat("yyyy-MM-dd")

    val date = format.parse(dateStr)

    val start = (date.getTime/1000).toLong
    val end = start + 24*60*60

    /* Consider using a binary search to find the region for the date.
     * This would likely be a performance improvement for long
     * trajectories. */
    Trajectory(id, measurements.filter(m => m.time >= start && m.time < end))
  }

  /* Keep only the measurements occurring withing the given box. */
  def filterBox(box: Array[(Double, Double)]): Trajectory = {
    Trajectory(id, measurements.filter(_.location.isInBox(box)))
  }

  /* Splits the measurements of the array by their date. This method
   * assumes that the time for the measurements is Unix-time. Returns
   * an array of tuples where the first element corresponds to the
   * Unix-time for the beginning of the date and the second element to
   * the corresponding trajectory. Notice that all returned
   * trajectories keep the same id as the original. */
  def splitByDate(): Array[(Long, Trajectory)] =
    measurements
      .groupBy(m => m.time - m.time%(24*60*60))
      .toArray
      .map(g => (g._1, Trajectory(id, g._2)))

  /* Return the jumpchain of the trajectory. This is the chain of
   * locations for the trajectory, removing any succesive
   * duplicates. */
  def jumpchain(partitioning: Double): (Int, Array[LocationPartition]) =
    (id, TrajectoryHelper
      .jumpchain(measurements.map(_.location.partition(partitioning))))

  /* Return the jumpchain times of a trajectory. This is a list of times
   * where each time represents the time the trajectory stays in one
   * particular location. */
  def jumpchainTimes(partitioning: Double): (Int, Array[Long]) =
    (id, TrajectoryHelper
      .jumpchainTimes(measurements
        .map(m => MeasurementPartition(m.time,
          m.location.partition(partitioning)))))

  /* Return the list of transitions for a trajectory. The list of
   * transition consists of pairs of locations together with the time
   * spent in the first location before going to the second. */
  def transitions(partitioning: Double):
      (Int, Array[(LocationPartition, LocationPartition, Long)]) =
    (id, TrajectoryHelper
      .transitions(measurements
        .map(m => MeasurementPartition(m.time,
          m.location.partition(partitioning)))))

  /* Return a map matched version of the trajectory. The trajectory is
   * matched using Open Street Map data. For more details see the
   * GraphHopperHelper class.*/
  def mapMatch(mm: com.graphhopper.matching.MapMatching =
    GraphHopperHelper.getMapMatcher): Trajectory = {

    val gpxEntries = measurements
      .map{
        m => new GPXEntry(m.location.latitude, m.location.longitude, m.time)}
      .toList
      .asJava

    val mr = Try(mm.doWork(gpxEntries))

    val matchedLocations = mr match {
      case Success(result) => GraphHopperHelper.extractLocations(result)
      case Failure(_) => Array(): Array[Location] // When no match can be made
    }

    /* The time for the measurements are lost in the map matching. Instead
     * of trying to recreate reasonable times we here just use a time
     * starting at 0 and increasing by one for every measurement. */
    val matchedMeasurements = matchedLocations
      .zipWithIndex
      .map{case (location, time) => Measurement(time, location)}

    Trajectory(id, matchedMeasurements)
  }

  import Visualize.LonLatJsonProtocol._

  def toJson(): String =
    Visualize.LonLatJson(coordinates =
      measurements.map(m => (m.location.longitude, m.location.latitude)))
    .toJson
    .prettyPrint

}

/* Holds a trajectory represented by an id and an array of partitions */
case class TrajectoryPartition(id: Int,
  partitions: Array[MeasurementPartition]) {
  override def equals(that: Any): Boolean =
    that match {
      case that: TrajectoryPartition => id == that.id &&
        partitions.length == that.partitions.length &&
        partitions.sameElements(that.partitions)
      case _ => false
    }

  /* The methods for TrajectoryPartition assumes that the array of
   * partitioned measurements is sorted with respect to time. This
   * method makes sure that this is the case.*/
  def normalize() = TrajectoryPartition(id, partitions.sortBy(_.time))

  /* Return the Trajectory given by unpartitioning all partitioned
   * measurements in the trajectory. */
  def unpartition(partitioning: (Long, Double)): Trajectory =
    Trajectory(id, partitions.map(_.unpartition(partitioning)))

  /* Return the jumpchain of the trajectory. This is the chain of
   * locations for the trajectory, removing any succesive
   * duplicates. */
  def jumpchain(): (Int, Array[LocationPartition]) =
    (id, TrajectoryHelper.jumpchain(partitions.map(_.location)))

  /* Return the jumpchain times of a trajectory. This is a list of times
   * where each time represents the time the trajectory stays in one
   * particular location. */
  def jumpchainTimes(): (Int, Array[Long]) =
    (id, TrajectoryHelper.jumpchainTimes(partitions))

  /* Return the list of transitions for a trajectory. The list of
   * transition consists of pairs of locations together with the time
   * spent in the first location before going to the second. */
  def transitions():
      (Int, Array[(LocationPartition, LocationPartition, Long)]) =
    (id, TrajectoryHelper.transitions(partitions))
}
