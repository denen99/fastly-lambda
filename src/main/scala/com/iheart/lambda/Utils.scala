package com.iheart.lambda

import java.text.SimpleDateFormat
import java.util.Date
import java.util.regex.Pattern
import com.amazonaws.services.logs.model.{InputLogEvent, PutLogEventsRequest}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.logs._
import com.amazonaws.services.logs.model._
import org.json4s.{FieldSerializer, DefaultFormats}
import org.json4s.native.Serialization.write
import com.typesafe.config._
import scala.collection.JavaConversions._


import scala.io.Source

case class LogEntry(fastlyHost: String,
                    ip: String,
                    timestamp: Long,
                    httpMethod: String,
                    uri: String,
                    hostname: String,
                    statusCode: String,
                    hitMiss: String,
                    referrer: String,
                    eventType: String = "FastlyDebug")

object Utils {

  //implicit Class to convert case class to JSON
  implicit class logEntryToJson(l: Seq[LogEntry]) {
    implicit val formats = DefaultFormats + FieldSerializer[LogEntry]()

    def asJ = write(l)
  }

  val conf = ConfigFactory.load()
  val s3Client = new AmazonS3Client()
  val cwlClient = new AWSLogsClient()
  val cwlLogGroup = "/aws/lambda/fastlyLogProcessorSkips"
  val cwlLogStream = "Skips"

  val fastlyHost = """[^ ]+\s+([^ ]+)\s+AmazonS3\[\d+\]\:""".r
  val date = """(\S{3}\,\s*\d{1,2}\s+\S{3}\s+\d{4}\s+\d{2}\:\d{2}\:\d{2}\s+\S+)""".r
  val statusCode = """(\d{3})""".r
  val ip = """(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})?""".r
  val hitmiss = """(HIT|MISS)(?:\s*,\s*(?:HIT|MISS))*""".r
  val url = """([^ ]+)""".r
  val hostname = """(\S+)""".r
  val httpMethod = """(\S+)""".r
  val regex = s"$fastlyHost\\s+$ip\\s+$date\\s+$httpMethod\\s+$url\\s+$hostname\\s+$statusCode\\s+$hitmiss\\s+$url"
  val pattern = Pattern.compile(regex)

  val insightApiKey = conf.getString("newrelic.apikey")
  val insightUrl = conf.getString("newrelic.apiUrl")


  def getEventType(hostname: String) = {
    val key = "event-types." + hostname
    conf.hasPath(key) match {
      case true => conf.getString(key)
      case false => conf.getString("event-types.default")
    }
  }

  def parseLogFile(bucket: String, key: String): Seq[Option[LogEntry]] = {
    val s3Object = s3Client.getObject(new GetObjectRequest(bucket,key))
    val data = Source.fromInputStream(s3Object.getObjectContent).getLines()
    data.map(parseRecord(_)).toSeq
  }

  def getCloudSeqToken = {
    val req = new DescribeLogStreamsRequest(cwlLogGroup)
    val res: DescribeLogStreamsResult = cwlClient.describeLogStreams(req)
    val streams = res.getLogStreams
    streams.last.getUploadSequenceToken
  }

  def sendCloudWatchLog(log: String) = {
     println("Skipping cloudwatch log: " + log)
     val event = new InputLogEvent
     event.setTimestamp(new Date().getTime)
     event.setMessage(log)
     val req = new PutLogEventsRequest(cwlLogGroup,cwlLogStream,List(event))
     req.setSequenceToken(getCloudSeqToken)
     cwlClient.putLogEvents(req)
  }

  def parseDate(d: String): Long = {
    val fmt = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")
    val res = fmt.parse(d)
    res.getTime / 1000
  }

  def parseRecord(line: String): Option[LogEntry] = {

    val matcher = pattern.matcher(line)

    if (matcher.find())
     Some(LogEntry(matcher.group(1),
                   matcher.group(2),
                   parseDate(matcher.group(3)),
                   matcher.group(4),
                   matcher.group(5),
                   matcher.group(6),
                   matcher.group(7),
                   matcher.group(8),
                   matcher.group(9),
                   getEventType(matcher.group(6))))
    else {
     sendCloudWatchLog(line)
     None
    }

  }
}
