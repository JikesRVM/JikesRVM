/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

/**
 * A pragma that can be used to declare that although a
 * particular method is interruptible, 
 * uninterruptible code is allowed to call the method
 * without generating a warning. The intended usage of this
 * pragma is to allow uninterruptible code to raise exceptions
 * during error conditions.  For example, VM_Runtime.raiseClassCastException.
 * <p>
 * Extreme care must be exercised when using this pragma since it supresses 
 * the checking of uninterruptibility.
 * 
 * @author Dave Grove
 */
public class VM_PragmaInterruptibleNoWarn extends VM_PragmaException {
  private static final VM_TypeReference me = getTypeRef("Lcom/ibm/JikesRVM/VM_PragmaInterruptibleNoWarn;");
  public static boolean declaredBy(VM_Method method) {
    return declaredBy(me, method);
  }
}
