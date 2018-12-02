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
  // get list of files inside 'dir' directory
  def getListOfFiles(dir: String): List[String] = {
    val file = new File(dir)
    file.listFiles.filter(_.isFile).map(_.getAbsolutePath).toList
  }

  // convert string to normal format to learn better and increase true predict probability
  def normalizeString(s: String): String = {
    s.toLowerCase().replaceAll("\n", " "). // delete new lines
      replaceAll("[!@#$%^&*()<>,.?/:;'_+*~=|-]", " "). // erase symbols
      replaceAll("http(.*?)\\s", ""). // delete links
      replaceAll(" ( )*", " ") // delete >1 spaces
  }

  def main(args: Array[String]) {
    val durationSeconds = 15
    val conf = new SparkConf().setAppName("RSS Spark Application").setIfMissing("spark.master", "local[*]")
    val sc = new SparkContext(conf)
    val ssc = new StreamingContext(sc, Seconds(durationSeconds))
    sc.setLogLevel("ERROR")

    val datasetTrainPosDir = "dataset/train/pos" // path to positive training dataset
    val datasetTrainNegDir = "dataset/train/neg" // path to negative training dataset
    var trainData: ListBuffer[(Double, String)] = ListBuffer()

    // get text from files and add to trainData
    getListOfFiles(datasetTrainPosDir).foreach(file => {
      val source = Source.fromFile(file)
      trainData.append((1.0, normalizeString(source.getLines().mkString)))
      source.close()
    })

    // get text from files and add to trainData
    getListOfFiles(datasetTrainNegDir).foreach(file => {
      val source = Source.fromFile(file)
      trainData.append((0.0, normalizeString(source.getLines().mkString)))
      source.close()
    })

    val spark = SparkSession.builder().appName(sc.appName).getOrCreate()
    val training = spark.createDataFrame(trainData.toList).toDF("label", "text")

    val tokenizer = new Tokenizer().setInputCol("text").setOutputCol("words") // string tokenizer
    val hashingTF = new HashingTF().setInputCol(tokenizer.getOutputCol).setOutputCol("rawFeatures") // term-frequency
    val idf = new IDF().setInputCol("rawFeatures").setOutputCol("features") // idf
    val lr = new LogisticRegression().setMaxIter(10).setRegParam(0.001) // logistic regression

    val pipeline = new Pipeline().setStages(Array(tokenizer, hashingTF, idf, lr)) // pipeline: tokenizer -> tf -> idf -> logistic regression

    println("Fitting")
    val model = pipeline.fit(training) // fitting the model
    println("Fitted")

    //        val datasetTestPosDir = "dataset/test/pos"
    //        val datasetTestNegDir = "dataset/test/neg"
    //        var testData : ListBuffer[(Double, String)] = ListBuffer()
    //
    //        getListOfFiles(datasetTestPosDir).foreach(file => {
    //          val source = Source.fromFile(file)
    //          testData.append((1.0, source.getLines().mkString))
    //          source.close()
    //        })
    //
    //        getListOfFiles(datasetTestNegDir).foreach(file => {
    //          val source = Source.fromFile(file)
    //          testData.append((0.0, source.getLines().mkString))
    //          source.close()
    //        })
    //
    //        val test = spark.createDataFrame(testData.toList).toDF("label", "text")
    //
    //        val predictions = model.transform(test)
    //        val evaluator = new BinaryClassificationEvaluator().setLabelCol("label").setRawPredictionCol("prediction").setMetricName("areaUnderROC")
    //
    //        val accuracy = evaluator.evaluate(predictions)
    //        println(accuracy)


    val urlCSV = args(0)
    val urls = urlCSV.split(",")
    // https://queryfeed.net/twitter?q=%23today&title-type=tweet-text-full&order-by=recent&geocode=
    urls.foreach(url => println("URL=" + url))
    val stream = new RSSInputDStream(urls, Map[String, String](
      "User-Agent" -> "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36"
    ), ssc, StorageLevel.MEMORY_ONLY, pollingPeriodInSeconds = durationSeconds)

    stream.foreachRDD(rdd => {
      val spark = SparkSession.builder().appName(sc.appName).getOrCreate()
      import spark.sqlContext.implicits._
      rdd.toDS().select("title").collect().foreach(text => {
        // get string and normalize to better format
        val temp = normalizeString(text.toString())
        println(temp)
        // predict and print the answer
        val test = Seq((1.0, temp)).toDF("label", "text")
        val prediction = model.transform(test) // predict negative/positive for current text
        println(prediction.first().getAs("prediction")) // negative = 0.0, positive = 1.0
      })
    })

    // run forever
    ssc.start()
    ssc.awaitTermination()
  }
}
