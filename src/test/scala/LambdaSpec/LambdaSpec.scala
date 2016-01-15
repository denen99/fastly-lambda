package com.iheart.lambda

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
      result.get.hostname mustEqual "www.domain.com"
      result.get.hitMissShield mustEqual "HIT"
      result.get.hitMissEdge mustEqual "MISS"
      result.get.httpMethod mustEqual "GET"
      result.get.eventType mustEqual "FastlyDebug"
      result.get.ip mustEqual "1.2.3.4"
      result.get.statusCode mustEqual "200"
      result.get.uri mustEqual "/path/hello.txt"
      result.get.timestamp mustEqual 1452278159
      result.get.fastlyHost mustEqual "cache-atl6234"
      result.get.referrer mustEqual "(null)"
    }

    "correctly parse all formats of HIT/MISS" in {
      val record1 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 HIT, HIT (null)"
      val record2 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 HIT (null)"
      val record3 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 MISS, MISS (null)"
      val record4 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 MISS (null)"
      val record5 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 HIT, MISS (null)"
      val record6 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 MISS, HIT (null)"

      val res1 = parseRecord(record1)
      res1 mustNotEqual None and(res1.get.hitMissShield mustEqual "HIT") and(res1.get.hitMissEdge mustEqual "HIT")

      val res2 = parseRecord(record2)
      res2 mustNotEqual None and(res2.get.hitMissShield mustEqual "HIT") and(res2.get.hitMissEdge mustEqual "NONE")

      val res3 = parseRecord(record3)
      res3 mustNotEqual None and(res3.get.hitMissShield mustEqual "MISS") and(res3.get.hitMissEdge mustEqual "MISS")

      val res4 = parseRecord(record4)
      res4 mustNotEqual None and(res4.get.hitMissShield mustEqual "MISS") and(res4.get.hitMissEdge mustEqual "NONE")

      val res5 = parseRecord(record5)
      res5 mustNotEqual None and(res5.get.hitMissShield mustEqual "HIT") and(res5.get.hitMissEdge mustEqual "MISS")

      val res6 = parseRecord(record6)
      res6 mustNotEqual None and(res6.get.hitMissShield mustEqual "MISS") and(res6.get.hitMissEdge mustEqual "HIT")

    }

  }
}