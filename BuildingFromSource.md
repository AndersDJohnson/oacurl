# Building and Running Oacurl From Source #
If you intend to contribute to oacurl, or you just want to use the latest features you can also build oacurl from source.

## Prerequisites ##
  * [Mercurial](http://mercurial.selenic.com/)
  * [Maven](http://maven.apache.org/)
  * Java 1.6.

## Getting the Source and Building ##
```
$ hg clone https://oacurl.googlecode.com/hg/ oacurl
$ cd oacurl
$ mvn assembly:assembly
```

## Logging In ##
```
$ ./oacurl login --buzz
```
More info: LoginTool

## Fetching a URL ##
```
$ ./oacurl https://www.googleapis.com/buzz/v1/activities/@me/@consumption?prettyprint=1
```
More info: FetchTool
