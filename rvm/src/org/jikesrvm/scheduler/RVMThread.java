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
package org.jikesrvm.scheduler;

import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_DUMP_STACK_AND_DIE;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_MAIN_THREAD_COULD_NOT_LAUNCH;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_RECURSIVELY_SHUTTING_DOWN;
import static org.jikesrvm.runtime.SysCall.sysCall;
import static org.jikesrvm.objectmodel.ThinLockConstants.TL_THREAD_ID_SHIFT;

import java.security.AccessController;
import java.security.PrivilegedAction;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.OSRListener;
import org.jikesrvm.adaptive.OnStackReplacementEvent;
import org.jikesrvm.adaptive.measurements.RuntimeMeasurements;
import org.jikesrvm.architecture.AbstractRegisters;
import org.jikesrvm.architecture.ArchitectureFactory;
import org.jikesrvm.architecture.StackFrameLayout;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.baseline.BaselineCompiledMethod;
import org.jikesrvm.compilers.common.CodeArray;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.compilers.opt.runtimesupport.OptEncodedCallSiteTree;
import org.jikesrvm.compilers.opt.runtimesupport.OptMachineCodeMap;
import org.jikesrvm.jni.JNIEnvironment;
import org.jikesrvm.mm.mminterface.CollectorThread;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.mm.mminterface.ThreadContext;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.osr.ObjectHolder;
import org.jikesrvm.runtime.BootRecord;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.runtime.Time;
import org.jikesrvm.tuningfork.Feedlet;
import org.jikesrvm.tuningfork.TraceEngine;
import org.jikesrvm.util.Services;
import org.jikesrvm.util.UnimplementedError;
import org.vmmagic.pragma.BaselineNoRegisters;
import org.vmmagic.pragma.BaselineSaveLSRegisters;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.NoCheckStore;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NoOptCompile;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.pragma.UnpreemptibleNoWarn;
import org.vmmagic.pragma.Untraced;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * A generic java thread's execution context.
 * <p>
 * Threads use a state machine to indicate to other threads, as well as VM
 * services, how this thread should be treated in the case of an asynchronous
 * request, for example in the case of GC.  The state machine uses the
 * following states:
 * <ul>
 * <li>NEW</li>
 * <li>IN_JAVA</li>
 * <li>IN_NATIVE</li>
 * <li>IN_JNI</li>
 * <li>IN_JAVA_TO_BLOCK</li>
 * <li>BLOCKED_IN_NATIVE</li>
 * <li>BLOCKED_IN_JNI</li>
 * <li>TERMINATED</li>
 * </ul>
 * The following state transitions are legal:
 * <ul>
 * <li>NEW to IN_JAVA: occurs when the thread is actually started.  At this
 *     point it is safe to expect that the thread will reach a safe point in
 *     some bounded amount of time, at which point it will have a complete
 *     execution context, and this will be able to have its stack traces by GC.</li>
 * <li>IN_JAVA to IN_JAVA_TO_BLOCK: occurs when an asynchronous request is
 *     made, for example to stop for GC, do a mutator flush, or do an isync on PPC.</li>
 * <li>IN_JAVA to IN_NATIVE: occurs when the code opts to run in privileged mode,
 *     without synchronizing with GC.  This state transition is only performed by
 *     HeavyCondLock, in cases where the thread is about to go idle while waiting
 *     for notifications (such as in the case of park, wait, or sleep).</li>
 * <li>IN_JAVA to IN_JNI: occurs in response to a JNI downcall, or return from a JNI
 *     upcall.</li>
 * <li>IN_JAVA_TO_BLOCK to BLOCKED_IN_NATIVE: occurs when a thread that had been
 *     asked to perform an async activity decides to go idle instead.  This state
 *     always corresponds to a notification being sent to other threads, letting
 *     them know that this thread is idle.  When the thread is idle, any asynchronous
 *     requests (such as mutator flushes) can instead be performed on behalf of this
 *     thread by other threads, since this thread is guaranteed not to be running
 *     any user Java code, and will not be able to return to running Java code without
 *     first blocking, and waiting to be unblocked (see BLOCKED_IN_NATIVE to IN_JAVA
 *     transition.</li>
 * <li>IN_JAVA_TO_BLOCK to BLOCKED_IN_JNI: occurs when a thread that had been
 *     asked to perform an async activity decides to make a JNI downcall, or return
 *     from a JNI upcall, instead.  In all other regards, this is identical to the
 *     IN_JAVA_TO_BLOCK to BLOCKED_IN_NATIVE transition.</li>
 * <li>IN_NATIVE to IN_JAVA: occurs when a thread returns from idling or running
 *     privileged code to running Java code.</li>
 * <li>BLOCKED_IN_NATIVE to IN_JAVA: occurs when a thread that had been asked to
 *     perform an async activity while running privileged code or idling decides to
 *     go back to running Java code.  The actual transition is preceded by the
 *     thread first performing any requested actions (such as mutator flushes) and
 *     waiting for a notification that it is safe to continue running (for example,
 *     the thread may wait until GC is finished).</li>
 * <li>IN_JNI to IN_JAVA: occurs when a thread returns from a JNI downcall, or makes
 *     a JNI upcall.</li>
 * <li>BLOCKED_IN_JNI to IN_JAVA: same as BLOCKED_IN_NATIVE to IN_JAVA, except that
 *     this occurs in response to a return from a JNI downcall, or as the thread
 *     makes a JNI upcall.</li>
 * <li>IN_JAVA to TERMINATED: the thread has terminated, and will never reach any
 *     more safe points, and thus will not be able to respond to any more requests
 *     for async activities.</li>
 * </ul>
 * Observe that the transitions from BLOCKED_IN_NATIVE and BLOCKED_IN_JNI to IN_JAVA
 * constitute a safe point.  Code running in BLOCKED_IN_NATIVE or BLOCKED_IN_JNI is
 * "GC safe" but is not quite at a safe point; safe points are special in that
 * they allow the thread to perform async activities (such as mutator flushes or
 * isyncs), while GC safe code will not necessarily perform either.
 *
 * @see org.jikesrvm.mm.mminterface.CollectorThread
 * @see FinalizerThread
 * @see org.jikesrvm.adaptive.measurements.organizers.Organizer
 */
@Uninterruptible
@NonMoving
public final class RVMThread extends ThreadContext {
  /*
   * debug and statistics
   */
  /** Trace thread blockage */
  protected static final boolean traceBlock = false;

  /** Trace when a thread is really blocked */
  protected static final boolean traceReallyBlock = false || traceBlock;

  protected static final boolean traceAboutToTerminate = false;

  protected static final boolean dumpStackOnBlock = false; // DANGEROUS! can lead to crashes!

  protected static final boolean traceBind = false;

  /** Trace thread start/stop */
  protected static final boolean traceAcct = false;

  /** Trace execution */
  protected static final boolean trace = false;

  /** Trace thread termination */
  private static final boolean traceTermination = false;

  /** Trace adjustments to stack size */
  private static final boolean traceAdjustments = false;

  /** Trace thread priority */
  private static final boolean tracePriority = false;

  /** Never kill threads.  Useful for testing bugs related to interaction of
      thread death with for example MMTk.  For production, this should never
      be set to true. */
  private static final boolean neverKillThreads = false;

  /** Generate statistics? */
  private static final boolean STATS = Lock.STATS;

  /** Number of wait operations */
  static long waitOperations;

  /** Number of timed wait operations */
  static long timedWaitOperations;

  /** total number of milliseconds this thread has waited */
  static long totalWaitTime;

  /** start time of the last wait */
  static long waitTimeStart;

  /** Number of notify operations */
  static int notifyOperations;

  /** Number of notifyAll operations */
  static int notifyAllOperations;

  public static final boolean ALWAYS_LOCK_ON_STATE_TRANSITION = false;

  /*
   * definitions for thread status for interaction of Java-native transitions
   * and requests for threads to stop.  THESE ARE PRIVATE TO THE SCHEDULER, and
   * are only used deep within the stack.
   * Note: If you change the assignments, update READABLE_EXEC_STATUS to ensure
   * correct debug output.
   */
  /**
   * Thread has not yet started. This state holds right up until just before we
   * call pthread_create().
   */
  public static final int NEW = 0;

  /** Thread is executing "normal" Java bytecode */
  public static final int IN_JAVA = 1;

  /**
   * A state used by the scheduler to mark that a thread is in privileged code
   * that does not need to synchronize with the collector.  This is a special
   * state, similar to the IN_JNI state but requiring different interaction with
   * the collector (as there is no JNI stack frame, the registers have to be
   * saved in contextRegisters).  As well, this state should only be entered
   * from privileged code in the org.jikesrvm.scheduler package.  Typically,
   * this state is entered using a call to enterNative() just prior to idling
   * the thread; though it would not be wrong to enter it prior to any other
   * long-running activity that does not require interaction with the GC.
   */
  public static final int IN_NATIVE = 2;

  /**
   * Same as IN_NATIVE, except that we're executing JNI code and thus have a
   * JNI stack frame and JNI environment, and thus the GC can load registers
   * from there rather than using contextRegisters.
   */
  public static final int IN_JNI = 3;

  /**
   * thread is in Java code but is expected to block. the transition from IN_JAVA
   * to IN_jAVA_TO_BLOCK happens as a result of an asynchronous call by the GC
   * or any other internal VM code that requires this thread to perform an
   * asynchronous activity (another example is the request to do an isync on PPC).
   * the point is that we're waiting for the thread to reach a safe point and
   * expect this to happen in bounded time; but if the thread were to escape to
   * native we want to know about it. thus, transitions into native code while
   * in the IN_JAVA_TO_BLOCK state result in a notification (broadcast on the
   * thread's monitor) and a state change to BLOCKED_IN_NATIVE. Observe that it
   * is always safe to conservatively change IN_JAVA to IN_JAVA_TO_BLOCK.
   */
  public static final int IN_JAVA_TO_BLOCK = 4;

  /**
   * thread is in native code, and is to block before returning to Java code.
   * the transition from IN_NATIVE to BLOCKED_IN_NATIVE happens as a result
   * of an asynchronous call by the GC or any other internal VM code that
   * requires this thread to perform an asynchronous activity (another example
   * is the request to do an isync on PPC).  as well, code entering privileged
   * code that would otherwise go from IN_JAVA to IN_NATIVE will go to
   * BLOCKED_IN_NATIVE instead, if the state was IN_JAVA_TO_BLOCK.
   * <p>
   * the point of this state is that the thread is guaranteed not to execute
   * any Java code until:
   * <ol>
   * <li>The state changes to IN_NATIVE, and
   * <li>The thread gets a broadcast on its monitor.
   * </ol>
   * Observe that it is always safe to conservatively change IN_NATIVE to
   * BLOCKED_IN_NATIVE.
   */
  public static final int BLOCKED_IN_NATIVE = 5;

  /**
   * like BLOCKED_IN_NATIVE, but indicates that the thread is in JNI rather than
   * VM native code.
   */
  public static final int BLOCKED_IN_JNI = 6;

  /**
   * Thread has died. As in, it's no longer executing any Java code and will
   * never do so in the future. Once this is set, the GC will no longer mark any
   * part of the thread as live; the thread's stack will be deleted. Note that
   * this is distinct from the aboutToTerminate state.
   */
  public static final int TERMINATED = 7;

  /** Not actually a state but just a marker. */
  public static final int LAST_EXEC_STATUS = 8;

  private static final String[] READABLE_EXEC_STATUS =
    {"NEW", "IN_JAVA", "IN_NATIVE", "IN_JNI", "IN_JAVA_TO_BLOCK",
      "BLOCKED_IN_NATIVE", "BLOCKED_IN_JNI", "TERMINATED", "LAST_EXEC_STATUS"};

  public static boolean notRunning(int state) {
    return state == NEW || state == TERMINATED;
  }

  /** Registers used by return barrier trampoline */
  @Entrypoint
  private AbstractRegisters trampolineRegisters = ArchitectureFactory.createRegisters();

  /** Return address of stack frame hijacked by return barrier */
  @Entrypoint
  private Address hijackedReturnAddress;

  /** Callee frame pointer for stack frame hijacked by return barrier */
  private Address hijackedReturnCalleeFp = Address.zero();

  /** Caller frame pointer for stack frame hijacked by return barrier */
  private Address hijackedReturnCallerFp = StackFrameLayout.getStackFrameSentinelFP();

  /** @return the callee frame pointer for the stack frame hijacked by the return barrier */
  public Address getHijackedReturnCalleeFp() {
    return hijackedReturnCalleeFp;
  }

  /** debugging flag for return barrier trampoline */
  public static final boolean DEBUG_STACK_TRAMPOLINE = false;

  /** pointer to bridge code for return barrier trampoline */
  public static CodeArray stackTrampolineBridgeInstructions;

  /**
   * Thread state. Indicates if the thread is running, and if so, what mode of
   * execution it is using (Java, VM native, or JNI)
   */
  @Entrypoint
  private int execStatus;

  public int getExecStatus() {
    observeExecStatus();
    return execStatus;
  }

  private boolean attemptFastExecStatusTransition(int oldState,
                                                  int newState) {
    if (Synchronization.tryCompareAndSwap(
          this,
          Entrypoints.execStatusField.getOffset(),
          oldState,
          newState)) {
      observeStateTransition(oldState,newState);
      return true;
    } else {
      return false;
    }
  }

  // call this only when holding the lock or if you really know what you're
  // doing.
  private void setExecStatus(int newState) {
    observeStateTransition(execStatus,newState);
    execStatus = newState;
  }

  /**
   * Is the thread about to terminate? Protected by the thread's monitor. Note
   * that this field is not set atomically with the entering of the thread onto
   * the aboutToTerminate array - in fact it happens before that. When this
   * field is set to true, the thread's stack will no longer be scanned by GC.
   * Note that this is distinct from the TERMINATED state.
   */
  // FIXME: there should be an execStatus state called TERMINATING that
  // corresponds to this. that would make a lot more sense.
  private boolean isAboutToTerminate;

  public boolean getIsAboutToTerminate() {
    return isAboutToTerminate;
  }

  /** Is this thread in the process of blocking? */
  boolean isBlocking;

  /**
   * Is the thread no longer executing user code? Protected by the Java monitor
   * associated with the Thread object.
   */
  boolean isJoinable;

  /**
   * Link pointer for queues (specifically ThreadQueue). A thread can only be
   * on one such queue at a time. The queue that a thread is on is indicated by
   * <code>queuedOn</code>.
   */
  @Untraced
  RVMThread next;

  /**
   * The queue that the thread is on, or null if the thread is not on a queue
   * (specifically ThreadQueue). If the thread is on such a queue, the
   * <code>next</code> field is used as a link pointer.
   */
  @Untraced
  volatile ThreadQueue queuedOn;

  /**
   * @return True if this thread is currently on a queue.
   */
  public boolean isOnQueue() {
    return queuedOn != null;
  }

  /**
   * Used to handle contention for spin locks
   */
  @Untraced
  SpinLock awaitingSpinLock;

  @Untraced
  RVMThread contenderLink;

  /**
   * java.lang.Thread wrapper for this Thread. Not final so it may be assigned
   * during booting
   */
  private Thread thread;

  /** Name of the thread (can be changed during execution) */
  private String name;

  /**
   * The virtual machine terminates when the last non-daemon (user) thread
   * terminates.
   */
  protected boolean daemon;

  /**
   * Scheduling priority for this thread. Note that:
   * {@link java.lang.Thread#MIN_PRIORITY} &lt;= priority &lt;=
   * {@link java.lang.Thread#MAX_PRIORITY}.
   */
  private int priority;

  /**
   * Index of this thread in {@link #threadBySlot}[]. This value must be non-zero
   * because it is shifted and used in {@link Object} lock ownership tests.
   */
  @Entrypoint
  public int threadSlot;

  public int lockingId;

  /**
   * Non-null indicates this is a system thread, that is one used by the system and as such
   * doesn't have a Runnable...
   */
  private final SystemThread systemThread;

  /**
   * The boot thread, can't be final so as to allow initialization during boot
   * image writing.
   */
  @Entrypoint
  public static RVMThread bootThread;

  /**
   * Is the threading system initialized?
   */
  public static boolean threadingInitialized = false;

  /**
   * Number of timer ticks we've seen
   */
  public static long timerTicks;

  private long yieldpointsTaken;

  private long yieldpointsTakenFully;

  private long nativeEnteredBlocked;

  private long jniEnteredBlocked;

  /**
   * Assertion checking while manipulating raw addresses -- see
   * {@link VM#disableGC()}/{@link VM#enableGC()}. A value of "true" means
   * it's an error for this thread to call "new". This is only used for
   * assertion checking; we do not bother to set it when
   * {@link VM#VerifyAssertions} is false.
   */
  private boolean disallowAllocationsByThisThread;

  /**
   * Counts the depth of outstanding calls to {@link VM#disableGC()}. If this
   * is set, then we should also have {@link #disallowAllocationsByThisThread}
   * set. The converse also holds.
   */
  private int disableGCDepth = 0;

  public int barriersEntered = 0;

  public int barriersExited = 0;

  /**
   * Execution stack for this thread.
   */
  @Entrypoint
  private byte[] stack;

  /** The {@link Address} of the guard area for {@link #stack}. */
  @Entrypoint
  public Address stackLimit;

  /* --------- BEGIN IA-specific fields. NOTE: NEED TO REFACTOR --------- */
  // On powerpc, these values are in dedicated registers,
  // we don't have registers to burn on IA32, so we indirect
  // through the TR register to get them instead.
  /**
   * FP for current frame, saved in the prologue of every method
   */
  @Entrypoint
  Address framePointer;

  /**
   * "hidden parameter" for interface invocation thru the IMT
   */
  @Entrypoint
  int hiddenSignatureId;

  /**
   * "hidden parameter" from ArrayIndexOutOfBounds trap to C trap handler
   */
  @Entrypoint
  int arrayIndexTrapParam;

  /* --------- END IA-specific fields. NOTE: NEED TO REFACTOR --------- */

  /**
   * Is the next taken yieldpoint in response to a request to perform OSR?
   */
  public boolean yieldToOSRRequested;

  /**
   * Is CBS enabled for 'call' yieldpoints?
   */
  public boolean yieldForCBSCall;

  /**
   * Is CBS enabled for 'method' yieldpoints?
   */
  public boolean yieldForCBSMethod;

  /**
   * Number of CBS samples to take in this window
   */
  public int numCBSCallSamples;

  /**
   * Number of call yieldpoints between CBS samples
   */
  public int countdownCBSCall;

  /**
   * round robin starting point for CBS samples
   */
  public int firstCBSCallSample;

  /**
   * Number of CBS samples to take in this window
   */
  public int numCBSMethodSamples;

  /**
   * Number of counter ticks between CBS samples
   */
  public int countdownCBSMethod;

  /**
   * round robin starting point for CBS samples
   */
  public int firstCBSMethodSample;

  /* --------- BEGIN PPC-specific fields. NOTE: NEED TO REFACTOR --------- */
  /**
   * flag indicating this processor needs to execute a memory synchronization
   * sequence Used for code patching on SMP PowerPCs.
   */
  public boolean codePatchSyncRequested;

  /* --------- END PPC-specific fields. NOTE: NEED TO REFACTOR --------- */

  /**
   * For builds using counter-based sampling. This field holds a
   * processor-specific counter so that it can be updated efficiently on SMP's.
   */
  @Entrypoint
  public int thread_cbs_counter;

  /**
   * Should this thread yield at yieldpoints? A value of: 1 means "yes"
   * (yieldpoints enabled) &lt;= 0 means "no" (yieldpoints disabled)
   */
  private int yieldpointsEnabledCount;

  /**
   * Is a takeYieldpoint request pending on this thread?
   */
  boolean yieldpointRequestPending;

  /**
   * Are we at a yieldpoint right now?
   */
  boolean atYieldpoint;

  /**
   * Is there a flush request for this thread? This is handled via a soft
   * handshake.
   */
  public boolean flushRequested;

  /**
   * Is a soft handshake requested? Logically, this field is protected by the
   * thread's monitor - but it is typically only mucked with when both the
   * thread's monitor and the softHandshakeDataLock are held.
   */
  public boolean softHandshakeRequested;

  /**
   * How many threads have not yet reached the soft handshake? (protected by
   * softHandshakeDataLock)
   */
  public static int softHandshakeLeft;

  /**
   * Lock that protects soft handshake fields.
   */
  public static Monitor softHandshakeDataLock;

  /**
   * Lock that prevents multiple (soft or hard) handshakes from proceeding
   * concurrently.
   */
  public static Monitor handshakeLock;

  /**
   * Place to save register state when this thread is not actually running.
   */
  @Entrypoint(fieldMayBeFinal = true)
  @Untraced
  public final AbstractRegisters contextRegisters;
  @SuppressWarnings("unused")
  @Entrypoint(fieldMayBeFinal = true)
  private final AbstractRegisters contextRegistersShadow;

  /**
   * Place to save register state when this thread is not actually running.
   */
  @Entrypoint(fieldMayBeFinal = true)
  @Untraced
  public final AbstractRegisters contextRegistersSave;
  @SuppressWarnings("unused")
  @Entrypoint(fieldMayBeFinal = true)
  private final AbstractRegisters contextRegistersSaveShadow;

  /**
   * Place to save register state during hardware(C signal trap handler) or
   * software (RuntimeEntrypoints.athrow) trap handling.
   */
  @Entrypoint(fieldMayBeFinal = true)
  @Untraced
  private final AbstractRegisters exceptionRegisters;
  @SuppressWarnings("unused")
  @Entrypoint(fieldMayBeFinal = true)
  private final AbstractRegisters exceptionRegistersShadow;

  /** Count of recursive uncaught exceptions, we need to bail out at some point */
  private int uncaughtExceptionCount = 0;

  /**
   * A cached free lock. Not a free list; this will only ever contain 0 or 1
   * locks!
   */
  public Lock cachedFreeLock;

  /*
   * Wait/notify fields
   */

  /**
   * Place to save/restore this thread's monitor state during
   * {@link Object#wait} and {@link Object#notify}.
   */
  protected Object waitObject;

  /** Lock recursion count for this thread's monitor. */
  protected int waitCount;

  /**
   * Should the thread suspend?
   */
  boolean shouldSuspend;

  /**
   * An integer token identifying the last suspend request
   */
  int shouldSuspendToken;

  /**
   * Is the thread suspended?
   */
  boolean isSuspended;

  /**
   * Should the thread block for handshake?
   */
  boolean shouldBlockForHandshake;

  /**
   * Is the thread blocked for handshake?
   */
  boolean isBlockedForHandshake;

  /**
   * Should the thread block for a thread-to-thread communication?
   */
  boolean shouldBlockForGC;

  /**
   * Is the thread blocked for thread-to-thread communication?
   */
  boolean isBlockedForGC;

  /**
   * An integer token identifying the last stack trace request
   */
  int shouldBlockForStackTraceToken;

  /**
   * Should the thread block so that another thread can get a stack trace for it?
   */
  boolean shouldBlockForStackTrace;

  /**
   * Is the thread blocked because another thread wants to get a stack trace for it?
   */
  boolean isBlockedForStackTrace;



  /**
   * A block adapter specifies the reason for blocking or unblocking a thread.  A thread
   * remains blocked so long as any of the block adapters say that it should be blocked.
   * Block adapters are statically allocated, and store their state in instance fields of
   * RVMThread.
   */
  @Uninterruptible
  @NonMoving
  public abstract static class BlockAdapter {

    /**
     * @param t a thread
     * @return whether the given thread should be blocked for this block
     *  adapter. If {@code true}, the thread is guaranteed to block.
     */
    abstract boolean isBlocked(RVMThread t);

    /**
     * Specifies that the thread is either blocked {@code (value == true)} or not
     * blocked {@code (value == false)} for this block adapter.  This call
     * indicates a statement of fact by the thread itself - it's used either
     * to acknowledge a block request (see {@link #hasBlockRequest(RVMThread)}
     * below) or to respond to a request to unblock.
     * @param t the thread
     * @param value the new value of the status for blocking as described above
     */
    abstract void setBlocked(RVMThread t, boolean value);

    /**
     * Requests that the thread block, for this block adapter, at its earliest
     * convenience.
     * <p>
     * Called from RVMThread.block() and associated methods.  Some block adapters
     * allow for multiple requests to block; in that case this will return a
     * "token" that can be passed to hasBlockRequest() to check, not only whether
     * there is a block request, but whether that block request is still
     * associated with a particular call to requestBlock().  This is used to
     * prevent a suspend() call from stalling due to a concurrent resume() and
     * second suspend(). Note that most block adapers don't care about this
     * scenario, and will just return 0 (or some other meaningless number) here.
     *
     * @param t the thread that needs to block
     * @return a token as described above
     */
    abstract int requestBlock(RVMThread t);

    /**
     * @param t the thread
     * @return whether the thread has a request to block for this
     *  block adapter
     */
    abstract boolean hasBlockRequest(RVMThread t);

    /**
     * @param t the thread to check for block requests
     * @param token a token, see {@link #requestBlock(RVMThread)}
     * @return whether the thread has a block request
     *  associated with the given requestBlock() call
     */
    abstract boolean hasBlockRequest(RVMThread t, int token);

    /**
     * Clears any blocking requests for the thread.
     * @param t thread whose block requests will be cleared
     */
    abstract void clearBlockRequest(RVMThread t);
  }

  @Uninterruptible
  @NonMoving
  public static class SuspendBlockAdapter extends BlockAdapter {
    @Override
    boolean isBlocked(RVMThread t) {
      return t.isSuspended;
    }

    @Override
    void setBlocked(RVMThread t, boolean value) {
      t.isSuspended = value;
    }

    @Override
    int requestBlock(RVMThread t) {
      if (t.isSuspended || t.shouldSuspend) {
        return t.shouldSuspendToken;
      } else {
        t.shouldSuspend = true;
        t.shouldSuspendToken++;
        return t.shouldSuspendToken;
      }
    }

    @Override
    boolean hasBlockRequest(RVMThread t) {
      return t.shouldSuspend;
    }

    @Override
    boolean hasBlockRequest(RVMThread t, int token) {
      return t.shouldSuspend && t.shouldSuspendToken == token;
    }

    @Override
    void clearBlockRequest(RVMThread t) {
      t.shouldSuspend = false;
    }
  }

  public static final SuspendBlockAdapter suspendBlockAdapter = new SuspendBlockAdapter();

  @Uninterruptible
  @NonMoving
  public static class ThreadStackTraceBlockAdapter extends BlockAdapter {
    @Override
    boolean isBlocked(RVMThread t) {
      return t.isBlockedForStackTrace;
    }

    @Override
    void setBlocked(RVMThread t, boolean value) {
      t.isBlockedForStackTrace = value;
    }

    @Override
    int requestBlock(RVMThread t) {
      if (t.isBlockedForStackTrace || t.shouldBlockForStackTrace) {
        return t.shouldBlockForStackTraceToken;
      } else {
        t.shouldBlockForStackTrace = true;
        t.shouldBlockForStackTraceToken++;
        return t.shouldBlockForStackTraceToken;
      }
    }

    @Override
    boolean hasBlockRequest(RVMThread t) {
      return t.shouldBlockForStackTrace;
    }

    @Override
    boolean hasBlockRequest(RVMThread t, int token) {
      return t.shouldBlockForStackTrace && t.shouldBlockForStackTraceToken == token;
    }

    @Override
    void clearBlockRequest(RVMThread t) {
      t.shouldBlockForStackTrace = false;
    }
  }

  public static final ThreadStackTraceBlockAdapter stackTraceBlockAdapter = new ThreadStackTraceBlockAdapter();


  @Uninterruptible
  @NonMoving
  public static class HandshakeBlockAdapter extends BlockAdapter {
    @Override
    boolean isBlocked(RVMThread t) {
      return t.isBlockedForHandshake;
    }

    @Override
    void setBlocked(RVMThread t, boolean value) {
      t.isBlockedForHandshake = value;
    }

    @Override
    int requestBlock(RVMThread t) {
      if (!t.isBlockedForHandshake) {
        t.shouldBlockForHandshake = true;
      }
      return 0;
    }

    @Override
    boolean hasBlockRequest(RVMThread t) {
      return t.shouldBlockForHandshake;
    }

    @Override
    boolean hasBlockRequest(RVMThread t, int token) {
      return t.shouldBlockForHandshake;
    }

    @Override
    void clearBlockRequest(RVMThread t) {
      t.shouldBlockForHandshake = false;
    }
  }

  public static final HandshakeBlockAdapter handshakeBlockAdapter = new HandshakeBlockAdapter();

  @Uninterruptible
  @NonMoving
  public static class GCBlockAdapter extends BlockAdapter {
    @Override
    boolean isBlocked(RVMThread t) {
      return t.isBlockedForGC;
    }

    @Override
    void setBlocked(RVMThread t, boolean value) {
      t.isBlockedForGC = value;
    }

    @Override
    int requestBlock(RVMThread t) {
      if (!t.isBlockedForGC) {
        t.shouldBlockForGC = true;
      }
      return 0;
    }

    @Override
    boolean hasBlockRequest(RVMThread t) {
      return t.shouldBlockForGC;
    }

    @Override
    boolean hasBlockRequest(RVMThread t, int token) {
      return t.shouldBlockForGC;
    }

    @Override
    void clearBlockRequest(RVMThread t) {
      t.shouldBlockForGC = false;
    }
  }

  public static final GCBlockAdapter gcBlockAdapter = new GCBlockAdapter();

  static final BlockAdapter[] blockAdapters = new BlockAdapter[] {
    suspendBlockAdapter, handshakeBlockAdapter, gcBlockAdapter,
    stackTraceBlockAdapter };

  /**
   * An enumeration that describes the different manners in which a thread might
   * be voluntarily waiting.
   */
  protected enum Waiting {
    /** The thread is not waiting at all. In fact it's running. */
    RUNNABLE,
    /** The thread is waiting without a timeout. */
    WAITING,
    /** The thread is waiting with a timeout. */
    TIMED_WAITING
  }

  /**
   * Accounting of whether or not a thread is waiting (in the Java thread state
   * sense), and if so, how it's waiting.
   * <p>
   * Invariant: the RVM runtime does not ever use this field for any purpose
   * other than updating it so that the java.lang.Thread knows the state. Thus,
   * if you get sloppy with this field, the worst case outcome is that some Java
   * program that queries the thread state will get something other than what it
   * may or may not have expected.
   */
  protected Waiting waiting;

  /**
   * Exception to throw in this thread at the earliest possible point.
   */
  Throwable asyncThrowable;

  /**
   * Has the thread been interrupted?
   */
  boolean hasInterrupt;

  /**
   * Should the next executed yieldpoint be taken? Can be true for a variety of
   * reasons. See RVMThread.yieldpoint
   * <p>
   * To support efficient sampling of only prologue/epilogues we also encode
   * some extra information into this field. 0 means that the yieldpoint should
   * not be taken. &gt;0 means that the next yieldpoint of any type should be taken
   * &lt;0 means that the next prologue/epilogue yieldpoint should be taken
   * <p>
   * Note the following rules:
   * <ol>
   * <li>If takeYieldpoint is set to 0 or -1 it is perfectly safe to set it to
   * 1; this will have almost no effect on the system. Thus, setting
   * takeYieldpoint to 1 can always be done without acquiring locks.</li>
   * <li>Setting takeYieldpoint to any value other than 1 should be done while
   * holding the thread's monitor().</li>
   * <li>The exception to rule (2) above is that the yieldpoint itself sets
   * takeYieldpoint to 0 without holding a lock - but this is done after it
   * ensures that the yieldpoint is deferred by setting yieldpointRequestPending
   * to true.
   * </ol>
   */
  @Entrypoint
  public int takeYieldpoint;

  /**
   * How many times has the "timeslice" expired? This is only used for profiling
   * and OSR (in particular base-to-opt OSR).
   */
  public int timeSliceExpired;

  /** Is a running thread permitted to ignore the next park request */
  private boolean parkingPermit;

  /*
   * JNI fields
   */

  /**
   * Cached JNI environment for this thread
   */
  @Entrypoint
  @Untraced
  private JNIEnvironment jniEnv;
  @SuppressWarnings("unused")
  private JNIEnvironment jniEnvShadow;

  /** Used by GC to determine collection success */
  private boolean physicalAllocationFailed;

  /** Used by GC to determine collection success */
  private int collectionAttempt;

  /** The OOME to throw */
  private static OutOfMemoryError outOfMemoryError;

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
   * Only used by OSR when VM.BuildForAdaptiveSystem. Declared as an Object to
   * cut link to adaptive system. Ugh.
   */
  public final Object /* OnStackReplacementEvent */onStackReplacementEvent;

  /**
   * The flag indicates whether this thread is waiting for on stack replacement
   * before being rescheduled.
   */
  // flags should be packaged or replaced by other solutions
  public boolean isWaitingForOsr = false;

  /**
   * Before call new instructions, we need a bridge to recover register states
   * from the stack frame.
   */
  public CodeArray bridgeInstructions = null;

  /** Foo frame pointer offset */
  public Offset fooFPOffset = Offset.zero();

  /** Thread switch frame pointer offset */
  public Offset tsFPOffset = Offset.zero();

  /**
   * Flag to synchronize with osr organizer, the trigger sets osr requests the
   * organizer clear the requests
   */
  public boolean requesting_osr = false;

  /**
   * Flag to indicate that the last OSR request is done.
   */
  public boolean osr_done = false;

  /**
   * The number of processors to use.
   */
  public static int availableProcessors = -1;

  /**
   * Thread handle. Currently stores pthread_t, which we assume to be no larger
   * than a pointer-sized word.
   */
  public Word pthread_id;

  /**
   * Thread priority handle.  Used when manipulating the threads priority.
   * This may be different from pthread_id.
   */
  public Word priority_handle;

  /**
   * Scratch area for use for gpr &lt;=&gt; fpr transfers by PPC baseline compiler.
   * Used to transfer x87 to SSE registers on IA32
   */
  @SuppressWarnings({ "unused" })
  @Entrypoint
  private double scratchStorage;

  /**
   * Current index of this thread in the threads array. This may be changed by
   * another thread, but only while the acctLock is held.
   */
  private int threadIdx;

  /**
   * Is the system in the process of shutting down (has System.exit been called)
   */
  private static boolean systemShuttingDown = false;

  /**
   * Flag set by external signal to request debugger activation at next thread
   * switch. See also: sysSignal.c
   */
  @Entrypoint
  public static volatile boolean debugRequested;

  public volatile boolean asyncDebugRequestedForThisThread;

  /**
   * The latch for reporting profile data.
   */
  public static Latch doProfileReport;

  /** Number of times dump stack has been called recursively */
  protected int inDumpStack = 0;

  /** Is this a "registered mutator?" */
  public boolean activeMutatorContext = false;

  /** Lock used for dumping stack and such. */
  public static Monitor dumpLock;

  /** In dump stack and dying */
  protected static boolean exitInProgress = false;

  /** Extra debug from traces */
  protected static final boolean traceDetails = false;

  /** Toggle display of frame pointer address in stack dump */
  private static final boolean SHOW_FP_IN_STACK_DUMP = true;

  /** Index of thread in which "VM.boot()" runs */
  public static final int PRIMORDIAL_THREAD_INDEX = 1;

  /** Maximum number of RVMThread's that we can support. */
  public static final int LOG_MAX_THREADS = 10;

  public static final int MAX_THREADS = 1 << LOG_MAX_THREADS;

  /**
   * thread array - all threads are stored in this array according to their
   * threadSlot.
   */
  public static RVMThread[] threadBySlot = new RVMThread[MAX_THREADS];

  /**
   * Per-thread monitors. Note that this array is statically initialized. It
   * starts out all null. When a new thread slot is allocated, a monitor is
   * added for that slot.
   * <p>
   * Question: what is the outcome, if any, of taking a yieldpoint while holding
   * this lock?
   * <ol>
   * <li>If there is a GC request we will wait on this condition variable and
   * thus release it. Someone else might then acquire the lock before realizing
   * that there is a GC request and then do bad things.</li>
   * <li>The yieldpoint might acquire another thread's monitor. Thus, two
   * threads may get into lock inversion with each other.</li>
   * <li>???</li>
   * </ol>
   */
  private static final NoYieldpointsMonitor[] monitorBySlot = new NoYieldpointsMonitor[MAX_THREADS];

  private static final Monitor[] communicationLockBySlot = new Monitor[MAX_THREADS];

  /**
   * Lock (mutex) used for creating and destroying threads as well as thread
   * accounting.  This mutex should not be held while thread monitors (see monitorBySlot)
   * are held.  Use this mutex only to protect accesses to:
   * <ul>
   * <li>the global thread lists, such as threadBySlot, aboutToTerminate, threads, and
   *     freeLots</li>
   * <li>threadIdx field of RVMThread</li>
   * <li>numThreads, numActiveThreads, numActiveSystemThreads, numActiveDaemons static fields of RVMThread</li>
   * </ul>
   */
  public static NoYieldpointsMonitor acctLock;

  /**
   * Lock (mutex) used for servicing debug requests.
   */
  public static NoYieldpointsMonitor debugLock;

  /**
   * Lock used for generating debug output.
   */
  private static NoYieldpointsMonitor outputLock;

  /**
   * Thread slots of threads that are about to terminate.  This must be
   * an int array because it's accessed from code that cannot have
   * barriers.
   */
  private static final int[] aboutToTerminate = new int[MAX_THREADS];

  /**
   * Number of threads that are about to terminate.
   */
  private static int aboutToTerminateN;

  /**
   * Free thread slots
   */
  private static final int[] freeSlots = new int[MAX_THREADS];

  /**
   * Number of free thread slots.
   */
  private static int freeSlotN;

  /**
   * When there are no thread slots on the free list, this is the next one to
   * use.
   */
  public static int nextSlot = 2;

  /**
   * Number of threads in the system (some of which may not be active).
   */
  public static int numThreads;

  /**
   * Packed and unordered array or active threads. Only entries in the range 0
   * to numThreads-1 (inclusive) are defined. Note that it should be possible to
   * scan this array without locking and get all of the threads - but only if
   * you scan downward and place a memory fence between loads.
   * <p>
   * Note further that threads remain in this array even after the Java
   * libraries no longer consider the thread to be active.
   */
  public static final RVMThread[] threads = new RVMThread[MAX_THREADS];

  /**
   * Preallocated array for use in handshakes. Protected by handshakeLock.
   */
  public static final RVMThread[] handshakeThreads = new RVMThread[MAX_THREADS];

  /**
   * Preallocated array for use in debug requested. Protected by debugLock.
   */
  public static final RVMThread[] debugThreads = new RVMThread[MAX_THREADS];

  /**
   * Number of active threads in the system.
   */
  private static int numActiveThreads;

  /**
   * Number of active system threads. Necessary for JMX because
   * it's better to not count system threads to be consistent
   * with other VMs.
   */
  private static int numActiveSystemThreads;

  /**
   * Number of active daemon threads.
   */
  private static int numActiveDaemons;

  /*
   * TuningFork instrumentation support
   */
  /**
   * The Feedlet instance for this thread to use to make addEvent calls.
   */
  public Feedlet feedlet;

  /**
   * @param slot the thread's slot
   * @return a NoYieldpointsCondLock for a given thread slot.
   */
  static NoYieldpointsMonitor monitorForSlot(int slot) {
    NoYieldpointsMonitor result = monitorBySlot[slot];
    if (VM.VerifyAssertions)
      VM._assert(result != null);
    return result;
  }

  /**
   * @return the NoYieldpointsCondLock for this thread.
   */
  public NoYieldpointsMonitor monitor() {
    return monitorForSlot(threadSlot);
  }

  public Monitor communicationLockForSlot(int slot) {
    Monitor result = communicationLockBySlot[slot];
    if (VM.VerifyAssertions)
      VM._assert(result != null);
    return result;
  }

  public Monitor communicationLock() {
    return communicationLockForSlot(threadSlot);
  }

  /**
   * Initialize the threading subsystem for the boot image.
   */
  @Interruptible
  public static void init() {
    // Enable us to dump a Java Stack from the C trap handler to aid in
    // debugging things that
    // show up as recursive use of hardware exception registers (eg the
    // long-standing lisp bug)
    BootRecord.the_boot_record.dumpStackAndDieOffset =
      Entrypoints.dumpStackAndDieMethod.getOffset();
    Lock.init();
  }

  public void assertAcceptableStates(int expected) {
    if (VM.VerifyAssertions) {
      int curStatus = getExecStatus();
      if (curStatus != expected) {
        VM.sysWriteln("FATAL ERROR: unexpected thread state.");
        VM.sysWriteln("Expected: ",READABLE_EXEC_STATUS[expected]);
        VM.sysWriteln("Observed: ",READABLE_EXEC_STATUS[curStatus]);
        VM._assert(curStatus == expected);
      }
    }
  }

  public void assertAcceptableStates(int expected1,int expected2) {
    if (VM.VerifyAssertions) {
      int curStatus = getExecStatus();
      if (curStatus != expected1 &&
          curStatus != expected2) {
        VM.sysWriteln("FATAL ERROR: unexpected thread state.");
        VM.sysWriteln("Expected: ",READABLE_EXEC_STATUS[expected1]);
        VM.sysWriteln("      or: ",READABLE_EXEC_STATUS[expected2]);
        VM.sysWriteln("Observed: ",READABLE_EXEC_STATUS[curStatus]);
        VM._assert(curStatus == expected1 ||
                   curStatus == expected2);
      }
    }
  }

  public void assertUnacceptableStates(int unexpected) {
    if (VM.VerifyAssertions) {
      int curStatus = getExecStatus();
      if (curStatus == unexpected) {
        VM.sysWriteln("FATAL ERROR: unexpected thread state.");
        VM.sysWriteln("Unexpected: ",READABLE_EXEC_STATUS[unexpected]);
        VM.sysWriteln("  Observed: ",READABLE_EXEC_STATUS[curStatus]);
        VM._assert(curStatus != unexpected);
      }
    }
  }

  public void assertUnacceptableStates(int unexpected1,int unexpected2) {
    if (VM.VerifyAssertions) {
      int curStatus = getExecStatus();
      if (curStatus == unexpected1 ||
          curStatus == unexpected2) {
        VM.sysWriteln("FATAL ERROR: unexpected thread state for thread", threadSlot);
        VM.sysWriteln("Unexpected: ",READABLE_EXEC_STATUS[unexpected1]);
        VM.sysWriteln("       and: ",READABLE_EXEC_STATUS[unexpected2]);
        VM.sysWriteln("  Observed: ",READABLE_EXEC_STATUS[curStatus]);
        VM._assert(curStatus != unexpected1 &&
                   curStatus != unexpected2);
      }
    }
  }

  static void bind(int cpuId) {
    if (VM.VerifyAssertions) VM._assert(sysCall.sysThreadBindSupported() == 1);
    sysCall.sysThreadBind(cpuId);
  }

  static void bindIfRequested() {
    if (VM.forceOneCPU >= 0) {
      if (traceBind) {
        VM.sysWriteln("binding thread to CPU: ",VM.forceOneCPU);
      }
      bind(VM.forceOneCPU);
    }
  }

  /**
   * Boot the threading subsystem.
   */
  @Interruptible
  // except not really, since we don't enable yieldpoints yet
  public static void boot() {
    outOfMemoryError = new OutOfMemoryError();
    dumpLock = new Monitor();
    acctLock = new NoYieldpointsMonitor();
    debugLock = new NoYieldpointsMonitor();
    outputLock = new NoYieldpointsMonitor();
    softHandshakeDataLock = new Monitor();
    handshakeLock = new Monitor();
    doProfileReport = new Latch(false);
    monitorBySlot[getCurrentThread().threadSlot] = new NoYieldpointsMonitor();
    communicationLockBySlot[getCurrentThread().threadSlot] = new Monitor();
    sysCall.sysStashVMThread(getCurrentThread());

    if (traceAcct) {
      VM.sysWriteln("boot thread at ",Magic.objectAsAddress(getCurrentThread()));
    }

    bindIfRequested();

    threadingInitialized = true;
    // Always run timer thread, so we can respond to debug requests
    new TimerThread().start();
    if (VM.BuildForAdaptiveSystem) {
      ObjectHolder.boot();
    }

    FinalizerThread.boot();
    getCurrentThread().enableYieldpoints();
    if (traceAcct) VM.sysWriteln("RVMThread booted");
  }

  /**
   * Add this thread to the termination watchlist. Called by terminating threads
   * before they finish terminating.
   */
  private void addAboutToTerminate() {
    monitor().lockNoHandshake();
    isAboutToTerminate = true;
    activeMutatorContext = false;
    monitor().broadcast();

    handleHandshakeRequest();
    deinitMutator();

    // WARNING! DANGER! Since we've set isAboutToTerminate to true, when we
    // release this lock the GC will:
    // 1) No longer scan the thread's stack (though it will *see* the
    // thread's stack and mark the stack itself as live, without scanning
    // it).
    // 2) No longer include the thread in any mutator phases ... hence the
    // need to ensure that the mutator context is flushed above.
    // 3) No longer attempt to block the thread.
    // Moreover, we can no longer do anything that leads to write barriers
    // or allocation.
    monitor().unlock();

    softRendezvous();

    acctLock.lockNoHandshake();
    aboutToTerminate[aboutToTerminateN++] = threadSlot;
    acctLock.unlock();
  }

  /**
   * Method called after processing a list of threads, or before starting a new
   * thread.  This does two things.  First, it guarantees that the thread slots
   * used by any dead threads are freed.  Second, it guarantees that each thread
   * deregisters itself from GC.  Thus, it is essential that after requesting
   * things like mutator flushes, you call this, to ensure that any threads that
   * had died before or during the mutator flush request do the Right Thing.
   */
  @NoCheckStore
  public static void processAboutToTerminate() {
    if (!neverKillThreads) {
      restart: while (true) {
        int notKilled = 0;
        acctLock.lockNoHandshake();
        for (int i = 0; i < aboutToTerminateN; ++i) {
          RVMThread t = threadBySlot[aboutToTerminate[i]];
          if (t.getExecStatus() == TERMINATED) {
            aboutToTerminate[i--] = aboutToTerminate[--aboutToTerminateN];
            acctLock.unlock();
            t.releaseThreadSlot();
            continue restart;
          } else {
            notKilled++;
          }
        }
        acctLock.unlock();
        if (notKilled > 0 && traceAboutToTerminate) {
          VM.sysWriteln("didn't kill ", notKilled, " threads");
        }
        break;
      }
    }
  }

  /**
   * Find a thread slot not in use by any other live thread and bind the given
   * thread to it. The thread's threadSlot field is set accordingly.
   */
  @Interruptible
  void assignThreadSlot() {
    if (!VM.runningVM) {
      // primordial thread
      threadSlot = 1;
      threadBySlot[1] = this;
      threads[0] = this;
      threadIdx = 0;
      numThreads = 1;
    } else {
      processAboutToTerminate();
      acctLock.lockNoHandshake();
      if (freeSlotN > 0) {
        threadSlot = freeSlots[--freeSlotN];
      } else {
        if (nextSlot == threads.length) {
          VM.sysFail("too many threads");
        }
        threadSlot = nextSlot++;
      }
      acctLock.unlock();
      // before we actually use this slot, ensure that there is a monitor
      // for it. note that if the slot doesn't have a monitor, then we
      // "own" it since we allocated it above but haven't done anything
      // with it (it's not assigned to a thread, so nobody else can touch
      // it)
      if (monitorBySlot[threadSlot] == null) {
        monitorBySlot[threadSlot] = new NoYieldpointsMonitor();
      }
      if (communicationLockBySlot[threadSlot] == null) {
        Monitor m = new Monitor();
        handshakeLock.lockWithHandshake();
        communicationLockBySlot[threadSlot] = m;
        handshakeLock.unlock();
      }
      // TODO is this actually needed? The synchronization for locks
      // should normally take care of required barriers and bar
      // code movement
      Magic.fence(); /*
                     * make sure that nobody sees the thread in any of the
                     * tables until the thread slot is inited
                     */

      acctLock.lockNoHandshake();
      threadBySlot[threadSlot] = this;

      threadIdx = numThreads++;
      threads[threadIdx] = this;

      acctLock.unlock();
    }
    lockingId = threadSlot << TL_THREAD_ID_SHIFT;
    if (traceAcct) {
      VM.sysWriteln("Thread #", threadSlot, " at ", Magic.objectAsAddress(this));
      VM.sysWriteln("stack at ", Magic.objectAsAddress(stack), " up to ", Magic.objectAsAddress(stack).plus(stack.length));
    }
  }

  /**
   * Release a thread's slot in the threads array.
   */
  @NoCheckStore
  void releaseThreadSlot() {
    acctLock.lockNoHandshake();
    RVMThread replacementThread = threads[numThreads - 1];
    threads[threadIdx] = replacementThread;
    replacementThread.threadIdx = threadIdx;
    threadIdx = -1;
    Magic.fence(); /*
                   * make sure that if someone is processing the threads array
                   * without holding the acctLock (which is definitely legal)
                   * then they see the replacementThread moved to the new index
                   * before they see the numThreads decremented (otherwise they
                   * would miss replacementThread; but with the current
                   * arrangement at worst they will see it twice)
                   */
    threads[--numThreads] = null;
    threadBySlot[threadSlot] = null;
    freeSlots[freeSlotN++] = threadSlot;
    acctLock.unlock();
  }

   /**
    * Create a new RVM Thread
    *
    * @param stack The stack on which to execute the thread.
    * @param thread The corresponding java.lang.Thread.
    * @param name The name of the thread
    * @param daemon True if this is a daemon thread.
    * @param systemThread True if this is a system thread.
    * @param priority The threads execution priority.
    */
   public RVMThread(byte[] stack, Thread thread, String name, boolean daemon, SystemThread systemThread, int priority) {
    this.stack = stack;

    this.daemon = daemon;
    this.priority = priority;
    this.systemThread = systemThread;

    this.contextRegisters = this.contextRegistersShadow = ArchitectureFactory.createRegisters();
    this.contextRegistersSave = this.contextRegistersSaveShadow = ArchitectureFactory.createRegisters();
    this.exceptionRegisters = this.exceptionRegistersShadow = ArchitectureFactory.createRegisters();

    if (VM.runningVM) {
      feedlet = TraceEngine.engine.makeFeedlet(name, name);
    }

    if (VM.VerifyAssertions) VM._assert(stack != null);

    // put self in list of threads known to scheduler and garbage collector
    if (!VM.runningVM) {
      if (VM.VerifyAssertions) VM._assert(name != null);
      this.name = name;
      // create primordial thread (in boot image)
      assignThreadSlot();

      if (trace)
        trace("RVMThread create: ", name);
      if (trace)
        trace("daemon: ", daemon ? "true" : "false");
      if (trace)
        trace("RVMThread", "create");

      initMutator(threadSlot);
      this.activeMutatorContext = true;
      // Remember the boot thread
      this.execStatus = IN_JAVA;
      this.waiting = Waiting.RUNNABLE;
      // assign final field
      onStackReplacementEvent = null;
    } else {
      // create a normal (ie. non-primordial) thread

      // set up wrapper Thread if one exists
      this.thread = thread;
      // Set thread type

      this.execStatus = NEW;
      this.waiting = Waiting.RUNNABLE;

      stackLimit = Magic.objectAsAddress(stack).plus(StackFrameLayout.getStackSizeGuard());

      // get instructions for method to be executed as thread startoff
      CodeArray instructions = Entrypoints.threadStartoffMethod.getCurrentEntryCodeArray();

      VM.disableGC();

      // initialize thread registers
      Address ip = Magic.objectAsAddress(instructions);
      Address sp = Magic.objectAsAddress(stack).plus(stack.length);

      // Initialize the a thread stack as if "startoff" method had been called
      // by an empty baseline-compiled "sentinel" frame with one local variable.
      contextRegisters.initializeStack(ip, sp);

      VM.enableGC();

      assignThreadSlot();
      this.name = name == null ? "Thread-" + threadSlot : name;
      initMutator(threadSlot);
      activeMutatorContext = true;
      if (traceAcct) {
        VM.sysWriteln("registered mutator for ", threadSlot);
      }

      initializeJNIEnv();

      if (traceAcct)
        VM.sysWriteln("Finishing initializeJniEnv() for the thread");

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
   * Creates a thread with default stack and with the given name. The
   * thread will be a daemon thread that runs at normal priority and is not
   * associated with a {@link Thread} object.
   *
   * @param systemThread the associated system thread
   * @param name human-readable name
   */
  public RVMThread(SystemThread systemThread, String name) {
    this(MemoryManager.newStack(StackFrameLayout.getStackSizeNormal()), null, // java.lang.Thread
        name, true, // daemon
        systemThread,
        Thread.NORM_PRIORITY);
  }

  /**
   * Create a thread with the given stack and name. Used by
   * {@link org.jikesrvm.mm.mminterface.CollectorThread} and the
   * boot image writer for the boot thread.
   *
   * @param systemThread the associated system thread
   * @param stack the thread's stack
   * @param name human-readable name of the thread
   */
  public RVMThread(SystemThread systemThread, byte[] stack, String name) {
    this(stack, null, // java.lang.Thread
        name, true, // daemon
        systemThread,
        Thread.NORM_PRIORITY);
  }

  /**
   * Create a thread with ... called by java.lang.VMThread.create. System thread
   * isn't set.
   *
   * @param thread the associated Java thread
   * @param stacksize desired stack size in bytes, must be positive
   * @param name human-readable name
   * @param daemon whether the thread is a daemon
   * @param priority the priority for the thread
   */
  public RVMThread(Thread thread, int stacksize, String name, boolean daemon, int priority) {
    this(MemoryManager.newStack(stacksize), thread, name, daemon, null, priority);
  }

  /**
   * Check if the thread has block requests (for example, for suspension and GC).  If
   * it does, clear the requests and marked the thread as blocked for that request.
   * If there were any block requests, do a broadcast() on the thread's monitor().
   * This is an internal method and should only be called from code that implements
   * thread blocking.  The monitor() lock must be held for this method to work properly.
   */
  private void acknowledgeBlockRequests() {
    boolean hadSome = false;
    if (VM.VerifyAssertions)
      VM._assert(blockAdapters != null);
    for (int i = 0; i < blockAdapters.length; ++i) {
      if (blockAdapters[i].hasBlockRequest(this)) {
        blockAdapters[i].setBlocked(this, true);
        blockAdapters[i].clearBlockRequest(this);
        hadSome = true;
      }
    }
    if (hadSome) {
      monitor().broadcast();
    }
  }

  /**
   * Checks if the thread system has acknowledged that the thread is supposed
   * to be blocked. This will return true if the thread is actually blocking, or
   * if the thread is running native code but is guaranteed to block before
   * returning to Java.  Only call this method when already holding the monitor(),
   * for two reasons:
   * <ol>
   * <li>This method does not acquire the monitor() lock even though it needs
   * to have it acquired given the data structures that it is accessing.
   * <li>You will typically want to call this method to decide if you need to
   * take action under the assumption that the thread is blocked (or not
   * blocked). So long as you hold the lock the thread cannot change state from
   * blocked to not blocked.
   * </ol>
   *
   * @return if the thread is supposed to be blocked
   */
  public boolean isBlocked() {
    for (int i = 0; i < blockAdapters.length; ++i) {
      if (blockAdapters[i].isBlocked(this)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if the thread is executing Java code. A thread is executing Java
   * code if its <code>execStatus</code> is <code>IN_JAVA</code> or
   * <code>IN_JAVA_TO_BLOCK</code>, and if it is not
   * <code>aboutToTerminate</code>, and if it is not blocked. Only call this
   * method when already holding the monitor(), and probably only after calling
   * setBlockedExecStatus(), for two reasons:
   * <ol>
   * <li>This method does not acquire the monitor() lock even though it needs
   * to have it acquired given the data structures that it is accessing.
   * <li>You will typically want to call this method to decide if you need to
   * take action under the assumption that the thread is running Java (or not
   * running Java). So long as you hold the lock - and you have called
   * setBlockedExecStatus() - the thread cannot change state from running-Java
   * to not-running-Java.
   * </ol>
   *
   * @return if the thread is running Java
   */
  public boolean isInJava() {
    return !isBlocking && !isAboutToTerminate &&
      (getExecStatus() == IN_JAVA || getExecStatus() == IN_JAVA_TO_BLOCK);
  }

  /**
   * Checks whether the thread is in native code as understood by the JMX ThreadInfo.
   * A thread is considered in native if it is executing JNI code.
   * <p>
   * Note: this method is NOT designed for internal use by the RVMThread class and
   * must not be used for scheduling. For comparison see a method used for
   * internal scheduling decisions such as {@link #isInJava()}.
   *
   * @return if the thread is running JNI code
   */
  boolean isInNativeAccordingToJMX() {
    return !isAboutToTerminate &&
        (getExecStatus() == IN_JNI || getExecStatus() == BLOCKED_IN_JNI);
  }

  /**
   * Should the thread by eligible for sampling by the timer thread?
   * <p>
   * Heuristically, we use timer-based sampling the in the adaptive system
   * to determine where the program is spending time (and thus what to optimize).
   * This doesn't have to be a 100% accurate, but it must be non-blocking
   * and also closely approximate whether or not the thread is executing.
   * For now, approximate just as being in JAVA.
   * <p>
   * As a future item, we may want to actually correctly attribute time
   * spent in native code to the top native method on the frame when the timer
   * goes off.  This will require work in the JNI enter/exit sequence to deal with
   * timer samples appropriately.
   *
   * @return whether this thread should be sampled by the timer thread.
   */
  public boolean shouldBeSampled() {
    return execStatus == IN_JAVA;
  }

  /** A variant of checkBlock() that does not save the thread state. */
  @NoInline
  @Unpreemptible("May block if the thread was asked to do so, but otherwise does no actions that would cause blocking")
  private void checkBlockNoSaveContext() {
    assertUnacceptableStates(NEW, TERMINATED);
    if (VM.VerifyAssertions) VM._assert(!isAboutToTerminate);
    if (VM.VerifyAssertions) VM._assert(!isBlocking);

    if (traceBlock)
      VM.sysWriteln("Thread #", threadSlot, " in checkBlockNoSaveContext");
    // NB: anything this method calls CANNOT change the contextRegisters
    // or the JNI env. as well, this code will be running concurrently
    // with stop-the-world GC!
    monitor().lockNoHandshake();
    isBlocking = true;
    if (traceBlock)
      VM.sysWriteln("Thread #", threadSlot,
          " acquired lock and has notified everyone that we're blocked");

    // deal with requests that would require a soft handshake rendezvous
    handleHandshakeRequest();
    // check if a soft handshake has been requested, and if so, clear the
    // request
    boolean commitSoftRendezvous = softRendezvousCheckAndClear();
    if (commitSoftRendezvous) {
      // if a soft handshake had been requested, we need to acknowledge it.
      // but to acknowledge it we cannot be holding the monitor() lock.
      // it turns out that at this point in the code it is perfectly safe
      // to release it, because:
      // 1) callers of this method expect that it may, in all likelihood,
      // release the monitor() lock if they were holding it, since it
      // calls wait()
      // 2) if the block requests get cleared when we release the lock,
      // we won't call wait, since we reacquire the lock prior to checking
      // for block requests.
      int recCount = monitor().unlockCompletely();
      softRendezvousCommit();
      monitor().relockNoHandshake(recCount);
    }

    if (traceBlock)
      VM.sysWriteln("Thread #", threadSlot,
                    " has acknowledged soft handshakes");

    boolean hadReallyBlocked = false;

    for (;;) {
      // deal with block requests
      acknowledgeBlockRequests();
      // are we blocked?
      if (!isBlocked()) {
        break;
      }
      if (traceReallyBlock) {
        hadReallyBlocked = true;
        VM.sysWriteln("Thread #", threadSlot,
                      " is really blocked with status ",
                      READABLE_EXEC_STATUS[getExecStatus()]);
        VM.sysWriteln("Thread #", threadSlot,
            " has fp = ", Magic.getFramePointer());
        if (dumpStackOnBlock) {
          dumpStack();
        }
      }
      // what if a GC request comes while we're here for a suspend()
      // request?
      // answer: we get awoken, reloop, and acknowledge the GC block
      // request.
      monitor().waitNoHandshake();

      if (traceBlock)
        VM.sysWriteln("Thread #", threadSlot,
            " has awoken; checking if we're still blocked");
    }

    if (traceBlock || (traceReallyBlock && hadReallyBlocked))
      VM.sysWriteln("Thread #", threadSlot, " is unblocking");

    // we're about to unblock, so indicate to the world that we're running
    // again.
    setExecStatus(IN_JAVA);
    // let everyone know that we're back to executing code
    isBlocking = false;
    // deal with requests that came up while we were blocked.
    handleHandshakeRequest();
    monitor().unlock();

    if (traceBlock)
      VM.sysWriteln("Thread #", threadSlot, " is unblocked");
  }

  /**
   * Check if the thread is supposed to block, and if so, block it. This method
   * will ensure that soft handshake requests are acknowledged or else
   * inhibited, that any blocking request is handled, that the execution state
   * of the thread (<code>execStatus</code>) is set to <code>IN_JAVA</code>
   * once all blocking requests are cleared, and that other threads are notified
   * that this thread is in the middle of blocking by setting the appropriate
   * flag (<code>isBlocking</code>). Note that this thread acquires the
   * monitor(), though it may release it completely either by calling wait() or
   * by calling unlockCompletely(). Thus, although it isn't generally a problem
   * to call this method while holding the monitor() lock, you should only do so
   * if the loss of atomicity is acceptable.
   * <p>
   * Generally, this method should be called from the following four places:
   * <ol>
   * <li>The block() method, if the thread is requesting to block itself.
   * Currently such requests only come when a thread calls suspend(). Doing so
   * has unclear semantics (other threads may call resume() too early causing
   * the well-known race) but must be supported because it's still part of the
   * JDK. Why it's safe: the block() method needs to hold the monitor() for the
   * time it takes it to make the block request, but does not need to continue
   * to hold it when it calls checkBlock(). Thus, the fact that checkBlock()
   * breaks atomicity is not a concern.
   * <li>The yieldpoint. One of the purposes of a yieldpoint is to periodically
   * check if the current thread should be blocked. This is accomplished by
   * calling checkBlock(). Why it's safe: the yieldpoint performs several
   * distinct actions, all of which individually require the monitor() lock -
   * but the monitor() lock does not have to be held contiguously. Thus, the
   * loss of atomicity from calling checkBlock() is fine.
   * <li>The "WithHandshake" methods of HeavyCondLock. These methods allow you to
   * block on a mutex or condition variable while notifying the system that you
   * are not executing Java code. When these blocking methods return, they check
   * if there had been a request to block, and if so, they call checkBlock().
   * Why it's safe: This is subtle. Two cases exist. The first case is when a
   * WithHandshake method is called on a HeavyCondLock instance that is not a thread
   * monitor(). In this case, it does not matter that checkBlock() may acquire
   * and then completely release the monitor(), since the user was not holding
   * the monitor(). However, this will break if the user is <i>also</i> holding
   * the monitor() when calling the WithHandshake method on a different lock. This case
   * should never happen because no other locks should ever be acquired when the
   * monitor() is held. Additionally: there is the concern that some other locks
   * should never be held while attempting to acquire the monitor(); the
   * HeavyCondLock ensures that checkBlock() is only called when that lock
   * itself is released. The other case is when a WithHandshake method is called on the
   * monitor() itself. This should only be done when using <i>your own</i>
   * monitor() - that is the monitor() of the thread your are running on. In
   * this case, the WithHandshake methods work because: (i) lockWithHandshake() only calls
   * checkBlock() on the initial lock entry (not on recursive entry), so
   * atomicity is not broken, and (ii) waitWithHandshake() and friends only call
   * checkBlock() after wait() returns - at which point it is safe to release
   * and reacquire the lock, since there cannot be a race with broadcast() once
   * we have committed to not calling wait() again.
   * <li>Any code following a potentially-blocking native call. Case (3) above
   * is somewhat subsumed in this except that it is special due to the fact that
   * it's blocking on VM locks. So, this case refers specifically to JNI. The
   * JNI epilogues will call leaveJNIBlocked(), which calls a variant of this
   * method.
   * </ol>
   */
  @NoInline
  @NoOptCompile
  @BaselineSaveLSRegisters
  @Unpreemptible("May block if asked to do so, but otherwise does not actions that would block")
  void checkBlock() {
    saveThreadState();
    checkBlockNoSaveContext();
  }

  /**
   * Internal method for transitioning a thread from IN_JAVA or IN_JAVA_TO_BLOCK to
   * either BLOCKED_IN_NATIVE or BLOCKED_IN_JNI, depending on the value of the jni
   * parameter.  It is always safe to conservatively call this method when transitioning
   * to native code, though it is faster to call either enterNative(),
   * enterJNIFromCallIntoNative(), or enterJNIFromJNIFunctionCall().
   * <p>
   * This method takes care of all bookkeeping and notifications required when a
   * a thread that has been requested to block instead decides to run native code.
   * Threads enter native code never need to block, since they will not be executing
   * any Java code.  However, such threads must ensure that any system services (like
   * GC) that are waiting for this thread to stop are notified that the thread has
   * instead chosen to exit Java.  As well, any requests to perform a soft handshake
   * must be serviced and acknowledged.
   *
   * @param jni whether this method is called for entering JNI or not
   */
  private void enterNativeBlockedImpl(boolean jni) {
    if (traceReallyBlock)
      VM.sysWriteln("Thread #", threadSlot, " entering native blocked.");
    // NB: anything this method calls CANNOT change the contextRegisters
    // or the JNI env. as well, this code will be running concurrently
    // with stop-the-world GC!
    boolean commitSoftRendezvous;
    monitor().lockNoHandshake();
    if (jni) {
      jniEnteredBlocked++;
      setExecStatus(BLOCKED_IN_JNI);
    } else {
      nativeEnteredBlocked++;
      setExecStatus(BLOCKED_IN_NATIVE);
    }
    acknowledgeBlockRequests();
    handleHandshakeRequest();
    commitSoftRendezvous = softRendezvousCheckAndClear();
    monitor().unlock();
    if (traceBlock)
      VM.sysWriteln("Thread #", threadSlot,
          " done with the locking part of native entry.");
    if (commitSoftRendezvous)
      softRendezvousCommit();
    if (traceBlock)
      VM.sysWriteln("Thread #", threadSlot, " done enter native blocked.");
  }

  @Unpreemptible("May block if the thread was asked to do so, but otherwise does no actions that would cause blocking")
  private void leaveNativeBlockedImpl() {
    checkBlockNoSaveContext();
  }

  private void enterNativeBlocked() {
    assertAcceptableStates(IN_JAVA,IN_JAVA_TO_BLOCK);
    enterNativeBlockedImpl(false);
    assertAcceptableStates(IN_NATIVE,BLOCKED_IN_NATIVE);
  }

  @Unpreemptible("May block if the thread was asked to do so, but otherwise does no actions that would cause blocking")
  private void leaveNativeBlocked() {
    assertAcceptableStates(IN_NATIVE,BLOCKED_IN_NATIVE);
    leaveNativeBlockedImpl();
    assertAcceptableStates(IN_JAVA,IN_JAVA_TO_BLOCK);
  }

  private void enterJNIBlocked() {
    assertAcceptableStates(IN_JAVA,IN_JAVA_TO_BLOCK);
    enterNativeBlockedImpl(true);
    assertAcceptableStates(IN_JNI,BLOCKED_IN_JNI);
  }

  @Unpreemptible("May block if the thread was asked to do so, but otherwise does no actions that would cause blocking")
  private void leaveJNIBlocked() {
    assertAcceptableStates(IN_JNI,BLOCKED_IN_JNI);
    leaveNativeBlockedImpl();
    assertAcceptableStates(IN_JAVA,IN_JAVA_TO_BLOCK);
  }

  @Entrypoint
  public static void enterJNIBlockedFromJNIFunctionCall() {
    RVMThread t = getCurrentThread();
    if (traceReallyBlock) {
      VM.sysWriteln("Thread #",t.getThreadSlot(), " in enterJNIBlockedFromJNIFunctionCall");
      VM.sysWriteln("thread address = ",Magic.objectAsAddress(t));
    }
    t.enterJNIBlocked();
  }

  @Entrypoint
  public static void enterJNIBlockedFromCallIntoNative() {
    RVMThread t = getCurrentThread();
    if (traceReallyBlock) {
      VM.sysWriteln("Thread #",t.getThreadSlot(), " in enterJNIBlockedFromCallIntoNative");
      VM.sysWriteln("thread address = ",Magic.objectAsAddress(t));
    }
    t.enterJNIBlocked();
  }

  @Entrypoint
  @Unpreemptible("May block if the thread was asked to do so, but otherwise will not block")
  static void leaveJNIBlockedFromJNIFunctionCall() {
    RVMThread t = getCurrentThread();
    if (traceReallyBlock) {
      VM.sysWriteln("Thread #", t.getThreadSlot(),
          " in leaveJNIBlockedFromJNIFunctionCall");
      VM.sysWriteln("thread address = ",Magic.objectAsAddress(t));
      VM.sysWriteln("state = ", READABLE_EXEC_STATUS[t.getExecStatus()]);
      VM.sysWriteln("jtoc = ", Magic.getJTOC());
    }
    t.leaveJNIBlocked();
  }

  /**
   * Called when JNI code tried to transition from  IN_JNI to IN_JAVA but failed
   */
  @Entrypoint
  @Unpreemptible("May block if the thread was asked to do so, but otherwise will not block")
  public static void leaveJNIBlockedFromCallIntoNative() {
    RVMThread t = getCurrentThread();
    if (traceReallyBlock) {
      VM.sysWriteln("Thread #", t.getThreadSlot(),
          " in leaveJNIBlockedFromCallIntoNative");
      VM.sysWriteln("state = ", READABLE_EXEC_STATUS[t.getExecStatus()]);
      VM.sysWriteln("jtoc = ", Magic.getJTOC());
    }
    t.leaveJNIBlocked();
  }

  private int setBlockedExecStatus() {
    int oldState, newState;
    do {
      oldState = getExecStatus();
      if (oldState == IN_JAVA) {
        newState = IN_JAVA_TO_BLOCK;
      } else if (oldState == IN_NATIVE) {
        newState = BLOCKED_IN_NATIVE;
      } else if (oldState == IN_JNI) {
        newState = BLOCKED_IN_JNI;
      } else {
        newState = oldState;
      }
      /*
       * use the CAS to assert that we observed what we
       * thought we observed
       */
    } while (!(attemptFastExecStatusTransition(oldState,newState)));
    return newState;
  }

  /**
   * Attempts to block the thread, and return the state it is in after the
   * attempt. If we're blocking ourselves, this will always return IN_JAVA. If
   * the thread signals to us the intention to die as we are trying to block it,
   * this will return TERMINATED. NOTE: the thread's execStatus will not
   * actually be TERMINATED at that point yet.
   * <p>
   * Note that this method is ridiculously dangerous, especially if you pass
   * asynchronous==false.  Waiting for another thread to stop is not in itself
   * interruptible - so if you ask another thread to block and they ask you
   * to block, you might deadlock.
   *
   * @param ba the adapter to block on
   * @param asynchronous {@code true} if the request is asynchronous (i.e. the
   *  receiver is only notified), {@code false} if the caller waits for the
   *  receiver to block
   * @return the new state of the thread
   */
  @Unpreemptible("Only blocks if the receiver is the current thread, or if asynchronous is set to false and the thread is not already blocked")
  int block(BlockAdapter ba, boolean asynchronous) {
    int result;
    if (traceBlock)
      VM.sysWriteln("Thread #", getCurrentThread().threadSlot,
          " is requesting that thread #", threadSlot, " blocks.");
    monitor().lockNoHandshake();
    int token = ba.requestBlock(this);
    if (getCurrentThread() == this) {
      if (traceBlock)
        VM.sysWriteln("Thread #", threadSlot, " is blocking.");
      checkBlock();
      result = getExecStatus();
    } else {
      if (traceBlock)
        VM.sysWriteln("Thread #", threadSlot, " is being told to block.");
      if (isAboutToTerminate) {
        if (traceBlock)
          VM.sysWriteln("Thread #", threadSlot,
              " is terminating, returning as if blocked in TERMINATED state.");
        result = TERMINATED;
      } else {
        takeYieldpoint = 1;
        // CAS the execStatus field
        int newState = setBlockedExecStatus();
        result = newState;
        if (traceReallyBlock)
          VM.sysWriteln("Thread #", getCurrentThreadSlot(),
              " is blocking thread #", threadSlot, " which is in state ",
              newState);
        // this broadcast serves two purposes: notifies threads that are
        // IN_JAVA but waiting on monitor() that they should awake and
        // acknowledge the block request; or notifies anyone
        // waiting for this thread to block that the thread is
        // BLOCKED_IN_NATIVE or BLOCKED_IN_JNI. in the latter case the
        // broadcast() happens _before_ the setting of the flags that the
        // other threads would be awaiting, but that is fine, since we're
        // still holding the lock anyway.
        monitor().broadcast();
        if (newState == IN_JAVA_TO_BLOCK) {
          if (!asynchronous) {
            if (traceBlock)
              VM.sysWriteln("Thread #", getCurrentThread().threadSlot,
                  " is waiting for thread #", threadSlot, " to block.");
            while (ba.hasBlockRequest(this, token) && !ba.isBlocked(this) && !isAboutToTerminate) {
              if (traceBlock)
                VM.sysWriteln("Thread #", getCurrentThread().threadSlot,
                    " is calling wait until thread #", threadSlot, " blocks.");
              // will this deadlock when the thread dies?
              if (VM.VerifyAssertions) {
                // do a timed wait, and assert that the thread did not disappear
                // into native in the meantime
                monitor().timedWaitRelativeNoHandshake(1000L * 1000L * 1000L); // 1 sec
                if (traceReallyBlock) {
                  VM.sysWriteln("Thread #", threadSlot, "'s status is ",
                      READABLE_EXEC_STATUS[getExecStatus()]);
                }
                assertUnacceptableStates(IN_NATIVE);
              } else {
                monitor().waitNoHandshake();
              }
              if (traceBlock)
                VM.sysWriteln("Thread #", getCurrentThread().threadSlot,
                    " has returned from the wait call.");
            }
            if (isAboutToTerminate) {
              result = TERMINATED;
            } else {
              result = getExecStatus();
            }
          }
        } else if (newState == BLOCKED_IN_NATIVE || newState == BLOCKED_IN_JNI) {
          // we own the thread for now - it cannot go back to executing Java
          // code until we release the lock. before we do so we change its
          // state accordingly and tell anyone who is waiting.
          if (traceBlock)
            VM.sysWriteln("Thread #", getCurrentThread().threadSlot,
                " has seen thread #", threadSlot,
                " in native; changing its status accordingly.");
          ba.clearBlockRequest(this);
          ba.setBlocked(this, true);
        }
      }
    }
    monitor().unlock();
    if (traceReallyBlock)
      VM.sysWriteln("Thread #", getCurrentThread().threadSlot,
          " is done telling thread #", threadSlot, " to block.");
    return result;
  }

  public boolean blockedFor(BlockAdapter ba) {
    monitor().lockNoHandshake();
    boolean result = ba.isBlocked(this);
    monitor().unlock();
    return result;
  }

  @UninterruptibleNoWarn("Never blocks; only asynchronously notifies the receiver to do so")
  public int asyncBlock(BlockAdapter ba) {
    if (VM.VerifyAssertions)
      VM._assert(getCurrentThread() != this);
    return block(ba, true);
  }

  @Unpreemptible("May block if the receiver is the current thread or if the receiver is not yet blocked; otherwise does not perform actions that lead to blocking")
  public int block(BlockAdapter ba) {
    return block(ba, false);
  }

  @Unpreemptible
  public void beginPairWith(RVMThread other) {
    if (traceBlock) VM.sysWriteln("attempting to pair ",threadSlot," with ",other.threadSlot);
    Monitor.lockWithHandshake(
      communicationLock(),Word.fromIntSignExtend(threadSlot),
      other.communicationLock(),Word.fromIntSignExtend(other.threadSlot));
  }

  public void endPairWith(RVMThread other) {
    communicationLock().unlock();
    other.communicationLock().unlock();
    if (traceBlock) VM.sysWriteln("unpairing ",threadSlot," from ",other.threadSlot);
  }

  @Unpreemptible
  public void beginPairWithCurrent() {
    beginPairWith(getCurrentThread());
  }

  public void endPairWithCurrent() {
    endPairWith(getCurrentThread());
  }

  @Unpreemptible
  private int safeBlock(BlockAdapter ba, boolean asynchronous) {
    if (VM.VerifyAssertions)
      VM._assert(getCurrentThread() != this);
    beginPairWithCurrent();
    int result = block(ba,asynchronous);
    endPairWithCurrent();
    return result;
  }

  @Unpreemptible
  public int safeAsyncBlock(BlockAdapter ba) {
    return safeBlock(ba, true);
  }

  @Unpreemptible
  public int safeBlock(BlockAdapter ba) {
    if (getCurrentThread() == this) {
      return block(ba,false);
    } else {
      return safeBlock(ba, false);
    }
  }

  @Unpreemptible
  public void beginPairHandshake() {
    beginPairWithCurrent();
    block(handshakeBlockAdapter);
  }

  @Uninterruptible
  public void endPairHandshake() {
    unblock(handshakeBlockAdapter);
    endPairWithCurrent();
  }

  /**
   * Save the current thread state.  Call this prior to calling enterNative().  You must
   * be in a method that is marked BaselineSaveLSRegisters.
   */
  @NoInline
  public static void saveThreadState() {
    Address curFP = Magic.getFramePointer();
    getCurrentThread().contextRegisters.setInnermost(Magic.getReturnAddressUnchecked(curFP),
                                                     Magic.getCallerFramePointer(curFP));
  }

  /**
   * Indicate that we'd like the current thread to be executing privileged code that
   * does not require synchronization with the GC.  This call may be made on a thread
   * that is IN_JAVA or IN_JAVA_TO_BLOCK, and will result in the thread being either
   * IN_NATIVE or BLOCKED_IN_NATIVE.  In the case of an
   * IN_JAVA_TO_BLOCK-&gt;BLOCKED_IN_NATIVE transition, this call will acquire the
   * thread's lock and send out a notification to any threads waiting for this thread
   * to reach a safepoint.  This notification serves to notify them that the thread
   * is in GC-safe code, but will not reach an actual safepoint for an indetermined
   * amount of time.  This is significant, because safepoints may perform additional
   * actions (such as handling handshake requests, which may include things like
   * mutator flushes and running isync) that IN_NATIVE code will not perform until
   * returning to IN_JAVA by way of a leaveNative() call.
   */
  @NoInline // so we can get the fp
  public static void enterNative() {
    RVMThread t = getCurrentThread();
    if (ALWAYS_LOCK_ON_STATE_TRANSITION) {
      t.enterNativeBlocked();
    } else {
      int oldState, newState;
      do {
        oldState = t.getExecStatus();
        if (oldState == IN_JAVA) {
          newState = IN_NATIVE;
        } else {
          t.assertAcceptableStates(IN_JAVA_TO_BLOCK);
          t.enterNativeBlocked();
          return;
        }
      } while (!(t.attemptFastExecStatusTransition(oldState, newState)));
    }
    // NB this is not a correct assertion, as there is a race.  we could succeed in
    // CASing the status to IN_NATIVE, but then someone else could asynchronosly
    // set it to whatever they want.
    //if (VM.VerifyAssertions)
    //  VM._assert(t.execStatus == IN_NATIVE);
  }

  /**
   * Attempt to transition from IN_JNI or IN_NATIVE to IN_JAVA, fail if execStatus is
   * anything but IN_JNI or IN_NATIVE.
   *
   * @return true if thread transitioned to IN_JAVA, otherwise false
   */
  public static boolean attemptLeaveNativeNoBlock() {
    if (ALWAYS_LOCK_ON_STATE_TRANSITION)
      return false;
    RVMThread t = getCurrentThread();
    int oldState, newState;
    do {
      oldState = t.getExecStatus();
      if (oldState == IN_NATIVE || oldState == IN_JNI) {
        newState = IN_JAVA;
      } else {
        t.assertAcceptableStates(BLOCKED_IN_NATIVE,BLOCKED_IN_JNI);
        return false;
      }
    } while (!(t.attemptFastExecStatusTransition(oldState, newState)));
    return true;
  }

  /**
   * Leave privileged code.  This is valid for threads that are either IN_NATIVE,
   * IN_JNI, BLOCKED_IN_NATIVE, or BLOCKED_IN_JNI, and always results in the thread
   * being IN_JAVA.  If the thread was previously BLOCKED_IN_NATIVE or BLOCKED_IN_JNI,
   * the thread will block until notified that it can run again.
   */
  @Unpreemptible("May block if the thread was asked to do so; otherwise does no actions that would lead to blocking")
  public static void leaveNative() {
    if (!attemptLeaveNativeNoBlock()) {
      if (traceReallyBlock) {
        VM.sysWriteln("Thread #", getCurrentThreadSlot(),
            " is leaving native blocked");
      }
      getCurrentThread().leaveNativeBlocked();
    }
  }

  public static void enterJNIFromCallIntoNative() {
    // FIXME: call these in PPC instead of doing it in machine code...
    getCurrentThread().observeExecStatus();
    if (!getCurrentThread().attemptFastExecStatusTransition(RVMThread.IN_JAVA,
        RVMThread.IN_JNI)) {
      RVMThread.enterJNIBlockedFromCallIntoNative();
    }
  }

  @Unpreemptible
  public static void leaveJNIFromCallIntoNative() {
    // FIXME: call these in PPC instead of doing it in machine code...
    getCurrentThread().observeExecStatus();
    if (!getCurrentThread().attemptFastExecStatusTransition(RVMThread.IN_JNI,
        RVMThread.IN_JAVA)) {
      RVMThread.leaveJNIBlockedFromCallIntoNative();
    }
  }

  public static void enterJNIFromJNIFunctionCall() {
    // FIXME: call these instead of doing it in machine code...  currently this
    // is never called.
    getCurrentThread().observeExecStatus();
    if (!getCurrentThread().attemptFastExecStatusTransition(RVMThread.IN_JAVA,
        RVMThread.IN_JNI)) {
      RVMThread.enterJNIBlockedFromJNIFunctionCall();
    }
  }

  @Unpreemptible
  public static void leaveJNIFromJNIFunctionCall() {
    // FIXME: call these instead of doing it in machine code...  currently this
    // is never called.
    getCurrentThread().observeExecStatus();
    if (!getCurrentThread().attemptFastExecStatusTransition(RVMThread.IN_JNI,
        RVMThread.IN_JAVA)) {
      RVMThread.leaveJNIBlockedFromJNIFunctionCall();
    }
  }

  public void unblock(BlockAdapter ba) {
    if (traceBlock)
      VM.sysWriteln("Thread #", getCurrentThread().threadSlot,
          " is requesting that thread #", threadSlot, " unblocks.");
    monitor().lockNoHandshake();
    ba.clearBlockRequest(this);
    ba.setBlocked(this, false);
    monitor().broadcast();
    monitor().unlock();
    if (traceBlock)
      VM.sysWriteln("Thread #", getCurrentThread().threadSlot,
          " is done requesting that thread #", threadSlot, " unblocks.");
  }

  private void handleDebugRequestForThread() {
    monitor().lockNoHandshake();
    dumpLock.lockNoHandshake();
    extDump();
    if (!isAboutToTerminate) {
      setBlockedExecStatus();
      if (isInJava()) {
        asyncDebugRequestedForThisThread = true;
        takeYieldpoint = 1;
        VM.sysWriteln("(stack trace will follow if thread is not lost...)");
      } else {
        if (contextRegisters != null) {
          dumpStack(contextRegisters.getInnermostFramePointer());
        } else {
          VM.sysWriteln("(cannot dump stack trace; thread is not running in Java but has no contextRegisters)");
        }
      }
    }
    dumpLock.unlock();
    monitor().unlock();
  }

  @NoCheckStore
  public static void checkDebugRequest() {
    if (debugRequested) {
      debugLock.lockNoHandshake();
      if (debugRequested) {
        debugRequested = false;
        VM.sysWriteln("=== Debug requested - attempting safe VM dump ===");
        dumpAcct();
        reportThreadTransitionCounts();

        // FIXME: this code runs concurrently to GC and has no way of stopping
        // it.  hence it is dangerous.  leaving it as-is for now, since it's
        // only meant to be used for debugging.

        VM.sysWriteln("Timer ticks = ", timerTicks);
        doProfileReport.openNoHandshake();
        // snapshot the threads
        acctLock.lockNoHandshake();
        int numDebugThreads = numThreads;
        for (int i = 0; i < numThreads; ++i) {
          debugThreads[i] = threads[i];
        }
        acctLock.unlock();
        // do the magic
        for (int i = 0; i < numDebugThreads; ++i) {
          debugThreads[i].handleDebugRequestForThread();
          debugThreads[i] = null;
        }
      }
      debugLock.unlock();
    }
  }

  void timerTick() {
    if (shouldBeSampled()) {
      timeSliceExpired++;
      takeYieldpoint = 1;
    }
  }

  /** @return whether the thread is allowed to take yieldpoints */
  @Inline
  public boolean yieldpointsEnabled() {
    return yieldpointsEnabledCount == 1;
  }

  /** Enable yieldpoints on this thread. */
  public void enableYieldpoints() {
    ++yieldpointsEnabledCount;
    if (VM.VerifyAssertions)
      VM._assert(yieldpointsEnabledCount <= 1);
    if (yieldpointsEnabled() && yieldpointRequestPending) {
      takeYieldpoint = 1;
      yieldpointRequestPending = false;
    }
  }

  /** Disable yieldpoints on this thread. */
  public void disableYieldpoints() {
    --yieldpointsEnabledCount;
  }

  /**
   * Fail if yieldpoints are disabled on this thread
   */
  public void failIfYieldpointsDisabled() {
    if (!yieldpointsEnabled()) {
      VM.sysWrite("No yieldpoints on thread ", threadSlot);
      VM.sysWrite(" with addr ", Magic.objectAsAddress(this));
      VM.sysWriteln();
      VM.sysFail("Yieldpoints are disabled on this thread!");
    }
  }

  /**
   * @return The currently executing thread
   */
  @Uninterruptible
  public static RVMThread getCurrentThread() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.ThreadLocalState.getCurrentThread();
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.ppc.ThreadLocalState.getCurrentThread();
    }
  }

  /**
   * @return the unique slot of the currently executing thread
   */
  public static int getCurrentThreadSlot() {
    return getCurrentThread().threadSlot;
  }

  /**
   * @return the slot of this thread
   */
  public int getThreadSlot() {
    return threadSlot;
  }

  /**
   * Called during booting to give the boot thread a java.lang.Thread
   */
  @Interruptible
  public void setupBootJavaThread() {
    if (VM.BuildForOpenJDK) {
      // Use a fake thread to get through the constructor calls
      Thread fakeThread = (Thread) RuntimeEntrypoints.resolvedNewScalar(JikesRVMSupport.getTypeForClass(Thread.class).asClass());
      // Need to set a valid priority, 10 is maximum
      RVMField[] instanceFields = JikesRVMSupport.getTypeForClass(Thread.class).getInstanceFields();
      RVMField priorityField = null;
      for (RVMField f : instanceFields) {
        if (f.getName().toString().equals("priority")) {
          priorityField = f;
          break;
        }
      }
      priorityField.setIntValueUnchecked(fakeThread, 10);
      thread = fakeThread;
    }
    // Create the real thread
    thread = java.lang.JikesRVMSupport.createThread(this, "Jikes_RVM_Boot_Thread");
  }

  /**
   * String representation of thread
   */
  @Override
  public String toString() {
    return name;
  }

  /**
   * @return the current java.lang.Thread.
   */
  public Thread getJavaLangThread() {
    return thread;
  }

  /**
   * @return current thread's JNI environment.
   */
  public JNIEnvironment getJNIEnv() {
    return jniEnv;
  }

  /** @return the disable GC depth */
  public int getDisableGCDepth() {
    return disableGCDepth;
  }

  public void setDisableGCDepth(int d) {
    disableGCDepth = d;
  }

  /** @return whether allocations by this thread are disallowed */
  public boolean getDisallowAllocationsByThisThread() {
    return disallowAllocationsByThisThread;
  }

  /** Disallow allocations by this thread */
  public void setDisallowAllocationsByThisThread() {
    disallowAllocationsByThisThread = true;
  }

  /** Allow allocations by this thread */
  public void clearDisallowAllocationsByThisThread() {
    disallowAllocationsByThisThread = false;
  }

  /**
   * Initialize JNI environment for system threads. Called by VM.finishBooting
   */
  @Interruptible
  public void initializeJNIEnv() {
    this.jniEnv = this.jniEnvShadow = new JNIEnvironment();
  }

  /**
   * Indicate whether the stack of this Thread contains any C frame (used in
   * RuntimeEntrypoints.deliverHardwareException for stack resize)
   *
   * @return {@code false} during the prolog of the first Java to C transition,
   *  {@code true} afterward
   */
  public boolean hasNativeStackFrame() {
    return jniEnv != null && jniEnv.hasNativeStackFrame();
  }

  /*
   * Starting and ending threads
   */

  /**
   * Method to be executed when this thread starts running. Calls
   * java.lang.Thread.run but system threads can override directly.
   * <p>
   * This method will catch all uncaught throwables from the thread
   * and pass them to the thread's uncaught exception handler.
   */
  @Interruptible
  @Entrypoint
  public void run() {
    try {
      synchronized (thread) {
        Throwable t = java.lang.JikesRVMSupport.getStillBorn(thread);
        if (t != null) {
          java.lang.JikesRVMSupport.setStillBorn(thread, null);
          throw t;
        }
      }
      thread.run();
    } catch (Throwable t) {
      if (traceAcct) {
        VM.sysWriteln("Thread ",getThreadSlot()," exiting with exception.");
      }
      // Any throwable that reaches this point wasn't caught by the
      // thread and is therefore an uncaught exception by definition.
      // In order to make sure that terminate() sets the correct exit
      // status for this case, uncaughtExceptionCount needs to be
      // increased.
      uncaughtExceptionCount++;
      try {
        Thread.UncaughtExceptionHandler handler;
        handler = thread.getUncaughtExceptionHandler();
        handler.uncaughtException(thread, t);
      } catch (Throwable ignore) {
      }
    }
  }

  /**
   * Begin execution of current thread by calling its "run" method. This method
   * is at the bottom of all created method's stacks.
   */
  @Interruptible
  @SuppressWarnings({ "unused" })
  @Entrypoint
  private static void startoff() {
    bindIfRequested();

    RVMThread currentThread = getCurrentThread();

    /*
     * get pthread_id from the operating system and store into RVMThread field
     */
    currentThread.pthread_id = sysCall.sysGetThreadId();
    currentThread.priority_handle = sysCall.sysGetThreadPriorityHandle();

    /*
     * set thread priority to match stored value
     */
    sysCall.sysSetThreadPriority(currentThread.pthread_id,
        currentThread.priority_handle, currentThread.priority - Thread.NORM_PRIORITY);

    currentThread.enableYieldpoints();
    sysCall.sysStashVMThread(currentThread);
    if (traceAcct) {
      VM.sysWriteln("Thread #", currentThread.threadSlot, " with pthread id ",
          currentThread.pthread_id, " running!");
    }

    if (trace) {
      VM.sysWriteln("Thread.startoff(): about to call ", currentThread.toString(), ".run()");
    }

    try {
      if (currentThread.systemThread != null) {
        currentThread.systemThread.run();
      } else {
        currentThread.run();
      }
    } finally {
      if (trace) {
        VM.sysWriteln("Thread.startoff(): finished ", currentThread.toString(), ".run()");
      }
      currentThread.terminate();
      if (VM.VerifyAssertions)
        VM._assert(VM.NOT_REACHED);
    }
  }

  /**
   * Start execution of 'this' by creating and starting a native thread.
   */
  @Interruptible
  public void start() {
    // N.B.: cannot hit a yieldpoint between setting execStatus and starting the
    // thread!!
    setExecStatus(IN_JAVA);
    acctLock.lockNoHandshake();
    numActiveThreads++;
    if (isSystemThread()) {
      numActiveSystemThreads++;
    }
    JMXSupport.updatePeakThreadCount(numActiveThreads, numActiveSystemThreads);
    if (daemon) {
      numActiveDaemons++;
    }
    acctLock.unlock();
    if (traceAcct)
      VM.sysWriteln("Thread #", threadSlot, " starting!");
    sysCall.sysThreadCreate(contextRegisters.getInnermostInstructionAddress(),
        contextRegisters.getInnermostFramePointer(), Magic.objectAsAddress(this),
        Magic.getJTOC());
    if (!isSystemThread()) {
      JMXSupport.increaseStartedThreadCount();
    }
  }

  /**
   * Terminate execution of current thread by abandoning all references to it
   * and resuming execution in some other (ready) thread.
   */
  @Interruptible
  public void terminate() {
    if (traceAcct)
      VM.sysWriteln("in terminate() for Thread #", threadSlot);
    if (VM.VerifyAssertions)
      VM._assert(getCurrentThread() == this);
    boolean terminateSystem = false;
    if (traceTermination) {
      VM.disableGC();
      VM.sysWriteln("[ BEGIN Verbosely dumping stack at time of thread termination");
      dumpStack();
      VM.sysWriteln("END Verbosely dumping stack at time of creating thread termination ]");
      VM.enableGC();
    }

    // allow java.lang.Thread.exit() to remove this thread from ThreadGroup
    java.lang.JikesRVMSupport.threadDied(thread);

    TraceEngine.engine.removeFeedlet(feedlet);

    if (VM.VerifyAssertions) {
      if (Lock.countLocksHeldByThread(getLockingId()) > 0) {
        VM.sysWriteln("Error, thread terminating holding a lock");
        RVMThread.dumpVirtualMachine();
      }
    }

    if (traceAcct)
      VM.sysWriteln("doing accounting...");
    acctLock.lockNoHandshake();

    // if the thread terminated because of an exception, remove
    // the mark from the exception register object, or else the
    // garbage collector will attempt to relocate its ip field.
    exceptionRegisters.setInUse(false);

    numActiveThreads -= 1;
    if (isSystemThread()) {
      numActiveSystemThreads -= 1;
    }
    if (daemon) {
      numActiveDaemons -= 1;
    }
    if (traceAcct)
      VM.sysWriteln("active = ", numActiveThreads, ", daemons = ",
          numActiveDaemons);
    if ((numActiveDaemons == numActiveThreads) && (VM.mainThread != null) && VM.mainThread.launched) {
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
      VM.sysWriteln("  RVMThread.numActiveThreads = ",
          RVMThread.numActiveThreads);
      VM.sysWriteln("  RVMThread.numActiveDaemons = ",
          RVMThread.numActiveDaemons);
      VM.sysWriteln("  terminateSystem = ", terminateSystem);
    }

    acctLock.unlock();

    if (traceAcct)
      VM.sysWriteln("done with accounting.");

    if (terminateSystem) {
      if (traceAcct)
        VM.sysWriteln("terminating system.");
      if (uncaughtExceptionCount > 0)
      /* Use System.exit so that any shutdown hooks are run. */ {
        if (VM.TraceExceptionDelivery) {
          VM.sysWriteln("Calling sysExit due to uncaught exception.");
        }
        callSystemExit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
      } else if (thread instanceof MainThread) {
        MainThread mt = (MainThread) thread;
        if (!mt.launched) {
          /*
           * Use System.exit so that any shutdown hooks are run. It is possible
           * that shutdown hooks may be installed by static initializers which
           * were run by classes initialized before we attempted to run the main
           * thread. (As of this writing, 24 January 2005, the Classpath
           * libraries do not do such a thing, but there is no reason why we
           * should not support this.) This was discussed on
           * jikesrvm-researchers on 23 Jan 2005 and 24 Jan 2005.
           */
          callSystemExit(EXIT_STATUS_MAIN_THREAD_COULD_NOT_LAUNCH);
        }
      }
      /* Use System.exit so that any shutdown hooks are run. */
      callSystemExit(0);
      if (VM.VerifyAssertions)
        VM._assert(VM.NOT_REACHED);
    }

    if (traceAcct)
      VM.sysWriteln("making joinable...");

    // this works.  we use synchronized because we cannot use the thread's
    // monitor().  see comment in join().  this is fine, because we're still
    // "running" from the standpoint of GC.
    synchronized (this) {
      isJoinable = true;
      notifyAll();
    }
    if (traceAcct)
      VM.sysWriteln("Thread #", threadSlot, " is joinable.");

    if (traceAcct)
      VM.sysWriteln("making joinable...");

    // Switch to uninterruptible portion of termination
    terminateUnpreemptible();
  }

  /**
   * Calls {@link System#exit(int)} with the correct security status.
   *
   * @param exitStatus the exit status to pass on
   */
  @Interruptible
  private void callSystemExit(final int exitStatus) {
    AccessController.doPrivileged(new PrivilegedAction<Object>() {
      @Override
      public Object run() {
        System.exit(exitStatus);
        return null;
      }
    });
  }

  /**
   * Unpreemptible portion of thread termination. Unpreemptible to avoid a dead
   * thread from being scheduled.
   */
  @Unpreemptible
  private void terminateUnpreemptible() {
    // return cached free lock
    if (traceAcct)
      VM.sysWriteln("returning cached lock...");

    if (cachedFreeLock != null) {
      if (Lock.trace) {
        VM.sysWriteln("Thread #", threadSlot, ": about to free lock ",
            Magic.objectAsAddress(cachedFreeLock));
      }
      if (VM.VerifyAssertions)
        VM._assert(cachedFreeLock.mutex.latestContender != this);
      Lock.returnLock(cachedFreeLock);
      cachedFreeLock = null;
    }

    if (traceAcct)
      VM.sysWriteln("adding to aboutToTerminate...");

    addAboutToTerminate();
    // NB we can no longer do anything that would lead to write barriers or
    // GC

    if (traceAcct) {
      VM.sysWriteln("acquireCount for my monitor: ", monitor().acquireCount);
      VM.sysWriteln("timer ticks: ", timerTicks);
      VM.sysWriteln("yieldpoints taken: ", yieldpointsTaken);
      VM.sysWriteln("yieldpoints taken fully: ", yieldpointsTakenFully);
    }
    if (traceAcct)
      VM.sysWriteln("finishing thread termination...");

    finishThreadTermination();
  }

  /** Uninterruptible final portion of thread termination. */
  void finishThreadTermination() {
    sysCall.sysThreadTerminate();
    if (VM.VerifyAssertions)
      VM._assert(VM.NOT_REACHED);
  }

  /*
   * Support for yieldpoints
   */

  /**
   * Yieldpoint taken in prologue.
   */
  @BaselineSaveLSRegisters
  // Save all non-volatile registers in prologue
  @NoOptCompile
  @NoInline
  // We should also have a pragma that saves all non-volatiles in opt compiler,
  // BaselineExecuctionStateExtractor.java, should then restore all
  // non-volatiles before stack replacement
  // TODO fix this -- related to SaveVolatile
  @Entrypoint
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
  public static void yieldpointFromPrologue() {
    Address fp = Magic.getFramePointer();
    yieldpoint(PROLOGUE, fp);
  }

  /**
   * Yieldpoint taken on backedge.
   */
  @BaselineSaveLSRegisters
  // Save all non-volatile registers in prologue
  @NoOptCompile
  @NoInline
  // We should also have a pragma that saves all non-volatiles in opt compiler,
  // BaselineExecuctionStateExtractor.java, should then restore all
  // non-volatiles before stack replacement
  // TODO fix this -- related to SaveVolatile
  @Entrypoint
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
  public static void yieldpointFromBackedge() {
    Address fp = Magic.getFramePointer();
    yieldpoint(BACKEDGE, fp);
  }

  /**
   * The return barrier.
   * <p>
   * The following code implements return barriers as described
   * for Lisp by Yuasa
   *
   * http://www.yuasa.kuis.kyoto-u.ac.jp/~yuasa/ilc2002/index.html
   * http://dx.doi.org/10.1109/ISORC.2005.45
   *
   * and for Jikes RVM by Kumar et al
   *
   * http://dx.doi.org/10.1145/2398857.2384639
   * <p>
   * This code is executed when a method returns into a frame that
   * has been hijacked by the return barrier mechanism.   The return
   * barrier trampoline will save state, execute this method, and
   * then upon return from this method will transparently return into
   * the frame that had been hijacked.
   * <p>
   * In this default implementation, the barrier reinstalls itself
   * in the caller's frame thus incrementally moving the barrier down
   * the stack.
   * <p>
   * The execution of this method is fragile.  It is generally safest
   * to call some other method from here that does the substantive work
   * of the barrier.
   */
  @Entrypoint
  @Uninterruptible
  @Unpreemptible
  public static void returnBarrier() {
    /* reinstall the barrier in the caller's frame */
    if (DEBUG_STACK_TRAMPOLINE) {
      if (VM.BuildForIA32) {
        VM.sysWriteln(getCurrentThread().getId(), " T0: ",
            getCurrentThread().trampolineRegisters.getGPRs().
            get(org.jikesrvm.ia32.RegisterConstants.EAX.value()).toAddress());
        VM.sysWriteln(getCurrentThread().getId(), " T1: ",
            getCurrentThread().trampolineRegisters.getGPRs().
            get(org.jikesrvm.ia32.RegisterConstants.EDX.value()).toAddress());
      } else {
        // Return barrier not yet supported on other architectures
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      }
      VM.sysWriteln(getCurrentThread().getId(), " nf: ", getCurrentThread().hijackedReturnCallerFp);
      VM.sysWriteln(getCurrentThread().getId(), " lf: ", getCurrentThread().hijackedReturnCalleeFp);
      VM.sysWriteln(getCurrentThread().getId(), " fp: ", Magic.getFramePointer());
      VM.sysWriteln(getCurrentThread().getId(), " np: ", Magic.getCallerFramePointer(Magic.getFramePointer()));
    }
    /* reinstall the barrier in the specified frame */
    getCurrentThread().installStackTrampolineBridge(getCurrentThread().hijackedReturnCallerFp);
  }

  /**
   * Install the stack trampoline bridge at a given frame, hijacking
   * that frame, saving the hijacked return address and callee fp
   * in thread-local state to allow execution of the hijacked frame
   * later.
   *
   * @param targetFp The frame to be hijacked.
   */
  @Uninterruptible
  public void installStackTrampolineBridge(Address targetFp) {
    Address trampoline = getStackTrampolineBridgeIP();
    if (trampoline.isZero()) {
      if (VM.VerifyAssertions)
        VM._assert(VM.NOT_REACHED);
      else
        VM.sysWriteln("Warning: attempt to install stack trampoline without bridge instructions - nothing done.  See RVMThread.");
    } else if (trampoline.NE(Magic.getReturnAddressUnchecked(targetFp))) {
      /* install the trampoline at fp or the next suitable frame after fp */
      while (true) {
        if (Magic.getCallerFramePointer(targetFp).EQ(StackFrameLayout.getStackFrameSentinelFP())) {
          /* if we're at the bottom of the stack, then do not install anything */
          hijackedReturnAddress = Address.zero();
          hijackedReturnCalleeFp = Address.zero();
          return;
        }
        int cmid = Magic.getCompiledMethodID(targetFp);
        if (cmid == StackFrameLayout.getInvisibleMethodID()) {
          /* skip invisible methods */
          targetFp = Magic.getCallerFramePointer(targetFp);
        } else {
          CompiledMethod calleeCM = CompiledMethods.getCompiledMethod(cmid);
          if (calleeCM.getCompilerType() == CompiledMethod.TRAP ||
              calleeCM.getMethod().getDeclaringClass().hasBridgeFromNativeAnnotation()) {
            /* skip traps and native bridges */
            targetFp = Magic.getCallerFramePointer(targetFp);
          } else
            break;
        }
      }
      hijackedReturnAddress = Magic.getReturnAddressUnchecked(targetFp);
      hijackedReturnCalleeFp = targetFp;
      hijackedReturnCallerFp = Magic.getCallerFramePointer(targetFp);
      if (VM.VerifyAssertions) VM._assert(trampoline.NE(hijackedReturnAddress));
      if (DEBUG_STACK_TRAMPOLINE) dumpFrame(targetFp);
      Magic.setReturnAddress(targetFp, trampoline);
      if (DEBUG_STACK_TRAMPOLINE) {
        dumpFrame(targetFp);
        VM.sysWriteln(getId(), " Installing trampoline at: ", targetFp);
        VM.sysWriteln(getId(), " Trampoline: ", trampoline);
        VM.sysWriteln(getId(), " Hijacked return address: ", hijackedReturnAddress);
        VM.sysWriteln(getId(), " Callee fp: ", hijackedReturnCalleeFp);
        VM.sysWriteln(getId(), " Caller fp: ", hijackedReturnCallerFp);
        dumpStack(hijackedReturnCalleeFp);
      }
    }
  }

  /**
   * de-install the stack trampoline (disabling return barriers).
   */
  @Uninterruptible
  public void deInstallStackTrampoline() {
    if (DEBUG_STACK_TRAMPOLINE) VM.sysWriteln("deinstalling trampoline: ", framePointer);
    if (!hijackedReturnCalleeFp.isZero()) {
      if (DEBUG_STACK_TRAMPOLINE) VM.sysWriteln("need to reinstall: ", hijackedReturnAddress);
      hijackedReturnCalleeFp.plus(StackFrameLayout.getStackFrameReturnAddressOffset()).store(hijackedReturnAddress);
      hijackedReturnCalleeFp = Address.zero();
      hijackedReturnCallerFp = StackFrameLayout.getStackFrameSentinelFP();
    }
  }

  /** @return the address of the stack trampoline bridge code */
  @Inline
  private Address getStackTrampolineBridgeIP() {
    return Magic.objectAsAddress(stackTrampolineBridgeInstructions);
  }

  /** @return the hijacked return address */
  @Inline
  public Address getTrampolineHijackedReturnAddress() {
    return hijackedReturnAddress;
  }

  /**
   * Determine whether a given method is the stack trampoline
   *
   * @param ip the code to be checked
   * @return <code>true</code> if the code is the stack trampoline.
   */
  @Inline
  public static boolean isTrampolineIP(Address ip) {
    return getCurrentThread().getStackTrampolineBridgeIP().EQ(ip);
  }

  /**
   * Given a frame that has been hijacked by the stack trampoline,
   * return the real (hijacked) return address.
   *
   * @param hijackedFp a frame that has been hijacked by the stack trampoline
   * @return the return address for the frame that was hijacked.
   */
  @Uninterruptible
  public static Address getHijackedReturnAddress(Address hijackedFp) {
    if (VM.VerifyAssertions) VM._assert(isTrampolineIP(Magic.getReturnAddressUnchecked(hijackedFp)));
    RVMThread t = getCurrentThread();
      if (!t.hijackedReturnCalleeFp.EQ(hijackedFp)) {
        for (int tid = 0; tid < nextSlot; tid++) {
          t = threadBySlot[tid];
          if (t != null && t.hijackedReturnCalleeFp.EQ(hijackedFp))
            break;
        }
      }
      if (VM.VerifyAssertions) VM._assert(t.hijackedReturnCalleeFp.EQ(hijackedFp),
          "No matching thread found");
      return t.hijackedReturnAddress;
  }

  /**
   * Dump the specified frame in a format useful for debugging the stack
   * trampoline
   *
   * @param fp The frame to be dumped.
   */
  private static void dumpFrame(Address fp) {
    final Offset returnAddressOffset = StackFrameLayout.getStackFrameReturnAddressOffset();
    final Offset methodIDOffset = StackFrameLayout.getStackFrameMethodIDOffset();
    Address sp = fp.minus(40);
    VM.sysWriteln("--");
    Address nextFp = Magic.getCallerFramePointer(fp);
    while (sp.LE(nextFp)) {
      VM.sysWrite("[", sp, "]");
      if (sp.EQ(fp) || sp.EQ(nextFp)) {
        VM.sysWrite("* ");
      } else if (sp.EQ(fp.plus(returnAddressOffset)) || sp.EQ(nextFp.plus(returnAddressOffset))) {
        VM.sysWrite("R ");
      }  else if (sp.EQ(fp.plus(methodIDOffset)) || sp.EQ(nextFp.plus(methodIDOffset))) {
        VM.sysWrite("M ");
      } else {
        VM.sysWrite(" ");
      }
      VM.sysWriteln(sp.loadInt());
      sp = sp.plus(4);
    }
  }

  /**
   * @return the caller of the frame in which the trampoline is installed (STACKFRAME_SENTINEL_FP by default)
   */
  public Address getNextUnencounteredFrame() {
    return hijackedReturnCallerFp.EQ(StackFrameLayout.getStackFrameSentinelFP()) ? hijackedReturnCallerFp : Magic.getCallerFramePointer(hijackedReturnCallerFp);
  }

  /**
   * Yieldpoint taken in epilogue.
   */
  @BaselineSaveLSRegisters
  // Save all non-volatile registers in prologue
  @NoOptCompile
  @NoInline
  // We should also have a pragma that saves all non-volatiles in opt compiler,
  // BaselineExecutionStateExtractor.java, should then restore all non-volatiles
  // before stack replacement
  // TODO fix this -- related to SaveVolatile
  @Entrypoint
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
  public static void yieldpointFromEpilogue() {
    Address fp = Magic.getFramePointer();
    yieldpoint(EPILOGUE, fp);
  }

  /*
   * Support for suspend/resume
   */

  /**
   * Suspend execution of current thread until it is resumed. Call only if
   * caller has appropriate security clearance.
   */
  @UnpreemptibleNoWarn("Exceptions may possibly cause yields")
  public void suspend() {
    if (false) VM.sysWriteln("Thread #",getCurrentThreadSlot()," suspending Thread #",getThreadSlot());
    ObjectModel.genericUnlock(thread);
    Throwable rethrow = null;
    try {
      observeExecStatus();
      if (execStatus != IN_JAVA && execStatus != IN_JAVA_TO_BLOCK &&
          execStatus != IN_NATIVE && execStatus != BLOCKED_IN_NATIVE &&
          execStatus != BLOCKED_IN_JNI && execStatus != IN_JNI) {
        throw new IllegalThreadStateException(
          "Cannot suspend a thread that is not running.");
      }
      block(suspendBlockAdapter);
    } catch (Throwable t) {
      rethrow = t;
    }
    ObjectModel.genericLock(thread);
    if (rethrow != null)
      RuntimeEntrypoints.athrow(rethrow);
  }

  /**
   * Resume execution of a thread that has been suspended. Call only if caller
   * has appropriate security clearance.
   */
  @Interruptible
  public void resume() {
    unblock(suspendBlockAdapter);
  }

  public static void yieldNoHandshake() {
    sysCall.sysThreadYield();
  }

  @UnpreemptibleNoWarn
  public static void yieldWithHandshake() {
    getCurrentThread().checkBlock();
    sysCall.sysThreadYield();
  }
  /**
   * Suspend execution of current thread for specified number of seconds (or
   * fraction).
   *
   * @param ns the number of nanoseconds to sleep for
   * @throws InterruptedException when the sleep is interrupted
   */
  @Interruptible
  public static void sleep(long ns) throws InterruptedException {
    RVMThread t = getCurrentThread();
    t.waiting = Waiting.TIMED_WAITING;
    long atStart = sysCall.sysNanoTime();
    long whenEnd = atStart + ns;
    t.monitor().lockNoHandshake();
    while (!t.hasInterrupt && t.asyncThrowable == null &&
        sysCall.sysNanoTime() < whenEnd) {
      t.monitor().timedWaitAbsoluteWithHandshake(whenEnd);
    }
    boolean throwInterrupt = false;
    Throwable throwThis = null;
    if (t.hasInterrupt) {
      t.hasInterrupt = false;
      throwInterrupt = true;
    }
    if (t.asyncThrowable != null) {
      throwThis = t.asyncThrowable;
      t.asyncThrowable = null;
    }
    t.monitor().unlock();
    t.waiting = Waiting.RUNNABLE;
    if (throwThis != null) {
      RuntimeEntrypoints.athrow(throwThis);
    }
    if (throwInterrupt) {
      throw new InterruptedException("sleep interrupted");
    }
  }

  /**
   * Suspend execution of current thread for specified number of seconds (or
   * fraction). The time from both parameters is added up.
   *
   * @param ns the number of nanoseconds to sleep for
   * @param millis the number of milliseconds to sleep for
   * @throws InterruptedException when the sleep is interrupted
   */
  @Interruptible
  public static void sleep(long millis, int ns) throws InterruptedException {
    sleep(ns + millis * 1000L * 1000L);
  }

  /*
   * Wait and notify support
   */

  @Interruptible
  void waitImpl(Object o, boolean hasTimeout, long whenWakeupNanos) {
    boolean throwInterrupt = false;
    Throwable throwThis = null;
    if (asyncThrowable != null) {
      throwThis = asyncThrowable;
      asyncThrowable = null;
    } else if (!ObjectModel.holdsLock(o, this)) {
      throw new IllegalMonitorStateException("waiting on " + o);
    } else if (hasInterrupt) {
      throwInterrupt = true;
      hasInterrupt = false;
    } else {
      if (STATS) {
        waitTimeStart = Time.currentTimeMillis();
      }
      waiting = hasTimeout ? Waiting.TIMED_WAITING : Waiting.WAITING;
      if (STATS) {
        if (hasTimeout) {
          timedWaitOperations++;
        } else {
          waitOperations++;
        }
      }
      // get lock for object
      Lock l = ObjectModel.getHeavyLock(o, true);

      // release the lock
      l.mutex.lock();
      // this thread is supposed to own the lock on o
      if (VM.VerifyAssertions) VM._assert(l.getOwnerId() == getLockingId());
      RVMThread toAwaken = l.entering.dequeue();
      waitObject = l.getLockedObject();
      waitCount = l.getRecursionCount();
      l.setOwnerId(0);
      l.waiting.enqueue(this);
      l.mutex.unlock();

      // if there was a thread waiting, awaken it
      if (toAwaken != null) {
        // is this where the problem is coming from?
        toAwaken.monitor().lockedBroadcastNoHandshake();
      }
      // block
      monitor().lockNoHandshake();
      while (l.waiting.isQueued(this) && !hasInterrupt && asyncThrowable == null &&
             (!hasTimeout || sysCall.sysNanoTime() < whenWakeupNanos)) {
        if (hasTimeout) {
          monitor().timedWaitAbsoluteWithHandshake(whenWakeupNanos);
        } else {
          monitor().waitWithHandshake();
        }
      }
      // figure out if anything special happened while we were blocked
      if (hasInterrupt) {
        throwInterrupt = true;
        hasInterrupt = false;
      }
      if (asyncThrowable != null) {
        throwThis = asyncThrowable;
        asyncThrowable = null;
      }
      monitor().unlock();
      if (l.waiting.isQueued(this)) {
        l.mutex.lock();
        l.waiting.remove(this); /*
                                 * in case we got here due to an interrupt or a
                                 * stop() rather than a notify
                                 */
        l.mutex.unlock();
        // Note that the above must be done before attempting to acquire
        // the lock, since acquiring the lock may require queueing the thread.
        // But we cannot queue the thread if it is already on another
        // queue.
      }
      // reacquire the lock, restoring the recursion count
      ObjectModel.genericLock(o);
      waitObject = null;
      if (waitCount != 1) { // reset recursion count
        Lock l2 = ObjectModel.getHeavyLock(o, true);
        l2.setRecursionCount(waitCount);
      }
      waiting = Waiting.RUNNABLE;
      if (STATS) {
        totalWaitTime += (sysCall.sysCurrentTimeMillis() - waitTimeStart);
      }
    }
    // check if we should exit in a special way
    if (throwThis != null) {
      RuntimeEntrypoints.athrow(throwThis);
    }
    if (throwInterrupt) {
      RuntimeEntrypoints.athrow(new InterruptedException("sleep interrupted"));
    }
  }

  /**
   * Support for Java {@link java.lang.Object#wait()} synchronization primitive.
   *
   * @param o
   *          the object synchronized on
   */
  @Interruptible
  /* only loses control at expected points -- I think -dave */
  public static void wait(Object o) {
    getCurrentThread().waitImpl(o, false, 0);
  }

  /**
   * Support for Java {@link java.lang.Object#wait()} synchronization primitive.
   *
   * @param o
   *          the object synchronized on
   * @param millis
   *          the number of milliseconds to wait for notification
   */
  @Interruptible
  public static void wait(Object o, long millis) {
    long currentNanos = sysCall.sysNanoTime();
    getCurrentThread().waitImpl(o, true, currentNanos + millis * 1000 * 1000);
  }

  long getTotalWaitingCount() {
    if (STATS) {
      return waitOperations + timedWaitOperations;
    } else {
      return -1L;
    }
  }

  long getTotalWaitedTime() {
    if (STATS) {
      return totalWaitTime;
    } else {
      return -1;
    }
  }

  /**
   * Support for RTSJ- and pthread-style absolute wait.
   *
   * @param o
   *          the object synchronized on
   * @param whenNanos
   *          the absolute time in nanoseconds when we should wake up
   */
  @Interruptible
  public static void waitAbsoluteNanos(Object o, long whenNanos) {
    getCurrentThread().waitImpl(o, true, whenNanos);
  }

  @UnpreemptibleNoWarn("Possible context when generating exception")
  public static void raiseIllegalMonitorStateException(String msg, Object o) {
    throw new IllegalMonitorStateException(msg + (o == null ? "<null>" : o.toString()));
  }

  /**
   * Support for Java {@link java.lang.Object#notify()} synchronization
   * primitive.
   *
   * @param o the object synchronized on
   */
  @Interruptible
  public static void notify(Object o) {
    if (STATS)
      notifyOperations++;
    Lock l = ObjectModel.getHeavyLock(o, false);
    if (l == null)
      return;
    // the reason for locking: when inflating a lock we *first* install it in the status
    // word and *then* initialize its state.  but fortunately, we do so while holding
    // the lock's mutex.  thus acquiring the lock's mutex is the only way to ensure that
    // we see the lock's state after initialization.
    l.mutex.lock();
    int owner = l.getOwnerId();
    l.mutex.unlock();
    int me = getCurrentThread().getLockingId();
    if (owner != me) {
      raiseIllegalMonitorStateException("notifying (expected lock to be held by " +
          me + "(" + getCurrentThread().getLockingId() + ") but was held by " +
          owner + "(" + l.getOwnerId() + ")) ", o);
    }
    l.mutex.lock();
    RVMThread toAwaken = l.waiting.dequeue();
    l.mutex.unlock();
    if (toAwaken != null) {
      toAwaken.monitor().lockedBroadcastNoHandshake();
    }
  }

  /**
   * Support for Java synchronization primitive.
   *
   * @param o the object synchronized on
   * @see java.lang.Object#notifyAll
   */
  @Interruptible
  public static void notifyAll(Object o) {
    if (STATS)
      notifyAllOperations++;
    Lock l = ObjectModel.getHeavyLock(o, false);
    if (l == null)
      return;
    l.mutex.lock();
    int owner = l.getOwnerId();
    l.mutex.unlock();
    if (owner != getCurrentThread().getLockingId()) {
      raiseIllegalMonitorStateException("notifying all (expected lock to be held by " +
          getCurrentThread().getLockingId() + " but was held by " + l.getOwnerId() +
          ") ", o);
    }
    for (;;) {
      l.mutex.lock();
      RVMThread toAwaken = l.waiting.dequeue();
      l.mutex.unlock();
      if (toAwaken == null)
        break;
      toAwaken.monitor().lockedBroadcastNoHandshake();
    }
  }

  public void stop(Throwable cause) {
    monitor().lockNoHandshake();
    asyncThrowable = cause;
    takeYieldpoint = 1;
    monitor().broadcast();
    monitor().unlock();
  }

  /*
   * Park and unpark support
   */
  @Interruptible
  public void park(boolean isAbsolute, long time) throws Throwable {
    if (parkingPermit) {
      // fast path
      parkingPermit = false;
      return;
    }
    // massive retardation. someone might be holding the java.lang.Thread lock.
    boolean holdsLock = holdsLock(thread);
    if (holdsLock)
      ObjectModel.genericUnlock(thread);
    boolean hasTimeout;
    long whenWakeupNanos;
    hasTimeout = (time != 0);
    if (isAbsolute) {
      whenWakeupNanos = time;
    } else {
      whenWakeupNanos = sysCall.sysNanoTime() + time;
    }
    Throwable throwThis = null;
    monitor().lockNoHandshake();
    waiting = hasTimeout ? Waiting.TIMED_WAITING : Waiting.WAITING;
    while (!parkingPermit && !hasInterrupt && asyncThrowable == null &&
           (!hasTimeout || sysCall.sysNanoTime() < whenWakeupNanos)) {
      if (hasTimeout) {
        monitor().timedWaitAbsoluteWithHandshake(whenWakeupNanos);
      } else {
        monitor().waitWithHandshake();
      }
    }
    waiting = Waiting.RUNNABLE;
    parkingPermit = false;
    if (asyncThrowable != null) {
      throwThis = asyncThrowable;
      asyncThrowable = null;
    }
    monitor().unlock();

    if (holdsLock)
      ObjectModel.genericLock(thread);

    if (throwThis != null) {
      throw throwThis;
    }
  }

  @Interruptible
  public void unpark() {
    monitor().lockNoHandshake();
    parkingPermit = true;
    monitor().broadcast();
    monitor().unlock();
  }

  /**
   * Get this thread's id for use in lock ownership tests. This is just the
   * thread's slot as returned by {@link #getThreadSlot()}, shifted appropriately
   * so it can be directly used in the ownership tests.
   *
   * @return the thread's id for use in lock owner ship tests
   */
  public int getLockingId() {
    return lockingId;
  }

  /**
   * Provides a skeleton implementation for use in soft handshakes.
   * <p>
   * During a soft handshake, the requesting thread waits for all mutator threads
   * (i.e. non-gc threads) to perform a requested action.
   */
  @Uninterruptible
  public abstract static class SoftHandshakeVisitor {
    /**
     * Sets whatever flags need to be set to signal that the given thread should
     * perform some action when it acknowledges the soft handshake.
     * <p>
     * This method is only called for threads for which {@link #includeThread(RVMThread)}
     * is {@code true}.
     * <p>
     * This method is called with the thread's monitor held, but while the
     * thread may still be running. This method is not called on mutators that
     * have indicated that they are about to terminate.
     *
     * @param t the thread that will be processed
     * @return {@code false} if not interested in this thread, {@code true} otherwise.
     * Returning {@code true} will cause a soft handshake request to be put through.
     */
    public abstract boolean checkAndSignal(RVMThread t);

    /**
     * Called when it is determined that the thread is stuck in native. While
     * this method is being called, the thread cannot return to running Java
     * code. As such, it is safe to perform actions "on the thread's behalf".
     * <p>
     * This implementation does nothing.
     *
     * @param t the thread that's stuck in native
     */
    public void notifyStuckInNative(RVMThread t) {
    }

    /**
     * Checks whether to include the specified thread in the soft handshake.
     * <p>
     * This method will never see any threads from the garbage collector because
     * those are excluded from the soft handshake by design.
     * <p>
     * This implementation always returns {@code true}.
     *
     * @param t The thread to check for inclusion
     * @return {@code true} if the thread should be included.
     */
    public boolean includeThread(RVMThread t) {
      return true;
    }
  }

  @NoCheckStore
  public static int snapshotHandshakeThreads(SoftHandshakeVisitor v) {
    // figure out which threads to consider
    acctLock.lockNoHandshake(); // get a consistent view of which threads are live.

    int numToHandshake = 0;
    for (int i = 0; i < numThreads; ++i) {
      RVMThread t = threads[i];
      // We exclude the following threads from the handshake:
      // -the current thread (because we would deadlock if we included it)
      // -threads that ignore handshakes by design (e.g. the timer thread)
      // -collector threads (because they never yield and we would deadlock if we
      //   tried to wait for them)
      // -the threads that the provided visitor does not want to include
      if (t != RVMThread.getCurrentThread() && !t.ignoreHandshakesAndGC() &&
          !t.isCollectorThread() && v.includeThread(t)) {
        handshakeThreads[numToHandshake++] = t;
      }
    }
    acctLock.unlock();
    return numToHandshake;
  }

  /**
   * Tell each thread to take a yieldpoint and wait until all of them have done
   * so at least once. Additionally, call the visitor on each thread when making
   * the yieldpoint request; the purpose of the visitor is to set any additional
   * fields as needed to make specific requests to the threads that yield. Note
   * that the visitor's <code>visit()</code> method is called with both the
   * thread's monitor held, and the <code>softHandshakeDataLock</code> held.
   * <p>
   * Currently we only use this mechanism for code patch isync requests on PPC,
   * but this mechanism is powerful enough to be used by sliding-views style
   * concurrent GC.
   *
   * @param v the visitor to use for the handshake
   */
  @NoCheckStore
  @Unpreemptible("Does not perform actions that lead to blocking, but may wait for threads to rendezvous with the soft handshake")
  public static void softHandshake(SoftHandshakeVisitor v) {
    handshakeLock.lockWithHandshake(); /*
                                        * prevent multiple (soft or hard) handshakes
                                        * from proceeding concurrently
                                        */

    int numToHandshake = snapshotHandshakeThreads(v);
    if (VM.VerifyAssertions)
      VM._assert(softHandshakeLeft == 0);

    // in turn, check if each thread needs a handshake, and if so,
    // request one
    for (int i = 0; i < numToHandshake; ++i) {
      RVMThread t = handshakeThreads[i];
      handshakeThreads[i] = null; // help GC
      t.monitor().lockNoHandshake();
      boolean waitForThisThread = false;
      if (!t.isAboutToTerminate && v.checkAndSignal(t)) {
        // CAS the execStatus field
        t.setBlockedExecStatus();
        // Note that at this point if the thread tries to either enter or
        // exit Java code, it will be diverted into either
        // enterNativeBlocked() or checkBlock(), both of which cannot do
        // anything until they acquire the monitor() lock, which we now
        // hold. Thus, the code below can, at its leisure, examine the
        // thread's state and make its decision about what to do, fully
        // confident that the thread's state is blocked from changing.
        if (t.isInJava()) {
          // the thread is currently executing Java code, so we must ensure
          // that it either:
          // 1) takes the next yieldpoint and rendezvous with this soft
          // handshake request (see yieldpoint), or
          // 2) performs the rendezvous when leaving Java code
          // (see enterNativeBlocked, checkBlock, and addAboutToTerminate)
          // either way, we will wait for it to get there before exiting
          // this call, since the caller expects that after softHandshake()
          // returns, no thread will be running Java code without having
          // acknowledged.
          t.softHandshakeRequested = true;
          t.takeYieldpoint = 1;
          waitForThisThread = true;
        } else {
          // the thread is not in Java code (it may be blocked or it may be
          // in native), so we don't have to wait for it since it will
          // do the Right Thing before returning to Java code. essentially,
          // the thread cannot go back to running Java without doing whatever
          // was requested because:
          // A) we've set the execStatus to blocked, and
          // B) we're holding its lock.
          v.notifyStuckInNative(t);
        }
      }
      t.monitor().unlock();

      // NOTE: at this point the thread may already decrement the
      // softHandshakeLeft counter, causing it to potentially go negative.
      // this is unlikely and completely harmless.

      if (waitForThisThread) {
        softHandshakeDataLock.lockNoHandshake();
        softHandshakeLeft++;
        softHandshakeDataLock.unlock();
      }
    }

    // wait for all threads to reach the handshake
    softHandshakeDataLock.lockNoHandshake();
    if (VM.VerifyAssertions)
      VM._assert(softHandshakeLeft >= 0);
    while (softHandshakeLeft > 0) {
      // wait and tell the world that we're off in native land. this way
      // if someone tries to block us at this point (suspend() or GC),
      // they'll know not to wait for us.
      softHandshakeDataLock.waitWithHandshake();
    }
    if (VM.VerifyAssertions)
      VM._assert(softHandshakeLeft == 0);
    softHandshakeDataLock.unlock();

    processAboutToTerminate();

    handshakeLock.unlock();
  }

  /**
   * Checks and clears the need for a soft handshake rendezvous.  This method
   * cannot do anything that leads to a write barrier or allocation.
   *
   * @return whether the soft handshake can be committed
   */
  public boolean softRendezvousCheckAndClear() {
    boolean result = false;
    monitor().lockNoHandshake();
    if (softHandshakeRequested) {
      softHandshakeRequested = false;
      result = true;
    }
    monitor().unlock();
    return result;
  }

  /**
   * Commits the soft handshake rendezvous.  This method cannot do anything
   * that leads to a write barrier or allocation.
   */
  public void softRendezvousCommit() {
    softHandshakeDataLock.lockNoHandshake();
    softHandshakeLeft--;
    if (softHandshakeLeft == 0) {
      softHandshakeDataLock.broadcast();
    }
    softHandshakeDataLock.unlock();
  }

  /**
   * Rendezvous with a soft handshake request. Can only be called when the
   * thread's monitor is held.
   */
  public void softRendezvous() {
    if (softRendezvousCheckAndClear())
      softRendezvousCommit();
  }

  /**
   * Handle requests that required a soft handshake. May be called after we
   * acknowledged the soft handshake. Thus - this is for actions in which it is
   * sufficient for the thread to acknowledge that it plans to act upon the
   * request in the immediate future, rather than that the thread acts upon the
   * request prior to acknowledging.
   * <p>
   * This is almost always called with the monitor() lock held, but that's
   * not guaranteed.  If you need that lock, you can grab it (since it's a
   * recursive lock).  But you should avoid grabbing other sorts of locks since
   * that might cause deadlock.
   */
  void handleHandshakeRequest() {
    // Process request for code-patch memory sync operation
    if (VM.BuildForPowerPC && codePatchSyncRequested) {
      codePatchSyncRequested = false;
      // Q: Is this sufficient? Ask Steve why we don't need to sync
      // icache/dcache. --dave
      // A: Yes, this is sufficient. We (Filip and Dave) talked about it and
      // agree that remote processors only need to execute isync. --Filip
      // make sure not get stale data
      Magic.synchronizeInstructionCache();
    }
    // process memory management requests
    if (flushRequested && activeMutatorContext) {
      MemoryManager.flushMutatorContext();
      flushRequested = false;
    }
    // not really a "soft handshake" request but we handle it here anyway
    if (asyncDebugRequestedForThisThread) {
      asyncDebugRequestedForThisThread = false;
      dumpLock.lockNoHandshake();
      VM.sysWriteln("Handling async stack trace request...");
      dump();
      VM.sysWriteln();
      dumpStack();
      dumpLock.unlock();
    }
  }

  /**
   * Stop all mutator threads. This is current intended to be run by a single thread.
   *
   * Fixpoint until there are no threads that we haven't blocked. Fixpoint is needed to
   * catch the (unlikely) case that a thread spawns another thread while we are waiting.
   */
  @NoCheckStore
  @Unpreemptible
  public static void blockAllMutatorsForGC() {
    RVMThread.handshakeLock.lockNoHandshake();
    while (true) {
      // (1) Find all the threads that need to be blocked for GC
      RVMThread.acctLock.lockNoHandshake();
      int numToHandshake = 0;
      for (int i = 0; i < RVMThread.numThreads; i++) {
        RVMThread t = RVMThread.threads[i];
        if (!t.isCollectorThread() && !t.ignoreHandshakesAndGC()) {
          RVMThread.handshakeThreads[numToHandshake++] = t;
        }
      }
      RVMThread.acctLock.unlock();

      // (2) Remove any threads that have already been blocked from the list.
      for (int i = 0; i < numToHandshake; i++) {
        RVMThread t = RVMThread.handshakeThreads[i];
        t.monitor().lockNoHandshake();
        if (t.blockedFor(RVMThread.gcBlockAdapter) || RVMThread.notRunning(t.asyncBlock(RVMThread.gcBlockAdapter))) {
          // Already blocked or not running, remove.
          RVMThread.handshakeThreads[i--] = RVMThread.handshakeThreads[--numToHandshake];
          RVMThread.handshakeThreads[numToHandshake] = null; // help GC
        }
        t.monitor().unlock();
      }

      // (3) Quit trying to block threads if all threads are either blocked
      //     or not running (a thread is "not running" if it is NEW or TERMINATED;
      //     in the former case it means that the thread has not had start()
      //     called on it while in the latter case it means that the thread
      //     is either in the TERMINATED state or is about to be in that state
      //     real soon now, and will not perform any heap-related work before
      //     terminating).
      if (numToHandshake == 0) break;

      // (4) Request a block for GC from all other threads.
      for (int i = 0; i < numToHandshake; i++) {
        if (false) VM.sysWriteln("Waiting for ", RVMThread.handshakeThreads[i].getThreadSlot(), " to block.");
        RVMThread t = RVMThread.handshakeThreads[i];
        RVMThread.observeExecStatusAtSTW(t.block(RVMThread.gcBlockAdapter));
        RVMThread.handshakeThreads[i] = null; // help GC
      }
    }
    RVMThread.handshakeLock.unlock();

    // Deal with terminating threads to ensure that all threads are either dead to MMTk or stopped above.
    RVMThread.processAboutToTerminate();
  }

  /**
   * Unblock all mutators blocked for GC.
   */
  @NoCheckStore
  @Unpreemptible
  public static void unblockAllMutatorsForGC() {
    RVMThread.handshakeLock.lockNoHandshake();
    RVMThread.acctLock.lockNoHandshake();
    int numToHandshake = 0;
    for (int i = 0; i < RVMThread.numThreads; i++) {
      RVMThread t = RVMThread.threads[i];
      if (!t.isCollectorThread() && !t.ignoreHandshakesAndGC()) {
        RVMThread.handshakeThreads[numToHandshake++] = t;
      }
    }
    RVMThread.acctLock.unlock();
    for (int i = 0; i < numToHandshake; i++) {
      RVMThread.handshakeThreads[i].unblock(RVMThread.gcBlockAdapter);
      RVMThread.handshakeThreads[i] = null; // Help GC
    }
    RVMThread.handshakeLock.unlock();
  }

  @Uninterruptible
  public static class HardHandshakeVisitor {
    public boolean includeThread(RVMThread t) {
      return true;
    }
  }

  @Uninterruptible
  @NonMoving
  static class AllButGCHardHandshakeVisitor extends HardHandshakeVisitor {
    @Override
    public boolean includeThread(RVMThread t) {
      return !t.isCollectorThread();
    }
  }

  public static final AllButGCHardHandshakeVisitor allButGC =
    new AllButGCHardHandshakeVisitor();

  static long totalSuspendTime;
  static long totalResumeTime;

  @Unpreemptible
  @NoCheckStore
  public static void hardHandshakeSuspend(BlockAdapter ba,
                                          HardHandshakeVisitor hhv) {
    long before = sysCall.sysNanoTime();

    RVMThread current = getCurrentThread();

    handshakeLock.lockWithHandshake();
    int numLockedLocks = 0;
    for (int i = 0; i < nextSlot;++i) {
      Monitor l = communicationLockBySlot[i];
      if (l != null) {
        l.lockWithHandshake();
        numLockedLocks++;
      }
    }

    // fixpoint until there are no threads that we haven't blocked.
    // fixpoint is needed in case some thread spawns another thread
    // while we're waiting.  that is unlikely but possible.
    for (;;) {
      acctLock.lockNoHandshake();
      int numToHandshake = 0;
      for (int i = 0; i < numThreads;++i) {
        RVMThread t = threads[i];
        if (t != current &&
            !t.ignoreHandshakesAndGC() &&
            hhv.includeThread(t)) {
          handshakeThreads[numToHandshake++] = t;
        }
      }
      acctLock.unlock();

      for (int i = 0; i < numToHandshake;++i) {
        RVMThread t = handshakeThreads[i];
        t.monitor().lockNoHandshake();
        if (t.blockedFor(ba) ||
            notRunning(t.asyncBlock(ba))) {
          // already blocked or not running, remove
          handshakeThreads[i--] = handshakeThreads[--numToHandshake];
          handshakeThreads[numToHandshake] = null; // help GC
        }
        t.monitor().unlock();
      }
      // quit trying to block threads if all threads are either blocked
      // or not running (a thread is "not running" if it is NEW or TERMINATED;
      // in the former case it means that the thread has not had start()
      // called on it while in the latter case it means that the thread
      // is either in the TERMINATED state or is about to be in that state
      // real soon now, and will not perform any heap-related stuff before
      // terminating).
      if (numToHandshake == 0) break;
      for (int i = 0; i < numToHandshake;++i) {
        RVMThread t = handshakeThreads[i];
        observeExecStatusAtSTW(t.block(ba));
        handshakeThreads[i] = null; // help GC
      }
    }

    processAboutToTerminate(); /*
                                * ensure that any threads that died while
                                * we were stopping the world notify us
                                * that they had stopped.
                                */

    int numUnlockedLocks = 0;
    for (int i = 0; i < nextSlot;++i) {
      Monitor l = communicationLockBySlot[i];
      if (l != null) {
        l.unlock();
        numUnlockedLocks++;
      }
    }
    if (VM.VerifyAssertions) VM._assert(numLockedLocks == numUnlockedLocks);
    handshakeLock.unlock();

    if (false) {
      long after = sysCall.sysNanoTime();
      totalSuspendTime += after - before;
      VM.sysWriteln("Stopping the world took ",(after - before)," ns (",totalSuspendTime," ns total)");
    }
  }

  @NoCheckStore
  @Unpreemptible
  public static void hardHandshakeResume(BlockAdapter ba,
                                         HardHandshakeVisitor hhv) {
    long before = sysCall.sysNanoTime();

    handshakeLock.lockWithHandshake();

    RVMThread current = getCurrentThread();
    acctLock.lockNoHandshake();
    int numToHandshake = 0;
    for (int i = 0; i < numThreads;++i) {
      RVMThread t = threads[i];
      if (t != current &&
          !t.ignoreHandshakesAndGC() &&
          hhv.includeThread(t)) {
        handshakeThreads[numToHandshake++] = t;
      }
    }
    acctLock.unlock();
    for (int i = 0; i < numToHandshake;++i) {
      handshakeThreads[i].unblock(ba);
      handshakeThreads[i] = null; // help GC
    }

    handshakeLock.unlock();

    if (false) {
      long after = sysCall.sysNanoTime();
      totalResumeTime += after - before;
      VM.sysWriteln("Resuming the world took ",(after - before)," ns (",totalResumeTime," ns total)");
    }
  }

  @Unpreemptible
  public static void hardHandshakeSuspend() {
    hardHandshakeSuspend(handshakeBlockAdapter,allButGC);
  }

  @Unpreemptible
  public static void hardHandshakeResume() {
    hardHandshakeResume(handshakeBlockAdapter,allButGC);
  }

  /**
   * Process a taken yieldpoint.
   *
   * @param whereFrom source of the yieldpoint (e.g. backedge)
   * @param yieldpointServiceMethodFP the frame pointer of the service
   *  method that called this method
   */
  @Unpreemptible("May block if the thread was asked to do so but otherwise does not perform actions that may lead to blocking")
  public static void yieldpoint(int whereFrom, Address yieldpointServiceMethodFP) {
    RVMThread t = getCurrentThread();
    boolean wasAtYieldpoint = t.atYieldpoint;
    t.atYieldpoint = true;
    t.yieldpointsTaken++;
    // If thread is in critical section we can't do anything right now, defer
    // until later
    // we do this without acquiring locks, since part of the point of disabling
    // yieldpoints is to ensure that locks are not "magically" acquired
    // through unexpected yieldpoints. As well, this makes code running with
    // yieldpoints disabled more predictable. Note furthermore that the only
    // race here is setting takeYieldpoint to 0. But this is perfectly safe,
    // since we are guaranteeing that a yieldpoint will run after we emerge from
    // the no-yieldpoints code. At worst, setting takeYieldpoint to 0 will be
    // lost (because some other thread sets it to non-0), but in that case we'll
    // just come back here and reset it to 0 again.
    if (!t.yieldpointsEnabled()) {
      if (VM.VerifyAssertions)
        VM._assert(!t.yieldToOSRRequested);
      if (traceBlock && !wasAtYieldpoint) {
        VM.sysWriteln("Thread #", t.threadSlot, " deferring yield!");
        dumpStack();
      }
      t.yieldpointRequestPending = true;
      t.takeYieldpoint = 0;
      t.atYieldpoint = false;
      return;
    }
    t.yieldpointsTakenFully++;

    Throwable throwThis = null;
    t.monitor().lockNoHandshake();

    int takeYieldpointVal = t.takeYieldpoint;
    if (takeYieldpointVal != 0) {
      t.takeYieldpoint = 0;
      // do two things: check if we should be blocking, and act upon
      // handshake requests. This also has the effect of reasserting that
      // we are in fact IN_JAVA (as opposed to IN_JAVA_TO_BLOCK).
      t.checkBlock();

      // Process timer interrupt event
      if (t.timeSliceExpired != 0) {
        t.timeSliceExpired = 0;

        if (t.yieldForCBSCall || t.yieldForCBSMethod) {
          /*
           * CBS Sampling is still active from previous quantum. Note that fact,
           * but leave all the other CBS parameters alone.
           */
        } else {
          if (VM.CBSCallSamplesPerTick > 0) {
            t.yieldForCBSCall = true;
            t.takeYieldpoint = -1;
            t.firstCBSCallSample++;
            t.firstCBSCallSample = t.firstCBSCallSample % VM.CBSCallSampleStride;
            t.countdownCBSCall = t.firstCBSCallSample;
            t.numCBSCallSamples = VM.CBSCallSamplesPerTick;
          }

          if (VM.CBSMethodSamplesPerTick > 0) {
            t.yieldForCBSMethod = true;
            t.takeYieldpoint = -1;
            t.firstCBSMethodSample++;
            t.firstCBSMethodSample = t.firstCBSMethodSample % VM.CBSMethodSampleStride;
            t.countdownCBSMethod = t.firstCBSMethodSample;
            t.numCBSMethodSamples = VM.CBSMethodSamplesPerTick;
          }
        }

        if (VM.BuildForAdaptiveSystem) {
          RuntimeMeasurements.takeTimerSample(whereFrom,
              yieldpointServiceMethodFP);
        }
        if (VM.BuildForAdaptiveSystem) {
          OSRListener
              .checkForOSRPromotion(whereFrom, yieldpointServiceMethodFP);
        }
      }

      if (t.yieldForCBSCall) {
        if (!(whereFrom == BACKEDGE || whereFrom == OSROPT)) {
          if (--t.countdownCBSCall <= 0) {
            if (VM.BuildForAdaptiveSystem) {
              // take CBS sample
              RuntimeMeasurements.takeCBSCallSample(whereFrom,
                  yieldpointServiceMethodFP);
            }
            t.countdownCBSCall = VM.CBSCallSampleStride;
            t.numCBSCallSamples--;
            if (t.numCBSCallSamples <= 0) {
              t.yieldForCBSCall = false;
            }
          }
        }
        if (t.yieldForCBSCall) {
          t.takeYieldpoint = -1;
        }
      }

      if (t.yieldForCBSMethod) {
        if (--t.countdownCBSMethod <= 0) {
          if (VM.BuildForAdaptiveSystem) {
            // take CBS sample
            RuntimeMeasurements.takeCBSMethodSample(whereFrom,
                yieldpointServiceMethodFP);
          }
          t.countdownCBSMethod = VM.CBSMethodSampleStride;
          t.numCBSMethodSamples--;
          if (t.numCBSMethodSamples <= 0) {
            t.yieldForCBSMethod = false;
          }
        }
        if (t.yieldForCBSMethod) {
          t.takeYieldpoint = 1;
        }
      }

      if (VM.BuildForAdaptiveSystem && t.yieldToOSRRequested) {
        t.yieldToOSRRequested = false;
        OSRListener.handleOSRFromOpt(yieldpointServiceMethodFP);
      }

      // what is the reason for this? and what was the reason for doing
      // a thread switch following the suspension in the OSR trigger code?
      // ... it seems that at least part of the point here is that if a
      // thread switch was desired for other reasons, then we need to ensure
      // that between when this runs and when the glue code runs there will
      // be no interleaved GC; obviously if we did this before the thread
      // switch then there would be the possibility of interleaved GC.
      if (VM.BuildForAdaptiveSystem && t.isWaitingForOsr) {
        if (VM.BuildForIA32) {
          org.jikesrvm.osr.ia32.PostThreadSwitch.postProcess(t);
        } else {
          if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
          org.jikesrvm.osr.ppc.PostThreadSwitch.postProcess(t);
        }
      }
      if (t.asyncThrowable != null) {
        throwThis = t.asyncThrowable;
        t.asyncThrowable = null;
      }
    }
    t.monitor().unlock();
    t.atYieldpoint = false;
    if (throwThis != null) {
      throwFromUninterruptible(throwThis);
    }
  }

  @Unpreemptible
  private static void throwFromUninterruptible(Throwable e) {
    RuntimeEntrypoints.athrow(e);
  }

  /**
   * Change the size of the currently executing thread's stack.
   *
   * @param newSize
   *          new size (in bytes)
   * @param exceptionRegisters
   *          register state at which stack overflow trap was encountered (null
   *          --&gt; normal method call, not a trap)
   */
  @Unpreemptible("May block due to allocation")
  public static void resizeCurrentStack(int newSize,
      AbstractRegisters exceptionRegisters) {
    if (!getCurrentThread().hijackedReturnAddress.isZero()) {
      /* stack resizing currently unsupported with return barrier */
      VM.sysFail("system error: resizing stack while return barrier enabled (currently unsupported)");
    }
    if (traceAdjustments)
      VM.sysWriteln("Thread: resizeCurrentStack");
    if (MemoryManager.gcInProgress()) {
      VM.sysFail("system error: resizing stack while GC is in progress");
    }
    byte[] newStack = MemoryManager.newStack(newSize);
    getCurrentThread().disableYieldpoints();
    transferExecutionToNewStack(newStack, exceptionRegisters);
    getCurrentThread().enableYieldpoints();
    if (traceAdjustments) {
      RVMThread t = getCurrentThread();
      VM.sysWrite("Thread: resized stack ", t.getThreadSlot());
      VM.sysWrite(" to ", t.stack.length / 1024);
      VM.sysWriteln("k");
    }
  }

  @NoInline
  @BaselineNoRegisters
  // this method does not do a normal return and hence does not execute epilogue
  // --> non-volatiles not restored!
  private static void transferExecutionToNewStack(byte[] newStack, AbstractRegisters exceptionRegisters) {
    // prevent opt compiler from inlining a method that contains a magic
    // (returnToNewStack) that it does not implement.

    RVMThread myThread = getCurrentThread();
    byte[] myStack = myThread.stack;

    // initialize new stack with live portion of stack we're
    // currently running on
    //
    // lo-mem hi-mem
    // |<---myDepth----|
    // +----------+---------------+
    // | empty | live |
    // +----------+---------------+
    // ^myStack ^myFP ^myTop
    //
    // +-------------------+---------------+
    // | empty | live |
    // +-------------------+---------------+
    // ^newStack ^newFP ^newTop
    //
    Address myTop = Magic.objectAsAddress(myStack).plus(myStack.length);
    Address newTop = Magic.objectAsAddress(newStack).plus(newStack.length);

    Address myFP = Magic.getFramePointer();
    Offset myDepth = myTop.diff(myFP);
    Address newFP = newTop.minus(myDepth);

    // The frame pointer addresses the top of the frame on powerpc and
    // the bottom
    // on intel. if we copy the stack up to the current
    // frame pointer in here, the
    // copy will miss the header of the intel frame. Thus we make another
    // call
    // to force the copy. A more explicit way would be to up to the
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
    myThread.stackLimit = Magic.objectAsAddress(newStack).plus(StackFrameLayout.getStackSizeGuard());

    // return to caller, resuming execution on new stack
    // (original stack now abandoned)
    //
    if (VM.BuildForPowerPC) {
      Magic.returnToNewStack(Magic.getCallerFramePointer(newFP));
    } else if (VM.BuildForIA32) {
      Magic.returnToNewStack(newFP);
    }

    if (VM.VerifyAssertions)
      VM._assert(VM.NOT_REACHED);
  }

  /**
   * This (suspended) thread's stack has been moved. Fixup register and memory
   * references to reflect its new position.
   *
   * @param delta
   *          displacement to be applied to all interior references
   */
  public void fixupMovedStack(Offset delta) {
    if (traceAdjustments)
      VM.sysWriteln("Thread: fixupMovedStack");

    if (!contextRegisters.getInnermostFramePointer().isZero()) {
      adjustRegisters(contextRegisters, delta);
    }
    if ((exceptionRegisters.getInUse()) &&
        (exceptionRegisters.getInnermostFramePointer().NE(Address.zero()))) {
      adjustRegisters(exceptionRegisters, delta);
    }
    if (!contextRegisters.getInnermostFramePointer().isZero()) {
      adjustStack(stack, contextRegisters.getInnermostFramePointer(), delta);
    }
    stackLimit = stackLimit.plus(delta);
  }

  /**
   * A thread's stack has been moved or resized. Adjust registers to reflect new
   * position.
   *
   * @param registers
   *          registers to be adjusted
   * @param delta
   *          displacement to be applied
   */
  private static void adjustRegisters(AbstractRegisters registers, Offset delta) {
    if (traceAdjustments)
      VM.sysWriteln("Thread: adjustRegisters");

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
    // (1) frames from all compilers on IA32 need to update ESP
    int compiledMethodId = Magic.getCompiledMethodID(registers
        .getInnermostFramePointer());
    if (compiledMethodId != StackFrameLayout.getInvisibleMethodID()) {
      if (VM.BuildForIA32) {
        registers.adjustESP(delta, traceAdjustments);
      }
      if (traceAdjustments) {
        CompiledMethod compiledMethod = CompiledMethods
            .getCompiledMethod(compiledMethodId);
        VM.sysWrite(" method=");
        VM.sysWrite(compiledMethod.getMethod());
        VM.sysWriteln();
      }
    }
  }

  /**
   * A thread's stack has been moved or resized. Adjust internal pointers to
   * reflect new position.
   *
   * @param stack
   *          stack to be adjusted
   * @param fp
   *          pointer to its innermost frame
   * @param delta
   *          displacement to be applied to all its interior references
   */
  private static void adjustStack(byte[] stack, Address fp, Offset delta) {
    if (traceAdjustments)
      VM.sysWriteln("Thread: adjustStack");

    while (Magic.getCallerFramePointer(fp).NE(StackFrameLayout.getStackFrameSentinelFP())) {
      // adjust FP save area
      //
      Magic.setCallerFramePointer(fp, Magic.getCallerFramePointer(fp).plus(
          delta));
      if (traceAdjustments) {
        VM.sysWrite(" fp=", fp.toWord());
      }

      // advance to next frame
      //
      fp = Magic.getCallerFramePointer(fp);
    }
  }

  /**
   * Initialize a new stack with the live portion of the stack we're currently
   * running on.
   *
   * <pre>
   *  lo-mem                                        hi-mem
   *                           |&lt;---myDepth----|
   *                 +----------+---------------+
   *                 |   empty  |     live      |
   *                 +----------+---------------+
   *                  &circ;myStack   &circ;myFP           &circ;myTop
   *       +-------------------+---------------+
   *       |       empty       |     live      |
   *       +-------------------+---------------+
   *        &circ;newStack           &circ;newFP          &circ;newTop
   * </pre>
   *
   * @param newStack space for the new stack
   * @return offset that needs to be applied to all interior references of
   *  the new stack
   */
  private static Offset copyStack(byte[] newStack) {
    RVMThread myThread = getCurrentThread();
    byte[] myStack = myThread.stack;

    Address myTop = Magic.objectAsAddress(myStack).plus(myStack.length);
    Address newTop = Magic.objectAsAddress(newStack).plus(newStack.length);
    Address myFP = Magic.getFramePointer();
    Offset myDepth = myTop.diff(myFP);
    Address newFP = newTop.minus(myDepth);

    // before copying, make sure new stack isn't too small
    //
    if (VM.VerifyAssertions) {
      VM._assert(newFP.GE(Magic.objectAsAddress(newStack).plus(StackFrameLayout.getStackSizeGuard())));
    }

    Memory.memcopy(newFP, myFP, myDepth.toWord().toExtent());

    return newFP.diff(myFP);
  }

  private static ThreadGroup systemThreadGroup;

  public static void setThreadGroupForSystemThreads(
      ThreadGroup systemThreadGroup) {
        RVMThread.systemThreadGroup = systemThreadGroup;
  }

  public static ThreadGroup getThreadGroupForSystemThreads() {
    return RVMThread.systemThreadGroup;
  }

  /**
   * Set the "isDaemon" status of this thread. Although a java.lang.Thread can
   * only have setDaemon invoked on it before it is started, Threads can become
   * daemons at any time. Note: making the last non daemon a daemon will
   * terminate the VM.
   * <p>
   * Note: This method might need to be uninterruptible so it is final, which is
   * why it isn't called setDaemon.
   * <p>
   * Public so that java.lang.Thread can use it.
   *
   * @param on new status for daemon flag
   */
  public void makeDaemon(boolean on) {
    if (daemon == on) {
      // nothing to do
    } else {
      daemon = on;
      if (getExecStatus() == NEW) {
        // thread will start as a daemon
      } else {
        boolean terminateSystem = false;
        acctLock.lockNoHandshake();
        numActiveDaemons += on ? 1 : -1;
        if (numActiveDaemons == numActiveThreads) {
          terminateSystem = true;
        }
        acctLock.unlock();
        if (terminateSystem) {
          if (VM.TraceThreads) {
            trace("Thread", "last non Daemon demonized");
          }
          VM.sysExit(0);
          if (VM.VerifyAssertions)
            VM._assert(VM.NOT_REACHED);
        }
      }
    }
  }

  /**
   * Dump information for all threads, via {@link VM#sysWrite(String)}. Each
   * thread's info is newline-terminated.
   *
   * @param verbosity Ignored.
   */
  public static void dumpAll(int verbosity) {
    for (int i = 0; i < numThreads; i++) {
      RVMThread t = threads[i];
      if (t == null)
        continue;
      VM.sysWrite("Thread ");
      VM.sysWriteInt(t.threadSlot);
      VM.sysWrite(":  ");
      VM.sysWriteHex(Magic.objectAsAddress(t));
      VM.sysWrite("   ");
      t.dump(verbosity);
      // Compensate for t.dump() not newline-terminating info.
      VM.sysWriteln();
    }
  }

  /** @return whether this is the primordial thread, i.e.
   *    the thread that boots the VM before starting the
   *    main thread
*  */
  public boolean isBootThread() {
    return this == bootThread;
  }

  /** @return whether this is the main thread */
  private boolean isMainThread() {
    return thread instanceof MainThread;
  }

  /** @return whether this is a system thread */
  public boolean isSystemThread() {
    return systemThread != null;
  }

  /** @return the collector thread this RVMTHread is running */
  public CollectorThread getCollectorThread() {
    if (VM.VerifyAssertions) VM._assert(isCollectorThread());
    return (CollectorThread)systemThread;
  }

  /** @return the value of {@link #daemon}. */
  public boolean isDaemonThread() {
    return daemon;
  }

  /**
   * @return whether this thread should run concurrently with
   * stop-the-world garbage collection and ignore handshakes
   */
  public boolean ignoreHandshakesAndGC() {
    if (systemThread == null) return false;
    return systemThread instanceof TimerThread;
  }

  /** @return whether the thread started and not terminated */
  public boolean isAlive() {
    monitor().lockNoHandshake();
    observeExecStatus();
    boolean result = execStatus != NEW && execStatus != TERMINATED && !isAboutToTerminate;
    monitor().unlock();
    return result;
  }

  /**
   * Sets the name of the thread
   *
   * @param name the new name for the thread
   * @see java.lang.Thread#setName(String)
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the name of the thread
   *
   * @see java.lang.Thread#getName()
   * @return name of the thread
   */
  public String getName() {
    return name;
  }

  /**
   * Does the currently running Thread hold the lock on an obj?
   *
   * @param obj
   *          the object to check
   * @return whether the thread holds the lock
   * @see java.lang.Thread#holdsLock(Object)
   */
  public boolean holdsLock(Object obj) {
    RVMThread mine = getCurrentThread();
    return ObjectModel.holdsLock(obj, mine);
  }

  /**
   * Was this thread interrupted?
   *
   * @return whether the thread has been interrupted
   * @see java.lang.Thread#isInterrupted()
   */
  public boolean isInterrupted() {
    return hasInterrupt;
  }

  /**
   * Clear the interrupted status of this thread
   *
   * @see java.lang.Thread#interrupted()
   */
  public void clearInterrupted() {
    hasInterrupt = false;
  }

  /**
   * Interrupt this thread
   *
   * @see java.lang.Thread#interrupt()
   */
  @Interruptible
  public void interrupt() {
    monitor().lockNoHandshake();
    hasInterrupt = true;
    monitor().broadcast();
    monitor().unlock();
  }

  /**
   * Get the priority of the thread
   *
   * @return the thread's priority
   * @see java.lang.Thread#getPriority()
   */
  public int getPriority() {
    if (isAlive()) {
      // compute current priority
      priority = sysCall.sysGetThreadPriority(pthread_id, priority_handle) + Thread.NORM_PRIORITY;
    }
    if (tracePriority) {
      VM.sysWriteln("Thread #", getThreadSlot(), " get priority returning: ", priority);
    }
    return priority;
  }

  /**
   * Set the priority of the thread
   *
   * @param priority the thread's priority
   * @see java.lang.Thread#getPriority()
   */
  public void setPriority(int priority) {
    if (isAlive()) {
      int result = sysCall.sysSetThreadPriority(pthread_id, priority_handle, priority - Thread.NORM_PRIORITY);
      if (result == 0) {
        this.priority = priority;
        if (tracePriority) {
          VM.sysWriteln("Thread #", getThreadSlot(), " set priority: ", priority);
        }
      } else {
        // setting priority failed
        if (tracePriority) {
          VM.sysWriteln("Thread #", getThreadSlot(), " failed to set priority: ", priority, ", result: ", result);
        }
      }
    } else {
      if (tracePriority) {
        VM.sysWriteln("Thread #", getThreadSlot(), " set priority: ", priority, " while not running");
      }
      this.priority = priority;
    }
  }

  /**
   * Get the state of the thread in a manner compatible with the Java API
   *
   * @return thread state
   * @see java.lang.Thread#getState()
   */
  @Interruptible
  public Thread.State getState() {
    monitor().lockNoHandshake();
    try {
      observeExecStatus();
      switch (execStatus) {
      case NEW:
        return Thread.State.NEW;
      case IN_JAVA:
      case IN_NATIVE:
      case IN_JNI:
      case IN_JAVA_TO_BLOCK:
      case BLOCKED_IN_NATIVE:
      case BLOCKED_IN_JNI:
        if (isAboutToTerminate) {
          return Thread.State.TERMINATED;
        }
        switch (waiting) {
        case RUNNABLE:
          return Thread.State.RUNNABLE;
        case WAITING:
          return Thread.State.WAITING;
        case TIMED_WAITING:
          return Thread.State.TIMED_WAITING;
        default:
          VM.sysFail("Unknown waiting value: " + waiting);
          return null;
        }
      case TERMINATED:
        return Thread.State.TERMINATED;
      default:
        VM.sysFail("Unknown value of execStatus: " + execStatus);
        return null;
      }
    } finally {
      monitor().unlock();
    }
  }

  /**
   * Wait for the thread to die or for the timeout to occur
   *
   * @param ms
   *          milliseconds to wait
   * @param ns
   *          nanoseconds to wait
   * @throws InterruptedException when the thread is interrupted
   */
  @Interruptible
  public void join(long ms, int ns) throws InterruptedException {
    RVMThread myThread = getCurrentThread();
    if (VM.VerifyAssertions)
      VM._assert(myThread != this);
    if (traceBlock)
      VM.sysWriteln("Joining on Thread #", threadSlot);
    // this uses synchronized because we cannot have one thread acquire
    // another thread's lock using the WithHandshake scheme, as that would result
    // in a thread holding two threads' monitor()s.  using synchronized
    // turns out to be just fine - see comment in terminate().
    synchronized (this) {
      if (ms == 0 && ns == 0) {
        while (!isJoinable) {
          wait(this);
          if (traceBlock)
            VM.sysWriteln("relooping in join on Thread #", threadSlot);
        }
      } else {
        long startNano = Time.nanoTime();
        long whenWakeup = startNano + ms * 1000L * 1000L + ns;
        while (!isJoinable) {
          waitAbsoluteNanos(this, whenWakeup);
          if (Time.nanoTime() >= whenWakeup) {
            break;
          }
          if (traceBlock)
            VM.sysWriteln("relooping in join on Thread #", threadSlot);
        }
      }
    }
  }

  /**
   * Gets live threads.
   * <p>
   * Note: this is an expensive operation operation because we're grabbing
   * the accounting lock and thus prevent the threading system from changing
   * the set of active threads.
   *
   * @return the live threads that ought to be user-visible, i.e.
   *  all threads except the system threads
   */
  @Interruptible
  public static Thread[] getLiveThreadsForJMX() {
    int threadIndex = 0;

    acctLock.lockNoHandshake();
    Thread[] liveThreads = new Thread[numActiveThreads];
    for (int i = 0; i < RVMThread.numThreads; i++) {
      RVMThread t = RVMThread.threads[i];
      if (t.isAlive() && !t.isSystemThread()) {
        Thread javaLangThread = t.getJavaLangThread();
        if (javaLangThread == null) {
          continue;
        }
        boolean enoughSpace = threadIndex < numActiveThreads;
          if (!enoughSpace) {
            // unlock because of imminent (assertion) failure
            acctLock.unlock();

            if (VM.VerifyAssertions) {
              VM._assert(VM.NOT_REACHED,
                  "Not enough space in array for all live threads");
            } else {
              VM.sysFail("Not enough space in array for all live threads");
            }
          }

        liveThreads[threadIndex] = javaLangThread;
        threadIndex++;
      }
    }
    acctLock.unlock();
    return liveThreads;
  }

  /**
   * Counts the stack frames of this thread.
   *
   * @return the number of stack frames in this thread
   */
  @Interruptible
  public int countStackFrames() {
    if (!isSuspended) {
      throw new IllegalThreadStateException(
          "Thread.countStackFrames called on non-suspended thread");
    }
    throw new UnimplementedError();
  }

  /**
   * @return the length of the stack
   */
  public int getStackLength() {
    return stack.length;
  }

  /**
   * @return the stack
   */
  public byte[] getStack() {
    return stack;
  }

  /**
   * @return the thread's exception registers
   */
  public AbstractRegisters getExceptionRegisters() {
    return exceptionRegisters;
  }

  /**
   * @return the thread's context registers (saved registers when thread is
   *         suspended by scheduler).
   */
  public AbstractRegisters getContextRegisters() {
    return contextRegisters;
  }

  /** Set the initial attempt. */
  public void reportCollectionAttempt() {
    collectionAttempt++;
  }

  /** @return the number of collection attempts */
  public int getCollectionAttempt() {
    return collectionAttempt;
  }

  /** Resets the attempts. */
  public void resetCollectionAttempts() {
    collectionAttempt = 0;
  }

  /** @return the physical allocation failed flag. */
  public boolean physicalAllocationFailed() {
    return physicalAllocationFailed;
  }

  /** Set the physical allocation failed flag. */
  public void setPhysicalAllocationFailed() {
    physicalAllocationFailed = true;
  }

  /** Clear the physical allocation failed flag. */
  public void clearPhysicalAllocationFailed() {
    physicalAllocationFailed = false;
  }

  /**
   * @return the outstanding OutOfMemoryError.
   */
  public static OutOfMemoryError getOutOfMemoryError() {
    return outOfMemoryError;
  }

  /**
   * @return number of active threads in the system.
   */
  public static int getNumActiveThreads() {
    return numActiveThreads;
  }

  public static int getNumActiveSystemThreads() {
    return numActiveSystemThreads;
  }

  /**
   * @return number of active daemon threads.
   */
  public static int getNumActiveDaemons() {
    return numActiveDaemons;
  }

  /**
   * Handles uncaught exceptions for subclasses of {@link SystemThread}.
   * Uncaught exceptions for normal threads will end up in that thread's {@link #run()}
   * method which will invoke the thread's uncaught exception handler.
   *
   * @param exceptionObject the exception object that wasn't caught
   * @see #run() run() method of application threads
   * @see SystemThread#run() run() method of system threads
   */
  @Interruptible
  public void handleUncaughtException(Throwable exceptionObject) {
    uncaughtExceptionCount++;

    handlePossibleRecursiveException();
    VM.enableGC();
    if (thread == null) {
      VM.sysWrite("Exception in the primordial thread \"", getName(),
          "\" while booting: ");
    } else {
      // This is output like that of the Sun JDK.
      VM.sysWrite("Exception in thread \"", getName(), "\": ");
    }
    if (exceptionObject instanceof OutOfMemoryError) {
      VM.sysWriteln("   <<No stacktrace available>>");
    } else if (VM.fullyBooted) {
      exceptionObject.printStackTrace();
    }
    getCurrentThread().terminate();
    if (VM.VerifyAssertions)
      VM._assert(VM.NOT_REACHED);
  }

  /** Handle the case of exception handling triggering new exceptions. */
  private void handlePossibleRecursiveException() {
    if (uncaughtExceptionCount > 1 &&
        uncaughtExceptionCount <= VM.maxSystemTroubleRecursionDepth + VM.maxSystemTroubleRecursionDepthBeforeWeStopVMSysWrite) {
      VM.sysWrite("We got an uncaught exception while (recursively) handling ");
      VM.sysWrite(uncaughtExceptionCount - 1);
      VM.sysWrite(" uncaught exception");
      if (uncaughtExceptionCount - 1 != 1) {
        VM.sysWrite("s");
      }
      VM.sysWriteln(".");
    }
    if (uncaughtExceptionCount > VM.maxSystemTroubleRecursionDepth) {
      dumpVirtualMachine();
      VM.dieAbruptlyRecursiveSystemTrouble();
      if (VM.VerifyAssertions)
        VM._assert(VM.NOT_REACHED);
    }
  }

  private static void dumpThread(RVMThread t) {
    if (t == null) {
      VM.sysWrite("none");
    } else {
      VM.sysWrite(t.threadSlot, "(", READABLE_EXEC_STATUS[t.getExecStatus()]);
      if (t.isAboutToTerminate) {
        VM.sysWrite("T");
      }
      if (t.isBlocking) {
        VM.sysWrite("B");
      }
      if (t.isJoinable) {
        VM.sysWrite("J");
      }
      if (t.atYieldpoint) {
        VM.sysWrite("Y");
      }
      VM.sysWrite(")");
    }
  }

  private static void dumpThreadArray(RVMThread[] array, int bound) {
    for (int i = 0; i < bound; ++i) {
      if (i != 0) {
        VM.sysWrite(", ");
      }
      VM.sysWrite(i, ":");
      dumpThread(array[i]);
    }
  }

  private static void dumpThreadSlotArray(int[] array, int bound) {
    for (int i = 0; i < bound; ++i) {
      if (i != 0) {
        VM.sysWrite(", ");
      }
      VM.sysWrite(i, ":");
      int threadSlot = array[i];
      VM.sysWrite(threadSlot, ",");
      dumpThread(threadBySlot[array[i]]);
    }
  }

  private static void dumpThreadArray(String name, RVMThread[] array, int bound) {
    VM.sysWrite(name);
    VM.sysWrite(": ");
    dumpThreadArray(array, bound);
    VM.sysWriteln();
  }

  private static void dumpThreadSlotArray(String name, int[] array, int bound) {
    VM.sysWrite(name);
    VM.sysWrite(": ");
    dumpThreadSlotArray(array, bound);
    VM.sysWriteln();
  }

  public static void dumpAcct() {
    acctLock.lockNoHandshake();
    dumpLock.lockNoHandshake();
    VM.sysWriteln("====== Begin Thread Accounting Dump ======");
    dumpThreadArray("threadBySlot", threadBySlot, nextSlot);
    dumpThreadSlotArray("aboutToTerminate", aboutToTerminate, aboutToTerminateN);
    VM.sysWrite("freeSlots: ");
    for (int i = 0; i < freeSlotN; ++i) {
      if (i != 0) {
        VM.sysWrite(", ");
      }
      VM.sysWrite(i, ":", freeSlots[i]);
    }
    VM.sysWriteln();
    dumpThreadArray("threads", threads, numThreads);
    VM.sysWriteln("====== End Thread Accounting Dump ======");
    dumpLock.unlock();
    acctLock.unlock();
  }

  public void extDump() {
    dump();
    VM.sysWriteln();
    VM.sysWriteln("acquireCount for my monitor: ", monitor().acquireCount);
    VM.sysWriteln("yieldpoints taken: ", yieldpointsTaken);
    VM.sysWriteln("yieldpoints taken fully: ", yieldpointsTakenFully);
    VM.sysWriteln("native entered blocked: ", nativeEnteredBlocked);
    VM.sysWriteln("JNI entered blocked: ", jniEnteredBlocked);
  }

  /**
   * Dump this thread's identifying information, for debugging, via
   * {@link VM#sysWrite(String)}. We do not use any spacing or newline
   * characters. Callers are responsible for space-separating or
   * newline-terminating output.
   */
  public void dump() {
    dump(0);
  }

  /**
   * Dump this thread's identifying information, for debugging, via
   * {@link VM#sysWrite(String)}. We pad to a minimum of leftJustify
   * characters. We do not use any spacing characters. Callers are responsible
   * for space-separating or newline-terminating output.
   *
   * @param leftJustify
   *          minimum number of characters emitted, with any extra characters
   *          being spaces.
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
   * {@link VM#sysWrite(String)}. We do not use any spacing or newline
   * characters. Callers are responsible for space-separating or
   * newline-terminating output.
   *
   * This function avoids write barriers and allocation.
   *
   * @param verbosity
   *          Ignored.
   */
  public void dump(int verbosity) {
    char[] buf = Services.grabDumpBuffer();
    int len = dump(buf);
    VM.sysWrite(buf, len);
    Services.releaseDumpBuffer();
  }

  /**
   * Dump this thread's info, for debugging. Copy the info about it into a
   * destination char array. We do not use any spacing or newline characters.
   *
   * This function may be called during GC; it avoids write barriers and
   * allocation.
   *
   * For this reason, we do not throw an <code>IndexOutOfBoundsException</code>.
   *
   * @param dest
   *          char array to copy the source info into.
   * @param offset
   *          Offset into <code>dest</code> where we start copying
   *
   * @return 1 plus the index of the last character written. If we were to write
   *         zero characters (which we won't) then we would return
   *         <code>offset</code>. This is intended to represent the first
   *         unused position in the array <code>dest</code>. However, it also
   *         serves as a pseudo-overflow check: It may have the value
   *         <code>dest.length</code>, if the array <code>dest</code> was
   *         completely filled by the call, or it may have a value greater than
   *         <code>dest.length</code>, if the info needs more than
   *         <code>dest.length - offset</code> characters of space.
   *
   * -1 if <code>offset</code> is negative.
   */
  public int dump(char[] dest, int offset) {
    offset = Services.sprintf(dest, offset, getThreadSlot()); // id
    if (daemon) {
      offset = Services.sprintf(dest, offset, "-daemon"); // daemon thread?
    }
    if (isBootThread()) {
      offset = Services.sprintf(dest, offset, "-Boot"); // Boot (Primordial)
      // thread
    }
    if (isSystemThread()) {
      offset = Services.sprintf(dest, offset, "-system"); // System Thread
    }
    if (isMainThread()) {
      offset = Services.sprintf(dest, offset, "-main"); // Main Thread
    }
    if (isCollectorThread()) {
      offset = Services.sprintf(dest, offset, "-collector"); // gc thread?
    }
    offset = Services.sprintf(dest, offset, "-");
    offset = Services.sprintf(dest, offset, READABLE_EXEC_STATUS[getExecStatus()]);
    offset = Services.sprintf(dest, offset, "-");
    offset = Services.sprintf(dest, offset, java.lang.JikesRVMSupport
        .getEnumName(waiting));
    if (hasInterrupt || asyncThrowable != null) {
      offset = Services.sprintf(dest, offset, "-interrupted");
    }
    if (isAboutToTerminate) {
      offset = Services.sprintf(dest, offset, "-terminating");
    }
    return offset;
  }

  /**
   * Dump this thread's info, for debugging. Copy the info about it into a
   * destination char array. We do not use any spacing or newline characters.
   * <p>
   * This is identical to calling {@link #dump(char[],int)} with an
   * <code>offset</code> of zero.
   *
   * @param dest array to dump the info into
   *
   * @return see {@link #dump(char[], int)}
   */
  public int dump(char[] dest) {
    return dump(dest, 0);
  }

  /** Dump statistics gather on operations */
  static void dumpStats() {
    VM.sysWrite("FatLocks: ");
    VM.sysWrite(waitOperations);
    VM.sysWriteln(" wait operations");
    VM.sysWrite("FatLocks: ");
    VM.sysWrite(timedWaitOperations);
    VM.sysWriteln(" timed wait operations");
    VM.sysWrite("FatLocks: ");
    VM.sysWrite(notifyOperations);
    VM.sysWriteln(" notify operations");
    VM.sysWrite("FatLocks: ");
    VM.sysWrite(notifyAllOperations);
  }

  /**
   * Prints out message in format {@code "[j] (td) who: what"}, where:
   * <ul>
   *  <li>{@code j = java thread id}
   *  <li>{@code t = numActiveThreads}
   *  <li>{@code d = numActiveDaemon}
   * </ul>
   * The parenthetical values are printed only if {@link #traceDetails} is true.
   * <p>
   * We serialize against a mutex to avoid intermingling debug output from
   * multiple threads.
   *
   * @param who the string for the who parameter
   * @param what the string for the what parameter
   */
  public static void trace(String who, String what) {
    outputLock.lockNoHandshake();
    VM.sysWrite("[");
    RVMThread t = getCurrentThread();
    t.dump();
    VM.sysWrite("] ");
    if (traceDetails) {
      VM.sysWrite("(");
      VM.sysWriteInt(numActiveDaemons);
      VM.sysWrite("/");
      VM.sysWriteInt(numActiveThreads);
      VM.sysWrite(") ");
    }
    VM.sysWrite(who);
    VM.sysWrite(": ");
    VM.sysWrite(what);
    VM.sysWriteln();
    outputLock.unlock();
  }

 /**
  * Prints out message in format {@code "[j] (td) who: what howmany"}, where:
  * <ul>
  *  <li>{@code j = java thread id}
  *  <li>{@code t = numActiveThreads}
  *  <li>{@code d = numActiveDaemon}
  * </ul>
  * The parenthetical values are printed only if {@link #traceDetails} is true.
  * <p>
  * We serialize against a mutex to avoid intermingling debug output from
  * multiple threads.
  *
  * @param who the string for the who parameter
  * @param what the string for the what parameter
  * @param howmany the count for the howmany parameter
  */
  public static void trace(String who, String what, int howmany) {
    _trace(who, what, howmany, false);
  }

  // same as trace, but prints integer value in hex
  //
  public static void traceHex(String who, String what, int howmany) {
    _trace(who, what, howmany, true);
  }

  public static void trace(String who, String what, Address addr) {
    outputLock.lockNoHandshake();
    VM.sysWrite("[");
    getCurrentThread().dump();
    VM.sysWrite("] ");
    if (traceDetails) {
      VM.sysWrite("(");
      VM.sysWriteInt(numActiveDaemons);
      VM.sysWrite("/");
      VM.sysWriteInt(numActiveThreads);
      VM.sysWrite(") ");
    }
    VM.sysWrite(who);
    VM.sysWrite(": ");
    VM.sysWrite(what);
    VM.sysWrite(" ");
    VM.sysWriteHex(addr);
    VM.sysWriteln();
    outputLock.unlock();
  }

  private static void _trace(String who, String what, int howmany, boolean hex) {
    outputLock.lockNoHandshake();
    VM.sysWrite("[");
    // VM.sysWriteInt(RVMThread.getCurrentThread().getThreadSlot());
    getCurrentThread().dump();
    VM.sysWrite("] ");
    if (traceDetails) {
      VM.sysWrite("(");
      VM.sysWriteInt(numActiveDaemons);
      VM.sysWrite("/");
      VM.sysWriteInt(numActiveThreads);
      VM.sysWrite(") ");
    }
    VM.sysWrite(who);
    VM.sysWrite(": ");
    VM.sysWrite(what);
    VM.sysWrite(" ");
    if (hex) {
      VM.sysWriteHex(howmany);
    } else {
      VM.sysWriteInt(howmany);
    }
    VM.sysWriteln();
    outputLock.unlock();
  }

  /**
   * Print interesting scheduler information, starting with a stack traceback.
   * <p>
   * Note: the system could be in a fragile state when this method is called, so
   * we try to rely on as little runtime functionality as possible (eg. use no
   * bytecodes that require RuntimeEntrypoints support).
   *
   * @param message the message to write before the actual traceback
   */
  public static void traceback(String message) {
    if (VM.runningVM && threadingInitialized) {
      outputLock.lockNoHandshake();
    }
    VM.sysWriteln(message);
    tracebackWithoutLock();
    if (VM.runningVM && threadingInitialized) {
      outputLock.unlock();
    }
  }

  public static void traceback(String message, int number) {
    if (VM.runningVM && threadingInitialized) {
      outputLock.lockNoHandshake();
    }
    VM.sysWriteln(message, number);
    tracebackWithoutLock();
    if (VM.runningVM && threadingInitialized) {
      outputLock.unlock();
    }
  }

  static void tracebackWithoutLock() {
    if (VM.runningVM) {
      VM.sysWriteln("Thread #", getCurrentThreadSlot());
      dumpStack(Magic.getCallerFramePointer(Magic.getFramePointer()));
    } else {
      dumpStack();
    }
  }

  /**
   * Dump stack of calling thread, starting at callers frame
   */
  @UninterruptibleNoWarn("Never blocks")
  public static void dumpStack() {
    if (VM.runningVM) {
      VM.sysWriteln("Dumping stack for Thread #", getCurrentThreadSlot());
      dumpStack(Magic.getFramePointer());
    } else {
      StackTraceElement[] elements = (new Throwable(
          "--traceback from Jikes RVM's RVMThread class--")).getStackTrace();
      for (StackTraceElement element : elements) {
        System.err.println(element.toString());
      }
    }
  }

  /**
   * Dump state of a (stopped) thread's stack.
   *
   * @param fp address of starting frame. first frame output is the calling
   * frame of passed frame
   */
  public static void dumpStack(Address fp) {
    if (VM.VerifyAssertions) {
      VM._assert(VM.runningVM);
    }
    Address ip = Magic.getReturnAddress(fp);
    fp = Magic.getCallerFramePointer(fp);
    dumpStack(ip, fp);
  }

  /**
   * Dump state of a (stopped) thread's stack.
   *
   * @param ip instruction pointer for first frame to dump
   * @param fp frame pointer for first frame to dump
   */
  public static void dumpStack(Address ip, Address fp) {
    boolean b = Monitor.lockNoHandshake(dumpLock);
    RVMThread t = getCurrentThread();
    ++t.inDumpStack;
    if (t.inDumpStack > 1 &&
        t.inDumpStack <= VM.maxSystemTroubleRecursionDepth + VM.maxSystemTroubleRecursionDepthBeforeWeStopVMSysWrite) {
      VM.sysWrite("RVMThread.dumpStack(): in a recursive call, ");
      VM.sysWrite(t.inDumpStack);
      VM.sysWriteln(" deep.");
    }
    if (t.inDumpStack > VM.maxSystemTroubleRecursionDepth) {
      VM.dieAbruptlyRecursiveSystemTrouble();
      if (VM.VerifyAssertions)
        VM._assert(VM.NOT_REACHED);
    }

    if (fp.EQ(StackFrameLayout.getStackFrameSentinelFP())) {
      VM.sysWriteln("Empty stack");
    } else if (!isAddressValidFramePointer(fp)) {
      VM.sysWrite("Bogus looking frame pointer: ", fp);
      VM.sysWriteln(" not dumping stack");
    } else {
      try {
        VM.sysWriteln("-- Stack --");
        while (Magic.getCallerFramePointer(fp).NE(
            StackFrameLayout.getStackFrameSentinelFP())) {

          // if code is outside of RVM heap, assume it to be native code,
          // skip to next frame
          if (!MemoryManager.addressInVM(ip)) {
            showMethod("native frame", fp);
            ip = Magic.getReturnAddress(fp);
            fp = Magic.getCallerFramePointer(fp);
          } else {

            int compiledMethodId = Magic.getCompiledMethodID(fp);
            boolean idOutOfRange = compiledMethodId > CompiledMethods.numCompiledMethods() ||
                compiledMethodId < 1;
            VM.sysWrite("(", fp);
            VM.sysWrite(" ", compiledMethodId, ")");
            if (compiledMethodId == StackFrameLayout.getInvisibleMethodID()) {
              showMethod("invisible method", fp);
            } else if (idOutOfRange) {
                showMethod("invalid compiled method id", fp);
                break;
            } else {
              // normal java frame(s)
              CompiledMethod compiledMethod = CompiledMethods
                  .getCompiledMethod(compiledMethodId);
              if (compiledMethod == null) {
                showMethod(compiledMethodId, fp);
              } else if (compiledMethod.getCompilerType() == CompiledMethod.TRAP) {
                showMethod("hardware trap", fp);
              } else if (!isAddressValidFramePointer(fp)) {
                  VM.sysWrite("Bogus looking frame pointer: ", fp);
                  VM.sysWriteln(" not dumping stack");
                  break;
              } else {
                RVMMethod method = compiledMethod.getMethod();
                if (compiledMethod.containsReturnAddress(ip)) {
                  Offset instructionOffset = compiledMethod
                      .getInstructionOffset(ip);
                  int lineNumber = compiledMethod
                      .findLineNumberForInstruction(instructionOffset);
                  boolean frameShown = false;
                  if (VM.BuildForOptCompiler && compiledMethod.getCompilerType() == CompiledMethod.OPT) {
                    OptCompiledMethod optInfo = (OptCompiledMethod) compiledMethod;
                    // Opt stack frames may contain multiple inlined methods.
                    OptMachineCodeMap map = optInfo.getMCMap();
                    int iei = map.getInlineEncodingForMCOffset(instructionOffset);
                    if (iei >= 0) {
                      int[] inlineEncoding = map.inlineEncoding;
                      int bci = map.getBytecodeIndexForMCOffset(instructionOffset);
                      for (; iei >= 0; iei = OptEncodedCallSiteTree.getParent(iei, inlineEncoding)) {
                        int mid = OptEncodedCallSiteTree.getMethodID(iei, inlineEncoding);
                        method = MemberReference.getMethodRef(mid).getResolvedMember();
                        lineNumber = ((NormalMethod) method).getLineNumberForBCIndex(bci);
                        showMethod(method, lineNumber, fp, bci, instructionOffset);
                        if (iei > 0) {
                          bci = OptEncodedCallSiteTree.getByteCodeOffset(iei, inlineEncoding);
                        }
                      }
                      frameShown = true;
                    }
                  }
                  if (!frameShown) {
                    int bci = -1;
                    if (compiledMethod.getCompilerType() == CompiledMethod.BASELINE) {
                      BaselineCompiledMethod bcm = (BaselineCompiledMethod) compiledMethod;
                      bci = bcm.findBytecodeIndexForInstruction(instructionOffset);
                    }
                    showMethod(method, lineNumber, fp, bci, instructionOffset);
                  }
                } else {
                  VM.sysWrite("    WARNING: Instruction pointer ");
                  VM.sysWrite(ip);
                  VM.sysWrite(" not in method code");
                  showMethod(method, -1, fp, -1, Offset.max());
                }
              }
            }
            ip = Magic.getReturnAddress(fp);
            fp = Magic.getCallerFramePointer(fp);
          }
          if (!isAddressValidFramePointer(fp)) {
            VM.sysWrite("Bogus looking frame pointer: ", fp);
            VM.sysWriteln(" end of stack dump");
            break;
          }
        } // end while
      } catch (Throwable th) {
        VM.sysWriteln("Something bad killed the stack dump. The last frame pointer was: ", fp);
      }
    }
    --t.inDumpStack;

    Monitor.unlock(b, dumpLock);
  }

  /**
   * Return true if the supplied address could be a valid frame pointer. To
   * check for validity we make sure the frame pointer is in one of the spaces;
   * <ul>
   * <li>LOS (For regular threads)</li>
   * <li>Immortal (For threads allocated in immortal space such as collectors)</li>
   * <li>Boot (For the boot thread)</li>
   * </ul>
   *
   * <p>
   * or it is {@link StackFrameLayout#getStackFrameSentinelFP()}.
   * {@code STACKFRAME_SENTINEL_FP} is possible when the thread has been created
   * but has yet to be scheduled.
   * </p>
   *
   * @param address
   *          the address.
   * @return true if the address could be a frame pointer, false otherwise.
   */
  private static boolean isAddressValidFramePointer(final Address address) {
    if (address.EQ(Address.zero()))
      return false; // Avoid hitting assertion failure in MMTk
    else
      return address.EQ(StackFrameLayout.getStackFrameSentinelFP()) || MemoryManager.mightBeFP(address);
  }

  private static void showPrologue(Address fp) {
    VM.sysWrite("   at ");
    if (SHOW_FP_IN_STACK_DUMP) {
      VM.sysWrite("[");
      VM.sysWrite(fp);
      VM.sysWrite(", ");
      VM.sysWrite(Magic.getReturnAddress(fp));
      VM.sysWrite("] ");
    }
  }

  /**
   * Show a method where getCompiledMethod returns null
   *
   * @param compiledMethodId the id of the compiled method
   * @param fp the frame pointer of the method's frame
   */
  private static void showMethod(int compiledMethodId, Address fp) {
    showPrologue(fp);
    VM.sysWriteln(
        "<unprintable normal Java frame: CompiledMethods.getCompiledMethod(",
        compiledMethodId, ") returned null>");
  }

  /**
   * Shows a method that we can't show (ie just a text description of the stack
   * frame
   *
   * @param name the method's name
   * @param fp the frame pointer of the method's frame
   */
  private static void showMethod(String name, Address fp) {
    showPrologue(fp);
    VM.sysWrite("<");
    VM.sysWrite(name);
    VM.sysWriteln(">");
  }

  /**
   * Helper function for {@link #dumpStack(Address,Address)}. Print a stack
   * frame showing the method.
   *
   * @param method the underlying method
   * @param lineNumber the line number for the stack trace
   * @param fp the frame pointer of the method's frame
   * @param bci byte code index (value &lt; 0 if unknown)
   * @param mcOffset machine code offset for the instruction ({@code Offset.max()} if unknown)
   */
  private static void showMethod(RVMMethod method, int lineNumber, Address fp, int bci, Offset mcOffset) {
    showPrologue(fp);
    if (method == null) {
      VM.sysWrite("<unknown method>");
    } else {
      VM.sysWrite(method.getDeclaringClass().getDescriptor());
      VM.sysWrite(" ");
      VM.sysWrite(method.getName());
      VM.sysWrite(method.getDescriptor());
    }
    if (lineNumber > 0) {
      VM.sysWrite(" at line ");
      VM.sysWriteInt(lineNumber);
    }
    if (bci >= 0) {
      VM.sysWrite(" at bytecode index ");
      VM.sysWriteInt(bci);
    }
    if (!mcOffset.isMax()) {
      VM.sysWrite(" at machine code offset ");
      VM.sysWrite(mcOffset);
    }
    VM.sysWriteln();
  }

  /**
   * Dump state of a (stopped) thread's stack and exit the virtual machine.
   *
   * @param fp
   *          address of starting frame Returned: doesn't return. This method is
   *          called from sysSignal*.c when something goes horrifically wrong
   *          with exception handling and we want to die with useful
   *          diagnostics.
   */
  @Entrypoint
  public static void dumpStackAndDie(Address fp) {
    if (!exitInProgress) {
      // This is the first time I've been called, attempt to exit "cleanly"
      exitInProgress = true;
      dumpStack(fp);
      VM.sysExit(EXIT_STATUS_DUMP_STACK_AND_DIE);
    } else {
      // Another failure occurred while attempting to exit cleanly.
      // Get out quick and dirty to avoid hanging.
      sysCall.sysExit(EXIT_STATUS_RECURSIVELY_SHUTTING_DOWN);
    }
  }

  /**
   * @return whether it is safe to start forcing garbage collects for stress testing
   */
  public static boolean safeToForceGCs() {
    return gcEnabled();
  }

  /**
   * @return whether garbage collection is enabled
   */
  public static boolean gcEnabled() {
    return threadingInitialized && getCurrentThread().yieldpointsEnabled();
  }

  /**
   * Set up the initial thread and processors as part of boot image writing
   *
   * @return the boot thread
   */
  @Interruptible
  public static RVMThread setupBootThread() {
    if (VM.VerifyAssertions) VM._assert(bootThread == null);
    BootThread bt = new BootThread();
    bootThread = bt.getRVMThread();
    bootThread.feedlet = TraceEngine.engine.makeFeedlet(
        "Jikes RVM boot thread",
        "Thread used to execute the initial boot sequence of Jikes RVM");
    numActiveThreads++;
    numActiveSystemThreads++;
    numActiveDaemons++;
    return bootThread;
  }

  /**
   * Dump state of virtual machine.
   */
  public static void dumpVirtualMachine() {
    boolean b = Monitor.lockNoHandshake(dumpLock);
    getCurrentThread().disableYieldpoints();
    VM.sysWriteln();
    VM.sysWrite("-- Threads --");
    VM.sysWriteln();
    for (int i = 0; i < numThreads; ++i) {
      RVMThread t = threads[i];
      if (t != null) {
        t.dumpWithPadding(30);
        VM.sysWriteln();
      }
    }
    VM.sysWriteln();

    VM.sysWriteln();
    VM.sysWrite("-- Locks in use --");
    VM.sysWriteln();
    Lock.dumpLocks();

    VM.sysWriteln("Dumping stack of active thread");
    VM.sysWriteln();
    dumpStack();

    VM.sysWriteln("Attempting to dump the stack of all other live threads");
    VM.sysWriteln("This is somewhat risky since if the thread is running we're going to be quite confused");
    for (int i = 0; i < numThreads; ++i) {
      RVMThread thr = threads[i];
      if (thr != null && thr != RVMThread.getCurrentThread() && thr.isAlive()) {
        thr.dump();
        // PNT: FIXME: this won't work so well since the context registers
        // don't tend to have sane values
        if (thr.contextRegisters != null && !thr.ignoreHandshakesAndGC())
          dumpStack(thr.contextRegisters.getInnermostFramePointer());
      }
    }
    getCurrentThread().enableYieldpoints();
    Monitor.unlock(b, dumpLock);
  }

  public static Feedlet getCurrentFeedlet() {
    return getCurrentThread().feedlet;
  }

  ////////////////////////// VM.countThreadTransitions support //////////////////////////

  static final int[] sloppyExecStatusHistogram =
    new int[LAST_EXEC_STATUS];
  static final int[] statusAtSTWHistogram =
    new int[LAST_EXEC_STATUS];
  static final int[] execStatusTransitionHistogram =
    new int[LAST_EXEC_STATUS * LAST_EXEC_STATUS];

  public static void reportThreadTransitionCounts() {
    VM.sysWriteln("Thread Transition Counts:");
    dump1DHisto("Sloppy Exec Status Histogram",sloppyExecStatusHistogram);
    dump1DHisto("Status At Stop-the-world Histogram",statusAtSTWHistogram);
    VM.sysWriteln("  Exec Status Transition Histogram:");
    for (int fromI = 0; fromI < LAST_EXEC_STATUS; ++fromI) {
      for (int toI = 0; toI < LAST_EXEC_STATUS; ++toI) {
        int val =
          execStatusTransitionHistogram[
            transitionHistogramIndex(fromI,toI)];
        if (val != 0) {
          VM.sysWriteln("    ",fromI,"->",toI," ",val);
        }
      }
    }
  }

  static void dump1DHisto(String name,int[] histo) {
    VM.sysWriteln("  ",name,":");
    for (int i = 0; i < LAST_EXEC_STATUS; ++i) {
      if (histo[i] != 0) {
        VM.sysWriteln("    ",i," ",histo[i]);
      }
    }
  }

  void observeExecStatus() {
    sloppyExecStatusHistogram[execStatus]++;
  }

  public static void observeExecStatusAtSTW(int execStatus) {
    statusAtSTWHistogram[execStatus]++;
  }

  // FIXME: add histograms for states returned from various calls to block()
  // currently we just do it for the block() call in GC STW.

  static int transitionHistogramIndex(int oldState,int newState) {
    return oldState + newState * LAST_EXEC_STATUS;
  }

  static void observeStateTransition(int oldState,int newState) {
    execStatusTransitionHistogram[transitionHistogramIndex(oldState,newState)]++;
    sloppyExecStatusHistogram[oldState]++;
    sloppyExecStatusHistogram[newState]++;
  }
}
