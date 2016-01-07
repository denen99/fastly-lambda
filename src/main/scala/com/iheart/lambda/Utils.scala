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
                    statusCode: Int,
                    hitMiss: String,
                    eventType: String = "FastlyDebug")

object Utils {

  //implicit Class to convert case class to JSON
  implicit class logEntryToJson(l: Seq[LogEntry]) {
    implicit val formats = DefaultFormats + FieldSerializer[LogEntry]()

    def asJ = write(l)
  }

  val conf = ConfigFactory.load()

  def getEventType(hostname: String) = {
    val key = "event-types." + hostname
    conf.hasPath(key) match {
      case true => conf.getString(key)
      case false => conf.getString("default")
    }
  }

  def parseDate(d: String): Long = {
    val fmt = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")
    val res = fmt.parse(d)
    res.getTime / 1000
  }

  def parseRecord(line: String): Option[LogEntry] = {
    val fastlyHost = """[^ ]+\s+([^ ]+)\s+AmazonS3\[\d+\]\:""".r
    val date = """(\S{3}\,\s*\d{1,2}\s+\S{3}\s+\d{4}\s+\d{2}\:\d{2}\:\d{2}\s+\S+)""".r
    val statusCode = """(\d{3})""".r
    val ip = """(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})?""".r
    val hitmiss = """(\S+)""".r
    val url = """([^ ]+)""".r
    val hostname = """(\S+)""".r
    val httpMethod = """(\S+)""".r

    val regex = s"$fastlyHost\\s+$ip\\s+$date\\s+$httpMethod\\s+$url\\s+$hostname\\s+$statusCode\\s+$hitmiss"

    val p = Pattern.compile(regex)
    val matcher = p.matcher(line)

    if (matcher.find())
     Some(LogEntry(matcher.group(1),
                   matcher.group(2),
                   parseDate(matcher.group(3)),
                   matcher.group(4),
                   matcher.group(5),
                   matcher.group(6),
                   matcher.group(7).toInt,
                   matcher.group(8)))
    else
     None
  }
}
