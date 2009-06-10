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

package org.jikesrvm.tools.bootImageWriter;

public class BootImageWriterMessages {

  protected static void say(String...messages) {
    System.out.print("BootImageWriter: ");
    for (String message : messages)
      System.out.print(message);
    System.out.println();
}

  protected static void fail(String message) throws Error {
    throw new Error("\nBootImageWriter: " + message);
  }
}

