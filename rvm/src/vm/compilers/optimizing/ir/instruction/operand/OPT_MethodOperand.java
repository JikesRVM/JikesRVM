/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
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

  /** Enumeration of types of invokes */
  public static final byte STATIC    = 0;
  /** Enumeration of types of invokes */
  public static final byte SPECIAL   = 1;
  /** Enumeration of types of invokes */
  public static final byte VIRTUAL   = 2;
  /** Enumeration of types of invokes */
  public static final byte INTERFACE = 3;

  /**
   * The method being invoked
   */
  public VM_Method method;
  
  /**
   * For use when invoking internal methods (defined as INSTRUCTION[]
   * reachable from the JTOC but that don't have VM_Method objecrs/
   */
  public VM_Member internal;
  
  /**
   * offset into jtoc/tib (for internal methods only).
   * (internal != null, method = null).
   */
  public int offset; 

  /**
   * Does the invoke only have a single target?
   */
  private boolean isSingleTarget;

  /**
   * Is the invocation target refined (via static analysis)?
   */
  private boolean isRefined; 

  /**
   * Is this the operand of a call that never returns?
   */
  private boolean isNonReturningCall=false;
  
  /**
   * Is this the operand of a call that is the off-branch of a guarded inline?
   */
  private boolean isGuardedInlineOffBranch = false;

  /**
   * The type of the invoke (STATIC, SPECIAL, VIRTUAL, INTERFACE)
   */
  public byte type = -1;

  /**
   * Is the target currently unresolved?
   */
  public boolean unresolved;

  /**
   * @param callee the method to call
   * @param t the type of invoke used to call it 
   *          (STATIC, SPECIAL, VIRTUAL, INTERFACE)
   * @param r is the target currently unresolved?
   */
  private OPT_MethodOperand(VM_Method callee, byte t, boolean r) {
    method    = callee;
    type      = t;
    unresolved= r;
    if (!unresolved) {
      // put direct information. used for a) inlining 
      // b) devirtualization, and c) IPA
      // TODO: add more rules
      VM_Class klass = callee.getDeclaringClass();
      switch (t) {
      case STATIC: case SPECIAL:
	isSingleTarget = true;
	break;
      case VIRTUAL:
	isSingleTarget = callee.isFinal() || callee.getDeclaringClass().isFinal();
	break;
      }
    }
  }

  /**
   * create a method operand for an INVOKE_SPECIAL bytecode
   * 
   * @param callee the method to call
   * @param r is the target currently unresolved?
   * @return the newly created method operand
   */
  public static OPT_MethodOperand SPECIAL(VM_Method callee, boolean r) {
    return new OPT_MethodOperand(callee,SPECIAL,r);
  }

  /**
   * create a method operand for an INVOKE_STATIC bytecode
   * 
   * @param callee the method to call
   * @param r is the target currently unresolved?
   * @return the newly created method operand
   */
  public static OPT_MethodOperand STATIC(VM_Method callee, boolean r) {
    return new OPT_MethodOperand(callee,STATIC,r);
  }

  /**
   * create a method operand for an INVOKE_STATIC bytecode
   * where the target is known to be resolved.
   * 
   * @param callee the method to call
   * @return the newly created method operand
   */
  public static OPT_MethodOperand STATIC(VM_Method callee) {
    return new OPT_MethodOperand(callee,STATIC,false);
  }

  /**
   * create a method operand for an INVOKE_VIRTUAL bytecode
   * 
   * @param callee the method to call
   * @param r is the target currently unresolved?
   * @return the newly created method operand
   */
  public static OPT_MethodOperand VIRTUAL(VM_Method callee, boolean r) {
    return new OPT_MethodOperand(callee,VIRTUAL,r);
  }

  /**
   * create a method operand for an INVOKE_VIRTUAL bytecode
   * whose target may have been refined
   * 
   * @param callee the method to call
   * @param r is the target currently unresolved?
   * @param refined has the target been refined?
   * @return the newly created method operand
   */
  public static OPT_MethodOperand VIRTUAL(VM_Method callee, 
				   boolean r, 
				   boolean refined) {
    OPT_MethodOperand mo = new OPT_MethodOperand(callee,VIRTUAL,r);
    mo.isRefined = refined;
    return mo;
  }

  /**
   * create a method operand for an INVOKE_INTERFACE bytecode
   * 
   * @param callee the method to call
   * @param r is the target currently unresolved?
   * @return the newly created method operand
   */
  public static OPT_MethodOperand INTERFACE(VM_Method callee, boolean r) {
    return new OPT_MethodOperand(callee,INTERFACE,r);
  }

  /**
   * Create a method operand for an internal method
   */
  OPT_MethodOperand(VM_Member member, byte t, int o) {
    internal = member;
    type     = t;
    offset   = o;
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

  public boolean isSingleTarget() {
    return isSingleTarget;
  }

  public boolean isRefined() {
    return isRefined;
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
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    if (method == null) {
      return new OPT_MethodOperand(internal, type, offset);
    } else {
      OPT_MethodOperand mo = new OPT_MethodOperand(method, type, unresolved);
      mo.setIsGuardedInlineOffBranch(isGuardedInlineOffBranch());
      return mo;
    }
  }

  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code> 
   *           if they are not.
   */
  public boolean similar(OPT_Operand op) {
    return (op instanceof OPT_MethodOperand) && 
      (method == ((OPT_MethodOperand)op).method);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
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
    if (unresolved)
      s += "_unresolved";
    if (isSingleTarget && (type != STATIC)) 
      s += "_single";
    if (hasSpecialVersion()) {
      return s+"\""+spMethod.toString()+"\"";
    }
    if (method != null)
      return s+"\""+method.toString()+"\"";
    else if (internal != null)
      return s+"<"+internal+">";
    else 
      return s+"<unknown>";
  }

  /*
   * SPECIALIZATION SUPPORT
   */

  public OPT_SpecializedMethod spMethod;
  public boolean hasSpecialVersion(){
    if (spMethod != null){ return true;}
    return false;
  }
}
