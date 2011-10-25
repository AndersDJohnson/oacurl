// Copyright 2010 Google, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.oacurl;

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.oauth.OAuth;
import net.oauth.OAuth.Parameter;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.OAuthServiceProvider;
import net.oauth.client.OAuthClient;
import net.oauth.client.httpclient4.HttpClient4;
import net.oauth.http.HttpMessage;
import net.oauth.http.HttpResponseMessage;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.google.oacurl.LoginCallbackServer.TokenStatus;
import com.google.oacurl.dao.AccessorDao;
import com.google.oacurl.dao.ConsumerDao;
import com.google.oacurl.dao.ServiceProviderDao;
import com.google.oacurl.options.LoginOptions;
import com.google.oacurl.options.OAuthVersion;
import com.google.oacurl.util.LoggingConfig;
import com.google.oacurl.util.OAuthUtil;
import com.google.oacurl.util.PropertiesProvider;

/**
 * Main class for doing the initial OAuth dance to get an access token and
 * secret.
 *
 * @author phopkins@google.com
 */
public class Login {

  private static Logger logger = Logger.getLogger(Login.class.getName());

  public static void main(String[] args) throws Exception {
    LoginOptions options = new LoginOptions();
    try {
      options.parse(args);
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      System.exit(-1);
    }

    if (options.isHelp()) {
      new HelpFormatter().printHelp(" ", options.getOptions());
      System.exit(0);
    }

    if (options.isInsecure()) {
      SSLSocketFactory.getSocketFactory().setHostnameVerifier(new AllowAllHostnameVerifier());
    }

    LoggingConfig.init(options.isVerbose());
    if (options.isWirelogVerbose()) {
      LoggingConfig.enableWireLog();
    }

    ServiceProviderDao serviceProviderDao = new ServiceProviderDao();
    ConsumerDao consumerDao = new ConsumerDao(options);
    AccessorDao accessorDao = new AccessorDao();

    String serviceProviderFileName = options.getServiceProviderFileName();
    if (serviceProviderFileName == null) {
      if (options.isBuzz()) {
        serviceProviderFileName = "BUZZ";
      } else if (options.isBlogger()) {
        serviceProviderFileName = "BLOGGER";
      } else if (options.isWave()) {
        serviceProviderFileName = "WAVE";
      } else if (options.isLatitude()) {
        serviceProviderFileName = "LATITUDE";
      } else {
        serviceProviderFileName = "GOOGLE";
      }
    }

    // We have a wee library of service provider properties files bundled into
    // the resources, so we set up the PropertiesProvider to search for them
    // if the file cannot be found.
    OAuthServiceProvider serviceProvider = serviceProviderDao.loadServiceProvider(
        new PropertiesProvider(serviceProviderFileName,
            ServiceProviderDao.class, "services/").get());
    OAuthConsumer consumer = consumerDao.loadConsumer(
        new PropertiesProvider(options.getConsumerFileName()).get(), serviceProvider);
    OAuthAccessor accessor = accessorDao.newAccessor(consumer);

    OAuthClient client = new OAuthClient(new HttpClient4());

    LoginCallbackServer callbackServer = null;

    boolean launchedBrowser = false;

    try {
      if (!options.isNoServer()) {
        callbackServer = new LoginCallbackServer(options);
        callbackServer.start();        
      }

      String callbackUrl;
      if (options.getCallback() != null) {
        callbackUrl = options.getCallback();
      } else if (callbackServer != null) {
        callbackUrl = callbackServer.getCallbackUrl();
      } else {
        callbackUrl = null;
      }

      do {
        String authorizationUrl;
        switch (options.getVersion()) {
        case V1:
          authorizationUrl = getV1AuthorizationUrl(client, accessor, options, callbackUrl);
          break;
        case V2:
          authorizationUrl = getV2AuthorizationUrl(accessor, options, callbackUrl);
          break;
        case WRAP:
          authorizationUrl = getWrapAuthorizationUrl(accessor, options, callbackUrl);
          break;
        default:
          throw new AssertionError("Unknown version: " + options.getVersion());
        }

        if (!options.isNoServer()) {
          callbackServer.setAuthorizationUrl(authorizationUrl);
        }

        if (!launchedBrowser) {
          String url = options.isDemo() ? callbackServer.getDemoUrl() : authorizationUrl;
    
          if (options.isNoBrowser()) {
            System.out.println(url);
            System.out.flush();
          } else {
            launchBrowser(options, url);        
          }
  
          launchedBrowser = true;
        }
 
        accessor.accessToken = null;

        logger.log(Level.INFO, "Waiting for verification token...");
        String verifier;
        if (options.isNoServer()) {
          System.out.print("Verification token: ");
          BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
          verifier = "";
          while (verifier.isEmpty()) {
            String line = reader.readLine();
            if (line == null) {
              System.exit(-1);
            }
            verifier = line.trim();
          }
        } else {
          verifier = callbackServer.waitForVerifier(accessor, -1);
          if (verifier == null) {
            System.err.println("Wait for verifier interrupted");
            System.exit(-1);
          }        
        }
        logger.log(Level.INFO, "Verification token received: " + verifier);
        boolean success;
  
        switch (options.getVersion()) {
        case V1:
          success = fetchV1AccessToken(accessor, client, verifier);
          break;
        case V2:
        case WRAP:
          success = fetchV2AccessToken(accessor, client, callbackUrl, verifier, options.getVersion());
          break;
        default:
          throw new AssertionError("Unknown version: " + options.getVersion());
        }

        if (success) {
          if (callbackServer != null) {
            callbackServer.setTokenStatus(TokenStatus.VALID);
          }

          Properties loginProperties = new Properties();
          accessorDao.saveAccessor(accessor, loginProperties);
          consumerDao.saveConsumer(consumer, loginProperties);
          loginProperties.put("oauthVersion", options.getVersion().toString());
          new PropertiesProvider(options.getLoginFileName()).overwrite(loginProperties);
        } else {
          if (callbackServer != null) {
            callbackServer.setTokenStatus(TokenStatus.INVALID);
          }
        }
      } while (options.isDemo());
    } catch (OAuthProblemException e) {
      OAuthUtil.printOAuthProblemException(e);
    } finally {
      if (callbackServer != null) {
        callbackServer.stop();
      }
    }
  }

  private static boolean fetchV1AccessToken(OAuthAccessor accessor,
      OAuthClient client, String verifier)
      throws IOException, OAuthException, URISyntaxException {
    boolean success;

    List<OAuth.Parameter> accessTokenParams = OAuth.newList(
        OAuth.OAUTH_TOKEN, accessor.requestToken,
        OAuth.OAUTH_VERIFIER, verifier);
    logger.log(Level.INFO, "Fetching access token with parameters: " + accessTokenParams);

    try {
      OAuthMessage accessTokenResponse = client.getAccessToken(accessor, null, accessTokenParams);
      logger.log(Level.INFO, "Access token received: " + accessTokenResponse.getParameters());
      logger.log(Level.FINE, accessTokenResponse.getDump().get(HttpMessage.RESPONSE).toString());

      success = true;
    } catch (OAuthProblemException e) {
      if (e.getHttpStatusCode() == 400) {
        success = false;
      } else {
        throw e;
      }
    }

    return success;
  }

  private static boolean fetchV2AccessToken(OAuthAccessor accessor, OAuthClient client,
      String callbackUrl, String verifier, OAuthVersion version) throws IOException {
    OAuthConsumer consumer = accessor.consumer;

    boolean wrap = version == OAuthVersion.WRAP;
    
    if (callbackUrl != null) {
      // HACK(phopkins): The callback needs to be exactly the same, so put in
      // the "requestToken" originally generated in #getWrapAuthorizationUrl
      if (wrap) {
        callbackUrl = OAuth.addParameters(callbackUrl, OAuth.OAUTH_TOKEN, accessor.requestToken);
      }
    } else {
      callbackUrl = wrap ? "" : "urn:ietf:wg:oauth:2.0:oob";
    }

    List<OAuth.Parameter> accessTokenParams = OAuth.newList(
        wrap ? "wrap_client_id" : "client_id", consumer.consumerKey,
        wrap ? "wrap_client_secret" : "client_secret", consumer.consumerSecret,
        wrap ? "wrap_callback" : "redirect_uri", callbackUrl,
        wrap ? "wrap_verification_code" : "code", verifier);

    if (!wrap) {
      accessTokenParams.add(new OAuth.Parameter("grant_type", "authorization_code"));
    }
    
    logger.log(Level.INFO, "Fetching access token with parameters: " + accessTokenParams);

    String requestString = OAuth.formEncode(accessTokenParams);
    byte[] requestBytes = requestString.getBytes("UTF-8");
    InputStream requestStream = new ByteArrayInputStream(requestBytes);

    String url = consumer.serviceProvider.accessTokenURL;

    HttpMessage request = new HttpMessage("POST", new URL(url), requestStream);
    request.headers.add(new Parameter("Content-Type", "application/x-www-form-urlencoded"));
    request.headers.add(new Parameter("Content-Length", "" + requestString.length()));

    HttpResponseMessage response = client.getHttpClient().execute(request,
        client.getHttpParameters());
    InputStream bodyStream = response.getBody();
    BufferedReader reader = new BufferedReader(new InputStreamReader(bodyStream));

    StringBuilder respBuf = new StringBuilder();

    String line;
    while ((line = reader.readLine()) != null) {
      respBuf.append(line);
    }

    String resp = respBuf.toString();
    
    switch (version) {
    case WRAP:
      parseWrapTokenResponse(resp, accessor);
      break;
    case V2:
      parseV2TokenResponse(resp, accessor);
      break;
    }
    
    return accessor.accessToken != null;
  }

  private static void parseWrapTokenResponse(String resp, OAuthAccessor accessor) {
    List<Parameter> params = OAuth.decodeForm(resp);

    List<String> logList = new ArrayList<String>();

    for (Parameter param : params) {
      logList.add(param.getKey() + "=" + param.getValue());

      if (param.getKey().equals("wrap_access_token")) {
        accessor.accessToken = param.getValue();
        accessor.tokenSecret = "";
      }
    }

    logger.log(Level.INFO, "Access token response params: " + logList);
  }
  
  private static void parseV2TokenResponse(String resp, OAuthAccessor accessor) {
    JSONObject respObj = (JSONObject) JSONValue.parse(resp);
    
    String accessToken = (String) respObj.get("access_token");
    if (accessToken != null) {
      accessor.accessToken = accessToken;
      accessor.tokenSecret = "";
    }

    logger.log(Level.INFO, "Access token response: " + resp);
  }

  private static String getV1AuthorizationUrl(OAuthClient client,
      OAuthAccessor accessor, LoginOptions options, String callbackUrl)
      throws IOException, OAuthException, URISyntaxException {
    List<OAuth.Parameter> requestTokenParams = OAuth.newList();
    if (callbackUrl != null) {
      requestTokenParams.add(new OAuth.Parameter(OAuth.OAUTH_CALLBACK, callbackUrl));
    }

    if (options.getScope() != null) {
      requestTokenParams.add(new OAuth.Parameter("scope", options.getScope()));
    }

    if (accessor.consumer.consumerKey.equals("anonymous")) {
      requestTokenParams.add(new OAuth.Parameter("xoauth_displayname", "OACurl"));
    }

    logger.log(Level.INFO, "Fetching request token with parameters: " + requestTokenParams);
    OAuthMessage requestTokenResponse = client.getRequestTokenResponse(accessor, null,
        requestTokenParams);
    logger.log(Level.INFO, "Request token received: " + requestTokenResponse.getParameters());
    logger.log(Level.FINE, requestTokenResponse.getDump().get(HttpMessage.RESPONSE).toString());

    String authorizationUrl = accessor.consumer.serviceProvider.userAuthorizationURL;

    if (options.isBuzz()) {
      authorizationUrl = OAuth.addParameters(authorizationUrl,
          "scope", options.getScope(),
          "domain", accessor.consumer.consumerKey);

      if (accessor.consumer.consumerKey.equals("anonymous")) {
        authorizationUrl = OAuth.addParameters(authorizationUrl,
            "xoauth_displayname", "OACurl");
      }
    }

    if (options.isLatitude()) {
      authorizationUrl = OAuth.addParameters(authorizationUrl,
          "domain", accessor.consumer.consumerKey);
    }

    authorizationUrl = OAuth.addParameters(authorizationUrl, options.getParameters());

    authorizationUrl = OAuth.addParameters(
        authorizationUrl,
        OAuth.OAUTH_TOKEN, accessor.requestToken);
    return authorizationUrl;
  }

  private static String getWrapAuthorizationUrl(OAuthAccessor accessor,
      LoginOptions options, String callbackUrl) throws IOException {
    OAuthConsumer consumer = accessor.consumer;

    // This isn't used for anything fancy or cryptographic. Instead it's a
    // demonstration of best practice that the callback URL for OAuth-WRAP
    // should be unique for the request. Basically, XSRF protection.
    // We just use requestToken because it's handy and not use by OAuth-WRAP.
    String requestToken = Long.toHexString(new Random().nextLong());

    accessor.requestToken = requestToken;

    List<Parameter> authParams = new ArrayList<Parameter>();
    authParams.add(new OAuth.Parameter("wrap_client_id", consumer.consumerKey));

    if (callbackUrl != null) {
      authParams.add(new OAuth.Parameter("wrap_callback",
          OAuth.addParameters(callbackUrl, OAuth.OAUTH_TOKEN, requestToken)));
    }

    if (options.getScope() != null) {
      authParams.add(new OAuth.Parameter("wrap_scope", options.getScope()));
    }

    return OAuth.addParameters(consumer.serviceProvider.userAuthorizationURL,
        authParams);
  }

  private static String getV2AuthorizationUrl(OAuthAccessor accessor,
      LoginOptions options, String callbackUrl) throws IOException {
    OAuthConsumer consumer = accessor.consumer;

    String requestToken = Long.toHexString(new Random().nextLong());
    accessor.requestToken = requestToken;

    List<Parameter> authParams = new ArrayList<Parameter>();
    authParams.add(new OAuth.Parameter("client_id", consumer.consumerKey));
    authParams.add(new OAuth.Parameter("state", requestToken));

    if (callbackUrl == null) {
      callbackUrl = "urn:ietf:wg:oauth:2.0:oob";
    }
    
    authParams.add(new OAuth.Parameter("redirect_uri", callbackUrl));
    authParams.add(new OAuth.Parameter("response_type", "code"));

    if (options.getScope() != null) {
      authParams.add(new OAuth.Parameter("scope", options.getScope()));
    }

    return OAuth.addParameters(consumer.serviceProvider.userAuthorizationURL,
        authParams);
  }

  private static void launchBrowser(LoginOptions options,
      String authorizationUrl) {
    logger.log(Level.INFO, "Redirecting to URL: " + authorizationUrl);

    boolean browsed = false;
    if (options.getBrowser() == null) {
      if (Desktop.isDesktopSupported()) {
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Action.BROWSE)) {
          try {
            desktop.browse(URI.create(authorizationUrl));
            browsed = true;
          } catch (IOException e) {
            // In some situations "BROWSE" appears supported but throws an
            // exception.
            logger.log(Level.WARNING, "Error opening browser for Desktop#browse(String)",
                options.isVerbose() ? e : null);
          }
        } else {
          logger.log(Level.WARNING, "java.awt.Desktop BROWSE action not supported.");
        }
      } else {
        logger.log(Level.WARNING, "java.awt.Desktop not supported. You should use Java 1.6.");
      }
    }

    if (!browsed) {
      String browser = options.getBrowser();
      if (browser == null) {
        browser = "google-chrome";
      }

      try {
        Runtime.getRuntime().exec(new String[] { browser, authorizationUrl });
      } catch (IOException e) {
        logger.log(Level.SEVERE, "Error running browser: " + browser + ". " +
            "Specify a browser with --browser or use --nobrowser to print URL.",
            options.isVerbose() ? e : null);
        System.exit(-1);
      }
    }
  }
}
