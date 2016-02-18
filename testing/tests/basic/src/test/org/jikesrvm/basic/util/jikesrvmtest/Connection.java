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

public class Connection extends URLConnection {

  protected Connection(URL url) {
    super(url);
  }

  @Override
  public void connect() throws IOException {
    System.out.println("Connecting via JikesRVMTestURLConnection (i.e. printing to standard out):");
    System.out.println(this.url.getPath());
  }

}
