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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.oauth.OAuth;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.ParseException;


public class FetchOptions extends CommonOptions {
  public enum Method {
    GET,
    POST,
    PUT,
    DELETE,
  }

  private static final Map<String, String> CONTENT_TYPE_MAP = new HashMap<String, String>();
  static {
    CONTENT_TYPE_MAP.put("ATOM", "application/atom+xml");
    CONTENT_TYPE_MAP.put("XML", "application/xml");
    CONTENT_TYPE_MAP.put("JSON", "application/json");
    CONTENT_TYPE_MAP.put("CSV", "text/csv");
    CONTENT_TYPE_MAP.put("TEXT", "text/plain");
  }

  private Method method = Method.GET;
  private String contentType;
  private List<OAuth.Parameter> headers;
  private boolean include;

  @SuppressWarnings("static-access")
  public FetchOptions() {
    options.addOption("X", "request", true, "HTTP method: GET, POST, PUT, or DELETE");
    options.addOption(OptionBuilder.withArgName("method")
        .withLongOpt("header")
        .hasArgs()
        .withDescription("Custom header to pass to server").create("H"));
    options.addOption("t", "content-type", true,
        "Content-Type header (or ATOM, XML, JSON, CSV, TEXT)");
    options.addOption("i", "include", false, "Include protocol headers in the output");
  }

  @Override
  public CommandLine parse(String[] args) throws ParseException {
    CommandLine line = super.parse(args);
    if (line.hasOption("request")) {
      method = Method.valueOf(line.getOptionValue("request"));
    }

    contentType = line.getOptionValue("content-type", "application/atom+xml");
    if (CONTENT_TYPE_MAP.containsKey(contentType)) {
      contentType = CONTENT_TYPE_MAP.get(contentType);
    }

    headers = new ArrayList<OAuth.Parameter>();
    String[] headerArray = line.getOptionValues("header");
    if (headerArray != null) {
      for (String header : headerArray) {
        String[] headerBits = header.split(":", 1);
        headers.add(new OAuth.Parameter(headerBits[0].trim(), headerBits[1].trim()));
      }
    }

    include = line.hasOption("include");

    return line;
  }

  public Method getMethod() {
    return method;
  }

  public String getContentType() {
    return contentType;
  }

  public List<OAuth.Parameter> getHeaders() {
    return headers;
  }

  public boolean isInclude() {
    return include;
  }
}
