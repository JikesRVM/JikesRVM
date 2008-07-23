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
package org.jikesrvm.scheduler;

import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.ArchitectureSpecific.Registers;
import static org.jikesrvm.ArchitectureSpecific.StackframeLayoutConstants.INVISIBLE_METHOD_ID;
import static org.jikesrvm.ArchitectureSpecific.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP;
import static org.jikesrvm.ArchitectureSpecific.StackframeLayoutConstants.STACK_SIZE_GUARD;
import org.jikesrvm.VM;
import org.jikesrvm.Configuration;
import org.jikesrvm.Services;
import org.jikesrvm.UnimplementedError;
import org.jikesrvm.adaptive.OnStackReplacementEvent;
import org.jikesrvm.adaptive.measurements.RuntimeMeasurements;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.jni.JNIEnvironment;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.objectmodel.ThinLockConstants;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.runtime.Time;
import org.vmmagic.pragma.BaselineNoRegisters;
import org.vmmagic.pragma.BaselineSaveLSRegisters;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.LogicallyUninterruptible;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NoOptCompile;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Untraced;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * A generic java thread's execution context.
 *
 * @see org.jikesrvm.scheduler.greenthreads.GreenThread
 * @see org.jikesrvm.mm.mminterface.CollectorThread
 * @see DebuggerThread
 * @see FinalizerThread
 * @see org.jikesrvm.adaptive.measurements.organizers.Organizer
 */
@Uninterruptible
@NonMoving
public abstract class RVMThread {
  /*
   *  debug and statistics
   */
  /** Trace execution */
  protected static final boolean trace = false;
  /** Trace thread termination */
  private static final boolean traceTermination = false;
  /** Trace adjustments to stack size */
  private static final boolean traceAdjustments = false;
  /** Generate statistics? */
  private static final boolean STATS = Lock.STATS;
  /** Number of wait operations */
  static int waitOperations;
  /** Number of timed wait operations */
  static int timedWaitOperations;
  /** Number of notify operations */
  static int notifyOperations;
  /** Number of notifyAll operations */
  static int notifyAllOperations;

  /**
   * The thread is modeled by a state machine the following constants describe
   * the state of the state machine. Invalid transitions will generate
   * IllegalThreadStateExceptions.
   */
  protected static enum State {
    /**
     * The thread is created but not yet scheduled to run. This state is the
     * same as {@link Thread.State#NEW}
     */
    NEW,
    /**
     * The thread is scheduled to run on a Processor. This state is the same
     * as {@link Thread.State#RUNNABLE}
     */
    RUNNABLE,
    /**
     * The thread is blocked by waiting for a monitor lock. This state is the
     * same as {@link Thread.State#BLOCKED}
     */
    BLOCKED,
    /**
     * The thread is waiting indefintely for a notify. This state maps to
     * {@link Thread.State#WAITING}
     */
    WAITING,
    /**
     * The thread is waiting for a notify or a time out. This state maps to
     * {@link Thread.State#TIMED_WAITING}
     */
    TIMED_WAITING,
    /**
     * The thread has exited. This state maps to {@link Thread.State#TERMINATED}
     */
    TERMINATED,
    /**
     * The thread is waiting for a notify or a time out. This state maps to
     * {@link Thread.State#TIMED_WAITING}
     */
    SLEEPING,
    /**
     * The thread is suspended awaiting a resume. This state maps to
     * {@link Thread.State#WAITING} which makes better sense than the JDK 1.5
     * convention
     */
    SUSPENDED,
    /**
     * The thread is parked for OSR awaiting an OSR unpark. This state maps to
     * {@link Thread.State#WAITING}
     */
    OSR_PARKED,
    /**
     * The thread is awaiting this thread to become RUNNABLE. This state maps to
     * {@link Thread.State#WAITING} matching JDK 1.5 convention
     */
    JOINING,
    /**
     * The thread is parked awaiting a unpark. This state maps to
     * {@link Thread.State#WAITING} matching JDK 1.5 convention
     */
    PARKED,
    /**
     * The thread is parked awaiting a unpark. This state maps to
     * {@link Thread.State#TIMED_WAITING} matching JDK 1.5 convention
     */
    TIMED_PARK,
    /**
     * This state is valid only for green threads. The thread is awaiting IO to
     * readable. This state maps to {@link Thread.State#WAITING}.
     */
    IO_WAITING,
    /**
     * This state is valid only for green threads. The thread is awaiting a
     * process to finish. This state maps to {@link Thread.State#WAITING}.
     */
    PROCESS_WAITING
  }

  /**
   * State of the thread. Either NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING,
   * TERMINATED, SLEEPING, SUSPENDED or PARKED
   */
  protected State state;

  /**
   * java.lang.Thread wrapper for this Thread. Not final so it may be
   * assigned during booting
   */
  private Thread thread;

  /** Name of the thread (can be changed during execution) */
  private String name;

  /**
   * The virtual machine terminates when the last non-daemon (user)
   * thread terminates.
   */
  protected boolean daemon;

  /**
   * Should the thread throw the external interrupt object the next time it's scheduled?
   */
  protected volatile boolean throwInterruptWhenScheduled;
  /**
   * Has the thread been interrupted? We should throw an interrupted exception
   * when we next get to an interruptible operation
   */
  protected volatile boolean interrupted;
  /**
   * The cause of the thread interruption (stop) or interruption
   */
  protected volatile Throwable causeOfThreadDeath;
  /**
   * Scheduling priority for this thread.
   * Note that: {@link java.lang.Thread#MIN_PRIORITY} <= priority
   * <= {@link java.lang.Thread#MAX_PRIORITY}.
   */
  private int priority;

  /**
   * Index of this thread in {@link Scheduler#threads}[].
   * This value must be non-zero because it is shifted
   * and used in {@link Object} lock ownership tests.
   */
  @Entrypoint
  private final int threadSlot;
  /**
   * Is this thread's stack being "borrowed" by thread dispatcher
   * (ie. while choosing next thread to run)?
   */
  @Entrypoint
  public boolean beingDispatched;

  /**
   * Thread is a system thread, that is one used by the system and as
   * such doesn't have a Runnable...
   */
  final boolean systemThread;

  /**
   * The boot thread, can't be final so as to allow initialization during boot
   * image writing.
   */
  private static RVMThread bootThread;

  /**
   * Assertion checking while manipulating raw addresses --
   * see {@link VM#disableGC()}/{@link VM#enableGC()}.
   * A value of "true" means it's an error for this thread to call "new".
   * This is only used for assertion checking; we do not bother to set it when
   * {@link VM#VerifyAssertions} is false.
   */
  private boolean disallowAllocationsByThisThread;

  /**
   * Counts the depth of outstanding calls to {@link VM#disableGC()}.  If this
   * is set, then we should also have {@link #disallowAllocationsByThisThread}
   * set.  The converse also holds.
   */
  private int disableGCDepth = 0;

  /**
   * Execution stack for this thread.
   */
  @Entrypoint
  private byte[] stack;

  /** The {@link Address} of the guard area for {@link #stack}. */
  @Entrypoint
  public Address stackLimit;

  /**
   * Place to save register state when this thread is not actually running.
   */
  @Entrypoint
  @Untraced
  public final Registers contextRegisters;

  /**
   * Place to save register state during hardware(C signal trap handler) or
   * software (RuntimeEntrypoints.athrow) trap handling.
   */
  @Entrypoint
  @Untraced
  private final Registers exceptionRegisters;

  /** Count of recursive uncaught exceptions, we need to bail out at some point */
  private int uncaughtExceptionCount = 0;

  /*
   * Wait/notify fields
   */

  /**
   * Place to save/restore this thread's monitor state during
   * {@link Object#wait} and {@link Object#notify}.
   */
  protected Object waitObject;

  /** Lock recursion count for this thread's monitor. */
  protected int    waitCount;

  /*
   * Sleep fields
   */

  /**
   * If this thread is sleeping, when should it be awakened?
   */
  protected long wakeupNanoTime;

  /*
   * Parking fields
   */

  /** Is a running thread permitted to ignore the next park request */
  private boolean parkingPermit;

  /**
   * An interrupted parked thread never sees the stack trace so use a proxy
   * interrupt exception in all cases. Also used to substitute for an
   * interrupted exception when we're in uninterruptible code and unable to
   * create one.
   */
  protected static final InterruptedException proxyInterruptException =
    new InterruptedException("park interrupted");

  /*
   * JNI fields
   */

  /**
   * Cached JNI environment for this thread
   */
  @Entrypoint
  @Untraced
  public JNIEnvironment jniEnv;

  /*
   * Timing fields
   */
  /**
   * Per thread timing is only active when this field has a value greater than 0.
   */
  private int timingDepth = 0;


  /**
   * Value returned from {@link Time#nanoTime()} when this thread
   * started running. If not currently running, then it has the value 0.
   */
  private long startNano = 0;

  /**
   * Accumulated nanoTime as measured by {@link Time#nanoTime()}
   * used by this thread.
   */
  private long totalNanos = 0;

  /** Used by GC to determine collection success */
  private boolean physicalAllocationFailed;

  /** Is this thread performing emergency allocation, when the normal heap limits are ignored. */
  private boolean emergencyAllocation;

  /** Used by GC to determine collection success */
  private int collectionAttempt;

  /** The OOME to throw */
  private OutOfMemoryError outOfMemoryError;

  /*
   * Enumerate different types of yield points for sampling
   */
  public static final int PROLOGUE = 0;
  public static final int BACKEDGE = 1;
  public static final int EPILOGUE = 2;
  public static final int NATIVE_PROLOGUE = 3;
  public static final int NATIVE_EPILOGUE = 4;
  public static final int OSROPT = 5;

  /*
   * Fields used for on stack replacement
   */

  /**
   * Only used by OSR when VM.BuildForAdaptiveSystem. Declared as an
   * Object to cut link to adaptive system.  Ugh.
   */
  public final Object /* OnStackReplacementEvent */ onStackReplacementEvent;

  /**
   * The flag indicates whether this thread is waiting for on stack
   * replacement before being rescheduled.
   */
  //flags should be packaged or replaced by other solutions
  public boolean isWaitingForOsr = false;

  /**
   * Before call new instructions, we need a bridge to recover
   * register states from the stack frame.
   */
  public CodeArray bridgeInstructions = null;
  /** Foo frame pointer offset */
  public Offset fooFPOffset = Offset.zero();
  /** Thread switch frame pointer offset */
  public Offset tsFPOffset = Offset.zero();

  /**
   * Flag to synchronize with osr organizer, the trigger sets osr
   * requests the organizer clear the requests
   */
  public boolean requesting_osr = false;

  /**
   * Is the system in the process of shutting down (has System.exit been called)
   */
  private static boolean systemShuttingDown = false;

  /**
   * @param stack stack in which to execute the thread
   */
  protected RVMThread(byte[] stack, Thread thread, String name, boolean daemon, boolean system, int priority) {
    this.stack = stack;
    this.name = name;
    this.daemon = daemon;
    this.priority = priority;

    Registers contextRegisters   = new Registers();
    Registers exceptionRegisters = new Registers();

    if(VM.VerifyAssertions) VM._assert(stack != null);
    // put self in list of threads known to scheduler and garbage collector
    if (!VM.runningVM) {
      // create primordial thread (in boot image)
      threadSlot = Scheduler.assignThreadSlot(this);
      this.contextRegisters = contextRegisters;
      this.exceptionRegisters = exceptionRegisters;
      // Remember the boot thread
      if (VM.VerifyAssertions) VM._assert(bootThread == null);
      bootThread = this;
      this.systemThread = true;
      this.state = State.RUNNABLE;
      // assign final field
      onStackReplacementEvent = null;
    } else {
      // create a normal (ie. non-primordial) thread
      if (trace) Scheduler.trace("Thread create: ", name);
      if (trace) Scheduler.trace("daemon: ", daemon ? "true" : "false");
      if (trace) Scheduler.trace("Thread", "create");
      // set up wrapper Thread if one exists
      this.thread = thread;
      // Set thread type
      this.systemThread = system;

      this.state = State.NEW;

      stackLimit = Magic.objectAsAddress(stack).plus(STACK_SIZE_GUARD);

      // get instructions for method to be executed as thread startoff
      CodeArray instructions = Entrypoints.threadStartoffMethod.getCurrentEntryCodeArray();

      VM.disableGC();

      // initialize thread registers
      Address ip = Magic.objectAsAddress(instructions);
      Address sp = Magic.objectAsAddress(stack).plus(stack.length);

      // Initialize the a thread stack as if "startoff" method had been called
      // by an empty baseline-compiled "sentinel" frame with one local variable.
      Configuration.archHelper.initializeStack(contextRegisters, ip, sp);

      threadSlot = Scheduler.assignThreadSlot(this);
      this.contextRegisters = contextRegisters;
      this.exceptionRegisters = exceptionRegisters;
      VM.enableGC();

      // only do this at runtime because it will call Magic;
      // we set this explicitly for the boot thread as part of booting.
      jniEnv = JNIEnvironment.allocateEnvironment();

      if (VM.BuildForAdaptiveSystem) {
        onStackReplacementEvent = new OnStackReplacementEvent();
      } else {
        onStackReplacementEvent = null;
      }

      if (thread == null) {
        // create wrapper Thread if doesn't exist
        this.thread = java.lang.JikesRVMSupport.createThread(this, name);
      }
    }
  }

  /**
   * Called during booting to give the boot thread a java.lang.Thread
   */
  @Interruptible
  public final void setupBootThread() {
    thread = java.lang.JikesRVMSupport.createThread(this, "Jikes_RVM_Boot_Thread");
  }

  /**
   * String representation of thread
   */
  @Override
  @Interruptible
  public String toString() {
    return (name == null) ? "Thread-" + getIndex() : name;
  }

  /**
   * Get the current java.lang.Thread.
   */
  @Interruptible
  public final Thread getJavaLangThread() {
    return thread;
  }

  /**
   * Get current thread's JNI environment.
   */
  public final JNIEnvironment getJNIEnv() {
    return jniEnv;
  }

  /** Get the disable GC depth */
  public final int getDisableGCDepth() {
    return disableGCDepth;
  }

  /** Modify the disable GC depth */
  public final void setDisableGCDepth(int d) {
    disableGCDepth = d;
  }

  /** Are allocations allowed by this thread? */
  public final boolean getDisallowAllocationsByThisThread() {
    return disallowAllocationsByThisThread;
  }

  /** Disallow allocations by this thread */
  public final void setDisallowAllocationsByThisThread() {
    disallowAllocationsByThisThread = true;
  }

  /** Allow allocations by this thread */
  public final void clearDisallowAllocationsByThisThread() {
    disallowAllocationsByThisThread = false;
  }

  /**
   * Initialize JNI environment for system threads. Called by VM.finishBooting
   */
  @Interruptible
  public final void initializeJNIEnv() {
    jniEnv = JNIEnvironment.allocateEnvironment();
  }

  /**
   * Indicate whether the stack of this Thread contains any C frame
   * (used in RuntimeEntrypoints.deliverHardwareException for stack resize)
   * @return false during the prolog of the first Java to C transition
   *        true afterward
   */
  public final boolean hasNativeStackFrame() {
    return jniEnv != null && jniEnv.hasNativeStackFrame();
  }

  /**
   * Change the state of the thread and fail if we're not in the expected thread
   * state. This method is logically uninterruptible as we should never be in
   * the wrong state
   *
   * @param oldState the previous thread state
   * @param newState the new thread state
   */
  @LogicallyUninterruptible
  @Entrypoint
  protected final void changeThreadState(State oldState, State newState) {
    if (trace) {
      VM.sysWrite("Thread.changeThreadState: thread=", threadSlot, name);
      VM.sysWrite(" current=", java.lang.JikesRVMSupport.getEnumName(state));
      VM.sysWrite(" old=", java.lang.JikesRVMSupport.getEnumName(oldState));
      VM.sysWriteln(" new=", java.lang.JikesRVMSupport.getEnumName(newState));
    }
    if (state == oldState) {
      state = newState;
    } else {
      throw new IllegalThreadStateException("Illegal thread state change from " +
          oldState + " to " + newState + " when in state " + state + " in thread " + name);
    }
  }

  /*
   * Starting and ending threads
   */

  /**
   * Method to be executed when this thread starts running. Calls
   * java.lang.Thread.run but system threads can override directly.
   */
  @Interruptible
  @Entrypoint
  public synchronized void run() {
    try {
      synchronized(thread) {
        Throwable t = java.lang.JikesRVMSupport.getStillBorn(thread);
        if(t != null) {
          java.lang.JikesRVMSupport.setStillBorn(thread, null);
          throw t;
        }
      }
      thread.run();
    } catch(Throwable t) {
      try {
        Thread.UncaughtExceptionHandler handler;
        handler = thread.getUncaughtExceptionHandler();
        handler.uncaughtException(thread, t);
      } catch(Throwable ignore) {
      }
    }
  }

  /**
   * Begin execution of current thread by calling its "run" method. This method
   * is at the bottom of all created method's stacks.
   */
  @Interruptible
  @SuppressWarnings({"unused", "UnusedDeclaration"})
  // Called by back-door methods.
  private static void startoff() {
    RVMThread currentThread = Scheduler.getCurrentThread();
    if (trace) {
      VM.sysWriteln("Thread.startoff(): about to call ", currentThread.toString(), ".run()");
    }

    try {
      currentThread.run();
    } finally {
      if (trace) {
        VM.sysWriteln("Thread.startoff(): finished ", currentThread.toString(), ".run()");
      }
      currentThread.terminate();
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
  }

  /**
   * Put this thread on ready queue for subsequent execution on a future
   * timeslice.
   * Assumption: Thread.contextRegisters are ready to pick up execution
   *             ie. return to a yield or begin thread startup code
   */
  public abstract void schedule();
  /**
   * Update internal state of Thread and Scheduler to indicate that
   * a thread is about to start
   */
  protected abstract void registerThreadInternal();

  /**
   * Update internal state of Thread and Scheduler to indicate that
   * a thread is about to start
   */
  public final void registerThread() {
    changeThreadState(State.NEW, State.RUNNABLE);
    registerThreadInternal();
  }

  /**
   * Start execution of 'this' by putting it on the appropriate queue
   * of an unspecified virtual processor.
   */
  @Interruptible
  public final void start() {
    registerThread();
    schedule();
  }

  /**
   * Terminate execution of current thread by abandoning all references to it
   * and resuming execution in some other (ready) thread.
   */
  @Interruptible
  public final void terminate() {
    if (VM.VerifyAssertions) VM._assert(Scheduler.getCurrentThread() == this);
    boolean terminateSystem = false;
    if (trace) Scheduler.trace("Thread", "terminate");
    if (traceTermination) {
      VM.disableGC();
      VM.sysWriteln("[ BEGIN Verbosely dumping stack at time of thread termination");
      Scheduler.dumpStack();
      VM.sysWriteln("END Verbosely dumping stack at time of creating thread termination ]");
      VM.enableGC();
    }

    if (VM.BuildForAdaptiveSystem) {
      RuntimeMeasurements.monitorThreadExit();
    }

    // allow java.lang.Thread.exit() to remove this thread from ThreadGroup
    java.lang.JikesRVMSupport.threadDied(thread);

    if (VM.VerifyAssertions) {
      if (Lock.countLocksHeldByThread(getLockingId()) > 0) {
        VM.sysWriteln("Error, thread terminating holding a lock");
        Scheduler.dumpVirtualMachine();
      }
    }
    // begin critical section
    //
    Scheduler.threadCreationMutex.lock("thread termination");
    Processor.getCurrentProcessor().disableThreadSwitching("disabled for thread termination");

    //
    // if the thread terminated because of an exception, remove
    // the mark from the exception register object, or else the
    // garbage collector will attempt to relocate its ip field.
    exceptionRegisters.inuse = false;

    Scheduler.numActiveThreads -= 1;
    if (daemon) {
      Scheduler.numDaemons -= 1;
    }
    if ((Scheduler.numDaemons == Scheduler.numActiveThreads) &&
        (VM.mainThread != null) &&
        VM.mainThread.launched) {
      // no non-daemon thread remains and the main thread was launched
      terminateSystem = true;
    }
    if (terminateSystem) {
      if (systemShuttingDown == false) {
        systemShuttingDown = true;
      } else {
        terminateSystem = false;
      }
    }
    if (traceTermination) {
      VM.sysWriteln("Thread.terminate: myThread.daemon = ", daemon);
      VM.sysWriteln("  Scheduler.numActiveThreads = ", Scheduler.numActiveThreads);
      VM.sysWriteln("  Scheduler.numDaemons = ", Scheduler.numDaemons);
      VM.sysWriteln("  terminateSystem = ", terminateSystem);
    }
    // end critical section
    //
    Processor.getCurrentProcessor().enableThreadSwitching();
    Scheduler.threadCreationMutex.unlock();

    if (VM.VerifyAssertions) {
      if (VM.fullyBooted || !terminateSystem) {
        Processor.getCurrentProcessor().failIfThreadSwitchingDisabled();
      }
    }
    if (terminateSystem) {
      if (uncaughtExceptionCount > 0)
        /* Use System.exit so that any shutdown hooks are run.  */ {
        if (VM.TraceExceptionDelivery) {
          VM.sysWriteln("Calling sysExit due to uncaught exception.");
        }
        System.exit(VM.EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
      } else if (thread instanceof MainThread) {
        MainThread mt = (MainThread) thread;
        if (!mt.launched) {
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

    if (jniEnv != null) {
      JNIEnvironment.deallocateEnvironment(jniEnv);
      jniEnv = null;
    }

    // release anybody waiting on this thread -
    // in particular, see {@link #join()}
    synchronized (this) {
      state = State.TERMINATED;
      notifyAll(this);
    }
    // become another thread
    //
    Scheduler.releaseThreadSlot(threadSlot, this);

    beingDispatched = true;
    Processor.getCurrentProcessor().dispatch(false);

    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Get the field that holds the cause of a thread death caused by a stop
   */
  public final Throwable getCauseOfThreadDeath() {
    return causeOfThreadDeath;
  }

  /*
   * Support for yieldpoints
   */

  /**
   * Yieldpoint taken in prologue.
   */
  @BaselineSaveLSRegisters
  //Save all non-volatile registers in prologue
  @NoOptCompile
  //We should also have a pragma that saves all non-volatiles in opt compiler,
  // OSR_BaselineExecStateExtractor.java, should then restore all non-volatiles before stack replacement
  //todo fix this -- related to SaveVolatile
  @Entrypoint
  public static void yieldpointFromPrologue() {
    Address fp = Magic.getFramePointer();
    org.jikesrvm.scheduler.greenthreads.GreenThread.yieldpoint(PROLOGUE, fp);
  }

  /**
   * Yieldpoint taken on backedge.
   */
  @BaselineSaveLSRegisters
  //Save all non-volatile registers in prologue
  @NoOptCompile
  // We should also have a pragma that saves all non-volatiles in opt compiler,
  // OSR_BaselineExecStateExtractor.java, should then restore all non-volatiles before stack replacement
  // TODO fix this -- related to SaveVolatile
  @Entrypoint
  public static void yieldpointFromBackedge() {
    Address fp = Magic.getFramePointer();
    org.jikesrvm.scheduler.greenthreads.GreenThread.yieldpoint(BACKEDGE, fp);
  }

  /**
   * Yieldpoint taken in epilogue.
   */
  @BaselineSaveLSRegisters
  //Save all non-volatile registers in prologue
  @NoOptCompile
  //We should also have a pragma that saves all non-volatiles in opt compiler,
  // OSR_BaselineExecStateExtractor.java, should then restore all non-volatiles before stack replacement
  // TODO fix this -- related to SaveVolatile
  @Entrypoint
  public static void yieldpointFromEpilogue() {
    Address fp = Magic.getFramePointer();
    org.jikesrvm.scheduler.greenthreads.GreenThread.yieldpoint(EPILOGUE, fp);
  }

  /*
   * Support for suspend/resume
   */

  /**
   * Thread model dependent part of thread suspension
   */
  protected abstract void suspendInternal();

  /**
   * Suspend execution of current thread until it is resumed.
   * Call only if caller has appropriate security clearance.
   */
  @LogicallyUninterruptible
  public final void suspend() {
    Throwable rethrow = null;
    changeThreadState(State.RUNNABLE, State.SUSPENDED);
    try {
      // let go of outer lock
      ObjectModel.genericUnlock(thread);
      suspendInternal();
    } catch (Throwable t) {
      rethrow = t;
    }
    // regain outer lock
    ObjectModel.genericLock(thread);
    if (rethrow != null) {
      RuntimeEntrypoints.athrow(rethrow);
    }
  }

  /**
   * Thread model dependent part of thread resumption
   */
  protected abstract void resumeInternal();

  /**
   * Resume execution of a thread that has been suspended.
   * Call only if caller has appropriate security clearance.
   */
  @Interruptible
  public final void resume() {
    changeThreadState(State.SUSPENDED, State.RUNNABLE);
    if (trace) Scheduler.trace("Thread", "resume() scheduleThread ", getIndex());
    resumeInternal();
  }

  /*
   * OSR support
   */

  /** Suspend the thread pending completion of OSR, unless OSR has already
   * completed. */
  public abstract void osrPark();
  /** Signal completion of OSR activity on this thread.  Resume it if it was
   * already parked, or prevent it from parking if it is about to park. */
  public abstract void osrUnpark();

  /*
   * Sleep support
   */

  /**
   * Thread model dependent sleep
   * @param millis
   * @param ns
   */
  @Interruptible
  protected abstract void sleepInternal(long millis, int ns) throws InterruptedException;
  /**
   * Suspend execution of current thread for specified number of seconds
   * (or fraction).
   */
  @Interruptible
  public static void sleep(long millis, int ns) throws InterruptedException {
    RVMThread myThread = Scheduler.getCurrentThread();
    myThread.changeThreadState(State.RUNNABLE, State.SLEEPING);
    try {
      myThread.sleepInternal(millis, ns);
    } catch (InterruptedException ie) {
      if (myThread.state != State.RUNNABLE)
        myThread.changeThreadState(State.SLEEPING, State.RUNNABLE);
      myThread.clearInterrupted();
      throw(ie);
    }
    myThread.changeThreadState(State.SLEEPING, State.RUNNABLE);
  }

  /*
   * Wait and notify support
   */

  /**
   * Support for Java {@link java.lang.Object#wait()} synchronization primitive.
   *
   * @param o the object synchronized on
   * @param millis the number of milliseconds to wait for notification
   */
  @Interruptible
  protected abstract Throwable waitInternal(Object o, long millis);

  /**
   * Support for Java {@link java.lang.Object#wait()} synchronization primitive.
   *
   * @param o the object synchronized on
   */
  @Interruptible
  protected abstract Throwable waitInternal(Object o);

  /**
   * Support for Java {@link java.lang.Object#wait()} synchronization primitive.
   *
   * @param o the object synchronized on
   */
  @Interruptible
  /* only loses control at expected points -- I think -dave */
  public static void wait(Object o) {
    if (STATS) waitOperations++;
    RVMThread t = Scheduler.getCurrentThread();
    Throwable rethrow = t.waitInternal(o);
    if (rethrow != null) {
      RuntimeEntrypoints.athrow(rethrow); // doesn't return
    }
  }

  /**
   * Support for Java {@link java.lang.Object#wait()} synchronization primitive.
   *
   * @param o the object synchronized on
   * @param millis the number of milliseconds to wait for notification
   */
  @LogicallyUninterruptible
  /* only loses control at expected points -- I think -dave */
  public static void wait(Object o, long millis) {
    if (STATS) timedWaitOperations++;
    RVMThread t = Scheduler.getCurrentThread();
    Throwable rethrow = t.waitInternal(o, millis);
    if (rethrow != null) {
      RuntimeEntrypoints.athrow(rethrow);
    }
  }

  /**
   * Support for Java {@link java.lang.Object#notify()} synchronization primitive.
   *
   * @param o the object synchronized on
   */
  protected abstract void notifyInternal(Object o, Lock l);

  /**
   * Support for Java {@link java.lang.Object#notifyAll()} synchronization primitive.
   *
   * @param o the object synchronized on
   */
  protected abstract void notifyAllInternal(Object o, Lock l);

  @LogicallyUninterruptible
  private static void raiseIllegalMonitorStateException(String msg, Object o) {
    throw new IllegalMonitorStateException(msg + o);
  }
  /**
   * Support for Java {@link java.lang.Object#notify()} synchronization primitive.
   *
   * @param o the object synchronized on
   */
  public static void notify(Object o) {
    if (STATS) notifyOperations++;
    Lock l = ObjectModel.getHeavyLock(o, false);
    if (l == null) return;
    Processor proc = Processor.getCurrentProcessor();
    if (l.getOwnerId() != proc.threadId) {
      raiseIllegalMonitorStateException("notifying", o);
    }
    Scheduler.getCurrentThread().notifyInternal(o, l);
  }

  /**
   * Support for Java synchronization primitive.
   *
   * @param o the object synchronized on
   * @see java.lang.Object#notifyAll
   */
  public static void notifyAll(Object o) {
    if (STATS) notifyAllOperations++;
    Scheduler.LockModel l = (Scheduler.LockModel)ObjectModel.getHeavyLock(o, false);
    if (l == null) return;
    Processor proc = Processor.getCurrentProcessor();
    if (l.getOwnerId() != proc.threadId) {
      raiseIllegalMonitorStateException("notifyAll", o);
    }
    Scheduler.getCurrentThread().notifyAllInternal(o, l);
  }

  /*
   * Interrupt and stop support
   */

  /**
   * Thread model dependent part of stopping/interrupting a thread
   */
  protected abstract void killInternal();

  /**
   * Deliver the throwable stopping/interrupting this thread
   */
  @LogicallyUninterruptible
  public void kill(Throwable cause, boolean throwImmediately) {
    // yield() will notice the following and take appropriate action
    this.causeOfThreadDeath = cause;
    if (throwImmediately) {
      // FIXME - this is dangerous.  Only called from Thread.stop(),
      // which is deprecated.
      this.throwInterruptWhenScheduled = true;
    }
    killInternal();
  }

  /*
   * Park and unpark support
   */

  /**
   * Park the current thread
   * @param isAbsolute is the time value given relative or absolute
   * @param time the timeout value in nanoseconds, 0 => no timeout
   */
  @Interruptible
  public final void park(boolean isAbsolute, long time) throws Throwable {
    if (VM.VerifyAssertions) {
      RVMThread curThread = Scheduler.getCurrentThread();
      VM._assert(curThread == this);
    }
    // Has the thread already been unparked? (ie the permit is available?)
    if (parkingPermit) {
      // Yes: exit early double checking illegal thread states
      changeThreadState(State.RUNNABLE, State.RUNNABLE);
      parkingPermit = false;
    } else if (throwInterruptWhenScheduled) {
      // Was this thread interrupted prior to parking?
      changeThreadState(State.RUNNABLE, State.RUNNABLE);
      if (causeOfThreadDeath == null) {
        // interrupt exceptions are swallowed so ignore
      } else {
        // Thread was stopped so throw thread death
        throw causeOfThreadDeath;
      }
    } else {
      // Put thread into parked state
      State parkedState;
      long millis = 0L;
      int ns = 0;
      // Do we have a timeout?
      if (time != 0) {
        parkedState = State.TIMED_PARK;
        millis = time / 1000000;
        if (isAbsolute) {
          // if it's an absolute amount of time then remove the current time as
          // we will adjust up by this much in the sleep
          millis -= (Time.nanoTime() / ((long)1e6));
        }
        ns = (int)time % 1000000;
      } else {
        parkedState = State.PARKED;
      }
      changeThreadState(State.RUNNABLE, parkedState);
      // Do we hold the lock on the surrounding java.lang.Thread?
      boolean holdsLock = holdsLock(thread);
      if (holdsLock) {
        // If someone locked the java.lang.Thread, release before going to sleep
        // to allow interruption
        ObjectModel.genericUnlock(thread);
      }
      try {
        sleepInternal(millis, ns);
      } catch (InterruptedException thr) {
        // swallow thread interruptions
      }
      if (holdsLock) {
        ObjectModel.genericLock(thread);
      }
      if (state != State.RUNNABLE) {
        // change thread to runnable unless already performed by athrow
        changeThreadState(parkedState, State.RUNNABLE);
      }
    }
  }

  /**
   * Unpark this thread, not necessarily the current thread
   */
  public void unpark() {
    if (state == State.PARKED) {
      // Wake up sleeping thread
      kill(proxyInterruptException , false);
    } else if (state == State.RUNNABLE) {
      // Allow next call to park to just run through
      parkingPermit = true;
    } else {
      // nothing to do
    }
  }

  /**
   * Get this thread's index in {@link Scheduler#threads}[].
   */
  @LogicallyUninterruptible
  public final int getIndex() {
    if (VM.VerifyAssertions) VM._assert((state == State.TERMINATED) || Scheduler.threads[threadSlot] == this);
    return threadSlot;
  }

  /**
   * Get this thread's id for use in lock ownership tests.
   * This is just the thread's index as returned by {@link #getIndex()},
   * shifted appropriately so it can be directly used in the ownership tests.
   */
  public final int getLockingId() {
    if (VM.VerifyAssertions) VM._assert(Scheduler.threads[threadSlot] == this);
    return threadSlot << ThinLockConstants.TL_THREAD_ID_SHIFT;
  }

  /**
   * Change the size of the currently executing thread's stack.
   * @param newSize    new size (in bytes)
   * @param exceptionRegisters register state at which stack overflow trap
   * was encountered (null --> normal method call, not a trap)
   */
  @Interruptible
  public static void resizeCurrentStack(int newSize, Registers exceptionRegisters) {
    if (traceAdjustments) VM.sysWrite("Thread: resizeCurrentStack\n");
    if (MemoryManager.gcInProgress()) {
      VM.sysFail("system error: resizing stack while GC is in progress");
    }
    byte[] newStack = MemoryManager.newStack(newSize, false);
    Processor.getCurrentProcessor().disableThreadSwitching("disabled for stack resizing");
    transferExecutionToNewStack(newStack, exceptionRegisters);
    Processor.getCurrentProcessor().enableThreadSwitching();
    if (traceAdjustments) {
      RVMThread t = Scheduler.getCurrentThread();
      VM.sysWrite("Thread: resized stack ", t.getIndex());
      VM.sysWrite(" to ", t.stack.length / 1024);
      VM.sysWrite("k\n");
    }
  }

  @NoInline
  @BaselineNoRegisters
  //this method does not do a normal return and hence does not execute epilogue --> non-volatiles not restored!
  private static void transferExecutionToNewStack(byte[] newStack, Registers exceptionRegisters) {
    // prevent opt compiler from inlining a method that contains a magic
    // (returnToNewStack) that it does not implement.

    RVMThread myThread = Scheduler.getCurrentThread();
    byte[] myStack = myThread.stack;

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
    Address myTop = Magic.objectAsAddress(myStack).plus(myStack.length);
    Address newTop = Magic.objectAsAddress(newStack).plus(newStack.length);

    Address myFP = Magic.getFramePointer();
    Offset myDepth = myTop.diff(myFP);
    Address newFP = newTop.minus(myDepth);

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
    if (exceptionRegisters != null) {
      adjustRegisters(exceptionRegisters, delta);
    }
    adjustStack(newStack, newFP, delta);

    // install new stack
    //
    myThread.stack = newStack;
    myThread.stackLimit = Magic.objectAsAddress(newStack).plus(STACK_SIZE_GUARD);
    Processor.getCurrentProcessor().activeThreadStackLimit = myThread.stackLimit;

    // return to caller, resuming execution on new stack
    // (original stack now abandoned)
    //
    if (VM.BuildForPowerPC) {
      Magic.returnToNewStack(Magic.getCallerFramePointer(newFP));
    } else if (VM.BuildForIA32) {
      Magic.returnToNewStack(newFP);
    }

    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * This (suspended) thread's stack has been moved.
   * Fixup register and memory references to reflect its new position.
   * @param delta displacement to be applied to all interior references
   */
  public final void fixupMovedStack(Offset delta) {
    if (traceAdjustments) VM.sysWrite("Thread: fixupMovedStack\n");

    if (!contextRegisters.getInnermostFramePointer().isZero()) {
      adjustRegisters(contextRegisters, delta);
    }
    if ((exceptionRegisters.inuse) &&
        (exceptionRegisters.getInnermostFramePointer().NE(Address.zero()))) {
      adjustRegisters(exceptionRegisters, delta);
    }
    if (!contextRegisters.getInnermostFramePointer().isZero()) {
      adjustStack(stack, contextRegisters.getInnermostFramePointer(), delta);
    }
    stackLimit = stackLimit.plus(delta);
  }

  /**
   * A thread's stack has been moved or resized.
   * Adjust registers to reflect new position.
   *
   * @param registers registers to be adjusted
   * @param delta     displacement to be applied
   */
  private static void adjustRegisters(Registers registers, Offset delta) {
    if (traceAdjustments) VM.sysWrite("Thread: adjustRegisters\n");

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
    int compiledMethodId = Magic.getCompiledMethodID(registers.getInnermostFramePointer());
    if (compiledMethodId != INVISIBLE_METHOD_ID) {
      if (VM.BuildForIA32) {
        Configuration.archHelper.adjustESP(registers, delta, traceAdjustments);
      }
      if (traceAdjustments) {
        CompiledMethod compiledMethod = CompiledMethods.getCompiledMethod(compiledMethodId);
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
    if (traceAdjustments) VM.sysWrite("Thread: adjustStack\n");

    while (Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
      // adjust FP save area
      //
      Magic.setCallerFramePointer(fp, Magic.getCallerFramePointer(fp).plus(delta));
      if (traceAdjustments) {
        VM.sysWrite(" fp=", fp.toWord());
      }

      // advance to next frame
      //
      fp = Magic.getCallerFramePointer(fp);
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
  private static Offset copyStack(byte[] newStack) {
    RVMThread myThread = Scheduler.getCurrentThread();
    byte[] myStack = myThread.stack;

    Address myTop = Magic.objectAsAddress(myStack).plus(myStack.length);
    Address newTop = Magic.objectAsAddress(newStack).plus(newStack.length);
    Address myFP = Magic.getFramePointer();
    Offset myDepth = myTop.diff(myFP);
    Address newFP = newTop.minus(myDepth);

    // before copying, make sure new stack isn't too small
    //
    if (VM.VerifyAssertions) {
      VM._assert(newFP.GE(Magic.objectAsAddress(newStack).plus(STACK_SIZE_GUARD)));
    }

    Memory.memcopy(newFP, myFP, myDepth.toWord().toExtent());

    return newFP.diff(myFP);
  }

  /**
   * Set the "isDaemon" status of this thread.
   * Although a java.lang.Thread can only have setDaemon invoked on it
   * before it is started, Threads can become daemons at any time.
   * Note: making the last non daemon a daemon will terminate the VM.
   *
   * Note: This method might need to be uninterruptible so it is final,
   * which is why it isn't called setDaemon.
   *
   * Public so that java.lang.Thread can use it.
   */
  public final void makeDaemon(boolean on) {
    if (daemon == on) {
      // nothing to do
    } else {
      daemon = on;
      if (state == State.NEW) {
        // thread will start as a daemon
      } else {
        Scheduler.threadCreationMutex.lock("daemon creation mutex");
        Scheduler.numDaemons += on ? 1 : -1;
        Scheduler.threadCreationMutex.unlock();

        if (Scheduler.numDaemons == Scheduler.numActiveThreads) {
          if (VM.TraceThreads) {
            Scheduler.trace("Thread", "last non Daemon demonized");
          }
          VM.sysExit(0);
          if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
        }
      }
    }
  }

  /**
   * Dump information for all threads, via {@link VM#sysWrite(String)}.  Each
   * thread's info is newline-terminated.
   *
   * @param verbosity Ignored.
   */
  public static void dumpAll(int verbosity) {
    for (int i = 0; i < Scheduler.threads.length; i++) {
      RVMThread t = Scheduler.threads[i];
      if (t == null) continue;
      VM.sysWrite("Thread ");
      VM.sysWriteInt(i);
      VM.sysWrite(":  ");
      VM.sysWriteHex(Magic.objectAsAddress(t));
      VM.sysWrite("   ");
      t.dump(verbosity);
      // Compensate for t.dump() not newline-terminating info.
      VM.sysWriteln();
    }
  }

  /**
   * @return whether or not the thread has an active timer interval
   */
  public final boolean hasActiveTimedInterval() {
    return timingDepth > 0;
  }

  /**
   * Begin a possibly nested timing interval.
   * @return the current value of {@link #totalNanos}.
   */
  public final long startTimedInterval() {
    long now = Time.nanoTime();
    if (timingDepth == 0) {
      timingDepth = 1;
      startNano = now;
      totalNanos = 0;
    } else {
      timingDepth++;
      totalNanos += now - startNano;
      startNano = now;
    }
    return totalNanos;
  }

  /**
   * End a possibly nested timing interval
   */
  public final long endTimedInterval() {
    long now = Time.nanoTime();
    timingDepth--;
    totalNanos += now - startNano;
    startNano = now;
    return totalNanos;
  }

  /**
   * Called from  Processor.dispatch when a thread is about to
   * start executing.
   */
  public final void resumeInterval(long now) {
    if (VM.VerifyAssertions) VM._assert(startNano == 0);
    startNano = now;
  }

  /**
   * Called from {@link Processor#dispatch} when a thread is about to stop
   * executing.
   */
  public final void suspendInterval(long now) {
    totalNanos += now - startNano;
    startNano = 0;
  }

  /** @return The value of {@link #isBootThread} */
  public final boolean isBootThread() {
    return this == bootThread;
  }

  /** @return Is this the MainThread ? */
  private boolean isMainThread() {
    return thread instanceof MainThread;
  }

  /**
   * Is this the Idle thread?
   * @return false
   */
  public boolean isIdleThread() {
    return false;
  }

  /**
   * Is this the GC thread?
   * @return false
   */
  public boolean isGCThread() {
    return false;
  }

  /**
   * Is this the debugger thread?
   * @return false
   */
  public boolean isDebuggerThread() {
    return false;
  }

  /** Is this a system thread? */
  public final boolean isSystemThread() {
    return systemThread;
  }


  /** Returns the value of {@link #daemon}. */
  public final boolean isDaemonThread() {
    return daemon;
  }

  /** Is the thread started and not terminated */
  public final boolean isAlive() {
    return (state != State.NEW) && (state != State.TERMINATED);
  }

  /**
   * Sets the name of the thread
   * @param name the new name for the thread
   * @see java.lang.Thread#setName(String)
   */
  public final void setName(String name) {
    this.name = name;
  }
  /**
   * Gets the name of the thread
   * @see java.lang.Thread#getName()
   */
  public final String getName() {
    return name;
  }

  /**
   * Does the currently running Thread hold the lock on an obj?
   * @param obj the object to check
   * @return whether the thread holds the lock
   * @see java.lang.Thread#holdsLock(Object)
   */
  public final boolean holdsLock(Object obj) {
    RVMThread mine = Scheduler.getCurrentThread();
    return ObjectModel.holdsLock(obj, mine);
  }

  /**
   * Throw the external interrupt associated with the thread now it is running
   */
  @LogicallyUninterruptible
  protected final void postExternalInterrupt() {
    throwInterruptWhenScheduled = false;
    Throwable t = causeOfThreadDeath;
    causeOfThreadDeath = null;
    if (t instanceof InterruptedException  && t != proxyInterruptException) {
      t.fillInStackTrace();
    }
    state = State.RUNNABLE;
    RuntimeEntrypoints.athrow(t);
  }

  /**
   * Was this thread interrupted?
   * @return whether the thread has been interrupted
   * @see java.lang.Thread#isInterrupted()
   */
  public final boolean isInterrupted() {
    return interrupted;
  }
  /**
   * Clear the interrupted status of this thread
   * @see java.lang.Thread#interrupted()
   */
  public final void clearInterrupted() {
    interrupted = false;
  }
  /**
   * Interrupt this thread
   * @see java.lang.Thread#interrupt()
   */
  @Interruptible
  public final void interrupt() {
    interrupted = true;
    switch (state) {
    case WAITING:
    case TIMED_WAITING:
      kill(new InterruptedException("wait interrupted"), false);
      break;
    case SLEEPING:
      kill(new InterruptedException("sleep interrupted"), false);
      break;
    case JOINING:
      kill(new InterruptedException("join interrupted"), false);
      break;
    case PARKED:
    case TIMED_PARK:
      kill(proxyInterruptException, false);
      break;
    default:
      kill(new InterruptedException(), false);
    }
  }
  /**
   * Get the priority of the thread
   * @return the thread's priority
   * @see java.lang.Thread#getPriority()
   */
  public final int getPriority() {
    return priority;
  }
  /**
   * Set the priority of the thread
   * @param priority
   * @see java.lang.Thread#getPriority()
   */
  public final void setPriority(int priority) {
    this.priority = priority;
  }
  /**
   * Get the state of the thread in a manner compatible with the Java API
   * @return thread state
   * @see java.lang.Thread#getState()
   */
  @Interruptible
  public final Thread.State getState() {
    switch (state) {
    case NEW:
      return Thread.State.NEW;
    case RUNNABLE:
      return Thread.State.RUNNABLE;
    case BLOCKED:
      return Thread.State.BLOCKED;
    case WAITING:
    case SUSPENDED:
    case OSR_PARKED:
    case JOINING:
    case PARKED:
    case IO_WAITING:
    case PROCESS_WAITING:
      return Thread.State.WAITING;
    case TIMED_WAITING:
    case TIMED_PARK:
    case SLEEPING:
      return Thread.State.TIMED_WAITING;
    case TERMINATED:
      return Thread.State.TERMINATED;
    }
    VM.sysFail("Unknown thread state " + state);
    return null;
  }
  /**
   * Wait for the thread to die or for the timeout to occur
   * @param ms milliseconds to wait
   * @param ns nanoseconds to wait
   */
  @Interruptible
  public final void join(long ms, int ns) throws InterruptedException {
    RVMThread myThread = Scheduler.getCurrentThread();
    if (VM.VerifyAssertions) VM._assert(myThread != this);
    synchronized(this) {
      myThread.changeThreadState(State.RUNNABLE, State.JOINING);
      if (ms == 0 && ns != 0) {
        ms++;
      }
      if (ms == 0) {
        while (isAlive()) {
          wait(this);
        }
      } else {
        long startNano = Time.nanoTime();
        long timeLeft;
        if (isAlive()) {
          do {
            long elapsedMillis = (Time.nanoTime()-startNano)/((long) 1e6);
            timeLeft = ms - elapsedMillis;
            wait(this, timeLeft);
          } while (isAlive() && timeLeft > 0);
        }
      }
      myThread.changeThreadState(State.JOINING, State.RUNNABLE);
    }
  }

  /**
   * Count the stack frames of this thread
   */
  @Interruptible
  public final int countStackFrames() {
    if (state != State.SUSPENDED) {
      throw new IllegalThreadStateException("Thread.countStackFrames called on non-suspended thread");
    }
    throw new UnimplementedError();
  }

  /**
   * @return the length of the stack
   */
  public final int getStackLength() {
    return stack.length;
  }

  /**
   * @return the stack
   */
  public final byte[] getStack() {
    return stack;
  }

  /**
   * @return the thread's exception registers
   */
  public final Registers getExceptionRegisters() {
    return exceptionRegisters;
  }

  /**
   * @return the thread's context registers (saved registers when thread is suspended
   *         by green-thread scheduler).
   */
  public final Registers getContextRegisters() {
    return contextRegisters;
  }

  /**
   * Give a string of information on how a thread is set to be scheduled
   */
  @Interruptible
  public abstract String getThreadState();

  /** Set the initial attempt. */
  public final void reportCollectionAttempt() {
    collectionAttempt++;
  }

  /** Set the initial attempt. */
  public final int getCollectionAttempt() {
    return collectionAttempt;
  }

  /** Resets the attempts. */
  public final void resetCollectionAttempts() {
    collectionAttempt = 0;
  }

  /** Get the physical allocation failed flag. */
  public final boolean physicalAllocationFailed() {
    return physicalAllocationFailed;
  }

  /** Set the physical allocation failed flag. */
  public final void setPhysicalAllocationFailed() {
    physicalAllocationFailed = true;
  }

  /** Clear the physical allocation failed flag. */
  public final void clearPhysicalAllocationFailed() {
    physicalAllocationFailed = false;
  }

  /** Set the emergency allocation flag. */
  public final void setEmergencyAllocation() {
    emergencyAllocation = true;
  }

  /** Clear the emergency allocation flag. */
  public final void clearEmergencyAllocation() {
    emergencyAllocation = false;
  }

  /** Read the emergency allocation flag. */
  public final boolean emergencyAllocation() {
    return emergencyAllocation;
  }

  /**
   * Returns the outstanding OutOfMemoryError.
   */
  public final OutOfMemoryError getOutOfMemoryError() {
    return outOfMemoryError;
  }
  /**
   * Sets the outstanding OutOfMemoryError.
   */
  public final void setOutOfMemoryError(OutOfMemoryError oome) {
    outOfMemoryError = oome;
  }

  /**
   * Get the thread to use for building stack traces.
   * NB overridden by {@link org.jikesrvm.mm.mminterface.CollectorThread}
   */
  @Uninterruptible
  public RVMThread getThreadForStackTrace() {
    return this;
  }

  /**
   * Clears the outstanding OutOfMemoryError.
   */
  public final void clearOutOfMemoryError() {
    /*
     * SEE RVM-141
     * To avoid problems in GCTrace configuration, only clear the OOM if it is non-NULL.
     */
    if (outOfMemoryError != null) {
      outOfMemoryError = null;
    }
  }

  @Interruptible
  public final void handleUncaughtException(Throwable exceptionObject) {
    uncaughtExceptionCount++;

    if (exceptionObject instanceof OutOfMemoryError) {
      /* Say allocation from this thread is emergency allocation */
      setEmergencyAllocation();
    }
    handlePossibleRecursiveException();
    VM.enableGC();
    if (thread == null) {
      VM.sysWrite("Exception in the primordial thread \"", toString(), "\" while booting: ");
    } else {
      // This is output like that of the Sun JDK.
      VM.sysWrite("Exception in thread \"", getName(), "\": ");
    }
    if (VM.fullyBooted) {
      exceptionObject.printStackTrace();
    }
    Scheduler.getCurrentThread().terminate();
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** Handle the case of exception handling triggering new exceptions. */
  private void handlePossibleRecursiveException() {
    if (uncaughtExceptionCount > 1 &&
        uncaughtExceptionCount <=
        VM.maxSystemTroubleRecursionDepth + VM.maxSystemTroubleRecursionDepthBeforeWeStopVMSysWrite) {
      VM.sysWrite("We got an uncaught exception while (recursively) handling ");
      VM.sysWrite(uncaughtExceptionCount - 1);
      VM.sysWrite(" uncaught exception");
      if (uncaughtExceptionCount - 1 != 1) {
        VM.sysWrite("s");
      }
      VM.sysWriteln(".");
    }
    if (uncaughtExceptionCount > VM.maxSystemTroubleRecursionDepth) {
      Scheduler.dumpVirtualMachine();
      VM.dieAbruptlyRecursiveSystemTrouble();
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
  }

  /**
   * Dump this thread's identifying information, for debugging, via
   * {@link VM#sysWrite(String)}.
   * We do not use any spacing or newline characters.  Callers are responsible
   * for space-separating or newline-terminating output.
   */
  public void dump() {
    dump(0);
  }

  /**
   * Dump this thread's identifying information, for debugging, via
   * {@link VM#sysWrite(String)}.
   * We pad to a minimum of leftJustify characters. We do not use any spacing
   * characters.  Callers are responsible for space-separating or
   * newline-terminating output.
   *
   * @param leftJustify minium number of characters emitted, with any
   * extra characters being spaces.
   */
  public void dumpWithPadding(int leftJustify) {
    char[] buf = Services.grabDumpBuffer();
    int len = dump(buf);
    VM.sysWrite(buf, len);
    for (int i = leftJustify - len; i > 0; i--) {
      VM.sysWrite(" ");
    }
    Services.releaseDumpBuffer();
  }

  /**
   * Dump this thread's identifying information, for debugging, via
   * {@link VM#sysWrite(String)}.
   * We do not use any spacing or newline characters.  Callers are responsible
   * for space-separating or newline-terminating output.
   *
   *  This function avoids write barriers and allocation.
   *
   * @param verbosity Ignored.
   */
  public void dump(int verbosity) {
    char[] buf = Services.grabDumpBuffer();
    int len = dump(buf);
    VM.sysWrite(buf, len);
    Services.releaseDumpBuffer();
  }

  /**
   *  Dump this thread's info, for debugging.
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
   */
  public int dump(char[] dest, int offset) {
    offset = Services.sprintf(dest, offset, getIndex());   // id
    if (daemon) {
      offset = Services.sprintf(dest, offset, "-daemon");     // daemon thread?
    }
    if (isBootThread()) {
      offset = Services.sprintf(dest, offset, "-Boot");    // Boot (Primordial) thread
    }
    if (isMainThread()) {
      offset = Services.sprintf(dest, offset, "-main");    // Main Thread
    }
    if (isIdleThread()) {
      offset = Services.sprintf(dest, offset, "-idle");       // idle thread?
    }
    if (isGCThread()) {
      offset = Services.sprintf(dest, offset, "-collector");  // gc thread?
    }
    if (beingDispatched) {
      offset = Services.sprintf(dest, offset, "-being_dispatched");
    }
    offset = Services.sprintf(dest, offset, "-");
    offset = Services.sprintf(dest, offset, java.lang.JikesRVMSupport.getEnumName(state));
    if (state == State.TIMED_WAITING || state == State.TIMED_PARK) {
      offset = Services.sprintf(dest, offset, "(");
      long timeLeft = wakeupNanoTime - Time.nanoTime();
      offset = Services.sprintf(dest, offset, (long)Time.nanosToMillis(timeLeft));
      offset = Services.sprintf(dest, offset, "ms)");
    }
    if (throwInterruptWhenScheduled) {
      offset = Services.sprintf(dest, offset, "-interrupted");
    }
    return offset;
  }

  /**
   *  Dump this thread's info, for debugging.
   *  Copy the info about it into a destination char
   *  array.  We do not use any spacing or newline characters.
   *
   *  This is identical to calling {@link #dump(char[],int)} with an
   *  <code>offset</code> of zero.
   */
  public int dump(char[] dest) {
    return dump(dest, 0);
  }
  /** Dump statistics gather on operations */
  static void dumpStats() {
    VM.sysWrite("FatLocks: ");
    VM.sysWrite(waitOperations);
    VM.sysWrite(" wait operations\n");
    VM.sysWrite("FatLocks: ");
    VM.sysWrite(timedWaitOperations);
    VM.sysWrite(" timed wait operations\n");
    VM.sysWrite("FatLocks: ");
    VM.sysWrite(notifyOperations);
    VM.sysWrite(" notify operations\n");
    VM.sysWrite("FatLocks: ");
    VM.sysWrite(notifyAllOperations);
  }
}
