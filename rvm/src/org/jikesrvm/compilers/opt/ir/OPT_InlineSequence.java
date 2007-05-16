/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.opt.OPT_Stack;

/**
 * Represents an inlining sequence.
 * Used to uniquely identify program locations.
 */
public final class OPT_InlineSequence {
  static final boolean DEBUG = false;

  /**
   * Current method.
   */
  public final VM_NormalMethod method;

  /**
   * Caller info.  null if none.
   */
  public final OPT_InlineSequence caller;

  /**
   * bytecode index (in caller) of call site
   */
  public int bcIndex;

  /**
   * We need more detailed information of call site than bcIndex.
   */
  final OPT_Instruction callSite;

  /**
   * @return contents of {@link #method}
   */
  public VM_NormalMethod getMethod() {
    return method;
  }

  /**
   * @return contents of {@link #caller}
   */
  public OPT_InlineSequence getCaller() {
    return caller;
  }

  public boolean equals(Object o) {
    if (!(o instanceof OPT_InlineSequence)) return false;
    OPT_InlineSequence is = (OPT_InlineSequence) o;
    if (method == null) return (is.method == null);
    if (!method.equals(is.method)) return false;
    if (bcIndex != is.bcIndex) return false;
    if (caller == null) return (is.caller == null);
    return (caller.equals(is.caller));
  }

  /**
   * Constructs a new top-level inline sequence operand.
   *
   * @param method current method
   */
  OPT_InlineSequence(VM_NormalMethod method) {
    this(method, null, -1);
  }

  /**
   * Constructs a new inline sequence operand.
   *
   * @param method current method
   * @param caller caller info
   * @param bcIndex bytecode index of call site
   */
  OPT_InlineSequence(VM_NormalMethod method, OPT_InlineSequence caller, int bcIndex) {
    this.method = method;
    this.caller = caller;
    this.callSite = null;
    this.bcIndex = bcIndex;
  }

  /**
   * Constructs a new inline sequence operand.
   *
   * @param method current method
   * @param caller caller info
   * @param callsite the call site instruction of this callee
   */
  OPT_InlineSequence(VM_NormalMethod method, OPT_InlineSequence caller, OPT_Instruction callsite) {
    this.method = method;
    this.caller = caller;
    this.callSite = callsite;
    this.bcIndex = callsite.bcIndex;
  }

  public OPT_Instruction getCallSite() {
    return this.callSite;
  }

  /**
   * Returns the string representation of this inline sequence.
   */
  public String toString() {
    StringBuilder sb = new StringBuilder(" ");
    for (OPT_InlineSequence is = this; is != null; is = is.caller) {
      sb.append(is.method.getDeclaringClass().getDescriptor()).append(" ").
          append(is.method.getName()).append(" ").
          append(is.method.getDescriptor()).append(" ").
          append(is.bcIndex).append(" ");
    }
    return sb.toString();
  }

  /**
   * return the depth of inlining: (0 corresponds to no inlining)
   */
  public int getInlineDepth() {
    int depth = 0;
    OPT_InlineSequence parent = this.caller;
    while (parent != null) {
      depth++;
      parent = parent.caller;
    }
    return depth;
  }

  /**
   * Return the root method of this inline sequence
   */
  public VM_NormalMethod getRootMethod() {
    OPT_InlineSequence parent = this;
    while (parent.caller != null) {
      parent = parent.caller;
    }
    return parent.method;
  }

  /**
   * Does this inline sequence contain a given method?
   */
  public boolean containsMethod(VM_Method m) {
    if (method == m) return true;
    if (caller == null) return false;
    return (caller.containsMethod(m));
  }

  /**
   * Return a hashcode for this object.
   *
   * TODO: Figure out a better hashcode.  Efficiency doesn't matter
   * for now.
   *
   * @return the hashcode for this object.
   */
  public int hashCode() {
    return bcIndex;
  }

  public java.util.Enumeration<OPT_InlineSequence> enumerateFromRoot() {
    return new java.util.Enumeration<OPT_InlineSequence>() {
      OPT_Stack<OPT_InlineSequence> stack;

      {
        stack = new OPT_Stack<OPT_InlineSequence>();
        OPT_InlineSequence parent = OPT_InlineSequence.this;
        while (parent.caller != null) {
          stack.push(parent);
          parent = parent.caller;
        }
      }

      public boolean hasMoreElements() {
        return !stack.isEmpty();
      }

      public OPT_InlineSequence nextElement() {
        return stack.pop();
      }
    };
  }
}
