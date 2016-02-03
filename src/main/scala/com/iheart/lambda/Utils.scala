package com.iheart.lambda

import java.text.SimpleDateFormat
import java.util.regex.{Matcher, Pattern}
import org.json4s.{FieldSerializer, DefaultFormats}
import org.json4s.native.Serialization.write
import com.typesafe.config._
import play.Logger


case class LogEntry(fields: Map[String,Any])

object Utils  {

  import com.iheart.lambda.AmazonHelpers._

  //implicit Class to convert case class to JSON
  implicit class logEntryToJson(l: Seq[LogEntry]) {
    implicit val formats = DefaultFormats + FieldSerializer[LogEntry]()

    def asJ = write(l.map(_.fields))
  }

  type EmptyResponse = String

  val conf = ConfigFactory.load()
  val regex = conf.getString("regex.pattern")
  val pattern = Pattern.compile(regex)
  val insightApiKey = conf.getString("newrelic.apikey")
  val insightUrl = conf.getString("newrelic.apiUrl")


  /******************************************
    * parses application.conf for the eventType
    * to send to NewRelic using hostname
   ******************************************** */
  def getEventType(hostname: String) = {
    val key = "event-types." + hostname
    conf.hasPath(key) match {
      case true => conf.getString(key)
      case false => conf.getString("event-types.default")
    }
  }

  /****************************************
    * Reads a file from S3, parses it and returns
    * a sequence of Option[LogEntry]
  **********************************************/
  def parseLogFile(bucket: String, key: String): Seq[Option[LogEntry]] = {
    val data = readFileFromS3(bucket,key)
    data.map(parseRecord(_)).toSeq
  }


  /******************************************
    * Date format helper to convert date in log
    * to EPOCH format
  **********************************************/
  def parseDate(date: String): Long = {
    val fmt = new SimpleDateFormat(conf.getString("regex.dateformat"))
    val res = fmt.parse(date)
    res.getTime / 1000
  }


  /*****************************************************************
  * 2 keys in the map are special, hostname and timestamp.  NewRelic
  * uses the timestamp field in the JSON as the date they store.  The
  * hostname field is used to map to a custom eventType field. The
  * eventType field is how NewRelic stores different events inside
  * of Insights.
  ********************************************************************/
  def formatValue(key: String, value: Any): Map[String,Any] = key match {
    case "timestamp" => Map(key -> parseDate(value.asInstanceOf[String]))
    case "hostname" => Map(key -> value, "eventType" -> getEventType(key))
    case _ => Map(key -> value)
  }

  /*****************************************************
  * NewRelic requires a field called eventType ,
  * so we ensure its there
  ******************************************************/
  def ensureEventType(m: Map[String,Any]) = m.get("eventType") match {
    case None => Map("eventType" -> conf.getString("event-types.defualt"))
    case _ => Map()
  }

  /**************************************************
  * compiles Regex against log entry to build a
  * a map used to create a LogEntry class
  **************************************************/

  def buildMap(matcher: Matcher,count: Int, m: Map[String,Any] = Map()): Map[String,Any] = count match {
    case 0 => m ++ ensureEventType(m)
    case _ if conf.hasPath("regex." + count.toString) =>
             val key = conf.getString("regex." + count.toString)
             buildMap(matcher,count-1, m ++ formatValue(key,matcher.group(count)))
    case _ => buildMap(matcher,count-1,m)  //no regex.COUNT in application.conf
  }

  /**********************************************************
  * This is the method that gets passes an entry from
  * the logfile, parses it and returns an Option[LogEntry]
  ************************************************************/
  def parseRecord(line: String): Option[LogEntry] = {

    val matcher = pattern.matcher(line)

    if (matcher.find())
      Some(LogEntry(buildMap(matcher,matcher.groupCount())))
    else {
     sendCloudWatchLog(line)
     None
    }

  }
}
