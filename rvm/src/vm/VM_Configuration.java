/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Flags that specify the configuration of our virtual machine.
 *
 * Note: Final controls require the whole vm to be recompiled and 
 *       rebuilt after their values are changed.
 *
 * @author Bowen Alpern
 * @author Stephen Fink
 * @author David Grove
 */
public abstract class VM_Configuration {

  public static final boolean BuildForPowerPC =
	//-#if RVM_FOR_POWERPC
	  true;
	//-#else
	  false;
	//-#endif

  public static final boolean BuildForIA32 =
	//-#if RVM_FOR_IA32
	  true;
	//-#else
	  false;
	//-#endif

  public static final boolean LITTLE_ENDIAN = BuildForIA32;

  public static final boolean BuildForAix =
	//-#if RVM_FOR_AIX
	  true;
	//-#else
	  false;
	//-#endif

  public static final boolean BuildForLinux =
	//-#if RVM_FOR_LINUX
	  true;
	//-#else
	  false;
	//-#endif

  // Assertion checking.
  //  false --> no assertion checking at runtime
  //  true  --> execute assertion checks at runtime
  //
  // Note: code your assertion checks as 
  // "if (VM.VerifyAssertions) VM._assert(xxx);"
  //
  public static final boolean VerifyAssertions = 
        //-#if RVM_WITHOUT_ASSERTIONS
          false;
        //-#else
          true;
        //-#endif

  public static final boolean ExtremeAssertions = 
        //-#if RVM_WITH_EXTREME_ASSERTIONS
          true;
        //-#else
          false;
        //-#endif

  // Verify that Uninterruptible methods actually cannot be interrupted.
  // Disable until we can fix the last couple problem cases.
  public static final boolean VerifyUnint = true && VerifyAssertions;

  // Ignore supression pragma and print all warning messages.
  public static final boolean ParanoidVerifyUnint = false;

  // Multiprocessor operation.
  //  false --> vm will use multiple processors (requires operatying system that
  //            supports posix pthread, e.g., AIX)
  //  true  --> vm will use just one processor and no
  //            synchronization instructions
  //
  public static final boolean BuildForSingleVirtualProcessor =
	//-#if RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
	  true;
	//-#else
	  false;
	//-#endif

  // Use count of method prologues executed rather than timer interrupts to drive
  // preemptive thread switching.  Non preemptive thread switching is achieved by
  // setting the number of prologues between thread switches to infinity (-1).
  //
  public static final boolean BuildForDeterministicThreadSwitching =
	//-#if RVM_WITH_DETERMINISTIC_THREAD_SWITCHING
	  true;
	//-#else
        //-#if RVM_WITHOUT_PREEMPTIVE_THREAD_SWITCHING 
          true;
        //-#else
          false;
	//-#endif
	//-#endif

  // Does this build include support for Hardware Performance Monitors
  public static final boolean BuildForHPM = 
    //-#if RVM_WITH_HPM
      true;
    //-#else 
      false;
    //-#endif

  // Is this an adaptive build?
  public static final boolean BuildForAdaptiveSystem =
      //-#if RVM_WITH_ADAPTIVE_SYSTEM
        true;
      //-#else
        false;
      //-#endif

  // Interface method invocation.
  // We have five mechanisms:
  //   IMT-based (Alpern, Cocchi, Fink, Grove, and Lieber). 
  //    - embedded directly in the TIB
  //    - indirectly accessed off the TIB
  //   ITable-based
  //    - directly indexed (by interface id) iTables. 
  //    - searched (at dispatch time); 
  //   Naive, class object is searched for matching method on every dispatch.
  public static final boolean BuildForIMTInterfaceInvocation = true;
  public static final boolean BuildForIndirectIMT = true && 
                                              BuildForIMTInterfaceInvocation;
  public static final boolean BuildForEmbeddedIMT = !BuildForIndirectIMT && 
                                              BuildForIMTInterfaceInvocation;
  public static final boolean BuildForITableInterfaceInvocation = true && 
                                              !BuildForIMTInterfaceInvocation;
  public static final boolean DirectlyIndexedITables = false;

  // Epilogue yieldpoints increase sampling accuracy for adaptive recompilation.
  // In particular, they are key for large, leaf, loop-free methods.
  public static final boolean UseEpilogueYieldPoints =
      //-#if RVM_WITH_ADAPTIVE_SYSTEM
        true;
      //-#else
        false;
      //-#endif

  // Adaptive compilation.
  //
  public static final boolean LogAOSEvents =
      //-#if RVM_WITHOUT_AOS_LOG 
        false;
      //-#else
        true;
      //-#endif

  // Lazy vs. eager method compilation during class loading.
  //
  public static final boolean BuildForLazyCompilation =
      //-#if RVM_WITHOUT_LAZY_COMPILATION
        false;
      //-#else
        true;
      //-#endif

  // Capture threads that have gone Native (JNI) and not come back.  Issolate them
  // in Native.  Create a new (Native) virtual processor for them.  And create (or revive)
  // new pThreads to run the old virtual processors.
  //
  public static final boolean BuildWithNativeDaemonProcessor = 
    //-#if RVM_WITH_NATIVE_DAEMON_PROCESSOR
    !BuildForSingleVirtualProcessor;
    //-#else
    false;
    //-#endif

  // The following configuration objects are final when disabled, but
  // non-final when enabled.
  
  //-#if RVM_FOR_STRESSGC
  public static boolean ParanoidGCCheck       = true;
  public static boolean ForceFrequentGC       = true;
  //-#else
  public final static boolean ParanoidGCCheck = false;
  public final static boolean ForceFrequentGC = false;
  //-#endif

  public final static boolean CompileForGCTracing = false;

  //-#if RVM_FOR_IA32
  /**
   * Is ESI dedicated to always hold the processor register?
   */
  public final static boolean dedicatedESI = true;
  //-#endif
}
