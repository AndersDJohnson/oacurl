A Java URL fetching tool for OAuth requests which behaves similar to cURL. Oacurl currently supports  [OAuth v1](http://tools.ietf.org/html/rfc5849) and [OAuth WRAP](http://wiki.oauth.net/w/page/12238537/OAuth-WRAP)

This tool was developed for investigating and debugging Google’s APIs, but it can be used to interact with any OAuth endpoint.

## Quick Start ##
The easiest way to use oacurl is to run the classes from the precompiled jars available for download.

  1. [Download](http://code.google.com/p/oacurl/downloads/list) the latest oacurl jar
  1. From the folder where you downloaded it, run the LoginTool to launch a web browser and complete the OAuth dance. Your authorization refresh token will be saved to a file for future requests.
```
$ java -cp oacurl-1.2.0.jar com.google.oacurl.Login --buzz
```
  1. Run the FetchTool to fetch an OAuth protected resource
```
$ java -cp oacurl-1.2.0.jar com.google.oacurl.Fetch \
'https://www.googleapis.com/buzz/v1/activities/@me/@consumption?prettyprint=1'
```

Alternatively, you can also [build and run oacurl from source](BuildingFromSource.md).
