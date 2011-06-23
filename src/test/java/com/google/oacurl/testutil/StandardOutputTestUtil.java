package com.google.oacurl.testutil;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * @author mimming@google.com (Jennifer Murphy)
 */
public class StandardOutputTestUtil {
  private static final PrintStream DEFAULT_STANDARD_OUTPUT = System.out;

  public static StandardOutputCapture startCapture() {
    ByteArrayOutputStream testOut = new ByteArrayOutputStream();
    System.setOut(new PrintStream(testOut));
    return new StandardOutputCapture(testOut);
  }

  public static void resetSystemOutput() {
    System.setOut(DEFAULT_STANDARD_OUTPUT);
  }
}
