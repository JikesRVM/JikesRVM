/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Global variables used to implement virtual machine thread scheduler.
 *    - virtual cpus
 *    - threads
 *    - queues
 *    - locks
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_Scheduler implements VM_Constants, VM_Uninterruptible {

  /** Index of initial processor in which "VM.boot()" runs. */
  static final int PRIMORDIAL_PROCESSOR_ID = 1;

  /** Index of thread in which "VM.boot()" runs */
  static final int PRIMORDIAL_THREAD_INDEX = 1;

  // A processors id is its index in the processors array & a threads
  // id is its index in the threads array.  id's start at 1, so that
  // id 0 can be used in locking to represent an unheld lock

  /**
   * Maximum number of VM_Processor's that we can support. In SMP builds
   * the NativeDaemonProcessor takes one slot & the RVM can be run with
   * 1 to MAX_PROCESSORS-1 processors
   **/
  static final int MAX_PROCESSORS = 13;   // allow processors = 1 to 12

  /** Maximum number of VM_Thread's that we can support. */
  static final int LOG_MAX_THREADS = 14;
  static final int MAX_THREADS = 1 << LOG_MAX_THREADS;

  // Flag for controlling virtual-to-physical processor binding.
  //
  static final int NO_CPU_AFFINITY = -1;

  // Virtual cpu's.
  //
  static int                  cpuAffinity   = NO_CPU_AFFINITY; // physical cpu to which first virtual processor is bound (remainder are bound sequentially)
  static int                  numProcessors = 1; // total number of virtual processors to be used
  static VM_Processor[]       processors;        // list thereof (slot 0 always empty)
  static boolean              allProcessorsInitialized; // have all completed initialization?
  static boolean              terminated;        // VM is terminated, clean up and exit

  static int nativeDPndx;

  // Thread creation and deletion.
  //
  static VM_Thread[]          threads;             // list of threads that have been created (slot 0 always empty)
  static int                  threadAllocationIndex; // place to start searching threads[] for next free slot
  static int                  numActiveThreads;    // number of threads running or waiting to run
  static int                  numDaemons;          // number of "daemon" threads, in the java sense
  static VM_DeadThreadQueue   deadQueue;           // pseudo-queue for use in thread termination
  static VM_ProcessorLock     threadCreationMutex; // guard for serializing access to fields above
  static VM_ProcessorQueue    deadVPQueue;         // queue for VPs waiting for callToNative function
  static VM_ProcessorQueue    availableProcessorQueue;         // queue for VPs waiting for callToNative function

  // Thread execution.
  //
  static VM_ProxyWakeupQueue  wakeupQueue;         // threads waiting to wake up from a sleep()
  static VM_ProcessorLock     wakeupMutex;

  static VM_ThreadQueue       debuggerQueue;       // thread waiting to service debugging requests
  static VM_ProcessorLock     debuggerMutex;

  // RCGC addition
  static VM_ThreadQueue       gcWaitQueue;         // threads waiting for GC epoch to finish
  static VM_ProcessorLock     gcWaitMutex;

  static VM_ThreadQueue       collectorQueue;      // collector threads waiting to be resumed
  static VM_ProcessorLock     collectorMutex;

  static VM_ThreadQueue       finalizerQueue;      // Finalizer thread waits here when idle
  static VM_ProcessorLock     finalizerMutex;

  static VM_ProcessorQueue    nativeProcessorQueue;  // queue for VPs available for blocked native threads
  static VM_ProcessorLock     nativeProcessorMutex;

  // JNI external thread service
  static VM_ThreadQueue       attachThreadQueue;   // thread waiting to service external thread attach
  static VM_ProcessorLock     attachThreadMutex;

  // Debugging output.
  //
  // DEPRECATED! use lockOutput() and unlock Output() instead
  //
  static VM_ProcessorLock     outputMutex;         // guard for improving readability of trace output

  // Guarding access to two word volatile fields.
  //
  static VM_ProcessorLock     doublewordVolatileMutex;

  // Thick locks.
  //
  static VM_Lock [] locks;

  // Stack for use by VM_CollectorThread's.
  // !!TODO: this is temporary until we have a way to create
  //         pinned memory objects outside the heap
  //
  static int[][] collectorThreadStacks;

  // Stacks for use by VM_StartupThread's.
  // !!TODO: this is temporary until we have a way to create
  //         pinned memory objects outside the heap
  //
  static int[][] startupThreadStacks;

  // Flag set by external signal to request debugger activation at next thread switch.
  // See also: RunBootImage.C
  //
  static boolean debugRequested;

  // Flag set by AttachCurrentThread (libjni.C) to request new Java thread
  // and native VM_Processor for an external pthread
  // A non-zero value stored here is a pointer to an integer array that 
  // contains the necessary arguments
  static int attachThreadRequested;

  // Trace flags.
  //
  static final boolean countLocks = false;

  // RC (concurrent GC) vars
  static int              globalEpoch = -1;
  final static int EPOCH_MAX = 32 * 1024;
  // ~RC vars

  // Initialize boot image.
  //
  static void init() {
    threadCreationMutex     = new VM_ProcessorLock();
    outputMutex             = new VM_ProcessorLock();
    if (VM.BuildForStrongVolatileSemantics) doublewordVolatileMutex = new VM_ProcessorLock();
    threads                 = new VM_Thread[MAX_THREADS];
    threadAllocationIndex   = PRIMORDIAL_THREAD_INDEX;

    // Enable us to dump a Java Stack from the C trap handler to aid in debugging things that 
    // show up as recursive use of hardware exception registers (eg the long-standing lisp bug)
    VM_BootRecord.the_boot_record.dumpStackAndDieOffset = VM.getMember("LVM_Scheduler;", "dumpStackAndDie", "(I)V").getOffset();

    // pre-allocate pinned stacks for later use in boot()
    //
    collectorThreadStacks = new int[MAX_PROCESSORS][];
    for (int i = 0; i < MAX_PROCESSORS; ++i)
      collectorThreadStacks[i] = VM_RuntimeStructures.newStack(STACK_SIZE_COLLECTOR);

    startupThreadStacks   = new int[MAX_PROCESSORS][];
    for (int i = 0; i < MAX_PROCESSORS; ++i)
      startupThreadStacks[i]   = VM_RuntimeStructures.newStack(STACK_SIZE_NORMAL);

    // allocate initial processor list
    //
    processors = new VM_Processor[1 + PRIMORDIAL_PROCESSOR_ID];
    processors[PRIMORDIAL_PROCESSOR_ID] = new VM_Processor(PRIMORDIAL_PROCESSOR_ID, VM_Processor.RVM);

    // allocate lock structures
    //
    VM_Lock.init();
  }

  // Begin multi-threaded vm operation.
  // Taken:    main thread to be run
  //           VM_Scheduler.numProcessors == number of virtual processors desired
  // Returned: never returns (virtual processors begin running)
  //
  static void boot (VM_Thread mainThread) {
    if (VM.VerifyAssertions) VM.assert(1 <= numProcessors && numProcessors <= MAX_PROCESSORS);

    if (VM.TraceThreads)
      trace("VM_Scheduler.boot","numProcessors =", numProcessors);

    // Create a VM_Processor object for each virtual cpu that we'll be running.
    // Note that the VM_Processor object for the primordial processor
    // (the virtual cpu in whose context we are currently running)
    // was already created in the boot image by init(), above.
    //
    VM_Processor primordialProcessor = processors[PRIMORDIAL_PROCESSOR_ID];

    if (VM.BuildForDedicatedNativeProcessors) {
      processors = new VM_Processor[1 + numProcessors];
    } else {
      processors = new VM_Processor[1 + numProcessors + 1];
    }

    processors[PRIMORDIAL_PROCESSOR_ID] = primordialProcessor;
    for (int i = PRIMORDIAL_PROCESSOR_ID; ++i <= numProcessors; )
      processors[i] = new VM_Processor(i, VM_Processor.RVM);

    if (!VM.BuildForDedicatedNativeProcessors) {
      // setting of vpStatusAddress during JDK building of bootimage is not valid
      // so reset here...maybe change everything to just use index
      primordialProcessor.vpStatusAddress = VM_Magic.objectAsAddress(VM_Processor.vpStatus)+
        (primordialProcessor.vpStatusIndex<<2);
      // Create NativeDaemonProcessor as N+1st processor in the processors array.
      // It is NOT included in "numProcessors" which is the index of the last RVM processor.
      //
      nativeDPndx = numProcessors + 1;		// the last entry in processors[]                                          
      if (VM.BuildWithNativeDaemonProcessor) {
        processors[nativeDPndx] = new VM_Processor(numProcessors + 1, VM_Processor.NATIVEDAEMON);
        if (VM.TraceThreads)
          trace("VM_Scheduler.boot","created nativeDaemonProcessor with id",nativeDPndx);
      } else {
        processors[nativeDPndx] = null;
        if (VM.TraceThreads)
          trace("VM_Scheduler.boot","NativeDaemonProcessor not created");
      }
    }

    // Create work queues.
    //
    wakeupQueue     = new VM_ProxyWakeupQueue(VM_EventLogger.WAKEUP_QUEUE);
    wakeupMutex     = new VM_ProcessorLock();

    debuggerQueue   = new VM_ThreadQueue(VM_EventLogger.DEBUGGER_QUEUE);
    debuggerMutex   = new VM_ProcessorLock();

    attachThreadQueue = new VM_ThreadQueue(VM_EventLogger.ATTACHTHREAD_QUEUE);
    attachThreadMutex = new VM_ProcessorLock();

    // RCGC addition
    gcWaitQueue     = new VM_ThreadQueue(VM_EventLogger.GC_WAIT_QUEUE);
    gcWaitMutex     = new VM_ProcessorLock();
    // ~RC addition

    collectorQueue  = new VM_ThreadQueue(VM_EventLogger.COLLECTOR_QUEUE);
    collectorMutex  = new VM_ProcessorLock();

    finalizerQueue  = new VM_ThreadQueue(VM_EventLogger.FINALIZER_QUEUE);
    finalizerMutex  = new VM_ProcessorLock();

    nativeProcessorQueue  = new VM_ProcessorQueue(VM_EventLogger.DEAD_VP_QUEUE);
    nativeProcessorMutex  = new VM_ProcessorLock();

    deadQueue       = new VM_DeadThreadQueue(VM_EventLogger.DEAD_QUEUE);
    deadVPQueue     = new VM_ProcessorQueue(VM_EventLogger.DEAD_VP_QUEUE);
    availableProcessorQueue     = new VM_ProcessorQueue(VM_EventLogger.DEAD_VP_QUEUE);

    // Create one collector thread and one idle thread per processor.
    //
    VM_CollectorThread.boot(numProcessors);
    for (int i = 0; i < numProcessors; ++i) {
      VM_Thread t;
      t = VM_CollectorThread.createActiveCollectorThread(collectorThreadStacks[i], processors[1+i]);
      t.start(processors[1+i].readyQueue);

      t = new VM_IdleThread(processors[1+i]);
      t.start(processors[1+i].idleQueue);
    }

    VM_Thread t;

    if (VM.BuildWithNativeDaemonProcessor) {
      // Create one collector thread and one idle thread for the NATIVEDAEMON processor
      t = VM_CollectorThread.createActiveCollectorThread(collectorThreadStacks[numProcessors], processors[nativeDPndx]);
      t.start(processors[nativeDPndx].readyQueue);
      t = new VM_IdleThread(processors[nativeDPndx]);
      t.start(processors[nativeDPndx].idleQueue);
      // create the NativeDaemonThread that runs on the NativeDaemonProcessor
      t = new VM_NativeDaemonThread(processors[nativeDPndx]);
      t.start(processors[nativeDPndx].readyQueue);
    }
    // Create one debugger thread.
    //

    t = new DebuggerThread();
    t.start(debuggerQueue);

    // JNI support
    attachThreadRequested = 0;
    terminated = false;         

    // Schedule "main" thread for execution.
    //
    mainThread.start();

    // Create the FinalizerThread
    //
    FinalizerThread tt = new FinalizerThread();
    tt.makeDaemon(true);
    tt.start();

    // the one we're running on
    processors[PRIMORDIAL_PROCESSOR_ID].isInitialized = true; 

    // Create virtual cpu's.
    //

    //-#if RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    //-#else 
    if (VM.BuildWithNativeDaemonProcessor)
      VM.sysInitializeStartupLocks( numProcessors + 1 );
    else
      VM.sysInitializeStartupLocks( numProcessors );

    if (cpuAffinity != NO_CPU_AFFINITY)
      VM.sysVirtualProcessorBind(cpuAffinity + PRIMORDIAL_PROCESSOR_ID - 1); // bind it to a physical cpu

    // VM.disableGC();

    for (int i = PRIMORDIAL_PROCESSOR_ID; ++i <= numProcessors; ) {
      // create VM_Thread for virtual cpu to execute
      //
      VM_Thread target = new VM_StartupThread(startupThreadStacks[i-1]);

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
        VM.sysVirtualProcessorCreate(VM_Magic.getTocPointer(),
                                     VM_Magic.objectAsAddress(processors[i]),
                                     target.contextRegisters.gprs[THREAD_ID_REGISTER],
                                     target.contextRegisters.gprs[FRAME_POINTER]);
        //-#endif
      } else if (VM.BuildForIA32) {
        VM.sysVirtualProcessorCreate(VM_Magic.getTocPointer(),
                                     VM_Magic.objectAsAddress(processors[i]),
                                     target.contextRegisters.ip,
                                     target.contextRegisters.getInnermostFramePointer());
      }

    }

    if (VM.BuildWithNativeDaemonProcessor) {
      VM_Thread target = new VM_StartupThread(startupThreadStacks[numProcessors]);

      processors[nativeDPndx].activeThread = target;
      processors[nativeDPndx].activeThreadStackLimit = target.stackLimit;
      target.registerThread(); // let scheduler know that thread is active.
      if (VM.TraceThreads)
        trace("VM_Scheduler.boot", "starting native daemon processor id", nativeDPndx);
      if (VM.BuildForPowerPC) {
        //-#if RVM_FOR_POWERPC
        VM.sysVirtualProcessorCreate(VM_Magic.getTocPointer(),
                                     VM_Magic.objectAsAddress(processors[nativeDPndx]),
                                     target.contextRegisters.gprs[THREAD_ID_REGISTER],
                                     target.contextRegisters.gprs[FRAME_POINTER]);
        //-#endif
      } else if (VM.BuildForIA32) {
        VM.sysVirtualProcessorCreate(VM_Magic.getTocPointer(),
                                     VM_Magic.objectAsAddress(processors[nativeDPndx]),
                                     target.contextRegisters.ip,
                                     target.contextRegisters.getInnermostFramePointer());
      }
      if (VM.TraceThreads)
        trace("VM_Scheduler.boot", "started native daemon processor id", nativeDPndx);
    }

    // wait for everybody to start up
    //
    VM.sysWaitForVirtualProcessorInitialization();
    //-#endif

    allProcessorsInitialized = true;

    //    for (int i = PRIMORDIAL_PROCESSOR_ID; i <= numProcessors; ++i)
    //      processors[i].enableThreadSwitching();
    VM_Processor.getCurrentProcessor().enableThreadSwitching();

    // Start interrupt driven timeslicer to improve threading fairness and responsiveness.
    //
    if (!VM.BuildForDeterministicThreadSwitching)
      VM.sysVirtualProcessorEnableTimeSlicing();

    // Start event logger.
    //
    if (VM.BuildForEventLogging)
      VM_EventLogger.boot();

    // Allow virtual cpus to commence feeding off the work queues.
    //
    //-#if RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    //-#else
    VM.sysWaitForMultithreadingStart();
    //-#endif

    // End of boot thread. Relinquish control to next job on work queue.
    //
    if (VM.TraceThreads)
      trace("VM_Scheduler.boot", "completed - terminating");

    VM_Thread.terminate();
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
  }

  //
  // Debugging aids.
  //

  /**
   * Override standard assertion checker.
   * @deprecated Should use VM.assert()
   */
  static void assert(boolean b) {
    VM.assert(b);
  }


  // Terminate all the pthreads that belong to the VM
  // This path is used when the VM is taken down by an external pthread via 
  // the JNI call DestroyJavaVM.  All pthreads in the VM must eventually reach this 
  // method from VM_Thread.terminate() for the termination to proceed and for control 
  // to return to the pthread that calls DestroyJavaVM
  // Going by the order in processor[], the pthread for each processor will join with 
  // the next one, and the external pthread calling DestroyJavaVM will join with the
  // main pthread of the VM (see libjni.C)
  //
  // Note:  the NativeIdleThread's don't need to be terminated since they don't have
  // their own pthread;  they run on the external pthreads that had called CreateJavaVM
  // or AttachCurrentThread.
  //
  //
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
      VM.sysCall1(VM_BootRecord.the_boot_record.sysPthreadJoinIP,
                  VPtoWaitFor.pthread_id);
    }

    // then exit myself with pthread_exit
    VM.sysCall0(VM_BootRecord.the_boot_record.sysPthreadExitIP);	

    // does not return
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);

  }



  private static final boolean traceDetails = true;

  // Print out message in format "p[j] (cez#td) who: what", where:
  //    p  = processor id
  //    j  = java thread id
  //    c* = ava thread id of the owner of threadCreationMutex (if any)
  //    e* = java thread id of the owner of threadExecutionMutex (if any)
  //    z* = VM_Processor.getCurrentProcessor().threadSwitchingEnabledCount
  //         (0 means thread switching is enabled outside of the call to debug)
  //    t* = numActiveThreads
  //    d* = numDaemons
  //
  // * parenthetical values, printed only if traceDetails = true)
  //
  // We serialize against a mutex to avoid intermingling debug output from multiple threads.
  //
  public static void trace(String who, String what) {
    lockOutput();
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    writeDecimal(VM_Processor.getCurrentProcessorId());
    writeString("[");
    writeDecimal(VM_Thread.getCurrentThread().getIndex());
    writeString("] ");
    if (traceDetails) {
      writeString("(");
      // writeDecimal(threadCreationMutex.owner);
      // writeString("-");
      // writeDecimal(-VM_Processor.getCurrentProcessor().threadSwitchingEnabledCount);
      // writeString("#");
      writeDecimal(numDaemons);
      writeString("/");
      writeDecimal(numActiveThreads);
      writeString(") ");
    }
    writeString(who);
    writeString(": ");
    writeString(what);
    writeString("\n");
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
    unlockOutput();
  }

  // Print out message in format "p[j] (cez#td) who: what howmany", where:
  //    p  = processor id
  //    j  = java thread id
  //    c* = ava thread id of the owner of threadCreationMutex (if any)
  //    e* = java thread id of the owner of threadExecutionMutex (if any)
  //    z* = VM_Processor.getCurrentProcessor().threadSwitchingEnabledCount
  //         (0 means thread switching is enabled outside of the call to debug)
  //    t* = numActiveThreads
  //    d* = numDaemons
  //
  // * parenthetical values, printed only if traceDetails = true)
  //
  // We serialize against a mutex to avoid intermingling debug output from multiple threads.
  //
  public static void trace(String who, String what, int howmany) {
    _trace( who, what, howmany, false );
  }

  // same as trace, but prints integer value in hex
  //
  public static void traceHex(String who, String what, int howmany) {
    _trace( who, what, howmany, true );
  }

  private static void _trace(String who, String what, int howmany, boolean hex) {
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    lockOutput();
    writeDecimal(VM_Processor.getCurrentProcessorId());
    writeString("[");
    writeDecimal(VM_Thread.getCurrentThread().getIndex());
    writeString("] ");
    if (traceDetails) {
      writeString("(");
      // writeDecimal(threadCreationMutex.owner);
      // writeString("-");
      // writeDecimal(-VM_Processor.getCurrentProcessor().threadSwitchingEnabledCount);
      // writeString("#");
      writeDecimal(numDaemons);
      writeString("/");
      writeDecimal(numActiveThreads);
      writeString(") ");
    }
    writeString(who);
    writeString(": ");
    writeString(what);
    writeString(" ");
    if (hex) 
      writeHex(howmany);
    else
      writeDecimal(howmany);
    writeString("\n");
    unlockOutput();
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }


  // Print interesting scheduler information, starting with a stack traceback.
  // Note: the system could be in a fragile state when this method
  // is called, so we try to rely on as little runtime functionality
  // as possible (eg. use no bytecodes that require VM_Runtime support).
  //
  static void traceback(String message) {
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    lockOutput();
    tracebackWithoutLock(message);
    unlockOutput();
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }

  static void tracebackWithoutLock(String message) {
    writeString(message);
    writeString("\n");

    dumpStack(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()));

    // The following line often causes a hang and prevents overnight sanity tests from finishing.
    // So, for the moment, I commented it out. Maybe someday we can come up with some sort of
    // of dead man timer that will expire and kill us if we take too long to finish. [--DL]
    // dumpVirtualMachine();
  }

  // Dump stack of calling thread, starting at callers frame
  //
  static void dumpStack () {
    dumpStack(VM_Magic.getFramePointer());
  }

  // Dump state of a (stopped) thread's stack.
  // Taken:    address of starting frame. first frame output
  //           is the calling frame of passed frame
  // Returned: nothing
  //
  static void dumpStack (int fp) {
    int ip = VM_Magic.getReturnAddress(fp);
    fp = VM_Magic.getCallerFramePointer(fp);
    dumpStack( ip, fp );
  }

  // Dump state of a (stopped) thread's stack.
  // Taken:    fp & ip for first frame to dump
  // Returned: nothing
  //
  static void dumpStack ( int ip,int fp ) {
    writeString("\n-- Stack --\n");
    while (VM_Magic.getCallerFramePointer(fp) != STACKFRAME_SENTINAL_FP) {

      // if code is outside of RVM heap, assume it to be native code,
      // skip to next frame
      if ( (ip < VM_BootRecord.the_boot_record.startAddress) || 
           (ip > (VM_BootRecord.the_boot_record.largeStart
                  + VM_BootRecord.the_boot_record.largeSize))) {
        writeString("   <native frame>\n");
        ip = VM_Magic.getReturnAddress(fp);
        fp = VM_Magic.getCallerFramePointer(fp);
        continue; // done printing this stack frame
      } 

      int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
      if (compiledMethodId == INVISIBLE_METHOD_ID) {
        writeString("   <invisible method>\n");
      } else {
        // normal java frame(s)
        VM_CompiledMethod compiledMethod    = VM_CompiledMethods.getCompiledMethod(compiledMethodId);
        VM_Method         method            = compiledMethod.getMethod();
        int               instructionOffset = ip - VM_Magic.objectAsAddress(compiledMethod.getInstructions());
        int               lineNumber        = compiledMethod.getCompilerInfo().findLineNumberForInstruction(instructionOffset);

        //-#if RVM_WITH_OPT_COMPILER
        VM_CompilerInfo   info              = compiledMethod.getCompilerInfo();
        if (info.getCompilerType() == VM_CompilerInfo.OPT) {
          VM_OptCompilerInfo optInfo = (VM_OptCompilerInfo)info;
          // Opt stack frames may contain multiple inlined methods.
          VM_OptMachineCodeMap map = optInfo.getMCMap();
          int iei = map.getInlineEncodingForMCOffset(instructionOffset);
          if (iei >= 0) {
            int[] inlineEncoding = map.inlineEncoding;
            int bci = map.getBytecodeIndexForMCOffset(instructionOffset);
            for (int j = iei; j >= 0; j = VM_OptEncodedCallSiteTree.getParent(j,inlineEncoding)) {
              int mid = VM_OptEncodedCallSiteTree.getMethodID(j, inlineEncoding);
              method = VM_MethodDictionary.getValue(mid);
              VM_LineNumberMap lmap = method.getLineNumberMap();
              if (lmap == null) 
                lineNumber = 0;
              else 
                lineNumber = lmap.getLineNumberForBCIndex(bci);
              writeString("   ");
              writeAtom(method.getDeclaringClass().getDescriptor());
              writeString(" ");
              writeAtom(method.getName());
              writeAtom(method.getDescriptor());
              writeString(" at line ");
              writeDecimal(lineNumber);
              writeString("\n");
              if (j > 0) 
                bci = VM_OptEncodedCallSiteTree.getByteCodeOffset(j, inlineEncoding);
            }
          } else {
            writeString("   Unknown location in opt compiled method ");
            writeAtom(method.getDeclaringClass().getDescriptor());
            writeString(" ");
            writeAtom(method.getName());
            writeAtom(method.getDescriptor());
            writeString("\n");
          }
          ip = VM_Magic.getReturnAddress(fp);
          fp = VM_Magic.getCallerFramePointer(fp);
          continue; // done printing this stack frame
        } 
        //-#endif

        writeString("   ");
        writeAtom(method.getDeclaringClass().getDescriptor());
        writeString(" ");
        writeAtom(method.getName());
        writeAtom(method.getDescriptor());
        writeString(" at line ");
        writeDecimal(lineNumber);
        writeString("\n");
      }
      ip = VM_Magic.getReturnAddress(fp);
      fp = VM_Magic.getCallerFramePointer(fp);
    }
  }  // dumpStack(ip,fp)

  // Dump state of a (stopped) thread's stack and exit the virtual machine.
  // Taken:    address of starting frame
  // Returned: doesn't return.
  // This method is called from RunBootImage.C when something goes horrifically
  // wrong with exception handling and we want to die with useful diagnostics.
  private static boolean exitInProgress = false;
  static void dumpStackAndDie(int fp) {
    if (!exitInProgress) {
      // This is the first time I've been called, attempt to exit "cleanly"
      exitInProgress = true;
      dumpStack(fp);
      VM.sysExit(9999);
    } else {
      // Another failure occured while attempting to exit cleanly.  
      // Get out quick and dirty to avoid hanging.
      VM.sysCall1(VM_BootRecord.the_boot_record.sysExitIP, 9999);
    }
  }

  // Dump state of virtual machine.
  //
  static void dumpVirtualMachine() {
    VM_Processor processor;
    writeString("\n-- Processors --\n");
    for (int i = 1; i <= numProcessors; ++i) {
      processor = processors[i];
      processor.dumpProcessorState();
    }

    if (VM.BuildWithNativeDaemonProcessor) {
      writeString("\n-- NativeDaemonProcessor --\n");
      processors[nativeDPndx].dumpProcessorState();
    }

    writeString("\n-- Native Processors --\n");
    for (int i = 1; i <= VM_Processor.numberNativeProcessors;i++) {
      processor =  VM_Processor.nativeProcessors[i];
      if (processor == null) {
        writeString(" NULL processor for nativeProcessors entry = ");
        writeDecimal(i); 
        continue;
      }
      processor.dumpProcessorState();
    }

    // system queues	
    writeString("\n-- System Queues -- \n");   wakeupQueue.dump();
    writeString(" wakeupQueue:");   wakeupQueue.dump();
    writeString(" debuggerQueue:"); debuggerQueue.dump();
    writeString(" gcWaitQueue:");   gcWaitQueue.dump();    // RCGC addition
    writeString(" deadQueue:");     deadQueue.dump();
    writeString(" deadVPQueue:");     deadVPQueue.dump();
    writeString(" collectorQueue:");   collectorQueue.dump();
    writeString(" finalizerQueue:");   finalizerQueue.dump();
    writeString(" nativeProcessorQueue:");   nativeProcessorQueue.dump();

    writeString("\n-- Threads --\n");
    for (int i = 1; i < threads.length; ++i)
      if (threads[i] != null) threads[i].dump();
    writeString("\n");

    writeString("\n-- Locks available --\n");
    for (int i = PRIMORDIAL_PROCESSOR_ID; i <= numProcessors; ++i) {
      processor = processors[i];
      int unallocated = processor.lastLockIndex - processor.nextLockIndex + 1;
      writeString(" processor ");             writeDecimal(i); writeString(": ");
      writeDecimal(processor.locksAllocated); writeString(" locks allocated, ");
      writeDecimal(processor.locksFreed);     writeString(" locks freed, ");
      writeDecimal(processor.freeLocks);      writeString(" free looks, ");
      writeDecimal(unallocated);              writeString(" unallocated slots\n");
    }
    writeString("\n");

    writeString("\n-- Locks in use --\n");
    for (int i = 0; i < locks.length; ++i)
      if (locks[i] != null) locks[i].dump();
    writeString("\n");
  }

  //---------------------------//
  // Low level output locking. //
  //---------------------------//

  static int outputLock;

  static final void lockOutput () {
    if (VM.BuildForSingleVirtualProcessor) return;
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    do {
      int processorId = VM_Magic.prepare(VM_Magic.getJTOC(), VM_Entrypoints.outputLockField.getOffset());
      if (processorId == 0 && VM_Magic.attempt(VM_Magic.getJTOC(), VM_Entrypoints.outputLockField.getOffset(), 0, VM_Processor.getCurrentProcessorId())) {
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
        int processorId = VM_Magic.prepare(VM_Magic.getJTOC(), VM_Entrypoints.outputLockField.getOffset());
        if (VM.VerifyAssertions && processorId != VM_Processor.getCurrentProcessorId()) VM.sysExit(664);
        if (VM_Magic.attempt(VM_Magic.getJTOC(), VM_Entrypoints.outputLockField.getOffset(), processorId, 0)) {
          break; 
        }
      } while (true);
    }
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }

  //--------------------//
  // Low level writing. //
  //--------------------//

  static void writeAtom(VM_Atom value) {
    value.sysWrite();
  }

  static void writeString(String s) {
    VM.sysWrite(s);
  }

  static void writeHex (int n) {
    for (int i=0; i<8; i++) {
      int v = n >>> 28;
      if (v < 10) {
        VM.sysWrite((char) (v + '0'));
      } else {
        VM.sysWrite((char) (v + 'a' - 10));
      }
      n <<= 4;
    }
  }

  static void writeDecimal (int n) {
    if (n < 0) {
      VM.sysWrite('-');
      writeDecimalDigits(-n);
    } else if (n == 0) {
      VM.sysWrite('0');
    } else {
      writeDecimalDigits(n);
    }
  }

  private static void writeDecimalDigits (int n) {
    if (n == 0) return;
    writeDecimalDigits(n/10);
    VM.sysWrite((char) ('0' + (n%10)));
  }
}
