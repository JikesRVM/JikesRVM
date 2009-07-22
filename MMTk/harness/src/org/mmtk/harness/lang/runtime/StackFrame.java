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
package org.mmtk.harness.lang.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.mmtk.harness.lang.Declaration;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.lang.pcode.PseudoOp;
import org.mmtk.harness.lang.type.Type;
import org.mmtk.harness.vm.ObjectModel;
import org.mmtk.harness.vm.ReferenceProcessor;
import org.mmtk.plan.TraceLocal;
import org.vmmagic.unboxed.ObjectReference;

/**
 * A stack frame.  Currently assumes each slot contains exactly one
 * variable, and that all variables are live all the time.
 */
public class StackFrame {

  /**
   * Enable the assertion that objects won't move after being traced.
   * This is notably not true for MC, but can be useful for debugging other
   * collectors.
   */
  private static final boolean ASSERT_WILL_NOT_MOVE = false;

  /** A sentinel for slots that have no value */
  public static final int NO_SUCH_SLOT = Integer.MAX_VALUE;

  /** The values of variables and temporaries */
  private final Value[] values;
  /** (for debugging) the names of the value slots */
  private String[] names = null;
  /** The saved program counter during a method call */
  private int savedPc;
  /** The saved instruction array for a method call */
  private PseudoOp[] savedCode;
  /** The slot for the return value of a method call */
  private int resultSlot = NO_SUCH_SLOT;

  /**
   * Create a stack frame, given a list of declarations and a quantity of temporaries
   * @param decls Variables declared in this stack frame
   * @param nTemp Number of temporaries
   */
  public StackFrame(List<Declaration> decls, int nTemp) {
    int size = decls.size()+nTemp;
    this.values = new Value[size];
    if (Trace.isEnabled(Item.ENV) || Trace.isEnabled(Item.ROOTS)) {
      this.names = new String[size];
      for (Declaration d : decls) {
        declare(d);
      }
      for (int i=decls.size(); i < size; i++) {
        names[i] = "t"+i;
      }
    }
  }

  /**
   * Declare a variable in a given slot.  Only used when tracing.
   * @param d The variable declaration
   */
  public void declare(Declaration d) {
    values[d.getSlot()] = d.getInitial();
    names[d.getSlot()] = d.getName();
  }

  /**
   * Return the variable at the given slot in the current stack frame
   * @param slot The stack frame slot
   * @return The value in the slot
   */
  public Value get(int slot) {
    if (slot >= 0) {
      return values[slot];
    }
    return ConstantPool.get(slot);
  }

  /**
   * Return the type of the variable at the given slot.
   * @param slot The stack frame slot
   * @return The type of the value in the slot
   */
  public Type getType(int slot) {
    return values[slot].type();
  }

  /**
   * Assign a new value to the given slot
   * @param slot Stack-frame slot to modify
   * @param value New value
   */
  public void set(int slot, Value value) {
    assert value != null : "Unexpected null value";
    if (Trace.isEnabled(Item.EVAL)) {
      Trace.printf(Item.EVAL, "%s %s = %s",value.type().toString(),getSlotName(slot),value.toString());
    }
    values[slot] = value;
  }

  private String getSlotName(int slot) {
    if (names != null && names[slot] != null) {
      return names[slot];
    }
    return "t" + slot;
  }

  /**
   * GC support: trace this stack frame.
   * @param trace The MMTk trace object to receive the roots
   * @return The number of roots found
   */
  public int computeRoots(TraceLocal trace) {
    int rootCount = 0;
    for (ObjectValue object : getRoots()) {
      if (!object.getObjectValue().isNull()) {
        if (Trace.isEnabled(Item.ROOTS)) {
          Trace.trace(Item.ROOTS, "Tracing root %s", object.toString());
        }
        object.traceObject(trace);
        if (ASSERT_WILL_NOT_MOVE) {
          assert trace.willNotMoveInCurrentCollection(object.getObjectValue()) :
            object.getObjectValue()+" has been traced but willNotMoveInCurrentCollection is still false";
        }
        if (Trace.isEnabled(Item.ROOTS)) {
          Trace.trace(Item.ROOTS, "new value of %s", object.toString());
        }
        rootCount++;
      }
    }
    Trace.trace(Item.REFERENCES, "Discovering references");
    for (ReferenceValue reference : getReferences()) {
      ReferenceProcessor.discover(reference);
    }
    return rootCount;
  }

  /**
   *
   * @return The root ObjectValues for this stack frame
   */
  public Collection<ObjectValue> getRoots() {
    List<ObjectValue> roots = new ArrayList<ObjectValue>();
    for (Value value : values) {
      if (value != null && value instanceof ObjectValue) {
        roots.add((ObjectValue)value);
      }
    }
    return roots;
  }

  /**
   *
   * @return The root ReferenceValues for this stack frame
   */
  private Collection<ReferenceValue> getReferences() {
    List<ReferenceValue> roots = new ArrayList<ReferenceValue>();
    for (Value value : values) {
      if (value != null && value instanceof ReferenceValue) {
        roots.add((ReferenceValue)value);
      }
    }
    return roots;
  }

  /**
   * Debug printing support: dump this stack frame and return roots.
   * @param width Output field width
   * @return The collection of roots in this frame
   */
  public Collection<ObjectReference> dumpRoots(int width) {
    List<ObjectReference> roots = new ArrayList<ObjectReference>();
    for (int i=0; i < values.length; i++) {
      Value value = values[i];
      String name;
      if (Trace.isEnabled(Item.ROOTS)) {
        name = names != null && i < names.length ? getSlotName(i) : "t"+i;
      } else {
        name = "slot["+i+"]";
      }
      if (value != null && value instanceof ObjectValue) {
        ObjectReference ref = ((ObjectValue)value).getObjectValue();
        System.err.printf(" %s=%s", name, ObjectModel.formatObject(width, ref));
        if (!ref.isNull()) roots.add(ref);
      }
    }
    return roots;
  }

  /**
   * Save a program counter value
   * @param pc The program counter
   */
  public void savePc(int pc) {
    savedPc = pc;
  }

  /** @return the saved program counter */
  public int getSavedPc() {
    return savedPc;
  }

  /**
   * Save a method code array
   * @param code The code array
   */
  public void saveMethod(PseudoOp[] code) {
    savedCode = code;
  }

  /**
   * @return the saved code array
   */
  public PseudoOp[] getSavedMethod() {
    return savedCode;
  }

  /**
   * Set the slot for the return value
   * @param slot The slot in which to store the return value
   */
  public void setResultSlot(int slot) {
    resultSlot = slot;
  }

  /**
   * Clear the return value slot
   */
  public void clearResultSlot() {
    resultSlot = NO_SUCH_SLOT;
  }

  /**
   * Set the return value
   * @param returnValue The procedure return value
   */
  public void setResult(Value returnValue) {
    assert resultSlot != NO_SUCH_SLOT : "Attempt to return a value to a method call with no result slot";
    set(resultSlot,returnValue);
  }
}
