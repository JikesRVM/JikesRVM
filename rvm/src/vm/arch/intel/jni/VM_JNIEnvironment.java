/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.jni;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;

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
   * This is a table of pointers to the shared JNI function table.  All entries 
   * point to the same function table.  Each thread uses the pointer at its thread id
   * offset to allow us to determine a threads id from the pointer it is using.
   * Needed when native calls Java (JNIFunctions) and passes its JNIEnv pointer.
   * Its offset into the JNIFunctionPts array is the same as the threads offset
   * in the Scheduler.threads array.
   */
  private static VM_AddressArray JNIFunctionPointers;

  /**
   * Initialize the array of JNI functions.
   * This function is called during bootimage writing.
   */
  public static void initFunctionTable(VM_CodeArray[] functions) {
    // An extra entry is allocated, to hold the RVM JTOC 07/01 SES
    JNIFunctions = new VM_CodeArray[functions.length + 1];
    System.arraycopy(functions, 0, JNIFunctions, 0, functions.length);

    // First word is a pointer to the JNIFunction table
    // Second word is address of current processors vpStatus word
    // (JTOC is now stored at end of shared JNIFunctions array)
    JNIFunctionPointers = VM_AddressArray.create(VM_Scheduler.MAX_THREADS * 2);
  }

  public static void boot() {
    // store RVM JTOC address in last (extra) entry in JNIFunctions array
    // to be restored when native C invokes JNI functions implemented in java
    //
    // the array is not well typed, so forced to setMemoryAddress instead
    // TODO: this is higly bogus and needs to be removed. --dave
    int offset = getJNIFunctionsJTOCOffset();
    VM_Magic.setMemoryAddress(VM_Magic.objectAsAddress(JNIFunctions).add(offset),
			      VM_Magic.getTocPointer());
  }

  /**
   * Create a thread specific JNI environment.
   * @param threadSlot index of creating thread in Schedule.threads array (thread id)
   */
  void initializeState(int threadSlot) {
    initializeState();
    JNIFunctionPointers.set(threadSlot * 2, VM_Magic.objectAsAddress(JNIFunctions));
    JNIFunctionPointers.set((threadSlot * 2)+1, VM_Address.zero());
    JNIEnvAddress = VM_Magic.objectAsAddress(JNIFunctionPointers).add(threadSlot*(2*BYTES_IN_ADDRESS));
  }

  static int getJNIFunctionsJTOCOffset() {
    return VM_JNIFunctions.FUNCTIONCOUNT << VM_SizeConstants.LOG_BYTES_IN_ADDRESS;
  }
}
