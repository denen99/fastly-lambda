package com.iheart.lambda
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.s3.event.S3EventNotification._
import org.joda.time.DateTime
import scala.collection.JavaConversions._

trait AmazonStub {

  val bucketName = "test-bucket"

  val reqParams = new RequestParametersEntity("1.1.1.1")
  val respElem = new ResponseElementsEntity("X-Amzid2","X-amz-reqid")
  val userIdentity = new UserIdentityEntity("principalId")
  val s3BuckEnt = new S3BucketEntity(bucketName,userIdentity,"arn")
  val s3ObjEnt = new S3ObjectEntity("key",100.toLong,"etag","versionid")
  val s3Ent = new S3Entity("configid",s3BuckEnt,s3ObjEnt,"schema")



  def validEvent = {
    val eventRec = new S3EventNotificationRecord("us1e-c",
      "eventName",
      "eventSource",
      new DateTime().toString,
      "v2",reqParams,respElem,s3Ent,userIdentity)
    new S3Event(List(eventRec))
  }

  def invalidEvent = {
    val eventRec = new S3EventNotificationRecord("us1e-c",
      "eventName",
      "eventSource",
      "BAD_DATE",
      "v2",reqParams,respElem,s3Ent,userIdentity)
    new S3Event(List(eventRec))
  }

}
