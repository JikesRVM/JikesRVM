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
package test.org.jikesrvm.opttests.bugs;

/**
 * Testcase for elision of constructors of subclasses of Throwable
 * in stack traces. This test case makes sure that UncreatableException
 * isn't incorrectly elided from the stack trace.
 */
public class RVM_1129_C {

  static class UncreatableException extends RuntimeException {

    private static final long serialVersionUID = 8658807651859688373L;

    UncreatableException() {
      throw new RuntimeException();
    }
  }

  public static void main(String[] args) {
    RuntimeException e = new UncreatableException();
    throw e;
  }

}
