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
import org.jikesrvm.compilers.opt.InstrumentedEventCounterManager;

/**
 * An instance of this class is used to store method counters.  It is
 * initialized at startup, and instrumentation phase
 * InsertMethodInvocationCounter.java inserts instrumentation that
 * writes into this data.
 */
public final class VM_MethodInvocationCounterData extends VM_ManagedCounterData implements VM_Reportable {

  /**
   * @param manager The manager that will provide the counter space
   */
  VM_MethodInvocationCounterData(InstrumentedEventCounterManager manager) {
    super(manager);
  }

  /**
   *  Part of VM_Reportable interface.  Called on system exit
   */
  public void report() {
    super.report(new VM_MethodNameFunction());
  }

  /**
   *  Part of VM_Reportable interface
   **/
  public void reset() {
    VM._assert(false, "TODO: implement reset for VM_BasicBlockCounterDatabase");
  }

}


