/*
 * (C) Copyright IBM Corp. 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.jni.VM_JNIEnvironment;

//-#if RVM_WITH_ADAPTIVE_SYSTEM
import com.ibm.JikesRVM.adaptive.VM_RuntimeMeasurements;
import com.ibm.JikesRVM.adaptive.VM_Controller;
import com.ibm.JikesRVM.adaptive.VM_ControllerMemory;
//-#endif

//-#if RVM_WITH_OSR
import com.ibm.JikesRVM.adaptive.OSR_OnStackReplacementTrigger;
import com.ibm.JikesRVM.adaptive.OSR_OnStackReplacementEvent;
import com.ibm.JikesRVM.OSR.OSR_PostThreadSwitch;
import com.ibm.JikesRVM.OSR.OSR_ObjectHolder;
//-#endif

/**
 * A java thread's execution context.
 *  
 * @author Derek Lieber
 * @modified Peter F. Sweeney (2003) added support for accessing HPM counter values
 * @modified Matthias Hauswirth (August, 2003) collect mid's with HPM counter values
 */
public class VM_Thread implements VM_Constants, VM_Uninterruptible {

  /**
   * debug flag
   */
  private final static boolean trace = false;

  /**
   * Enumerate different types of yield points for sampling
   */
  public final static int PROLOGUE = 0;
  public final static int BACKEDGE = 1;
  public final static int EPILOGUE = 2;
  public final static int NATIVE_PROLOGUE = 3;
  public final static int NATIVE_EPILOGUE = 4;

  //-#if RVM_WITH_OSR
  public final static int OSRBASE = 98;
  public final static int OSROPT  = 99;
  //-#endif
  
  /* Set by exception handler. */
  public boolean dyingWithUncaughtException = false;

  //-#if RVM_WITH_HPM
  // Keep counter values for each Java thread.
  public HPM_counters hpm_counters = null;
  // when thread is scheduled, record real time
  public long startOfWallTime = -1;
  // globally unique thread id, needed because thread slots can be reused.
  static private int GLOBAL_TID_INITIAL_VALUE = -1;
  private int global_tid = GLOBAL_TID_INITIAL_VALUE;
  public final int getGlobalIndex() { return global_tid; }
  // globally unique thread id counter.  Increment ever time a thread is created.
  static private int global_hpm_tid = 1;
  // globally unique thread id counter.  Increment ever time a thread is created.
  static private Object global_hpm_tid_LOCK = new Object();
  // generate a globally unique thread id (only called from constructors)
  private final void assignGlobalTID() throws VM_PragmaLogicallyUninterruptible
  {
    synchronized (global_hpm_tid_LOCK) {
      global_tid = global_hpm_tid;
      global_hpm_tid++;
    }
    if (global_tid < VM_Scheduler.MAX_THREADS) {
      VM_Scheduler.hpm_threads[global_tid] = this;
      //      VM_Magic.setObjectAtOffset(VM_Scheduler.hpm_threads,global_tid << LOG_BYTES_IN_ADDRESS, this);
    } else {
      // loose information!
    }
    if(VM_HardwarePerformanceMonitors.verbose>=2) {
      VM.sysWrite(" VM_Thread.assignGlobalTID (",threadSlot,") assigned ");
      VM.sysWriteln(global_tid);
    }
  }

  //-#endif

  /**
   * Create a thread with default stack.
   */ 
  public VM_Thread () {
    this(MM_Interface.newStack(STACK_SIZE_NORMAL, false));
  }

  /**
   * Get current thread.
   */ 
  public static VM_Thread getCurrentThread () {
    return VM_Processor.getCurrentProcessor().activeThread;
  }
      
  /**
   * Get current thread's JNI environment.
   */ 
  public final VM_JNIEnvironment getJNIEnv() {
    return jniEnv;
  }

  public final void initializeJNIEnv() throws VM_PragmaInterruptible {
    jniEnv = VM_JNIEnvironment.allocateEnvironment();
  }

  /**
   * Indicate whether the stack of this VM_Thread contains any C frame
   * (used in VM_Runtime.deliverHardwareException for stack resize)
   * @return false during the prolog of the first Java to C transition
   *        true afterward
   */
  public final boolean hasNativeStackFrame() {
    return jniEnv != null && jniEnv.hasNativeStackFrame();
  }

  public String toString() throws VM_PragmaInterruptible {
    return "VM_Thread";
  }

  /**
   * Method to be executed when this thread starts running.
   * Subclass should override with something more interesting.
  */
  public void run () throws VM_PragmaInterruptible {
  }

  /**
   * Method to be executed when this thread termnates.
   * Subclass should override with something more interesting.
   */ 
  public void exit () throws VM_PragmaInterruptible {
  }

  /**
   * Suspend execution of current thread until it is resumed.
   * Call only if caller has appropriate security clearance.
   */ 
  public final void suspend () {
    suspendLock.lock();
    suspendPending = true;
    suspendLock.unlock();
    if (this == getCurrentThread()) yield();
  }
     
  /**
   * Resume execution of a thread that has been suspended.
   * Call only if caller has appropriate security clearance.
   */ 
  public void resume () throws VM_PragmaInterruptible {
    suspendLock.lock();
    suspendPending = false;
    if (suspended) { // this thread is not on any queue
      suspended = false;
      suspendLock.unlock();
      if (trace) VM_Scheduler.trace("VM_Thread", "resume() scheduleThread ", getIndex());
      VM_Processor.getCurrentProcessor().scheduleThread(this);
    } else {         // this thread is queued somewhere
      suspendLock.unlock();
    }
  }

  //-#if RVM_WITH_OSR
  /**
   * Suspends the thread waiting for OSR (rescheduled by recompilation
   * thread when OSR is done).
   */
  public final void osrSuspend() {
        suspendLock.lock();
        suspendPending  = true;
        suspendLock.unlock();
  }
  //-#endif
  
  /**
   * Put given thread to sleep.
   */
  public static void sleepImpl(VM_Thread thread) {
    VM_Scheduler.wakeupMutex.lock();
    yield(VM_Scheduler.wakeupQueue, VM_Scheduler.wakeupMutex);
  }

  /**
   * Put given thread onto the IO wait queue.
   * @param waitData the wait data specifying the file descriptor(s)
   * to wait for.
   */
  public static void ioWaitImpl(VM_ThreadIOWaitData waitData) {
    VM_Thread myThread = getCurrentThread();
    myThread.waitData = waitData;
    yield(VM_Processor.getCurrentProcessor().ioQueue);
  }

  /**
   * Put given thread onto the process wait queue.
   * @param waitData the wait data specifying which process to wait for
   * @param process the <code>VM_Process</code> object associated
   *    with the process
   */
  public static void processWaitImpl(VM_ThreadProcessWaitData waitData, VM_Process process) {
    VM_Thread myThread = getCurrentThread();
    myThread.waitData = waitData;

    // Note that we have to perform the wait on the pthread
    // that created the process, which may involve switching
    // to a different VM_Processor.

    VM_Processor creatingProcessor = process.getCreatingProcessor();
    VM_ProcessorLock queueLock = creatingProcessor.processWaitQueueLock;
    queueLock.lock();

    // This will throw InterruptedException if the thread
    // is interrupted while on the queue.
    yield(creatingProcessor.processWaitQueue, queueLock);
  }

  /**
   * Deliver an exception to this thread.
   */ 
  public final void kill (Throwable externalInterrupt, boolean throwImmediately) {
    // yield() will notice the following and take appropriate action
    this.externalInterrupt = externalInterrupt; 
    if (throwImmediately) {
      // FIXME - this is dangerous.  Only called from Thread.stop(),
      // which is deprecated.
      this.throwInterruptWhenScheduled = true;
    }

    // remove this thread from wakeup and/or waiting queue
    VM_Proxy p = proxy; 
    if (p != null) {
      // If the thread has a proxy, then (presumably) it is either
      // doing a sleep() or a wait(), both of which are interruptible,
      // so let morph() know that it should throw the
      // external interrupt object.
      this.throwInterruptWhenScheduled = true;

      VM_Thread t = p.unproxy(); // t == this or t == null
      if (t != null) t.schedule();
    }
    // TODO!! handle this thread executing native code
  }

  // NOTE: The ThreadSwitchSampling code depends on there
  // being the same number of wrapper routines for all
  // compilers. Please talk to me (Dave G) before changing this. Thanks.
  // We could try a substantially more complex implementation
  // (especially on the opt side) to avoid the wrapper routine, 
  // for the baseline compiler, but I think this is the easiest way
  // to handle all the cases at reasonable runtime-cost. 
  /**
   * Preempt execution of current thread.
   * Called by compiler-generated yieldpoints approx. every 10ms.
   */ 
  public static void threadSwitchFromPrologue() {
    threadSwitch(PROLOGUE);
  }

  /**
   * Preempt execution of current thread.
   * Called by compiler-generated yieldpoints approx. every 10ms.
   */ 
  public static void threadSwitchFromBackedge() {
    threadSwitch(BACKEDGE);
  }

  /**
   * Preempt execution of current thread.
   * Called by compiler-generated yieldpoints approx. every 10ms.
   */ 
  public static void threadSwitchFromEpilogue() {
    threadSwitch(EPILOGUE);
  }

  //-#if RVM_WITH_OSR
  public static void threadSwitchFromOsrBase() {
    threadSwitch(OSRBASE);
  }
  public static void threadSwitchFromOsrOpt() {
    threadSwitch(OSROPT);
  }
  //-#endif

  /**
   * Preempt execution of current thread.
   * Called by compiler-generated yieldpoints approx. every 10ms.
   */ 
  public static void threadSwitch(int whereFrom) throws VM_PragmaNoInline {
    VM_Processor.getCurrentProcessor().threadSwitchRequested   = 0;

    //-#if RVM_FOR_POWERPC
    /* give a chance to check the sync request
     */
    if (VM_Processor.getCurrentProcessor().needsSync) {
      VM_Processor.getCurrentProcessor().needsSync = false;
      // make sure not get stale data
      VM_Magic.isync();
      VM_Synchronization.fetchAndDecrement(VM_Magic.getJTOC(), VM_Entrypoints.toSyncProcessorsField.getOffset(), 1);
    }
    //-#endif

    if (!VM_Processor.getCurrentProcessor().threadSwitchingEnabled()) { 
      // thread in critical section: can't switch right now, defer 'till later
      VM_Processor.getCurrentProcessor().threadSwitchPending = true;
      return;
    }

    /*
     * Thread switch only when interruptQuantumCounter == schedulingMultiplier.
     * Otherwise sample HPM counter values and return.
     * This code must go after checking for thread switch enabled to ensure that we don't
     * interrupt thread switch disabled code!  In particular, the VM.write(String) method.
     */
    VM_Processor.getCurrentProcessor().interruptQuantumCounter++;
    boolean threadSwitch = ! (VM_Processor.getCurrentProcessor().interruptQuantumCounter < VM.schedulingMultiplier);

    //-#if RVM_WITH_HPM
    if (VM_HardwarePerformanceMonitors.sample || threadSwitch) {
      // sample HPM counter values at every interrupt or a thread switch.
      if (VM.BuildForHPM && VM_HardwarePerformanceMonitors.safe && 
          ! VM_HardwarePerformanceMonitors.thread_group) {
        captureCallChainCMIDs(true);
        VM_Thread myThread = getCurrentThread();
        VM_Processor.getCurrentProcessor().hpm.updateHPMcounters(myThread, true, threadSwitch);
      }
    }
    //-#endif 

    if (!threadSwitch) {
      //-#if RVM_WITH_HPM
      // set start time of thread
      getCurrentThread().startOfWallTime = VM_Magic.getTimeBase();
      //-#endif

      return;
    }

    VM_Processor.getCurrentProcessor().interruptQuantumCounter = 0;

    if (VM_Scheduler.debugRequested && VM_Scheduler.allProcessorsInitialized) { 
      // service "debug request" generated by external signal
      VM_Scheduler.debuggerMutex.lock();
      if (VM_Scheduler.debuggerQueue.isEmpty()) { 
        // debugger already running
        VM_Scheduler.debuggerMutex.unlock();
      } else { // awaken debugger
        VM_Thread t = VM_Scheduler.debuggerQueue.dequeue();
        VM_Scheduler.debuggerMutex.unlock();
        t.schedule();
      }
    }

    if (VM_Scheduler.wakeupQueue.isReady()) {
      VM_Scheduler.wakeupMutex.lock();
      VM_Thread t = VM_Scheduler.wakeupQueue.dequeue();
      VM_Scheduler.wakeupMutex.unlock();
      if (t != null) {
        // VM_Scheduler.trace("VM_Thread", 
        // "threadSwitch: awaken ", t.getIndex());
        t.schedule();
      }
    }


    // Reset thread switch count for deterministic thread switching
    if(VM.BuildForDeterministicThreadSwitching) 
      VM_Processor.getCurrentProcessor().deterministicThreadSwitchCount = 
        VM.deterministicThreadSwitchInterval;

    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    // We use threadswitches as a rough approximation of time. 
    // Every threadswitch is a clock tick.
    VM_Controller.controllerClock++;

    //
    // "The idle thread is boring, and does not deserve to be sampled"
    //                           -- AOS Commandment Number 1
    if (!VM_Thread.getCurrentThread().isIdleThread) {

      // First, get the cmid for the method in which the yieldpoint was taken.

      // Get pointer to my caller's frame
      VM_Address fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()); 

      // Skip over wrapper to "real" method
      fp = VM_Magic.getCallerFramePointer(fp);                             
      int ypTakenInCMID = VM_Magic.getCompiledMethodID(fp);

      // Next, get the cmid for that method's caller.
      fp = VM_Magic.getCallerFramePointer(fp);
      int ypTakenInCallerCMID = VM_Magic.getCompiledMethodID(fp);

      // Determine if ypTakenInCallerCMID actually corresponds to a real 
      // Java stackframe.
      boolean ypTakenInCallerCMIDValid = true;
      VM_CompiledMethod ypTakenInCM = VM_CompiledMethods.getCompiledMethod(ypTakenInCMID);

      // Check for one of the following:
      //    Caller is out-of-line assembly (no VM_Method object) or top-of-stack psuedo-frame
      //    Caller is a native method
      if (ypTakenInCallerCMID == INVISIBLE_METHOD_ID    ||
          ypTakenInCM.getMethod().getDeclaringClass().isBridgeFromNative()) { 
        ypTakenInCallerCMIDValid = false;
      } 

      //-#if RVM_WITH_OSR   
      // check if there are pending osr request
      if ((VM_Controller.osrOrganizer != null) 
          && (VM_Controller.osrOrganizer.osr_flag)) {
        VM_Controller.osrOrganizer.activate(); 
      }
     
      if (!VM_Thread.getCurrentThread().isSystemThread) {
        boolean baseToOptOSR = false;
        if (whereFrom == VM_Thread.BACKEDGE) {
          if (ypTakenInCM.isOutdated()) {
            baseToOptOSR = true;
          }
        }

        if (baseToOptOSR || (whereFrom == VM_Thread.OSROPT)) {  
          // get this fram pointer
          VM_Address tsFP = VM_Magic.getFramePointer();         
          // Get pointer to my caller's frame
          VM_Address tsFromFP = VM_Magic.getCallerFramePointer(tsFP);
          // Skip over wrapper to "real" method
          VM_Address realFP = VM_Magic.getCallerFramePointer(tsFromFP);
          
          VM_Address stackbeg = VM_Magic.objectAsAddress(VM_Thread.getCurrentThread().stack);
          
          VM_Offset tsFromFPoff = tsFromFP.diff(stackbeg);
          VM_Offset realFPoff = realFP.diff(stackbeg);
          
          OSR_OnStackReplacementTrigger.trigger(ypTakenInCMID, 
                                                tsFromFPoff,
                                                realFPoff,
                                                whereFrom);
        }
      }
      //-#endif

      // Now that we have the basic information we need, 
      // notify all currently registered listeners
      if (VM_RuntimeMeasurements.hasMethodListener()){
        // set the Caller CMID to -1 if invalid
        if (!ypTakenInCallerCMIDValid) ypTakenInCallerCMID = -1;  
        VM_RuntimeMeasurements.activateMethodListeners(ypTakenInCMID,
                                                       ypTakenInCallerCMID, 
                                                       whereFrom);
      }

      if (ypTakenInCallerCMIDValid && 
          VM_RuntimeMeasurements.hasContextListener()) {
        // Have to start over again in case an intervening GC has moved fp 
        //    since the last time we did this.

        // Get pointer to my caller's frame
        fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());

        // Skip over wrapper to "real" method
        fp = VM_Magic.getCallerFramePointer(fp);                         
        VM_RuntimeMeasurements.activateContextListeners(fp, whereFrom);

      }

      if (VM_RuntimeMeasurements.hasNullListener()){
        VM_RuntimeMeasurements.activateNullListeners(whereFrom);
      }
    }
    //-#endif

    // VM_Scheduler.trace("VM_Thread", "threadSwitch");
    timerTickYield(whereFrom);

    //-#if RVM_WITH_OSR
    VM_Thread myThread = getCurrentThread();
    if (myThread.isWaitingForOsr) {
      OSR_PostThreadSwitch.postProcess(myThread);
    }
    //-#endif 
  }

  /**
   * Suspend execution of current thread, in favor of some other thread.
   * Move this thread to a random virtual processor (for minimal load balancing)
   * if this processor has other runnable work.
   *
   * @param whereFrom  backedge, prologue, epilogue?
   */ 
  public static void timerTickYield (int whereFrom) {
    VM_Thread myThread = getCurrentThread();
    // thread switch
    myThread.beingDispatched = true;
    if (trace) VM_Scheduler.trace("VM_Thread", "timerTickYield() scheduleThread ", myThread.getIndex());
    VM_Processor.getCurrentProcessor().scheduleThread(myThread);
    morph(true);
  }

  //BEGIN HRM
  //-#if RVM_WITH_HPM
  /**
   * Capture the CMIDs of 
   * a) this method's caller, and the caller's caller, or
   * b) this method's caller's caller, and the caller's caller's caller (if startWithCallersCaller is true)
   * Sets the callee_CMID and caller_CMID of the processor's hpm.
   *
   * @param startWithCallersCaller walk to next activation record if true
   * @throws  do not inline
   */ 
  public static final void captureCallChainCMIDs (final boolean startWithCallersCaller) 
    throws VM_PragmaNoInline 
  {
    final VM_Address myFp = VM_Magic.getFramePointer();
    final VM_Address callerFp = VM_Magic.getCallerFramePointer(myFp);
    // choose frame pointer of first frame to get cmid from
    VM_Address startFp;
    if (startWithCallersCaller) {
      startFp = VM_Magic.getCallerFramePointer(callerFp);
    } else {
      startFp = callerFp;
    }
    // get cmid1
    final VM_Address fp1 = startFp;
    final int callee_CMID = VM_Magic.getCompiledMethodID(fp1);
    if (fp1.EQ(STACKFRAME_SENTINEL_FP)) {
      VM.sysWriteln("***VM_Thread.captureCallChainCMIDs() fp1 == STACKFRAME_SENTINEL_FP***");
      dumpCallStack(); VM.sysExit(-1);
    }
    if (callee_CMID == INVISIBLE_METHOD_ID) {
      if (VM_HardwarePerformanceMonitors.verbose>=4) {
        VM.sysWriteln("***VM_Thread.captureCallChainCMIDs() callee_CMID == INVISIBLE_METHOD_ID***");
        //      dumpCallStack(); 
      }
    }

    // get cmid2
    final VM_Address fp2 = VM_Magic.getCallerFramePointer(startFp);
    final int caller_CMID = VM_Magic.getCompiledMethodID(fp2);
    if (fp2.EQ(STACKFRAME_SENTINEL_FP)) {
      VM.sysWriteln("***VM_Thread.captureCallChainCMIDs() fp2 == STACKFRAME_SENTINEL_FP***");
      dumpCallStack(); VM.sysExit(-1);
    }
    if (caller_CMID==INVISIBLE_METHOD_ID) {
      VM.sysWriteln("***VM_Thread.captureCallChainCMIDs() caller_CMID == INVISIBLE_METHOD_ID***");
      dumpCallStack(); VM.sysExit(-1);
    }
    // save CMIDs in VM_Processor's HPM object
    VM_Processor.getCurrentProcessor().hpm.callee_CMID = callee_CMID;
    VM_Processor.getCurrentProcessor().hpm.caller_CMID = caller_CMID;
    VM_Processor.getCurrentProcessor().hpm.cmidAvailable = true;
  }
  /**
   * Crawl the threads stack and print out activation records.
   *
   * @throws do not inline this method
   */
  public static final void dumpCallStack () throws VM_PragmaNoInline 
  {
    VM.sysWriteln("dumpCallStack()");
    VM_Address fp = VM_Magic.getFramePointer();
    int cmid = VM_Magic.getCompiledMethodID(fp);
    while (cmid!=VM_Constants.STACKFRAME_SENTINEL_FP.toInt() && cmid!=VM_Constants.INVISIBLE_METHOD_ID) {
      VM.sysWrite("  ",cmid);
      final VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
      if (cm==null) {
        VM.sysWriteln(" cm==null!");
        return;
      } else {
        final VM_Method m = cm.getMethod();
        if (m==null) {
          VM.sysWriteln(" m==null");
          return;
        } else {
          VM.sysWrite(" m: [",m.getId(),"] ");
          VM.sysWrite(m.getDeclaringClass().getDescriptor());
          VM.sysWrite(m.getName());
          VM.sysWrite(m.getDescriptor());
          VM.sysWrite("  ");
          if (m.getDeclaringClass().isBridgeFromNative()) {
            VM.sysWriteln("isBridgeFromNative!");
            return;
          } else {
            VM.sysWriteln();
            fp = VM_Magic.getCallerFramePointer(fp);
            cmid = VM_Magic.getCompiledMethodID(fp);
          }
        }
      }
    }
  }
  //-#endif
  //END HRM

  /**
   * Suspend execution of current thread, in favor of some other thread.
   */ 
  public static void yield () throws VM_PragmaNoInline {
    //BEGIN HRM
    //-#if RVM_WITH_HPM
    captureCallChainCMIDs(false);
    // sample HPM counter values at every yield
    if (VM.BuildForHPM && VM_HardwarePerformanceMonitors.safe && 
        ! VM_HardwarePerformanceMonitors.thread_group) {
      VM_Thread myThread = getCurrentThread();
      VM_Processor.getCurrentProcessor().hpm.updateHPMcounters(myThread, false, true);
    }
    //-#endif
    //END HRM
    VM_Thread myThread = getCurrentThread();
    myThread.beingDispatched = true;
    VM_Processor.getCurrentProcessor().readyQueue.enqueue(myThread);
    morph(false);
  }

  /**
   * Suspend execution of current thread in favor of some other thread.
   * @param q queue to put thread onto (must be processor-local, ie. 
   * not guarded with a lock)
  */
  public static void yield (VM_AbstractThreadQueue q)  throws VM_PragmaNoInline {
    //BEGIN HRM
    //-#if RVM_WITH_HPM
    captureCallChainCMIDs(false);
    // sample HPM counter values at every yield
    if (VM.BuildForHPM && VM_HardwarePerformanceMonitors.safe && 
        ! VM_HardwarePerformanceMonitors.thread_group) {
      VM_Thread myThread = getCurrentThread();
      VM_Processor.getCurrentProcessor().hpm.updateHPMcounters(myThread, false, true);
    }
    //-#endif
    //END HRM
    VM_Thread myThread = getCurrentThread();
    myThread.beingDispatched = true;
    q.enqueue(myThread);
    morph(false);
  }
  
  /**
   * Suspend execution of current thread in favor of some other thread.
   * @param q queue to put thread onto
   * @param l lock guarding that queue (currently locked)
   */ 
  public static void yield (VM_AbstractThreadQueue q, VM_ProcessorLock l) throws VM_PragmaNoInline  {
    //BEGIN HRM
    //-#if RVM_WITH_HPM
    captureCallChainCMIDs(false);
    // sample HPM counter values at every yield
    if (VM.BuildForHPM && VM_HardwarePerformanceMonitors.safe && 
        ! VM_HardwarePerformanceMonitors.thread_group) {
      VM_Thread myThread = getCurrentThread();
      VM_Processor.getCurrentProcessor().hpm.updateHPMcounters(myThread, false, true);
    }
    //-#endif
    //END HRM
    VM_Thread myThread = getCurrentThread();
    myThread.beingDispatched = true;
    q.enqueue(myThread);
    l.unlock();
    morph(false);
  }

  /**
   * For timed wait, suspend execution of current thread in favor of some other thread.
   * Put a proxy for the current thread 
   *   on a queue waiting a notify, and 
   *   on a wakeup queue waiting for a timeout.
   *
   * @param ql the VM_ProxyWaitingQueue upon which to wait for notification
   * @param l1 the VM_ProcessorLock guarding q1 (currently locked)
   * @param q2 the VM_ProxyWakeupQueue upon which to wait for timeout
   * @param l2 the VM_ProcessorLock guarding q2 (currently locked)
   */ 
  static void yield (VM_ProxyWaitingQueue q1, VM_ProcessorLock l1, VM_ProxyWakeupQueue q2, VM_ProcessorLock l2) throws VM_PragmaNoInline {
    //BEGIN HRM
    //-#if RVM_WITH_HPM
    captureCallChainCMIDs(false);
    // sample HPM counter values at every yield
    if (VM.BuildForHPM && VM_HardwarePerformanceMonitors.safe && 
        ! VM_HardwarePerformanceMonitors.thread_group) {
      VM_Thread myThread = getCurrentThread();
      VM_Processor.getCurrentProcessor().hpm.updateHPMcounters(myThread, false, true);
    }
    //-#endif
    //END HRM
    VM_Thread myThread = getCurrentThread();
    myThread.beingDispatched = true;
    q1.enqueue(myThread.proxy); // proxy has been cached before locks were obtained
    q2.enqueue(myThread.proxy); // proxy has been cached before locks were obtained
    l1.unlock();
    l2.unlock();
    morph(false);
  }

  static void morph () {
    morph(false);
  }

  /**
   * Current thread has been placed onto some queue. Become another thread.
   * @param timerTick   timer interrupted if true
   */ 
  static void morph (boolean timerTick) {
    VM_Magic.sync();  // to ensure beingDispatched flag written out to memory
    if (trace) VM_Scheduler.trace("VM_Thread", "morph ");
    VM_Thread myThread = getCurrentThread();
    if (VM.VerifyAssertions) {
      if (!VM_Processor.getCurrentProcessor().threadSwitchingEnabled()) {
        VM.sysWrite("no threadswitching on proc ", VM_Processor.getCurrentProcessor().id);
        VM.sysWriteln(" with addr ", VM_Magic.objectAsAddress(VM_Processor.getCurrentProcessor()));
      }
      VM._assert(VM_Processor.getCurrentProcessor().threadSwitchingEnabled(), "thread switching not enabled");
      VM._assert(myThread.beingDispatched == true, "morph: not beingDispatched");
    }
    // become another thread
    //
    VM_Processor.getCurrentProcessor().dispatch(timerTick);
    // respond to interrupt sent to this thread by some other thread
    //
    if (myThread.externalInterrupt != null && myThread.throwInterruptWhenScheduled) {
      postExternalInterrupt(myThread);
    }
  }

  private static void postExternalInterrupt(VM_Thread myThread) throws VM_PragmaLogicallyUninterruptible {
    Throwable t = myThread.externalInterrupt;
    myThread.externalInterrupt = null;
    myThread.throwInterruptWhenScheduled = false;
    t.fillInStackTrace();
    VM_Runtime.athrow(t);
  }

  //----------------------------------//
  // Interface to scheduler subsystem //
  //----------------------------------//

  /**
   * Put this thread on ready queue for subsequent execution on a future 
   * timeslice.
   * Assumption: VM_Thread.contextRegisters are ready to pick up execution
   *             ie. return to a yield or begin thread startup code
   * 
   * !!TODO: consider having an argument to schedule() that tells what priority
   *         to give the thread. Then eliminate scheduleHighPriority().
   */ 
  public final void schedule () {
    if (trace) VM_Scheduler.trace("VM_Thread", "schedule", getIndex());
    VM_Processor.getCurrentProcessor().scheduleThread(this);
  }

  /**
   * Put this thread at the front of the ready queue for subsequent 
   * execution on a future timeslice.
   * Assumption: VM_Thread.contextRegisters are ready to pick up execution
   *             ie. return to a yield or begin thread startup code
   * !!TODO: this method is a no-op, stop using it
   */ 
  public final void scheduleHighPriority () {
    if (trace) VM_Scheduler.trace("VM_Thread", "scheduleHighPriority", getIndex());
    VM_Processor.getCurrentProcessor().scheduleThread(this);
  }

  /**
   * Begin execution of current thread by calling its "run" method.
   */ 
  private static void startoff () throws VM_PragmaInterruptible {
    VM_Thread currentThread = getCurrentThread();
    //-#if RVM_WITH_HPM
    if (VM_HardwarePerformanceMonitors.verbose>=5) {
      VM.sysWriteln("***VM_Thread.startoff() run thread "+currentThread.getIndex()+"***!");
    }
    //-#endif 
    currentThread.run();

    terminate();
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }


  /**
   * Update internal state of Thread and Scheduler to indicate that
   * a thread is about to start
   */
  final void registerThread() {
    isAlive = true; 
    VM_Scheduler.threadCreationMutex.lock();
    VM_Scheduler.numActiveThreads += 1;
    if (isDaemon) VM_Scheduler.numDaemons += 1;
    VM_Scheduler.threadCreationMutex.unlock();
  }


  /**
   * Start execution of 'this' by putting it on the appropriate queue
   * of an unspecified virutal processor.
   */
  public synchronized void start() throws VM_PragmaInterruptible {
    registerThread();
    schedule();
  }

  /**
   * Start execution of 'this' by putting it on the given queue.
   * Precondition: If the queue is global, caller must have the appropriate mutex.
   * @param q the VM_ThreadQueue on which to enqueue this thread.
   */
  final void start(VM_ThreadQueue q) {
    registerThread();
    q.enqueue(this);
  }

  /**
   * Terminate execution of current thread by abandoning all 
   * references to it and
   * resuming execution in some other (ready) thread.
   */ 
  static void terminate () throws VM_PragmaInterruptible {
    boolean terminateSystem = false;
    if (trace) VM_Scheduler.trace("VM_Thread", "terminate");

    //-#if RVM_WITH_HPM
    // sample HPM counter values at every interrupt or a thread switch.
    if (VM.BuildForHPM && VM_HardwarePerformanceMonitors.safe && 
        ! VM_HardwarePerformanceMonitors.thread_group) {
      captureCallChainCMIDs(false);
      VM_Thread myThread = getCurrentThread();
      if (VM_HardwarePerformanceMonitors.verbose>=5) {
        VM.sysWriteln("***VM_Thread.startoff() terminate thread "+myThread.getIndex()+"***!");
      }
      VM_Processor.getCurrentProcessor().hpm.updateHPMcounters(myThread, false, true);
    }
    //-#endif 


    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    VM_RuntimeMeasurements.monitorThreadExit();
    //-#endif

    VM_Thread myThread = getCurrentThread();
    // allow java.lang.Thread.exit() to remove this thread from ThreadGroup
    myThread.exit(); 

    synchronized (myThread) { // release anybody waiting on this thread - 

        // begin critical section
        //
        VM_Scheduler.threadCreationMutex.lock();
        VM_Processor.getCurrentProcessor().disableThreadSwitching();
        
        // in particular, see java.lang.Thread.join()
        myThread.isAlive = false;
        myThread.notifyAll();
    }
        
    //
    // if the thread terminated because of an exception, remove
    // the mark from the exception register object, or else the
    // garbage collector will attempt to relocate its ip field.
    myThread.hardwareExceptionRegisters.inuse = false;
    
    VM_Scheduler.numActiveThreads -= 1;
    if (myThread.isDaemon)
      VM_Scheduler.numDaemons -= 1;
    if (VM_Scheduler.numDaemons == VM_Scheduler.numActiveThreads) {
      // no non-daemon thread remains
      terminateSystem = true;
    }

    // end critical section
    //
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
    VM_Scheduler.threadCreationMutex.unlock();
    if (VM.VerifyAssertions) 
      VM._assert(VM_Processor.getCurrentProcessor().threadSwitchingEnabled());

    if (terminateSystem) {
      if (myThread.dyingWithUncaughtException)
        VM.sysExit(VM.exitStatusDyingWithUncaughtException);
      else if (myThread instanceof MainThread) {
        MainThread mt = (MainThread) myThread;
        if (! mt.launched) {
          VM.sysExit(VM.exitStatusMainThreadCouldNotLaunch);
        }
      }
      VM.sysExit(0);
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
    
    if (myThread.jniEnv != null) {
      VM_JNIEnvironment.deallocateEnvironment(myThread.jniEnv);
      myThread.jniEnv = null;
    }

    // become another thread
    // begin critical section
    //
    VM_Scheduler.threadCreationMutex.lock();

    myThread.releaseThreadSlot();
    
    myThread.beingDispatched = true;
    VM_Scheduler.threadCreationMutex.unlock();

    VM_Processor.getCurrentProcessor().dispatch(false);

    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }
  
  /**
   * Get this thread's index in VM_Scheduler.threads[]
   */ 
  public final int getIndex()  throws VM_PragmaLogicallyUninterruptible
  { return threadSlot; }
  
  /**
   * Get this thread's id for use in lock ownership tests.
   */ 
  public final int getLockingId() { return threadSlot << VM_ThinLockConstants.TL_THREAD_ID_SHIFT; }
  
  private static final boolean traceAdjustments = false;
  
  /**
   * Change size of currently executing thread's stack.
   * @param newSize    new size (in bytes)
   * @param exceptionRegisters register state at which stack overflow trap 
   * was encountered (null --> normal method call, not a trap)
   * @return nothing (caller resumes execution on new stack)
   */ 
  public static void resizeCurrentStack(int newSize, 
                                        VM_Registers exceptionRegisters) throws VM_PragmaInterruptible {
    if (traceAdjustments) VM.sysWrite("VM_Thread: resizeCurrentStack\n");
    if (MM_Interface.gcInProgress())
      VM.sysFail("system error: resizing stack while GC is in progress");
    byte[] newStack = MM_Interface.newStack(newSize, false);
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    transferExecutionToNewStack(newStack, exceptionRegisters);
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
    if (traceAdjustments) {
      VM_Thread t = getCurrentThread();
      VM.sysWrite("VM_Thread: resized stack ", t.getIndex());
      VM.sysWrite(" to ", t.stack.length/1024);
      VM.sysWrite("k\n");
    }
  }

  private static void transferExecutionToNewStack(byte[] newStack, 
                                                  VM_Registers 
                                                  exceptionRegisters) throws VM_PragmaNoInline {
    // prevent opt compiler from inlining a method that contains a magic
    // (returnToNewStack) that it does not implement.

    VM_Thread myThread = getCurrentThread();
    byte[]     myStack  = myThread.stack;

    // initialize new stack with live portion of stack we're 
    // currently running on
    //
    //  lo-mem                                        hi-mem
    //                           |<---myDepth----|
    //                +----------+---------------+
    //                |   empty  |     live      |
    //                +----------+---------------+
    //                 ^myStack   ^myFP           ^myTop
    //
    //       +-------------------+---------------+
    //       |       empty       |     live      |
    //       +-------------------+---------------+
    //        ^newStack           ^newFP          ^newTop
    //
    VM_Address myTop   = VM_Magic.objectAsAddress(myStack).add(myStack.length);
    VM_Address newTop  = VM_Magic.objectAsAddress(newStack).add(newStack.length);

    VM_Address myFP    = VM_Magic.getFramePointer();
    VM_Offset  myDepth = myTop.diff(myFP);
    VM_Address newFP   = newTop.sub(myDepth);

    // The frame pointer addresses the top of the frame on powerpc and 
    // the bottom
    // on intel.  if we copy the stack up to the current 
    // frame pointer in here, the
    // copy will miss the header of the intel frame.  Thus we make another 
    // call
    // to force the copy.  A more explicit way would be to up to the 
    // frame pointer
    // and the header for intel.
    VM_Offset delta = copyStack(newStack);

    // fix up registers and save areas so they refer 
    // to "newStack" rather than "myStack"
    //
    if (exceptionRegisters != null)
      adjustRegisters(exceptionRegisters, delta);
    adjustStack(newStack, newFP, delta);

    // install new stack
    //
    myThread.stack      = newStack;
    myThread.stackLimit = VM_Magic.objectAsAddress(newStack).add(STACK_SIZE_GUARD);
    VM_Processor.getCurrentProcessor().activeThreadStackLimit = myThread.stackLimit;
    
    // return to caller, resuming execution on new stack 
    // (original stack now abandoned)
    //
//-#if RVM_FOR_POWERPC
    VM_Magic.returnToNewStack(VM_Magic.getCallerFramePointer(newFP));
//-#endif
//-#if RVM_FOR_IA32
    VM_Magic.returnToNewStack(newFP);
//-#endif
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * This (suspended) thread's stack has been moved.
   * Fixup register and memory references to reflect its new position.
   * @param delta displacement to be applied to all interior references
   */ 
  public final void fixupMovedStack(VM_Offset delta) {
    if (traceAdjustments) VM.sysWrite("VM_Thread: fixupMovedStack\n");

    if (!contextRegisters.getInnermostFramePointer().isZero()) 
      adjustRegisters(contextRegisters, delta);
    if ((hardwareExceptionRegisters.inuse) &&
        (hardwareExceptionRegisters.getInnermostFramePointer().NE(VM_Address.zero()))) {
      adjustRegisters(hardwareExceptionRegisters, delta);
    }
    if (!contextRegisters.getInnermostFramePointer().isZero())
      adjustStack(stack, contextRegisters.getInnermostFramePointer(), delta);
    stackLimit = stackLimit.add(delta);
  }

  /**
   * A thread's stack has been moved or resized.
   * Adjust registers to reflect new position.
   * 
   * @param regsiters registers to be adjusted
   * @param delta     displacement to be applied
   */
  private static void adjustRegisters(VM_Registers registers, VM_Offset delta) {
    if (traceAdjustments) VM.sysWrite("VM_Thread: adjustRegisters\n");

    // adjust FP
    //
    VM_Address newFP = registers.getInnermostFramePointer().add(delta);
    VM_Address ip = registers.getInnermostInstructionAddress();
    registers.setInnermost(ip, newFP);
    if (traceAdjustments) {
      VM.sysWrite(" fp=");
      VM.sysWrite(registers.getInnermostFramePointer());
    }

    // additional architecture specific adjustments
    //  (1) frames from all compilers on IA32 need to update ESP
    int compiledMethodId = VM_Magic.getCompiledMethodID(registers.getInnermostFramePointer());
    if (compiledMethodId != INVISIBLE_METHOD_ID) {
      //-#if RVM_FOR_IA32
      VM_Word old = registers.gprs.get(ESP);
      registers.gprs.set(ESP, old.add(delta));
      if (traceAdjustments) {
        VM.sysWrite(" esp =");
        VM.sysWrite(registers.gprs.get(ESP));
      }
      //-#endif
      if (traceAdjustments) {
        VM_CompiledMethod compiledMethod = 
          VM_CompiledMethods.getCompiledMethod(compiledMethodId);
        VM.sysWrite(" method=");
        VM.sysWrite(compiledMethod.getMethod());
        VM.sysWrite("\n");
      }
    }
  }

  /**
   * A thread's stack has been moved or resized.
   * Adjust internal pointers to reflect new position.
   * 
   * @param stack stack to be adjusted
   * @param fp    pointer to its innermost frame
   * @param delta displacement to be applied to all its interior references
   */
  private static void adjustStack(byte[] stack, VM_Address fp, VM_Offset delta) {
    if (traceAdjustments) VM.sysWrite("VM_Thread: adjustStack\n");

    while (VM_Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
      // adjust FP save area
      //
      VM_Magic.setCallerFramePointer(fp, VM_Magic.getCallerFramePointer(fp).add(delta));
      if (traceAdjustments) {
        VM.sysWrite(" fp=", fp.toWord());
      }
        
      // advance to next frame
      //
      fp = VM_Magic.getCallerFramePointer(fp);
    }
  }

  /**
   * initialize new stack with live portion of stack 
   * we're currently running on
   *
   * <pre>
   *  lo-mem                                        hi-mem
   *                           |<---myDepth----|
   *                 +----------+---------------+
   *                 |   empty  |     live      |
   *                 +----------+---------------+
   *                  ^myStack   ^myFP           ^myTop
   * 
   *       +-------------------+---------------+
   *       |       empty       |     live      |
   *       +-------------------+---------------+
   *        ^newStack           ^newFP          ^newTop
   *  </pre>
   */ 
  private static VM_Offset copyStack (byte[] newStack) {
    VM_Thread myThread = getCurrentThread();
    byte[]     myStack  = myThread.stack;

    VM_Address myTop   = VM_Magic.objectAsAddress(myStack).add(myStack.length);
    VM_Address newTop  = VM_Magic.objectAsAddress(newStack).add(newStack.length);
    VM_Address myFP    = VM_Magic.getFramePointer();
    VM_Offset myDepth  = myTop.diff(myFP);
    VM_Address newFP   = newTop.sub(myDepth);

    // before copying, make sure new stack isn't too small
    //
    if (VM.VerifyAssertions)
      VM._assert(newFP.GE(VM_Magic.objectAsAddress(newStack).add(STACK_SIZE_GUARD)));
    
    VM_Memory.memcopy(newFP, myFP, myDepth.toInt());
    
    return newFP.diff(myFP);
  }

  /**
   * Set the "isDaemon" status of this thread.
   * Although a java.lang.Thread can only have setDaemon invoked on it
   * before it is started, VM_Threads can become daemons at any time.
   * Note: making the last non daemon a daemon will terminate the VM. 
   * 
   * Note: This method might need to be uninterruptible so it is final,
   * which is why it isn't called setDaemon.
   */ 
  protected final void makeDaemon (boolean on) {
    if (isDaemon == on) return;
    isDaemon = on;
    if (!isAlive) return; 
    VM_Scheduler.threadCreationMutex.lock();
    VM_Scheduler.numDaemons += on ? 1 : -1;
    VM_Scheduler.threadCreationMutex.unlock();

    if (VM_Scheduler.numDaemons == VM_Scheduler.numActiveThreads) {
      if (VM.TraceThreads) VM_Scheduler.trace("VM_Thread", 
                                              "last non Daemon demonized");
      VM.sysExit(0);
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
  }
  
  /**
   * Create a thread.
   * @param stack stack in which to execute the thread
   */ 
  public VM_Thread (byte[] stack) {
    this.stack = stack;

    chosenProcessorId = (VM.runningVM ? VM_Processor.getCurrentProcessorId() : 0); // for load balancing
    suspendLock = new VM_ProcessorLock();

    contextRegisters           = new VM_Registers();
    hardwareExceptionRegisters = new VM_Registers();

    // put self in list of threads known to scheduler and garbage collector
    // !!TODO: need growable array here
    // !!TODO: must recycle thread ids
    //

    if (!VM.runningVM) { // create primordial thread (in boot image)
      VM_Scheduler.threads[threadSlot = 
        VM_Scheduler.PRIMORDIAL_THREAD_INDEX] = this; 
      // note that VM_Scheduler.threadAllocationIndex (search hint) 
      // is out of date
      VM_Scheduler.numActiveThreads += 1;
      return;
    }

    // create a normal (ie. non-primordial) thread
    //
    if (trace) VM_Scheduler.trace("VM_Thread", "create");
      
    stackLimit = VM_Magic.objectAsAddress(stack).add(STACK_SIZE_GUARD);

    // get instructions for method to be executed as thread startoff
    //
    VM_CodeArray instructions = VM_Entrypoints.threadStartoffMethod.getCurrentInstructions();

    VM.disableGC();

    // initialize thread registers
    //
    VM_Address ip = VM_Magic.objectAsAddress(instructions);
    VM_Address sp = VM_Magic.objectAsAddress(stack).add(stack.length);
    VM_Address fp = STACKFRAME_SENTINEL_FP;

//-#if RVM_FOR_IA32 

    // initialize thread stack as if "startoff" method had been called
    // by an empty baseline-compiled "sentinel" frame with one local variable
    //
    sp = sp.sub(STACKFRAME_HEADER_SIZE);                   // last word of header
    fp = sp.sub(BYTES_IN_ADDRESS + STACKFRAME_BODY_OFFSET);  
    VM_Magic.setCallerFramePointer(fp, STACKFRAME_SENTINEL_FP);
    VM_Magic.setCompiledMethodID(fp, INVISIBLE_METHOD_ID);

    sp = sp.sub(BYTES_IN_ADDRESS);                                 // allow for one local
    contextRegisters.gprs.set(ESP, sp);
    contextRegisters.gprs.set(VM_BaselineConstants.JTOC,
                              VM_Magic.objectAsAddress(VM_Magic.getJTOC()));
    contextRegisters.fp  = fp;
    contextRegisters.ip  = ip;

//-#else

    // align stack frame
    int INITIAL_FRAME_SIZE = STACKFRAME_HEADER_SIZE;
    fp = VM_Memory.alignDown(sp.sub(INITIAL_FRAME_SIZE), STACKFRAME_ALIGNMENT);
    VM_Magic.setMemoryAddress(fp.add(STACKFRAME_FRAME_POINTER_OFFSET), STACKFRAME_SENTINEL_FP);
    VM_Magic.setMemoryAddress(fp.add(STACKFRAME_NEXT_INSTRUCTION_OFFSET), ip); // need to fix
    VM_Magic.setMemoryInt(fp.add(STACKFRAME_METHOD_ID_OFFSET), INVISIBLE_METHOD_ID);
        
    contextRegisters.gprs.set(FRAME_POINTER, fp);
    contextRegisters.ip  = ip;
    //-#endif

    VM_Scheduler.threadCreationMutex.lock();
    assignThreadSlot();

    //-#if RVM_WITH_HPM
    if (VM_HardwarePerformanceMonitors.booted()) {
      if (hpm_counters == null) hpm_counters = new HPM_counters();
      String name = getClass().toString();
      assignGlobalTID();
      if (VM_HardwarePerformanceMonitors.trace) {
	VM_HardwarePerformanceMonitors.writeThreadToHeaderFile(global_tid, threadSlot, name);
      }
      // stash away name for future use
      VM_HardwarePerformanceMonitors.putThreadName(name, global_tid);
    }
    //-#endif

    VM_Scheduler.threadCreationMutex.unlock();

    VM.enableGC();

    // only do this at runtime because it will call VM_Magic;
    // we set this explictly for the boot thread as part of booting.
    if (VM.runningVM)
      jniEnv = VM_JNIEnvironment.allocateEnvironment();

    //-#if RVM_WITH_OSR
    onStackReplacementEvent = new OSR_OnStackReplacementEvent();
    //-#endif
  }
  
  /**
   * Find an empty slot in threads[] array and bind it to this thread.
   * Assumption: call is guarded by threadCreationMutex.
   */
  private void assignThreadSlot() {
    for (int cnt = VM_Scheduler.threads.length; --cnt >= 1; )
       {
       int index = VM_Scheduler.threadAllocationIndex;
       if (++VM_Scheduler.threadAllocationIndex == VM_Scheduler.threads.length)
          VM_Scheduler.threadAllocationIndex = 1;
       if (VM_Scheduler.threads[index] == null) {
         /*
          *  Problem:
          *
          *  We'd like to say "VM_Scheduler.threads[index] = this;"
          *  but can't do "checkstore" without losing control. Since
          *  we're using magic for the store, we need to perform an
          *  explicit write barrier.
          */
         threadSlot = index;
         if (MM_Interface.NEEDS_WRITE_BARRIER)
           MM_Interface.arrayStoreWriteBarrier(VM_Scheduler.threads, threadSlot, this);
         VM_Magic.setObjectAtOffset(VM_Scheduler.threads,threadSlot << LOG_BYTES_IN_ADDRESS, this);
         return;
         }
       }
    VM.sysFail("too many threads"); // !!TODO: grow threads[] array
  }

  /**
   * Release this thread's threads[] slot.
   * Assumption: call is guarded by threadCreationMutex.
   * Note that after a thread calls this method, it can no longer 
   * make JNI calls.  This matters when exiting the VM, because it
   * implies that this method must be called after the exit callbacks
   * are invoked if they are to be able to do JNI.
   */ 
  final void releaseThreadSlot() {
    /*
     * Problem:
     *
     *  We'd like to say "VM_Scheduler.threads[index] = null;" but
     *  can't do "checkstore" inside dispatcher (with thread switching
     *  enabled) without losing control to a threadswitch, so we must
     *  hand code the operation via magic.  Since we're using magic
     *  for the store, we need to perform an explicit write
     *  barrier. Generational collectors may not care about a null
     *  store, but a reference counting collector sure does.
     */
    if (MM_Interface.NEEDS_WRITE_BARRIER)
      MM_Interface.arrayStoreWriteBarrier(VM_Scheduler.threads, threadSlot, null);
    VM_Magic.setObjectAtOffset(VM_Scheduler.threads, threadSlot << LOG_BYTES_IN_ADDRESS, null);
    VM_Scheduler.threadAllocationIndex = threadSlot;
    // ensure trap if we ever try to "become" this thread again
    if (VM.VerifyAssertions) threadSlot = -1; 
  }

  /**
   * Dump this thread, for debugging.
   */
  public void dump() {
    dump(0);
  }

  /** Dump this thread's info.  The <code>verbosity</code> argument is ignored.
   * We do not use any spacing or newline characters.  Callers are responsible
   * for space-separating or newline-terminating output. */
  public void dump(int verbosity) {
    VM.sysWriteInt(getIndex());   // id
    if (isDaemon)              
      VM.sysWrite("-daemon");     // daemon thread?
    if (isNativeIdleThread)    
      VM.sysWrite("-nativeidle");    // NativeIdle
    if (isIdleThread)          
      VM.sysWrite("-idle");       // idle thread?
    if (isGCThread)            
      VM.sysWrite("-collector");  // gc thread?
    if (isNativeDaemonThread)  
      VM.sysWrite("-nativeDaemon");  
    if (beingDispatched)       
      VM.sysWrite("-being_dispatched");
    // VM.sysWrite("\n");
  }

  public static void dumpAll(int verbosity) {
    for (int i=0; i<VM_Scheduler.threads.length; i++) {
      VM_Thread t = VM_Scheduler.threads[i];
      if (t == null) continue;
      VM.sysWrite("Thread ");
      VM.sysWriteInt(i);
      VM.sysWrite(":  ");
      VM.sysWriteHex(VM_Magic.objectAsAddress(t));
      VM.sysWrite("   ");
      t.dump(verbosity);
      // This is here to compensate for t.dump() not newline-terminating info.
      VM.sysWriteln();
    }
  }
  
  //-----------------//
  // Instance fields //
  //-----------------//

  // support for suspend and resume
  //
  VM_ProcessorLock suspendLock;
  boolean          suspendPending;
  boolean          suspended;
  
  /**
   * Index of this thread in "VM_Scheduler.threads"
   * Value must be non-zero because it is shifted 
   * and used in Object lock ownership tests.
   */
  private int threadSlot;
  /**
   * Proxywait/wakeup queue object.  
   */
  VM_Proxy proxy;
  
  /**
   * Has this thread been suspended via (java/lang/Thread).suspend()
   */
  protected volatile boolean isSuspended; 
 
  /**
   * Is an exception waiting to be delivered to this thread?
   * A non-null value means the next {@link #yield()} should deliver the
   * specified exception to this thread.
   */ 
  Throwable externalInterrupt; 

  /**
   * Should {@link #morph} throw the external
   * interrupt object?
   */
  boolean throwInterruptWhenScheduled;
  
  /**
   * Assertion checking while manipulating raw addresses --
   * see {@link VM#disableGC}/{@link VM#enableGC}.
   * A value of "true" means it's an error for this thread to call "new".
   * This is only used for assertion checking; we do not bother to set it when
   * {@link VM#VerifyAssertions} is false.
   */ 
  public boolean disallowAllocationsByThisThread; 

  /**
   * Counts the depth of outstanding calls to {@link VM#disableGC()}.  If this
   * is set, then we should also have {@link #disallowAllocationsByThisThread}
   * set.  The converse also holds.  
   */
  int disableGCDepth = 0;

  /**
   * Execution stack for this thread.
   */ 
  public byte[] stack;      // machine stack on which to execute this thread
  public VM_Address   stackLimit; // address of stack guard area
  
  /**
   * Place to save register state when this thread is not actually running.
   */ 
  public VM_Registers contextRegisters; 
  
  /**
   * Place to save register state when C signal handler traps 
   * an exception while this thread is running.
   */ 
  public VM_Registers hardwareExceptionRegisters;
  
  /**
   * Place to save/restore this thread's monitor state during "wait" 
   * and "notify".
   */ 
  Object waitObject; // object on which this thread is blocked, waiting for a notification
  int    waitCount;  // lock recursion count for this thread's monitor
  
  /**
   * If this thread is sleeping, when should it be awakened?
   */ 
  long wakeupCycle;

  /**
   * Object specifying the event the thread is waiting for.
   * E.g., set of file descriptors for an I/O wait.
   */
  VM_ThreadEventWaitData waitData;
  
  /**
   * Scheduling priority for this thread.
   * Note that: java.lang.Thread.MIN_PRIORITY <= priority <= MAX_PRIORITY
   */
  protected int priority;
   
  /**
   * Virtual processor that this thread wants to run on 
   * (null --> any processor is ok).
   */ 
  public VM_Processor processorAffinity;

  /**
   * Is this thread's stack being "borrowed" by thread dispatcher 
   * (ie. while choosing next thread to run)?
   */ 
  public boolean beingDispatched;

  /**
   * This thread's successor on a VM_ThreadQueue.
   */ 
  public VM_Thread next;       
  
  /**
   * A thread is "alive" if its start method has been called and the 
   * thread has not yet terminated execution.
   * Set by:   java.lang.Thread.start()
   * Unset by: VM_Thread.terminate()
   */ 
  protected boolean isAlive;

  /**
   * A thread is a "gc thread" if it's an instance of VM_CollectorThread
   */ 
  public boolean isGCThread;

  /**
   * A thread is an "idle thread" if it's an instance of VM_IdleThread
   */ 
  boolean isIdleThread;

  /**
   * A thread is an "native idle  thread" if it's an instance of 
   * VM_NativeIdleThread
   */ 
  boolean isNativeIdleThread;
  
  /**
   * A thread is a "native daemon  thread" if it's an instance of 
   * VM_NativedaemonThread
   */ 
  boolean isNativeDaemonThread;
  
  /**
   * The virtual machine terminates when the last non-daemon (user) 
   * thread terminates.
   */ 
  protected boolean isDaemon;
       
  /**
   * id of processor to run this thread (cycles for load balance)
   */
  public int chosenProcessorId; 

  public VM_JNIEnvironment jniEnv;
  
  /** 
   * Value returned from {@link VM_Time#cycles()} when this thread 
   * started running. If not currently running, then it has the value 0.
   */
  private long startCycle; 

  /**
   * Accumulated cycle count as measured by {@link VM_Time#cycles()} 
   * used by this thread.
   */
  private long totalCycles;  

  /**
   * Accumulate the interval from startCycles to the result
   * of calling {@link VM_Time#cycles()} into {@link #totalCycles}
   * returning the new value of totalCycles.
   * @return totalCycles
   */
  public long accumulateCycles() {
    long now = VM_Time.cycles();
    totalCycles += now - startCycle;
    startCycle = now;
    return totalCycles;
  }

  /**
   * Called from  VM_Processor.dispatch when a thread is about to
   * start executing.
   */
  void startQuantum(long now) {
    startCycle = now;
  }

  /**
   * Called from VM_Processor.dispatch when a thread is about to stop
   * executing.
   */
  void endQuantum(long now) {
    totalCycles += now - startCycle;
    startCycle = 0;
  }

  public double getCPUTimeMillis() {
    return VM_Time.cyclesToMillis(totalCycles);
  }

  public long getTotalCycles() {
    return totalCycles;
  }

  public boolean isIdleThread() {
    return isIdleThread;
  }

  public boolean isGCThread() {
    return isGCThread;
  }

  public boolean isDaemonThread() throws VM_PragmaInterruptible {
    return isDaemon;
  }

  public boolean isAlive() throws VM_PragmaInterruptible {
    return isAlive;
  }

  //-#if RVM_WITH_OSR
  public boolean isSystemThread() {
    return isSystemThread;
  }

  protected boolean isSystemThread = true;

  public OSR_OnStackReplacementEvent onStackReplacementEvent;

  ///////////////////////////////////////////////////////////
  // flags should be packaged or replaced by other solutions

  // the flag indicates whether this thread is waiting for on stack replacement
  // before being rescheduled.
  public boolean isWaitingForOsr = false;
 
  // before call new instructions, we need a bridge to recover register
  // states from the stack frame.
  public VM_CodeArray bridgeInstructions = null;
  public int fooFPOffset = 0;
  public int tsFPOffset = 0;

  // flag to synchronize with osr organizer, the trigger sets osr requests
  // the organizer clear the requests
  public boolean requesting_osr = false;
  //-#endif 
}
