/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * This pragma indicates that a particular method should never be 
 * compiled by the optimizing compiler. It also implies that the
 * method will never be inlined by the optimizing compiler.
 * 
 * @author Dave Grove
 */
public class VM_PragmaNoOptCompile extends VM_PragmaException {
  private static final VM_Class vmClass = getVMClass(VM_PragmaNoOptCompile.class);
  public static boolean declaredBy(VM_Method method) {
    return declaredBy(vmClass, method);
  }
}
