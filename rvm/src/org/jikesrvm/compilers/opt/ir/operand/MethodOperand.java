/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ir.operand;

import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_MethodReference;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.compilers.opt.SpecializedMethod;
import org.vmmagic.unboxed.Offset;

/**
 * Refers to a method. Used for method call instructions.
 * Contains a VM_Method (which may or may not have been resolved yet.)
 *
 * TODO: Create subclasses of MethodOperand for internal & specialized
 * targets.
 *
 * @see Operand
 * @see VM_Method
 */
public final class MethodOperand extends Operand {

  /* Enumeration of types of invokes */
  private static final byte STATIC = 0;
  private static final byte SPECIAL = 1;
  private static final byte VIRTUAL = 2;
  private static final byte INTERFACE = 3;

  /**
   * Member reference for target.
   * Usually a VM_MethodReference, but may be a VM_FieldReference for
   * internal methods that don't have 'real' Java method but come from
   * VM_OutOfLineMachineCode.
   */
  final VM_MemberReference memRef;

  /**
   * Target VM_Method of invocation.
   */
  VM_Method target;

  /**
   * Is target exactly the method being invoked by this call, or is it
   * a representative for a family of virtual/interface methods?
   */
  boolean isPreciseTarget;

  /**
   * Is this the operand of a call that never returns?
   */
  boolean isNonReturningCall;

  /**
   * Is this the operand of a call that is the off-branch of a guarded inline?
   */
  boolean isGuardedInlineOffBranch;

  /**
   * The type of the invoke (STATIC, SPECIAL, VIRTUAL, INTERFACE)
   */
  byte type = -1;

  private boolean designatedOffset = false;
  public Offset jtocOffset;

  /**
   * @param ref VM_MemberReference of method to call
   * @param tar the VM_Method to call (may be null)
   * @param t the type of invoke used to call it (STATIC, SPECIAL, VIRTUAL, INTERFACE)
   */
  private MethodOperand(VM_MemberReference ref, VM_Method tar, byte t) {
    memRef = ref;
    target = tar;
    type = t;
    setPreciseTarget();
  }

  private void setPreciseTarget() {
    if (isVirtual()) {
      isPreciseTarget = target != null && (target.isFinal() || target.getDeclaringClass().isFinal());
    } else {
      isPreciseTarget = !isInterface();
    }
  }

  /**
   * Returns a method operand representing a compiled method with designated
   * JTOC offset. (used by ConvertToLowLevelIR)
   * @param callee the callee method
   * @param offset designated jtop offset of compiled method of callee
   * @return the method operand
   */
  public static MethodOperand COMPILED(VM_Method callee, Offset offset) {
    byte type = callee.isStatic() ? STATIC : VIRTUAL;
    MethodOperand op = new MethodOperand(callee.getMemberRef(), callee, type);
    op.jtocOffset = offset;
    op.designatedOffset = true;
    op.isPreciseTarget = true;
    return op;
  }

  public boolean hasDesignatedTarget() {
    return this.designatedOffset;
  }

  /**
   * create a method operand for an INVOKE_SPECIAL bytecode
   *
   * @param ref VM_MemberReference of method to call
   * @param target the VM_Method to call (may be null)
   * @return the newly created method operand
   */
  public static MethodOperand SPECIAL(VM_MethodReference ref, VM_Method target) {
    return new MethodOperand(ref, target, SPECIAL);
  }

  /**
   * create a method operand for an INVOKE_STATIC bytecode
   *
   * @param ref VM_MemberReference of method to call
   * @param target the VM_Method to call (may be null)
   * @return the newly created method operand
   */
  public static MethodOperand STATIC(VM_MethodReference ref, VM_Method target) {
    return new MethodOperand(ref, target, STATIC);
  }

  /**
   * create a method operand for an INVOKE_STATIC bytecode
   * where the target method is known at compile time.
   *
   * @param target the VM_Method to call
   * @return the newly created method operand
   */
  public static MethodOperand STATIC(VM_Method target) {
    MethodOperand ans = new MethodOperand(target.getMemberRef(), target, STATIC);
    return ans;
  }

  /**
   * create a method operand for an INVOKE_STATIC bytecode
   * where the target method is known at compile time.
   *
   * @param target the VM_Method to call
   * @return the newly created method operand
   */
  public static MethodOperand STATIC(VM_Field target) {
    return new MethodOperand(target.getMemberRef(), null, STATIC);
  }

  /**
   * create a method operand for an INVOKE_VIRTUAL bytecode
   *
   * @param ref VM_MemberReference of method to call
   * @param target the VM_Method to call (may be null)
   * @return the newly created method operand
   */
  public static MethodOperand VIRTUAL(VM_MethodReference ref, VM_Method target) {
    return new MethodOperand(ref, target, VIRTUAL);
  }

  /**
   * create a method operand for an INVOKE_INTERFACE bytecode
   *
   * @param ref VM_MemberReference of method to call
   * @param target the VM_Method to call (may be null)
   * @return the newly created method operand
   */
  public static MethodOperand INTERFACE(VM_MethodReference ref, VM_Method target) {
    return new MethodOperand(ref, target, INTERFACE);
  }

  public boolean isStatic() {
    return type == STATIC;
  }

  public boolean isVirtual() {
    return type == VIRTUAL;
  }

  public boolean isSpecial() {
    return type == SPECIAL;
  }

  public boolean isInterface() {
    return type == INTERFACE;
  }

  public boolean hasTarget() {
    return target != null;
  }

  public boolean hasPreciseTarget() {
    return target != null && isPreciseTarget;
  }

  public VM_Method getTarget() {
    return target;
  }

  public VM_MemberReference getMemberRef() {
    return memRef;
  }

  /**
   * Get whether this operand represents a method call that never
   * returns (such as a call to athrow());
   *
   * @return Does this op represent a call that never returns?
   */
  public boolean isNonReturningCall() {
    return isNonReturningCall;
  }

  /**
   * Record whether this operand represents a method call that never
   * returns (such as a call to athrow());
   */
  public void setIsNonReturningCall(boolean neverReturns) {
    isNonReturningCall = neverReturns;
  }

  /**
   * Return whether this operand is the off branch of a guarded inline
   */
  public boolean isGuardedInlineOffBranch() {
    return isGuardedInlineOffBranch;
  }

  /**
   * Record that this operand is the off branch of a guarded inline
   */
  public void setIsGuardedInlineOffBranch(boolean f) {
    isGuardedInlineOffBranch = f;
  }

  /**
   * Refine the target information. Used to reduce the set of
   * targets for an invokevirtual.
   */
  public void refine(VM_Method target) {
    this.target = target;
    setPreciseTarget();
  }

  /**
   * Refine the target information. Used to reduce the set of
   * targets for an invokevirtual.
   */
  public void refine(VM_Type targetClass) {
    this.target = targetClass.findVirtualMethod(memRef.getName(), memRef.getDescriptor());
    setPreciseTarget();
  }

  /**
   * Refine the target information. Used to reduce the set of
   * targets for an invokevirtual.
   */
  public void refine(VM_Method target, boolean isPreciseTarget) {
    this.target = target;
    if (isPreciseTarget) {
      this.isPreciseTarget = isPreciseTarget;
    } else {
      setPreciseTarget();
    }
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   *
   * @return a copy of <code>this</code>
   */
  public Operand copy() {
    MethodOperand mo = new MethodOperand(memRef, target, type);
    mo.isPreciseTarget = isPreciseTarget;
    mo.isNonReturningCall = isNonReturningCall;
    mo.isGuardedInlineOffBranch = isGuardedInlineOffBranch;
    return mo;
  }

  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code>
   *           if they are not.
   */
  public boolean similar(Operand op) {
    if (op instanceof MethodOperand) {
      MethodOperand mop = (MethodOperand) op;
      return memRef == mop.memRef && target == mop.target && isPreciseTarget == mop.isPreciseTarget;
    } else {
      return false;
    }
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    String s = "";
    switch (type) {
      case STATIC:
        s += "static";
        break;
      case SPECIAL:
        s += "special";
        break;
      case VIRTUAL:
        s += "virtual";
        break;
      case INTERFACE:
        s += "interface";
        break;
    }
    if (isPreciseTarget && (type != STATIC)) {
      s += "_exact";
    }
    if (hasSpecialVersion()) {
      return s + "\"" + spMethod + "\"";
    }
    if (target != null) {
      return s + "\"" + target + "\"";
    } else {
      return s + "<" + memRef + ">";
    }
  }

  /*
   * SPECIALIZATION SUPPORT
   */
  public SpecializedMethod spMethod;

  public boolean hasSpecialVersion() { return spMethod != null; }
}
