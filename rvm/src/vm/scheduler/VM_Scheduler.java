/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_CollectorThread;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.classloader.*;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

//-#if RVM_WITH_OPT_COMPILER
import com.ibm.JikesRVM.opt.*;
//-#endif

//-#if RVM_WITH_OSR
import com.ibm.JikesRVM.OSR.OSR_ObjectHolder;
//-#endif

/**
 * Global variables used to implement virtual machine thread scheduler.
 *    - virtual cpus
 *    - threads
 *    - queues
 *    - locks
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 * PFS added calls to set HPM settings and start counting.
 */
public class VM_Scheduler implements VM_Constants, Uninterruptible {

  /** Index of initial processor in which "VM.boot()" runs. */
  public static final int PRIMORDIAL_PROCESSOR_ID = 1;

  /** Index of thread in which "VM.boot()" runs */
  public static final int PRIMORDIAL_THREAD_INDEX = 1;

  // A processors id is its index in the processors array & a threads
  // id is its index in the threads array.  id's start at 1, so that
  // id 0 can be used in locking to represent an unheld lock

  /**
   * Maximum number of VM_Processor's that we can support.
   */
  public static final int MAX_PROCESSORS = 12;   // allow processors = 1 to 12

  /** Maximum number of VM_Thread's that we can support. */
  public static final int LOG_MAX_THREADS = 14;
  public static final int MAX_THREADS = 1 << LOG_MAX_THREADS;

  // Flag for controlling virtual-to-physical processor binding.
  //
  public static final int NO_CPU_AFFINITY = -1;

  // scheduling quantum in milliseconds: interruptQuantum * interruptQuantumMultiplier
  public static       int schedulingQuantum = 10;

  // Virtual cpu's.
  //
  public static int                  cpuAffinity   = NO_CPU_AFFINITY; // physical cpu to which first virtual processor is bound (remainder are bound sequentially)
  public static int           numProcessors = 1; // total number of virtual processors to be used
  public static VM_Processor[]       processors;        // list thereof (slot 0 always empty)
  public static boolean              allProcessorsInitialized; // have all completed initialization?
  public static boolean              terminated;        // VM is terminated, clean up and exit

  // Thread creation and deletion.
  //
  public static VM_Thread[]          threads;             // list of threads that have been created (slot 0 always empty)

  //-#if RVM_WITH_HPM
  // Hack, don't to any GC of thread slots!
  // never forget about a thread for reporting.
  public static VM_Thread[]      hpm_threads; 
  //-#endif

  static int                  threadAllocationIndex; // place to start searching threads[] for next free slot
  static int                  numActiveThreads;    // number of threads running or waiting to run
  static int                  numDaemons;          // number of "daemon" threads, in the java sense
  static VM_ProcessorLock     threadCreationMutex; // guard for serializing access to fields above
  static VM_ProcessorQueue    deadVPQueue;         // queue for VPs waiting for callToNative function
  static VM_ProcessorQueue    availableProcessorQueue;         // queue for VPs waiting for callToNative function

  // Thread execution.
  //
  static VM_ProxyWakeupQueue  wakeupQueue;         // threads waiting to wake up from a sleep()
  static VM_ProcessorLock     wakeupMutex;

  static VM_ThreadQueue       debuggerQueue;       // thread waiting to service debugging requests
  static VM_ProcessorLock     debuggerMutex;

  public static VM_ThreadQueue       collectorQueue;      // collector threads waiting to be resumed
  public static VM_ProcessorLock     collectorMutex;

  public static VM_ThreadQueue       finalizerQueue;      // Finalizer thread waits here when idle
  public static VM_ProcessorLock     finalizerMutex;

  // Debugging output.
  //
  // DEPRECATED! use lockOutput() and unlock Output() instead
  //
  public static VM_ProcessorLock     outputMutex;         // guard for improving readability of trace output

  // Thick locks.
  //
  public static VM_Lock [] locks;

  // Flag set by external signal to request debugger activation at next thread switch.
  // See also: RunBootImage.C
  //
  public static boolean debugRequested;

  // Trace flags.
  //
  static final boolean countLocks = false;

  // RC (concurrent GC) vars
  static int              globalEpoch = -1;
  final static int EPOCH_MAX = 32 * 1024;
  // ~RC vars

  private static int NUM_EXTRA_PROCS = 0; // How many extra procs (not counting primordial) ?

  //-#if RVM_WITH_HPM  
  /**
   * This call sets the processor affinity of the thread that is
   * passed as the first parameter to a virtual processor that 
   * is computed from the second parameter.
   * ASSUMPTION: virtual processors are initialized before this is called.
   * Kludge for IVME'03.  Binds SPECjbb warehouses to virtual processors.
   * Called from JBBmain.java.
   *
   * @param t      thread as an object to fool jikes at compile time.
   * @param value  valued used to determine which virtual processor to bind thread to.
   *               Use mod of value to compute processor id.
   *               Assume value > 0.
   */
  static public void setProcessorAffinity(Object t, int value) 
  {
    if (VM.VerifyAssertions) VM._assert(value >= 0);
    int pid = 0;
    if (0 < value && value <= VM_Scheduler.numProcessors) {
      pid = value;
    } else {
      pid = (value % VM_Scheduler.numProcessors) + 1;
    }
    //    if(VM_HardwarePerformanceMonitors.verbose>=3) {
      VM.sysWriteln("VM_Thread.setProcessorAffinity(",value,") assigned pid ",pid);
      //    }
    VM_Thread thread = (VM_Thread)t;
    if (pid <= VM_Scheduler.numProcessors && pid > 0) {
      thread.processorAffinity = VM_Scheduler.processors[pid];
    }
  }
  //-#endif

  /**
   * Initialize boot image.
   */
  static void init() throws InterruptiblePragma {
    threadCreationMutex     = new VM_ProcessorLock();
    outputMutex             = new VM_ProcessorLock();
    threads                 = new VM_Thread[MAX_THREADS];
    //-#if RVM_WITH_HPM
    hpm_threads             = new VM_Thread[MAX_THREADS];
    //-#endif
    threadAllocationIndex   = PRIMORDIAL_THREAD_INDEX;

    // Enable us to dump a Java Stack from the C trap handler to aid in debugging things that 
    // show up as recursive use of hardware exception registers (eg the long-standing lisp bug)
    VM_BootRecord.the_boot_record.dumpStackAndDieOffset = VM_Entrypoints.dumpStackAndDieMethod.getOffset();

    // allocate initial processor list
    //
    processors = new VM_Processor[2 + NUM_EXTRA_PROCS];  // first slot unused, then primordial, then extra
    processors[PRIMORDIAL_PROCESSOR_ID] = new VM_Processor(PRIMORDIAL_PROCESSOR_ID);
    for (int i=1; i<=NUM_EXTRA_PROCS; i++)
      processors[PRIMORDIAL_PROCESSOR_ID + i] = new VM_Processor(PRIMORDIAL_PROCESSOR_ID + i);

    // allocate lock structures
    //
    VM_Lock.init();
  }

  /** This is run from VM.boot() */
  static void giveBootVM_ThreadAJavaLangThread() 
    throws InterruptiblePragma
  {
    VM_Thread vt = threads[PRIMORDIAL_THREAD_INDEX];
    
    vt.setJavaLangThread(java.lang.JikesRVMSupport.createThread(vt, "Jikes_RVM_Boot_Thread"));
  }

  /**
   * Begin multi-threaded vm operation.
   */
  static void boot () throws InterruptiblePragma {
    if (VM.VerifyAssertions) VM._assert(1 <= numProcessors && numProcessors <= MAX_PROCESSORS);

    if (VM.TraceThreads)
      trace("VM_Scheduler.boot","numProcessors =", numProcessors);

    // Create a VM_Processor object for each virtual cpu that we'll be running.
    // Note that the VM_Processor object for the primordial processor
    // (the virtual cpu in whose context we are currently running)
    // was already created in the boot image by init(), above.
    //
    VM_Processor primordialProcessor = processors[PRIMORDIAL_PROCESSOR_ID];
    VM_Processor [] origProcs = processors;
    processors = new VM_Processor[1 + numProcessors];

    for (int i = PRIMORDIAL_PROCESSOR_ID; i <= numProcessors; i++) {
      VM_Processor p = (i < origProcs.length) ? origProcs[i] : null;
      if (p == null) {
        processors[i] = new VM_Processor(i);
      } else { 
        processors[i] = p;
        //-#if RVM_FOR_IA32
        p.jtoc = VM_Magic.getJTOC();  // only needed for EXTRA_PROCS
        //-#endif
      }
      //-#if RVM_WITH_HPM
      // boot virtual processor's HPM producer
      if (VM_HardwarePerformanceMonitors.booted()) {
	processors[i].hpm.boot();    
      }
      //-#endif
    }

    // Create work queues.
    //
    wakeupQueue     = new VM_ProxyWakeupQueue();
    wakeupMutex     = new VM_ProcessorLock();

    debuggerQueue   = new VM_ThreadQueue();
    debuggerMutex   = new VM_ProcessorLock();

    collectorQueue  = new VM_ThreadQueue();
    collectorMutex  = new VM_ProcessorLock();

    finalizerQueue  = new VM_ThreadQueue();
    finalizerMutex  = new VM_ProcessorLock();

    deadVPQueue     = new VM_ProcessorQueue();
    availableProcessorQueue     = new VM_ProcessorQueue();

    VM_CollectorThread.boot(numProcessors);

    // Create one one idle thread per processor.
    //
    for (int i = 0; i < numProcessors; ++i) {
      int pid = i+1;
      VM_Thread t = new VM_IdleThread(processors[pid], pid != PRIMORDIAL_PROCESSOR_ID);
      processors[pid].idleQueue.enqueue(t);
    }

    // JNI support
    terminated = false;         

    // the one we're running on
    processors[PRIMORDIAL_PROCESSOR_ID].isInitialized = true; 

    // Create virtual cpu's.
    //
    
    //-#if RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    //-#else
    //-#if RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS 
    //-#else
    // Create thread-specific data key which will allow us to find
    // the correct VM_Processor from an arbitrary pthread.
    VM_SysCall.sysCreateThreadSpecificDataKeys();

    // enable spoofing of blocking native select calls
    System.loadLibrary("syswrap");
    //-#endif

    VM_SysCall.sysInitializeStartupLocks(numProcessors);

    if (cpuAffinity != NO_CPU_AFFINITY)
      VM_SysCall.sysVirtualProcessorBind(cpuAffinity + PRIMORDIAL_PROCESSOR_ID - 1); // bind it to a physical cpu

    for (int i = PRIMORDIAL_PROCESSOR_ID; ++i <= numProcessors; ) {
      // create VM_Thread for virtual cpu to execute
      //
      VM_Thread target = processors[i].idleQueue.dequeue();

      // create virtual cpu and wait for execution to enter target's code/stack.
      // this is done with gc disabled to ensure that garbage collector doesn't move
      // code or stack before the C startoff function has a chance
      // to transfer control into vm image.
      //
      if (VM.TraceThreads)
        trace("VM_Scheduler.boot", "starting processor id", i);

      processors[i].activeThread = target;
      processors[i].activeThreadStackLimit = target.stackLimit;
      target.registerThread(); // let scheduler know that thread is active.
      if (VM.BuildForPowerPC) {
        //-#if RVM_FOR_POWERPC
        // NOTE: it is critical that we acquire the tocPointer explicitly
        //       before we start the SysCall sequence. This prevents 
        //       the opt compiler from generating code that passes the AIX 
        //       sys toc instead of the RVM jtoc. --dave
        Address toc = VM_Magic.getTocPointer();
        VM_SysCall.sysVirtualProcessorCreate(toc,
                                             VM_Magic.objectAsAddress(processors[i]),
                                             target.contextRegisters.ip, 
                                             target.contextRegisters.getInnermostFramePointer());
        if (cpuAffinity != NO_CPU_AFFINITY)
          VM_SysCall.sysVirtualProcessorBind(cpuAffinity + i - 1); // bind it to a physical cpu
        //-#endif
      } else if (VM.BuildForIA32) {
        VM_SysCall.sysVirtualProcessorCreate(VM_Magic.getTocPointer(),
                                             VM_Magic.objectAsAddress(processors[i]),
                                             target.contextRegisters.ip, 
                                             target.contextRegisters.getInnermostFramePointer());
      }

    }

    // wait for everybody to start up
    //
    VM_SysCall.sysWaitForVirtualProcessorInitialization();
    //-#endif

    allProcessorsInitialized = true;

    //    for (int i = PRIMORDIAL_PROCESSOR_ID; i <= numProcessors; ++i)
    //      processors[i].enableThreadSwitching();
    VM_Processor.getCurrentProcessor().enableThreadSwitching();

    // Start interrupt driven timeslicer to improve threading fairness and responsiveness.
    //
    schedulingQuantum = VM.interruptQuantum * VM.schedulingMultiplier;
    if (VM.TraceThreads) {
      VM.sysWrite("  schedulingQuantum "       +  schedulingQuantum);
      VM.sysWrite(" = VM.interruptQuantum "    +VM.interruptQuantum);
      VM.sysWrite(" * VM.schedulingMultiplier "+VM.schedulingMultiplier);
      VM.sysWriteln();
    }
    VM_SysCall.sysVirtualProcessorEnableTimeSlicing(VM.interruptQuantum);

    // Allow virtual cpus to commence feeding off the work queues.
    //
    //-#if RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    //-#else
    VM_SysCall.sysWaitForMultithreadingStart();
    //-#endif

    //-#if RVM_WITH_OSR
    OSR_ObjectHolder.boot();
    //-#endif

    // Start collector threads on each VM_Processor.
    for (int i = 0; i < numProcessors; ++i) {
      VM_Thread t = VM_CollectorThread.createActiveCollectorThread(processors[1+i]);
      t.start(processors[1+i].readyQueue);
    }

    // Start the G.C. system.

    // Create the FinalizerThread
    FinalizerThread tt = new FinalizerThread();
    tt.makeDaemon(true);
    tt.start();

    //-#if RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
    //-#else
    // Store VM_Processor in pthread
    VM_Processor.getCurrentProcessor().stashProcessorInPthread();
    //-#endif
  }


  /**
   * Terminate all the pthreads that belong to the VM
   * This path is used when the VM is taken down by an external pthread via 
   * the JNI call DestroyJavaVM.  All pthreads in the VM must eventually reach this 
   * method from VM_Thread.terminate() for the termination to proceed and for control 
   * to return to the pthread that calls DestroyJavaVM
   * Going by the order in processor[], the pthread for each processor will join with 
   * the next one, and the external pthread calling DestroyJavaVM will join with the
   * main pthread of the VM (see libjni.C)
   *
   * Note:  the NativeIdleThread's don't need to be terminated since they don't have
   * their own pthread;  they run on the external pthreads that had called CreateJavaVM
   * or AttachCurrentThread.
   * 
   */
  static void processorExit(int rc) {
    // trace("VM_Scheduler", ("Exiting with " + numProcessors + " pthreads."));

    // set flag to get all idle threads to exit to VM_Thread.terminate()
    terminated = true;

    // TODO:
    // Get the collector to free system memory:  no more allocation beyond this point


    // Terminate the pthread: each processor waits for the next one
    // find the pthread to wait for
    VM_Processor myVP = VM_Processor.getCurrentProcessor();
    VM_Processor VPtoWaitFor = null;
    for (int i=1; i<numProcessors; i++) {
      if (processors[i] == myVP) {
        VPtoWaitFor = processors[i+1];
        break;
      }
    }

    // each join with the expected pthread 
    if (VPtoWaitFor!=null) {
      VM_SysCall.sysPthreadJoin(VPtoWaitFor.pthread_id);
    }

    // then exit myself with pthread_exit
    VM_SysCall.sysPthreadExit();

    // does not return
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);

  }



  private static final boolean traceDetails = false;

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
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    VM.sysWriteInt(VM_Processor.getCurrentProcessorId());
    VM.sysWrite("[");
    VM_Thread t = VM_Thread.getCurrentThread();
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
    _trace( who, what, howmany, false );
  }

  // same as trace, but prints integer value in hex
  //
  public static void traceHex(String who, String what, int howmany) {
    _trace( who, what, howmany, true );
  }

  public static void trace(String who, String what, Address addr) {
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    lockOutput();
    VM.sysWriteInt(VM_Processor.getCurrentProcessorId());
    VM.sysWrite("[");
    VM_Thread.getCurrentThread().dump();
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
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    lockOutput();
    VM.sysWriteInt(VM_Processor.getCurrentProcessorId());
    VM.sysWrite("[");
    //VM.sysWriteInt(VM_Thread.getCurrentThread().getIndex());
    VM_Thread.getCurrentThread().dump();
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
    if (hex) 
      VM.sysWriteHex(howmany);
    else
      VM.sysWriteInt(howmany);
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
  static void traceback(String message) {
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    lockOutput();
    VM.sysWriteln(message);
    tracebackWithoutLock();
    unlockOutput();
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }
  static void traceback(String message, int number) {
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    lockOutput();
    VM.sysWriteln(message, number);
    tracebackWithoutLock();
    unlockOutput();
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }

  static void tracebackWithoutLock() {

    dumpStack(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()));

//     VM.sysWrite("Here comes a Virtual Machine dump.  This can run to\n");
    
//     VM.sysWrite("thousands of lines, but it is sometimes useful.  Besides,\n");
//     VM.sysWrite("you wouldn't have gotten here unless something were broken.\n");
//     /* I'm open to taking this out; it was a marginal decision.  --Steve
//        Augart */
//     dumpVirtualMachine();
  }

  /**
   * Dump stack of calling thread, starting at callers frame
   */
  public static void dumpStack () {
    dumpStack(VM_Magic.getFramePointer());
  }

  /**
   * Dump state of a (stopped) thread's stack.
   * @param fp address of starting frame. first frame output
   *           is the calling frame of passed frame
   */
  static void dumpStack (Address fp) {
    if (VM.VerifyAssertions)
      VM._assert(VM.runningVM);

    Address ip = VM_Magic.getReturnAddress(fp);
    fp = VM_Magic.getCallerFramePointer(fp);
    dumpStack( ip, fp );
      
  }

  static int inDumpStack = 0;
  /**
   * Dump state of a (stopped) thread's stack.
   * @param ip instruction pointer for first frame to dump
   * @param fp frame pointer for first frame to dump
   */
  public static void dumpStack (Address ip, Address fp) {
    ++inDumpStack;
    if (inDumpStack > 1 && inDumpStack <= VM.maxSystemTroubleRecursionDepth + VM.maxSystemTroubleRecursionDepthBeforeWeStopVMSysWrite ) {
      VM.sysWrite("VM_Scheduler.dumpStack(): in a recursive call, ");
      VM.sysWrite(inDumpStack);
      VM.sysWriteln(" deep.");
    }
    if (inDumpStack > VM.maxSystemTroubleRecursionDepth) {
      VM.dieAbruptlyRecursiveSystemTrouble();
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
    
    VM.sysWrite("-- Stack --\n");
    while (VM_Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP) ){

      // if code is outside of RVM heap, assume it to be native code,
      // skip to next frame
      if (!MM_Interface.addressInVM(ip)) {
        VM.sysWrite("   <native frame>\n");
        ip = VM_Magic.getReturnAddress(fp);
        fp = VM_Magic.getCallerFramePointer(fp);
        continue; // done printing this stack frame
      } 

      int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
      if (compiledMethodId == INVISIBLE_METHOD_ID) {
        VM.sysWrite("   <invisible method>\n");
      } else {
        // normal java frame(s)
        VM_CompiledMethod compiledMethod    = VM_CompiledMethods.getCompiledMethod(compiledMethodId);
        if (compiledMethod.getCompilerType() == VM_CompiledMethod.TRAP) {
          VM.sysWrite("   <hardware trap>\n");
        } else {
          VM_Method method            = compiledMethod.getMethod();
          int       instructionOffset = compiledMethod.getInstructionOffset(ip);
          int       lineNumber        = compiledMethod.findLineNumberForInstruction(instructionOffset);
          
          //-#if RVM_WITH_OPT_COMPILER
          if (compiledMethod.getCompilerType() == VM_CompiledMethod.OPT) {
            VM_OptCompiledMethod optInfo = (VM_OptCompiledMethod)compiledMethod;
            // Opt stack frames may contain multiple inlined methods.
            VM_OptMachineCodeMap map = optInfo.getMCMap();
            int iei = map.getInlineEncodingForMCOffset(instructionOffset);
            if (iei >= 0) {
              int[] inlineEncoding = map.inlineEncoding;
              int bci = map.getBytecodeIndexForMCOffset(instructionOffset);
              for (int j = iei; j >= 0; j = VM_OptEncodedCallSiteTree.getParent(j,inlineEncoding)) {
                int mid = VM_OptEncodedCallSiteTree.getMethodID(j, inlineEncoding);
                method = VM_MemberReference.getMemberRef(mid).asMethodReference().getResolvedMember();
                lineNumber = ((VM_NormalMethod)method).getLineNumberForBCIndex(bci);
                VM.sysWrite("   ");
                VM.sysWrite(method.getDeclaringClass().getDescriptor());
                VM.sysWrite(" ");
                VM.sysWrite(method.getName());
                VM.sysWrite(method.getDescriptor());
                VM.sysWrite(" at line ");
                VM.sysWriteInt(lineNumber);
                VM.sysWrite("\n");
                if (j > 0) 
                  bci = VM_OptEncodedCallSiteTree.getByteCodeOffset(j, inlineEncoding);
              }
            } else {
              VM.sysWrite("   Unknown location in opt compiled method ");
              VM.sysWrite(method.getDeclaringClass().getDescriptor());
              VM.sysWrite(" ");
              VM.sysWrite(method.getName());
              VM.sysWrite(method.getDescriptor());
              VM.sysWrite("\n");
            }
            ip = VM_Magic.getReturnAddress(fp);
            fp = VM_Magic.getCallerFramePointer(fp);
            continue; // done printing this stack frame
          } 
          //-#endif

          VM.sysWrite("   ");
          VM.sysWrite(method.getDeclaringClass().getDescriptor());
          VM.sysWrite(" ");
          VM.sysWrite(method.getName());
          VM.sysWrite(method.getDescriptor());
          VM.sysWrite(" at line ");
          VM.sysWriteInt(lineNumber);
          VM.sysWrite("\n");
        }
      }
      ip = VM_Magic.getReturnAddress(fp);
      fp = VM_Magic.getCallerFramePointer(fp);
    }
    --inDumpStack;
  }  

  private static boolean exitInProgress = false;
  /**
   * Dump state of a (stopped) thread's stack and exit the virtual machine.
   * @param fp address of starting frame
   * Returned: doesn't return.
   * This method is called from RunBootImage.C when something goes horrifically
   * wrong with exception handling and we want to die with useful diagnostics.
   */
  public static void dumpStackAndDie(Address fp) {
    if (!exitInProgress) {
      // This is the first time I've been called, attempt to exit "cleanly"
      exitInProgress = true;
      dumpStack(fp);
      VM.sysExit(VM.exitStatusDumpStackAndDie);
    } else {
      // Another failure occured while attempting to exit cleanly.  
      // Get out quick and dirty to avoid hanging.
      VM_SysCall.sysExit(VM.exitStatusRecursivelyShuttingDown);
    }
  }

  /**
   * Dump state of virtual machine.
   */ 
  public static void dumpVirtualMachine() 
    throws InterruptiblePragma
  {
    VM_Processor processor;
    VM.sysWrite("\n-- Processors --\n");
    for (int i = 1; i <= numProcessors; ++i) {
      processor = processors[i];
      processor.dumpProcessorState();
    }

    // system queues    
    VM.sysWrite("\n-- System Queues -- \n");   wakeupQueue.dump();
    VM.sysWrite(" wakeupQueue: ");   wakeupQueue.dump();
    VM.sysWrite(" debuggerQueue: "); debuggerQueue.dump();
    VM.sysWrite(" deadVPQueue: ");     deadVPQueue.dump();
    VM.sysWrite(" collectorQueue: ");   collectorQueue.dump();
    VM.sysWrite(" finalizerQueue: ");   finalizerQueue.dump();

    VM.sysWrite("\n-- Threads --\n");
    for (int i = 1; i < threads.length; ++i) {
      if (threads[i] != null) {
        threads[i].dump();
        VM.sysWrite("\n");
      }
    }
    VM.sysWrite("\n");

    VM.sysWrite("\n-- Locks available --\n");
    for (int i = PRIMORDIAL_PROCESSOR_ID; i <= numProcessors; ++i) {
      processor = processors[i];
      int unallocated = processor.lastLockIndex - processor.nextLockIndex + 1;
      VM.sysWrite(" processor ");             VM.sysWriteInt(i); VM.sysWrite(": ");
      VM.sysWriteInt(processor.locksAllocated); VM.sysWrite(" locks allocated, ");
      VM.sysWriteInt(processor.locksFreed);     VM.sysWrite(" locks freed, ");
      VM.sysWriteInt(processor.freeLocks);      VM.sysWrite(" free looks, ");
      VM.sysWriteInt(unallocated);              VM.sysWrite(" unallocated slots\n");
    }
    VM.sysWrite("\n");

    VM.sysWrite("\n-- Locks in use --\n");
    for (int i = 0; i < locks.length; ++i)
      if (locks[i] != null)
        locks[i].dump();
    VM.sysWrite("\n");
  }

  //---------------------------//
  // Low level output locking. //
  //---------------------------//

  static int outputLock;

  static final void lockOutput () {
    if (VM.BuildForSingleVirtualProcessor) return;
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    do {
      int processorId = VM_Magic.prepareInt(VM_Magic.getJTOC(), VM_Entrypoints.outputLockField.getOffset());
      if (processorId == 0 && VM_Magic.attemptInt(VM_Magic.getJTOC(), VM_Entrypoints.outputLockField.getOffset(), 0, VM_Processor.getCurrentProcessorId())) {
        break; 
      }
    } while (true);
    VM_Magic.isync(); // TODO!! is this really necessary?
  }

  static final void unlockOutput () {
    if (VM.BuildForSingleVirtualProcessor) return;
    VM_Magic.sync(); // TODO!! is this really necessary?
    if (true) outputLock = 0; // TODO!! this ought to work, but doesn't?
    else {
      do {
        int processorId = VM_Magic.prepareInt(VM_Magic.getJTOC(), VM_Entrypoints.outputLockField.getOffset());
        if (VM.VerifyAssertions && processorId != VM_Processor.getCurrentProcessorId()) VM.sysExit(VM.exitStatusSysFail);
        if (VM_Magic.attemptInt(VM_Magic.getJTOC(), VM_Entrypoints.outputLockField.getOffset(), processorId, 0)) {
          break; 
        }
      } while (true);
    }
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }

  ////////////////////////////////////////////////
  // fields for synchronizing code patching
  ////////////////////////////////////////////////

  //-#if RVM_FOR_POWERPC
  /**
   * how may processors to be synchronized for code patching, the last
   * one (0) will notify the blocked thread.
   */
  public static int toSyncProcessors;

  /**
   * synchronize object 
   */
  public static Object syncObj = null;
  //-#endif
}
