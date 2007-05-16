/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.adaptive.measurements.instrumentation;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.measurements.VM_Reportable;
import org.jikesrvm.compilers.opt.OPT_InstrumentedEventCounterManager;

/**
 * An instance of this class is used to store method counters.  It is
 * initialized at startup, and instrumentation phase
 * OPT_InsertMethodInvocationCounter.java inserts instrumentation that
 * writes into this data.
 */
public final class VM_MethodInvocationCounterData extends VM_ManagedCounterData implements VM_Reportable {

  /**
   * @param manager The manager that will provide the counter space
   */
  VM_MethodInvocationCounterData(OPT_InstrumentedEventCounterManager manager) {
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


