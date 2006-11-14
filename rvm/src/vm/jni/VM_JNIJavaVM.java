/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.jni;

import org.vmmagic.unboxed.Address;

/**
 * Holder class for the global JavaVM instance
 * used by JNI_OnLoad and JNIEnv.GetJavaVM.
 *
 * @author Elias Naur
 */ 
public final class VM_JNIJavaVM {
  // this is the address of the malloc'ed JavaVM struct (one per VM)
  private static Address JavaVM; 

  private static native Address createJavaVM();

  public static Address getJavaVM() {
	  if (JavaVM == null)
		  JavaVM = createJavaVM();
	  return JavaVM;
  }
}
