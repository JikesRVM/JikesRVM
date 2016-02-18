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
package test.org.jikesrvm.basic.bugs;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/**
 * A test case for registration of custom protocol handlers.
 * <p>
 * The registration is implicit, i.e. we rely on the JVM to find the handlers using the
 * usual naming scheme. The JVM is supposed to find the classes Handler and Connection
 * in a package for the protocol.
 * <p>
 * For example, if the protocol was customprotocol, the JVM would search for the classes
 * prefix.customprotocol.Handler and prefix.customprotocol.Connection. The prefix part
 * is determined by the Java system property "java.protocol.handler.pkgs".
 */
public class RVM_853 {

  private static final String JAVA_PROTOCOL_HANDLERS = "java.protocol.handler.pkgs";

  public static void main(String[] args) throws IOException {
    registerCustomProtocolHandler();
    URL u = new URL("jikesrvmtest://printme");
    URLConnection openConnect = u.openConnection();
    openConnect.connect();
  }

  private static void registerCustomProtocolHandler() {
    String oldHandlerPackages = System.getProperty(JAVA_PROTOCOL_HANDLERS);
    String myHandlerPackage = "test.org.jikesrvm.basic.util";
    StringBuilder newHandlers = new StringBuilder();
    if (oldHandlerPackages != null) {
      newHandlers.append(oldHandlerPackages);
      newHandlers.append("|");
    }
    newHandlers.append(myHandlerPackage);
    System.setProperty(JAVA_PROTOCOL_HANDLERS, newHandlers.toString());
  }

}
