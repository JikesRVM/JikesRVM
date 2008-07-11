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

import org.jikesrvm.ArchitectureSpecificOpt;
import static org.jikesrvm.ArchitectureSpecific.StackframeLayoutConstants.STACK_SIZE_NORMAL;
import org.jikesrvm.VM;
import org.jikesrvm.adaptive.OSRListener;
import org.jikesrvm.adaptive.measurements.RuntimeMeasurements;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Time;
import org.jikesrvm.scheduler.Lock;
import org.jikesrvm.scheduler.Processor;
import org.jikesrvm.scheduler.ProcessorLock;
import org.jikesrvm.scheduler.Scheduler;
import org.jikesrvm.scheduler.Synchronization;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.LogicallyUninterruptible;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * A green thread's Java execution context
 */
@Uninterruptible
@NonMoving
public class GreenThread extends RVMThread {
  /** Offset of the lock field controlling the suspending of a thread */
  private static final Offset suspendPendingOffset = Entrypoints.suspendPendingField.getOffset();

  /** Lock used for handling parking and unparking for OSR.  This lock is
   * global, since OSR is serialized anyway. */
  private static final ProcessorLock osrParkLock = new ProcessorLock();

  /**
   * Parking permit for OSR.
   */
  private boolean osrParkingPermit;

  /**
   * Should this thread be suspended the next time it is considered
   * for scheduling? NB int as we CAS to modify it
   */
  private volatile int suspendPending;

  /**
   * This thread's successor on a queue.
   */
  private GreenThread next;

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
  public ThreadProxy threadProxy;

  /**
   * Object specifying the event the thread is waiting for.
   * E.g., set of file descriptors for an I/O wait.
   */
  ThreadEventWaitData waitData;

  /**
   * Virtual processor that this thread wants to run on
   * (null --> any processor is ok).
   */
  public GreenProcessor processorAffinity;


  /**
   * Create a thread with default stack and with the given name.
   */
  public GreenThread(String name) {
    this(MM_Interface.newStack(STACK_SIZE_NORMAL, false),
        null, // java.lang.Thread
        name,
        true, // daemon
        true, // system
        Thread.NORM_PRIORITY);
  }

  /**
   * Create a thread with the given stack and name. Used by
   * {@link org.jikesrvm.memorymanagers.mminterface.CollectorThread} and the
   * boot image writer for the boot thread.
   */
  public GreenThread(byte[] stack, String name) {
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
  public GreenThread(Thread thread, long stacksize, String name, boolean daemon, int priority) {
    this(MM_Interface.newStack((stacksize <= 0) ? STACK_SIZE_NORMAL : (int)stacksize, false),
        thread, name, daemon, false, priority);
  }

  /**
   * Create a thread.
   */
  protected GreenThread(byte[] stack, Thread thread, String name, boolean daemon, boolean system, int priority) {
    super(stack, thread, name, daemon, system, priority);
    // for load balancing
    chosenProcessorId = (VM.runningVM ? Processor.getCurrentProcessorId() : 0);
  }

  /*
   * Queue support
   */

  /**
   * Get the next element after this thread in a thread queue
   */
  public final GreenThread getNext() {
    return next;
  }
  /**
   * Set the next element after this thread in a thread queue
   */
  public final void setNext(GreenThread next) {
    this.next = next;
  }

  /**
   * Update internal state of Thread and Scheduler to indicate that
   * a thread is about to start
   */
  @Override
  protected final void registerThreadInternal() {
    GreenScheduler.registerThread(this);
  }

  /**
   * Start execution of 'this' by putting it on the given queue.
   * Precondition: If the queue is global, caller must have the appropriate mutex.
   * @param q the ThreadQueue on which to enqueue this thread.
   */
  public final void start(GreenThreadQueue q) {
    registerThread();
    q.enqueue(this);
  }

  /*
   * block and unblock
   */
  /**
   * Thread is blocked on a heavyweight lock
   * @see Lock#lockHeavy(Object)
   */
  public final void block(ThreadQueue entering, ProcessorLock mutex) {
    yield(entering, mutex);
  }

  /**
   * Unblock thread from heavyweight lock blocking
   * @see Lock#unlockHeavy(Object)
   */
  public final void unblock() {
    schedule();
  }

  /**
   * Process a taken yieldpoint.
   * May result in threadswitch, depending on state of various control
   * flags on the processor object.
   */
  public static void yieldpoint(int whereFrom, Address yieldpointServiceMethodFP) {
    boolean threadSwitch = false;
    boolean cbsOverrun = false;
    GreenProcessor p = GreenProcessor.getCurrentProcessor();
    int takeYieldpointVal = p.takeYieldpoint;
    p.takeYieldpoint = 0;

    // Process request for code-patch memory sync operation
    if (VM.BuildForPowerPC && p.codePatchSyncRequested) {
      p.codePatchSyncRequested = false;
      // make sure not get stale data
      Magic.isync();
      Synchronization.fetchAndDecrement(Magic.getJTOC(), ArchEntrypoints.toSyncProcessorsField.getOffset(), 1);
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

      if (p.yieldForCBSCall || p.yieldForCBSMethod) {
        /*
         * CBS Sampling is still active from previous quantum.
         * Note that fact, but leave all the other CBS parameters alone.
         */
        cbsOverrun = true;
      } else {
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
      }

      if (++p.interruptQuantumCounter >= VM.schedulingMultiplier) {
        threadSwitch = true;
        p.interruptQuantumCounter = 0;

        // Check various scheduling requests/queues that need to be polled periodically
        if (Scheduler.debugRequested && GreenScheduler.allProcessorsInitialized) {
          // service "debug request" generated by external signal
          GreenScheduler.debuggerMutex.lock("looking at debugger queue");
          if (GreenScheduler.debuggerQueue.isEmpty()) {
            // debugger already running
            GreenScheduler.debuggerMutex.unlock();
          } else { // awaken debugger
            GreenThread t = GreenScheduler.debuggerQueue.dequeue();
            GreenScheduler.debuggerMutex.unlock();
            t.schedule();
          }
        }
        if (GreenScheduler.wakeupQueue.isReady()) {
          GreenScheduler.wakeupMutex.lock("looking at wakeup queue");
          GreenThread t = GreenScheduler.wakeupQueue.dequeue();
          GreenScheduler.wakeupMutex.unlock();
          if (t != null) {
            t.schedule();
          }
        }
      }

      if (VM.BuildForAdaptiveSystem) {
        RuntimeMeasurements.takeTimerSample(whereFrom, yieldpointServiceMethodFP);
      }

      if (threadSwitch && !cbsOverrun && (p.yieldForCBSMethod || p.yieldForCBSCall)) {
        // want to sample the current thread, not the next one to be scheduled
        // So, defer actual threadswitch until we take all of our samples
        p.threadSwitchWhenCBSComplete = true;
        threadSwitch = false;
      }

      if (VM.BuildForAdaptiveSystem) {
        threadSwitch |= OSRListener.checkForOSRPromotion(whereFrom, yieldpointServiceMethodFP);
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
            RuntimeMeasurements.takeCBSCallSample(whereFrom, yieldpointServiceMethodFP);
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
          RuntimeMeasurements.takeCBSMethodSample(whereFrom, yieldpointServiceMethodFP);
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
      OSRListener.handleOSRFromOpt(yieldpointServiceMethodFP);
      threadSwitch = true;
    }

    if (threadSwitch) {
      timerTickYield(whereFrom);
    }

    GreenThread myThread = GreenScheduler.getCurrentThread();
    if (VM.BuildForAdaptiveSystem && myThread.isWaitingForOsr) {
      ArchitectureSpecificOpt.PostThreadSwitch.postProcess(myThread);
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
    GreenThread myThread = GreenScheduler.getCurrentThread();
    // thread switch
    myThread.beingDispatched = true;
    if (trace) Scheduler.trace("GreenThread", "timerTickYield() scheduleThread ", myThread.getIndex());
    GreenProcessor.getCurrentProcessor().scheduleThread(myThread);
    morph(true);
  }

  /**
   * Suspend execution of current thread, in favor of some other thread.
   */
  @NoInline
  public static void yield() {
    GreenThread myThread = GreenScheduler.getCurrentThread();
    myThread.beingDispatched = true;
    GreenProcessor.getCurrentProcessor().readyQueue.enqueue(myThread);
    morph(false);
  }

  /**
   * Suspend execution of current thread in favor of some other thread.
   * @param q queue to put thread onto
   * @param l lock guarding that queue (currently locked)
   */
  @NoInline
  public final void yield(AbstractThreadQueue q, ProcessorLock l) {
    if (VM.VerifyAssertions) VM._assert(this == GreenScheduler.getCurrentThread());
    if (state == State.RUNNABLE)
      changeThreadState(State.RUNNABLE, State.BLOCKED);
    beingDispatched = true;
    q.enqueue(this);
    l.unlock();
    morph(false);
  }

  /**
   * Suspend execution of current thread, change its state, and release
   * a lock.
   * @param l lock guarding the decision to suspend
   * @param newState state to change to
   */
  @NoInline
  public final void yield(ProcessorLock l, State newState) {
    changeThreadState(State.RUNNABLE, newState);
    beingDispatched = true;
    l.unlock();
    morph(false);
  }

  /**
   * For timed wait, suspend execution of current thread in favor of some other thread.
   * Put a proxy for the current thread
   *   on a queue waiting a notify, and
   *   on a wakeup queue waiting for a timeout.
   *
   * @param q1 the {@link ThreadProxyWaitingQueue} upon which to wait for notification
   * @param l1 the {@link ProcessorLock} guarding <code>q1</code> (currently locked)
   * @param q2 the {@link ThreadProxyWakeupQueue} upon which to wait for timeout
   * @param l2 the {@link ProcessorLock} guarding <code>q2</code> (currently locked)
   */
  @NoInline
  private static void yield(ThreadProxyWaitingQueue q1, ProcessorLock l1,
      ThreadProxyWakeupQueue q2, ProcessorLock l2) {
    GreenThread myThread = GreenScheduler.getCurrentThread();
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
    Magic.sync();  // to ensure beingDispatched flag written out to memory
    if (trace) Scheduler.trace("GreenThread", "morph ");
    GreenThread myThread = GreenScheduler.getCurrentThread();
    if (VM.VerifyAssertions) {
      GreenProcessor.getCurrentProcessor().failIfThreadSwitchingDisabled();
      VM._assert(myThread.beingDispatched, "morph: not beingDispatched");
    }
    // become another thread
    //
    GreenProcessor.getCurrentProcessor().dispatch(timerTick);
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
  public static void yield(AbstractThreadQueue q) {
    GreenThread myThread = GreenScheduler.getCurrentThread();
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
  protected final void sleepInternal(long millis, int ns) throws InterruptedException {
    wakeupNanoTime = Time.nanoTime() + (millis * (long)1e6) + ns;
    // cache the proxy before obtaining lock
    ThreadProxy proxy = new ThreadProxy(this, wakeupNanoTime);
    if(sleepImpl(proxy)) {
      throw new InterruptedException("sleep interrupted");
    }
  }

  /**
   * Uninterruptible portion of going to sleep
   * @return were we interrupted prior to going to sleep
   */
  private boolean sleepImpl(ThreadProxy proxy) {
    if (isInterrupted()) {
      // we were interrupted before putting this thread to sleep
      return true;
    }
    GreenScheduler.wakeupMutex.lock("wakeup mutex for sleep");
    this.threadProxy = proxy;
    yield(GreenScheduler.wakeupQueue, GreenScheduler.wakeupMutex);
    return false;
  }

  /**
   * Support for Java {@link java.lang.Object#wait()} synchronization primitive.
   *
   * @param o the object synchronized on
   */
  @Override
  @Interruptible
  protected final Throwable waitInternal(Object o) {
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
  protected final Throwable waitInternal(Object o, long millis) {
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
    if (!ObjectModel.holdsLock(o, Scheduler.getCurrentThread())) {
      return new IllegalMonitorStateException("waiting on " + o);
    }
    // get lock for object
    GreenLock l = (GreenLock)ObjectModel.getHeavyLock(o, true);
    // this thread is supposed to own the lock on o
    if (l.getOwnerId() != getLockingId()) {
      return new IllegalMonitorStateException("waiting on " + o);
    }
    // Get proxy and set wakeup time
    ThreadProxy proxy;
    if (!hasTimeout) {
      proxy = new ThreadProxy(this);
    } else {
      wakeupNanoTime = Time.nanoTime() + millis * (long)1e6;
      proxy = new ThreadProxy(this, wakeupNanoTime);
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
  private Throwable waitImpl(Object o, GreenLock l, boolean hasTimeout, long millis, ThreadProxy proxy) {
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
      RVMThread n = l.entering.dequeue();
      if (n != null) n.schedule();
      if (hasTimeout) {
        GreenScheduler.wakeupMutex.lock("performing timed Object.wait");
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
              GreenScheduler.wakeupQueue,
              GreenScheduler.wakeupMutex); // thread-switching benign
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
      ObjectModel.genericLock(o);
      waitObject = null;
      if (waitCount != 1) { // reset recursion count
        Lock l2 = ObjectModel.getHeavyLock(o, true);
        l2.setRecursionCount(waitCount);
      }
      return rethrow;
    }
  }

  /**
   * Support for Java {@link java.lang.Object#notify()} synchronization primitive.
   *
   * @param o the object synchronized on
   * @param lock the heavy weight lock
   */
  @Override
  protected final void notifyInternal(Object o, Lock lock) {
    GreenLock l = (GreenLock)lock;
    l.mutex.lock("notify mutex"); // until unlock(), thread-switching fatal
    GreenThread t = l.waiting.dequeue();

    if (t != null) {
      l.entering.enqueue(t);
    }
    l.mutex.unlock(); // thread-switching benign
  }

  /**
   * Support for Java {@link java.lang.Object#notify()} synchronization primitive.
   *
   * @param o the object synchronized on
   * @param lock the heavy weight lock
   */
  @Override
  protected final void notifyAllInternal(Object o, Lock lock) {
    GreenLock l = (GreenLock)lock;
    l.mutex.lock("notifyAll mutex"); // until unlock(), thread-switching fatal
    GreenThread t = l.waiting.dequeue();
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
  public static void ioWaitImpl(ThreadIOWaitData waitData) {
    GreenThread myThread = GreenScheduler.getCurrentThread();
    myThread.waitData = waitData;
    myThread.changeThreadState(State.RUNNABLE, State.IO_WAITING);
    yield(GreenProcessor.getCurrentProcessor().ioQueue);
    myThread.changeThreadState(State.IO_WAITING, State.RUNNABLE);
  }

  /**
   * Put given thread onto the process wait queue.
   * @param waitData the wait data specifying which process to wait for
   * @param process the <code>Process</code> object associated
   *    with the process
   */
  public static void processWaitImpl(ThreadProcessWaitData waitData, VMProcess process) {
    GreenThread myThread = GreenScheduler.getCurrentThread();
    myThread.waitData = waitData;
    myThread.changeThreadState(State.RUNNABLE, State.PROCESS_WAITING);

    // Note that we have to perform the wait on the pthread
    // that created the process, which may involve switching
    // to a different Processor.

    GreenProcessor creatingProcessor = process.getCreatingProcessor();
    ProcessorLock queueLock = creatingProcessor.processWaitQueueLock;
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
  protected final void killInternal() {
    // remove this thread from wakeup and/or waiting queue
    ThreadProxy p = threadProxy;
    if (p != null) {
      // If the thread has a proxy, then (presumably) it is either
      // doing a sleep() or a wait(), both of which are interruptible,
      // so let morph() know that it should throw the
      // external interrupt object.
      this.throwInterruptWhenScheduled = true;

      GreenThread t = p.unproxy(); // t == this or t == null
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
  protected final void suspendInternal() {
    if(Synchronization.tryCompareAndSwap(this, suspendPendingOffset, 0, 1)) {
      // successful change from no suspend pending to suspend pending
    } else {
      // TODO: in what cases do we want to allow suspending a suspended thread?
    }
    if (this == GreenScheduler.getCurrentThread()) yield();
  }
  /**
   * Thread model dependent part of thread resumption
   */
  @Override
  protected final void resumeInternal() {
    if (Synchronization.tryCompareAndSwap(this, suspendPendingOffset, 1, 0)) {
      // we cleared the fact a thread suspend is pending, so thread was never
      // removed from runnable queue. There's no work to do here.
    } else {
      // thread was actually dequeued so re-queue it again
      GreenProcessor.getCurrentProcessor().scheduleThread(this);
    }
  }

  /**
   * Suspend thread if a suspend is pending. Called by processor dispatch loop.
   * Thread will be dequeued and not run if this returns true.
   *
   * @return whether the thread had a suspend pending
   */
  final boolean suspendIfPending() {
    if (suspendPending == 1) {
      if (Synchronization.tryCompareAndSwap(this, suspendPendingOffset, 1, 0)) {
        // we turned the suspendPending flag off
        return true;
      } else {
        // swap failed, so it must have been resumed prior to being suspended
      }
    }
    return false;
  }

  /**
   * Park the thread for OSR.
   */
  @Override
  public void osrPark() {
    osrParkLock.lock("locking in osrPark");
    if (osrParkingPermit) {
      osrParkingPermit=false;
      osrParkLock.unlock();
    } else {
      yield(osrParkLock, State.OSR_PARKED);
    }
  }

  /**
   * Unpark the thread from OSR.
   */
  @Override
  public void osrUnpark() {
    boolean schedule=false;
    osrParkLock.lock("locking in osrUnpark");
    if (state == State.OSR_PARKED) {
      changeThreadState(State.OSR_PARKED, State.RUNNABLE);
      schedule=true;
    } else {
      osrParkingPermit=true;
    }
    osrParkLock.unlock();
    if (schedule) {
      GreenProcessor.getCurrentProcessor().scheduleThread(this);
    }
  }

  /**
   * Put this thread on ready queue for subsequent execution on a future
   * timeslice.
   * Assumption: Thread.contextRegisters are ready to pick up execution
   *             ie. return to a yield or begin thread startup code
   */
  @Override
  public final void schedule() {
    if (trace) Scheduler.trace("GreenThread", "schedule", getIndex());
    if (state == State.BLOCKED)
      changeThreadState(RVMThread.State.BLOCKED, State.RUNNABLE);
    GreenProcessor.getCurrentProcessor().scheduleThread(this);
  }

  /**
   * Give a string of information on how a thread is set to be scheduled
   */
  @Override
  @Interruptible
  public String getThreadState() {
    return GreenScheduler.getThreadState(this);
  }

  /**
   * Is this thread suitable for putting on a queue?
   * @return whether the thread is terminated
   */
  final boolean isQueueable() {
    return state != State.TERMINATED;
  }
}

