/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.opt.*;

/**
 * An instance of this class is used to store method counters.  It is
 * initialized at startup, and instrumentation phase
 * OPT_InsertMethodInvocationCounter.java inserts instrumentation that
 * writes into this data.
 *
 * @author Matthew Arnold
 */
public final class VM_MethodInvocationCounterData extends VM_ManagedCounterData
  implements VM_Reportable {

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


