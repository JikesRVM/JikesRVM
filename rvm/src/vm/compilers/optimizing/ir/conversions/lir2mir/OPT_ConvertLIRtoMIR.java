/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import org.vmmagic.unboxed.Offset;

/**
 * Convert an IR object from LIR to MIR via BURS
 *
 * @author Dave Grove
 * @author Mauricio Serrano
 */
final class OPT_ConvertLIRtoMIR extends OPT_OptimizationPlanCompositeElement {

  /**
   * Create this phase element as a composite of other elements
   */
  OPT_ConvertLIRtoMIR () {
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
        new OPT_OptimizationPlanAtomicElement(new ComplexOperators())
        });
  }

  /**
   * Stage 1: Reduce the LIR operator set to a core set of operators.
   */
  private static final class ReduceOperators extends OPT_CompilerPhase
    implements VM_Constants, OPT_Operators, OPT_Constants {

    public final String getName () {
      return "Reduce Operators";
    }

    public final OPT_CompilerPhase newExecution (OPT_IR ir) {
      return this;
    }

    public final void perform (OPT_IR ir) {
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
          {
            VM_ObjectModel.lowerGET_OBJ_TIB(s, ir);
          }
          break;

        case GET_CLASS_TIB_opcode:
          {
            VM_Type type = ((OPT_TypeOperand)Unary.getVal(s)).getVMType();
            Offset offset = type.getTibOffset();
            Load.mutate(s, REF_LOAD, Unary.getClearResult(s), 
                        ir.regpool.makeJTOCOp(ir,s), 
                        OPT_IRTools.AC(offset), new OPT_LocationOperand(offset.toInt()));
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

        //-#if RVM_FOR_32_ADDR
        case LONG_DIV_opcode:
          {
            OPT_Operand val1 = GuardedBinary.getClearVal1(s);
            OPT_Operand val2 = GuardedBinary.getClearVal2(s); 
            if (VM.BuildForPowerPC) {
              // NOTE: must move constants out of sysCall before we expand it.
              //       otherwise we'll have the wrong value in the JTOC register when
              //       we try to load the constant from the JTOC!
              val1 = ensureRegister(val1, s, ir);
              val2 = ensureRegister(val2, s, ir);
            }
            Call.mutate2(s, SYSCALL, 
                         GuardedBinary.getClearResult(s), null, 
                         OPT_MethodOperand.STATIC(VM_Entrypoints.sysLongDivideIPField), 
                         val1, val2);
            OPT_CallingConvention.expandSysCall(s, ir);
          }
          break;

        case LONG_REM_opcode:
          {
            OPT_Operand val1 = GuardedBinary.getClearVal1(s);
            OPT_Operand val2 = GuardedBinary.getClearVal2(s); 
            if (VM.BuildForPowerPC) {
              // NOTE: must move constants out of sysCall before we expand it.
              //       otherwise we'll have the wrong value in the JTOC register when
              //       we try to load the constant from the JTOC!
              val1 = ensureRegister(val1, s, ir);
              val2 = ensureRegister(val2, s, ir);
            }
            Call.mutate2(s, SYSCALL, 
                         GuardedBinary.getClearResult(s), null, 
                         OPT_MethodOperand.STATIC(VM_Entrypoints.sysLongRemainderIPField), 
                         val1, val2);
            OPT_CallingConvention.expandSysCall(s, ir);
          }
          break;
        //-#endif

          //-#if RVM_FOR_POWERPC
        case FLOAT_REM_opcode: case DOUBLE_REM_opcode:
          {
            // NOTE: must move constants out of sysCall before we expand it.
            //       otherwise we'll have the wrong value in the JTOC register when
            //       we try to load the constant from the JTOC!
            OPT_Operand val1 = ensureRegister(Binary.getClearVal1(s), s, ir);
            OPT_Operand val2 = ensureRegister(Binary.getClearVal2(s), s, ir);
            Call.mutate2(s, SYSCALL, 
                         Binary.getClearResult(s), null, 
                         OPT_MethodOperand.STATIC(VM_Entrypoints.sysDoubleRemainderIPField), 
                         val1, val2);
            OPT_CallingConvention.expandSysCall(s, ir);
          }
          break;
          //-#endif

        case LONG_2FLOAT_opcode:
          { 
            if (VM.BuildForPowerPC) {
              // NOTE: must move constants out of sysCall before we expand it.
              //       otherwise we'll have the wrong value in the JTOC register when
              //       we try to load the constant from the JTOC!
              OPT_Operand val = ensureRegister(Unary.getClearVal(s), s, ir);
              Call.mutate1(s, SYSCALL,
                           Unary.getClearResult(s), null,
                           OPT_MethodOperand.STATIC(VM_Entrypoints.sysLongToFloatIPField),
                           val);
              OPT_CallingConvention.expandSysCall(s, ir);
            }
          }
          break;
          
        case LONG_2DOUBLE_opcode:
          { 
            if (VM.BuildForPowerPC) {
              // NOTE: must move constants out of sysCall before we expand it.
              //       otherwise we'll have the wrong value in the JTOC register when
              //       we try to load the constant from the JTOC!
              OPT_Operand val = ensureRegister(Unary.getClearVal(s), s, ir);
              Call.mutate1(s, SYSCALL,
                           Unary.getClearResult(s),
                           null,
                           OPT_MethodOperand.STATIC(VM_Entrypoints.sysLongToDoubleIPField),
                           val);
              OPT_CallingConvention.expandSysCall(s, ir);
            }
          }
          break;
          
        case FLOAT_2LONG_opcode:
          { 
            OPT_Operand val = Unary.getClearVal(s);
            if (VM.BuildForPowerPC) {
              if (VM.BuildFor64Addr) break;
              // NOTE: must move constants out of sysCall before we expand it.
              //       otherwise we'll have the wrong value in the JTOC register when
              //       we try to load the constant from the JTOC!
              val = ensureRegister(val, s, ir);
            }
            Call.mutate1(s, SYSCALL,
                         Unary.getClearResult(s),
                         null,
                         OPT_MethodOperand.STATIC(VM_Entrypoints.sysFloatToLongIPField),
                         val);
            OPT_CallingConvention.expandSysCall(s, ir);
          }
          break;
          
        case DOUBLE_2LONG_opcode:
          { 
            OPT_Operand val = Unary.getClearVal(s);
            if (VM.BuildForPowerPC) {
              if (VM.BuildFor64Addr) break;
              // NOTE: must move constants out of sysCall before we expand it.
              //       otherwise we'll have the wrong value in the JTOC register when
              //       we try to load the constant from the JTOC!
              val = ensureRegister(val, s, ir);
            }
            Call.mutate1(s, SYSCALL,
                         Unary.getClearResult(s),
                         null,
                         OPT_MethodOperand.STATIC(VM_Entrypoints.sysDoubleToLongIPField),
                         val);
            OPT_CallingConvention.expandSysCall(s, ir);
          }
          break;
        }
      }
    }

    private final OPT_Operand ensureRegister(OPT_Operand op, OPT_Instruction s, OPT_IR ir) {
      if (op.isConstant()) {
        VM_TypeReference opType = op.getType();
        OPT_RegisterOperand rop = ir.regpool.makeTemp(opType);
        s.insertBefore(Move.create(OPT_IRTools.getMoveOp(opType), rop, op));
        return rop.copy();
      } else {
        return op;
      }
    }

  }


  /**
   * Stage 2: Normalize usage of int constants to make less work in Stage 3.
   */
  private static final class NormalizeConstants extends OPT_CompilerPhase {

    public final String getName () {
      return "Normalize Constants";
    }

    public final OPT_CompilerPhase newExecution (OPT_IR ir) {
      return this;
    }

    public final void perform (OPT_IR ir) {
      OPT_NormalizeConstants.perform(ir);
    }
  }

  /**
   */
  private static final class DoLiveness extends OPT_CompilerPhase {

    public final String getName () {
      return "Live Handlers";
    }

    public final OPT_CompilerPhase newExecution (OPT_IR ir) {
      return this;
    }

    public final void perform (OPT_IR ir) {
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

    public final String getName () {
      return "DepGraph & BURS";
    }

    public final OPT_CompilerPhase newExecution (OPT_IR ir) {
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

    public final void perform (OPT_IR ir) {
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

    public final String getName () {
      return "Complex Operators";
    }

    public final OPT_CompilerPhase newExecution (OPT_IR ir) {
      return this;
    }

    public final void perform (OPT_IR ir) {
      OPT_ComplexLIR2MIRExpansion.convert(ir);
      // ir now contains well formed MIR.
      ir.IRStage = OPT_IR.MIR;
      ir.MIRInfo = new OPT_MIRInfo(ir);
    }
  }
}
