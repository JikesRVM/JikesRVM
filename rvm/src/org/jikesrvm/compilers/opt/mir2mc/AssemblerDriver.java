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

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.ArchitectureSpecificOpt.AssemblerOpt;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptimizingCompiler;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;

/**
 * A compiler phase that generates machine code instructions and maps.
 */
final class AssemblerDriver extends CompilerPhase implements Constants {

  @Override
  public String getName() {
    return "Assembler Driver";
  }

  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    //don't bother printing afterwards, PRINT_MACHINECODE handles that
    return before && options.DEBUG_CODEGEN;
  }

  // this class has no instance fields.
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  @Override
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
    int codeLength = AssemblerOpt.generateCode(ir, shouldPrint);

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
      Memory.sync(Magic.objectAsAddress(ir.MIRInfo.machinecode),
                     codeLength << ArchitectureSpecific.RegisterConstants.LG_INSTRUCTION_WIDTH);
    }
  }

  @Override
  public void verify(IR ir) {
    /* Do nothing, IR invariants violated by final expansion*/
  }
}
