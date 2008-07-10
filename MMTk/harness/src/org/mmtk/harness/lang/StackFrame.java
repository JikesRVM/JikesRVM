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
import java.util.Stack;

import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.plan.TraceLocal;
import org.vmmagic.unboxed.ObjectReference;

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
    values[d.slot] = d.initial.clone();
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
    Trace.trace(Item.ENV, "%s %s = %s",values[slot].type().toString(),names[slot],value.toString());
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
  public int computeRoots(TraceLocal trace) {
    int rootCount = 0;
    for (int i=0; i < values.length; i++) {
      Value value = values[i];
      String name = names[i];
      if (value instanceof ObjectValue) {
        ObjectValue object = (ObjectValue)value;
        Trace.trace(Item.ROOTS, "Tracing root %s=%s", name, object.toString());
        object.traceObject(trace);
        rootCount++;
      }
    }
    return rootCount;
  }

  /**
   * Debug printing support: dump this stack frame and return roots.
   */
  public void dumpRoots(int width, Stack<ObjectReference> roots) {
    for (int i=0; i < values.length; i++) {
      Value value = values[i];
      String name = names[i];
      if (value instanceof ObjectValue) {
        ObjectReference ref = ((ObjectValue)value).getObjectValue();
        System.err.printf(" %s=%s", name, Mutator.formatObject(width, ref));
        if (!ref.isNull()) roots.push(ref);
      }
    }
  }
}
