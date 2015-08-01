# Introduction #

The `com.google.oacurl.Fetch` class uses the access token and secret saved by LoginTool in order to make HTTP requests. It also reuses the consumer key and secret that were provided to LoginTool so that those don’t need to be entered a second time.

The simplest use of Fetch is to pass a URL as its one argument. Fetch will GET that URL and write the HTTP response’s body to `stdout`.

Since URLs often contain & characters which are also often escape by shells, it's recommended that you quote your URLs.

# Details #

## Customizing the HTTP request ##

By default, Fetch performs a GET on the provided URL. For a different method, use the `--method` flag (or `-X`, which matches curl).

POST and PUT requests read the request body from `stdin`.

By default, POST and PUT send a content type of “application/atom+xml," but this can be changed with the `--content-type` flag. In addition to normal MIME types, this parameter accepts the shorthands “ATOM,” “XML,” “JSON,” “CSV,” and “TEXT,” as well as a handful of image types and sends the appropriate MIME type for those formats.

Fetch also accepts the general `--header` (or `-H`) flag for setting an arbitrary header.

## Sending files ##

Rather than redirecting a file from `stdin`, you can use the `--file` (`-f`) flag to name a file to read in. This additionally:
  * Defaults the method to POST
  * Defaults the content type to one based on the file’s extension
  * Adds a “Slug” header with the file’s name

You can generate a “multipart/related” post (used in the [Picasa Web Albums API](http://code.google.com/apis/picasaweb/docs/2.0/developers_guide_protocol.html#PostPhotos)) with the `-R` flag:

```
   ./bin/oacurl -R photo.jpg -R "entry.xml;application/atom+xml" \
    'http://picasaweb.google.com/data/feed/api/user/default/albumid/default'
```

`-R` sets the method to POST by default and generates a multipart/related request from the files specified by the flags. You can let oacurl guess the MIME type based on file extension, or provide it after the semicolon.

## Controlling output ##

Fetch will write the response body to `stdout` by default. If the `--include` (`-i`) flag is set, it will write the response header as well.

In `--verbose` (`-v`) mode, Fetch will additionally log the request and response to `stderr`. This wire logging is currently a bit off, as it omits the blank line between the header and the body and also shows newlines at the end of each buffer read, not just where they are in the body. Nevertheless, it gives you a picture of what’s happening over the network.
