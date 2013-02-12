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
package org.jikesrvm.compilers.opt.inlining;

import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.util.Stack;

/**
 * Represents an inlining sequence.
 * Used to uniquely identify program locations.
 */
public final class InlineSequence {
  /**
   * Current method.
   */
  public final NormalMethod method;

  /**
   * Caller info. {@code null} if none.
   */
  public final InlineSequence caller;

  /**
   * bytecode index (in caller) of call site
   */
  public int bcIndex;

  /**
   * We need more detailed information of call site than bcIndex.
   */
  final Instruction callSite;

  /**
   * @return contents of {@link #method}
   */
  public NormalMethod getMethod() {
    return method;
  }

  /**
   * @return contents of {@link #caller}
   */
  public InlineSequence getCaller() {
    return caller;
  }

  /**
   * Constructs a new top-level inline sequence operand.
   *
   * @param method current method
   */
  public InlineSequence(NormalMethod method) {
    this(method, null, -1);
  }

  /**
   * Constructs a new inline sequence operand.
   *
   * @param method current method
   * @param caller caller info
   * @param bcIndex bytecode index of call site
   */
  InlineSequence(NormalMethod method, InlineSequence caller, int bcIndex) {
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
  public InlineSequence(NormalMethod method, InlineSequence caller, Instruction callsite) {
    this.method = method;
    this.caller = caller;
    this.callSite = callsite;
    this.bcIndex = callsite.bcIndex;
  }

  public Instruction getCallSite() {
    return this.callSite;
  }

  /**
   * Returns the string representation of this inline sequence.
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(" ");
    for (InlineSequence is = this; is != null; is = is.caller) {
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
    InlineSequence parent = this.caller;
    while (parent != null) {
      depth++;
      parent = parent.caller;
    }
    return depth;
  }

  /**
   * Return the root method of this inline sequence
   */
  public NormalMethod getRootMethod() {
    InlineSequence parent = this;
    while (parent.caller != null) {
      parent = parent.caller;
    }
    return parent.method;
  }

  /**
   * Does this inline sequence contain a given method?
   */
  public boolean containsMethod(RVMMethod m) {
    if (method == m) return true;
    if (caller == null) return false;
    return (caller.containsMethod(m));
  }

  public java.util.Enumeration<InlineSequence> enumerateFromRoot() {
    return new java.util.Enumeration<InlineSequence>() {
      Stack<InlineSequence> stack;

      {
        stack = new Stack<InlineSequence>();
        InlineSequence parent = InlineSequence.this;
        while (parent.caller != null) {
          stack.push(parent);
          parent = parent.caller;
        }
      }

      @Override
      public boolean hasMoreElements() {
        return !stack.isEmpty();
      }

      @Override
      public InlineSequence nextElement() {
        return stack.pop();
      }
    };
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + bcIndex;
    result = prime * result + ((caller == null) ? 0 : caller.hashCode());
    result = prime * result + ((method == null) ? 0 : method.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    InlineSequence other = (InlineSequence) obj;
    if (bcIndex != other.bcIndex)
      return false;
    if (caller == null) {
      if (other.caller != null)
        return false;
    } else if (!caller.equals(other.caller))
      return false;
    if (method == null) {
      if (other.method != null)
        return false;
    } else if (!method.equals(other.method))
      return false;
    return true;
  }

}
