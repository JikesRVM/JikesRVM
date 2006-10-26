/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.adaptive;

/**
 * VM_AOSDatabase.java
 * 
 * Used to keep track of the various data structures that make up the
 * AOS database.  
 *
 * @author Matthew Arnold 
 */
public final class VM_AOSDatabase 
{
  /** 
   * Static links to data objects that are "whole-program" (as opposed
   * to per-method)
    */
  public static VM_MethodInvocationCounterData methodInvocationCounterData;
  public static VM_YieldpointCounterData yieldpointCounterData;
  public static VM_StringEventCounterData instructionCounterData;
  public static VM_StringEventCounterData debuggingCounterData;
 
  /**
   * Called at startup
   **/
  static void boot(VM_AOSOptions options)
  {
  }
}
