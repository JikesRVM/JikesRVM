/*
 * (C) Copyright IBM Corp 2001,2002
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

  /**
   * Get the classloader that couldn't load the class.
   */
  public final ClassLoader getClassLoader() {
    return classloader;
  }
   
  //----------------//
  // implementation //
  //----------------//
   
  /**
   * descriptor for type whose resolution caused the problem
   */
  final VM_Atom   targetTypeDescriptor; 

  /**
   * exception that prevented resolution
   */
  final Throwable exception;   

  /**
   * class loader we tried to use
   */
  final ClassLoader classloader;
   
  VM_ResolutionException(VM_Atom targetTypeDescriptor, Throwable exception) {
    this(targetTypeDescriptor, exception, null);
  }

  VM_ResolutionException(VM_Atom targetTypeDescriptor, Throwable exception, ClassLoader classloader) {
    this.targetTypeDescriptor = targetTypeDescriptor;
    this.exception            = exception;
    this.classloader          = classloader;
  }

  public final String toString() {
    final StringBuffer msg = new StringBuffer("VM_ResolutionException: ");
    msg.append(exception);
    msg.append(", using classloader ");
    msg.append(classloader);
    return msg.toString();
  }
}
