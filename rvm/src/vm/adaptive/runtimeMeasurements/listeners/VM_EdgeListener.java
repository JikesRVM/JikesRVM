/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.*;

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
 *
 */

class VM_EdgeListener extends VM_ContextListener 
  implements VM_Uninterruptible, VM_StackframeLayoutConstants {

  protected static final boolean DEBUG = false;

  /**
   * buffer provides the communication channel between the listener and the
   * organizer.
   * The buffer contains an array of triples <callee, caller, address> where
   * the caller and callee are VM_CompiledMethodID's.
   * Initially, buffer contains zeros.  The listener adds triples.
   * When the listener hits the end of the buffer, notify the organizer.
   * (Alternatively, could make the buffer circular.)
   */
  private int[] buffer;

  /**
   * the index in the buffer of the next free triple
   */
  private int nextIndex;

  /**
   * Number of samples to be taken before issuing callback to controller 
   */
  private int desiredSamples;

  /**
   *  Number of samples taken so far
   */
  protected int samplesTaken = 0;

  /**
   * Number of times update is called
   */
  protected int calledUpdate = 0;

  /**
   * Constructor
   */
   public VM_EdgeListener() {
      buffer         = null;
      desiredSamples = 0;
  }

  /**
   * returns the number of times that update is called
   * @returns the number of times that update is called
   */
  int getTimesUpdateCalled() { 
    return calledUpdate; 
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
      VM.assert(buffer.length%3 == 0);
    }

    if (DEBUG) {
      VM.sysWrite("VM_EdgeListener.setBuffer("+buffer.length+"): enter\n");     
    }

    this.buffer    = buffer;
    desiredSamples = buffer.length / 3;
    resetBuffer();
  }

  /**
   * This method is called when a call stack edge needs to be 
   * sampled.  Expect the sfp argument to point to the stack frame that
   * contains the target of the edge to be sampled.
   *
   * RESTRICTION: the execution time of this method is time critical 
   * (we don't want another thread switch to occur inside of it).  
   * Therefore, this method simple stuffs integers into buffer.
   *
   * RESTRICTION: while GC is disabled, do not preform any operation that can
   * allocate space!
   *
   * @param sfp  a pointer to the stack frame that corresponds to the callee of
   *             the call graph edge that is to be sampled.
   * @param whereFrom Was this a yieldpoint in a PROLOGUE, BACKEDGE, or
   *         EPILOGUE?
   */
  public final void update(VM_Address sfp, int whereFrom) {
    if (DEBUG) {
      VM.sysWrite("VM_EdgeListener.update("+sfp.toInt()+","+whereFrom+
		  "): enter "+samplesTaken+"\n");     
    }

    calledUpdate++;

    // don't take a sample for back edge yield points
    if (whereFrom == VM_Thread.BACKEDGE) return; 

    if (buffer == null) {
      VM.sysWrite("***Error: VM_EdgeListener.update() called "+
		  "before setBuffer() is called!\n");
      VM.sysExit(-1);
    }

    int calleeCMID    = 0;
    int callerCMID    = 0;
    VM_Address returnAddress = VM_Address.zero();

    // While GC is disabled, don't do string concatenation!
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
     
    if (VM_Magic.getMemoryWord(sfp) == STACKFRAME_SENTINAL_FP) {
      if (DEBUG) VM.sysWrite(" Walking off end of stack!\n");	
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
      return;
    }

    calleeCMID = VM_Magic.getCompiledMethodID(sfp);
    if (calleeCMID == INVISIBLE_METHOD_ID) {
      if (DEBUG){
	VM.sysWrite(" INVISIBLE_METHOD_ID  (assembler code) ");
	VM.sysWrite(calleeCMID); VM.sysWrite("\n");       
      } 
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
      return;
    }

    if (VM_CompiledMethods.getCompiledMethod(calleeCMID) == null) {
      VM.sysWrite("VM_EdgeListener:update: Found a callee cmid (");
      VM.sysWrite(calleeCMID, false);
      VM.sysWrite(") with a null compiled method. ");
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
      VM.sysFail("Exiting VM");
    }

    returnAddress = VM_Magic.getReturnAddress(sfp); // return address in caller
    sfp = VM_Magic.getCallerFramePointer(sfp);      // caller's frame pointer
    if(VM_Magic.getMemoryWord(sfp) == STACKFRAME_SENTINAL_FP) {
      if (DEBUG) VM.sysWrite(" Walking off end of stack\n");	
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
      return;
    }
    callerCMID = VM_Magic.getCompiledMethodID(sfp);
    if (callerCMID == INVISIBLE_METHOD_ID) {
      if (DEBUG) { 
	VM.sysWrite(" INVISIBLE_METHOD_ID  (assembler code) ");
	VM.sysWrite(callerCMID); VM.sysWrite("\n"); 
      }	
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
      return;
    }

    if (VM_CompiledMethods.getCompiledMethod(callerCMID) == null) {
      VM.sysWrite("VM_EdgeListener:update: Found a caller cmid (");
      VM.sysWrite(calleeCMID, false);
      VM.sysWrite(") with a null compiled method, exiting");
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
      VM.sysFail("Exiting VM");
    }

    // store the offset of the return address from the beginning of the 
    // instruction
    VM_CompiledMethod callerCM = VM_CompiledMethods.getCompiledMethod(callerCMID);
    VM_Address beginningOfMachineCode = VM_Magic.objectAsAddress(callerCM.getInstructions());
    int callSite = returnAddress.diff(beginningOfMachineCode);

    if (DEBUG){ 
      VM.sysWrite("  <");VM.sysWrite(calleeCMID);VM.sysWrite(",");
      VM.sysWrite(callerCMID);VM.sysWrite(",");VM.sysWrite(returnAddress);
      VM.sysWrite(">\n");
    }

    if (DEBUG) { 
      VM_Address fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
      int compiledMethodID = 0;
      for(int i=1; i<6; i++) {
	if (VM_Magic.getMemoryWord(fp) == STACKFRAME_SENTINAL_FP) {
	  VM.sysWrite(" Walking off end of stack\n"); break;
	}
	compiledMethodID = VM_Magic.getCompiledMethodID(fp);
	if (compiledMethodID == INVISIBLE_METHOD_ID) {
	  VM.sysWrite(" INVISIBLE_METHOD_ID  (assembler code) ");
	  VM.sysWrite(compiledMethodID); VM.sysWrite("\n");     continue;
	}
	VM.sysWrite("   Stack frame ");VM.sysWrite(i);VM.sysWrite(": ");
	VM.sysWrite(compiledMethodID);
	if (true) {
	  VM_CompiledMethod compiledMethod = null;
	  compiledMethod = VM_CompiledMethods.getCompiledMethod(compiledMethodID);
	  VM_Method  method = compiledMethod.getMethod();
	  VM.sysWrite(method);
	}
	VM.sysWrite("\n");
	fp = VM_Magic.getCallerFramePointer(fp);
      }
    }

    // done with stack inspection, re-enable GC
    VM_Processor.getCurrentProcessor().enableThreadSwitching();

    // Try to get 3 buffer slots and update nextIndex appropriately
    int idx = VM_Synchronization.fetchAndAdd(this, 
					     VM_Entrypoints.edgeListenerNextIndexField.getOffset(),
					     3);

    // Ensure that we got slots for our sample, if we don't (because another
    // thread is racing with us) we'll just ignore this sample
    if (idx < buffer.length) {
      buffer[idx+0] = calleeCMID;
      buffer[idx+1] = callerCMID;
      buffer[idx+2] = callSite;

      // Determine which sample we just completed.
      // fetchAndAdd returns the value before the increment, add one to
      // determine which sample we were
      int sampleNumber = 
	VM_Synchronization.fetchAndAdd(this, 
				       VM_Entrypoints.edgeListenerSamplesTakenField.getOffset(),
				       1) + 1;

      // If we are the last sample, we need to take action
      if (sampleNumber == desiredSamples) {
	thresholdReached();
      } 
    } 
  }

  /** 
   *  report() noop
   */
  public final void report() {}

  /**
   * Called when threshold is reached.
   */
  public void thresholdReached() {
    if (DEBUG) VM.sysWrite("VM_EdgeListener.thresholdReached(): enter\n");

    passivate();
    
    // Notify the organizer thread
    notifyOrganizer();
    if (DEBUG) VM.sysWrite("VM_EdgeListener.thresholdReached(): exit\n");
  }

  /**
   * Reset (in preparation of starting a new sampling window)
   */
  public void reset() {
     if (DEBUG) VM.sysWrite("VM_EdgeListener.reset(): enter\n");     
     samplesTaken = 0;
     calledUpdate = 0;
     resetBuffer();
  }

  /**
   *  Resets the buffer
   */
  private void resetBuffer() {
    for (int i=0; i<buffer.length; i++) {
      buffer[i] = 0;
    }
    nextIndex = 0;
  }
} 
