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
package org.jikesrvm.compilers.opt.mir2mc;

import org.jikesrvm.compilers.opt.driver.OptimizationPlanAtomicElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanCompositeElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;

/**
 * Convert an IR object from MIR to final Machinecode
 */
public final class ConvertMIRtoMC extends OptimizationPlanCompositeElement {

  /**
   * Create this phase element as a composite of other elements.
   */
  public ConvertMIRtoMC() {
    super("Generate Machine Code", new OptimizationPlanElement[]{
        // Step 1: Final MIR Expansion
        new OptimizationPlanAtomicElement(new FinalMIRExpansionDriver()),
        // Step 2: Assembly and map generation.
        new OptimizationPlanAtomicElement(new AssemblerDriver())});
  }
}
