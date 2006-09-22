/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
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
  //-#if RVM_WITH_POWEROPEN_ABI
  // index of IP in the linkage triplet
  static final int IP = 0;                    

  // index of TOC in the linkage triplet
  static final int TOC = 1;
  //-#endif
}

