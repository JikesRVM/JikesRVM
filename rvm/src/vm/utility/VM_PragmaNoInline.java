/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
/**
 * This pragma indicates that a particular method should never be inlined
 * by the optimizing compiler.
 * 
 * @author Stephen Fink
 */
public class VM_PragmaNoInline extends VM_PragmaException {
  private static final VM_TypeReference me = getTypeRef("Lcom/ibm/JikesRVM/VM_PragmaNoInline;");
  public static boolean declaredBy(VM_Method method) {
    return declaredBy(me, method);
  }
}
