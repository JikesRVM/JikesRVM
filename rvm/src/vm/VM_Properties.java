/*
 * (C) Copyright IBM Corp 2001,2002
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
public class VM_Properties extends VM_Options {

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
   * are we in the boot-image-writing portion of boot-image-creation
   */
  public static boolean writingImage = false;
  /**
   * is the running VM fully booted?
   * Set by VM.boot when the VM is fully booted.
   */
  public static boolean fullyBooted = false;

  /**
   * Is dynamic class loading enabled?  Set by VM.boot at the appropriate time.
   */
  public static boolean dynamicClassLoadingEnabled = false;

  /**
   * If true, don't exit from the process.  As of July, 2003, this has not
   * worked in a couple of years, nor has there been much interest in using it.
   * If it is resurrected, we need to check the code that calls dieAbruptlyRecursiveSystemTrouble(), to make
   * sure that instead we just kill the proper threads. 
   */
  public static boolean runningAsSubsystem = false;

  /**
   * The following is set on by -X:verboseBoot= command line arg.
   * When true, it generates messages to the sysWrite stream summarizing
   * progress during the execution of VM.boot
   */
  public static int verboseBoot = 0;

  /**
   * The following is set on by -verbose:class command line arg.
   * When true, it generates messages to the sysWrite stream summarizing
   * class loading activities
   */
  public static boolean verboseClassLoading = false;

  /**
   * The following is set on by -verbose:jni command line arg.
   * When true, it generates messages to the sysWrite stream summarizing
   * jni activities
   */
  public static boolean verboseJNI = false;

  // Runtime subsystem tracing.
  //
  public static final boolean TraceDictionaries       = false;
  public static final boolean TraceStatics            = false;
  public static final boolean TraceFileSystem         = false;
  public static final boolean TraceThreads            = false;
  public static final boolean TraceStackTrace         = false;

  // Baseline compiler reference map tracing.
  //
  public static final boolean TraceStkMaps                  = false;
  public static final boolean ReferenceMapsStatistics       = false;
  public static final boolean ReferenceMapsBitStatistics    = false;

  //-#if RVM_WITH_OSR
  public static final boolean TraceOnStackReplacement   = false; 
  //-#endif

  /** How much farther? */
  public static int maxSystemTroubleRecursionDepthBeforeWeStopVMSysWrite = 3;
}
