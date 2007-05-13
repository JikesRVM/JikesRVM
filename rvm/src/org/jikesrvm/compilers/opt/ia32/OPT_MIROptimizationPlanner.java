/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ia32;

import java.util.ArrayList;
import org.jikesrvm.compilers.opt.OPT_ConvertLIRtoMIR;
import org.jikesrvm.compilers.opt.OPT_ConvertMIRtoMC;
import org.jikesrvm.compilers.opt.OPT_ExpandCallingConvention;
import org.jikesrvm.compilers.opt.OPT_IRPrinter;
import org.jikesrvm.compilers.opt.OPT_LiveAnalysis;
import org.jikesrvm.compilers.opt.OPT_MIRBranchOptimizations;
import org.jikesrvm.compilers.opt.OPT_MutateSplits;
import org.jikesrvm.compilers.opt.OPT_OptimizationPlanElement;
import org.jikesrvm.compilers.opt.OPT_OptimizationPlanner;
import org.jikesrvm.compilers.opt.OPT_Options;
import org.jikesrvm.compilers.opt.OPT_PrologueEpilogueCreator;
import org.jikesrvm.compilers.opt.OPT_RegisterAllocator;
import org.jikesrvm.compilers.opt.OPT_SplitBasicBlock;

/**
 * This class specifies the order in which OPT_CompilerPhases are
 * executed in the target-specific backend of the optimzing compiler.
 * The methods LIR2MIR, MIROptimizations, and MIR2MC each specify the
 * elements that make up the main compilation stages.
 */
public class OPT_MIROptimizationPlanner extends OPT_OptimizationPlanner {

  /**
   * Initialize the "master plan" for the IA32 backend of the opt compiler.
   */
  public static void intializeMasterPlan(ArrayList<OPT_OptimizationPlanElement> temp) {
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
  private static void LIR2MIR(ArrayList<OPT_OptimizationPlanElement> p) {
    composeComponents(p, "Convert LIR to MIR", new Object[]{
        // Split very large basic blocks into smaller ones.
        new OPT_SplitBasicBlock(),
        // Optional printing of final LIR
        new OPT_IRPrinter("Final LIR") {
          public boolean shouldPerform(OPT_Options options) {
            return options.PRINT_FINAL_LIR;
          }
        },
        // Change operations that split live ranges to moves
        new OPT_MutateSplits(),
        // Instruction Selection
        new OPT_ConvertLIRtoMIR(),

        // Optional printing of initial MIR
        new OPT_IRPrinter("Initial MIR") {
          public boolean shouldPerform(OPT_Options options) {
            return options.PRINT_MIR;
          }
        }
    });
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed on IA32 MIR.
   *
   * @param p the plan under construction
   */
  private static void MIROptimizations(ArrayList<OPT_OptimizationPlanElement> p) {
    // Register Allocation
    composeComponents(p, "Register Mapping", new Object[]{
        new OPT_MIRSplitRanges(),
        // MANDATORY: Expand calling convention
        new OPT_ExpandCallingConvention(),
        // MANDATORY: Insert defs/uses due to floating-point stack
        new OPT_ExpandFPRStackConvention(),
        // MANDATORY: Perform Live analysis and create GC maps
        new OPT_LiveAnalysis(true, false),
        // MANDATORY: Perform register allocation
        new OPT_RegisterAllocator(),
        // MANDATORY: Add prologue and epilogue
        new OPT_PrologueEpilogueCreator(),
    });
    // Peephole branch optimizations
    addComponent(p, new OPT_MIRBranchOptimizations(1));
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed to convert IA32 MIR into
   * ready-to-execute machinecode (and associated mapping tables).
   *
   * @param p the plan under construction
   */
  private static void MIR2MC(ArrayList<OPT_OptimizationPlanElement> p) {
    // MANDATORY: Final assembly
    addComponent(p, new OPT_ConvertMIRtoMC());
  }

}
