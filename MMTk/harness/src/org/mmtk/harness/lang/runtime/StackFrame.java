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

import static org.mmtk.harness.lang.Trace.Item.ROOTS;
import static org.vmmagic.unboxed.harness.MemoryConstants.BYTES_IN_WORD;
import static org.vmmagic.unboxed.harness.MemoryConstants.LOG_BYTES_IN_WORD;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mmtk.harness.lang.Declaration;
import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.lang.compiler.CompiledMethod;
import org.mmtk.harness.lang.pcode.PseudoOp;
import org.mmtk.harness.lang.type.Type;
import org.mmtk.harness.sanity.Sanity;
import org.mmtk.harness.vm.ObjectModel;
import org.mmtk.harness.vm.ReferenceProcessor;
import org.mmtk.plan.TraceLocal;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.harness.Clock;

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
  public static final boolean ASSERT_WILL_NOT_MOVE = false;

  /** A sentinel for slots that have no value */
  public static final int NO_SUCH_SLOT = Integer.MAX_VALUE;

  /** The method executed by this stack frame */
  private final CompiledMethod method;

  /** Size in words of the stack frame */
  private final int size;

  /** The values of variables and temporaries */
  private final Value[] values;

  /** The saved program counter during a method call */
  private int savedPc;

  /** The saved instruction array for a method call */
  private PseudoOp[] savedCode;

  /** The slot for the return value of a method call */
  private int resultSlot = NO_SUCH_SLOT;

  /** The base heap address of the stack frame */
  private final Address base;

  /** The address of this thread's environment */
  private final Env env;

  /**
   * Create a stack frame, given a list of declarations and a quantity of temporaries
   *
   * @param env The current execution environment
   * @param method The method being executed in this frame
   * @param base The base address of the shadow stack in the heap
   */
  public StackFrame(Env env, CompiledMethod method, Address base) {
    this.method = method;
    this.size = method.frameSize();
    this.values = new Value[size];
    this.base = base;
    this.env = env;
    for (Declaration d : method.getDecls()) {
      setInternal(d.getSlot(),d.getInitial(), true);
    }
  }

  private Address slotAddress(int slot) {
    return base.plus(slot << LOG_BYTES_IN_WORD);
  }

  /**
   * Return the variable at the given slot in the current stack frame
   * @param slot The stack frame slot
   * @return The value in the slot
   */
  public Value get(int slot) {
    if (slot >= 0) {
      Value value = getInternal(slot);
      if (value == null) {
        Clock.stop();
        Trace.printf("Error: getInternal(%d) = null, variable=%s",slot,method.getSlotName(slot));
        throw new Error();
      }
      if (Trace.isEnabled(Item.EVAL) || method.isWatched(method.getSlotName(slot))) {
        Clock.stop();
        Trace.printf(Item.EVAL, "stack get: (slot %d) %s %s = %s%n",
            slot,
            value.type().toString(),
            method.getSlotName(slot),
            value);
        Clock.start();
      }
      return value;
    }
    return ConstantPool.get(slot);
  }

  /**
   * Read a value from a stack frame slot.  If the slot contains an object
   * reference, this may involve reading the shadow stack and updating
   * the 'cooked' copy from it.
   * @param slot
   * @return
   */
  private Value getInternal(int slot) {
    Value value = values[slot];
    if (value instanceof ObjectValue) {
      ObjectReference shadowValue = env.getReferenceStackSlot(slotAddress(slot));
      if (!shadowValue.equals(value.getObjectValue())) {
        Clock.stop();
        Trace.trace(ROOTS, "Updating %s from shadow stack, slot=%s, stack=%s, shadow=%s",
            method.getSlotName(slot), slotAddress(slot), value, shadowValue);
        Clock.start();
        value = values[slot] = new ObjectValue(shadowValue);
      }
    }
    return value;
  }

  /**
   * Return the type of the variable at the given slot.
   * @param slot The stack frame slot
   * @return The type of the value in the slot
   */
  public Type getType(int slot) {
    return values[slot].type();
  }

  private void setShadowStack(int slot, Value value) {
    env.setStackSlot(slotAddress(slot), value.getObjectValue());
  }

  /**
   * Assign a new value to the given slot
   * @param slot Stack-frame slot to modify
   * @param value New value
   */
  public void set(int slot, Value value) {
    assert value != null : "Unexpected null value";
    setInternal(slot, value, false);
  }

  private void setInternal(int slot, Value value, boolean isInitial) {
    if (Trace.isEnabled(Item.EVAL) || method.isWatched(method.getSlotName(slot))) {
      Clock.stop();
      Trace.printf(Item.EVAL, "stack set: (slot %d) %s %s : %s->%s%s%n",
          slot,
          value.type().toString(),
          method.getSlotName(slot),
          getInternal(slot),
          value.toString(),
          isInitial ? " (I)" : "");
      Clock.start();
    }
    values[slot] = value;
    if (value instanceof ObjectValue) {
      setShadowStack(slot, value);
    }
  }

  /**
   * GC support: trace this stack frame.
   * @param trace The MMTk trace object to receive the roots
   * @return The number of roots found
   */
  public int computeRoots(TraceLocal trace) {
    Clock.stop();
    Trace.trace(Item.ROOTS, "--- Computing roots, stack frame %s (%s) ---",
        method.getName(),base);
    Clock.start();
    int rootCount = 0;
    for (Address root : getRootAddresses()) {
      ObjectReference obj = root.loadObjectReference();
      if (!obj.isNull()) {
        Clock.stop();
        Sanity.assertValid(obj);
        if (Trace.isEnabled(Item.ROOTS)) {
          Trace.trace(Item.ROOTS, "Tracing root %s->%s", root, ObjectModel.getString(obj));
        }
        Clock.start();
        trace.reportDelayedRootEdge(root);
        rootCount++;
      }
    }
    Clock.stop();
    Trace.trace(Item.REFERENCES, "Discovering references");
    Clock.start();
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

  public List<Address> getRootAddresses() {
    List<Address> roots = new ArrayList<Address>();
    Set<Integer> gcMap = getGcMap();
    for (int i=0; i < values.length; i++) {
      Value value = values[i];
      if (value != null && value instanceof ObjectValue) {
        Clock.stop();
        if (!gcMap.contains(i)) {
          Trace.trace(Item.ROOTS, "Slot %d not in GCmap, name=%s", i, method.getSlotName(i));
        }
        Trace.trace(Item.ROOTS, "Root %s (%s->%s)", method.getSlotName(i),
            slotAddress(i), slotAddress(i).loadObjectReference());
        Clock.start();
        roots.add(slotAddress(i));
      }
    }
    for (int root : gcMap) {
      Value value = values[root];
      Clock.stop();
      if (value == null || !(value instanceof ObjectValue)) {
        Trace.trace(Item.ROOTS, "Slot %d in GCmap, but would not be scanned, name=%s", root, method.getSlotName(root));
      }
      Clock.start();
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
      Value value = get(i);
      String name = method.getSlotName(i);
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

  /**
   * @return The size in bytes of this stack frame
   */
  public int sizeInBytes() {
    return size * BYTES_IN_WORD;
  }

  public void prepare() {
    for (int root : getGcMap()) {
      Value value = values[root];
      setShadowStack(root,value);
    }
  }

  public void release() {
    for (int root : getGcMap()) {
      Trace.trace(Item.ROOTS, "Releasing stack slot %s", slotAddress(root));
      getInternal(root);
    }
  }

  /**
   * @return The set of (variable,object-id) pairs
   */
  @SuppressWarnings("unused")
  private Map<String,Integer> objectShadowMap() {
    Map<String,Integer> result = new HashMap<String,Integer>();
    for (int i=0; i < size; i++) {
      if (values[i] != null && values[i] instanceof ObjectValue) {
        ObjectReference obj = slotAddress(i).loadObjectReference();
        result.put(method.getSlotName(i),
            obj.isNull() ? 0 : ObjectModel.getId(obj));
      }
    }
    return result;
  }

  /**
   * @return The set of (variable,object-id) pairs
   */
  @SuppressWarnings("unused")
  private Map<String,Integer> objectMap() {
    Map<String,Integer> result = new HashMap<String,Integer>();
    for (int i=0; i < size; i++) {
      if (values[i] != null && values[i] instanceof ObjectValue) {
        ObjectReference obj = values[i].getObjectValue();
        result.put(method.getSlotName(i),
            obj.isNull() ? 0 : ObjectModel.getId(obj));
      }
    }
    return result;
  }

  /**
   * get the currently executing pseudo-op
   */
  private PseudoOp getCurrentInstr() {
    return savedCode[savedPc-1];
  }

  private Set<Integer> getGcMap() {
    return getCurrentInstr().getGcMap();
  }
}
