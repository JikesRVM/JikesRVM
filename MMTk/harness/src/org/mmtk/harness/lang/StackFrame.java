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
package org.mmtk.harness.lang;

import java.util.List;

import org.mmtk.plan.TraceLocal;

/**
 * A stack frame.  Currently assumes each slot contains exactly one
 * variable, and that all variables are live all the time.
 */
public class StackFrame {
  private final Value[] values;
  private final String[] names;

  public StackFrame(List<Declaration> decls) {
    int size = decls.size();
    this.values = new Value[size];
    this.names = new String[size];
    for (Declaration d : decls) {
      declare(d);
    }
  }

  /**
   * Declare a variable in a given slot
   */
  public void declare(Declaration d) {
    values[d.slot] = d.initial;
    names[d.slot] = d.name;
  }

  /**
   * Return the variable at the given slot in the current stack frame
   */
  public Value get(int slot) {
    return values[slot];
  }

  /**
   * Return the type of the variable at the given slot.
   */
  public Type getType(int slot) {
    return values[slot].type();
  }

  /**
   * Assign a new value to the given slot
   */
  public void set(int slot, Value value) {
    if (Env.TRACE) System.err.println(values[slot].type() + " " + names[slot] + " = " + value);
    values[slot].copyFrom(value);
  }

  /**
   * Print the value held in a given slot
   */
  public String getName(int slot) {
    return names[slot];
  }

  /**
   * GC support: trace this stack frame.
   */
  public void computeRoots(TraceLocal trace) {
    for (Value value : values) {
      if (value instanceof ObjectValue) {
        ((ObjectValue)value).traceObject(trace);
      }
    }
  }
}
