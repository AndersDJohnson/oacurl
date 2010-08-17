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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Properties;
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

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;

import com.google.oacurl.LoginCallbackServer.TokenStatus;
import com.google.oacurl.dao.AccessorDao;
import com.google.oacurl.dao.ConsumerDao;
import com.google.oacurl.dao.ServiceProviderDao;
import com.google.oacurl.options.LoginOptions;
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

    ServiceProviderDao serviceProviderDao = new ServiceProviderDao();
    ConsumerDao consumerDao = new ConsumerDao(options);
    AccessorDao accessorDao = new AccessorDao();

    String serviceProviderFileName = options.getServiceProviderFileName();
    if (serviceProviderFileName == null) {
      if (options.isBuzz()) {
        serviceProviderFileName = "BUZZ";
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
      String callbackUrl;
      if (options.isNoServer()) {
        callbackUrl = null;
      } else {
        callbackServer = new LoginCallbackServer(options);
        callbackServer.start();

        callbackUrl = callbackServer.getCallbackUrl();
      }

      do {
        String authorizationUrl;
        switch (options.getVersion()) {
        case V1:
          authorizationUrl = getV1AuthorizationUrl(client, accessor, options, callbackUrl);
          break;
        case WRAP:
          authorizationUrl = getWrapAuthorizationUrl(accessor, callbackUrl);
          break;
        default:
          throw new AssertionError("Unknown version: " + options.getVersion());
        }
  
        callbackServer.setAuthorizationUrl(authorizationUrl);
  
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
        case WRAP:
          success = fetchWrapAccessToken(accessor, callbackUrl, verifier);
          break;
        default:
          throw new AssertionError("Unknown version: " + options.getVersion());
        }
        
        if (success) {
          callbackServer.setTokenStatus(TokenStatus.VALID);

          Properties loginProperties = new Properties();
          accessorDao.saveAccessor(accessor, loginProperties);
          consumerDao.saveConsumer(consumer, loginProperties);
          loginProperties.put("oauthVersion", options.getVersion().toString());
          new PropertiesProvider(options.getLoginFileName()).overwrite(loginProperties);
        } else {
          callbackServer.setTokenStatus(TokenStatus.INVALID);
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

  private static boolean fetchWrapAccessToken(OAuthAccessor accessor, String callbackUrl,
      String verifier) throws IOException {
    OAuthConsumer consumer = accessor.consumer;

    List<OAuth.Parameter> accessTokenParams = OAuth.newList(
        "wrap_client_id", consumer.consumerKey,
        "wrap_client_secret", consumer.consumerSecret,
        // HACK(phopkins): The callback needs to be exactly the same, so put in
        // the version if it that was made in #getWrapAuthorizationUrl
        "wrap_callback", OAuth.addParameters(callbackUrl, OAuth.OAUTH_TOKEN, accessor.requestToken),
        "wrap_verification_code", verifier);

    logger.log(Level.INFO, "Fetching access token with parameters: " + accessTokenParams);

    String url = OAuth.addParameters(consumer.serviceProvider.accessTokenURL, accessTokenParams);
    BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(url).openStream()));

    StringBuilder resp = new StringBuilder();

    String line;
    while ((line = reader.readLine()) != null) {
      resp.append(line);
    }

    List<Parameter> params = OAuth.decodeForm(resp.toString());
    for (Parameter param : params) {
      if (param.getKey().equals("wrap_access_token")) {
        accessor.accessToken = param.getValue();
        accessor.tokenSecret = "";
      }
    }
    
    return accessor.accessToken != null;
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

  private static String getWrapAuthorizationUrl(OAuthAccessor accessor, String callbackUrl)
      throws IOException {
    OAuthConsumer consumer = accessor.consumer;

    String requestToken = Double.toHexString(Math.random());

    accessor.requestToken = requestToken;

    return OAuth.addParameters(consumer.serviceProvider.userAuthorizationURL,
        "wrap_client_id", consumer.consumerKey,
        "wrap_callback", OAuth.addParameters(callbackUrl, OAuth.OAUTH_TOKEN, requestToken));
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
