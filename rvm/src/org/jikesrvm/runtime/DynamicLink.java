/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.runtime;

import org.jikesrvm.classloader.BytecodeConstants;
import org.jikesrvm.classloader.MethodReference;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Place for CompiledMethod.getDynamicLink() to deposit return
 * information.
 * <p>
 * NB this method is called from within GCMapIterator
 * and has to be uninterruptible (i.e. contain no new bytecodes),
 * therefore the fields of this class are non-final).
 */
@Uninterruptible
public final class DynamicLink implements BytecodeConstants {
  /** method referenced at a call site */
  private MethodReference methodRef;
  /** how method was called at that site */
  private int bytecode;

  /** set the dynamic link information. */
  public void set(MethodReference methodRef, int bytecode) {
    this.methodRef = methodRef;
    this.bytecode = bytecode;
  }

  public MethodReference methodRef() {
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
