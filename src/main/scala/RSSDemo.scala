import com.github.catalystcode.fortis.spark.streaming.rss.RSSInputDStream
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import java.io.File

import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator
import org.apache.spark.ml.feature.{HashingTF, IDF, Tokenizer}

import scala.collection.mutable.ListBuffer
import scala.io.Source

object RSSDemo {
  def getListOfFiles(dir: String): List[String] = {
    val file = new File(dir)
    file.listFiles.filter(_.isFile).map(_.getAbsolutePath).toList
  }

  def main(args: Array[String]) {
    println("LETS START!")
    val durationSeconds = 10
    val conf = new SparkConf().setAppName("RSS Spark Application").setIfMissing("spark.master", "local[*]")
    val sc = new SparkContext(conf)
    val ssc = new StreamingContext(sc, Seconds(durationSeconds))
    sc.setLogLevel("ERROR")

    val datasetTrainPosDir = "dataset/train/pos"
    val datasetTrainNegDir = "dataset/train/neg"
    var trainData: ListBuffer[(Double, String)] = ListBuffer()
    var id = 0

    getListOfFiles(datasetTrainPosDir).foreach(file => {
      val source = Source.fromFile(file)
      trainData.append((1.0, source.getLines().mkString))
      source.close()
      id += 1
    })

    getListOfFiles(datasetTrainNegDir).foreach(file => {
      val source = Source.fromFile(file)
      trainData.append((0.0, source.getLines().mkString))
      source.close()
      id += 1
    })

    val spark = SparkSession.builder().appName(sc.appName).getOrCreate()
    val training = spark.createDataFrame(trainData.toList).toDF("label", "text")


    val tokenizer = new Tokenizer().setInputCol("text").setOutputCol("words")
    val hashingTF = new HashingTF().setInputCol(tokenizer.getOutputCol).setOutputCol("rawFeatures")
    val idf = new IDF().setInputCol("rawFeatures").setOutputCol("features")
    val lr = new LogisticRegression().setMaxIter(10).setRegParam(0.001)

    val pipeline = new Pipeline().setStages(Array(tokenizer, hashingTF, idf, lr))

    println("Fitting")
    val model = pipeline.fit(training)
    println("Fitted")

    val datasetTestPosDir = "dataset/test/pos"
    val datasetTestNegDir = "dataset/test/neg"
    var testData: ListBuffer[(Double, String)] = ListBuffer()
    id = 0

    getListOfFiles(datasetTestPosDir).foreach(file => {
      val source = Source.fromFile(file)
      testData.append((1.0, source.getLines().mkString))
      source.close()
      id += 1
    })

    getListOfFiles(datasetTestNegDir).foreach(file => {
      val source = Source.fromFile(file)
      testData.append((0.0, source.getLines().mkString))
      source.close()
      id += 1
    })

    val test = spark.createDataFrame(testData.toList).toDF("label", "text")
    //    print(test + "test data ")
    val predictions = model.transform(test)
    val evaluator = new BinaryClassificationEvaluator().setLabelCol("label").setRawPredictionCol("prediction").setMetricName("areaUnderROC")

    val accuracy = evaluator.evaluate(predictions)
    //println(accuracy)


    val urlCSV = args(0)
    val urls = urlCSV.split(",")
    // https://queryfeed.net/twitter?q=%23today&title-type=tweet-text-full&order-by=recent&geocode=
    // TODO: this query gave info for #today and write tweet text in the title, so now we don't need context.(!!! use it !!! you can change hashtag)
    // SMS: Ruslan, run test data using fitted model and check by hands how our model can predict the result!
    //urls.foreach(url => println("URL=" + url))
    val stream = new RSSInputDStream(urls, Map[String, String](
      "User-Agent" -> "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36"
    ), ssc, StorageLevel.MEMORY_ONLY, pollingPeriodInSeconds = durationSeconds)

    stream.foreachRDD(rdd => {

      val spark = SparkSession.builder().appName(sc.appName).getOrCreate()
      import spark.sqlContext.implicits._

      var data = rdd.toDS().select("title")
//      data.collect().foreach(d => println(d.toString()))
      var y_pred = model.transform(data)


      println('---------------------------)
      y_pred.select("prediction").collect().foreach(s => println(s.toString().slice(1, s.toString().length - 1)))
      //rdd.toDS().show()
      println('----------------------)
    })

    // run forever
    ssc.start()
    ssc.awaitTermination()
  }
}
