package com.iheart.lambda

import java.net.URLDecoder
import com.iheart.lambda.Utils._
import play.api.libs.ws.WSResponse
import play.api.libs.ws.ning.NingWSClient
import scala.collection.JavaConverters._
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.lambda.runtime.Context
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


class Main {

  val wsClient = NingWSClient()

  /**********************************************
    * make API call to NewRelic
    *
    * splitCount splits up the JSON posts so we
    * stay below the 5MB NewRelic limit
  **********************************************/
  def postJson(entries: Seq[LogEntry]) = {
    val json = entries.asJ
    val respF = wsClient.url(insightUrl)
      .withHeaders(("X-Insert-Key", insightApiKey), ("Content-Type", "application/json"))
      .post(json)
    Await.result(respF,Duration.create(10,"seconds"))
  }

  def sendToNewRelicChunk(entries: Seq[LogEntry], splitCount: Int, count: Int ): Unit = count match {
    case 0 => Future { postJson(entries)}
    case _ =>
              Future { postJson(entries.take(splitCount)) }
              sendToNewRelicChunk(entries.drop(splitCount),splitCount,count-1)
  }

  def sendToNewRelic(entries: Seq[Option[LogEntry]], splitCount: Int = 1000): Either[EmptyResponse,Unit] = {
    val validEntries = entries.flatMap(y => y)
    validEntries.isEmpty match {
      case true => Left("Skip")
      case _ =>
        val validEntries = entries.flatMap(y => y)
        Right(sendToNewRelicChunk(validEntries,splitCount,entries.length / splitCount))
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
       println("Received key " + key)
       sendToNewRelic(parseLogFile(bucket,key))
    } 
  } 
}
