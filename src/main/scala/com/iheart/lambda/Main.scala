package com.iheart.lambda

import java.net.URLDecoder
import com.iheart.lambda.Utils._
import play.api.libs.ws.ning.NingWSClient
import scala.collection.JavaConverters._
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.lambda.runtime.Context
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global


class Main {

  val wsClient = NingWSClient()

  def sendToNewRelic(entries: Seq[Option[LogEntry]]) = {
    val validEntries = entries.flatMap(y => y)
    validEntries.isEmpty match {
      case true => Nil
      case _ =>
        val json = validEntries.asJ
        //println("Sending JSON: " + json)
        val respF = wsClient.url(insightUrl)
          .withHeaders(("X-Insert-Key", insightApiKey), ("Content-Type", "application/json"))
          .post(json)
        Await.result(respF,Duration.Inf)
    }

  }

  /*********************************************
    * This is the function Lambda calls with the
    * S3 callback
  ************************************************/
  def handleEvent(event: S3Event, context: Context) = {
    event.getRecords.asScala.foreach { record =>
       val bucket = record.getS3.getBucket.getName
       val key = URLDecoder.decode(record.getS3.getObject.getKey,"UTF-8")
       println("Received key : " + key)
       sendToNewRelic(parseLogFile(bucket,key))
    } 
  } 
}
