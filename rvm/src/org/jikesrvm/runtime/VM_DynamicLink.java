/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.runtime;

import org.jikesrvm.classloader.VM_BytecodeConstants;
import org.jikesrvm.classloader.VM_MethodReference;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Place for VM_CompiledMethod.getDynamicLink() to deposit return
 * information.  NB this method is called from within VM_GCMapIterator
 * and has to be uninterruptible (ie contain no new bytecodes),
 * therefore the fields of this class are non-final).
 */
@Uninterruptible public final class VM_DynamicLink implements VM_BytecodeConstants {
  /** method referenced at a call site */
  private VM_MethodReference methodRef;
  /** how method was called at that site */
  private int       bytecode;

  /** set the dynamic link information. */
  public void set(VM_MethodReference methodRef, int bytecode) {
    this.methodRef = methodRef;
    this.bytecode  = bytecode;
  }

  public VM_MethodReference methodRef() {
    return methodRef;
  }

  public boolean isInvokedWithImplicitThisParameter() {
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
