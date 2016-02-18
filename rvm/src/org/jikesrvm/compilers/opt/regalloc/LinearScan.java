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

import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanAtomicElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanCompositeElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;

/**
 * Main driver for linear scan register allocation.
 */
public final class LinearScan extends OptimizationPlanCompositeElement {

  /**
   * Build this phase as a composite of others.
   */
  LinearScan() {
    super("Linear Scan Composite Phase",
          new OptimizationPlanElement[]{new OptimizationPlanAtomicElement(new IntervalAnalysis()),
                                            new OptimizationPlanAtomicElement(new RegisterRestrictionsPhase()),
                                            new OptimizationPlanAtomicElement(new LinearScanPhase()),
                                            new OptimizationPlanAtomicElement(new UpdateGCMaps1()),
                                            new OptimizationPlanAtomicElement(new SpillCode()),
                                            new OptimizationPlanAtomicElement(new UpdateGCMaps2()),
                                            new OptimizationPlanAtomicElement(new UpdateOSRMaps()),});
  }

  /*
   * debug flags
   */
  static final boolean DEBUG = false;
  static final boolean VERBOSE_DEBUG = false;
  static final boolean GC_DEBUG = false;
  static final boolean DEBUG_COALESCE = false;

  /**
   * Register allocation is required
   */
  @Override
  public boolean shouldPerform(OptOptions options) {
    return true;
  }

  @Override
  public String getName() {
    return "Linear Scan Composite Phase";
  }

  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }
}
