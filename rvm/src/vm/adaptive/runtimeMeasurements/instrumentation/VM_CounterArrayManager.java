/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
// $Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.opt.*;
import com.ibm.JikesRVM.opt.ir.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Statics;
import com.ibm.JikesRVM.VM_CompiledMethods;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_Entrypoints;

/**
 * An implementation of a OPT_InstrumentedEventCounterManager .  It
 * uses an unsynchronized two dimensional array of doubles to allocate
 * its counters. (see OPT_InstrumentedEventCounterManager.java for a
 * description of a counter manager)
 * 
 * NOTE: Much of this class was stolen from VM_CounterArray.java, which
 * is now gone.
 *
 * @author Matthew Arnold
 *
 */
final class VM_CounterArrayManager extends OPT_InstrumentedEventCounterManager
  implements OPT_Operators, OPT_Constants {

  static final boolean DEBUG=false;

  /**
   *  This method is called my a VM_ManagedData object to obtain space
   *  in the counter manager.  A handle or "ID" is returned for the
   *  data to identify its counter space.
   *
   * @param countersNeeded The number of counters being requested 
   * @return The handle for this data's counter space.
   **/
  synchronized public int registerCounterSpace(int countersNeeded) {
    if (counterArrays.length == numCounterArrays) {
      expandCounterArrays();
    }

    // return the handle of the next available counter array
    int handle = numCounterArrays;

    // resize the appropriate counter array
    resizeCounterSpace(handle,countersNeeded);

    numCounterArrays++;

    return handle;
  }

  /**
   *  This method is called to change the number of counters needed by
   *  a particular data.
   *
   * @param handle  The handle describing which the data to be resized
   * @param countersNeeded The number of counters being requested 
   **/
  synchronized public void resizeCounterSpace(int handle, int countersNeeded) {
    // allocate the new array
    double[] temp = new double[countersNeeded];
    
    // transfer the old data to the new array
    if (counterArrays[handle] != null) {
      for (int i=0; i<counterArrays[handle].length; i++) {
        temp[i] = counterArrays[handle][i];
      }
    }
    
    // switch to the new counter array
    counterArrays[handle] = temp;
  }


  /**
   * Return the value of a particular counter
   *
   * @param handle The handle describing which the data to look in
   * @param index The relative index number of the counter
   * @return The value of the counter
   */
  public double getCounter(int handle, int index) {
    return counterArrays[handle][index];
  }

  /**
   * Set the value of a particular counter
   *
   * @param handle The handle describing which the data to look in
   * @param index The relative index number of the counter
   * @param value The new value of the counter
   */
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
  public OPT_Instruction createEventCounterInstruction(int handle, int index,
                                                       double incrementValue) {

    // Doubles are annoying. They are too big to fit into the
    // instruction, so they must be loaded from the JTOC.  That means
    // we need to make sure the increment value is actually in the
    // JTOC.

    long l = Double.doubleToLongBits(incrementValue);
    int offset = VM_Statics.findOrCreateDoubleLiteral(l);

    // Now create the instruction to be returned.
    OPT_Instruction c = 
      InstrumentedCounter.create(INSTRUMENTED_EVENT_COUNTER, 
                                 new OPT_IntConstantOperand(handle),
                                 new OPT_IntConstantOperand(index),
                                 new OPT_DoubleConstantOperand(incrementValue,
                                                               offset));
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
  public void mutateOptEventCounterInstruction(OPT_Instruction counterInst, 
                                               OPT_IR ir) {
    if (VM.VerifyAssertions)
      VM._assert(InstrumentedCounter.conforms(counterInst));

    OPT_IntConstantOperand intOp =
      InstrumentedCounter.getData(counterInst);
    int handle = intOp.value;
    intOp = InstrumentedCounter.getIndex(counterInst);
    int index = intOp.value;

    // Get the base of array
    OPT_RegisterOperand counterArray =  OPT_ConvertToLowLevelIR.
      getStatic(counterInst, ir, VM_Entrypoints.counterArrayManagerCounterArraysField);

    // load counterArrays[handle]
    OPT_RegisterOperand array2 =
      InsertALoadOffset(counterInst,
                        ir, REF_ALOAD,
                        VM_TypeReference.JavaLangObject,
                        counterArray, handle);
    OPT_ConvertToLowLevelIR.
      doArrayLoad(counterInst.prevInstructionInCodeOrder(), ir, INT_LOAD, 2);
                                                                               
    // load counterArrays[handle][index]
    OPT_RegisterOperand origVal =
      InsertALoadOffset(counterInst,
                        ir, DOUBLE_ALOAD,
                        VM_TypeReference.Double,
                        array2, index);
    OPT_ConvertToLowLevelIR.
      doArrayLoad(counterInst.prevInstructionInCodeOrder(),ir, DOUBLE_LOAD, 3);

    
    OPT_Operand incOperand = InstrumentedCounter.getIncrement(counterInst);
    // Insert increment instruction
    OPT_RegisterOperand newValue =
      OPT_ConvertToLowLevelIR.InsertBinary(counterInst, ir, DOUBLE_ADD,
                                           VM_TypeReference.Double, origVal,
                                           incOperand.copy());

    // Store it
    OPT_Instruction store = AStore.mutate(counterInst,DOUBLE_ASTORE,
                                         newValue, array2.copyU2D(),
                                         OPT_IRTools.IC(index),null,null);
    OPT_ConvertToLowLevelIR.doArrayStore(store, ir, DOUBLE_STORE, 3);
                                       
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
  static OPT_RegisterOperand InsertALoadOffset (OPT_Instruction s, OPT_IR ir,
                                                OPT_Operator operator,
                                                VM_TypeReference type,
                                                OPT_Operand reg2,
                                                int offset){
    OPT_RegisterOperand regTarget = ir.regpool.makeTemp(type);
    OPT_Instruction s2 = ALoad.create(operator, regTarget, reg2,
                                      OPT_IRTools.IC(offset),
                                      null,null);
    s.insertBack(s2);
    return  regTarget.copyD2U();
  }    


  /**
   * Still  under construction.
   */
  public void insertBaselineCounter()
  {
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
    for (int i=0; i<len; i++) {
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
    double[][] temp = new double[counterArrays.length*2][];

    // transfer the old counter arrays to the new storage
    for (int i=0; i<counterArrays.length; i++) {
      temp[i] = counterArrays[i];
    }
    counterArrays = temp;
  }

} // end of class



