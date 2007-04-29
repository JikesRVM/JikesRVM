/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ppc;

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
import org.jikesrvm.compilers.opt.OPT_PrePassScheduler;
import org.jikesrvm.compilers.opt.OPT_PrologueEpilogueCreator;
import org.jikesrvm.compilers.opt.OPT_RegisterAllocator;
import org.jikesrvm.compilers.opt.OPT_SplitBasicBlock;

/**
 * This class specifies the order in which OPT_CompilerPhases are
 * executed in the target-specific backend of the optimzing compiler.
 * The methods LIR2MIR, MIROptimizations, and MIR2MC each specify the
 * elements that make up the main compilation stages.
 *
 * @author Stephen Fink
 * @author Dave Grove
 * @author Michael Hind 
 */
public abstract class OPT_MIROptimizationPlanner extends OPT_OptimizationPlanner {


  /**
   * Initialize the "master plan" for the PowerPC backend of the opt compiler.
   */
  public static void intializeMasterPlan(ArrayList<OPT_OptimizationPlanElement> temp) {
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
  private static void LIR2MIR(ArrayList<OPT_OptimizationPlanElement> p) {
    composeComponents(p, "Convert LIR to MIR", new Object[] {
      // Optional printing of final LIR
      new OPT_IRPrinter("Final LIR") {
        public boolean shouldPerform(OPT_Options options) {
          return options.PRINT_FINAL_LIR;
        }
      }, 
      // Split very large basic blocks into smaller ones.
      new OPT_SplitBasicBlock(), 
      // Change operations that split live ranges to moves
      new OPT_MutateSplits(),
      // Instruction selection
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
   * are to be performed on PowerPC MIR.
   *
   * @param p the plan under construction
   */
  private static void MIROptimizations(ArrayList<OPT_OptimizationPlanElement> p) {
    ////////////////////
    // MIR OPTS(1) (before register allocation)
    ////////////////////
    // INSTRUCTION SCHEDULING (PRE-PASS --- PRIOR TO REGISTER ALLOCATION)
    addComponent(p, new OPT_PrePassScheduler());
    ////////////////////
    // GCMapping part1 and RegisterAllocation
    ////////////////////

    composeComponents(p, "Register Mapping", new Object[] {
      // MANDATORY: Expand calling convention
      new OPT_ExpandCallingConvention(),
      // MANDATORY: Perform Live analysis and create GC maps
      new OPT_LiveAnalysis(true, false),
      // MANDATORY: Perform register allocation
      new OPT_RegisterAllocator(),
      // MANDATORY: Add prologue and epilogue
      new OPT_PrologueEpilogueCreator(),
    });
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
    addComponent(p, new OPT_MIRBranchOptimizations(1));
  }

  /** 
   * This method defines the optimization plan elements that
   * are to be performed to convert PowerPC MIR into
   * ready-to-execute machinecode (and associated mapping tables).
   * 
   * @param p the plan under construction
   */
  private static void MIR2MC(ArrayList<OPT_OptimizationPlanElement> p) {
    // MANDATORY: Final assembly
    addComponent(p, new OPT_IRPrinter("Final MIR") {
        public boolean shouldPerform(OPT_Options options) {
          return options.PRINT_FINAL_MIR; } 
      });
    addComponent(p, new OPT_ConvertMIRtoMC());
  }

}
