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

package com.google.oacurl.options;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.ParseException;


/**
 * Subclass for {@link Options}s that are used for the login step.
 *
 * @author phopkins@google.com
 */
public class LoginOptions extends CommonOptions {
  private static final Map<String, String> SCOPE_MAP = new HashMap<String, String>();
  static {
    SCOPE_MAP.put("BUZZ", "https://www.googleapis.com/auth/buzz");
    SCOPE_MAP.put("BUZZ_READONLY", "https://www.googleapis.com/auth/buzz.readonly");
  }

  private String consumerFileName;
  private String consumerKey;
  private String consumerSecret;
  private String serviceProviderFileName;
  private String scope;
  private String browser;
  private boolean nobrowser;
  private boolean noserver;

  @SuppressWarnings("static-access")
  public LoginOptions() {
    options.addOption("p", "service-provider", true,
        "properties file with service provider URLs (or GOOGLE, YAHOO, TWITTER, etc.)");
    options.addOption("c", "consumer", true, "properties file with consumer key and secret");
    options.addOption(OptionBuilder.withArgName("scope list")
        .withLongOpt("scope")
        .hasArgs()
        .withDescription("Scopes (or BUZZ, BUZZ_READONLY, etc.)")
        .withValueSeparator(',').create("s"));
    options.addOption("b", "browser", true, "Path to a browser to exec");
    options.addOption(null, "nobrowser", false, "Don't use a browser at all, write URL to stdout");
    options.addOption(null, "noserver", false, "Don't start the server, get token from stdin");
    options.addOption(null, "consumer-key", true, "Consumer key (if file is not specified)");
    options.addOption(null, "consumer-secret", true, "Consumer key (if file is not specified)");
  }

  @Override
  public CommandLine parse(String[] args) throws ParseException {
    CommandLine line = super.parse(args);

    serviceProviderFileName = line.getOptionValue("service-provider");
    consumerFileName = line.getOptionValue("consumer");
    consumerKey = line.getOptionValue("consumer-key");
    consumerSecret = line.getOptionValue("consumer-secret");
    browser = line.getOptionValue("browser");

    noserver = line.hasOption("noserver");
    nobrowser = line.hasOption("nobrowser");

    if (line.hasOption("scope")) {
      StringBuilder scopeBuilder = new StringBuilder();
      for (String oneScope : line.getOptionValues("scope")) {
        if (SCOPE_MAP.containsKey(oneScope)) {
          oneScope = SCOPE_MAP.get(oneScope);
        }

        if (scopeBuilder.length() > 0) {
          scopeBuilder.append(" ");
        }
        scopeBuilder.append(oneScope);
      }

      scope = scopeBuilder.toString();
    } else if (isBuzz()) {
      scope = SCOPE_MAP.get("BUZZ");
    }

    return line;
  }

  public String getServiceProviderFileName() {
    return serviceProviderFileName;
  }

  public String getConsumerFileName() {
    return consumerFileName;
  }

  public String getConsumerKey() {
    return consumerKey;
  }

  public String getConsumerSecret() {
    return consumerSecret;
  }

  public String getScope() {
    return scope;
  }

  public String getBrowser() {
    return browser;
  }

  public boolean isNoBrowser() {
    return nobrowser;
  }

  public boolean isNoServer() {
    return noserver;
  }
}
