/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
/**
 * This pragma indicates that a particular method should always be inlined
 * by the optimizing compiler.
 * 
 * @author Stephen Fink
 */
public class VM_PragmaInline extends VM_PragmaException {
  private static final VM_Class vmClass = getVMClass(VM_PragmaInline.class);
  public static boolean declaredBy(VM_Method method) {
    return declaredBy(vmClass, method);
  }
}
