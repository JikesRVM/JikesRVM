/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package test.org.jikesrvm.basic.util.jikesrvmtest;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class Handler extends URLStreamHandler {

  public static final boolean DEBUG_IMPL = false;

  public static final String PROTOCOL_NAME = "jikesrvmtest";
  public static final int PORT = -1;
  public static final String HOST = "";
  public static final int SLASH_LENGTH = 2;

  @Override
  protected void parseURL(URL url, String arg1, int arg2, int arg3) {
    String toPrint = arg1.substring(arg2 + SLASH_LENGTH, arg3);
    setURL(url, PROTOCOL_NAME, "", PORT, "", "", toPrint, null, null);
    if (DEBUG_IMPL) {
      System.out.println("setURL for JikesRVMTestURLStreamHandler");
    }
  }

  @Override
  protected URLConnection openConnection(URL url) throws IOException {
    if (DEBUG_IMPL) {
      System.out.println("return new JikesRVMTestURLConnection");
    }
    return new Connection(url);
  }

}
