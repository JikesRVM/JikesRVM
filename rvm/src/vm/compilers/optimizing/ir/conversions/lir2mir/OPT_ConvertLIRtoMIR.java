/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

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

	// Stage 2: Normalize usage of int constants to simplify Stage 3.
	new OPT_OptimizationPlanAtomicElement(new NormalizeConstants()), 

	// Stage 3a: Compute liveness information for DepGraph
        new OPT_OptimizationPlanAtomicElement(new DoLiveness()),

	// Stage 3b: Block by block build DepGraph and do 
	// BURS based instruction selection.
	new OPT_OptimizationPlanAtomicElement(new DoBURS()), 

	// Stage 4: Handle complex operators 
	// (those that expand to multiple basic blocks of MIR).
	new OPT_OptimizationPlanAtomicElement(new ComplexOperators())
	});
  }

  /**
   * Stage 1: Reduce the LIR operator set to a core set of operators.
   */
  private static final class ReduceOperators extends OPT_CompilerPhase
    implements VM_Constants, OPT_Operators, OPT_Constants {

    final String getName () {
      return  "Reduce Operators";
    }

    final OPT_CompilerPhase newExecution (OPT_IR ir) {
      return  this;
    }

    final void perform (OPT_IR ir) {
      for (OPT_Instruction s = ir.firstInstructionInCodeOrder(); s != 
	     null; s = s.nextInstructionInCodeOrder()) {
        switch (s.getOpcode()) {
	case ARRAYLENGTH_opcode:
	  {
	    OPT_Operand address = GuardedUnary.getClearVal(s);
	    // get array_ref+ARRAY_LENGTH_OFFSET into array_length_mem
	    Load.mutate(s, INT_LOAD, GuardedUnary.getClearResult(s), 
			address, I(ARRAY_LENGTH_OFFSET), 
			new OPT_LocationOperand(), 
			GuardedUnary.getClearGuard(s));
	  }
	  break;

	case GET_OBJ_STATUS_opcode:
	  {
	    // TODO: Valid location operand?
	    OPT_Operand address = GuardedUnary.getClearVal(s);
	    Load.mutate(s, INT_LOAD, GuardedUnary.getClearResult(s), 
			address, I(OBJECT_STATUS_OFFSET), null, 
			GuardedUnary.getClearGuard(s));
	  }
	  break;

        case SET_OBJ_STATUS_opcode:
          {
            Store.mutate(s, INT_STORE, GuardedSet.getClearVal(s),
                GuardedSet.getClearRef(s), I(OBJECT_STATUS_OFFSET), null,
                GuardedSet.getClearGuard(s));
          }
          break;

	case GET_OBJ_TIB_opcode:
	  {
	    // TODO: Valid location operand?
	    OPT_Operand address = GuardedUnary.getClearVal(s);
	    Load.mutate(s, INT_LOAD, GuardedUnary.getClearResult(s), 
			address, I(OBJECT_TIB_OFFSET), null, 
			GuardedUnary.getClearGuard(s));
	  }
	  break;

	case GET_OBJ_RAW_opcode:
	  {
	    OPT_Operand address = GuardedUnary.getClearVal(s);
	    Load.mutate(s, INT_LOAD, GuardedUnary.getClearResult(s), 
			address, I(OBJECT_REDIRECT_OFFSET), OPT_LocationOperand.createRedirection(),
			GetField.getClearGuard(s));
	  }
	  break;

	case GET_CLASS_TIB_opcode:
	  {
	    OPT_TypeOperand type = (OPT_TypeOperand)Unary.getVal(s);
	    int offset = type.type.getTibOffset();
	    Load.mutate(s, INT_LOAD, Unary.getClearResult(s), 
			ir.regpool.makeJTOCOp(ir,s), 
			I(offset), new OPT_LocationOperand(offset));
	  }
	  break;

	case GET_TYPE_FROM_TIB_opcode:
	  {
	    // TODO: Valid location operand?
	    Load.mutate(s, INT_LOAD, Unary.getClearResult(s), 
			Unary.getClearVal(s), 
			I(TIB_TYPE_INDEX << 2), null);
	  }
	  break;

	case GET_SUPERCLASS_IDS_FROM_TIB_opcode:
	  {
	    // TODO: Valid location operand?
	    Load.mutate(s, INT_LOAD, Unary.getClearResult(s), 
			Unary.getClearVal(s), 
			I(TIB_SUPERCLASS_IDS_INDEX << 2), null);
	  }
	  break;

	case GET_IMPLEMENTS_TRITS_FROM_TIB_opcode:
	  {
	    // TODO: Valid location operand?
	    Load.mutate(s, INT_LOAD, Unary.getClearResult(s), 
			Unary.getClearVal(s), 
			I(TIB_IMPLEMENTS_TRITS_INDEX << 2), null);
	  }
	  break;

	case GET_ARRAY_ELEMENT_TIB_FROM_TIB_opcode:
	  {
	    // TODO: Valid location operand?
	    Load.mutate(s, INT_LOAD, Unary.getClearResult(s), 
			Unary.getClearVal(s), 
			I(TIB_ARRAY_ELEMENT_TIB_INDEX << 2), null);
	  }
	  break;

	case LONG_DIV_opcode:
	  {
	    CallSpecial.mutate2(s, SYSCALL, 
				GuardedBinary.getClearResult(s), 
				null, 
				new OPT_SysMethodOperand("sysLongDivide"), 
				GuardedBinary.getClearVal1(s), 
				GuardedBinary.getClearVal2(s));
	    OPT_CallingConvention.expandSysCall(s, ir);
	  }
	  break;

	case LONG_REM_opcode:
	  {
	    CallSpecial.mutate2(s, SYSCALL, 
				GuardedBinary.getClearResult(s), 
				null, 
				new OPT_SysMethodOperand("sysLongRemainder"), 
				GuardedBinary.getClearVal1(s), 
				GuardedBinary.getClearVal2(s));
	    OPT_CallingConvention.expandSysCall(s, ir);
	  }
	  break;

        case MATERIALIZE_CONSTANT_opcode:
          {
            OPT_Operand val = Binary.getClearVal2(s);
            if (val instanceof OPT_IntConstantOperand) {
              Move.mutate(s, INT_MOVE, Binary.getClearResult(s), val);
            } else if (val instanceof OPT_LongConstantOperand) {
              Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), val);
            } else if (val instanceof OPT_StringConstantOperand) {
              OPT_StringConstantOperand sc = (OPT_StringConstantOperand)val;
              int offset = sc.value.offset();
              if (offset == 0)
                throw  new OPT_OptimizingCompilerException(
                    "String constant w/o valid JTOC offset");
              offset = offset << 2;
              OPT_LocationOperand loc = new OPT_LocationOperand(offset);
              Load.mutate(s, INT_LOAD, Binary.getClearResult(s), 
			  Binary.getClearVal1(s), 
			  OPT_NormalizeConstants.asImmediateOrReg(I(offset), 
								  s, ir), 
			  loc);
            } else if (val instanceof OPT_DoubleConstantOperand) {
              OPT_DoubleConstantOperand dc = (OPT_DoubleConstantOperand)val;
              int offset = dc.offset;
              if (offset == 0) {
                offset = VM_Statics.findOrCreateDoubleLiteral
		  (VM_Magic.doubleAsLongBits(dc.value));
              }
	      offset = offset << 2;
	      if (VM.BuildForIA32) {
		// leave MATERIALIZE_CONSTANT in IR
		dc.offset = offset;
	      } else {
		OPT_LocationOperand loc = new OPT_LocationOperand(offset);
		Load.mutate(s, DOUBLE_LOAD, 
			    Binary.getClearResult(s), Binary.getClearVal1(s),
			    OPT_NormalizeConstants.asImmediateOrReg(I(offset), 
								    s, ir), 
			    loc);
	      }
            } else if (val instanceof OPT_FloatConstantOperand) {
              OPT_FloatConstantOperand fc = (OPT_FloatConstantOperand)val;
              int offset = fc.offset;
              if (offset == 0) {
                offset = VM_Statics.findOrCreateFloatLiteral
		  (VM_Magic.floatAsIntBits(fc.value));
              }
	      offset = offset << 2;
	      if (VM.BuildForIA32) {
		// leave MATERIALIZE_CONSTANT in IR
		fc.offset = offset;
	      } else {
		OPT_LocationOperand loc = new OPT_LocationOperand(offset);
		Load.mutate(s, FLOAT_LOAD, Binary.getClearResult(s), 
			    Binary.getClearVal1(s),
			    OPT_NormalizeConstants.asImmediateOrReg(I(offset), 
								    s, ir), 
			    loc);
	      }
            } else {
              OPT_OptimizingCompilerException.UNREACHABLE(val.toString());
            }
          }
          break;

	default:
	  break;
        }
      }
    }

    private final OPT_IntConstantOperand I (int i) {
      return  new OPT_IntConstantOperand(i);
    }
  }


  /**
   * Stage 2: Normalize usage of int constants to make less work in Stage 3.
   */
  private static final class NormalizeConstants extends OPT_CompilerPhase {

    final String getName () {
      return  "Normalize Constants";
    }

    final OPT_CompilerPhase newExecution (OPT_IR ir) {
      return  this;
    }

    final void perform (OPT_IR ir) {
      OPT_NormalizeConstants.perform(ir);
    }
  }

  /**
   */
  private static final class DoLiveness extends OPT_CompilerPhase {

    final String getName () {
      return  "Live Handlers";
    }

    final OPT_CompilerPhase newExecution (OPT_IR ir) {
      return  this;
    }

    final void perform (OPT_IR ir) {
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

    final String getName () {
      return  "DepGraph & BURS";
    }

    final OPT_CompilerPhase newExecution (OPT_IR ir) {
      return  this;
    }

    // IR is inconsistent state between DoBURS and ComplexOperators.
    // It isn't verifiable again until after ComplexOperators completes.
    public void verify(OPT_IR ir) { }

    final void perform (OPT_IR ir) {
      OPT_Options options = ir.options;
      // Ensure that spans basic block is set correctly 
      // (required for BURS correctness).
      OPT_DefUse.recomputeSpansBasicBlock(ir);
      OPT_BURS burs = new OPT_BURS(ir);
      
      for (OPT_BasicBlock bb = ir.firstBasicBlockInCodeOrder(); 
	   bb != null; 
	   bb = bb.nextBasicBlockInCodeOrder()) {
        burs.prepareForBlock(bb);
        if (!bb.isEmpty()) {
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
          burs.invoke(dgraph);
          burs.finalizeBlock(bb);
        }
      }
    }
  }

  /**
   * Stage 4: Handle complex operators 
   * (those that expand to multiple basic blocks).
   */
  private static final class ComplexOperators extends OPT_CompilerPhase {

    final String getName () {
      return  "Complex Operators";
    }

    final OPT_CompilerPhase newExecution (OPT_IR ir) {
      return  this;
    }

    final void perform (OPT_IR ir) {
      OPT_ComplexLIR2MIRExpansion.convert(ir);
      // ir now contains well formed MIR.
      ir.IRStage = OPT_IR.MIR;
      ir.MIRInfo = new OPT_MIRInfo(ir);
    }
  }
}
