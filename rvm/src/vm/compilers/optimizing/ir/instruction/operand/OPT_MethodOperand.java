/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.OPT_SpecializedMethod;

/**
 * Refers to a method. Used for method call instructions.
 * Contains a VM_Method (which may or may not have been resolved yet.)
 * 
 * TODO: Create subclasses of OPT_MethodOperand for internal & specialized
 * targets.
 * 
 * @see OPT_Operand
 * @see VM_Method
 * 
 * @author Dave Grove
 * @author Mauricio Serrano
 * @author John Whaley
 */
public final class OPT_MethodOperand extends OPT_Operand {

  /* Enumeration of types of invokes */
  private static final byte STATIC    = 0;
  private static final byte SPECIAL   = 1;
  private static final byte VIRTUAL   = 2;
  private static final byte INTERFACE = 3;

  /**
   * Member reference for target.
   * Usually a VM_MethodReference, but may be a VM_FieldReference for
   * internal methods that don't have 'real' Java method but come from
   * VM_OutOfLineMachineCode.
   */
  protected VM_MemberReference memRef;
  
  /**
   * Target VM_Method of invocation.
   */
  protected VM_Method target;

  /**
   * Is target exactly the method being invoked by this call, or is it
   * a representative for a family of virtual/interface methods?
   */
  protected boolean isPreciseTarget;

  /**
   * Is this the operand of a call that never returns?
   */
  protected boolean isNonReturningCall;
  
  /**
   * Is this the operand of a call that is the off-branch of a guarded inline?
   */
  protected boolean isGuardedInlineOffBranch;

  /**
   * The type of the invoke (STATIC, SPECIAL, VIRTUAL, INTERFACE)
   */
  protected byte type = -1;

  //-#if RVM_WITH_OSR
  private boolean designatedOffset = false;
  public int jtocOffset;
  //-#endif

  /**
   * @param ref VM_MemberReference of method to call
   * @param tar the VM_Method to call (may be null)
   * @param t the type of invoke used to call it (STATIC, SPECIAL, VIRTUAL, INTERFACE)
   */
  private OPT_MethodOperand(VM_MemberReference ref, VM_Method tar, byte t) {
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

  //-#if RVM_WITH_OSR
  /**
   * Returns a method operand representing a compiled method with designated
   * JTOC offset. (used by OPT_ConvertToLowLevelIR)
   * @param callee the callee method
   * @param offset designated jtop offset of compiled method of callee
   * @return the method operand
   */
  public static OPT_MethodOperand COMPILED(VM_Method callee, int offset) {
    byte type = callee.isStatic()?STATIC:VIRTUAL;
    OPT_MethodOperand op = new OPT_MethodOperand(callee.getMemberRef(), callee, type);
    op.jtocOffset = offset;
    op.designatedOffset = true;
    op.isPreciseTarget = true;
    return op;
  }

  public boolean hasDesignatedTarget() {
    return this.designatedOffset;
  }
  //-#endif

  /**
   * create a method operand for an INVOKE_SPECIAL bytecode
   * 
   * @param ref VM_MemberReference of method to call
   * @param target the VM_Method to call (may be null)
   * @return the newly created method operand
   */
  public static OPT_MethodOperand SPECIAL(VM_MethodReference ref, VM_Method target) {
    return new OPT_MethodOperand(ref, target, SPECIAL);
  }

   /**
    * create a method operand for an INVOKE_STATIC bytecode
    * 
    * @param ref VM_MemberReference of method to call
    * @param target the VM_Method to call (may be null)
    * @return the newly created method operand
    */
   public static OPT_MethodOperand STATIC(VM_MethodReference ref, VM_Method target) {
     return new OPT_MethodOperand(ref, target, STATIC);
   }

   /**
    * create a method operand for an INVOKE_STATIC bytecode
    * where the target method is known at compile time.
    * 
    * @param target the VM_Method to call
    * @return the newly created method operand
    */
   public static OPT_MethodOperand STATIC(VM_Method target) {
     OPT_MethodOperand ans = new OPT_MethodOperand(target.getMemberRef(), target, STATIC);
     return ans;
   }

   /**
    * create a method operand for an INVOKE_STATIC bytecode
    * where the target method is known at compile time.
    * 
    * @param target the VM_Method to call
    * @return the newly created method operand
    */
   public static OPT_MethodOperand STATIC(VM_Field target) {
     return new OPT_MethodOperand(target.getMemberRef(), null, STATIC);
   }

   /**
    * create a method operand for an INVOKE_VIRTUAL bytecode
    * 
    * @param ref VM_MemberReference of method to call
    * @param target the VM_Method to call (may be null)
    * @return the newly created method operand
    */
   public static OPT_MethodOperand VIRTUAL(VM_MethodReference ref, VM_Method target) {
     return new OPT_MethodOperand(ref, target, VIRTUAL);
   }

   /**
    * create a method operand for an INVOKE_INTERFACE bytecode
    * 
    * @param ref VM_MemberReference of method to call
    * @param target the VM_Method to call (may be null)
    * @return the newly created method operand
    */
   public static OPT_MethodOperand INTERFACE(VM_MethodReference ref, VM_Method target) {
     return new OPT_MethodOperand(ref, target, INTERFACE);
   }

   public final boolean isStatic() {
     return type == STATIC;
   }

   public final boolean isVirtual() {
     return type == VIRTUAL;
   }

   public final boolean isSpecial() {
     return type == SPECIAL;
   }

   public final boolean isInterface() {
     return type == INTERFACE;
   }

   public final boolean hasTarget() {
     return target != null;
   }

   public final boolean hasPreciseTarget() {
     return target != null && isPreciseTarget;
   }

   public final VM_Method getTarget() {
     return target;
   }

   public final VM_MemberReference getMemberRef() {
     return memRef;
   }

   /**
    * Get whether this operand represents a method call that never 
    * returns (such as a call to athrow());
    *
    * @return Does this op represent a call that never returns?
    */
   public final boolean isNonReturningCall() {
     return isNonReturningCall;
   }

   /**
    * Record whether this operand represents a method call that never 
    * returns (such as a call to athrow());
    */
   public final void setIsNonReturningCall(boolean neverReturns) {
     isNonReturningCall = neverReturns;
   }

   /**
    * Return whether this operand is the off branch of a guarded inline
    */
   public final boolean isGuardedInlineOffBranch() {
     return isGuardedInlineOffBranch;
   }

   /**
    * Record that this operand is the off branch of a guarded inline
    */
   public final void setIsGuardedInlineOffBranch(boolean f) {
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
   public final OPT_Operand copy() {
     OPT_MethodOperand mo = new OPT_MethodOperand(memRef, target, type);
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
   public final boolean similar(OPT_Operand op) {
     if (op instanceof OPT_MethodOperand) {
       OPT_MethodOperand mop = (OPT_MethodOperand)op;
       return memRef == mop.memRef &&
         target == mop.target &&
         isPreciseTarget == mop.isPreciseTarget;
     } else {
       return false;
     }
   }

   /**
    * Returns the string representation of this operand.
    *
    * @return a string representation of this operand.
    */
   public final String toString() {
     String s = "";
     switch(type) {
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
       return s+"\""+spMethod+"\"";
     }
     if (target != null) {
       return s+"\""+target+"\"";
     } else {
       return s+"<"+memRef+">";
     }
   }

  /*
   * SPECIALIZATION SUPPORT
   */
  public OPT_SpecializedMethod spMethod;
  public final boolean hasSpecialVersion(){ return spMethod != null; }
}
