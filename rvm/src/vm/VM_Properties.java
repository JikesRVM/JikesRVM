/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Flags that control the behavior of our virtual machine.
 * 
 * Typically these are properties that can be set from the command line
 * (and thus are NOT final).  All final properties should be 
 * declared in VM_Configuration
 *
 * @author Bowen Alpern
 * @author Stephen Fink
 * @author David Grove
 */
public class VM_Properties extends VM_Configuration {

  // The VM class hierarchy is used in three ways:
  //    - by boot image writer to create an executable vm image
  //    - by tools that wish use VM classes for generic java programming
  //    - by vm image itself, at execution time
  // The following flags specify which behavior is desired.
  //
  
  /**
   * use classes for boot image generation? (see BootImageWriter)
   */
  public static boolean writingBootImage; 
  /**
   * use classes for generic java programming?
   */
  public static boolean runningTool;      
  /**
   * use classes for running actual VM?
   */
  public static boolean runningVM;        
  /**
   * if true, don't exit from the process
   */
  public static boolean runningAsSubsystem;  

  // Use count of method prologues executed rather than timer interrupts to drive
  // preemptive thread switching.  Non preemptive thread switching is achieved by
  // setting the number of prologues between thread switches to infinity (-1).
  //
  public static int deterministicThreadSwitchInterval =
	//-#if RVM_WITHOUT_PREEMPTIVE_THREAD_SWITCHING 
	  -1;
	//-#else
        //-#if RVM_WITH_DETERMINISTIC_THREAD_SWITCHING
           1000;
        //-#else // the normal case (timer-driven preemptive thread switching)
	  0;
	//-#endif
	//-#endif

  // suppress code gen when system is running on remote interpreter portion of jdp.
  public static boolean runningAsJDPRemoteInterpreter;

  /**
   * The following is set on by -verbose:class command line arg.
   * When true, it generates messages to the sysWrite stream summarizing
   * class loading activities
   */
  public static boolean verboseClassLoading = false;

  // Symbolic info to support debugger.
  //
  public static boolean LoadLocalVariableTables = false;

  /**
   * The following is set on by -X:measureCompilation=true command line arg.
   * When true, it times compilations and generates a report at VM exit.
   */
  public static boolean MeasureCompilation = false;  

  /**
   * Accumulate per java thread CPU time.
   * Used by AOS and MeasureCompilation to get accurate compilation times.
   */
  public static boolean EnableCPUMonitoring = VM_Configuration.BuildForAdaptiveSystem;

  /**
   * The following is set on by -X:verify=true command line arg.
   * When true, it invokes the bytecode verifier
   */
  public static boolean VerifyBytecode = false;  

  // Runtime subsystem tracing.
  //
  public static final boolean TraceDictionaries       = false;
  public static final boolean TraceStatics            = false;
  public static final boolean TraceFileSystem         = false;
  public static final boolean TraceThreads            = false;
  public static final boolean TraceStackTrace         = false;
  public static boolean TraceClassLoading             = true;

  // Baseline compiler reference map tracing.
  //
  public static final boolean TraceStkMaps                  = false;
  public static final boolean ReferenceMapsStatistics       = false;
  public static final boolean ReferenceMapsBitStatistics    = false;

  // Event logging.
  //
  public static final boolean BuildForEventLogging      = false;
  public static       boolean EventLoggingEnabled       = false;  // TODO!! make this final, see profiler/VM_EventLogger.java
  public static final boolean BuildForNetworkMonitoring = false;
}
