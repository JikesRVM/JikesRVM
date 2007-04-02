/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.*;
import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.objectmodel.VM_JavaHeader;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.ArchitectureSpecific.OPT_CallingConvention;
import org.jikesrvm.ArchitectureSpecific.OPT_ComplexLIR2MIRExpansion;
import org.jikesrvm.ArchitectureSpecific.OPT_ConvertALUOperators;
import org.jikesrvm.ArchitectureSpecific.OPT_NormalizeConstants;
import org.jikesrvm.classloader.*;
import org.jikesrvm.compilers.opt.ir.*;
import org.vmmagic.unboxed.Offset;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.*;
import static org.jikesrvm.objectmodel.VM_TIBLayoutConstants.*;
import static org.jikesrvm.VM_SizeConstants.*;

/**
 * Convert an IR object from LIR to MIR via BURS
 *
 * @author Dave Grove
 * @author Mauricio Serrano
 */
public final class OPT_ConvertLIRtoMIR extends OPT_OptimizationPlanCompositeElement {

  /**
   * Create this phase element as a composite of other elements
   */
  public OPT_ConvertLIRtoMIR () {
    super("Instruction Selection", new OPT_OptimizationPlanElement[] {
        // Stage 1: Reduce the LIR operator set to a core set of operators.
        new OPT_OptimizationPlanAtomicElement(new ReduceOperators()), 
        
        // Stage 2: Convert ALU operators 
        new OPT_OptimizationPlanAtomicElement(new OPT_ConvertALUOperators()), 

        // Stage 3: Normalize usage of constants to simplify Stage 3.
        new OPT_OptimizationPlanAtomicElement(new NormalizeConstants()), 

        // Stage 4a: Compute liveness information for DepGraph
        new OPT_OptimizationPlanAtomicElement(new DoLiveness()),

        // Stage 4b: Block by block build DepGraph and do 
        //           BURS based instruction selection.
        new OPT_OptimizationPlanAtomicElement(new DoBURS()), 

        // Stage 5: Handle complex operators 
        //          (those that expand to multiple basic blocks of MIR).
        new OPT_OptimizationPlanAtomicElement(new ComplexOperators()),

        // Stage 6: Use validation operands to do null check combining,
        //          and then finish the removal off all validation
        //          operands (they are not present in the MIR).
        new OPT_OptimizationPlanAtomicElement(new OPT_NullCheckCombining() {
																public void perform(OPT_IR ir) {
																  super.perform(ir);
																  // ir now contains well formed MIR.
																  ir.IRStage = OPT_IR.MIR;
																  ir.MIRInfo = new OPT_MIRInfo(ir);
																}
															 })
			 });
  }

  /**
   * Stage 1: Reduce the LIR operator set to a core set of operators.
   */
  private static final class ReduceOperators extends OPT_CompilerPhase {

    public String getName () {
      return "Reduce Operators";
    }

    public OPT_CompilerPhase newExecution (OPT_IR ir) {
      return this;
    }

    public void perform (OPT_IR ir) {
      for (OPT_Instruction s = ir.firstInstructionInCodeOrder(); s != 
             null; s = s.nextInstructionInCodeOrder()) {
        switch (s.getOpcode()) {
        case ARRAYLENGTH_opcode:
          {
            // array_ref[VM_ObjectModel.getArrayLengthOffset()] contains the length
            Load.mutate(s, INT_LOAD, GuardedUnary.getClearResult(s), 
                        GuardedUnary.getClearVal(s),
                        OPT_IRTools.AC(VM_ObjectModel.getArrayLengthOffset()), 
                        new OPT_LocationOperand(), 
                        GuardedUnary.getClearGuard(s));
          }
          break;

        case GET_OBJ_TIB_opcode:
          // TODO: valid location operand.
          OPT_Operand address = GuardedUnary.getClearVal(s);
          Load.mutate(s, OPT_Operators.REF_LOAD, GuardedUnary.getClearResult(s), 
                      address, new OPT_AddressConstantOperand(VM_JavaHeader.getTibOffset()), 
                      null, GuardedUnary.getClearGuard(s));
          break;

        case GET_CLASS_TIB_opcode:
          {
            VM_Type type = ((OPT_TypeOperand)Unary.getVal(s)).getVMType();
            Offset offset = type.getTibOffset();
            Load.mutate(s, REF_LOAD, Unary.getClearResult(s), 
                        ir.regpool.makeJTOCOp(ir,s), 
                        OPT_IRTools.AC(offset), new OPT_LocationOperand(offset));
          }
          break;

        case GET_TYPE_FROM_TIB_opcode:
          {
            // TODO: Valid location operand?
            Load.mutate(s, REF_LOAD, Unary.getClearResult(s), 
                        Unary.getClearVal(s), 
                        OPT_IRTools.AC(Offset.fromIntZeroExtend(TIB_TYPE_INDEX << LOG_BYTES_IN_ADDRESS)), null);
          }
          break;

        case GET_SUPERCLASS_IDS_FROM_TIB_opcode:
          {
            // TODO: Valid location operand?
            Load.mutate(s, REF_LOAD, Unary.getClearResult(s), 
                        Unary.getClearVal(s), 
                        OPT_IRTools.AC(Offset.fromIntZeroExtend(TIB_SUPERCLASS_IDS_INDEX << LOG_BYTES_IN_ADDRESS)), null);
          }
          break;

        case GET_DOES_IMPLEMENT_FROM_TIB_opcode:
          {
            // TODO: Valid location operand?
            Load.mutate(s, REF_LOAD, Unary.getClearResult(s), 
                        Unary.getClearVal(s), 
                        OPT_IRTools.AC(Offset.fromIntZeroExtend(TIB_DOES_IMPLEMENT_INDEX << LOG_BYTES_IN_ADDRESS)), null);
          }
          break;

        case GET_ARRAY_ELEMENT_TIB_FROM_TIB_opcode:
          {
            // TODO: Valid location operand?
            Load.mutate(s, REF_LOAD, Unary.getClearResult(s), 
                        Unary.getClearVal(s), 
                        OPT_IRTools.AC(Offset.fromIntZeroExtend(TIB_ARRAY_ELEMENT_TIB_INDEX << LOG_BYTES_IN_ADDRESS)), null);
          }
          break;

        case LONG_DIV_opcode:
          {
            if (VM.BuildForPowerPC && VM.BuildFor64Addr) break; // don't reduce operator -- leave for BURS
            Call.mutate2(s, SYSCALL, 
                         GuardedBinary.getClearResult(s), null, 
                         OPT_MethodOperand.STATIC(VM_Entrypoints.sysLongDivideIPField),
                         GuardedBinary.getClearVal1(s), 
                         GuardedBinary.getClearVal2(s));
            OPT_ConvertToLowLevelIR.expandSysCallTarget(s, ir);
            OPT_CallingConvention.expandSysCall(s, ir);
          }
          break;

        case LONG_REM_opcode:
          {
            if (VM.BuildForPowerPC && VM.BuildFor64Addr) break; // don't reduce operator -- leave for BURS
            Call.mutate2(s, SYSCALL, 
                         GuardedBinary.getClearResult(s), null, 
                         OPT_MethodOperand.STATIC(VM_Entrypoints.sysLongRemainderIPField), 
                         GuardedBinary.getClearVal1(s),
                         GuardedBinary.getClearVal2(s));
            OPT_ConvertToLowLevelIR.expandSysCallTarget(s, ir);
            OPT_CallingConvention.expandSysCall(s, ir);
          }
          break;

        case FLOAT_REM_opcode: case DOUBLE_REM_opcode:
          {
            if (VM.BuildForPowerPC) {
              Call.mutate2(s, SYSCALL, 
                           Binary.getClearResult(s), null, 
                           OPT_MethodOperand.STATIC(VM_Entrypoints.sysDoubleRemainderIPField), 
                           Binary.getClearVal1(s),
                           Binary.getClearVal2(s));
              OPT_ConvertToLowLevelIR.expandSysCallTarget(s, ir);
              OPT_CallingConvention.expandSysCall(s, ir);
            }
          }
          break;

        case LONG_2FLOAT_opcode:
          { 
            if (VM.BuildForPowerPC) {
              Call.mutate1(s, SYSCALL,
                           Unary.getClearResult(s), null,
                           OPT_MethodOperand.STATIC(VM_Entrypoints.sysLongToFloatIPField),
                           Unary.getClearVal(s));
              OPT_ConvertToLowLevelIR.expandSysCallTarget(s, ir);
              OPT_CallingConvention.expandSysCall(s, ir);
            }
          }
          break;
          
        case LONG_2DOUBLE_opcode:
          { 
            if (VM.BuildForPowerPC) {
              Call.mutate1(s, SYSCALL,
                           Unary.getClearResult(s),
                           null,
                           OPT_MethodOperand.STATIC(VM_Entrypoints.sysLongToDoubleIPField),
                           Unary.getClearVal(s));
              OPT_ConvertToLowLevelIR.expandSysCallTarget(s, ir);
              OPT_CallingConvention.expandSysCall(s, ir);
            }
          }
          break;
          
        case FLOAT_2LONG_opcode:
          { 
            if (VM.BuildForPowerPC && VM.BuildFor64Addr) break; // don't reduce operator -- leave for BURS
            Call.mutate1(s, SYSCALL,
                         Unary.getClearResult(s),
                         null,
                         OPT_MethodOperand.STATIC(VM_Entrypoints.sysFloatToLongIPField),
                         Unary.getClearVal(s));
            OPT_ConvertToLowLevelIR.expandSysCallTarget(s, ir);
            OPT_CallingConvention.expandSysCall(s, ir);
          }
          break;
          
        case DOUBLE_2LONG_opcode:
          { 
            if (VM.BuildForPowerPC && VM.BuildFor64Addr) break; // don't reduce operator -- leave for BURS
            Call.mutate1(s, SYSCALL,
                         Unary.getClearResult(s),
                         null,
                         OPT_MethodOperand.STATIC(VM_Entrypoints.sysDoubleToLongIPField),
                         Unary.getClearVal(s));
            OPT_ConvertToLowLevelIR.expandSysCallTarget(s, ir);
            OPT_CallingConvention.expandSysCall(s, ir);
          }
          break;

        case SYSCALL_opcode:
          OPT_CallingConvention.expandSysCall(s, ir);
          break;
        }
      }
    }
  }


  /**
   * Stage 2: Normalize usage of int constants to make less work in Stage 3.
   */
  private static final class NormalizeConstants extends OPT_CompilerPhase {

    public String getName () {
      return "Normalize Constants";
    }

    public OPT_CompilerPhase newExecution (OPT_IR ir) {
      return this;
    }

    public void perform (OPT_IR ir) {
      OPT_NormalizeConstants.perform(ir);
    }
  }

  /**
   */
  private static final class DoLiveness extends OPT_CompilerPhase {

    public String getName () {
      return "Live Handlers";
    }

    public OPT_CompilerPhase newExecution (OPT_IR ir) {
      return this;
    }

    public void perform (OPT_IR ir) {
      if (ir.options.HANDLER_LIVENESS) {        
        new OPT_LiveAnalysis(false, false, true).perform(ir);
      }
    }
  }

  /**
   * Stage 3: Block by block build DepGraph and do BURS based 
   * instruction selection.
   */
  private static final class DoBURS extends OPT_CompilerPhase {

    public String getName () {
      return "DepGraph & BURS";
    }

    public OPT_CompilerPhase newExecution (OPT_IR ir) {
      return this;
    }

    public void reportAdditionalStats() {
      VM.sysWrite("  ");
      VM.sysWrite(container.counter1/container.counter2*100, 2);
      VM.sysWrite("% Infrequent BBs");
    }

    // IR is inconsistent state between DoBURS and ComplexOperators.
    // It isn't verifiable again until after ComplexOperators completes.
    public void verify(OPT_IR ir) { }

    public void perform (OPT_IR ir) {
      OPT_Options options = ir.options;
      OPT_DefUse.recomputeSpansBasicBlock(ir);
        OPT_MinimalBURS mburs = new OPT_MinimalBURS(ir);
        OPT_NormalBURS burs = new OPT_NormalBURS(ir);
        for (OPT_BasicBlock bb = ir.firstBasicBlockInCodeOrder(); 
             bb != null; 
             bb = bb.nextBasicBlockInCodeOrder()) {
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
          OPT_DepGraph dgraph = new OPT_DepGraph(ir, 
                                                 bb.firstRealInstruction(), 
                                                 bb.lastRealInstruction(),
                                                 bb);
          if (options.PRINT_DG_BURS) {
            // print dependence graph.
            OPT_Compiler.header("DepGraph", ir.method);
            dgraph.printDepGraph();
            OPT_Compiler.bottom("DepGraph", ir.method);
          }
          if (options.VCG_DG_BURS) {
            // output dependence graph in VCG format.
            // CAUTION: creates A LOT of files (one per BB)
            OPT_VCG.printVCG("depgraph_BURS_" + ir.method + "_" + bb + 
                             ".vcg", dgraph);
          }
          // II. Invoke BURS and rewrite block from LIR to MIR
          try { 
            burs.invoke(dgraph);
          }
          catch (OPT_OptimizingCompilerException e) {
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
  private static final class ComplexOperators extends OPT_CompilerPhase {

    public String getName () {
      return "Complex Operators";
    }

    public OPT_CompilerPhase newExecution (OPT_IR ir) {
      return this;
    }

    public void perform (OPT_IR ir) {
      OPT_ComplexLIR2MIRExpansion.convert(ir);
    }
  }
}
