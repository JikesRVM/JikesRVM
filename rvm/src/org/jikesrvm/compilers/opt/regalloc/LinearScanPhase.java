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
package org.jikesrvm.compilers.opt.regalloc;

import java.lang.reflect.Constructor;

import org.jikesrvm.ArchitectureSpecificOpt.PhysicalRegisterConstants;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.regalloc.LinearScan.MappedBasicInterval;

public final class LinearScanPhase extends CompilerPhase
    implements PhysicalRegisterConstants {

  /**
   * An object which manages spill location assignments.
   */
  private SpillLocationManager spillManager;

  private static final Constructor<CompilerPhase> constructor = getCompilerPhaseConstructor(LinearScanPhase.class);

  /**
   * {@inheritDoc}
   * @return compiler phase constructor
   */
  @Override
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  /**
   * @return {@code true} because register allocation is required
   */
  @Override
  public boolean shouldPerform(OptOptions options) {
    return true;
  }

  @Override
  public String getName() {
    return "Linear Scan";
  }

  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   *  Perform the linear scan register allocation algorithm.<p>
   *
   *  See TOPLAS 21(5), Sept 1999, p 895-913
   *  @param ir the IR
   */
  @Override
  public void perform(IR ir) {
    // Create the object that manages spill locations
    spillManager = new SpillLocationManager(ir);

    // Create an (empty) set of active intervals.
    ActiveSet active = new ActiveSet(ir, spillManager);
    ir.MIRInfo.linearScanState.active = active;

    // Intervals sorted by increasing start point
    for (BasicInterval b : ir.MIRInfo.linearScanState.intervals) {

      MappedBasicInterval bi = (MappedBasicInterval) b;
      CompoundInterval ci = bi.container;

      active.expireOldIntervals(bi);

      // If the interval does not correspond to a physical register
      // then we process it.
      if (!ci.getRegister().isPhysical()) {
        // Update register allocation based on the new interval.
        active.allocate(bi, ci);
      } else {
        // Mark the physical register as currently allocated.
        ci.getRegister().allocateRegister();
      }
      active.add(bi);
    }

    // update the state.
    if (active.spilledSomething()) {
      ir.MIRInfo.linearScanState.spilledSomething = true;
    }
  }
}
