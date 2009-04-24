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
package org.jikesrvm.compilers.opt.driver.ppc;

import java.util.ArrayList;

import org.jikesrvm.compilers.opt.MutateSplits;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.controlflow.MIRBranchOptimizations;
import org.jikesrvm.compilers.opt.driver.IRPrinter;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanner;
import org.jikesrvm.compilers.opt.instrsched.PrePassScheduler;
import org.jikesrvm.compilers.opt.lir2mir.ConvertLIRtoMIR;
import org.jikesrvm.compilers.opt.lir2mir.SplitBasicBlock;
import org.jikesrvm.compilers.opt.liveness.LiveAnalysis;
import org.jikesrvm.compilers.opt.mir2mc.ConvertMIRtoMC;
import org.jikesrvm.compilers.opt.regalloc.ExpandCallingConvention;
import org.jikesrvm.compilers.opt.regalloc.PrologueEpilogueCreator;
import org.jikesrvm.compilers.opt.regalloc.RegisterAllocator;

/**
 * This class specifies the order in which CompilerPhases are
 * executed in the target-specific backend of the optimzing compiler.
 * The methods LIR2MIR, MIROptimizations, and MIR2MC each specify the
 * elements that make up the main compilation stages.
 */
public abstract class MIROptimizationPlanner extends OptimizationPlanner {

  /**
   * Initialize the "master plan" for the PowerPC backend of the opt compiler.
   */
  public static void intializeMasterPlan(ArrayList<OptimizationPlanElement> temp) {
    LIR2MIR(temp);
    MIROptimizations(temp);
    MIR2MC(temp);
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed to convert LIR to PowerPC MIR.
   *
   * @param p the plan under construction
   */
  private static void LIR2MIR(ArrayList<OptimizationPlanElement> p) {
    composeComponents(p, "Convert LIR to MIR", new Object[]{
        // Optional printing of final LIR
        new IRPrinter("Final LIR") {
          public boolean shouldPerform(OptOptions options) {
            return options.PRINT_FINAL_LIR;
          }
        },
        // Split very large basic blocks into smaller ones.
        new SplitBasicBlock(),
        // Change operations that split live ranges to moves
        new MutateSplits(),
        // Instruction selection
        new ConvertLIRtoMIR(),
        // Optional printing of initial MIR
        new IRPrinter("Initial MIR") {
          public boolean shouldPerform(OptOptions options) {
            return options.PRINT_MIR;
          }
        }});
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed on PowerPC MIR.
   *
   * @param p the plan under construction
   */
  private static void MIROptimizations(ArrayList<OptimizationPlanElement> p) {
    ////////////////////
    // MIR OPTS(1) (before register allocation)
    ////////////////////
    // INSTRUCTION SCHEDULING (PRE-PASS --- PRIOR TO REGISTER ALLOCATION)
    addComponent(p, new PrePassScheduler());
    ////////////////////
    // GCMapping part1 and RegisterAllocation
    ////////////////////

    composeComponents(p, "Register Mapping", new Object[]{
        // MANDATORY: Expand calling convention
        new ExpandCallingConvention(),
        // MANDATORY: Perform Live analysis and create GC maps
        new LiveAnalysis(true, false),
        // MANDATORY: Perform register allocation
        new RegisterAllocator(),
        // MANDATORY: Add prologue and epilogue
        new PrologueEpilogueCreator(),});
    ////////////////////
    // MIR OPTS(2) (after register allocation)
    // NOTE: GCMapping part 1 has created the GC maps already.
    //       From now until the end of compilation, we cannot change
    //       the set of live references at a GC point
    //       without updating the GCMaps.
    //       Effectively this means that we can only do the
    //       most trivial optimizations from
    //       here on out without having to some potentially complex bookkeeping.
    ////////////////////
    // Peephole branch optimizations
    addComponent(p, new MIRBranchOptimizations(1));
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed to convert PowerPC MIR into
   * ready-to-execute machinecode (and associated mapping tables).
   *
   * @param p the plan under construction
   */
  private static void MIR2MC(ArrayList<OptimizationPlanElement> p) {
    // MANDATORY: Final assembly
    addComponent(p, new IRPrinter("Final MIR") {
      public boolean shouldPerform(OptOptions options) {
        return options.PRINT_FINAL_MIR;
      }
    });
    addComponent(p, new ConvertMIRtoMC());
  }

}
