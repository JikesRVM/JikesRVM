/*
 * (C) Copyright IBM Corp 2003
 */
//$Id$

package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.*;
import com.ibm.JikesRVM.classloader.*;
import java.util.Vector;
import java.util.Enumeration;

import org.vmmagic.pragma.*;

/** 
 *  VM_CounterBasedSampling.java
 *
 *  Contains necessary infrastructure to perform counter-based
 *  sampling used with the instrumentation sampling code (PLDI'01)
 *  (see OPT_InstrumentationSamplingFramework)  
 *
 *  @author Matthew Arnold 
 * */
public final class VM_CounterBasedSampling implements Uninterruptible, VM_Constants
{
  static final boolean DEBUG = false;


   /**
    * Holds the value that is used to reset the global counter after
    * a sample is taken.
    */
   public static int resetValue=100;

   /**
    *  The global counter.
    */
   public static int globalCounter=resetValue; 

  /**
   * Perform at system boot.
   */
  public static void boot(VM_AOSOptions options) {
    // Initialize the counter values
    resetValue = options.COUNTER_BASED_SAMPLE_INTERVAL - 1;
    globalCounter=resetValue;

  }
}
