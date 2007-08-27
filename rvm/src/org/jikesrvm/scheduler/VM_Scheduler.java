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

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.compilers.opt.VM_OptCompiledMethod;
import org.jikesrvm.compilers.opt.VM_OptEncodedCallSiteTree;
import org.jikesrvm.compilers.opt.VM_OptMachineCodeMap;
import org.jikesrvm.memorymanagers.mminterface.MM_Constants;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.memorymanagers.mminterface.Selected;
import org.jikesrvm.runtime.VM_Magic;
import static org.jikesrvm.runtime.VM_SysCall.sysCall;
import org.jikesrvm.scheduler.greenthreads.VM_GreenScheduler;
import org.mmtk.policy.Space;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.LogicallyUninterruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Global variables used to implement virtual machine thread scheduler.
 *    - virtual cpus
 *    - threads
 *    - locks
 */
@Uninterruptible
public abstract class VM_Scheduler {
  private static final VM_Scheduler singleton = new VM_GreenScheduler();

  public static class ThreadModel extends org.jikesrvm.scheduler.greenthreads.VM_GreenThread {
    public ThreadModel(byte[] stack, String s) {
      super(stack, s);
    }
    public ThreadModel(String s) {
      super(s);
    }
  }

  public static final class LockModel extends org.jikesrvm.scheduler.greenthreads.VM_GreenLock {
  }

  private static VM_Scheduler getScheduler() {
    return singleton;
  }

  /** Toggle display of frame pointer address in stack dump */
  private static final boolean SHOW_FP_IN_STACK_DUMP = true;

  /** Index of thread in which "VM.boot()" runs */
  public static final int PRIMORDIAL_THREAD_INDEX = 1;

  /** Maximum number of VM_Thread's that we can support. */
  public static final int LOG_MAX_THREADS = 14;
  public static final int MAX_THREADS = 1 << LOG_MAX_THREADS;

  /** Flag for controlling virtual-to-physical processor binding. */
  public static final int NO_CPU_AFFINITY = -1;

  /** Scheduling quantum in milliseconds: interruptQuantum * interruptQuantumMultiplier */
  public static int schedulingQuantum = 10;

  // Virtual cpu's.
  //
  /**
   * Physical cpu to which first virtual processor is bound (remainder are bound
   * sequentially)
   */
  public static int cpuAffinity = NO_CPU_AFFINITY;
  /** VM is terminated, clean up and exit */
  public static boolean terminated;

  // Thread creation and deletion.
  //
  /** list of threads that have been created (slot 0 always empty) */
  public static final ThreadModel[] threads = new ThreadModel[MAX_THREADS];

  /** place to start searching threads[] for next free slot */
  protected static int threadAllocationIndex;
  /** highest thread index allocated */
  private static int threadHighWatermark;
  /** number of threads running or waiting to run */
  protected static int numActiveThreads;
  /** number of "daemon" threads, in the java sense */
  protected static int numDaemons;
  /**
   * guard for serializing access to fields above, also serializes thread
   * termination
   */
  public static final VM_ProcessorLock threadCreationMutex = new VM_ProcessorLock();

  /**
   * Flag set by external signal to request debugger activation at next thread switch.
   * See also: RunBootImage.C
   */
  public static volatile boolean debugRequested;

  /** Number of times dump stack has been called recursively */
  protected static int inDumpStack = 0;

  /** In dump stack and dying */
  protected static boolean exitInProgress = false;

  /** Extra debug from traces */
  protected static final boolean traceDetails = false;

  /** Int controlling output. 0 => output can be used, otherwise ID of processor */
  @SuppressWarnings({"unused", "UnusedDeclaration"})
  @Entrypoint
  protected static int outputLock;

  ////////////////////////////////////////////////
  // fields for synchronizing code patching
  ////////////////////////////////////////////////

  /**
   * How may processors to be synchronized for code patching, the last one (0)
   * will notify the blocked thread. Used only if RVM_FOR_POWERPC is true
   */
  public static int toSyncProcessors;

  /**
   * Synchronize object. Used only if RVM_FOR_POWERPC is true
   */
  public static Object syncObj = null;

  /**
   * Find an empty slot in the {@link VM_Scheduler#threads}[] array and bind
   * it to this thread.  <br>
   * <b>Assumption:</b> call is guarded by threadCreationMutex.
   * @return the thread slot assigned this thread
   */
  @LogicallyUninterruptible
  static int assignThreadSlot(VM_Thread thread) {
    if (!VM.runningVM) {
      // create primordial thread (in boot image)
      int threadSlot = VM_Scheduler.PRIMORDIAL_THREAD_INDEX;
      VM_Scheduler.threads[threadSlot] = (ThreadModel)thread;
      // note that VM_Scheduler.threadAllocationIndex (search hint)
      // is out of date
      VM_Scheduler.numActiveThreads ++;
      return PRIMORDIAL_THREAD_INDEX;
    } else {
      VM_Scheduler.threadCreationMutex.lock("thread creation mutex");
      for (int cnt = threads.length; --cnt >= 1;) {
        int index = threadAllocationIndex;
        threadAllocationIndex++;
        if (threadAllocationIndex == threads.length) {
          VM_Scheduler.threadAllocationIndex = 1;
        }
        if (VM_Scheduler.threads[index] == null) {
          /*
           *  Problem:
           *
           *  We'd like to say "VM_Scheduler.threads[index] = this;"
           *  but can't do "checkstore" without losing control. Since
           *  we're using magic for the store, we need to perform an
           *  explicit write barrier.
           */
          if (index > threadHighWatermark) {
            threadHighWatermark = index;
          }
          if (MM_Constants.NEEDS_WRITE_BARRIER) {
            MM_Interface.arrayStoreWriteBarrier(VM_Scheduler.threads,
                index, thread);
          }
          VM_Magic.setObjectAtOffset(threads,
              Offset.fromIntZeroExtend(index << VM_SizeConstants.LOG_BYTES_IN_ADDRESS), thread);
          VM_Scheduler.threadCreationMutex.unlock();
          return index;
        }
      }
      VM.sysFail("too many threads"); // !!TODO: grow threads[] array
      return -1;
    }
  }

    /**
     * Release this thread's threads[] slot.
     * Assumption: call is guarded by threadCreationMutex.
     * Note that after a thread calls this method, it can no longer
     * make JNI calls.  This matters when exiting the VM, because it
     * implies that this method must be called after the exit callbacks
     * are invoked if they are to be able to do JNI.
     */
  static void releaseThreadSlot(int threadSlot, VM_Thread thread) {
    threadCreationMutex.lock("releasing a thread slot");
    if (VM.VerifyAssertions) VM._assert(VM_Scheduler.threads[threadSlot] == thread);
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
    threadCreationMutex.unlock();
  }

  /**
   * Scheduler dependent dump of state of virtual machine.
   */
  protected abstract void dumpVirtualMachineInternal();

  /**
   * Dump state of virtual machine.
   */
  public static void dumpVirtualMachine() {
    getScheduler().dumpVirtualMachineInternal();
  }

  protected abstract void lockOutputInternal();

  public static void lockOutput() {
    getScheduler().lockOutputInternal();
  }

  protected abstract void unlockOutputInternal();

  /**
   * Unlock output
   */
  public static void unlockOutput() {
    getScheduler().unlockOutputInternal();
  }

  protected abstract void suspendDebuggerThreadInternal();

  static void suspendDebuggerThread() {
    getScheduler().suspendDebuggerThreadInternal();
  }

  /**
   * Schedule another thread
   */
  protected abstract void yieldInternal();

  /**
   * Schedule another thread
   */
  public static void yield() {
    getScheduler().yieldInternal();
  }

  /**
   * Schedule thread waiting on l to give it a chance to acquire the lock
   * @param l the lock to allow other thread chance to acquire
   */
  protected abstract void yieldToOtherThreadWaitingOnLockInternal(VM_Lock l);

  /**
   * Schedule thread waiting on l to give it a chance to acquire the lock
   * @param l the lock to allow other thread chance to acquire
   */
  static void yieldToOtherThreadWaitingOnLock(VM_Lock l) {
    getScheduler().yieldToOtherThreadWaitingOnLockInternal(l);
  }

  /** Start the debugger thread */
  @Interruptible
  protected abstract void startDebuggerThreadInternal();

  /** Start the debugger thread */
  @Interruptible
  public static void startDebuggerThread() {
    getScheduler().startDebuggerThreadInternal();
  }

  /** Scheduler specific initialization */
  @Interruptible
  protected abstract void initInternal();

  /** Scheduler specific initialization */
  @Interruptible
  public static void init() {
    getScheduler().initInternal();
  }

  /** Scheduler specific boot up */
  @Interruptible
  protected abstract void bootInternal();

    /** Scheduler specific boot up */
  @Interruptible
  public static void boot() {
    getScheduler().bootInternal();
  }
  /** Scheduler specific sysExit shutdown */
  @Interruptible
  protected abstract void sysExitInternal();

  /** Scheduler specific sysExit shutdown */
  @Interruptible
  public static void sysExit() {
    getScheduler().sysExitInternal();
  }


  /**
   *  Number of available processors
   *  @see Runtime#availableProcessors()
   */
  protected abstract int availableProcessorsInternal();

  /**
   *  Number of available processors
   *  @see Runtime#availableProcessors()
   */
  public static int availableProcessors() {
    return getScheduler().availableProcessorsInternal();
  }

  /**
   *  Number of VM_Processors
   */
  protected abstract int getNumberOfProcessorsInternal();

  /**
   *  Number of VM_Processors
   */
  public static int getNumberOfProcessors() {
    return getScheduler().getNumberOfProcessorsInternal();
  }

  /**
   * Get the current executing thread on this VM_Processor
   */
  public static VM_Thread getCurrentThread() {
    return VM_Magic.objectAsThread(VM_Processor.getCurrentProcessor().activeThread);
  }

  /*
   * MMTk interface
   */

  /**
   * Returns if the VM is ready for a garbage collection.
   *
   * @return True if the RVM is ready for GC, false otherwise.
   */
  public abstract boolean gcEnabledInternal();

  /**
   * Returns if the VM is ready for a garbage collection.
   *
   * @return True if the RVM is ready for GC, false otherwise.
   */
  public static boolean gcEnabled() {
    return getScheduler().gcEnabledInternal();
  }

  /**
   * Suspend a concurrent worker: it will resume when the garbage collector notifies.
   */
  protected abstract void suspendConcurrentCollectorThreadInternal();

  /**
   * Suspend a concurrent worker: it will resume when the garbage collector notifies.
   */
  public static void suspendConcurrentCollectorThread() {
    getScheduler().suspendConcurrentCollectorThreadInternal();
  }

  /**
   * Schedule the concurrent workers that are not already running
   * @see org.jikesrvm.mm.mmtk.Collection
   */
  protected abstract void scheduleConcurrentCollectorThreadsInternal();

  /**
   * Schedule the concurrent workers that are not already running
   * @see org.jikesrvm.mm.mmtk.Collection
   */
  public static void scheduleConcurrentCollectorThreads() {
    getScheduler().scheduleConcurrentCollectorThreadsInternal();
  }

  /**
   * suspend the finalizer thread: it will resume when the garbage collector
   * places objects on the finalizer queue and notifies.
   */
  protected abstract void suspendFinalizerThreadInternal();

  /**
   * suspend the finalizer thread: it will resume when the garbage collector
   * places objects on the finalizer queue and notifies.
   */
  static void suspendFinalizerThread() {
    getScheduler().suspendFinalizerThreadInternal();
  }

  /**
   * Schedule the finalizer thread if its not already running
   * @see org.jikesrvm.mm.mmtk.Collection
   */
  protected abstract void scheduleFinalizerInternal();

  /**
   * Schedule the finalizer thread if its not already running
   * @see org.jikesrvm.mm.mmtk.Collection
   */
  public static void scheduleFinalizer() {
    getScheduler().scheduleFinalizerInternal();
  }

  /**
   * Request that all mutators flush their context for gc.
   * @see org.jikesrvm.mm.mmtk.Collection
   */
  protected abstract void requestMutatorFlushInternal();

  /**
   * Request that all mutators flush their context for gc.
   * @see org.jikesrvm.mm.mmtk.Collection
   */
  public static void requestMutatorFlush() {
    getScheduler().requestMutatorFlushInternal();
  }

  /**
   * Print out message in format "p[j] (cez#td) who: what", where:
   *    p  = processor id
   *    j  = java thread id
   *    c* = ava thread id of the owner of threadCreationMutex (if any)
   *    e* = java thread id of the owner of threadExecutionMutex (if any)
   *    z* = VM_Processor.getCurrentProcessor().threadSwitchingEnabledCount
   *         (0 means thread switching is enabled outside of the call to debug)
   *    t* = numActiveThreads
   *    d* = numDaemons
   *
   * * parenthetical values, printed only if traceDetails = true)
   *
   * We serialize against a mutex to avoid intermingling debug output from multiple threads.
   */
  public static void trace(String who, String what) {
    lockOutput();
    VM_Processor.getCurrentProcessor().disableThreadSwitching("disabled for scheduler to trace processor(1)");
    VM.sysWriteInt(VM_Processor.getCurrentProcessorId());
    VM.sysWrite("[");
    VM_Thread t = getCurrentThread();
    t.dump();
    VM.sysWrite("] ");
    if (traceDetails) {
      VM.sysWrite("(");
      // VM.sysWriteInt(threadCreationMutex.owner);
      // VM.sysWrite("-");
      // VM.sysWriteInt(-VM_Processor.getCurrentProcessor().threadSwitchingEnabledCount);
      // VM.sysWrite("#");
      VM.sysWriteInt(numDaemons);
      VM.sysWrite("/");
      VM.sysWriteInt(numActiveThreads);
      VM.sysWrite(") ");
    }
    VM.sysWrite(who);
    VM.sysWrite(": ");
    VM.sysWrite(what);
    VM.sysWrite("\n");
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
    unlockOutput();
  }

  /** @return highest thread index allocated */
  public static int getThreadHighWatermark() {
    return threadHighWatermark;
  }

  /**
   * Print out message in format "p[j] (cez#td) who: what howmany", where:
   *    p  = processor id
   *    j  = java thread id
   *    c* = java thread id of the owner of threadCreationMutex (if any)
   *    e* = java thread id of the owner of threadExecutionMutex (if any)
   *    z* = VM_Processor.getCurrentProcessor().threadSwitchingEnabledCount
   *         (0 means thread switching is enabled outside of the call to debug)
   *    t* = numActiveThreads
   *    d* = numDaemons
   *
   * * parenthetical values, printed only if traceDetails = true)
   *
   * We serialize against a mutex to avoid intermingling debug output from multiple threads.
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
    VM_Processor.getCurrentProcessor().disableThreadSwitching("disabled for scheduler to trace processor(2)");
    lockOutput();
    VM.sysWriteInt(VM_Processor.getCurrentProcessorId());
    VM.sysWrite("[");
    getCurrentThread().dump();
    VM.sysWrite("] ");
    if (traceDetails) {
      VM.sysWrite("(");
      VM.sysWriteInt(numDaemons);
      VM.sysWrite("/");
      VM.sysWriteInt(numActiveThreads);
      VM.sysWrite(") ");
    }
    VM.sysWrite(who);
    VM.sysWrite(": ");
    VM.sysWrite(what);
    VM.sysWrite(" ");
    VM.sysWriteHex(addr);
    VM.sysWrite("\n");
    unlockOutput();
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }

  private static void _trace(String who, String what, int howmany, boolean hex) {
    VM_Processor.getCurrentProcessor().disableThreadSwitching("disabled for scheduler to trace processor(3)");
    lockOutput();
    VM.sysWriteInt(VM_Processor.getCurrentProcessorId());
    VM.sysWrite("[");
    //VM.sysWriteInt(VM_Thread.getCurrentThread().getIndex());
    getCurrentThread().dump();
    VM.sysWrite("] ");
    if (traceDetails) {
      VM.sysWrite("(");
      // VM.sysWriteInt(threadCreationMutex.owner);
      // VM.sysWrite("-");
      // VM.sysWriteInt(-VM_Processor.getCurrentProcessor().threadSwitchingEnabledCount);
      // VM.sysWrite("#");
      VM.sysWriteInt(numDaemons);
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
    VM.sysWrite("\n");
    unlockOutput();
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }

  /**
   * Print interesting scheduler information, starting with a stack traceback.
   * Note: the system could be in a fragile state when this method
   * is called, so we try to rely on as little runtime functionality
   * as possible (eg. use no bytecodes that require VM_Runtime support).
   */
  public static void traceback(String message) {
    if (VM.runningVM) {
      VM_Processor.getCurrentProcessor().disableThreadSwitching("disabled for scheduler to trace processor(3)");
      lockOutput();
    }
    VM.sysWriteln(message);
    tracebackWithoutLock();
    if (VM.runningVM) {
      unlockOutput();
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
    }
  }

  public static void traceback(String message, int number) {
    if (VM.runningVM) {
      VM_Processor.getCurrentProcessor().disableThreadSwitching("disabled for scheduler to trace processor(4)");
      lockOutput();
    }
    VM.sysWriteln(message, number);
    tracebackWithoutLock();
    if (VM.runningVM) {
      unlockOutput();
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
    }
  }

  static void tracebackWithoutLock() {
    if (VM.runningVM) {
      dumpStack(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()));
    } else {
      dumpStack();
    }
  }

  /**
   * Dump stack of calling thread, starting at callers frame
   */
  @LogicallyUninterruptible
  public static void dumpStack() {
    if (VM.runningVM) {
      dumpStack(VM_Magic.getFramePointer());
    } else {
      StackTraceElement[] elements =
        (new Throwable("--traceback from Jikes RVM's VM_Scheduler class--")).getStackTrace();
      for (StackTraceElement element: elements) {
        System.err.println(element.toString());
      }
    }
  }

  /**
   * Dump state of a (stopped) thread's stack.
   * @param fp address of starting frame. first frame output
   *           is the calling frame of passed frame
   */
  public static void dumpStack(Address fp) {
    if (VM.VerifyAssertions) {
      VM._assert(VM.runningVM);
    }

    Address ip = VM_Magic.getReturnAddress(fp);
    fp = VM_Magic.getCallerFramePointer(fp);
    dumpStack(ip, fp);

  }

  /**
   * Dump state of a (stopped) thread's stack.
   * @param ip instruction pointer for first frame to dump
   * @param fp frame pointer for first frame to dump
   */
  public static void dumpStack(Address ip, Address fp) {
    ++inDumpStack;
    if (inDumpStack > 1 &&
        inDumpStack <= VM.maxSystemTroubleRecursionDepth + VM.maxSystemTroubleRecursionDepthBeforeWeStopVMSysWrite) {
      VM.sysWrite("VM_Scheduler.dumpStack(): in a recursive call, ");
      VM.sysWrite(inDumpStack);
      VM.sysWriteln(" deep.");
    }
    if (inDumpStack > VM.maxSystemTroubleRecursionDepth) {
      VM.dieAbruptlyRecursiveSystemTrouble();
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }

    VM.sysWriteln();
    if (!isAddressValidFramePointer(fp)) {
      VM.sysWrite("Bogus looking frame pointer: ", fp);
      VM.sysWriteln(" not dumping stack");
    } else {
      try {
        VM.sysWriteln("-- Stack --");
        while (VM_Magic.getCallerFramePointer(fp).NE(ArchitectureSpecific.VM_StackframeLayoutConstants.STACKFRAME_SENTINEL_FP)) {

          // if code is outside of RVM heap, assume it to be native code,
          // skip to next frame
          if (!MM_Interface.addressInVM(ip)) {
            showMethod("native frame", fp);
            ip = VM_Magic.getReturnAddress(fp);
            fp = VM_Magic.getCallerFramePointer(fp);
          } else {

            int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
            if (compiledMethodId == ArchitectureSpecific.VM_StackframeLayoutConstants.INVISIBLE_METHOD_ID) {
              showMethod("invisible method", fp);
            } else {
              // normal java frame(s)
              VM_CompiledMethod compiledMethod = VM_CompiledMethods.getCompiledMethod(compiledMethodId);
              if (compiledMethod == null) {
                showMethod(compiledMethodId, fp);
              } else if (compiledMethod.getCompilerType() == VM_CompiledMethod.TRAP) {
                showMethod("hardware trap", fp);
              } else {
                VM_Method method = compiledMethod.getMethod();
                Offset instructionOffset = compiledMethod.getInstructionOffset(ip);
                int lineNumber = compiledMethod.findLineNumberForInstruction(instructionOffset);
                boolean frameShown = false;
                if (VM.BuildForOptCompiler && compiledMethod.getCompilerType() == VM_CompiledMethod.OPT) {
                  VM_OptCompiledMethod optInfo = (VM_OptCompiledMethod) compiledMethod;
                  // Opt stack frames may contain multiple inlined methods.
                  VM_OptMachineCodeMap map = optInfo.getMCMap();
                  int iei = map.getInlineEncodingForMCOffset(instructionOffset);
                  if (iei >= 0) {
                    int[] inlineEncoding = map.inlineEncoding;
                    int bci = map.getBytecodeIndexForMCOffset(instructionOffset);
                    for (; iei >= 0; iei = VM_OptEncodedCallSiteTree.getParent(iei, inlineEncoding)) {
                      int mid = VM_OptEncodedCallSiteTree.getMethodID(iei, inlineEncoding);
                      method = VM_MemberReference.getMemberRef(mid).asMethodReference().getResolvedMember();
                      lineNumber = ((VM_NormalMethod)method).getLineNumberForBCIndex(bci);
                      showMethod(method, lineNumber, fp);
                      if (iei > 0) {
                        bci = VM_OptEncodedCallSiteTree.getByteCodeOffset(iei, inlineEncoding);
                      }
                    }
                    frameShown=true;
                  }
                }
                if(!frameShown) {
                  showMethod(method, lineNumber, fp);
                }
              }
            }
            ip = VM_Magic.getReturnAddress(fp);
            fp = VM_Magic.getCallerFramePointer(fp);
          }
          if (!isAddressValidFramePointer(fp)) {
            VM.sysWrite("Bogus looking frame pointer: ", fp);
            VM.sysWriteln(" end of stack dump");
            break;
          }
        } // end while
      } catch (Throwable t) {
        VM.sysWriteln("Something bad killed the stack dump. The last frame pointer was: ", fp);
      }
    }
    --inDumpStack;
  }

  /**
   * Return true if the supplied address could be a valid frame pointer.
   * To check for validity we make sure the frame pointer is in one of the
   * spaces;
   * <ul>
   *   <li>LOS (For regular threads)</li>
   *   <li>Immortal (For threads allocated in immortal space such as collectors)</li>
   *   <li>Boot (For the boot thread)</li>
   * </ul>
   *
   * <p>or it is {@link ArchitectureSpecific.VM_StackframeLayoutConstants#STACKFRAME_SENTINEL_FP}.
   * The STACKFRAME_SENTINEL_FP is possible when the thread has been created but has yet to be
   * scheduled.</p>
   *
   * @param address the address.
   * @return true if the address could be a frame pointer, false otherwise.
   */
  private static boolean isAddressValidFramePointer(final Address address) {
    return address.EQ(ArchitectureSpecific.VM_StackframeLayoutConstants.STACKFRAME_SENTINEL_FP) ||
           isAddressInSpace(address, Selected.Plan.loSpace) ||
           isAddressInSpace(address, Selected.Plan.immortalSpace)||
           isAddressInSpace(address, Selected.Plan.vmSpace);
  }

  /**
   * Return true if address is in space.
   *
   * @param address the address.
   * @param space the space.
   * @return true if address is in space.
   */
  private static boolean isAddressInSpace(final Address address,
                                          final Space space) {
    return address.GE(space.getStart()) && address.LE(space.getStart().plus(space.getExtent()));
  }

  private static void showPrologue(Address fp) {
    VM.sysWrite("   at ");
    if (SHOW_FP_IN_STACK_DUMP) {
      VM.sysWrite("[");
      VM.sysWrite(fp);
      VM.sysWrite("] ");
    }
  }

  /**
   * Show a method where getCompiledMethod returns null
   *
   * @param compiledMethodId
   * @param fp
   */
  private static void showMethod(int compiledMethodId, Address fp) {
    showPrologue(fp);
    VM.sysWrite("<unprintable normal Java frame: VM_CompiledMethods.getCompiledMethod(",
                compiledMethodId,
                ") returned null>\n");
  }

  /**
   * Show a method that we can't show (ie just a text description of the
   * stack frame
   *
   * @param name
   * @param fp
   */
  private static void showMethod(String name, Address fp) {
    showPrologue(fp);
    VM.sysWrite("<");
    VM.sysWrite(name);
    VM.sysWrite(">\n");
  }

  /**
   * Helper function for {@link #dumpStack(Address,Address)}. Print a stack
   * frame showing the method.
   */
  private static void showMethod(VM_Method method, int lineNumber, Address fp) {
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
    VM.sysWrite("\n");
  }

  /**
   * Dump state of a (stopped) thread's stack and exit the virtual machine.
   * @param fp address of starting frame
   * Returned: doesn't return.
   * This method is called from RunBootImage.C when something goes horrifically
   * wrong with exception handling and we want to die with useful diagnostics.
   */
  @Entrypoint
  public static void dumpStackAndDie(Address fp) {
    if (!exitInProgress) {
      // This is the first time I've been called, attempt to exit "cleanly"
      exitInProgress = true;
      dumpStack(fp);
      VM.sysExit(VM.EXIT_STATUS_DUMP_STACK_AND_DIE);
    } else {
      // Another failure occurred while attempting to exit cleanly.
      // Get out quick and dirty to avoid hanging.
      sysCall.sysExit(VM.EXIT_STATUS_RECURSIVELY_SHUTTING_DOWN);
    }
  }

  /**
   * Is it safe to start forcing garbage collects for stress testing?
   */
  protected abstract boolean safeToForceGCsInternal();

  /**
   * Is it safe to start forcing garbage collects for stress testing?
   */
  public static boolean safeToForceGCs() {
    return getScheduler().safeToForceGCsInternal();
  }

  /**
   * Set up the initial thread and processors as part of boot image writing
   * @return the boot thread
   */
  @Interruptible
  protected abstract VM_Thread setupBootThreadInternal();

  /**
   * Set up the initial thread and processors as part of boot image writing
   * @return the boot thread
   */
  @Interruptible
  public static VM_Thread setupBootThread() {
    if (VM.VerifyAssertions) VM._assert(!VM.runningVM);
    return getScheduler().setupBootThreadInternal();
  }

  /**
   * Get the type of the thread (to avoid guarded inlining..)
   */
  @Interruptible
  protected abstract VM_TypeReference getThreadTypeInternal();

  /**
   * Get the type of the thread (to avoid guarded inlining..)
   */
  @Interruptible
  public static VM_TypeReference getThreadType() {
    return getScheduler().getThreadTypeInternal();
  }

  /**
   * Get the type of the processor (to avoid guarded inlining..)
   */
  @Interruptible
  protected abstract VM_TypeReference getProcessorTypeInternal();

  /**
   * Get the type of the processor (to avoid guarded inlining..)
   */
  @Interruptible
  public static VM_TypeReference getProcessorType() {
    return getScheduler().getProcessorTypeInternal();
  }
}
