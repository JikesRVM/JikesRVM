/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2003
 */

package com.ibm.jikesrvm.adaptive;

import com.ibm.jikesrvm.*;

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
@Uninterruptible public final class VM_CounterBasedSampling implements VM_Constants
{
  static final boolean DEBUG = false;


   /**
    * Holds the value that is used to reset the global counter after
    * a sample is taken.
    */
  static int resetValue=100;

   /**
    *  The global counter.
    */
  static int globalCounter=resetValue; 

  /**
   * Perform at system boot.
   */
  public static void boot(VM_AOSOptions options) {
    // Initialize the counter values
    resetValue = options.COUNTER_BASED_SAMPLE_INTERVAL - 1;
    globalCounter=resetValue;

  }
}
