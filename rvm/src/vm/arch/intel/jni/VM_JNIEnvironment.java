/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.jni;

import com.ibm.JikesRVM.*;

/**
 * Platform dependent aspects of the JNIEnvironment.
 *
 * @author Dave Grove
 * @author Ton Ngo
 * @author Steve Smith 
 */
public final class VM_JNIEnvironment extends VM_JNIGenericEnvironment {
  
  /**
   * This is the shared JNI function table used by native code
   * to invoke methods in @link{VM_JNIFunctions}.
   */
  private static VM_CodeArray[] JNIFunctions;

  /**
   * This is the pointer to the shared JNIFunction table.
   * When we invoke a native method, we adjust the pointer we
   * pass to the native code such that this field is at offset 0.
   * In other words, we turn a VM_JNIEnvironment into a JNIEnv*
   * by handing the native code an interior pointer to 
   * this object that points directly to this field.
   */ 
  private final VM_Address externalJNIFunctions = VM_Magic.objectAsAddress(JNIFunctions);

  /**
   * Initialize the array of JNI functions.
   * This function is called during bootimage writing.
   */
  public static void initFunctionTable(VM_CodeArray[] functions) {
    JNIFunctions = functions;
  }

  public static void boot() {
  }
}
