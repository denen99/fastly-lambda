package com.iheart.lambda

import com.typesafe.config.{ConfigFactory, Config}
import org.specs2.mutable._
import com.iheart.lambda.Utils._
import play.Logger
import scala.collection.JavaConversions._

class LambdaSpec extends Specification with AmazonStub {

  //case class LogEntry(fastlyHost: String,
//  ip: String,
//  timestamp: Long,
//  httpMethod: String,
//  uri: String,
//  hostname: String,
//  statusCode: String,
//  hitMiss: String,
//  referrer: String,
//  eventType: String = "FastlyDebug")


  "Lambda Application" should {

    "correctly parse a valid log entry" in {
      val record = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 HIT, MISS (null)"
      val result = parseRecord(record)
      result mustNotEqual None
      result.get.fields("hostname") mustEqual "www.domain.com"
      result.get.fields("hitMissShield") mustEqual "HIT"
      result.get.fields("hitMissEdge") mustEqual "MISS"
      result.get.fields("httpMethod") mustEqual "GET"
      result.get.fields("eventType") mustEqual "FastlyDebug"
      result.get.fields("ip") mustEqual "1.2.3.4"
      result.get.fields("statusCode") mustEqual "200"
      result.get.fields("uri") mustEqual "/path/hello.txt"
      result.get.fields("timestamp") mustEqual 1452278159
      result.get.fields("fastlyHost") mustEqual "cache-atl6234"
      result.get.fields("referrer") mustEqual "(null)"
    }

    "correctly parse all formats of HIT/MISS" in {
      val record1 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 HIT, HIT http://referrer.com"
      val record2 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 HIT http://referrer.com"
      val record3 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 MISS, MISS http://referrer.com"
      val record4 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 MISS http://referrer.com"
      val record5 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 HIT, MISS http://referrer.com"
      val record6 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 MISS, HIT http://referrer.com"

      val res1 = parseRecord(record1)
      res1 mustNotEqual None and(res1.get.fields("hitMissShield") mustEqual "HIT") and(res1.get.fields("hitMissEdge") mustEqual "HIT")

      val res2 = parseRecord(record2)
      res2 mustNotEqual None and(res2.get.fields("hitMissShield") mustEqual "HIT") and(res2.get.fields("hitMissEdge") mustEqual null)

      val res3 = parseRecord(record3)
      res3 mustNotEqual None and(res3.get.fields("hitMissShield") mustEqual "MISS") and(res3.get.fields("hitMissEdge") mustEqual "MISS")

      val res4 = parseRecord(record4)
      res4 mustNotEqual None and(res4.get.fields("hitMissShield") mustEqual "MISS") and(res4.get.fields("hitMissEdge") mustEqual null)

      val res5 = parseRecord(record5)
      res5 mustNotEqual None and(res5.get.fields("hitMissShield") mustEqual "HIT") and(res5.get.fields("hitMissEdge") mustEqual "MISS")

      val res6 = parseRecord(record6)
      res6 mustNotEqual None and(res6.get.fields("hitMissShield") mustEqual "MISS") and(res6.get.fields("hitMissEdge") mustEqual "HIT")

    }

  }
}