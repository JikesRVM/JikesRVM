/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

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

  // suppress code gen when system is to be interpreted
  public static boolean BuildForInterpreter;

  /**
   * The following is set on by -verbose:class command line arg.
   * When true, it generates messages to the sysWrite stream summarizing
   * class loading activities
   */
  public static boolean verboseClassLoading = false;

  // Symbolic info to support debugger.
  //
  public static       boolean LoadLocalVariableTables = false;

  /**
   * The following is set on by -X:measureCompilation=true command line arg.
   * When true, it times compilations and generates a report at VM exit.
   */
  public static       boolean MeasureCompilation      = false;  

  /**
   * The following boolean fields provide controls for various diagnostics
   * Normally, these are static final and false, so no overhead is 
   * associated with them.
   * For systems built with RVM_WITH_DIAGNOSTICS=1, these fields a not final.
   * They are tested periodically by system code.  As long as they are false 
   * (the default),
   * the overhead of such tests should be very small.  These fields can be 
   * turned
   * on under program control (and TODO!! by command line arguments).  
   * (If the overhead
   * of checking all these fields is felt to distort normal behavior, 
   * these controls can
   * be enabled individually by editing this file while RVM_WITH_DIAGNOSTICS=0.)
   *
   * (Note, some of the diagnostics may no longer work.)
   */
  //-#if RVM_WITH_DIAGNOSTICS

  // Currently not used.
  //
  public static final boolean BuildWithDiagnostics    = true;

  // Virtual machine subsystem timing.
  //
  public static boolean TraceTimes              = false;
  public static boolean TraceRuntimeTimes       = false;
  public static boolean TraceTypecheckTimes     = false;

  // Runtime subsystem tracing.
  //
  public static boolean TraceDictionaries       = false;
  public static boolean TraceStatics            = false;
  public static boolean TraceRepositoryReading  = false;
  public static boolean TraceClassLoading       = false;
  public static boolean TraceDynamicLinking     = false;
  public static boolean TraceFileSystem         = false;
  public static boolean TraceThreads            = false;

  // Baseline compiler tracing.
  //
  public static boolean TraceAssembler         = false;
  public static boolean PrintAssemblerWarnings = false;
  public static boolean TraceCompilation       = false;

  // Baseline compiler reference map tracing.
  //
  public static boolean TraceStkMaps                  = false;
  public static boolean ReferenceMapsStatistics       = false;
  public static boolean ReferenceMapsBitStatistics    = false;
  public static boolean DynamicReferenceMaps          = false;
  public static boolean ReachLiveGCTwice              = false;

  // Event logging.
  //
  public static final boolean BuildForEventLogging      = true;  // see profiler/EventLogger.java
  public static       boolean EventLoggingEnabled       = false;
  public static       boolean BuildForNetworkMonitoring = false;

  //-#else // RVM_WITH_DIAGNOSTICS=0

  // 
  //
  public static final boolean BuildWithDiagnostics    = false;

  // Virtual machine subsystem timing.
  //
  public static final boolean TraceTimes              = false;
  public static final boolean TraceRuntimeTimes       = false;
  public static final boolean TraceTypecheckTimes     = false;

  // Runtime subsystem tracing.
  //
  public static final boolean TraceDictionaries       = false;
  public static final boolean TraceStatics            = false;
  public static final boolean TraceRepositoryReading  = false;
  public static final boolean TraceClassLoading       = false;
  public static final boolean TraceDynamicLinking     = false;
  public static final boolean TraceFileSystem         = false;
  public static final boolean TraceThreads            = false;

  // Baseline compiler tracing.
  //
  public static final boolean TraceAssembler         = false;
  public static final boolean PrintAssemblerWarnings = false;
  public static final boolean TraceCompilation       = false;

  // Baseline compiler reference map tracing.
  //
  public static final boolean TraceStkMaps                  = false;
  public static final boolean ReferenceMapsStatistics       = false;
  public static final boolean ReferenceMapsBitStatistics    = false;
  public static final boolean DynamicReferenceMaps          = false;
  public static final boolean ReachLiveGCTwice              = false;

  // Event logging.
  //
  public static final boolean BuildForEventLogging      = false;
  public static       boolean EventLoggingEnabled       = false;  // TODO!! make this final, see profiler/VM_EventLogger.java
  public static final boolean BuildForNetworkMonitoring = false;

  //-#endif // RVM_WITH_DIAGNOSTICS

}
