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
package org.jikesrvm.scheduler.greenthreads;

import org.jikesrvm.ArchitectureSpecific;
import static org.jikesrvm.ArchitectureSpecific.VM_StackframeLayoutConstants.STACK_SIZE_NORMAL;
import org.jikesrvm.VM;
import org.jikesrvm.adaptive.OSR_Listener;
import org.jikesrvm.adaptive.measurements.VM_RuntimeMeasurements;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.runtime.VM_ArchEntrypoints;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Time;
import org.jikesrvm.scheduler.VM_Lock;
import org.jikesrvm.scheduler.VM_Processor;
import org.jikesrvm.scheduler.VM_ProcessorLock;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.VM_Synchronization;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.LogicallyUninterruptible;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * A green thread's Java execution context
 */
@Uninterruptible
public class VM_GreenThread extends VM_Thread {
  /** Lock controlling the suspending of a thread */
  private static final Offset suspendPendingOffset = VM_Entrypoints.suspendPendingField.getOffset();

  /**
   * Should this thread be suspended the next time it is considered
   * for scheduling? NB int as we CAS to modify it
   */
  private volatile int suspendPending;

  /**
   * This thread's successor on a queue.
   */
  private VM_GreenThread next;

  /**
   * ID of processor to run this thread (cycles for load balance)
   */
  public int chosenProcessorId;

  /**
   * A thread proxy. Either null or an object holding a reference to this class
   * and sitting in two queues. When one queue dequeues the object they nullify
   * the reference to this class in the thread proxy, thereby indicating to the
   * other queue the thread is no longer in their queue.
   */
  public VM_ThreadProxy threadProxy;

  /**
   * Object specifying the event the thread is waiting for.
   * E.g., set of file descriptors for an I/O wait.
   */
  VM_ThreadEventWaitData waitData;

  /**
   * Virtual processor that this thread wants to run on
   * (null --> any processor is ok).
   */
  public VM_GreenProcessor processorAffinity;


  /**
   * Create a thread with default stack and with the given name.
   */
  public VM_GreenThread(String name) {
    this(MM_Interface.newStack(STACK_SIZE_NORMAL, false),
        null, // java.lang.Thread
        name,
        true, // daemon
        true, // system
        Thread.NORM_PRIORITY);
  }

  /**
   * Create a thread with the given stack and name. Used by
   * {@link org.jikesrvm.memorymanagers.mminterface.VM_CollectorThread} and the
   * boot image writer for the boot thread.
   */
  public VM_GreenThread(byte[] stack, String name) {
    this(stack,
        null, // java.lang.Thread
        name,
        true, // daemon
        true, // system
        Thread.NORM_PRIORITY);
  }

  /**
   * Create a thread with ... called by java.lang.VMThread.create. System thread
   * isn't set.
   */
  public VM_GreenThread(Thread thread, long stacksize, String name, boolean daemon, int priority) {
    this(MM_Interface.newStack((stacksize <= 0) ? STACK_SIZE_NORMAL : (int)stacksize, false),
        thread, name, daemon, false, priority);
  }

  /**
   * Create a thread.
   */
  protected VM_GreenThread (byte[] stack, Thread thread, String name, boolean daemon, boolean system, int priority) {
    super(stack, thread, name, daemon, system, priority);
    // for load balancing
    chosenProcessorId = (VM.runningVM ? VM_Processor.getCurrentProcessorId() : 0);
  }

  /*
   * Queue support
   */

  /**
   * Get the next element after this thread in a thread queue
   */
  public VM_GreenThread getNext() {
    return next;
  }
  /**
   * Set the next element after this thread in a thread queue
   */
  public void setNext(VM_GreenThread next) {
    this.next = next;
  }

  /**
   * Update internal state of Thread and Scheduler to indicate that
   * a thread is about to start
   */
  @Override
  protected void registerThreadInternal() {
    VM_GreenScheduler.registerThread(this);
  }

  /**
   * Start execution of 'this' by putting it on the given queue.
   * Precondition: If the queue is global, caller must have the appropriate mutex.
   * @param q the VM_ThreadQueue on which to enqueue this thread.
   */
  public final void start(VM_GreenThreadQueue q) {
    registerThread();
    q.enqueue(this);
  }

  /*
   * block and unblock
   */
  /**
   * Thread is blocked on a heavyweight lock
   * @see VM_Lock#lockHeavy(Object)
   */
  public void block(VM_ThreadQueue entering, VM_ProcessorLock mutex) {
    yield(entering, mutex);
  }

  /**
   * Unblock thread from heavyweight lock blocking
   * @see VM_Lock#unlockHeavy(Object)
   */
  public void unblock() {
    schedule();
  }

  // NOTE: The ThreadSwitchSampling code depends on there
  // being the same number of wrapper routines for all
  // compilers. Please talk to me (Dave G) before changing this. Thanks.
  // We could try a substantially more complex implementation
  // (especially on the opt side) to avoid the wrapper routine,
  // for the baseline compiler, but I think this is the easiest way
  // to handle all the cases at reasonable runtime-cost.

  /**
   * Process a taken yieldpoint.
   * May result in threadswitch, depending on state of various control
   * flags on the processor object.
   */
  @NoInline
  public static void yieldpoint(int whereFrom) {
    boolean threadSwitch = false;
    VM_GreenProcessor p = VM_GreenProcessor.getCurrentProcessor();
    int takeYieldpointVal = p.takeYieldpoint;
    p.takeYieldpoint = 0;

    // Process request for code-patch memory sync operation
    if (VM.BuildForPowerPC && p.codePatchSyncRequested) {
      p.codePatchSyncRequested = false;
      // TODO: Is this sufficient? Ask Steve why we don't need to sync icache/dcache. --dave
      // make sure not get stale data
      VM_Magic.isync();
      VM_Synchronization.fetchAndDecrement(VM_Magic.getJTOC(), VM_ArchEntrypoints.toSyncProcessorsField.getOffset(), 1);
    }

    // If thread is in critical section we can't switch right now, defer until later
    if (!p.threadSwitchingEnabled()) {
      if (p.threadSwitchPending != 1) {
        p.threadSwitchPending = takeYieldpointVal;
      }
      return;
    }

    // Process timer interrupt event
    if (p.timeSliceExpired != 0) {
      p.timeSliceExpired = 0;

      if (VM.CBSCallSamplesPerTick > 0) {
        p.yieldForCBSCall = true;
        p.takeYieldpoint = -1;
        p.firstCBSCallSample++;
        p.firstCBSCallSample = p.firstCBSCallSample % VM.CBSCallSampleStride;
        p.countdownCBSCall = p.firstCBSCallSample;
        p.numCBSCallSamples = VM.CBSCallSamplesPerTick;
      }

      if (VM.CBSMethodSamplesPerTick > 0) {
        p.yieldForCBSMethod = true;
        p.takeYieldpoint = -1;
        p.firstCBSMethodSample++;
        p.firstCBSMethodSample = p.firstCBSMethodSample % VM.CBSMethodSampleStride;
        p.countdownCBSMethod = p.firstCBSMethodSample;
        p.numCBSMethodSamples = VM.CBSMethodSamplesPerTick;
      }

      if (++p.interruptQuantumCounter >= VM.schedulingMultiplier) {
        threadSwitch = true;
        p.interruptQuantumCounter = 0;

        // Check various scheduling requests/queues that need to be polled periodically
        if (VM_Scheduler.debugRequested && VM_GreenScheduler.allProcessorsInitialized) {
          // service "debug request" generated by external signal
          VM_GreenScheduler.debuggerMutex.lock("looking at debugger queue");
          if (VM_GreenScheduler.debuggerQueue.isEmpty()) {
            // debugger already running
            VM_GreenScheduler.debuggerMutex.unlock();
          } else { // awaken debugger
            VM_GreenThread t = VM_GreenScheduler.debuggerQueue.dequeue();
            VM_GreenScheduler.debuggerMutex.unlock();
            t.schedule();
          }
        }
        if (VM_GreenScheduler.wakeupQueue.isReady()) {
          VM_GreenScheduler.wakeupMutex.lock("looking at wakeup queue");
          VM_GreenThread t = VM_GreenScheduler.wakeupQueue.dequeue();
          VM_GreenScheduler.wakeupMutex.unlock();
          if (t != null) {
            t.schedule();
          }
        }
      }

      if (VM.BuildForAdaptiveSystem) {
        VM_RuntimeMeasurements.takeTimerSample(whereFrom);
      }

      if (threadSwitch && (p.yieldForCBSMethod || p.yieldForCBSCall)) {
        // want to sample the current thread, not the next one to be scheduled
        // So, defer actual threadswitch until we take all of our samples
        p.threadSwitchWhenCBSComplete = true;
        threadSwitch = false;
      }

      if (VM.BuildForAdaptiveSystem) {
        threadSwitch |= OSR_Listener.checkForOSRPromotion(whereFrom);
      }
      if (threadSwitch) {
        p.yieldForCBSMethod = false;
        p.yieldForCBSCall = false;
        p.threadSwitchWhenCBSComplete = false;
      }
    }

    if (p.yieldForCBSCall) {
      if (!(whereFrom == BACKEDGE || whereFrom == OSROPT)) {
        if (--p.countdownCBSCall <= 0) {
          if (VM.BuildForAdaptiveSystem) {
            // take CBS sample
            VM_RuntimeMeasurements.takeCBSCallSample(whereFrom);
          }
          p.countdownCBSCall = VM.CBSCallSampleStride;
          p.numCBSCallSamples--;
          if (p.numCBSCallSamples <= 0) {
            p.yieldForCBSCall = false;
            if (!p.yieldForCBSMethod) {
              p.threadSwitchWhenCBSComplete = false;
              threadSwitch = true;
            }
          }
        }
      }
      if (p.yieldForCBSCall) {
        p.takeYieldpoint = -1;
      }
    }

    if (p.yieldForCBSMethod) {
      if (--p.countdownCBSMethod <= 0) {
        if (VM.BuildForAdaptiveSystem) {
          // take CBS sample
          VM_RuntimeMeasurements.takeCBSMethodSample(whereFrom);
        }
        p.countdownCBSMethod = VM.CBSMethodSampleStride;
        p.numCBSMethodSamples--;
        if (p.numCBSMethodSamples <= 0) {
          p.yieldForCBSMethod = false;
          if (!p.yieldForCBSCall) {
            p.threadSwitchWhenCBSComplete = false;
            threadSwitch = true;
          }
        }
      }
      if (p.yieldForCBSMethod) {
        p.takeYieldpoint = 1;
      }
    }

    // Process request to initiate GC by forcing a thread switch.
    if (p.yieldToGCRequested) {
      p.yieldToGCRequested = false;
      p.yieldForCBSCall = false;
      p.yieldForCBSMethod = false;
      p.threadSwitchWhenCBSComplete = false;
      p.takeYieldpoint = 0;
      threadSwitch = true;
    }

    if (VM.BuildForAdaptiveSystem && p.yieldToOSRRequested) {
      p.yieldToOSRRequested = false;
      OSR_Listener.handleOSRFromOpt();
      threadSwitch = true;
    }

    if (threadSwitch) {
      timerTickYield(whereFrom);
    }

    VM_GreenThread myThread = VM_GreenScheduler.getCurrentThread();
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
  public static void timerTickYield(int whereFrom) {
    VM_GreenThread myThread = VM_GreenScheduler.getCurrentThread();
    // thread switch
    myThread.beingDispatched = true;
    if (trace) VM_Scheduler.trace("VM_GreenThread", "timerTickYield() scheduleThread ", myThread.getIndex());
    VM_GreenProcessor.getCurrentProcessor().scheduleThread(myThread);
    morph(true);
  }

  /**
   * Suspend execution of current thread, in favor of some other thread.
   */
  @NoInline
  public static void yield() {
    VM_GreenThread myThread = VM_GreenScheduler.getCurrentThread();
    myThread.beingDispatched = true;
    VM_GreenProcessor.getCurrentProcessor().readyQueue.enqueue(myThread);
    morph(false);
  }

  /**
   * Suspend execution of current thread in favor of some other thread.
   * @param q queue to put thread onto
   * @param l lock guarding that queue (currently locked)
   */
  @NoInline
  public void yield(VM_AbstractThreadQueue q, VM_ProcessorLock l) {
    if (VM.VerifyAssertions) VM._assert(this == VM_GreenScheduler.getCurrentThread());
    if (state == State.RUNNABLE)
      changeThreadState(State.RUNNABLE, State.BLOCKED);
    beingDispatched = true;
    q.enqueue(this);
    l.unlock();
    morph(false);
  }

  /**
   * For timed wait, suspend execution of current thread in favor of some other thread.
   * Put a proxy for the current thread
   *   on a queue waiting a notify, and
   *   on a wakeup queue waiting for a timeout.
   *
   * @param q1 the {@link VM_ThreadProxyWaitingQueue} upon which to wait for notification
   * @param l1 the {@link VM_ProcessorLock} guarding <code>q1</code> (currently locked)
   * @param q2 the {@link VM_ThreadProxyWakeupQueue} upon which to wait for timeout
   * @param l2 the {@link VM_ProcessorLock} guarding <code>q2</code> (currently locked)
   */
  @NoInline
  private static void yield(VM_ThreadProxyWaitingQueue q1, VM_ProcessorLock l1,
      VM_ThreadProxyWakeupQueue q2, VM_ProcessorLock l2) {
    VM_GreenThread myThread = VM_GreenScheduler.getCurrentThread();
    myThread.beingDispatched = true;
    q1.enqueue(myThread.threadProxy); // proxy has been cached before locks were obtained
    q2.enqueue(myThread.threadProxy); // proxy has been cached before locks were obtained
    l1.unlock();
    l2.unlock();
    morph(false);
  }

  static void morph() {
    morph(false);
  }

  /**
   * Current thread has been placed onto some queue. Become another thread.
   * @param timerTick   timer interrupted if true
   */
  @LogicallyUninterruptible
  static void morph(boolean timerTick) {
    VM_Magic.sync();  // to ensure beingDispatched flag written out to memory
    if (trace) VM_Scheduler.trace("VM_GreenThread", "morph ");
    VM_GreenThread myThread = VM_GreenScheduler.getCurrentThread();
    if (VM.VerifyAssertions) {
      VM_GreenProcessor.getCurrentProcessor().failIfThreadSwitchingDisabled();
      VM._assert(myThread.beingDispatched, "morph: not beingDispatched");
    }
    // become another thread
    //
    VM_GreenProcessor.getCurrentProcessor().dispatch(timerTick);
    // respond to interrupt sent to this thread by some other thread
    // NB this can create a stack trace, so is interruptible
    if (myThread.throwInterruptWhenScheduled) {
      myThread.postExternalInterrupt();
    }
  }

  /**
   * Suspend execution of current thread in favor of some other thread.
   * @param q queue to put thread onto (must be processor-local, ie.
   * not guarded with a lock)
   */
  @NoInline
  public static void yield(VM_AbstractThreadQueue q) {
    VM_GreenThread myThread = VM_GreenScheduler.getCurrentThread();
    myThread.beingDispatched = true;
    q.enqueue(myThread);
    morph(false);
  }

  /**
   * Thread model dependant sleep
   * @param millis
   * @param ns
   */
  @Interruptible
  @Override
  protected void sleepInternal(long millis, int ns) throws InterruptedException {
    wakeupCycle = VM_Time.cycles() + VM_Time.millisToCycles(millis);
    // cache the proxy before obtaining lock
    VM_ThreadProxy proxy = new VM_ThreadProxy(this, wakeupCycle);
    if(sleepImpl(proxy)) {
      throw new InterruptedException("sleep interrupted");
    }
  }

  /**
   * Uninterruptible portion of going to sleep
   * @return were we interrupted prior to going to sleep
   */
  private boolean sleepImpl(VM_ThreadProxy proxy) {
    if (isInterrupted()) {
      // we were interrupted before putting this thread to sleep
      return true;
    }
    VM_GreenScheduler.wakeupMutex.lock("wakeup mutex for sleep");
    this.threadProxy = proxy;
    yield(VM_GreenScheduler.wakeupQueue, VM_GreenScheduler.wakeupMutex);
    return false;
  }

  /**
   * Support for Java {@link java.lang.Object#wait()} synchronization primitive.
   *
   * @param o the object synchronized on
   */
  @Override
  @Interruptible
  protected Throwable waitInternal(Object o) {
    return waitInternal2(o, false, 0L);
  }
  /**
   * Support for Java {@link java.lang.Object#wait()} synchronization primitive.
   *
   * @param o the object synchronized on
   * @param millis the number of milliseconds to wait for notification
   */
  @Override
  @Interruptible
  protected Throwable waitInternal(Object o, long millis) {
    return waitInternal2(o, true, millis);
  }
  /**
   * Combine the two outer waitInternal into one bigger one
   * @param o the object to wait upon
   * @param hasTimeout have a timeout ?
   * @param millis timeout value
   * @return any exceptions created along the way
   */
  @Interruptible
  private Throwable waitInternal2(Object o, boolean hasTimeout, long millis) {
    // Check early otherwise we'll fail an assert when creating the heavy lock
    if (!VM_ObjectModel.holdsLock(o, VM_Scheduler.getCurrentThread())) {
      return new IllegalMonitorStateException("waiting on " + o);
    }
    // get lock for object
    VM_GreenLock l = (VM_GreenLock)VM_ObjectModel.getHeavyLock(o, true);
    // this thread is supposed to own the lock on o
    if (l.getOwnerId() != getLockingId()) {
      return new IllegalMonitorStateException("waiting on " + o);
    }
    // Get proxy and set wakeup time
    VM_ThreadProxy proxy;
    if (!hasTimeout) {
      proxy = new VM_ThreadProxy(this);
    } else {
      wakeupCycle = VM_Time.cycles() + VM_Time.millisToCycles(millis);
      proxy = new VM_ThreadProxy(this, wakeupCycle);
    }
    // carry on to uninterruptible portion
    Throwable t = waitImpl(o, l, hasTimeout, millis, proxy);
    if (t == proxyInterruptException) {
      // Create a proper stack trace
      t = new InterruptedException("wait interrupted");
    }
    return t;
  }
  /**
   * Uninterruptible portion of waiting
   */
  private Throwable waitImpl(Object o, VM_GreenLock l, boolean hasTimeout, long millis, VM_ThreadProxy proxy) {
    // Check thread isn't already in interrupted state
    if (isInterrupted()) {
      // it is so throw either thread death (from stop) or interrupted exception
      if (VM.VerifyAssertions && (state != State.JOINING))
        changeThreadState(State.RUNNABLE, State.RUNNABLE);
      clearInterrupted();
      if(causeOfThreadDeath == null) {
        return proxyInterruptException;
      } else {
        return causeOfThreadDeath;
      }
    } else {
      // non-interrupted wait
      Throwable rethrow = null;
      if (state != State.JOINING) {
        if (hasTimeout) {
          changeThreadState(State.RUNNABLE, State.TIMED_WAITING);
        } else {
          changeThreadState(State.RUNNABLE, State.WAITING);
        }
      }
      // allow an entering thread a chance to get the lock
      l.mutex.lock("performing Object.wait"); // until unlock(), thread-switching fatal
      VM_Thread n = l.entering.dequeue();
      if (n != null) n.schedule();
      if (hasTimeout) {
        VM_GreenScheduler.wakeupMutex.lock("performing timed Object.wait");
      }
      // squirrel away lock state in current thread
      waitObject = l.getLockedObject();
      waitCount = l.getRecursionCount();
      // cache the proxy before obtaining lock
      threadProxy = proxy;
      // release l and simultaneously put t on l's waiting queue
      l.setOwnerId(0);
      if (!hasTimeout) {
        try {
          yield(l.waiting, l.mutex); // thread-switching benign
        } catch (Throwable thr) {
          rethrow = thr; // An InterruptedException. We'll rethrow it after regaining the lock on o.
        }
      } else {
        try {
          yield(l.waiting,
              l.mutex,
              VM_GreenScheduler.wakeupQueue,
              VM_GreenScheduler.wakeupMutex); // thread-switching benign
        } catch (Throwable thr) {
          rethrow = thr;
        }
      }
      if (state != State.JOINING && rethrow == null) {
        if (hasTimeout) {
          changeThreadState(State.TIMED_WAITING, State.RUNNABLE);
        } else {
          changeThreadState(State.WAITING, State.RUNNABLE);
        }
      }
      // regain lock
      VM_ObjectModel.genericLock(o);
      waitObject = null;
      if (waitCount != 1) { // reset recursion count
        VM_Lock l2 = VM_ObjectModel.getHeavyLock(o, true);
        l2.setRecursionCount(waitCount);
      }
      return rethrow;
    }
  }

  /**
   * Support for Java {@link java.lang.Object#notify()} synchronization primitive.
   *
   * @param o the object synchronized on
   * @param l the heavy weight lock
   */
  @Override
  protected void notifyInternal(Object o, VM_Lock lock) {
    VM_GreenLock l = (VM_GreenLock)lock;
    l.mutex.lock("notify mutex"); // until unlock(), thread-switching fatal
    VM_GreenThread t = l.waiting.dequeue();

    if (t != null) {
      l.entering.enqueue(t);
    }
    l.mutex.unlock(); // thread-switching benign
  }

  /**
   * Support for Java {@link java.lang.Object#notify()} synchronization primitive.
   *
   * @param o the object synchronized on
   * @param l the heavy weight lock
   */
  @Override
  protected void notifyAllInternal(Object o, VM_Lock lock) {
    VM_GreenLock l = (VM_GreenLock)lock;
    l.mutex.lock("notifyAll mutex"); // until unlock(), thread-switching fatal
    VM_GreenThread t = l.waiting.dequeue();
    while (t != null) {
      l.entering.enqueue(t);
      t = l.waiting.dequeue();
    }
    l.mutex.unlock(); // thread-switching benign
  }

  /**
   * Put given thread onto the IO wait queue.
   * @param waitData the wait data specifying the file descriptor(s)
   * to wait for.
   */
  public static void ioWaitImpl(VM_ThreadIOWaitData waitData) {
    VM_GreenThread myThread = VM_GreenScheduler.getCurrentThread();
    myThread.waitData = waitData;
    myThread.changeThreadState(State.RUNNABLE, State.IO_WAITING);
    yield(VM_GreenProcessor.getCurrentProcessor().ioQueue);
    myThread.changeThreadState(State.IO_WAITING, State.RUNNABLE);
  }

  /**
   * Put given thread onto the process wait queue.
   * @param waitData the wait data specifying which process to wait for
   * @param process the <code>VM_Process</code> object associated
   *    with the process
   */
  public static void processWaitImpl(VM_ThreadProcessWaitData waitData, VM_Process process) {
    VM_GreenThread myThread = VM_GreenScheduler.getCurrentThread();
    myThread.waitData = waitData;
    myThread.changeThreadState(State.RUNNABLE, State.PROCESS_WAITING);

    // Note that we have to perform the wait on the pthread
    // that created the process, which may involve switching
    // to a different VM_Processor.

    VM_GreenProcessor creatingProcessor = process.getCreatingProcessor();
    VM_ProcessorLock queueLock = creatingProcessor.processWaitQueueLock;
    queueLock.lock("wait for process");

    // This will throw InterruptedException if the thread
    // is interrupted while on the queue.
    myThread.yield(creatingProcessor.processWaitQueue, queueLock);
    myThread.changeThreadState(State.PROCESS_WAITING, State.RUNNABLE);
  }

  /**
   * Thread model dependent part of stopping/interrupting a thread
   */
  @Override
  protected void killInternal() {
    // remove this thread from wakeup and/or waiting queue
    VM_ThreadProxy p = threadProxy;
    if (p != null) {
      // If the thread has a proxy, then (presumably) it is either
      // doing a sleep() or a wait(), both of which are interruptible,
      // so let morph() know that it should throw the
      // external interrupt object.
      this.throwInterruptWhenScheduled = true;

      VM_GreenThread t = p.unproxy(); // t == this or t == null
      if (t != null) {
        t.schedule();
      }
    }
    // TODO!! handle this thread executing native code
  }

  /**
   * Thread model dependent part of thread suspension
   */
  @Override
  protected void suspendInternal() {
    VM_Synchronization.tryCompareAndSwap(this, suspendPendingOffset, 0, 1);
    if (this == VM_GreenScheduler.getCurrentThread()) yield();
  }
  /**
   * Thread model dependent part of thread resumption
   */
  @Override
  protected void resumeInternal() {
    VM_Synchronization.tryCompareAndSwap(this, suspendPendingOffset, 1, 0);
    VM_GreenProcessor.getCurrentProcessor().scheduleThread(this);
  }

  /**
   * Suspend thread if a suspend is pending. Called by processor dispatch loop.
   * @return whether the thread had a suspend pending
   */
  final boolean suspendIfPending() {
    if (suspendPending == 1) {
      if(VM_Synchronization.tryCompareAndSwap(this, suspendPendingOffset, 1, 0)) {
        // we turned the suspendPending flag off
        return true;
      } else {
        // swap failed, so it must have been resumed prior to being suspended
        return false;
      }
    } else {
      return false;
    }
  }
  /**
   * Put this thread on ready queue for subsequent execution on a future
   * timeslice.
   * Assumption: VM_Thread.contextRegisters are ready to pick up execution
   *             ie. return to a yield or begin thread startup code
   */
  @Override
  public final void schedule() {
    if (trace) VM_Scheduler.trace("VM_GreenThread", "schedule", getIndex());
    if (state == State.BLOCKED)
      changeThreadState(VM_Thread.State.BLOCKED, State.RUNNABLE);
    VM_GreenProcessor.getCurrentProcessor().scheduleThread(this);
  }

  /**
   * Give a string of information on how a thread is set to be scheduled
   */
  @Override
  @Interruptible
  public String getThreadState() {
    return VM_GreenScheduler.getThreadState(this);
  }

  /**
   * Is this thread suitable for putting on a queue?
   * @return whether the thread is terminated
   */
  final boolean isQueueable() {
    return state != State.TERMINATED;
  }
}

