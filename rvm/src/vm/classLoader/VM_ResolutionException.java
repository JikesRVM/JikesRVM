/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * An exception to indicate that a method in the vm failed to return
 * a value due to a class-loading, class-initialization,
 * or name-resolution problem.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_ResolutionException extends Exception {
  //-----------//
  // interface //
  //-----------//
   
  /**
   * Get (descriptor for) type that was trying to be resolved.
   */ 
  public final VM_Atom getTargetTypeDescriptor() {
    return targetTypeDescriptor;
  }
   
  /**
   * Get exception that prevented resolution.
   */ 
  public final Throwable getException() {
    return exception;
  }
   
  //----------------//
  // implementation //
  //----------------//
   
  /**
   * descriptor for type whose resolution caused the problem
   */
  VM_Atom   targetTypeDescriptor; 
  /**
   * exception that prevented resolution
   */
  Throwable exception;            
   
  VM_ResolutionException(VM_Atom targetTypeDescriptor, Throwable exception) {
    this.targetTypeDescriptor = targetTypeDescriptor;
    this.exception = exception;
  }

  public final String toString() {
    return "VM_ResolutionException: " + exception;
  }
}
