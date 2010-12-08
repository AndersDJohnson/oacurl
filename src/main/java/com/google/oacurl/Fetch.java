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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Logger;

import net.oauth.OAuth;
import net.oauth.OAuth.Parameter;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.OAuthServiceProvider;
import net.oauth.ParameterStyle;
import net.oauth.client.OAuthClient;
import net.oauth.client.OAuthResponseMessage;
import net.oauth.client.httpclient4.HttpClient4;
import net.oauth.client.httpclient4.HttpClientPool;
import net.oauth.http.HttpMessage;
import net.oauth.http.HttpMessageDecoder;
import net.oauth.http.HttpResponseMessage;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;

import com.google.oacurl.dao.AccessorDao;
import com.google.oacurl.dao.ConsumerDao;
import com.google.oacurl.dao.ServiceProviderDao;
import com.google.oacurl.options.FetchOptions;
import com.google.oacurl.options.FetchOptions.Method;
import com.google.oacurl.options.OAuthVersion;
import com.google.oacurl.util.LoggingConfig;
import com.google.oacurl.util.MultipartRelatedInputStream;
import com.google.oacurl.util.OAuthUtil;
import com.google.oacurl.util.PropertiesProvider;

/**
 * Main class for curl-like interactions authenticated by OAuth.
 * <p>
 * Assumes that the user has run {@link Login} to save the OAuth access
 * token to a local properties file.
 * <p>
 * TODO(phopkins): Make some of this a bit more object-oriented.
 *
 * @author phopkins@google.com
 */
public class Fetch {

  @SuppressWarnings("unused")
  private static Logger logger = Logger.getLogger(Login.class.getName());

  public static void main(String[] args) throws Exception {
    FetchOptions options = new FetchOptions();
    CommandLine line = options.parse(args);
    args = line.getArgs();

    if (options.isHelp()) {
      new HelpFormatter().printHelp("url", options.getOptions());
      System.exit(0);
    }

    if (args.length != 1) {
      new HelpFormatter().printHelp("url", options.getOptions());
      System.exit(-1);
    }

    if (options.isInsecure()) {
      SSLSocketFactory.getSocketFactory().setHostnameVerifier(new AllowAllHostnameVerifier());
    }

    LoggingConfig.init(options.isVerbose());
    if (options.isVerbose()) {
      LoggingConfig.enableWireLog();
    }

    String url = args[0];

    ServiceProviderDao serviceProviderDao = new ServiceProviderDao();
    ConsumerDao consumerDao = new ConsumerDao();
    AccessorDao accessorDao = new AccessorDao();

    Properties loginProperties = null;
    try {
      loginProperties = new PropertiesProvider(options.getLoginFileName()).get();
    } catch (FileNotFoundException e) {
      System.err.println(".oacurl.properties file not found in homedir");
      System.err.println("Make sure you've run oacurl-login first!");
      System.exit(-1);
    }

    OAuthServiceProvider serviceProvider = serviceProviderDao.nullServiceProvider();
    OAuthConsumer consumer = consumerDao.loadConsumer(loginProperties, serviceProvider);
    OAuthAccessor accessor = accessorDao.loadAccessor(loginProperties, consumer);

    OAuthVersion version = (loginProperties.containsKey("oauthVersion")) ?
        OAuthVersion.valueOf(loginProperties.getProperty("oauthVersion")) :
          OAuthVersion.V1;

    OAuthClient client = new OAuthClient(new HttpClient4(SingleClient.HTTP_CLIENT_POOL));

    try {
      OAuthMessage request;

      List<Entry<String, String>> related = options.getRelated();

      Method method = options.getMethod();
      if (method == Method.POST || method == Method.PUT) {
        InputStream bodyStream;
        if (related != null) {
          bodyStream = new MultipartRelatedInputStream(related);
        } else if (options.getFile() != null) {
          bodyStream = new FileInputStream(options.getFile());
        } else {
          bodyStream = System.in;
        }
        request = newRequestMessage(accessor, method, url, bodyStream, version);
        request.getHeaders().add(new OAuth.Parameter("Content-Type", options.getContentType()));
      } else {
        request = newRequestMessage(accessor, method, url, null, version);
      }

      List<Parameter> headers = options.getHeaders();
      addHeadersToRequest(request, headers);

      HttpResponseMessage httpResponse;
      if (version == OAuthVersion.V1) {
        OAuthResponseMessage response;
        response = client.access(request, ParameterStyle.AUTHORIZATION_HEADER);
        httpResponse = response.getHttpResponse();
      } else {
        HttpMessage httpRequest = new HttpMessage(
            request.method, new URL(request.URL), request.getBodyAsStream());
        httpRequest.headers.addAll(request.getHeaders());
        httpResponse = client.getHttpClient().execute(httpRequest, client.getHttpParameters());
        httpResponse = HttpMessageDecoder.decode(httpResponse);
      }

      System.err.flush();

      if (options.isInclude()) {
        Map<String, Object> dump = new HashMap<String, Object>();
        httpResponse.dump(dump);
        System.out.print(dump.get(HttpMessage.RESPONSE));
      }

      // Dump the bytes in the response's encoding.
      InputStream bodyStream = httpResponse.getBody();
      byte[] buf = new byte[1024];
      int count;
      while ((count = bodyStream.read(buf)) > -1) {
        System.out.write(buf, 0, count);
      }
    } catch (OAuthProblemException e) {
      OAuthUtil.printOAuthProblemException(e);
    }
  }

  private static OAuthMessage newRequestMessage(OAuthAccessor accessor,
      Method method, String url, InputStream bodyStream, OAuthVersion version)
      throws OAuthException, IOException, URISyntaxException {

    // Inlined in order to put a conditional on #addRequiredParameters
    String methodStr = method.toString();
    if (methodStr == null) {
      methodStr = (String) accessor.getProperty("httpMethod");
      if (methodStr == null) {
        methodStr = (String) accessor.consumer.getProperty("httpMethod");
        if (methodStr == null) {
          methodStr = OAuthMessage.GET;
        }
      }
    }

    OAuthMessage message = new OAuthMessage(methodStr, url, null, bodyStream);

    // By not calling #addRequiredParameters, we prevent all the OAuth 1.0
    // signing bits and adding of headers, while retaining all of our existing
    // code around HttpMessages and such for OAuth-WRAP.
    if (version == OAuthVersion.V1) {
      message.addRequiredParameters(accessor);
    } else {
      message.getHeaders().add(new OAuth.Parameter(
          "Authorization", "WRAP access_token=" + accessor.accessToken));
    }

    return message;
  }

  private static void addHeadersToRequest(OAuthMessage request, List<Parameter> headers) {
    // HACK(phopkins): If someone added their own Expect header, then tell
    // Apache not to add its own. This is a bit hacky, but gets around that
    // the RequestExpectContinue class doesn't check for an existing header
    // before adding its own.
    //
    // Fix for: http://code.google.com/p/oacurl/issues/detail?id=1
    boolean hasExpect = false;
    for (Parameter param : headers) {
      if (param.getKey().equalsIgnoreCase(HTTP.EXPECT_DIRECTIVE)) {
        hasExpect = true;
        break;
      }
    }

    if (hasExpect) {
      HttpProtocolParams.setUseExpectContinue(
          SingleClient.HTTP_CLIENT_POOL.getHttpClient().getParams(), false);
    }

    request.getHeaders().addAll(headers);
  }

  /**
   * Broken out of {@link HttpClient4} so that we can get access to the
   * underlying {@link DefaultHttpClient} object.
   */
  private static class SingleClient implements HttpClientPool {
    public static final SingleClient HTTP_CLIENT_POOL = new SingleClient();

    private SingleClient() {
      HttpClient client = new DefaultHttpClient();
      ClientConnectionManager mgr = client.getConnectionManager();
      if (!(mgr instanceof ThreadSafeClientConnManager)) {
        HttpParams params = client.getParams();
        client = new DefaultHttpClient(new ThreadSafeClientConnManager(
            params, mgr.getSchemeRegistry()), params);
      }

      this.client = client;
    }

    private final HttpClient client;

    public HttpClient getHttpClient() {
      return client;
    }

    public HttpClient getHttpClient(URL server) {
      return client;
    }
  }
}
