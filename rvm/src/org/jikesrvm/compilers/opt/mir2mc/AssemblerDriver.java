/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.mir2mc;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.ArchitectureSpecific.Assembler;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptimizingCompiler;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Memory;

/**
 * A compiler phase that generates machine code instructions and maps.
 */
final class AssemblerDriver extends CompilerPhase implements VM_Constants {

  public String getName() {
    return "Assembler Driver";
  }

  public boolean printingEnabled(OptOptions options, boolean before) {
    //don't bother printing afterwards, PRINT_MACHINECODE handles that
    return before && options.DEBUG_CODEGEN;
  }

  // this class has no instance fields.
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  public void perform(IR ir) {
    OptOptions options = ir.options;
    boolean shouldPrint =
        (options.PRINT_MACHINECODE) &&
        (!ir.options.hasMETHOD_TO_PRINT() || ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString()));

    if (IR.SANITY_CHECK) {
      ir.verify("right before machine codegen", true);
    }

    //////////
    // STEP 2: Generate the machinecode array.
    // As part of the generation, the machinecode offset
    // of every instruction will be set by calling setmcOffset.
    //////////
    int codeLength = Assembler.generateCode(ir, shouldPrint);

    //////////
    // STEP 3: Generate all the mapping information
    // associated with the machine code.
    //////////
    // 3a: Create the exception table
    ir.compiledMethod.createFinalExceptionTable(ir);
    // 3b: Create the primary machine code map
    ir.compiledMethod.createFinalMCMap(ir, codeLength);
    // 3c: Create OSR maps
    ir.compiledMethod.createFinalOSRMap(ir);
    // 3d: Create code patching maps
    if (ir.options.guardWithCodePatch()) {
      ir.compiledMethod.createCodePatchMaps(ir);
    }

    if (shouldPrint) {
      // print exception tables (if any)
      ir.compiledMethod.printExceptionTable();
      OptimizingCompiler.bottom("Final machine code", ir.method);
    }

    if (VM.runningVM) {
      VM_Memory.sync(VM_Magic.objectAsAddress(ir.MIRInfo.machinecode),
                     codeLength << ArchitectureSpecific.VM_RegisterConstants.LG_INSTRUCTION_WIDTH);
    }
  }

  public void verify(IR ir) {
    /* Do nothing, IR invariants violated by final expansion*/
  }
}
