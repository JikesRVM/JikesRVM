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
package org.jikesrvm.runtime;

/**
 * Exception to indicate that a blocking call has timed out.
 * This is purposely not a subtype of any library exception class,
 * because in the context of the VM it is sometimes not possible
 * to know what the calling method wants.  For example, InterruptedException,
 * SocketTimeoutException, etc.
 */
public class TimeoutException extends Exception {
  public TimeoutException(String msg) {
    super(msg);
  }

  private static final long serialVersionUID = 1L;
}
