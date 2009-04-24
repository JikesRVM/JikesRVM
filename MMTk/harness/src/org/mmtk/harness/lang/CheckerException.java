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
package org.mmtk.harness.lang;

public class CheckerException extends RuntimeException {

  public CheckerException() {
    super();
  }

  public CheckerException(String message, Throwable cause) {
    super(message, cause);
  }

  public CheckerException(String message) {
    super(message);
  }

  public CheckerException(Throwable cause) {
    super(cause);
  }


  /**
   *
   */
  private static final long serialVersionUID = 1L;

}
