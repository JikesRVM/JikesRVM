/*
 * (C) Copyright IBM Corp. 2001
 */
import instructionFormats.*;
/**
 * This abstract class provides a set of useful methods for
 * manipulating physical registers for an IR.
 *
 * @author Jong-Deok Choi
 * @author Dave Grove
 * @author Mauricio Serrano
 * @author John Whaley
 * @author Stephen Fink
 */
abstract class OPT_PhysicalRegisterTools extends
OPT_GenericPhysicalRegisterTools{

  /**
   * Return the governing IR.
   */
  abstract OPT_IR getIR();

  /**
   * Create an MIR instruction to move rhs into lhs
   */
  static OPT_Instruction makeMoveInstruction(OPT_RegisterOperand lhs, 
                                             OPT_RegisterOperand rhs) {
    if (rhs.register.isInteger() || rhs.register.isLong()) {
      if (VM.VerifyAssertions) 
	VM.assert(lhs.register.isInteger() || lhs.register.isLong());
      return MIR_Move.create(IA32_MOV, lhs, rhs);
    } else if (rhs.register.isDouble() || rhs.register.isFloat()) {
      if (VM.VerifyAssertions) 
	VM.assert(lhs.register.isDouble() || lhs.register.isFloat());
      return MIR_Move.create(IA32_FMOV, lhs, rhs);
    } else {
      OPT_OptimizingCompilerException.TODO("OPT_PhysicalRegisterTools.makeMoveInstruction");
      return null;
    }
  }
  
  /**
   * Create an instruction to load the nth word of parameter data into a
   * GPR register
   */
  static OPT_Instruction makeLoadSpilledGPRParam(OPT_RegisterOperand lhs, 
                                                 int n) {
    return MIR_Unary.create(LOAD_SPILLED_GPR_PARAM, lhs, I(n));
  }

  /**
   * Create an instruction to load the nth word of parameter data into a
   * FPR register as a Float
   */
  static OPT_Instruction makeLoadSpilledFloatParam(OPT_RegisterOperand lhs, 
                                                   int n) {
    return MIR_Unary.create(LOAD_SPILLED_FLOAT_PARAM, lhs, I(n));
  }
  /**
   * Create an instruction to load the n and n+1st word of parameter data into a
   * FPR register as a Dboule
   */
  static OPT_Instruction makeLoadSpilledDoubleParam(OPT_RegisterOperand lhs, 
                                                    int n) {
    return MIR_Unary.create(LOAD_SPILLED_DOUBLE_PARAM, lhs, I(n));
  }
}
