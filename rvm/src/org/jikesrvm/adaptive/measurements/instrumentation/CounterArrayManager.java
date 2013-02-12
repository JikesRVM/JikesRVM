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
package org.jikesrvm.adaptive.measurements.instrumentation;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.AosEntrypoints;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.InstrumentedEventCounterManager;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.hir2lir.ConvertToLowLevelIR;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.InstrumentedCounter;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.operand.DoubleConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.vmmagic.unboxed.Offset;

/**
 * An implementation of a InstrumentedEventCounterManager .  It
 * uses an unsynchronized two dimensional array of doubles to allocate
 * its counters. (see InstrumentedEventCounterManager.java for a
 * description of a counter manager)
 * <p>
 * NOTE: Much of this class was stolen from CounterArray.java, which
 * is now gone.
 */
public final class CounterArrayManager extends InstrumentedEventCounterManager implements Operators, OptConstants {

  static final boolean DEBUG = false;

  /**
   *  This method is called by a {@link ManagedCounterData} object to obtain space
   *  in the counter manager.  A handle or "ID" is returned for the
   *  data to identify its counter space.
   *
   * @param countersNeeded The number of counters being requested
   * @return The handle for this data's counter space.
   **/
  @Override
  public synchronized int registerCounterSpace(int countersNeeded) {
    if (counterArrays.length == numCounterArrays) {
      expandCounterArrays();
    }

    // return the handle of the next available counter array
    int handle = numCounterArrays;

    // resize the appropriate counter array
    resizeCounterSpace(handle, countersNeeded);

    numCounterArrays++;

    return handle;
  }

  @Override
  public synchronized void resizeCounterSpace(int handle, int countersNeeded) {
    // allocate the new array
    double[] temp = new double[countersNeeded];

    // transfer the old data to the new array
    if (counterArrays[handle] != null) {
      for (int i = 0; i < counterArrays[handle].length; i++) {
        temp[i] = counterArrays[handle][i];
      }
    }

    // switch to the new counter array
    counterArrays[handle] = temp;
  }

  @Override
  public double getCounter(int handle, int index) {
    return counterArrays[handle][index];
  }

  @Override
  public void setCounter(int handle, int index, double value) {
    counterArrays[handle][index] = value;
  }

  /**
   * Create a place holder instruction to represent the counted event.
   *
   * @param handle  The handle of the array for the method
   * @param index   Index within that array
   * @param incrementValue The value to add to the counter
   * @return The counter instruction
   **/
  @Override
  public Instruction createEventCounterInstruction(int handle, int index, double incrementValue) {
    // Now create the instruction to be returned.
    Instruction c =
        InstrumentedCounter.create(INSTRUMENTED_EVENT_COUNTER,
                                   new IntConstantOperand(handle),
                                   new IntConstantOperand(index),
                                   new DoubleConstantOperand(incrementValue, Offset.zero()));
    c.bcIndex = INSTRUMENTATION_BCI;

    return c;
  }

  /**
   *  Take an event counter instruction and mutate it into IR
   *  instructions that will do the actual counting.
   *
   *  Precondition: IR is in LIR
   *
   * @param counterInst   The counter instruction to mutate
   * @param ir            The governing IR
   **/
  @Override
  public void mutateOptEventCounterInstruction(Instruction counterInst, IR ir) {
    if (VM.VerifyAssertions) {
      VM._assert(InstrumentedCounter.conforms(counterInst));
    }

    IntConstantOperand intOp = InstrumentedCounter.getData(counterInst);
    int handle = intOp.value;
    intOp = InstrumentedCounter.getIndex(counterInst);
    int index = intOp.value;

    // Get the base of array
    RegisterOperand counterArray = ConvertToLowLevelIR.
        getStatic(counterInst, ir, AosEntrypoints.counterArrayManagerCounterArraysField);

    // load counterArrays[handle]
    RegisterOperand array2 =
        InsertALoadOffset(counterInst, ir, REF_ALOAD, TypeReference.JavaLangObject, counterArray, handle);
    ConvertToLowLevelIR.
        doArrayLoad(counterInst.prevInstructionInCodeOrder(), ir, INT_LOAD, 2);

    // load counterArrays[handle][index]
    RegisterOperand origVal =
        InsertALoadOffset(counterInst, ir, DOUBLE_ALOAD, TypeReference.Double, array2, index);
    ConvertToLowLevelIR.
        doArrayLoad(counterInst.prevInstructionInCodeOrder(), ir, DOUBLE_LOAD, 3);

    Operand incOperand = InstrumentedCounter.getIncrement(counterInst);
    // Insert increment instruction
    RegisterOperand newValue =
        ConvertToLowLevelIR.insertBinary(counterInst,
                                             ir,
                                             DOUBLE_ADD,
                                             TypeReference.Double,
                                             origVal,
                                             incOperand.copy());

    // Store it
    Instruction store =
        AStore.mutate(counterInst, DOUBLE_ASTORE, newValue, array2.copyU2D(), IRTools.IC(index), null, null);
    ConvertToLowLevelIR.doArrayStore(store, ir, DOUBLE_STORE, 3);

  }

  /**
   * Insert array load off before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param reg2 the base to load from
   * @param offset the offset to load at
   * @return the result operand of the inserted instruction
   */
  static RegisterOperand InsertALoadOffset(Instruction s, IR ir, Operator operator,
                                               TypeReference type, Operand reg2, int offset) {
    RegisterOperand regTarget = ir.regpool.makeTemp(type);
    Instruction s2 = ALoad.create(operator, regTarget, reg2, IRTools.IC(offset), null, null);
    s.insertBefore(s2);
    return regTarget.copyD2U();
  }

  /**
   * Still  under construction.
   */
  @Override
  public void insertBaselineCounter() {
  }

  /**
   * decay counters
   *
   * @param handle  The identifier of the counter array to decay
   * @param rate    The rate at which to decay, i.e. a value of 2 will divide
   *                all values in half
   */
  static void decay(int handle, double rate) {
    int len = counterArrays[handle].length;
    for (int i = 0; i < len; i++) {
      counterArrays[handle][i] /= rate;
    }
  }

  /** Implementation */
  static final int INITIAL_COUNT = 10;
  static final int INCREMENT = 10;
  static int numCounterArrays = 0;
  static double[][] counterArrays = new double[INITIAL_COUNT][];

  /**
   * increment the number of counter arrays
   */
  private static void expandCounterArrays() {
    // expand the number of counter arrays
    double[][] temp = new double[counterArrays.length * 2][];

    // transfer the old counter arrays to the new storage
    for (int i = 0; i < counterArrays.length; i++) {
      temp[i] = counterArrays[i];
    }
    counterArrays = temp;
  }

} // end of class



