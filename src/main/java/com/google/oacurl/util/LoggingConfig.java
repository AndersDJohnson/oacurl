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

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * @author phopkins
 *
 */
public class LoggingConfig {
  public static void init(boolean verbose) throws SecurityException, IOException {
    System.setProperty("org.apache.commons.logging.Log",
        "org.apache.commons.logging.impl.Jdk14Logger");

    Logger defaultLogger = Logger.getLogger("");
    if (verbose) {
      defaultLogger.setLevel(Level.INFO);
    } else {
      defaultLogger.setLevel(Level.SEVERE);
    }
  }

  public static void enableWireLog() {
    // For clarity, override the formatter so that it doesn't print the
    // date and method name for each line.
    Formatter wireFormatter = new Formatter() {
      @Override
      public String format(LogRecord record) {
        return record.getMessage() + System.getProperty("line.separator");
      }
    };

    ConsoleHandler wireHandler = new ConsoleHandler();
    wireHandler.setLevel(Level.FINE);
    wireHandler.setFormatter(wireFormatter);

    Logger wireLogger = Logger.getLogger("org.apache.http.wire");
    wireLogger.setLevel(Level.FINE);
    wireLogger.setUseParentHandlers(false);
    wireLogger.addHandler(wireHandler);
  }
}
