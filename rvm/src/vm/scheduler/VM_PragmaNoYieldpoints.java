/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

/**
 * A pragma that can be used to declare that a 
 * particular method should be compiled without
 * compiler-inserted yieldpoints and stackoverflow checks.
 * This does not imply that the method is actually uninterruptible
 * as it still may contain bytecodes that are interruptible or
 * may call interruptible code. It only indicates that any yieldpoint
 * will be the result of some source code action. 
 * 
 * @author Dave Grove
 */
public class VM_PragmaNoYieldpoints extends VM_PragmaException {
  private static final VM_TypeReference me = getTypeRef("Lcom/ibm/JikesRVM/VM_PragmaNoYieldpoints;");
  public static boolean declaredBy(VM_Method method) {
    return declaredBy(me, method);
  }
}
