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

package com.google.oacurl.dao;

import java.io.IOException;
import java.util.Properties;

import net.oauth.OAuthServiceProvider;

/**
 * Properties wrapper for the provider data.
 *
 * @author phopkins@google.com
 */
public class ServiceProviderDao {
  private static final String REQUEST_TOKEN_URL_PROPERTY = "requestTokenUrl";
  private static final String USER_AUTHORIZATION_URL_PROPERTY = "userAuthorizationUrl";
  private static final String ACCESS_TOKEN_URL_PROPERTY = "accessTokenUrl";

  private static final String DEFAULT_REQUEST_TOKEN_URL =
      "https://www.google.com/accounts/OAuthGetRequestToken";
  private static final String DEFAULT_USER_AUTHORIZATION_URL =
      "https://www.google.com/accounts/OAuthAuthorizeToken";
  private static final String DEFAULT_ACCESS_TOKEN_URL =
      "https://www.google.com/accounts/OAuthGetAccessToken";

  public OAuthServiceProvider nullServiceProvider() {
    return new OAuthServiceProvider(null, null, null);
  }

  public OAuthServiceProvider loadServiceProvider(Properties properties) throws IOException {
    String requestTokenUrl = properties.getProperty(REQUEST_TOKEN_URL_PROPERTY,
        DEFAULT_REQUEST_TOKEN_URL);
    String userAuthorizationUrl = properties.getProperty(USER_AUTHORIZATION_URL_PROPERTY,
        DEFAULT_USER_AUTHORIZATION_URL);
    String accessTokenUrl = properties.getProperty(ACCESS_TOKEN_URL_PROPERTY,
        DEFAULT_ACCESS_TOKEN_URL);

    return new OAuthServiceProvider(requestTokenUrl, userAuthorizationUrl, accessTokenUrl);
  }
}
