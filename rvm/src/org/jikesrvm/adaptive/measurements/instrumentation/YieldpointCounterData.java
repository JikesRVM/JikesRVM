/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.adaptive.measurements.instrumentation;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.measurements.Reportable;
import org.jikesrvm.compilers.opt.InstrumentedEventCounterManager;

/**
 * An extension of StringEventCounterData so that the printing can
 * be specialized for yieldpoints.  Otherwise, the functionality is
 * identical.
 */
public final class YieldpointCounterData extends StringEventCounterData implements Reportable {

  static final boolean DEBUG = false;

  /**
   *  Constructor
   *
   * @param manager the manager that will provide the counter space
   **/
  YieldpointCounterData(InstrumentedEventCounterManager manager) {
    super(manager, "Yieldpoint Counter");

    automaticallyGrowCounters(true);
  }

  /**
   *  Called at end when data should dump its contents.
   */
  @Override
  public void report() {
    // Turn off future instrumentation so that the data structures do
    // not change while we are iterating over them
    Instrumentation.disableInstrumentation();

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

}


