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
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.oauth.OAuth;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.OAuthServiceProvider;
import net.oauth.client.OAuthClient;
import net.oauth.client.httpclient4.HttpClient4;
import net.oauth.http.HttpMessage;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;

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
	    new HelpFormatter().printHelp(null, options.getOptions());
	    System.exit(0);
	  }

    LoggingConfig.init(options.isVerbose());

	  ServiceProviderDao serviceProviderDao = new ServiceProviderDao();
	  ConsumerDao consumerDao = new ConsumerDao(options);
	  AccessorDao accessorDao = new AccessorDao();

    String serviceProviderFileName = options.getServiceProviderFileName();
    if (serviceProviderFileName == null) {
      serviceProviderFileName = options.isBuzz() ? "BUZZ" : "GOOGLE";
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

    try {
      String callbackUrl;
      if (options.isNoServer()) {
        callbackUrl = null;
      } else {
        callbackServer = new LoginCallbackServer();
        callbackServer.start();

        callbackUrl = callbackServer.getCallbackUrl();
      }

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
      }

       authorizationUrl = OAuth.addParameters(
          authorizationUrl,
          OAuth.OAUTH_TOKEN, accessor.requestToken);

      if (options.isNoBrowser()) {
        System.out.println(authorizationUrl);
        System.out.flush();
      } else {
        launchBrowser(options, authorizationUrl);        
      }

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

      List<OAuth.Parameter> accessTokenParams = OAuth.newList(
          OAuth.OAUTH_TOKEN, accessor.requestToken,
          OAuth.OAUTH_VERIFIER, verifier);
      logger.log(Level.INFO, "Fetching access token with parameters: " + accessTokenParams);
      OAuthMessage accessTokenResponse = client.getAccessToken(accessor, null, accessTokenParams);
      logger.log(Level.INFO, "Access token received: " + accessTokenResponse.getParameters());
      logger.log(Level.FINE, accessTokenResponse.getDump().get(HttpMessage.RESPONSE).toString());

      Properties loginProperties = new Properties();
      accessorDao.saveAccessor(accessor, loginProperties);
      consumerDao.saveConsumer(consumer, loginProperties);
      new PropertiesProvider(options.getLoginFileName()).overwrite(loginProperties);
    } catch (OAuthProblemException e) {
      OAuthUtil.printOAuthProblemException(e);
	  } finally {
	    if (callbackServer != null) {
	      callbackServer.stop();
	    }
	  }
	}

  private static void launchBrowser(LoginOptions options,
      String authorizationUrl) {
    Desktop desktop = null;
    if (Desktop.isDesktopSupported()) {
      desktop = Desktop.getDesktop();
    }

    logger.log(Level.INFO, "Redirecting to URL: " + authorizationUrl);

    boolean browsed = false;
    if (desktop != null && desktop.isSupported(Action.BROWSE) && options.getBrowser() == null) {
      try {
        desktop.browse(URI.create(authorizationUrl));
        browsed = true;
      } catch (IOException e) {
        // In some situations "BROWSE" appears supported but throws an
        // exception.
        logger.log(Level.WARNING, "Exception thrown when using Desktop#browse(String)", e);
      }
    }

    if (!browsed) {
      try {
        String browser = options.getBrowser();
        if (browser == null) {
          browser = "google-chrome";
        }
        Runtime.getRuntime().exec(new String[] { browser, authorizationUrl });
      } catch (IOException e) {
        logger.log(Level.SEVERE, "Error running browser: " + options.getBrowser(), e);
        System.err.flush();
        // Just print the URL with no messaging so that this can be piped
        // somewhere useful (or used with ``s).
        System.out.println(authorizationUrl);
      }
    }
  }
}
