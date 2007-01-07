/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm;

/**
 * This error is thrown when the VM encounters an operation
 * that is not yet implemented.
 *
 * @author Igor Pechtchanski
 */
public class VM_UnimplementedError extends VirtualMachineError {
  /**
   * Constructs a new instance of this class with its
   * walkback filled in.
   */
  public VM_UnimplementedError() {
    super();
  }

  /**
   * Constructs a new instance of this class with its
   * walkback and message filled in.
   * @param detailMessage message to fill in
   */
  public VM_UnimplementedError(java.lang.String detailMessage) {
    super(detailMessage+": not implemented");
  }
  
  private static final long serialVersionUID = 1L;
}

