# Fastly-Lambda 

A scala app to run on Amazon Lambda for Java that parses realtime logs from Amazon S3 shipped from Fastly and into NewRelic Insights.

# 1. Configuration 

Copy src/main/resources/application.conf.example to src/main/application.conf for production and src/main/application.test.conf for your unit tests

## Event Types

 In application.conf there is a block "event-types" that is used to map your logEntry to a NewRelic Insight "Event Type".  An Event Type in NewRelic Insights is how they filter your event data into different "buckets".  Currently the way this works is there is expected to be a "hostname" field in the regex section below.  That hostname is looked up here and if it is not found, the key "default" is used.  The "default" key is required.
 
## NewRelic 

There is a newRelic configuration that requires your API key and Insight URL that the app should post to.  These fields are required.

## Regex 

The regex block is how we dynamically map a logfile entry to an Event.  The way this works is there is a simple case class called LogEntry with a single param called fields that is a Map[String,Any].  The fields from the logfile are parsed , converted into a Map and ultimately posted as JSON.  

The first required field is "pattern".  This is the regex that will get matched against your logfile record.  Take note of the "groupings" in your regex as this is how you need to reference your field names.

The numbers simply represent a mapping of the regex group number [See Matcher](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Matcher.html#group().  There is no real limit to the number of fields you can have.  Just ensure that you have the ordering correct.

There are 2 special fieldnames that are used in this model, "hostname" and "timestamp".   Hostname is used to lookup in the event-types {} configuration block to get a NewRelic Insight Event Type.  If no hostname field is provided, then it uses the event-types.default configuration parameter.

The second special field is "timestamp".  NewRelic can accept a timestamp field that correlates to the timestamp of the event, versus the time of the API request.  Additionally, you need to specify a "dateformat" config parameter which will allow the app to convert the date format in the logfile to a proper Epoch time format. If there is no timestamp in the provided fields, NewRelic will just use the timestamp of the event.

# 2. Testing and Building

You can create an application.test.conf to simulate your actual configuration.  You can then modify any of the unit tests with some of your own data to confirm your regexes are working as expected.  Simply run `sbt test` once you update.  


 To build the application, simply type `sbt assembly` in the root and upload the jar to Lambda.  The jar is likely to exceed the 10MB upload limit, so you are going to have to upload the jar to S3 and paste in an S3 URL to Lambda for the new app.
 


# 3. Deploying 

## Lambda 

A few things to keep in mind when deploying your app in Lambda.  

When configuring the Lambda app, make sure you use the following string for the "Handler" property:

    com.iheart.lambda.Main::handleEvent
    
Additionally, make sure you have enough memory to handle your logfiles (we use 512Mb to be safe).  A few rounds of testing should show you in the console how much memory your app is using.  Also, i would safely bump up the timeout to the max of 59 seconds.  Some of the jvm warmup times can be quite slow, coupled with the time it takes to read a potentially large file off of S3.  We noticed, that sending data more frequently is actually a lot more performant, b/c lambda is not constantly restarting a new jvm, but re-using a warm one.    

Also, remember, you need to configure the source S3 bucket with the right permissions to invoke your Lambda app.  Under "Event Sources" in your Lambda app you should see an S3 bucket that reacts to "Object Created".

## NewRelic
Once you are wired up you should see data in NewRelic almost instantly.  Keep in mind Fastly supports sending a lot of custom fields like cache HIT/MISS, Fastly Datacenter, Referrers, etc.  All really valuable stuff to give you fine grain visibility into your data.  You can then run super cool queries like this that will show the cache hitrate for a URL regex

    SELECT count(uri) FROM Fastly SINCE 1 HOUR AGO where uri like ‘%/someuri%’ FACET hitMiss TIMESERIES