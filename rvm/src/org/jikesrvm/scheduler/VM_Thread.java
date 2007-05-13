/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001,2002,2004
 */
package org.jikesrvm.scheduler;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.ArchitectureSpecific.VM_CodeArray;
import org.jikesrvm.ArchitectureSpecific.VM_Registers;
import org.jikesrvm.VM;
import org.jikesrvm.VM_Configuration;
import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.adaptive.OSR_Listener;
import org.jikesrvm.adaptive.OSR_OnStackReplacementEvent;
import org.jikesrvm.adaptive.measurements.VM_RuntimeMeasurements;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.jni.VM_JNIEnvironment;
import org.jikesrvm.memorymanagers.mminterface.MM_Constants;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.mm.mmtk.Barriers;
import org.jikesrvm.objectmodel.VM_ThinLockConstants;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Memory;
import org.jikesrvm.runtime.VM_Process;
import org.jikesrvm.runtime.VM_Runtime;
import org.jikesrvm.runtime.VM_Time;
import org.vmmagic.pragma.BaselineNoRegisters;
import org.vmmagic.pragma.BaselineSaveLSRegisters;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.LogicallyUninterruptible;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NoOptCompile;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * A java thread's execution context.
 * 
 * @see org.jikesrvm.memorymanagers.mminterface.VM_CollectorThread
 *  
 */
@Uninterruptible public class VM_Thread implements ArchitectureSpecific.VM_StackframeLayoutConstants {

  /**
   * debug flag
   */
  private static final boolean trace = false;
  private static final boolean traceTermination = false;

  private Thread thread;         // Can't be final -- the primordial thread is
                                // created by the boot image writer without an
                                // associated java.lang.Thread ; we need to be
                                // booting before we can create a Jikes RVM
                                // java.lang.Thread, at which point we will
                                // perform the assignment.  I am also highly
                                // suspicous of the CollectorThread.
  
  /**
   * Enumerate different types of yield points for sampling
   */
  public static final int PROLOGUE = 0;
  public static final int BACKEDGE = 1;
  public static final int EPILOGUE = 2;
  public static final int NATIVE_PROLOGUE = 3;
  public static final int NATIVE_EPILOGUE = 4;
  public static final int OSROPT  = 5;
  
  /* Set by exception handler. */
  public boolean dyingWithUncaughtException = false;

  /**
   * zero-arg constructor for backwards compatibility.
   */
  public VM_Thread () {
    this(null);
  }

  /**
   * Create a thread with default stack.
   */ 
  public VM_Thread (Thread thread) {
    this(MM_Interface.newStack(STACK_SIZE_NORMAL, false), thread, null);
  }

  /**
   * Create a thread with default stack and with the name myName.
   */ 
  public VM_Thread (Thread thread, String myName) {
    this(MM_Interface.newStack(STACK_SIZE_NORMAL, false), thread, myName);
  }

  /**
   * Get current VM_Thread.
   */ 
  public static VM_Thread getCurrentThread () {
    return VM_Processor.getCurrentProcessor().activeThread;
  }
      
  /** Get the current java.lang.Thread.  Prints out a warning if someone asks
      for a thread too soon.   The warning is for code that does not expect to
      be called early in the boot process.  Use peekJavaLangThread if you
      expect you might get null. */
  @Interruptible
  public Thread getJavaLangThread() { 
    if (thread != null)
      return thread;

    if (!VM.safeToAllocateJavaThread) {
      VM.sysWriteln("Someone asked for a Java thread before it is safe to allocate one -- dumping the stack and returning null.");
      VM_Scheduler.dumpStack();
      return null;
    }
    thread = java.lang.JikesRVMSupport.createThread(this, toString());
    return thread;
  }

  /** Peek at the current java.lang.Thread.  Do not print out any warnings.
      This is used by code that expects it might be called early in the boot
      process.  Use getJavaLangThread if your caller is not necessarily
      prepared to get null. */
  @Interruptible
  public Thread peekJavaLangThread() { 
    if (VM.safeToAllocateJavaThread)
      return getJavaLangThread();
    else
      return thread;
  }

  public void setJavaLangThread(Thread t) {
    thread = t;
  }

  /**
   * Get current thread's JNI environment.
   */ 
  public final VM_JNIEnvironment getJNIEnv() {
    return jniEnv;
  }

  @Interruptible
  public final void initializeJNIEnv() { 
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

  private String myName;
  
  @Interruptible
  public String toString() { 
    return myName == null ? "VM_Thread-" + getIndex() : myName;
  }

  /**
   * Method to be executed when this thread starts running.
   * Subclass should override with something more interesting.
  */
  @Interruptible
  public void run () { 
    thread.run();
  }

  /**
   * Method to be executed when this thread termnates.
   * Subclass should override with something more interesting.
   */ 
  @Interruptible
  public void exit () { 
    /* Early in the boot process, we are running the Boot Thread, which does
     * not have an associated java.lang.Thread, so "thread" will be null.  In
     * this case, if there is trouble during initialization, we still want to
     * be able to exit peacefully, without throwing a confusing additional
     * exception.  */ 
    if (thread != null)
      thread.exit();
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
  @Interruptible
  public void resume () { 
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

  /**
   * Suspends the thread waiting for OSR (rescheduled by recompilation
   * thread when OSR is done).
   */
  public final void osrSuspend() {
    suspendLock.lock();
    suspendPending  = true;
    suspendLock.unlock();
  }
  
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
   * Yieldpoint taken in prologue
   */
  @BaselineSaveLSRegisters //Save all non-volatile registers in prologue  
  @NoOptCompile //We should also have a pragma that saves all non-volatiles in opt compiler, 
    // OSR_BaselineExecStateExtractor.java, should then restore all non-volatiles before stack replacement
    //todo fix this -- related to SaveVolatile
  public static void yieldpointFromPrologue() {
    yieldpoint(PROLOGUE);
  }

  /**
   * Yieldpoint taken on backedge
   */
  @BaselineSaveLSRegisters //Save all non-volatile registers in prologue  
  @NoOptCompile //We should also have a pragma that saves all non-volatiles in opt compiler, 
    // OSR_BaselineExecStateExtractor.java, should then restore all non-volatiles before stack replacement
    //todo fix this -- related to SaveVolatile
  public static void yieldpointFromBackedge() {
    yieldpoint(BACKEDGE);
  }

  /**
   * Yieldpoint taken in epilogue
   */ 
  @BaselineSaveLSRegisters //Save all non-volatile registers in prologue  
  @NoOptCompile //We should also have a pragma that saves all non-volatiles in opt compiler, 
    // OSR_BaselineExecStateExtractor.java, should then restore all non-volatiles before stack replacement
    //todo fix this -- related to SaveVolatile
  public static void yieldpointFromEpilogue() {
    yieldpoint(EPILOGUE);
  }

  /**
   * Process a taken yieldpoint.
   * May result in threadswitch, depending on state of various control
   * flags on the processor object.
   */ 
  @NoInline
  public static void yieldpoint(int whereFrom) { 
    boolean threadSwitch = false;
    int takeYieldpointVal = VM_Processor.getCurrentProcessor().takeYieldpoint;

    VM_Processor.getCurrentProcessor().takeYieldpoint = 0;
    
    // Process request for code-patch memory sync operation
    if (VM.BuildForPowerPC && VM_Processor.getCurrentProcessor().codePatchSyncRequested) {
      VM_Processor.getCurrentProcessor().codePatchSyncRequested = false;
      // TODO: Is this sufficient? Ask Steve why we don't need to sync icache/dcache. --dave
      // make sure not get stale data
      VM_Magic.isync();
      VM_Synchronization.fetchAndDecrement(VM_Magic.getJTOC(), VM_Entrypoints.toSyncProcessorsField.getOffset(), 1);
    }

    // If thread is in critical section we can't switch right now, defer until later
    if (!VM_Processor.getCurrentProcessor().threadSwitchingEnabled()) { 
      if (VM_Processor.getCurrentProcessor().threadSwitchPending != 1) {
        VM_Processor.getCurrentProcessor().threadSwitchPending = takeYieldpointVal;
      }
      return;
    }

    // Process timer interrupt event
    if (VM_Processor.getCurrentProcessor().timeSliceExpired != 0) {
      VM_Processor.getCurrentProcessor().timeSliceExpired = 0;
      
      if (VM.CBSCallSamplesPerTick > 0) {
        VM_Processor.getCurrentProcessor().yieldForCBSCall = true;
        VM_Processor.getCurrentProcessor().takeYieldpoint = -1;
        VM_Processor.getCurrentProcessor().firstCBSCallSample = (++VM_Processor.getCurrentProcessor().firstCBSCallSample) % VM.CBSCallSampleStride;
        VM_Processor.getCurrentProcessor().countdownCBSCall = VM_Processor.getCurrentProcessor().firstCBSCallSample;
        VM_Processor.getCurrentProcessor().numCBSCallSamples = VM.CBSCallSamplesPerTick;
      }
      
      if (VM.CBSMethodSamplesPerTick > 0) {
        VM_Processor.getCurrentProcessor().yieldForCBSMethod = true;
        VM_Processor.getCurrentProcessor().takeYieldpoint = -1;
        VM_Processor.getCurrentProcessor().firstCBSMethodSample = (++VM_Processor.getCurrentProcessor().firstCBSMethodSample) % VM.CBSMethodSampleStride;
        VM_Processor.getCurrentProcessor().countdownCBSMethod = VM_Processor.getCurrentProcessor().firstCBSMethodSample;
        VM_Processor.getCurrentProcessor().numCBSMethodSamples = VM.CBSMethodSamplesPerTick;
      }
      
      if (++VM_Processor.getCurrentProcessor().interruptQuantumCounter >= VM.schedulingMultiplier) {
        threadSwitch = true;
        VM_Processor.getCurrentProcessor().interruptQuantumCounter = 0;

        // Check various scheduling requests/queues that need to be polled periodically
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
            t.schedule();
          }
        }
      }

      if (VM.BuildForAdaptiveSystem) {
        VM_RuntimeMeasurements.takeTimerSample(whereFrom);
      }

      if (threadSwitch && (VM_Processor.getCurrentProcessor().yieldForCBSMethod ||
			   VM_Processor.getCurrentProcessor().yieldForCBSCall)) {
        // want to sample the current thread, not the next one to be scheduled
        // So, defer actual threadswitch until we take all of our samples
        VM_Processor.getCurrentProcessor().threadSwitchWhenCBSComplete = true;
        threadSwitch = false;
      }

      if (VM.BuildForAdaptiveSystem) {
        threadSwitch |= OSR_Listener.checkForOSRPromotion(whereFrom);
      }
      if (threadSwitch) {
        VM_Processor.getCurrentProcessor().yieldForCBSMethod = false;
        VM_Processor.getCurrentProcessor().yieldForCBSCall = false; 
        VM_Processor.getCurrentProcessor().threadSwitchWhenCBSComplete = false;
      }        
    }

    if (VM_Processor.getCurrentProcessor().yieldForCBSCall) {
      if (!(whereFrom == BACKEDGE || whereFrom == OSROPT)) {
        if (--VM_Processor.getCurrentProcessor().countdownCBSCall <= 0) {
          if (VM.BuildForAdaptiveSystem) {
            // take CBS sample
            VM_RuntimeMeasurements.takeCBSCallSample(whereFrom);
          }
          VM_Processor.getCurrentProcessor().countdownCBSCall = VM.CBSCallSampleStride;
          VM_Processor.getCurrentProcessor().numCBSCallSamples--;
          if (VM_Processor.getCurrentProcessor().numCBSCallSamples <= 0) {
            VM_Processor.getCurrentProcessor().yieldForCBSCall = false;
            if (!VM_Processor.getCurrentProcessor().yieldForCBSMethod) {
              VM_Processor.getCurrentProcessor().threadSwitchWhenCBSComplete = false;
              threadSwitch = true;
            }
          
          }
        }
      }
      if (VM_Processor.getCurrentProcessor().yieldForCBSCall) {
        VM_Processor.getCurrentProcessor().takeYieldpoint = -1;
      }
    }
    
    if (VM_Processor.getCurrentProcessor().yieldForCBSMethod) {
      if (--VM_Processor.getCurrentProcessor().countdownCBSMethod <= 0) {
        if (VM.BuildForAdaptiveSystem) {
        // take CBS sample
          VM_RuntimeMeasurements.takeCBSMethodSample(whereFrom);
        }
        VM_Processor.getCurrentProcessor().countdownCBSMethod = VM.CBSMethodSampleStride;
        VM_Processor.getCurrentProcessor().numCBSMethodSamples--;
        if (VM_Processor.getCurrentProcessor().numCBSMethodSamples <= 0) {
          VM_Processor.getCurrentProcessor().yieldForCBSMethod = false;
          if (!VM_Processor.getCurrentProcessor().yieldForCBSCall) {
            VM_Processor.getCurrentProcessor().threadSwitchWhenCBSComplete = false;
            threadSwitch = true;
          }
        }
      }
      if (VM_Processor.getCurrentProcessor().yieldForCBSMethod) {
        VM_Processor.getCurrentProcessor().takeYieldpoint = 1;
      }
    }
    
    // Process request to initiate GC by forcing a thread switch.
    if (VM_Processor.getCurrentProcessor().yieldToGCRequested) {
      VM_Processor.getCurrentProcessor().yieldToGCRequested = false;
      VM_Processor.getCurrentProcessor().yieldForCBSCall = false;
      VM_Processor.getCurrentProcessor().yieldForCBSMethod = false;
      VM_Processor.getCurrentProcessor().threadSwitchWhenCBSComplete = false;
      VM_Processor.getCurrentProcessor().takeYieldpoint = 0;
      threadSwitch = true;
    }
    
    if (VM.BuildForAdaptiveSystem && VM_Processor.getCurrentProcessor().yieldToOSRRequested) {
      VM_Processor.getCurrentProcessor().yieldToOSRRequested = false;
      OSR_Listener.handleOSRFromOpt();
      threadSwitch = true;
    }

    if (threadSwitch) {
      timerTickYield(whereFrom);
    }

    VM_Thread myThread = getCurrentThread();
    if (VM.BuildForAdaptiveSystem && myThread.isWaitingForOsr) {
      ArchitectureSpecific.OSR_PostThreadSwitch.postProcess(myThread);
    }
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

  /**
   * Suspend execution of current thread, in favor of some other thread.
   */ 
  @NoInline
  public static void yield () { 
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
  @NoInline
  public static void yield (VM_AbstractThreadQueue q) { 
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
  @NoInline
  public static void yield (VM_AbstractThreadQueue q, VM_ProcessorLock l) { 
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
   * @param q1 the {@link VM_ProxyWaitingQueue} upon which to wait for notification
   * @param l1 the {@link VM_ProcessorLock} guarding <code>q1</code> (currently locked)
   * @param q2 the {@link VM_ProxyWakeupQueue} upon which to wait for timeout
   * @param l2 the {@link VM_ProcessorLock} guarding <code>q2</code> (currently locked)
   */ 
  @NoInline
  static void yield (VM_ProxyWaitingQueue q1, VM_ProcessorLock l1, VM_ProxyWakeupQueue q2, VM_ProcessorLock l2) { 
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

  @LogicallyUninterruptible
  private static void postExternalInterrupt(VM_Thread myThread) { 
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
   */ 
  public final void schedule () {
    if (trace) VM_Scheduler.trace("VM_Thread", "schedule", getIndex());
    VM_Processor.getCurrentProcessor().scheduleThread(this);
  }

  /**
   * Begin execution of current thread by calling its "run" method.
   */ 
  @Interruptible
  @SuppressWarnings({"unused", "UnusedDeclaration"}) // Called by back-door methods.
  private static void startoff () { 
    VM_Thread currentThread = getCurrentThread();
    if (trace) VM.sysWriteln("VM_Thread.startoff(): about to call ", 
                             currentThread.toString(), ".run()");
    
    currentThread.run();
    if (trace) VM.sysWriteln("VM_Thread.startoff(): finished ", 
                             currentThread.toString(), ".run()");

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
  @Interruptible
  public synchronized void start() { 
    registerThread();
    schedule();
  }

  /**
   * Start execution of 'this' by putting it on the given queue.
   * Precondition: If the queue is global, caller must have the appropriate mutex.
   * @param q the VM_ThreadQueue on which to enqueue this thread.
   */
  public final void start(VM_ThreadQueue q) {
    registerThread();
    q.enqueue(this);
  }

  /**
   * Terminate execution of current thread by abandoning all 
   * references to it and
   * resuming execution in some other (ready) thread.
   */ 
  @Interruptible
  public static void terminate () { 
    boolean terminateSystem = false;
    if (trace) VM_Scheduler.trace("VM_Thread", "terminate");

    if (traceTermination) {
      VM.disableGC();
      VM.sysWriteln("[ BEGIN Verbosely dumping stack at time of thread termination");
      VM_Scheduler.dumpStack();
      VM.sysWriteln("END Verbosely dumping stack at time of creating thread termination ]");
      VM.enableGC();

    }

    if (VM.BuildForAdaptiveSystem) {
      VM_RuntimeMeasurements.monitorThreadExit();
    }

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
    if (traceTermination) {
      VM.sysWriteln("VM_Thread.terminate: myThread.isDaemon = ", 
                    myThread.isDaemon);
      VM.sysWriteln("  VM_Scheduler.numActiveThreads = ", 
                    VM_Scheduler.numActiveThreads);
      VM.sysWriteln("  VM_Scheduler.numDaemons = ", 
                    VM_Scheduler.numDaemons);
      VM.sysWriteln("  terminateSystem = ", 
                    terminateSystem);
    }    

    // end critical section
    //
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
    VM_Scheduler.threadCreationMutex.unlock();
    if (VM.VerifyAssertions) 
      VM._assert( (!VM.fullyBooted && terminateSystem )
                 || VM_Processor.getCurrentProcessor().threadSwitchingEnabled());

    if (terminateSystem) {
      if (myThread.dyingWithUncaughtException)
        /* Use System.exit so that any shutdown hooks are run.  */
        System.exit(VM.EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
      else if (myThread.thread instanceof VM_MainThread) {
        VM_MainThread mt = (VM_MainThread) myThread.thread;
        if (! mt.launched) {
          /* Use System.exit so that any shutdown hooks are run.  It is
           * possible that shutdown hooks may be installed by static
           * initializers which were run by classes initialized before we
           * attempted to run the main thread.  (As of this writing, 24
           * January 2005, the Classpath libraries do not do such a thing, but
           * there is no reason why we should not support this.)   This was
           * discussed on jikesrvm-researchers 
           * on 23 Jan 2005 and 24 Jan 2005. */  
          System.exit(VM.EXIT_STATUS_MAIN_THREAD_COULD_NOT_LAUNCH);
        }
      }
      /* Use System.exit so that any shutdown hooks are run.  */
      System.exit(0);
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
   * Get this thread's index in {@link VM_Scheduler#threads}[].
   */ 
  @LogicallyUninterruptible
  public final int getIndex() { return threadSlot; } 
  
  /**
   * Get this thread's id for use in lock ownership tests.  
   * This is just the thread's index as returned by {@link #getIndex()},
   * shifted appropriately so it can be directly used in the ownership tests. 
   */ 
  public final int getLockingId() { 
    return threadSlot << VM_ThinLockConstants.TL_THREAD_ID_SHIFT; 
  }
  
  private static final boolean traceAdjustments = false;
  
  /**
   * Change the size of the currently executing thread's stack.
   * @param newSize    new size (in bytes)
   * @param exceptionRegisters register state at which stack overflow trap 
   * was encountered (null --> normal method call, not a trap)
   */ 
  @Interruptible
  public static void resizeCurrentStack(int newSize, 
                                        VM_Registers exceptionRegisters) { 
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

  @NoInline
  @BaselineNoRegisters //this method does not a normal return and hence does not execute epilogue --> non-volatiles not restored!
  private static void transferExecutionToNewStack(byte[] newStack, 
                                                  VM_Registers 
                                                  exceptionRegisters) { 
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
    Address myTop   = VM_Magic.objectAsAddress(myStack).plus(myStack.length);
    Address newTop  = VM_Magic.objectAsAddress(newStack).plus(newStack.length);

    Address myFP    = VM_Magic.getFramePointer();
    Offset  myDepth = myTop.diff(myFP);
    Address newFP   = newTop.minus(myDepth);

    // The frame pointer addresses the top of the frame on powerpc and 
    // the bottom
    // on intel.  if we copy the stack up to the current 
    // frame pointer in here, the
    // copy will miss the header of the intel frame.  Thus we make another 
    // call
    // to force the copy.  A more explicit way would be to up to the 
    // frame pointer
    // and the header for intel.
    Offset delta = copyStack(newStack);

    // fix up registers and save areas so they refer 
    // to "newStack" rather than "myStack"
    //
    if (exceptionRegisters != null)
      adjustRegisters(exceptionRegisters, delta);
    adjustStack(newStack, newFP, delta);

    // install new stack
    //
    myThread.stack      = newStack;
    myThread.stackLimit = 
      VM_Magic.objectAsAddress(newStack).plus(STACK_SIZE_GUARD);
    VM_Processor.getCurrentProcessor().activeThreadStackLimit =
      myThread.stackLimit;
    
    // return to caller, resuming execution on new stack 
    // (original stack now abandoned)
    //
    if (VM.BuildForPowerPC)
      VM_Magic.returnToNewStack(VM_Magic.getCallerFramePointer(newFP));
    else if (VM.BuildForIA32)
      VM_Magic.returnToNewStack(newFP);
   
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * This (suspended) thread's stack has been moved.
   * Fixup register and memory references to reflect its new position.
   * @param delta displacement to be applied to all interior references
   */ 
  public final void fixupMovedStack(Offset delta) {
    if (traceAdjustments) VM.sysWrite("VM_Thread: fixupMovedStack\n");

    if (!contextRegisters.getInnermostFramePointer().isZero()) 
      adjustRegisters(contextRegisters, delta);
    if ((hardwareExceptionRegisters.inuse) &&
        (hardwareExceptionRegisters.getInnermostFramePointer().NE(Address.zero()))) {
      adjustRegisters(hardwareExceptionRegisters, delta);
    }
    if (!contextRegisters.getInnermostFramePointer().isZero())
      adjustStack(stack, contextRegisters.getInnermostFramePointer(), delta);
    stackLimit = stackLimit.plus(delta);
  }

  /**
   * A thread's stack has been moved or resized.
   * Adjust registers to reflect new position.
   * 
   * @param registers registers to be adjusted
   * @param delta     displacement to be applied
   */
  private static void adjustRegisters(VM_Registers registers, Offset delta) {
    if (traceAdjustments) VM.sysWrite("VM_Thread: adjustRegisters\n");

    // adjust FP
    //
    Address newFP = registers.getInnermostFramePointer().plus(delta);
    Address ip = registers.getInnermostInstructionAddress();
    registers.setInnermost(ip, newFP);
    if (traceAdjustments) {
      VM.sysWrite(" fp=");
      VM.sysWrite(registers.getInnermostFramePointer());
    }

    // additional architecture specific adjustments
    //  (1) frames from all compilers on IA32 need to update ESP
    int compiledMethodId = VM_Magic.getCompiledMethodID(registers.getInnermostFramePointer());
    if (compiledMethodId != INVISIBLE_METHOD_ID) {
      if (VM.BuildForIA32)
        VM_Configuration.archHelper.adjustESP(registers, delta, traceAdjustments);
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
  private static void adjustStack(byte[] stack, Address fp, Offset delta) {
    if (traceAdjustments) VM.sysWrite("VM_Thread: adjustStack\n");

    while (VM_Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
      // adjust FP save area
      //
      VM_Magic.setCallerFramePointer(fp, VM_Magic.getCallerFramePointer(fp).plus(delta));
      if (traceAdjustments) {
        VM.sysWrite(" fp=", fp.toWord());
      }
        
      // advance to next frame
      //
      fp = VM_Magic.getCallerFramePointer(fp);
    }
  }

  /**
   * Initialize a new stack with the live portion of the stack 
   * we're currently running on.
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
  private static Offset copyStack (byte[] newStack) {
    VM_Thread myThread = getCurrentThread();
    byte[]     myStack  = myThread.stack;

    Address myTop   = VM_Magic.objectAsAddress(myStack).plus(myStack.length);
    Address newTop  = VM_Magic.objectAsAddress(newStack).plus(newStack.length);
    Address myFP    = VM_Magic.getFramePointer();
    Offset myDepth  = myTop.diff(myFP);
    Address newFP   = newTop.minus(myDepth);

    // before copying, make sure new stack isn't too small
    //
    if (VM.VerifyAssertions)
      VM._assert(newFP.GE(VM_Magic.objectAsAddress(newStack).plus(STACK_SIZE_GUARD)));
    
    VM_Memory.memcopy(newFP, myFP, myDepth.toWord().toExtent());
    
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
   *
   * Public so that java.lang.Thread can use it.
   */ 
  public final void makeDaemon (boolean on) {
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
  public VM_Thread (byte[] stack, Thread thread, String myName) {
    this.stack = stack;
    this.thread = thread;
    this.myName = myName;

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

      
      /* Don't create the Thread object for the primordial thread; we'll have
         to do that later. */
      return;
    }
    
    /* We're running the VM; if we weren't, we'd have to wait until boot time
     * to do this. */
    if ( thread == null )
      thread = java.lang.JikesRVMSupport.createThread(this, myName);

    // create a normal (ie. non-primordial) thread
    //
    if (trace) VM_Scheduler.trace("VM_Thread", "create");
      
    stackLimit = VM_Magic.objectAsAddress(stack).plus(STACK_SIZE_GUARD);

    // get instructions for method to be executed as thread startoff
    //
    VM_CodeArray instructions = VM_Entrypoints.threadStartoffMethod.getCurrentEntryCodeArray();

    VM.disableGC();

    // initialize thread registers
    //
    Address ip = VM_Magic.objectAsAddress(instructions);
    Address sp = VM_Magic.objectAsAddress(stack).plus(stack.length);

    /* Initialize the  a thread stack as if "startoff" method had been called by an
     * empty baseline-compiled "sentinel" frame with one local variable. */
    VM_Configuration.archHelper.initializeStack(contextRegisters, ip, sp);
    
    VM_Scheduler.threadCreationMutex.lock();
    assignThreadSlot();

    VM_Scheduler.threadCreationMutex.unlock();

    VM.enableGC();

    // only do this at runtime because it will call VM_Magic;
    // we set this explictly for the boot thread as part of booting.
    if (VM.runningVM)
      jniEnv = VM_JNIEnvironment.allocateEnvironment();

    if (VM.BuildForAdaptiveSystem) {
      onStackReplacementEvent = new OSR_OnStackReplacementEvent();
    } else {
      onStackReplacementEvent = null;
    }
  }
  
  /**
   * Find an empty slot in the {@link VM_Scheduler#threads}[] array and bind
   * it to this thread.  <br>
   * <b>Assumption:</b> call is guarded by threadCreationMutex.
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
         if (threadSlot > VM_Scheduler.threadHighWatermark) {
           VM_Scheduler.threadHighWatermark = threadSlot;
         }
         if (MM_Constants.NEEDS_WRITE_BARRIER)
           MM_Interface.arrayStoreWriteBarrier(VM_Scheduler.threads, 
                                               threadSlot, this);
         VM_Magic.setObjectAtOffset(VM_Scheduler.threads,
                                    Offset.fromIntZeroExtend(threadSlot << VM_SizeConstants.LOG_BYTES_IN_ADDRESS), this);
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
    if (MM_Constants.NEEDS_WRITE_BARRIER)
      MM_Interface.arrayStoreWriteBarrier(VM_Scheduler.threads, 
                                          threadSlot, null);
    VM_Magic.setObjectAtOffset(VM_Scheduler.threads, 
                               Offset.fromIntZeroExtend(threadSlot << VM_SizeConstants.LOG_BYTES_IN_ADDRESS), null);
    if (threadSlot < VM_Scheduler.threadAllocationIndex)
      VM_Scheduler.threadAllocationIndex = threadSlot;
    // ensure trap if we ever try to "become" this thread again
    if (VM.VerifyAssertions) threadSlot = -1; 
  }

  /**
   * Dump this thread's identifying information, for debugging, via
   * {@link VM#sysWrite}.
   * We do not use any spacing or newline characters.  Callers are responsible
   * for space-separating or newline-terminating output. 
   */
  public void dump() {
    dump(0);
  }

  /** 
   * Dump this thread's identifying information, for debugging, via
   * {@link VM#sysWrite}.
   * We pad to a minimum of leftJustify characters. We do not use any spacing
   * characters.  Callers are responsible for space-separating or
   * newline-terminating output.
   *
   * @param leftJustify minium number of characters emitted, with any
   * extra characters being spaces.
   */
  public void dumpWithPadding(int leftJustify) {
    char[] buf = grabDumpBuffer();
    int len = dump(buf);
    VM.sysWrite(buf, len);
    for( int i = leftJustify - len; i > 0; i-- ) {
        VM.sysWrite(" ");
    }
    releaseDumpBuffer();
  }

  /**
   * Dump this thread's identifying information, for debugging, via
   * {@link VM#sysWrite}.
   * We do not use any spacing or newline characters.  Callers are responsible
   * for space-separating or newline-terminating output. 
   *
   *  This function avoids write barriers and allocation.
   *
   * @param verbosity Ignored.
   */
  public void dump(int verbosity) {
    char[] buf = grabDumpBuffer();
    int len = dump(buf);
    VM.sysWrite(buf, len);
    releaseDumpBuffer();
  }

  /** Dump this thread's info, for debugging.  
   *  Copy the info about it into a destination char
   *  array.  We do not use any spacing or newline characters. 
   *
   *  This function may be called during GC; it avoids write barriers and
   *  allocation.   
   *
   *  For this reason, we do not throw an
   *  <code>IndexOutOfBoundsException</code>.  
   *  
   * @param dest char array to copy the source info into.
   * @param offset Offset into <code>dest</code> where we start copying
   *
   * @return 1 plus the index of the last character written.  If we were to
   *         write zero characters (which we won't) then we would return
   *         <code>offset</code>.  This is intended to represent the first
   *         unused position in the array <code>dest</code>.  However, it also
   *         serves as a pseudo-overflow check:  It may have the value
   *         <code>dest.length</code>, if the array <code>dest</code> was
   *         completely filled by the call, or it may have a value greater
   *         than <code>dest.length</code>, if the info needs more than
   *         <code>dest.length - offset</code> characters of space.
   *
   *         -1 if <code>offset</code> is negative.
   *
   */
  public int dump(char[] dest, int offset) {
    offset = sprintf(dest, offset, getIndex());   // id
    if (isDaemon)              
      offset = sprintf(dest, offset, "-daemon");     // daemon thread?
    if (isBootThread)    
      offset = sprintf(dest, offset, "-Boot");    // Boot (Primordial) thread
    if (isMainThread)    
      offset = sprintf(dest, offset, "-main");    // Main Thread
    if (isIdleThread)          
      offset = sprintf(dest, offset, "-idle");       // idle thread?
    if (isGCThread)            
      offset = sprintf(dest, offset, "-collector");  // gc thread?
    if (beingDispatched)
      offset = sprintf(dest, offset, "-being_dispatched");
    if ( ! isAlive )
      offset = sprintf(dest, offset, "-not_alive");
    return offset;
  }

  /**
   *  Dump this thread's info, for debugging.  
   *  Copy the info about it into a destination char
   *  array.  We do not use any spacing or newline characters. 
   *
   *  This is identical to calling {@link #dump(char[], int)} with an
   *  <code>offset</code> of zero.
   */
  public int dump(char[] dest) {
    return dump(dest, 0);
  }

  /** Biggest buffer you would possibly need for {@link #dump(char[], int)}
   *  Modify this if you modify that method.   
   */
  public static final int MAX_DUMP_LEN =
    10 /* for thread ID  */ + 7 + 5 + 5 + 11 + 5 + 10 + 13 + 17 + 10;

  /** Pre-allocate the dump buffer, since dump() might get called inside GC. */
  private static char[] dumpBuffer = new char[MAX_DUMP_LEN];

  @SuppressWarnings({"unused", "CanBeFinal", "UnusedDeclaration"})// accessed via VM_EntryPoints
  private static int dumpBufferLock = 0;
  
  /** Reset at boot time. */
  private static Offset dumpBufferLockOffset = Offset.max();

  public static char[] grabDumpBuffer() {
    if (!dumpBufferLockOffset.isMax()) {
      while (!VM_Synchronization.testAndSet(VM_Magic.getJTOC(), 
                                            dumpBufferLockOffset, 1)) 
        ;
    }
    return dumpBuffer;
  }

  public static void releaseDumpBuffer() {
    if (!dumpBufferLockOffset.isMax()) {
      VM_Synchronization.fetchAndStore(VM_Magic.getJTOC(), 
                                       dumpBufferLockOffset, 0);  
    }
  }

  /** Called during the boot sequence, any time before we go multi-threaded.
      We do this so that we can leave the lockOffsets set to -1 until the VM 
      actually needs the locking (and is running multi-threaded).
      */
  public static void boot() {
    dumpBufferLockOffset = VM_Entrypoints.dumpBufferLockField.getOffset();
    intBufferLockOffset = VM_Entrypoints.intBufferLockField.getOffset();
  }

  /** Copy a String into a character array.
   *
   *  This function may be called during GC and may be used in conjunction
   *  with the MMTk {@link org.mmtk.utility.Log} class.   It avoids write barriers and allocation.
   *  <p>
   *  XXX This function should probably be moved to a sensible location where
   *   we can use it as a utility.   Suggestions welcome.
   *  <P>
   *
   * @param dest char array to copy into.
   * @param destOffset Offset into <code>dest</code> where we start copying
   *
   * @return 1 plus the index of the last character written.  If we were to
   *         write zero characters (which we won't) then we would return
   *         <code>offset</code>.  This is intended to represent the first
   *         unused position in the array <code>dest</code>.  However, it also
   *         serves as a pseudo-overflow check:  It may have the value
   *         <code>dest.length</code>, if the array <code>dest</code> was
   *         completely filled by the call, or it may have a value greater
   *         than <code>dest.length</code>, if the info needs more than
   *         <code>dest.length - offset</code> characters of space.
   *
   * @return  -1 if <code>offset</code> is negative.
   *
   * the MMTk {@link org.mmtk.utility.Log} class).
   */
  public static int sprintf(char[] dest, int destOffset, String s) {
    final char[] sArray = java.lang.JikesRVMSupport.getBackingCharArray(s);
    return sprintf(dest, destOffset, sArray);
  }

  public static int sprintf(char[] dest, int destOffset, char[] src) {
    return sprintf(dest, destOffset, src, 0, src.length);
  }

  /** Copies characters from <code>src</code> into the destination character
   * array <code>dest</code>.
   *
   *  The first character to be copied is at index <code>srcBegin</code>; the
   *  last character to be copied is at index <code>srcEnd-1</code>.  (This is
   *  the same convention as followed by java.lang.String#getChars). 
   *
   * @param dest char array to copy into.
   * @param destOffset Offset into <code>dest</code> where we start copying
   * @param src Char array to copy from
   * @param srcStart index of the first character of <code>src</code> to copy.
   * @param srcEnd index after the last character of <code>src</code> to copy.
   *
   */
  public static int sprintf(char[] dest, int destOffset, char[] src,
                            int srcStart, int srcEnd) 
  {
    for (int i = srcStart; i < srcEnd; ++i) {
      char nextChar = Barriers.getArrayNoBarrierStatic(src, i);
      destOffset = sprintf(dest, destOffset, nextChar);
    }
    return destOffset;
  }

  public static int sprintf(char[] dest, int destOffset, char c) {
    if (destOffset < 0)             // bounds check
      return -1;
      
    if (destOffset < dest.length)
      Barriers.setArrayNoBarrierStatic(dest, destOffset, c);
    return destOffset + 1;
  }


  /** Copy the printed decimal representation of a long into 
   * a character array.  The value is not padded and no
   * thousands seperator is copied.  If the value is negative a
   * leading minus sign (-) is copied.
   *  
   *  This function may be called during GC and may be used in conjunction
   *  with the Log class.   It avoids write barriers and allocation.
   *  <p>
   *  XXX This function should probably be moved to a sensible location where
   *   we can use it as a utility.   Suggestions welcome.
   * <p>
   *  XXX This method's implementation is stolen from the {@link org.mmtk.utility.Log} class.
   *   
   * @param dest char array to copy into.
   * @param offset Offset into <code>dest</code> where we start copying
   *
   * @return 1 plus the index of the last character written.  If we were to
   *         write zero characters (which we won't) then we would return
   *         <code>offset</code>.  This is intended to represent the first
   *         unused position in the array <code>dest</code>.  However, it also
   *         serves as a pseudo-overflow check:  It may have the value
   *         <code>dest.length</code>, if the array <code>dest</code> was
   *         completely filled by the call, or it may have a value greater
   *         than <code>dest.length</code>, if the info needs more than
   *         <code>dest.length - offset</code> characters of space.
   *
   * @return  -1 if <code>offset</code> is negative.
   *
   */
  public static int sprintf(char[] dest, int offset, long l) {
    boolean negative = l < 0;
    int nextDigit;
    char nextChar;
    int index = INT_BUFFER_SIZE - 1;
    char [] intBuffer = grabIntBuffer();
    
    nextDigit = (int)(l % 10);
    nextChar = Barriers.getArrayNoBarrierStatic(hexDigitCharacter,
                                              negative
                                              ? - nextDigit
                                              : nextDigit);
    Barriers.setArrayNoBarrierStatic(intBuffer, index--, nextChar);
    l = l / 10;
    
    while (l != 0) {
      nextDigit = (int)(l % 10);
      nextChar = Barriers.getArrayNoBarrierStatic(hexDigitCharacter,
                                                negative
                                                ? - nextDigit
                                                : nextDigit);
      Barriers.setArrayNoBarrierStatic(intBuffer, index--, nextChar);
      l = l / 10;
    }
    
    if (negative)
      Barriers.setArrayNoBarrierStatic(intBuffer, index--, '-');
    
    int newOffset = 
      sprintf(dest, offset, intBuffer, index+1, INT_BUFFER_SIZE);
    releaseIntBuffer();
    return newOffset;
  }
  
  /**
   * A map of hexadecimal digit values to their character representations.
   * <P>
   * XXX We currently only use '0' through '9'.  The rest are here pending
   * possibly merging this code with the similar code in Log.java, or breaking
   * this code out into a separate utility class.
   */
  private static final char [] hexDigitCharacter =
  { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e',
    'f' };

  /**
   * How many characters we need to have in a buffer for building string
   * representations of <code>long</code>s, such as {@link #intBuffer}.  A <code>long</code> is a signed
   * 64-bit integer in the range -2^63 to 
   * 2^63+1.  The number of digits in the decimal representation of
   * 2^63 is ceiling(log10(2^63)) == ceiling(63 * log10(2)) == 19.  An
   * extra character may be required for a minus sign (-).  So the
   * maximum number of characters is 20.
   */
  private static final int INT_BUFFER_SIZE = 20;

  /** A buffer for building string representations of <code>long</code>s */
  private static final char [] intBuffer = new char[INT_BUFFER_SIZE];

  /** A lock for {@link #intBuffer} */
  @SuppressWarnings({"unused", "CanBeFinal", "UnusedDeclaration"})// accessed via VM_EntryPoints
  private static int intBufferLock = 0;

  /** The offset of {@link #intBufferLock} in this class's TIB.
   *  This is set properly at boot time, even though it's a
   *  <code>private</code> variable. . */
  private static Offset intBufferLockOffset = Offset.max();

  /**
   * Get exclusive access to {@link #intBuffer}, the buffer for building
   * string representations of integers. 
   */
  private static char [] grabIntBuffer() {
    if (!intBufferLockOffset.isMax()) {
      while (!VM_Synchronization.testAndSet(VM_Magic.getJTOC(), 
                                            intBufferLockOffset, 1)) 
        ;
    }
    return intBuffer;
  }

  /**
   * Release {@link #intBuffer}, the buffer for building string
   * representations of integers. 
   */
  private static void releaseIntBuffer() {
    if (!intBufferLockOffset.isMax()) {
      VM_Synchronization.fetchAndStore(VM_Magic.getJTOC(), 
                                       intBufferLockOffset, 0);  
    }
  }

  /** Dump information for all threads, via {@link VM#sysWrite}.  Each
   * thread's info is newline-terminated. 
   *
   * @param verbosity Ignored.
   */
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
      // Compensate for t.dump() not newline-terminating info.
      VM.sysWriteln();
    }
  }
  
  //-----------------//
  // Instance fields //
  //-----------------//

  /** Support for suspend and resume */
  final VM_ProcessorLock suspendLock;
  boolean          suspendPending;
  boolean          suspended;
  
  /**
   * Index of this thread in {@link VM_Scheduler#threads}[].
   * This value must be non-zero because it is shifted 
   * and used in {@link Object} lock ownership tests.
   */
  private int threadSlot;

  /**
   * Proxywait/wakeup queue object.  
   */
  VM_Proxy proxy;
  
  /**
   * Has this thread been suspended via {@link java.lang.Thread#suspend()}?
   */
  protected volatile boolean isSuspended; 
 
  /**
   * Is an exception waiting to be delivered to this thread?
   * A non-null value means the next {@link #yield} should deliver the
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
   * Counts the depth of outstanding calls to {@link VM#disableGC}.  If this
   * is set, then we should also have {@link #disallowAllocationsByThisThread}
   * set.  The converse also holds.  
   */
  public int disableGCDepth = 0;

  /**
   * Execution stack for this thread.
   */ 
  /** The machine stack on which to execute this thread. */
  public byte[] stack;
  /** The {@link Address} of the guard area for {@link #stack}. */
  public Address stackLimit;
  
  /**
   * Place to save register state when this thread is not actually running.
   */ 
  public final VM_Registers contextRegisters; 
  
  /**
   * Place to save register state when C signal handler traps 
   * an exception while this thread is running.
   */ 
  public final VM_Registers hardwareExceptionRegisters;
  
  /**
   * Place to save/restore this thread's monitor state during 
   * {@link Object#wait} and {@link Object#notify}.
   */ 
  Object waitObject;
  /** Lock recursion count for this thread's monitor. */
  int    waitCount;
  
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
   * Note that: {@link java.lang.Thread#MIN_PRIORITY} <= priority 
   * <= {@link java.lang.Thread#MAX_PRIORITY}. 
   *
   * Public so that {@link java.lang.Thread} can set it.
   */
  public int priority;
   
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
   * This thread's successor on a {@link VM_ThreadQueue}.
   */ 
  public VM_Thread next;       
  
  /* These status variables are used exclusively for debugging, via the
   * {@link VM_Thread#dump} method. 
   */

  /**
   * A thread is "alive" if its start method has been called and the 
   * thread has not yet terminated execution.
   * Set by:   {@link java.lang.Thread#start()}
   * Unset by: {@link VM_Thread#terminate()}
   */ 
  protected boolean isAlive;

  /**
   * The thread created by the boot image writer, the Primordial thread, is
   * the Boot thread.   It terminates as soon as it's done.
   */
  public boolean isBootThread;


  /** The Main Thread is the one created to run 
   * <code>static main(String[] args)</code> 
   */
  public boolean isMainThread;
  
  /**
   * A thread is a "gc thread" if it's an instance of 
   * {@link org.jikesrvm.memorymanagers.mminterface.VM_CollectorThread}
   */ 
  public boolean isGCThread;

  /**
   * A thread is an "idle thread" if it's an instance of {@link VM_IdleThread}.
   */ 
  boolean isIdleThread;
  
  /**
   * The virtual machine terminates when the last non-daemon (user) 
   * thread terminates.
   */ 
  protected boolean isDaemon;
       
  /**
   * ID of processor to run this thread (cycles for load balance)
   */
  public int chosenProcessorId; 

  public VM_JNIEnvironment jniEnv;
  
  /** 
   * Value returned from {@link org.jikesrvm.runtime.VM_Time#cycles()} when this thread 
   * started running. If not currently running, then it has the value 0.
   */
  private long startCycle; 

  /**
   * Accumulated cycle count as measured by {@link org.jikesrvm.runtime.VM_Time#cycles()} 
   * used by this thread.
   */
  private long totalCycles;  

  /**
   * Accumulate the interval from {@link #startCycle} to the result
   * of calling {@link org.jikesrvm.runtime.VM_Time#cycles()} into {@link #totalCycles}
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
  public void startQuantum(long now) {
    startCycle = now;
  }

  /**
   * Called from {@link VM_Processor#dispatch} when a thread is about to stop
   * executing.
   */
  void endQuantum(long now) {
    totalCycles += now - startCycle;
    startCycle = 0;
  }

  /** @return The value of {@link #totalCycles}, converted to milliseconds */
  public double getCPUTimeMillis() {
    return VM_Time.cyclesToMillis(totalCycles);
  }

  /** @return The value of {@link #totalCycles} */
  public long getTotalCycles() {
    return totalCycles;
  }

  /** @return The value of {@link #isBootThread} */
  public boolean isBootThread() {
    return isBootThread;
  }

  /** @return The value of {@link #isMainThread} */
  public boolean isMainThread() {
    return isMainThread;
  }

  /** @return The value of {@link #isIdleThread}. */
  public boolean isIdleThread() {
    return isIdleThread;
  }

  /** @return The value of {@link #isGCThread}. */
  public boolean isGCThread() {
    return isGCThread;
  }

  /** Returns the value of {@link #isDaemon}. */
  @Interruptible
  public boolean isDaemonThread() { 
    return isDaemon;
  }

  /** Returns the value of {@link #isAlive}. */
  @Interruptible
  public boolean isAlive() { 
    return isAlive;
  }

  /** Returns the value of {@link #isSystemThread}. */
  public boolean isSystemThread() {
    return isSystemThread;
  }

  // Public since it needs to be able to be set by java.lang.Thread.
  public boolean isSystemThread = true;

  // Only used by OSR when VM.BuildForAdaptiveSystem
  // Declared as an Object to cut link to adaptive system.  Ugh.
  public Object /* OSR_OnStackReplacementEvent */ onStackReplacementEvent;
  
  ///////////////////////////////////////////////////////////
  // flags should be packaged or replaced by other solutions

  // the flag indicates whether this thread is waiting for on stack replacement
  // before being rescheduled.
  public boolean isWaitingForOsr = false;
 
  // before call new instructions, we need a bridge to recover register
  // states from the stack frame.
  public VM_CodeArray bridgeInstructions = null;
  public Offset fooFPOffset = Offset.zero();
  public Offset tsFPOffset = Offset.zero();

  // flag to synchronize with osr organizer, the trigger sets osr requests
  // the organizer clear the requests
  public boolean requesting_osr = false;
}
