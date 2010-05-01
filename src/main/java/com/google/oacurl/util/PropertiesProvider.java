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

package com.google.oacurl.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Guice-ish wrapper around properties. Mostly convenience for property file
 * I/O and getting empty {@link Property} objects from null file names.
 *
 * @author phopkins@google.com
 */
public class PropertiesProvider {
  private final File file;
  private Properties properties;

  /**
   * @param fileName file name to load, may be null for empty
   *     {@link Properties}.
   */
  public PropertiesProvider(String fileName) {
    this((fileName == null) ? null : new File(fileName));
  }

  public PropertiesProvider(File file) {
    this.file = file;
  }

  public Properties get() throws IOException {
    if (properties == null) {
      properties = new Properties();

      if (file != null) {
        FileInputStream in = new FileInputStream(file);
        properties.load(in);
        in.close();
      }
    }

    return properties;
  }

  public void overwrite(Properties properties) throws IOException {
    if (file == null) {
      throw new IllegalStateException("Trying to save properties to null file");
    }

    file.setWritable(true, true);
    FileOutputStream out = new FileOutputStream(file);
    properties.store(out, null);
    out.close();

    this.properties = properties;
  }
}
