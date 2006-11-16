/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package com.ibm.jikesrvm;

/**
 * Exception to indicate that a blocking call has timed out.
 * This is purposely not a subtype of any library exception class,
 * because in the context of the VM it is sometimes not possible
 * to know what the calling method wants.  For example, InterruptedException,
 * SocketTimeoutException, etc.
 *
 * @author David Hovemeyer
 */
public class VM_TimeoutException extends Exception {
  public VM_TimeoutException(String msg) {
    super(msg);
  }
}
