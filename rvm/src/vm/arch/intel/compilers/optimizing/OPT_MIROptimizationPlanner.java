/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.ArrayList;

/**
 * This class specifies the order in which OPT_CompilerPhases are
 * executed in the target-specific backend of the optimzing compiler.
 * The methods LIR2MIR, MIROptimizations, and MIR2MC each specify the
 * elements that make up the main compilation stages.
 *
 * @author Stephen Fink
 * @author Dave Grove
 * @author Michael Hind */
class OPT_MIROptimizationPlanner extends OPT_OptimizationPlanner {

  /**
   * Initialize the "master plan" for the IA32 backend of the opt compiler.
   */
  static void intializeMasterPlan(ArrayList temp) {
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
  private static void LIR2MIR(ArrayList p) {
    composeComponents(p, "Convert LIR to MIR", new Object[] {
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
      // For now, always print the Initial MIR
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
  private static void MIROptimizations(ArrayList p) {
    // NullCheck combining and validation operand removal.
    addComponent(p, new OPT_NullCheckCombining());

    // Register Allocation
    composeComponents(p, "Register Mapping", new Object[] {
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
  private static void MIR2MC(ArrayList p) {
    // MANDATORY: Final assembly
    addComponent(p, new OPT_ConvertMIRtoMC());
  }

}
