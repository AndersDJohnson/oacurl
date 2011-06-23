package com.google.oacurl;

import com.google.oacurl.testutil.StandardOutputCapture;
import com.google.oacurl.testutil.StandardOutputTestUtil;
import junit.framework.Assert;
import org.junit.*;

import java.io.File;

/**
 * @author mimming@google.com (Jennifer Murphy)
 */
public class Oauth2IntegrationTest {
  private StandardOutputCapture standardOutputCapture;
  private static File oauthTokensFile = new File("/tmp/oacurlLoginStuff" + System.currentTimeMillis());

  @BeforeClass
  public static void before() throws Exception {
    // It cannot exist already
    Assert.assertFalse(oauthTokensFile.exists());

    // Do the login
    Login.main(new String[]{
            "-2",
            "--scope", "https://www.googleapis.com/auth/buzz",
            "--access-file", oauthTokensFile.getAbsolutePath()});

    // Verify that file was created
    Assert.assertTrue(oauthTokensFile.exists());
  }

  @AfterClass
  public static void after() {
    // Delete files created
    oauthTokensFile.delete();
  }

  @Before
  public void setUpStreams() throws Exception {
    // Set up standard output capturing
    standardOutputCapture = StandardOutputTestUtil.startCapture();
  }

  @After
  public void cleanUpStreams() {
    // Reset standard output capturing
    StandardOutputTestUtil.resetSystemOutput();
  }


  @Test
  public void testHelp() throws Exception {
    Login.main(new String[]{"--help"});

    standardOutputCapture.assertDoesNotContain("error");
    // Make sure it contains a few random keywords
    standardOutputCapture.assertContains("usage");
    standardOutputCapture.assertContains("insecure");
    standardOutputCapture.assertContains("verbose");

    standardOutputCapture.assertLineCountAtLeast(100);
  }

  @Test
  public void testLoginV2ExplicitScopeFetchStuff() throws Exception {

    // Now fetch a feed
    Fetch.main(new String[]{
            "-i",
            "--access-file", oauthTokensFile.getAbsolutePath(),
            "https://www.googleapis.com/buzz/v1/activities/@me/@self?prettyprint=true&alt=json"
    });

    standardOutputCapture.assertDoesNotContain("error");

    // Make sure it contains a few random keywords
    standardOutputCapture.assertContains("HTTP/1.1 200 OK");
    standardOutputCapture.assertContains("activityFeed");

    standardOutputCapture.assertLineCountAtLeast(100);
  }
}
