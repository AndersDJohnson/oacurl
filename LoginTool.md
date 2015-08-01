# Introduction #

The `com.google.oacurl.Login` class connects to an OAuth service provider and launch a browser to do the OAuth dance. It saves the access token and secret in a local file so that the [FetchTool](FetchTool.md) can later sign requests without any user interaction.

By default, LoginTool uses Google’s OAuth service provider and the “anonymous” consumer key and secret. It launches the system default browser and starts a local web server to receive the verifier token via callback URL redirect.

You will need to provide a scope via the `--scope` flag, or use either the `--buzz` or `--latitude` flags to set the right scope for those services.

# Details #

## Working with Buzz ##

Pass the `--buzz` flag to set up these behaviors for working with the Buzz API:

  * The scope will default to the read/write scope for Buzz
  * The service provider's “userAuthorizationUrl” will change to the special Buzz authorization page
  * The scope and consumer key will be passed as query parameters to the authorization page as “scope” and “domain,” respectively

## Working with Latitude ##

Pass the `--latitude` flag to set up these behaviors for working with Google Latitude:

  * The scope will default to the Latitude scope
  * The service provider's “userAuthorizationUrl” will change to the special Latitude authorization page
  * The consumer key will be passed as a query parameter to the authorization page as “domain”

Note that the Latitude API requires that you specify a consumer key and consumer secret. It does not work with “anonymous.”

You can use the `-P` flag to pass Latitude’s optional “location” and “granularity” parameters to the authorization page. E.g.: `-Pgranularity=city`.

## Setting a Scope ##

Google’s OAuth endpoint requires a “scope” URL to define the capabilities of the tokens it generates. You must set this via the `--scope` flag, or use the `--buzz` flag to set things up for Buzz.

## Consumer Information ##

Login will use the “anonymous” consumer key and secret by default, which is fine for playing around. To test with your app’s consumer information, do one of the following:

  * Call Login with `--consumer-key` and `--consumer-secret` arguments.
  * Make a Java properties file with “consumerKey” and “consumerSecret” properties and pass it in with the `--consumer` flag.

In either case, the consumer information will be saved in the “~/.oacurl.properties” file so that [FetchTool](FetchTool.md) will be able to use them.

Note: only HMAC-SHA1 signatures are supported right now.

## Launching a Browser ##

Login will launch the system’s default browser to show the OAuth login page. This is done via the [java.awt.Desktop](http://java.sun.com/javase/6/docs/api/java/awt/Desktop.html) API, which seems to work well on the Mac, probably Windows, and possibly Gnome, but maybe not in other situations.

To specify a particular browser, use the `--browser` flag with a path to its executable. Login will run it with a URL as its argument.

You can use the `--nobrowser` flag to keep Login from trying to run any browser. It will instead write the URL to `stdout`. You will need to visit that URL in a browser to continue with the OAuth process.

## Running a Server ##

Login runs an embedded Jetty web server on a free `localhost` port to receive the verify token redirect from the service provider. After you sign in to the service provider with your credentials, the service provider will redirect to this server to complete the process.

If you’re in a situation where visiting a local web server is not practical, you can pass the `--noserver` flag to keep it from starting up. Login will then prompt you to enter the verify token manually. Unfortunately, for Google’s endpoint this involves viewing source on the landing page and copying the token out of the `&lt;title&gt;` element.

The `--demo` flag will run a server that shows a fake “webapp” page with an OAuth login button. This button pops up a new window for the OAuth authorization flow, which is the preferred workflow for doing OAuth login.

## Custom Service Provider ##

Login defaults to using Google’s OAuth endpoint but should work equally well with other OAuth service providers. The `--service-provider` flag will let you point at a properties file with another service provider’s information. The properties file must have `requestTokenUrl`, `userAuthorizationUrl`, and `accessTokenUrl` keys.
