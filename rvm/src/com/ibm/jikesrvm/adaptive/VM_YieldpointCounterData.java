/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.adaptive;

import com.ibm.jikesrvm.VM;
import com.ibm.jikesrvm.opt.*;

/**
 * VM_YieldpointCounterData.java
 *
 * An extension of VM_StringEventCounterData so that the printing can
 * be specialized for yieldpoints.  Otherwise, the functionality is
 * identical.
 *
 * @see VM_StringEventCounterData.java
 *
 * @author Matthew Arnold
 *
**/
public final class VM_YieldpointCounterData extends VM_StringEventCounterData
  implements VM_Reportable 
{

  static final boolean DEBUG=false;


  /**
   *  Constructor
   *
   * @param manager the manager that will provide the counter space
   **/
  VM_YieldpointCounterData(OPT_InstrumentedEventCounterManager manager)
  {
    // Call superclass constructor
    super(manager,"Yieldpoint Counter");

    automaticallyGrowCounters(true);
  }

  /**
   *  Called at end when data should dump its contents.
   */
  public void report()
  {
    // Turn off future instrumentation so that the data structures do
    // not change while we are iterating over them
    VM_Instrumentation.disableInstrumentation();

    VM.sysWrite("Printing " + dataName + ":\n");
    VM.sysWrite("--------------------------------------------------\n");
    double total=0;
    double methodEntryTotal=0;
    double backedgeTotal=0;
    for (String stringName : stringToCounterMap.keySet()) {
      Integer counterNum = stringToCounterMap.get(stringName);
      double count = getCounter(counterNum.intValue());

      VM.sysWrite(count + " " + stringName + "\n");
      total += count;
      
      // If it's a method entry event
      if (stringName.indexOf("METHOD ENTRY") != -1)
        methodEntryTotal += count;
      
      if (stringName.indexOf("BACKEDGE") != -1)
        backedgeTotal += count;

    }
    VM.sysWrite("Total backedges: " + backedgeTotal + "\n");
    VM.sysWrite("Method Entry Total: " + methodEntryTotal + "\n");
    VM.sysWrite("Total Yieldpoints: " + total + "\n");
  }

} // end of class


