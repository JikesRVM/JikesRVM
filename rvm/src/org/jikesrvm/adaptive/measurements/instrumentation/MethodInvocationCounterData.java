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
 * An instance of this class is used to store method counters.  It is
 * initialized at startup, and instrumentation phase
 * InsertMethodInvocationCounter inserts instrumentation that
 * writes into this data.
 */
public final class MethodInvocationCounterData extends ManagedCounterData implements Reportable {

  /**
   * @param manager The manager that will provide the counter space
   */
  MethodInvocationCounterData(InstrumentedEventCounterManager manager) {
    super(manager);
  }

  /**
   *  Called on system exit
   */
  @Override
  public void report() {
    super.report(new MethodNameFunction());
  }

  @Override
  public void reset() {
    VM._assert(false, "TODO: implement reset for BasicBlockCounterDatabase");
  }

}


