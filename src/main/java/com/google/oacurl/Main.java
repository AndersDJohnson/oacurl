package com.google.oacurl;

//TODO: Refactor entry into oacurl in to one main class (instead of 3)

/**
 * A main class which serves no purpose other than to make oacurl work as an
 * executable jar.
 *
 * @author mimming@google.com (Jennifer Murphy)
 */
public class Main {
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      // Displaying the help is actually pretty complex, so for now let fetch do that
      Fetch.main(new String[0]);
      return;
    }

    // Pop off the first argument since it merely tells us which other main method
    // to invoke
    String[] newArgs = new String[args.length - 1];

    // Copy all but the first argument into a new argument array
    System.arraycopy(args, 1, newArgs, 0, newArgs.length);

    if (args[0].equals("login")) {
      Login.main(newArgs);
    } else if (args[0].equals("fetch")) {
      Fetch.main(newArgs);
    } else {
      // If no known action is specified, hand off the whole mess to fetch
      Fetch.main(args);
    }
  }
}
