/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

/**
 * Any method that is declared capable of throwing this exception
 * is treated specially by the machine code compiler:
 * (1) the normal thread switch test that would be
 *     emitted in the method prologue is omitted.
 * (2) the stack overflow test that would be emitted
 *     in the method prologue is omitted.
 * 
 * @author Chapman Flack
 */
public class VM_PragmaUninterruptible extends VM_PragmaException {
  private static final VM_TypeReference me = getTypeRef("Lcom/ibm/JikesRVM/VM_PragmaUninterruptible;");
  public static boolean declaredBy(VM_Method method) {
    return declaredBy(me, method);
  }
}
