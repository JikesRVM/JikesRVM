/*
 * (C) Copyright IBM Corp. 2002, 2003, 2004
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

/**
 * A pragma that can be used to declare that a 
 * particular method is interruptible.  
 * Used to override the class-wide pragma
 * implied by implementing {@link VM_Uninterruptible}.
 * 
 * @author Dave Grove
 */
public class VM_PragmaInterruptible extends VM_PragmaException {
  private static final VM_TypeReference me = getTypeRef("Lcom/ibm/JikesRVM/VM_PragmaInterruptible;");
  public static boolean declaredBy(VM_Method method) {
    return declaredBy(me, method);
  }
}
