/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.*;

/*
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

class VM_EdgeListener extends VM_ContextListener implements VM_Uninterruptible, VM_StackframeLayoutConstants {

  protected static final boolean DEBUG = false;

  /**
   *  Number of samples taken so far
   */
  protected int samplesTaken = 0;

  /**
   * Number of times update is called
   */
  protected int calledUpdate = 0;

  /**
   * returns the number of times that update is called
   * @returns the number of times that update is called
   */
  int getTimesUpdateCalled() { 
    return calledUpdate; 
  }

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
   * the number of triples contained in buffer.
   */
  private int bufferSize;

  /**
   * the index into the buffer
   */
  private int bufferIndex;

  // Number of samples to be taken before issuing callback to controller 
  private int  numberOfBufferTriples;
   /**
    * Setup buffer and buffer size.  
    * This method must be called before any data can be written to
    * the buffer.
    */
  public void setBuffer (int[] buffer, int bufferSize) {
     if (DEBUG) VM.sysWrite("VM_EdgeListener.setBuffer("+bufferSize+"): enter\n");     
     this.buffer           = buffer;
     this.bufferSize       = bufferSize;
     numberOfBufferTriples = bufferSize/3;
     resetBuffer();
  }

  /**
   * Constructor
   * @param _buffer	buffer with which listener communicates with organizer.
   * @param _bufferSize length of buffer in words.
   */
   public VM_EdgeListener() {
      buffer                = null;
      bufferSize            = 0;
      numberOfBufferTriples = 0;
  }

  /**
   * update();  This method is called when a call stack edge needs to be 
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
  public final void update(int sfp, int whereFrom) {
     if(DEBUG) {
        VM.sysWrite("VM_EdgeListener.update("+sfp+","+whereFrom+
		  "): enter "+samplesTaken+"\n");     
     }

     calledUpdate++;

     // only take the sample for prologues and epilogues
     if (whereFrom == VM_Thread.BACKEDGE) return; 

     // in order to walk this stack, we need to disable GC.
     // in order to disable GC, we need to make sure that we
     // have at least STACK_SIZE_GCDISABLED space.  Otherwise,
     // VM.disableGC() will attempt to resize and move this stack. So, sample
     // only if we have this much space left.  TODO:  look into this
     // again.  This may compromise the sampling accuracy a bit.
     if (VM_Magic.getFramePointer() - STACK_SIZE_GCDISABLED < VM_Thread.getCurrentThread().stackLimit) {
       return;
     }

     // avoid race conditions; pasivate the listener before proceeding
     synchronized (this) {
       if (!isActive()) return;
       passivate();
     }

     if (buffer == null) {
	VM.sysWrite("***Error: VM_EdgeListener.update() called "+
		    "before setBuffer() is called!\n");
	VM.sysExit(-1);
     }

     int calleeCMID    = 0;
     int callerCMID    = 0;
     int returnAddress = 0;

     // While GC is disabled, don't do string concatenation!
     VM.disableGC(); // so call stack doesn't change during walk
     
     if(VM_Magic.getMemoryWord(sfp) == STACKFRAME_SENTINAL_FP) {
	if (DEBUG) VM.sysWrite(" Walking off end of stack!\n");	
	VM.enableGC(); return;
     }




     calleeCMID = VM_Magic.getCompiledMethodID(sfp);
     if (calleeCMID == INVISIBLE_METHOD_ID) {
	if(DEBUG){      // Skip assembler routines!
	   VM.sysWrite(" INVISIBLE_METHOD_ID  (assembler code) ");
	   VM.sysWrite(calleeCMID); VM.sysWrite("\n");       } 
	VM.enableGC();return;
     }

     if (VM_ClassLoader.getCompiledMethod(calleeCMID) == null) {
       VM.sysWrite("VM_EdgeListener:update: Found a callee cmid (");
       VM.sysWrite(calleeCMID, false);
       VM.sysWrite(") with a null compiled method, exiting");
       throw new RuntimeException();
     }

     buffer[bufferIndex+0] = calleeCMID;
     // VM_CompiledMethod compiledMethod = VM_ClassLoader.getCompiledMethod(compiledMethodID);
     // VM_Method callee         = compiledMethod.getMethod();

     returnAddress = VM_Magic.getReturnAddress(sfp); // return address in caller
     sfp = VM_Magic.getCallerFramePointer(sfp);      // caller's frame pointer
     if(VM_Magic.getMemoryWord(sfp) == STACKFRAME_SENTINAL_FP) {
	if(DEBUG)VM.sysWrite(" Walking off end of stack\n");	
	VM.enableGC(); return;
     }
     callerCMID = VM_Magic.getCompiledMethodID(sfp);
     if (callerCMID == INVISIBLE_METHOD_ID) {
	if(DEBUG){ VM.sysWrite(" INVISIBLE_METHOD_ID  (assembler code) ");
                   VM.sysWrite(callerCMID); VM.sysWrite("\n"); }	
	buffer[bufferIndex+1] = 0;	VM.enableGC(); return;
     }

     if (VM_ClassLoader.getCompiledMethod(callerCMID) == null) {
       VM.sysWrite("VM_EdgeListener:update: Found a caller cmid (");
       VM.sysWrite(calleeCMID, false);
       VM.sysWrite(") with a null compiled method, exiting");
       throw new RuntimeException();
     }

     buffer[bufferIndex+1] = callerCMID;

     // store the offset of the return address from the beginning of the 
     // instruction
     VM_CompiledMethod callerCM = VM_ClassLoader.getCompiledMethod(callerCMID);
     int beginningOfMachineCode = VM_Magic.objectAsAddress(callerCM.getInstructions());
     buffer[bufferIndex+2] = returnAddress - beginningOfMachineCode;

     if(DEBUG){ 
	VM.sysWrite("  <");VM.sysWrite(calleeCMID);VM.sysWrite(",");
	VM.sysWrite(callerCMID);VM.sysWrite(",");VM.sysWrite(returnAddress);
	VM.sysWrite(">\n");
     }

     if(DEBUG) { // walk stack.
        int fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
	int compiledMethodID = 0;
	for(int i=1; i<6; i++) {
	   if(VM_Magic.getMemoryWord(fp) == STACKFRAME_SENTINAL_FP) {
	      VM.sysWrite(" Walking off end of stack\n"); break;
	   }
	   compiledMethodID = VM_Magic.getCompiledMethodID(fp);
	   if (compiledMethodID == INVISIBLE_METHOD_ID) {
	      VM.sysWrite(" INVISIBLE_METHOD_ID  (assembler code) ");
	      VM.sysWrite(compiledMethodID); VM.sysWrite("\n");     continue;
	   }
	   VM.sysWrite("   Stack frame ");VM.sysWrite(i);VM.sysWrite(": ");
	   VM.sysWrite(compiledMethodID);
	   if(true) {
	      VM_CompiledMethod compiledMethod = null;
	      compiledMethod = VM_ClassLoader.getCompiledMethod(compiledMethodID);
	      VM_Method  method = compiledMethod.getMethod();
	      VM.sysWrite(method);
	   }
	   VM.sysWrite("\n");
	   fp = VM_Magic.getCallerFramePointer(fp);
	}
     }

     VM.enableGC();

     // next triple
     bufferIndex += 3;

    // Keep track of the number of samples taken
     samplesTaken++;
     if (samplesTaken == numberOfBufferTriples) {
	// going to call the organizer.  do not activate.
        // The organizer will call activate for us.
	thresholdReached();
     } else { 
        // threshold not yet reached.  enable listening again.
        // If we reach this else, we are guarantee there are more slots
        // available in the buffer because of the invariant
        //      numberOfBufferTriples = bufferSize/3;
        //
        //   (See setBuffer)
     	synchronized (this) {
	  activate();
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
    if(DEBUG)VM.sysWrite("VM_EdgeListener.thresholdReached(): enter\n");

    // We should be passive already, but make sure.
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
    for (int i=0; i<bufferSize; i++) {
       buffer[i]=0;
    }
    bufferIndex = 0;
  }
}  // end of class
