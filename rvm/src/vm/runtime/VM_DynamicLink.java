/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Place for VM_CompiledMethod.getDynamicLink() to deposit return information.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public final class VM_DynamicLink implements VM_BytecodeConstants, 
				      VM_Uninterruptible {
  private VM_Method methodRef; // method referenced at a call site
  private int       bytecode;  // how method was called at that site

  public void set(VM_Method methodRef, int bytecode) {
    this.methodRef = methodRef;
    this.bytecode  = bytecode;
  }

  VM_Method methodRef() {
    return methodRef;
  }

  boolean isInvokedWithImplicitThisParameter() {
    return bytecode != JBC_invokestatic;
  }

  boolean isInvokeVirtual() {
    return bytecode == JBC_invokevirtual;
  }

  boolean isInvokeSpecial() {
    return bytecode == JBC_invokespecial;
  }

  boolean isInvokeStatic() {
    return bytecode == JBC_invokestatic;
  }

  boolean isInvokeInterface() {
    return bytecode == JBC_invokeinterface;
  }
}
