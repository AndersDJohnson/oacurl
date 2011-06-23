package com.google.oacurl.testutil;

import junit.framework.Assert;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;


/**
 * @author mimming@google.com (Jennifer Murphy)
 */
public class StandardOutputCapture {
  private final ByteArrayOutputStream outputStream;

  public StandardOutputCapture(ByteArrayOutputStream outputStream) {
    this.outputStream = outputStream;
  }

  public void assertContains(String string) {
    if (string != null) {
      String outputStreamString = outputStream.toString();
      Assert.assertTrue(outputStreamString.contains(string));
    }
  }

  public void assertDoesNotContain(String string) {
    if (string != null) {
      String outputStreamString = outputStream.toString();
      Assert.assertFalse(outputStreamString.contains(string));
    }
  }

  public void assertNoDuplicateLines() {
    String outputStreamString = outputStream.toString();

    String[] lines = outputStreamString.split("\n");
    Map<String, Object> linesMap = new HashMap<String, Object>();
    for (String line : lines) {
      linesMap.put(line, null);
    }

    Assert.assertEquals(lines.length, linesMap.keySet().size());
  }

  public void assertLineCount(int expected) {
    String outputStreamString = outputStream.toString();

    String[] lines = outputStreamString.split("\n");

    Assert.assertEquals(expected, lines.length);
  }

  public void assertLineCountAtLeast(int expected) {
    String outputStreamString = outputStream.toString();

    String[] lines = outputStreamString.split("\n");

    Assert.assertTrue(expected >= lines.length);
  }
}
