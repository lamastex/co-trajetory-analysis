import CoTrajectoryUtils._
import Swapmob._
import org.apache.spark.sql.Dataset
import org.apache.spark.graphx._
import java.io._
import scala.collection.JavaConversions._
import geotrellis.proj4._

object ExampleSynthCDR {
  
  val spark = SparkSessionHolder.spark
  import spark.implicits._
  
  /* Define coordinate transformation from ITM to Lat/Lng */
  private val src = CRS.fromEpsgCode(2039)
  private val dst = LatLng
  private val transformation = Transform(src, dst)
  
  /* Helper function runs SwapMob on set of trajectories 
   * for a fixed grid size and time resolution */
  private def runExample(cotraj: Dataset[Trajectory]) = {
    /* Open file for normal output */
    val output = new PrintWriter(new File("output/synthetic-cdr-test.txt"))

    /* Compute number of trajectories and number of measurements */
    val numTrajectories: Long = cotraj.count
    val numMeasurements: Long = cotraj.map(_.measurements.length).reduce(_ + _)

    output.println("Number of trajectories: " + numTrajectories.toString)
    println("Number of trajectories: " + numTrajectories.toString)

    output.println("Number of measurements: " + numMeasurements.toString)
    println("Number of measurements: " + numMeasurements.toString)

    /* Compute possible swaps */ 
    val partitioning: (Long, Double) = (60L * 5, 5L)
    val swaps: Dataset[Swap] = cotraj
      .map(_.partitionDistinct(partitioning))
      .swaps(partitioning._1)
      .cache

    val numSwaps: Long = swaps.count
    
    output.println("Number of possible swaps: " + numSwaps.toString)
    println("Number of possible swaps: " + numSwaps.toString)
    output.close()
  }

  /* Helper function runs SwapMob on set of trajectories 
   * for a sequence of grid size and time resolution parameters */
  private def runGridSizeTimeTest(cotraj: Dataset[Trajectory]) = {
    /* Open file for normal output */
    val output = new PrintWriter(new File("output/synthetic-cdr-grid-size-time.txt"))

    /* Compute number of trajectories and number of measurements */
    val numTrajectories: Long = cotraj.count
    val numMeasurements: Long = cotraj.map(_.measurements.length).reduce(_ + _)

    output.println("Number of trajectories: " + numTrajectories.toString)
    println("Number of trajectories: " + numTrajectories.toString)

    output.println("Number of measurements: " + numMeasurements.toString)
    println("Number of measurements: " + numMeasurements.toString)

    /* Compute possible swaps for different values of grid's size 
     * and time step. 
     * Chosen values approximately correspond to the sequence used 
     * in (Salas et al., 2020), but transformed to meters
     */ 
    output.println("Number of possible swaps for different grid size and time step")
    println("Number of possible swaps for different grid size and time step")

    output.println("Time step\tGrid size\tNumber of swaps")
    println("Time step\tGrid size\tNumber of swaps")

    for(timeStep <- Seq(5, 10, 15, 20, 25)) 
      for(gridSize <- Seq(111, 55, 27, 11, 8.3, 5.5, 1.11, 0.555)) {
        val partitioning: (Long, Double) = (60L * timeStep, 1000L * gridSize)
        
        val swaps: Dataset[Swap] = cotraj
          .map(_.partitionDistinct(partitioning))
          .swaps(partitioning._1)
          .cache

        val numSwaps: Long = swaps.count

        output.println(Seq(timeStep, gridSize, numSwaps).mkString("\t"))
        println(Seq(timeStep, gridSize, numSwaps).mkString("\t"))
    }
    
    output.close()
  }
  
  def exampleITM() = {
    /* Parse the co-trajectory */
    val cotraj: Dataset[Trajectory] = Parse
      .syntheticCDR("data/examples/synthetic_cdr_trajectories.csv")
      .cache

    runExample(cotraj)
  }

  def exampleLatLng() = {
    /* Parse the co-trajectory */
    val cotraj: Dataset[Trajectory] = Parse
      .syntheticCDR("data/examples/synthetic_cdr_trajectories.csv", transformation)
      .cache

    runExample(cotraj)
  }
  
  def gridSizeTimeTestITM() = {
    /* Parse the co-trajectory */
    val cotraj: Dataset[Trajectory] = Parse
      .syntheticCDR("data/examples/synthetic_cdr_trajectories.csv")
      .cache

    runGridSizeTimeTest(cotraj)
  }

  def gridSizeTimeTestLatLng() = {
    /* Parse the co-trajectory */
    val cotraj: Dataset[Trajectory] = Parse
      .syntheticCDR("data/examples/synthetic_cdr_trajectories.csv", transformation)
      .cache

    runGridSizeTimeTest(cotraj)
  }
  
}
