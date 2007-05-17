/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.adaptive.measurements.instrumentation;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.measurements.VM_Reportable;
import org.jikesrvm.compilers.opt.OPT_InstrumentedEventCounterManager;

/**
 * VM_YieldpointCounterData.java
 *
 * An extension of VM_StringEventCounterData so that the printing can
 * be specialized for yieldpoints.  Otherwise, the functionality is
 * identical.
 *
 * @see VM_StringEventCounterData.java
 *
 *
 **/
public final class VM_YieldpointCounterData extends VM_StringEventCounterData implements VM_Reportable {

  static final boolean DEBUG = false;

  /**
   *  Constructor
   *
   * @param manager the manager that will provide the counter space
   **/
  VM_YieldpointCounterData(OPT_InstrumentedEventCounterManager manager) {
    // Call superclass constructor
    super(manager, "Yieldpoint Counter");

    automaticallyGrowCounters(true);
  }

  /**
   *  Called at end when data should dump its contents.
   */
  public void report() {
    // Turn off future instrumentation so that the data structures do
    // not change while we are iterating over them
    VM_Instrumentation.disableInstrumentation();

    VM.sysWrite("Printing " + dataName + ":\n");
    VM.sysWrite("--------------------------------------------------\n");
    double total = 0;
    double methodEntryTotal = 0;
    double backedgeTotal = 0;
    for (String stringName : stringToCounterMap.keySet()) {
      Integer counterNum = stringToCounterMap.get(stringName);
      double count = getCounter(counterNum);

      VM.sysWrite(count + " " + stringName + "\n");
      total += count;

      // If it's a method entry event
      if (stringName.indexOf("METHOD ENTRY") != -1) {
        methodEntryTotal += count;
      }

      if (stringName.indexOf("BACKEDGE") != -1) {
        backedgeTotal += count;
      }

    }
    VM.sysWrite("Total backedges: " + backedgeTotal + "\n");
    VM.sysWrite("Method Entry Total: " + methodEntryTotal + "\n");
    VM.sysWrite("Total Yieldpoints: " + total + "\n");
  }

} // end of class


