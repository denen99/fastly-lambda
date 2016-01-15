package com.iheart.lambda

import java.text.SimpleDateFormat
import java.util.regex.Pattern
import org.json4s.{FieldSerializer, DefaultFormats}
import org.json4s.native.Serialization.write
import com.typesafe.config._

case class LogEntry(fastlyHost: String,
                    ip: String,
                    timestamp: Long,
                    httpMethod: String,
                    uri: String,
                    hostname: String,
                    statusCode: String,
                    hitMissShield: String,
                    hitMissEdge: String,
                    referrer: String,
                    eventType: String = "FastlyDebug")

object Utils  {

  import com.iheart.lambda.AmazonHelpers._

  //implicit Class to convert case class to JSON
  implicit class logEntryToJson(l: Seq[LogEntry]) {
    implicit val formats = DefaultFormats + FieldSerializer[LogEntry]()

    def asJ = write(l)
  }

  type EmptyResponse = String

  val conf = ConfigFactory.load()
  val fastlyHost = """[^ ]+\s+([^ ]+)\s+AmazonS3\[\d+\]\:""".r
  val date = """(\S{3}\,\s*\d{1,2}\s+\S{3}\s+\d{4}\s+\d{2}\:\d{2}\:\d{2}\s+\S+)""".r
  val statusCode = """(\d{3})""".r
  val ip = """(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})?""".r
  val hitmiss = """((?:HIT|MISS)(?:\s*,\s*(?:HIT|MISS))*?)""".r
  val urlR = """([^ ]+)""".r
  val hostname = """(\S+)""".r
  val httpMethod = """(\S+)""".r
  val regex = s"$fastlyHost\\s+$ip\\s+$date\\s+$httpMethod\\s+$urlR\\s+$hostname\\s+$statusCode\\s+$hitmiss\\s+$urlR"
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
    val fmt = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")
    val res = fmt.parse(date)
    res.getTime / 1000
  }

  def hitMissShield(s: String) =
    s.split(",")(0)


  def hitMissEdge(s: String) = s.split(",").length match {
    case i if i >= 2 => s.split(",")(1).replace(" ","")
    case  _ => "NONE"
  }


  /**************************************************
    * compiles Regex against log entry to build a
    * LogEntry class
  **************************************************/
  def parseRecord(line: String): Option[LogEntry] = {

    val matcher = pattern.matcher(line)

    if (matcher.find())
     Some(LogEntry(fastlyHost = matcher.group(1),
                   ip = matcher.group(2),
                   timestamp = parseDate(matcher.group(3)),
                   httpMethod = matcher.group(4),
                   uri = matcher.group(5),
                   hostname = matcher.group(6),
                   statusCode = matcher.group(7),
                   hitMissShield = hitMissShield(matcher.group(8)),
                   hitMissEdge = hitMissEdge(matcher.group(8)),
                   referrer = matcher.group(9),
                   eventType = getEventType(matcher.group(6))))
    else {
     sendCloudWatchLog(line)
     None
    }

  }
}
