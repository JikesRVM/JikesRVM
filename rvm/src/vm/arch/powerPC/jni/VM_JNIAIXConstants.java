/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Constants for JNI support
 *
 * @author Ton Ngo
 * @author Steve Smith
 */
interface VM_JNIAIXConstants extends VM_JNIConstants {
  // index of IP in the AIX linkage triplet
  static final int IP = 0;                    

  // index of TOC in the AIX linage triplet
  static final int TOC = 1;                   
}

