import com.github.catalystcode.fortis.spark.streaming.rss.RSSInputDStream
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

object RSSDemo {
  def main(args: Array[String]) {
    // TODO: download and try to run(ALL MEMBERS)
    // TODO: solve the problem with empty content
    val durationSeconds = 15
    val conf = new SparkConf().setAppName("RSS Spark Application").setIfMissing("spark.master", "local[*]")
    val sc = new SparkContext(conf)
    val ssc = new StreamingContext(sc, Seconds(durationSeconds))
    sc.setLogLevel("ERROR")
    // TODO: fit the model
    // SMS: Azat, choose the relevant model(Large Movie Review Dataset/Sentiment Tree Bank/UCI Sentiment Labelled Sentences/Twitter Sentiment) and try to learn it

    val urlCSV = args(0)
    val urls = urlCSV.split(",")
    val stream = new RSSInputDStream(urls, Map[String, String](
      "User-Agent" -> "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36"
    ), ssc, StorageLevel.MEMORY_ONLY, pollingPeriodInSeconds = durationSeconds)
    stream.foreachRDD(rdd=>{
      rdd.foreach(entry => {
        //TODO: normalize input data and predict
        // normalize = lowercase + delete shitty punctuation -> to vectors
        println(entry.title)
        println(entry.uri)
        print("content is ->")
        // TODO: look, content is empty
        entry.content.foreach(entry1 => {
          print(entry1.value)
        })
        println()
      })
      val spark = SparkSession.builder().appName(sc.appName).getOrCreate()
      import spark.sqlContext.implicits._
      rdd.toDS().show()
    })

    // run forever
    ssc.start()
    ssc.awaitTermination()
  }
}
