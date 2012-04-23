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
package org.jikesrvm.compilers.opt.driver.ia32;

import java.util.ArrayList;

import org.jikesrvm.compilers.opt.MutateSplits;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.controlflow.MIRBranchOptimizations;
import org.jikesrvm.compilers.opt.driver.IRPrinter;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanner;
import org.jikesrvm.compilers.opt.lir2mir.ConvertLIRtoMIR;
import org.jikesrvm.compilers.opt.lir2mir.SplitBasicBlock;
import org.jikesrvm.compilers.opt.liveness.LiveAnalysis;
import org.jikesrvm.compilers.opt.mir2mc.ConvertMIRtoMC;
import org.jikesrvm.compilers.opt.regalloc.ExpandCallingConvention;
import org.jikesrvm.compilers.opt.regalloc.PrologueEpilogueCreator;
import org.jikesrvm.compilers.opt.regalloc.RegisterAllocator;
import org.jikesrvm.compilers.opt.regalloc.ia32.ExpandFPRStackConvention;
import org.jikesrvm.compilers.opt.regalloc.ia32.MIRSplitRanges;

/**
 * This class specifies the order in which CompilerPhases are
 * executed in the target-specific backend of the optimzing compiler.
 * The methods LIR2MIR, MIROptimizations, and MIR2MC each specify the
 * elements that make up the main compilation stages.
 */
public class MIROptimizationPlanner extends OptimizationPlanner {

  /**
   * Initialize the "master plan" for the IA32 backend of the opt compiler.
   */
  public static void intializeMasterPlan(ArrayList<OptimizationPlanElement> temp) {
    LIR2MIR(temp);
    MIROptimizations(temp);
    MIR2MC(temp);
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed to convert LIR to IA32 MIR.
   *
   * @param p the plan under construction
   */
  private static void LIR2MIR(ArrayList<OptimizationPlanElement> p) {
    composeComponents(p, "Convert LIR to MIR", new Object[]{
        // Split very large basic blocks into smaller ones.
        new SplitBasicBlock(),
        // Optional printing of final LIR
        new IRPrinter("Final LIR") {
          @Override
          public boolean shouldPerform(OptOptions options) {
            return options.PRINT_FINAL_LIR;
          }
        },
        // Change operations that split live ranges to moves
        new MutateSplits(),
        // Instruction Selection
        new ConvertLIRtoMIR(),

        // Optional printing of initial MIR
        new IRPrinter("Initial MIR") {
          @Override
          public boolean shouldPerform(OptOptions options) {
            return options.PRINT_MIR;
          }
        }});
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed on IA32 MIR.
   *
   * @param p the plan under construction
   */
  private static void MIROptimizations(ArrayList<OptimizationPlanElement> p) {
    // Register Allocation
    composeComponents(p, "Register Mapping", new Object[]{new MIRSplitRanges(),
                                                          // MANDATORY: Expand calling convention
                                                          new ExpandCallingConvention(),
                                                          // MANDATORY: Insert defs/uses due to floating-point stack
                                                          new ExpandFPRStackConvention(),
                                                          // MANDATORY: Perform Live analysis and create GC maps
                                                          new LiveAnalysis(true, false),
                                                          // MANDATORY: Perform register allocation
                                                          new RegisterAllocator(),
                                                          // MANDATORY: Add prologue and epilogue
                                                          new PrologueEpilogueCreator(),});
    // Peephole branch optimizations
    addComponent(p, new MIRBranchOptimizations(1));
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed to convert IA32 MIR into
   * ready-to-execute machinecode (and associated mapping tables).
   *
   * @param p the plan under construction
   */
  private static void MIR2MC(ArrayList<OptimizationPlanElement> p) {
    // MANDATORY: Final assembly
    addComponent(p, new ConvertMIRtoMC());
  }

}
