/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.jni;

import java.lang.reflect.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;
import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

/**
 * Platform dependent aspects of the JNIEnvironment.
 *
 * @author Dave Grove
 * @author Ton Ngo 
 * @author Steve Smith
 */
public final class VM_JNIEnvironment extends VM_JNIGenericEnvironment implements VM_JNIAIXConstants {

  /**
   * This is the shared JNI function table used by native code
   * to invoke methods in @link{VM_JNIFunctions}.
   */
  private static VM_CodeArray[] JNIFunctions;

  //-#if RVM_FOR_AIX
  /**
   * On AIX we need a linkage triple instead of just
   * a function pointer.  
   * This is an array of such triples that matches JNIFunctions.
   */
  private static VM_AddressArray[] AIXLinkageTriplets;
  //-#endif
  
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
    //-#if RVM_FOR_AIX
    JNIFunctions = functions;
    AIXLinkageTriplets = new VM_AddressArray[functions.length];
    //-#elif RVM_FOR_LINUX
    // An extra entry is allocated, to hold the RVM JTOC
    JNIFunctions = new VM_CodeArray[functions.length + 1];
    System.arraycopy(functions, 0, JNIFunctions, 0, functions.length);
    //-#endif
	
    // 2 words for each thread
    JNIFunctionPointers = VM_AddressArray.create(VM_Scheduler.MAX_THREADS * 2);

    //-#if RVM_FOR_AIX
    // Allocate the linkage triplets 
    for (int i=0; i<JNIFunctions.length; i++) {
      AIXLinkageTriplets[i] = VM_AddressArray.create(3);
    }
    //-#endif
  }

  public static void boot() {
    //-#if RVM_FOR_AIX
    // fill in the TOC and IP entries for each AIX linkage triplet
    for (int i=0; i<JNIFunctions.length; i++) {
      VM_AddressArray triplet = AIXLinkageTriplets[i];
      triplet.set(TOC, VM_Magic.getTocPointer());
      triplet.set(IP, VM_Magic.objectAsAddress(JNIFunctions[i]));
    }
    //-#endif

    //-#if RVM_FOR_LINUX
    // set JTOC content, how about GC ? will it move JTOC ?
    int offset = getJNIFunctionsJTOCOffset();
    VM_Magic.setMemoryAddress(VM_Magic.objectAsAddress(JNIFunctions).add(offset),
			      VM_Magic.getTocPointer());
    //-#endif
  }

  // Instance:  create a thread specific JNI environment.  threadSlot = creating threads
  // thread id == index of its entry in Scheduler.threads array
  //
  public VM_JNIEnvironment(int threadSlot) {
    // as of 8/22 SES - let JNIEnvAddress be the address of the JNIFunctionPtr to be
    // used by the creating thread.  Passed as first arg (JNIEnv) to native C functions.

    // uses 2 VM_Addresses for each thread
    // the first is the AIXLinkageTriplets to be used to invoke VM_JNIFunctions
    //-#if RVM_FOR_AIX
    JNIFunctionPointers.set(threadSlot * 2, VM_Magic.objectAsAddress(AIXLinkageTriplets));
    //-#elif RVM_FOR_LINUX
    JNIFunctionPointers.set(threadSlot * 2, VM_Magic.objectAsAddress(JNIFunctions));
    //-#endif

    // the second contains the address of processor's vpStatus word
    JNIFunctionPointers.set((threadSlot * 2)+1, VM_Address.zero());

    JNIEnvAddress = VM_Magic.objectAsAddress(JNIFunctionPointers).add(threadSlot*(2 * BYTES_IN_ADDRESS));
  }

  static int getJNIFunctionsJTOCOffset() {
    return VM_JNIFunctions.FUNCTIONCOUNT << VM_SizeConstants.LOG_BYTES_IN_ADDRESS;
  }
}
