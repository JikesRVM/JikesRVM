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
   * Stash the JTOC somewhere we can find it later
   * when we are making a C => Java transition.
   * We mainly need this for OSX/Linux but it is also nice to have on AIX.
   */
  private final VM_Address savedJTOC = VM_Magic.getTocPointer();
   
  /**
   * This is the pointer to the shared JNIFunction table.
   * When we invoke a native method, we adjust the pointer we
   * pass to the native code such that this field is at offset 0.
   * In other words, we turn a VM_JNIEnvironment into a JNIEnv*
   * by handing the native code an interior pointer to 
   * this object that points directly to this field.
   * On AIX this field points to the AIXLinkageTriplets. 
   * On Linux and OSX it points to JNIFunctions.
   */ 
  //-#if RVM_FOR_AIX
  private final VM_Address externalJNIFunctions = VM_Magic.objectAsAddress(AIXLinkageTriplets);
  //-#elif RVM_FOR_LINUX || RVM_FOR_OSX
  private final VM_Address externalJNIFunctions = VM_Magic.objectAsAddress(JNIFunctions);
  //-#endif
  
  /**
   * Initialize the array of JNI functions.
   * This function is called during bootimage writing.
   */
  public static void initFunctionTable(VM_CodeArray[] functions) {
    JNIFunctions = functions;

    //-#if RVM_FOR_AIX
    // Allocate the linkage triplets in the bootimage (so they won't move)
    AIXLinkageTriplets = new VM_AddressArray[functions.length];
    for (int i=0; i<functions.length; i++) {
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
  }
}
