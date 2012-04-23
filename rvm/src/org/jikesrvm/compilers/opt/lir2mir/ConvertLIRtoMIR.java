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
package org.jikesrvm.compilers.opt.lir2mir;

import static org.jikesrvm.SizeConstants.LOG_BYTES_IN_ADDRESS;
import static org.jikesrvm.compilers.opt.ir.Operators.ARRAYLENGTH_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_2LONG_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_REM_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_2LONG_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_REM_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_ARRAY_ELEMENT_TIB_FROM_TIB_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_CLASS_TIB_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_DOES_IMPLEMENT_FROM_TIB_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_OBJ_TIB_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_SUPERCLASS_IDS_FROM_TIB_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_TYPE_FROM_TIB_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_2DOUBLE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_2FLOAT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_DIV_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_REM_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.SYSCALL;
import static org.jikesrvm.compilers.opt.ir.Operators.SYSCALL_opcode;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.TIB_ARRAY_ELEMENT_TIB_INDEX;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.TIB_DOES_IMPLEMENT_INDEX;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.TIB_SUPERCLASS_IDS_INDEX;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.TIB_TYPE_INDEX;

import org.jikesrvm.VM;
import org.jikesrvm.ArchitectureSpecificOpt.CallingConvention;
import org.jikesrvm.ArchitectureSpecificOpt.ComplexLIR2MIRExpansion;
import org.jikesrvm.ArchitectureSpecificOpt.ConvertALUOperators;
import org.jikesrvm.ArchitectureSpecificOpt.NormalizeConstants;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.NullCheckCombining;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.depgraph.DepGraph;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanAtomicElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanCompositeElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;
import org.jikesrvm.compilers.opt.driver.OptimizingCompiler;
import org.jikesrvm.compilers.opt.hir2lir.ConvertToLowLevelIR;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.GuardedBinary;
import org.jikesrvm.compilers.opt.ir.GuardedUnary;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Load;
import org.jikesrvm.compilers.opt.ir.MIRInfo;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.TypeOperand;
import org.jikesrvm.compilers.opt.liveness.LiveAnalysis;
import org.jikesrvm.objectmodel.JavaHeader;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.Entrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * Convert an IR object from LIR to MIR via BURS
 */
public final class ConvertLIRtoMIR extends OptimizationPlanCompositeElement {

  /**
   * Create this phase element as a composite of other elements
   */
  public ConvertLIRtoMIR() {
    super("Instruction Selection", new OptimizationPlanElement[]{
        // Stage 1: Reduce the LIR operator set to a core set of operators.
        new OptimizationPlanAtomicElement(new ReduceOperators()),

        // Stage 2: Convert ALU operators
        new OptimizationPlanAtomicElement(new ConvertALUOperators()),

        // Stage 3: Normalize usage of constants to simplify Stage 3.
        new OptimizationPlanAtomicElement(new NormalizeConstantsPhase()),

        // Stage 4a: Compute liveness information for DepGraph
        new OptimizationPlanAtomicElement(new DoLiveness()),

        // Stage 4b: Block by block build DepGraph and do
        //           BURS based instruction selection.
        new OptimizationPlanAtomicElement(new DoBURS()),

        // Stage 5: Handle complex operators
        //          (those that expand to multiple basic blocks of MIR).
        new OptimizationPlanAtomicElement(new ComplexOperators()),

        // Stage 6: Use validation operands to do null check combining,
        //          and then finish the removal off all validation
        //          operands (they are not present in the MIR).
        new OptimizationPlanAtomicElement(new NullCheckCombining() {
          @Override
          public void perform(IR ir) {
            super.perform(ir);
            // ir now contains well formed MIR.
            ir.IRStage = IR.MIR;
            ir.MIRInfo = new MIRInfo(ir);
          }
        })});
  }

  /**
   * Stage 1: Reduce the LIR operator set to a core set of operators.
   */
  private static final class ReduceOperators extends CompilerPhase {

    @Override
    public String getName() {
      return "Reduce Operators";
    }

    @Override
    public CompilerPhase newExecution(IR ir) {
      return this;
    }

    @Override
    public void perform(IR ir) {
      for (Instruction s = ir.firstInstructionInCodeOrder(); s != null; s = s.nextInstructionInCodeOrder()) {
        switch (s.getOpcode()) {
          case ARRAYLENGTH_opcode: {
            // array_ref[ObjectModel.getArrayLengthOffset()] contains the length
            Load.mutate(s,
                        INT_LOAD,
                        GuardedUnary.getClearResult(s),
                        GuardedUnary.getClearVal(s),
                        IRTools.AC(ObjectModel.getArrayLengthOffset()),
                        new LocationOperand(),
                        GuardedUnary.getClearGuard(s));
          }
          break;

          case GET_OBJ_TIB_opcode:
            // TODO: valid location operand.
            Operand address = GuardedUnary.getClearVal(s);
            Load.mutate(s,
                        Operators.REF_LOAD,
                        GuardedUnary.getClearResult(s),
                        address,
                        new AddressConstantOperand(JavaHeader.getTibOffset()),
                        null,
                        GuardedUnary.getClearGuard(s));
            break;

          case GET_CLASS_TIB_opcode: {
            RVMType type = ((TypeOperand) Unary.getVal(s)).getVMType();
            Offset offset = type.getTibOffset();
            Load.mutate(s,
                        REF_LOAD,
                        Unary.getClearResult(s),
                        ir.regpool.makeJTOCOp(ir, s),
                        IRTools.AC(offset),
                        new LocationOperand(offset));
          }
          break;

          case GET_TYPE_FROM_TIB_opcode: {
            // TODO: Valid location operand?
            Load.mutate(s,
                        REF_LOAD,
                        Unary.getClearResult(s),
                        Unary.getClearVal(s),
                        IRTools.AC(Offset.fromIntZeroExtend(TIB_TYPE_INDEX << LOG_BYTES_IN_ADDRESS)),
                        null);
          }
          break;

          case GET_SUPERCLASS_IDS_FROM_TIB_opcode: {
            // TODO: Valid location operand?
            Load.mutate(s,
                        REF_LOAD,
                        Unary.getClearResult(s),
                        Unary.getClearVal(s),
                        IRTools.AC(Offset.fromIntZeroExtend(TIB_SUPERCLASS_IDS_INDEX << LOG_BYTES_IN_ADDRESS)),
                        null);
          }
          break;

          case GET_DOES_IMPLEMENT_FROM_TIB_opcode: {
            // TODO: Valid location operand?
            Load.mutate(s,
                        REF_LOAD,
                        Unary.getClearResult(s),
                        Unary.getClearVal(s),
                        IRTools.AC(Offset.fromIntZeroExtend(TIB_DOES_IMPLEMENT_INDEX << LOG_BYTES_IN_ADDRESS)),
                        null);
          }
          break;

          case GET_ARRAY_ELEMENT_TIB_FROM_TIB_opcode: {
            // TODO: Valid location operand?
            Load.mutate(s,
                        REF_LOAD,
                        Unary.getClearResult(s),
                        Unary.getClearVal(s),
                        IRTools.AC(Offset.fromIntZeroExtend(TIB_ARRAY_ELEMENT_TIB_INDEX << LOG_BYTES_IN_ADDRESS)),
                        null);
          }
          break;

          case LONG_DIV_opcode: {
            if (VM.BuildForPowerPC && VM.BuildFor64Addr) break; // don't reduce operator -- leave for BURS
            Call.mutate2(s,
                         SYSCALL,
                         GuardedBinary.getClearResult(s),
                         null,
                         MethodOperand.STATIC(Entrypoints.sysLongDivideIPField),
                         GuardedBinary.getClearVal1(s),
                         GuardedBinary.getClearVal2(s));
            ConvertToLowLevelIR.expandSysCallTarget(s, ir);
            CallingConvention.expandSysCall(s, ir);
          }
          break;

          case LONG_REM_opcode: {
            if (VM.BuildForPowerPC && VM.BuildFor64Addr) break; // don't reduce operator -- leave for BURS
            Call.mutate2(s,
                         SYSCALL,
                         GuardedBinary.getClearResult(s),
                         null,
                         MethodOperand.STATIC(Entrypoints.sysLongRemainderIPField),
                         GuardedBinary.getClearVal1(s),
                         GuardedBinary.getClearVal2(s));
            ConvertToLowLevelIR.expandSysCallTarget(s, ir);
            CallingConvention.expandSysCall(s, ir);
          }
          break;

          case FLOAT_REM_opcode:
          case DOUBLE_REM_opcode: {
            if (VM.BuildForPowerPC) {
              Call.mutate2(s,
                           SYSCALL,
                           Binary.getClearResult(s),
                           null,
                           MethodOperand.STATIC(Entrypoints.sysDoubleRemainderIPField),
                           Binary.getClearVal1(s),
                           Binary.getClearVal2(s));
              ConvertToLowLevelIR.expandSysCallTarget(s, ir);
              CallingConvention.expandSysCall(s, ir);
            }
          }
          break;

          case LONG_2FLOAT_opcode: {
            if (VM.BuildForPowerPC) {
              Call.mutate1(s,
                           SYSCALL,
                           Unary.getClearResult(s),
                           null,
                           MethodOperand.STATIC(Entrypoints.sysLongToFloatIPField),
                           Unary.getClearVal(s));
              ConvertToLowLevelIR.expandSysCallTarget(s, ir);
              CallingConvention.expandSysCall(s, ir);
            }
          }
          break;

          case LONG_2DOUBLE_opcode: {
            if (VM.BuildForPowerPC) {
              Call.mutate1(s,
                           SYSCALL,
                           Unary.getClearResult(s),
                           null,
                           MethodOperand.STATIC(Entrypoints.sysLongToDoubleIPField),
                           Unary.getClearVal(s));
              ConvertToLowLevelIR.expandSysCallTarget(s, ir);
              CallingConvention.expandSysCall(s, ir);
            }
          }
          break;

          case FLOAT_2LONG_opcode: {
            if (VM.BuildForPowerPC && VM.BuildFor64Addr || VM.BuildForSSE2Full) break; // don't reduce operator -- leave for BURS
            Call.mutate1(s,
                         SYSCALL,
                         Unary.getClearResult(s),
                         null,
                         MethodOperand.STATIC(Entrypoints.sysFloatToLongIPField),
                         Unary.getClearVal(s));
            ConvertToLowLevelIR.expandSysCallTarget(s, ir);
            CallingConvention.expandSysCall(s, ir);
          }
          break;

          case DOUBLE_2LONG_opcode: {
            if (VM.BuildForPowerPC && VM.BuildFor64Addr || VM.BuildForSSE2Full) break; // don't reduce operator -- leave for BURS
            Call.mutate1(s,
                         SYSCALL,
                         Unary.getClearResult(s),
                         null,
                         MethodOperand.STATIC(Entrypoints.sysDoubleToLongIPField),
                         Unary.getClearVal(s));
            ConvertToLowLevelIR.expandSysCallTarget(s, ir);
            CallingConvention.expandSysCall(s, ir);
          }
          break;
          case SYSCALL_opcode:
            CallingConvention.expandSysCall(s, ir);
            break;
          default:
            break;
        }
      }
    }
  }

  /**
   * Stage 2: Normalize usage of int constants to make less work in Stage 3.
   */
  private static final class NormalizeConstantsPhase extends CompilerPhase {

    @Override
    public String getName() {
      return "Normalize Constants";
    }

    @Override
    public CompilerPhase newExecution(IR ir) {
      return this;
    }

    @Override
    public void perform(IR ir) {
      NormalizeConstants.perform(ir);
    }
  }

  private static final class DoLiveness extends CompilerPhase {

    @Override
    public String getName() {
      return "Live Handlers";
    }

    @Override
    public CompilerPhase newExecution(IR ir) {
      return this;
    }

    @Override
    public void perform(IR ir) {
      if (ir.options.L2M_HANDLER_LIVENESS) {
        new LiveAnalysis(false, false, true).perform(ir);
      } else {
        ir.setHandlerLivenessComputed(false);
      }
    }
  }

  /**
   * Stage 3: Block by block build DepGraph and do BURS based
   * instruction selection.
   */
  private static final class DoBURS extends CompilerPhase {

    @Override
    public String getName() {
      return "DepGraph & BURS";
    }

    @Override
    public CompilerPhase newExecution(IR ir) {
      return this;
    }

    @Override
    public void reportAdditionalStats() {
      VM.sysWrite("  ");
      VM.sysWrite(container.counter1 / container.counter2 * 100, 2);
      VM.sysWrite("% Infrequent BBs");
    }

    // IR is inconsistent state between DoBURS and ComplexOperators.
    // It isn't verifiable again until after ComplexOperators completes.
    @Override
    public void verify(IR ir) { }

    @Override
    public void perform(IR ir) {
      OptOptions options = ir.options;
      DefUse.recomputeSpansBasicBlock(ir);
      MinimalBURS mburs = new MinimalBURS(ir);
      NormalBURS burs = new NormalBURS(ir);
      for (BasicBlock bb = ir.firstBasicBlockInCodeOrder(); bb != null; bb = bb.nextBasicBlockInCodeOrder()) {
        if (bb.isEmpty()) continue;
        container.counter2++;
        if (bb.getInfrequent()) {
          container.counter1++;
          if (options.FREQ_FOCUS_EFFORT) {
            // Basic block is infrequent -- use quick and dirty instruction selection
            mburs.prepareForBlock(bb);
            mburs.invoke(bb);
            mburs.finalizeBlock(bb);
            continue;
          }
        }
        // Use Normal instruction selection.
        burs.prepareForBlock(bb);
        // I. Build Dependence graph for the basic block
        DepGraph dgraph = new DepGraph(ir, bb.firstRealInstruction(), bb.lastRealInstruction(), bb);
        if (options.PRINT_DG_BURS) {
          // print dependence graph.
          OptimizingCompiler.header("DepGraph", ir.method);
          dgraph.printDepGraph();
          OptimizingCompiler.bottom("DepGraph", ir.method);
        }
        // II. Invoke BURS and rewrite block from LIR to MIR
        try {
          burs.invoke(dgraph);
        } catch (OptimizingCompilerException e) {
          System.err.println("Exception occurred in ConvertLIRtoMIR");
          e.printStackTrace();
          ir.printInstructions();
          throw e;
        }
        burs.finalizeBlock(bb);
      }
    }
  }

  /**
   * Stage 4: Handle complex operators
   * (those that expand to multiple basic blocks).
   */
  private static final class ComplexOperators extends CompilerPhase {

    @Override
    public String getName() {
      return "Complex Operators";
    }

    @Override
    public CompilerPhase newExecution(IR ir) {
      return this;
    }

    @Override
    public void perform(IR ir) {
      ComplexLIR2MIRExpansion.convert(ir);
    }
  }
}
