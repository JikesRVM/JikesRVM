/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.*;
import java.util.*;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * A VM_EdgeListener defines a listener 
 * that computes a call graph edge from the call stack.
 * After a parameterized number of edges are collected, 
 * it notifies its organizer that the threshold is reached.
 *
 * Defines update's interface.
 *
 * VM_EdgeListener communicates with an organizer through a 
 * integer array, buffer.  Each time this listener is called, 
 * it places a triple of integers in buffer that correspond to
 * the callee, caller, and machine code offset of the call site
 *
 * @author Peter Sweeney
 * @author Michael Hind
 * @date   May 18, 2000
 */
class VM_EdgeListener extends VM_ContextListener 
  implements Uninterruptible, VM_StackframeLayoutConstants {

  protected static final boolean DEBUG = false;

  /**
   * buffer provides the communication channel between the listener and the
   * organizer.
   * The buffer contains an array of triples <callee, caller, address> where
   * the caller and callee are VM_CompiledMethodID's.
   * Initially, buffer contains zeros.  The listener adds triples.
   * When the listener hits the end of the buffer, notify the organizer.
   */
  private int[] buffer;

  /**
   * Number of samples to be taken before issuing callback to controller 
   */
  private int desiredSamples;

  /**
   * Number of samples taken so far
   */
  protected int samplesTaken;

  /**
   * Number of times update is called
   */
  protected int updateCalled;

  /**
   * Constructor
   */
  public VM_EdgeListener() {
    buffer         = null;
    desiredSamples = 0;
  }

  /**
   * @return the number of times that update has been called
   */
  int getTimesUpdateCalled() { 
    return updateCalled; 
  }

  /**
   * Setup buffer and buffer size.  
   * This method must be called before any data can be written to
   * the buffer.
   *
   * @param buffer the allocated buffer to contain the samples, size should
   *      be a muliple of 3
   */
  public void setBuffer(int[] buffer) {
    // ensure buffer is proper length
    if (VM.VerifyAssertions) {
      VM._assert(buffer.length%3 == 0);
    }

    if (DEBUG) {
      VM.sysWrite("VM_EdgeListener.setBuffer(",buffer.length,"): enter\n");     
    }

    this.buffer    = buffer;
    desiredSamples = buffer.length / 3;
    resetBuffer();
  }

  /**
   * This method is called when a call stack edge needs to be 
   * sampled.  Expect the sfp argument to point to the stack frame that
   * contains the target of the edge to be sampled.
   * NOTE: This method is uninterruptible, therefore we don't need to disable
   *       thread switching during stackframe inspection.
   *
   * @param sfp  a pointer to the stack frame that corresponds to the callee of
   *             the call graph edge that is to be sampled.
   * @param whereFrom Was this a yieldpoint in a PROLOGUE, BACKEDGE, or
   *         EPILOGUE?
   */
  public final void update(Address sfp, int whereFrom) {
    if (DEBUG) {
      VM.sysWrite("VM_EdgeListener.update(", sfp, ",", whereFrom);
      VM.sysWriteln("): enter ", samplesTaken);
    }

    VM_Synchronization.fetchAndAdd(this,
                                   VM_Entrypoints.edgeListenerUpdateCalledField.getOffset(),
                                   1);

    // don't take a sample for back edge yield points
    if (whereFrom == VM_Thread.BACKEDGE) return; 

    int calleeCMID    = 0;
    int callerCMID    = 0;
    Address returnAddress = Address.zero();

    if (sfp.loadAddress() == STACKFRAME_SENTINEL_FP) {
      if (DEBUG) VM.sysWrite(" Walking off end of stack!\n");   
      return;
    }

    calleeCMID = VM_Magic.getCompiledMethodID(sfp);
    if (calleeCMID == INVISIBLE_METHOD_ID) {
      if (DEBUG){
        VM.sysWrite(" INVISIBLE_METHOD_ID  (assembler code) ");
        VM.sysWrite(calleeCMID); VM.sysWrite("\n");       
      } 
      return;
    }

    returnAddress = VM_Magic.getReturnAddress(sfp); // return address in caller
    sfp = VM_Magic.getCallerFramePointer(sfp);      // caller's frame pointer
    if(sfp.loadAddress() == STACKFRAME_SENTINEL_FP) {
      if (DEBUG) VM.sysWrite(" Walking off end of stack\n");    
      return;
    }
    callerCMID = VM_Magic.getCompiledMethodID(sfp);
    if (callerCMID == INVISIBLE_METHOD_ID) {
      if (DEBUG) { 
        VM.sysWrite(" INVISIBLE_METHOD_ID  (assembler code) ");
        VM.sysWrite(callerCMID); VM.sysWrite("\n"); 
      } 
      return;
    }

    // store the offset of the return address from the beginning of the 
    // instruction
    VM_CompiledMethod callerCM = VM_CompiledMethods.getCompiledMethod(callerCMID);
    if (callerCM.getCompilerType() == VM_CompiledMethod.TRAP) {
      if (DEBUG) {
        VM.sysWriteln(" HARDWARE TRAP FRAME ");
      }
      return;
    }
    Address beginningOfMachineCode = VM_Magic.objectAsAddress(callerCM.getInstructions());
    Offset callSite = returnAddress.diff(beginningOfMachineCode);

    if (DEBUG){ 
      VM.sysWrite("  <");VM.sysWrite(calleeCMID);VM.sysWrite(",");
      VM.sysWrite(callerCMID);VM.sysWrite(",");VM.sysWrite(returnAddress);
      VM.sysWrite(">\n");
    }
    
    // Find out what sample we are.
    int sampleNumber =  VM_Synchronization.fetchAndAdd(this, 
                                                       VM_Entrypoints.edgeListenerSamplesTakenField.getOffset(),
                                                       1);
    int idx = 3*sampleNumber;

    // If we got buffer slots that are beyond the end of the buffer, that means
    // that we're actually not supposed to take the sample at all (the system
    // is in the process of activating our organizer and processing the buffer).
    if (idx < buffer.length) {
      buffer[idx+0] = calleeCMID;
      buffer[idx+1] = callerCMID;
      buffer[idx+2] = callSite.toInt();

      // If we are the last sample, we need to activate the organizer.
      if (sampleNumber+1 == desiredSamples) {
        activateOrganizer();
      } 
    } 
  }

  /** 
   *  report() noop
   */
  public final void report() {}

  /**
   * Reset (in preparation of starting a new sampling window)
   */
  public void reset() {
     if (DEBUG) VM.sysWrite("VM_EdgeListener.reset(): enter\n");     
     samplesTaken = 0;
     updateCalled = 0;
     resetBuffer();
  }

  /**
   *  Reset the buffer
   */
  private void resetBuffer() {
    for (int i=0; i<buffer.length; i++) {
      buffer[i] = 0;
    }
  }
} 
