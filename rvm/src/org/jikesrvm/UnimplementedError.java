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
package org.jikesrvm;

/**
 * This error is thrown when the VM encounters an operation
 * that is not yet implemented.
 */
public class UnimplementedError extends VirtualMachineError {
  /**
   * Constructs a new instance of this class with its
   * walkback filled in.
   */
  public UnimplementedError() {
    super();
  }

  /**
   * Constructs a new instance of this class with its
   * walkback and message filled in.
   * @param detailMessage message to fill in
   */
  public UnimplementedError(java.lang.String detailMessage) {
    super(detailMessage + ": not implemented");
  }

  private static final long serialVersionUID = 1L;
}

