/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2005
 */
//$Id$
package com.ibm.jikesrvm;

//-#if RVM_FOR_POWERPC
import com.ibm.jikesrvm.ppc.*;
//-#else
import com.ibm.jikesrvm.ia32.*;
//-#endif

/**
 * Flags that specify the configuration of our virtual machine.
 *
 * Note: Changing any <code>final</code> flags requires that the whole vm
 *       be recompiled and rebuilt after their values are changed.
 *
 * @author Bowen Alpern
 * @author Stephen Fink
 * @author David Grove
 */
public abstract class VM_Configuration {
  
  public static final 
  //-#if RVM_FOR_POWERPC
  //-#if RVM_FOR_32_ADDR
  VM_MachineSpecificPowerPC.PPC32 archHelper = VM_MachineSpecificPowerPC.PPC32.singleton;
  //-#else
  VM_MachineSpecificPowerPC.PPC64 archHelper = VM_MachineSpecificPowerPC.PPC64.singleton;
  //-#endif
  //-#else
  //-#if RVM_FOR_32_ADDR
  VM_MachineSpecificIA.IA32 archHelper = VM_MachineSpecificIA.IA32.singleton;
  //-#else
  VM_MachineSpecificIA.EM64T archHelper = VM_MachineSpecificIA.EM64T.singleton;
  //-#endif
  //-#endif


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
          
  /**
   * Multiprocessor operation?
   * <dl>
   * <dt>false</dt>
   *    <dd>VM will use multiple processors (requires an operating system
   *            that supports Posix pthreads, and has "floating stacks")</dd>
   * <dt>true</dt>
   *    <dd>VM will use just one processor and no
   *            synchronization instructions</dd>
   */
  public static final boolean singleVirtualProcessor =
        //-#if RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
          true;
        //-#else
          false;
        //-#endif

  public static final boolean LittleEndian = BuildForIA32;
  
  public static final boolean BuildFor32Addr = 
        //-#if RVM_FOR_32_ADDR
          true;
        //-#else
          false;
        //-#endif

  public static final boolean BuildFor64Addr = 
        //-#if RVM_FOR_64_ADDR
          true;
        //-#else
          false;
        //-#endif

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

  public static final boolean BuildForOsx =
        //-#if RVM_FOR_OSX
          true;
        //-#else
          false;
        //-#endif

  /* ABI selection.  Exactly one of these variables will be true in each build. */
  public static final boolean BuildForMachOABI = BuildForOsx;
  public static final boolean BuildForPowerOpenABI = BuildForAix || (BuildForLinux && BuildForPowerPC && BuildFor64Addr);
  public static final boolean BuildForSVR4ABI = !(BuildForPowerOpenABI || BuildForMachOABI);
          
  public static final boolean BuildWithAllClasses =
        //-#if RVM_WITH_ALL_CLASSES
          true;
        //-#else
          false;
        //-#endif

  /**
   * Can a dereference of a null pointer result in an access
   * to 'low' memory addresses that must be explicitly guarded because the
   * target OS doesn't allow us to read protect low memory?
   */
  public static final boolean ExplicitlyGuardLowMemory = BuildForAix;
  
 /** Assertion checking.
      <dl>
      <dt>false</dt>  <dd> no assertion checking at runtime</dd>
      <dt>true  </dt> <dd> execute assertion checks at runtime</dd>
      <dl>

      Note: code your assertion checks as 
      <pre>
        if (VM.VerifyAssertions) 
          VM._assert(xxx);
      </pre> 
  */
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

  /**  
   * If set, verify that Uninterruptible methods actually cannot be
   * interrupted.
   */ 
  public static final boolean VerifyUnint = true && VerifyAssertions;

  // If set, ignore the supression pragma and print all warning messages.
  public static final boolean ParanoidVerifyUnint = false;

  // Is this an adaptive build?
  public static final boolean BuildForAdaptiveSystem =
      //-#if RVM_WITH_ADAPTIVE_SYSTEM
        true;
      //-#else
        false;
      //-#endif

  // Is this an opt compiler build?
  public static final boolean BuildForOptCompiler =
      //-#if RVM_WITH_OPT_COMPILER
        true;
      //-#else
        false;
      //-#endif
        
   // build with Opt boot image compiler
   public static final boolean BuildWithOptBootImageCompiler =
      //-#if RVM_WITH_OPT_BOOTIMAGE_COMPILER
         true;
      //-#else
         false;
      //-#endif
         
   // build with Base boot image compiler
   public static final boolean BuildWithBaseBootImageCompiler =
      //-#if RVM_WITH_BASE_BOOTIMAGE_COMPILER
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

  /** Epilogue yieldpoints increase sampling accuracy for adaptive
      recompilation.  In particular, they are key for large, leaf, loop-free
      methods.  */
  public static final boolean UseEpilogueYieldPoints =
      //-#if RVM_WITH_ADAPTIVE_SYSTEM
        true;
      //-#else
        false;
      //-#endif

  /** The following configuration objects are final when disabled, but
      non-final when enabled. */
  
  //-#if RVM_FOR_STRESSGC
  public static boolean ParanoidGCCheck       = true;
  public static boolean ForceFrequentGC       = true;
  //-#else
  public final static boolean ParanoidGCCheck = false;
  public final static boolean ForceFrequentGC = false;
  //-#endif

  /** Do we have the facilities to intercept blocking system calls? */
  public final static boolean withoutInterceptBlockingSystemCalls =
    //-#if RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
    true;
    //-#else
    false;
    //-#endif

  /*
   * We often need to slightly tweak the VM boot sequence and/or
   * the library/VM interface depending on the version of GNU classpath
   * we are building against.
   * We always have at least two versions we are supporting (CVS Head and
   * the most recent release).  Sometimes we also support some back-level
   * releases of GNU classpath.
   * For each supported released version, define a static final boolean.
   * We don't define a boolean for CVS head because we prefer to define
   * CVS head as the ! of all other variables.
   * This makes it easier to find an eliminate
   * old code when we move up to the next version.
   */
  
  public final static boolean BuildForClasspath_0_93 =
    //-#if RVM_WITH_CLASSPATH_0_93
    true;
    //-#else
    false;
   //-#endif
    
  public final static boolean BuildWithGCTrace = 
    //-#if RVM_WITH_GCTRACE
    true;
    //-#else
    false;
    //-#endif
    
  public final static boolean BuildWithGCSpy = 
    //-#if RVM_WITH_GCSPY
    true;
    //-#else
    false;
    //-#endif
      
    
}
