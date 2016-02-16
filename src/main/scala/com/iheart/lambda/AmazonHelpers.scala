package com.iheart.lambda

import java.text.SimpleDateFormat
import java.util.Date

import com.amazonaws.services.logs.AWSLogsClient
import com.amazonaws.services.logs.model._
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GetObjectRequest
import scala.collection.JavaConverters._
import scala.io.Source


object AmazonHelpers {

  val s3Client = new AmazonS3Client()
  val cwlClient = new AWSLogsClient()
  val cwlLogGroup = "/aws/lambda/fastlyLogProcessorSkips"
  //val cwlLogStream = "Skips"

  def cwlLogStream() = {
    val s = new SimpleDateFormat("YYYY-MMDD-HH-mm")
    s.format(new Date())
  }

  def readFileFromS3(bucket: String, key: String) = {
    val s3Object = s3Client.getObject(new GetObjectRequest(bucket,key))
    Source.fromInputStream(s3Object.getObjectContent).getLines()
  }

  def getCloudSeqToken(logStream: String) = {
    val req = new DescribeLogStreamsRequest(cwlLogGroup).withLogStreamNamePrefix(logStream)
    val descResult = cwlClient.describeLogStreams(req)
    if (descResult != null && descResult.getLogStreams != null && !descResult.getLogStreams.isEmpty) {
      descResult.getLogStreams.asScala.last.getUploadSequenceToken
    }
    else {
      println("Creating log stream " + logStream)
      cwlClient.createLogStream(new CreateLogStreamRequest(cwlLogGroup,logStream))
      null
    }
  }

  def sendCloudWatchLog(log: String) = {
    println("Skipping cloudwatch log: " + log)
    val logStream = cwlLogStream()
    val token = getCloudSeqToken(logStream)
    println("token is : " + token)
    val event = new InputLogEvent
    event.setTimestamp(new Date().getTime)
    event.setMessage(log)
    val req = new PutLogEventsRequest(cwlLogGroup,logStream,List(event).asJava)
    req.setSequenceToken(token)
    cwlClient.putLogEvents(req)
  }
}
