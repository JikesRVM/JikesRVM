/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.jni;

/**
 * Constants for JNI support
 *
 * @author Ton Ngo
 * @author Steve Smith
 */
interface VM_JNIConstants {
  //-#if RVM_WITH_LINKAGE_TRIPLETS
  // index of IP in the linkage triplet
  static final int IP = 0;                    

  // index of TOC in the linkage triplet
  static final int TOC = 1;
  //-#endif
}

