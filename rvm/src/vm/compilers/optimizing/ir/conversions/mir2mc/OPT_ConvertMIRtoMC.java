/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;

/**
 * Convert an IR object from MIR to final Machinecode
 *
 * @author Dave Grove
 * @author Stephen Fink
 */
final class OPT_ConvertMIRtoMC extends OPT_OptimizationPlanCompositeElement {

  /**
   * Create this phase element as a composite of other elements.
   */
  OPT_ConvertMIRtoMC() {
    super("Generate Machine Code", new OPT_OptimizationPlanElement[] {
       // Step 1: Final MIR Expansion
       new OPT_OptimizationPlanAtomicElement(new FinalMIRExpansionDriver()),
       // Step 2: Assembly and map generation.
       new OPT_OptimizationPlanAtomicElement(new AssemblerDriver())
       });
  }

  /**
   * A compiler phase that drives final MIR expansion.
   */
  private static final class FinalMIRExpansionDriver extends OPT_CompilerPhase {
    public final String getName () {
      return "Final MIR Expansion";
    }
  
    public final boolean printingEnabled (OPT_Options options, boolean before) {
      return !before && options.PRINT_FINAL_MIR;
    }
  
    // this class has no instance fields.
    public final OPT_CompilerPhase newExecution (OPT_IR ir) {
      return this;
    }

    public final void perform (OPT_IR ir) {
      OPT_Options options = ir.options;

      if (OPT_IR.SANITY_CHECK) {
        ir.verify("right before Final MIR Expansion", true);
      }

      /* NOTE: MM_Interface.pickAllocator() depends on the name of
         this class and method to identify code allocation */
      int approxMachinecodeSize = OPT_FinalMIRExpansion.expand(ir);
      ir.MIRInfo.machinecode = VM_CodeArray.create(approxMachinecodeSize);
    }
  }
  /**
   * A compiler phase that generates machine code instructions and maps.
   */
  private static final class AssemblerDriver extends OPT_CompilerPhase
    implements VM_Constants {

    public final String getName () {
      return "Assembler Driver";
    }
  
    public final boolean printingEnabled (OPT_Options options, boolean before) {
      //don't bother printing afterwards, PRINT_MACHINECODE handles that
      return before && options.DEBUG_CODEGEN;
    }
  
    // this class has no instance fields.
    public final OPT_CompilerPhase newExecution (OPT_IR ir) {
      return this;
    }
  
    public final void perform (OPT_IR ir) {
      OPT_Options options = ir.options;
      boolean shouldPrint =
        (options.PRINT_MACHINECODE) &&
        (!ir.options.hasMETHOD_TO_PRINT() ||
         ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString()));
      
      
      if (OPT_IR.SANITY_CHECK) {
        ir.verify("right before machine codegen", true);
      }
  
      //////////
      // STEP 2: Generate the machinecode array.
      // As part of the generation, the machinecode offset
      // of every instruction will be set by calling setmcOffset.
      //////////
      int codeLength = OPT_Assembler.generateCode(ir, shouldPrint);

      //////////
      // STEP 3: Generate all the mapping information 
      // associated with the machine code.
      //////////
      // 3a: Create the exception table
      ir.compiledMethod.createFinalExceptionTable(ir);
      // 3b: Create the primary machine code map
      ir.compiledMethod.createFinalMCMap(ir, codeLength);

      //-#if RVM_WITH_OSR
      ir.compiledMethod.createFinalOSRMap(ir);
      //-#endif

      // 3c: Create code patching maps
      if (ir.options.guardWithCodePatch()) {
        ir.compiledMethod.createCodePatchMaps(ir);
      }

      if (shouldPrint) {
        // print exception tables (if any)
        ir.compiledMethod.printExceptionTable();
        OPT_Compiler.bottom("Final machine code", ir.method);
      }
      
      if (VM.runningVM)
        VM_Memory.sync(VM_Magic.objectAsAddress(ir.MIRInfo.machinecode), 
                     codeLength << LG_INSTRUCTION_WIDTH);
    }
  
    public void verify(OPT_IR ir) {
      /* Do nothing, IR invariants violated by final expansion*/
    }
  }
}
