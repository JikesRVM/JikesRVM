/*
 * (C) Copyright Ian Rogers, The University of Manchester 2003 - 2005
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.OPT_BasicBlock;
import com.ibm.JikesRVM.opt.ir.OPT_Instruction;
import com.ibm.JikesRVM.opt.ir.OPT_Operand;
import com.ibm.JikesRVM.opt.ir.OPT_HeapOperand;
import com.ibm.JikesRVM.opt.ir.OPT_Operators;
import com.ibm.JikesRVM.opt.ir.*;
import com.ibm.JikesRVM.VM;
import java.util.ArrayList;
import java.util.Enumeration;

/**
 * <p>A node in the LST (Loop Structure Tree) with added information
 * on:
 *
 * <ul><li>Whether this is a countable, affine or non-regular loop</li>
 *     <li>The registers being used to hold the loop iterator</li>
 *     <li>The initial loop iterator value</li>
 *     <li>The terminal loop iterator value</li>
 *     <li>The instruction that modifies the iterator</li>
 *     <li>The phi instruction that merges the redefined iterator with its original value</li>
 *     <li>The condition used to test for loop termination</li>
 *     <li>The stride operand</li>
 * </ul>
 *
 * The information is only held on regular loops. The regular loop
 * structure is:
 *
 * <listing>
 * predecessor:
 *   initialLoopIterator = ...;
 * header:
 *   phiLoopIterator = phi (initialLoopIterator, carriedLoopIterator)
 *   ...body1...
 *   carriedLoopIterator = phiLoopIterator iteratorInstr.opcode stride;
 *   ...body2...
 * exit:
 *   if carriedLoopIterator condition terminalIteratorValue goto header
 * successor: 
 * </listing>
 *
 * While loops (and implicitly for loops) aren't handled as they can
 * be transformed to this form by {@link OPT_CFGTransformations}.
 *
 * TODO:
 * <ul><li>More complex iterator instructions (sequences rather than single instructions)</li>
 *     <li>Handle longs, floats and doubles as loop iterators</li>
 *     <li>Consideration of more loop structures</li>
 * </ul>
 * </p>
 * 
 * @see OPT_LSTNode
 *
 * @author Ian Rogers
 */
final class OPT_AnnotatedLSTNode extends OPT_LSTNode implements OPT_Operators{
  // -oO Debug information Oo-
  /**
   * Flag to optionally print verbose debugging messages
   */
  private static final boolean DEBUG=false;

  // -oO Cached values for convenience Oo-
  /**
   * A pointer to the governing IR
   */  
  private OPT_IR ir;

  // -oO Blocks that get set up during the recognition of the loop Oo-
  /**
   * The out of loop block before the header
   */
  OPT_BasicBlock predecessor;
  // N.B. the header is defined in the superclass
  /**
   * The in loop block that either loops or leaves the loop
   */
  OPT_BasicBlock exit;
  /**
   * The out of loop block following the exit block
   */
  OPT_BasicBlock successor;

  // -oO Instructions that get set up during the recognition of the loop Oo-
  /**
   * The if instruction within the exit block
   */
  private OPT_Instruction ifCmpInstr;
  /**
   * The instruction that modifies the iterator
   */
  private OPT_Instruction iteratorInstr;

  // Values that get determined during the recognition of the loop
  /**
   * The the initial iterator that comes into the phi node in the header
   */
  OPT_Operand initialIteratorValue;
  /**
   * The iterator that is used to loop within the exit block
   */
  private OPT_Operand carriedLoopIterator;
  /**
   * The the phi iterator that gets modified by the stride to produce the carried iterator
   */
  private OPT_Operand phiLoopIterator;
  /**
   * The value that ends the loop
   */
  OPT_Operand terminalIteratorValue;
  /**
   * The condition that is used to check for the end of loop
   */
  OPT_ConditionOperand condition;
  /**
   * The stride operand to the iterator instruction
   */
  OPT_Operand strideValue;


  // -oO Interfaces to the rest of the compiler Oo-
  /**
   * Constructor
   *
   * @param ir   The containing IR
   * @param node The node that's being annotated
   */
  OPT_AnnotatedLSTNode(OPT_IR ir, OPT_LSTNode node) {
    // Clone information from non-annotated node
    super(node);
    this.ir = ir;

    // Process inner loops
    Enumeration innerLoops = node.outNodes();
    // Iterate over loops contained within this loop annotating from the inside out
    while (innerLoops.hasMoreElements()) {
      OPT_AnnotatedLSTNode nestedLoop = new OPT_AnnotatedLSTNode(ir, (OPT_LSTNode)innerLoops.nextElement());
      insertOut(nestedLoop);
    }
    // Annotate node
    perform();
  }

  /**
   * Is this a countable loop of the form:
   * <listing>
   * predecessor:
   *   initialLoopIterator = ConstantInitialValue;
   * header:
   *   phiLoopIterator = phi (initialLoopIterator, carriedLoopIterator)
   *   ...body1...
   *   carriedLoopIterator = phiLoopIterator (+|-) ConstantStride;
   *   ...body2...
   * exit:
   *   if carriedLoopIterator condition ConstantTerminalIteratorValue goto header
   * successor: 
   * </listing>
   * ie. lots of constant fields so we can calculate the number of
   * loop iterations (handy for pre-scheduling).
   *
   * @return Whether this a countable loop or not
   */
  boolean isCountableLoop() {
    return
      (initialIteratorValue != null) &&
      (isConstant(initialIteratorValue) == true) &&
      (terminalIteratorValue != null) &&
      (isConstant(terminalIteratorValue) == true) &&
      (strideValue != null) &&
      (isConstant(strideValue) == true) &&
      (iteratorInstr != null) &&
      ((iteratorInstr.operator.opcode == INT_ADD_opcode)||(iteratorInstr.operator.opcode == INT_SUB_opcode));
  }

  /**
   * Is this an affine loop of the form:
   * <listing>
   * predecessor:
   *   initialLoopIterator = ...;
   * header:
   *   phiLoopIterator = phi (initialLoopIterator, carriedLoopIterator)
   *   ...body1...
   *   carriedLoopIterator = phiLoopIterator (+|-) invariantStride;
   *   ...body2...
   * exit:
   *   if carriedLoopIterator condition invariantTerminalIteratorValue goto header
   * successor: 
   * </listing>
   * ie. lots of constant fields so we can calculate the number of
   * loop iterations (handy for pre-scheduling).
   *
   * @return Whether this an affine loop or not
   */
  boolean isAffineLoop() {
    return
      (initialIteratorValue != null) &&
      (isLoopInvariant(initialIteratorValue, loop, header) == true) &&
      (terminalIteratorValue != null) &&
      (isLoopInvariant(terminalIteratorValue, loop, header) == true) &&
      (strideValue != null) &&
      (isLoopInvariant(strideValue, loop, header) == true) &&
      (iteratorInstr != null) &&
      ((iteratorInstr.operator.opcode == INT_ADD_opcode)||(iteratorInstr.operator.opcode == INT_SUB_opcode));
  }

  /**
   * Is this loop a non-regular loop?
   *
   * @return Whether this is a non-regular loop
   */
  boolean isNonRegularLoop() {
    return isAffineLoop() == false;
  }
  /**
   * Is this value modified by the loop?
   *
   * @return whether the value is modified
   */
  boolean isInvariant(OPT_Operand op) {
	 return isLoopInvariant(op, loop, header);
  }
  /**
   * Is this operand related to the iterator of this loop?
   *
   * @param  op Operand to test
   * @return whether related to iterator (initial, phi or carried)
   */
  boolean isRelatedToIterator(OPT_Operand op){
    return isFixedDistanceFromPhiIterator(op);
  }
  /**
	* Is this operand related to the phi iterator of this loop?
	* @param op Operand to test
	* @return whether related to iterator (phi)
	*/
  boolean isPhiLoopIterator(OPT_Operand op) {
	 return op.similar(phiLoopIterator);
  }
  /**
	* Is this operand related to the carried iterator of this loop?
	* @param op Operand to test
	* @return whether related to iterator (carried)	  
	*/
  boolean isCarriedLoopIterator(OPT_Operand op) {
	 return op.similar(carriedLoopIterator);
  }
  /**
   * Is the loop iterator monotonic?
   */
  boolean isMonotonic() {
    return isConstant(strideValue);
  }
  /**
	* Return the stride value for monotonic loops
	*
	* @return the constant stride value
	*/
  int getMonotonicStrideValue() {
	 if (iteratorInstr.operator.opcode == INT_SUB_opcode) {
		return -((OPT_IntConstantOperand)strideValue).value;
	 }
	 else if (iteratorInstr.operator.opcode == INT_ADD_opcode) {
		return ((OPT_IntConstantOperand)strideValue).value;
	 }
	 else {
		throw new Error("Error reading stride value");
	 }
  }
  /**
   * Is the loop iterator a monotonic increasing value
   */
  boolean isMonotonicIncreasing() {
	 if ((isMonotonic() == false) || condition.isGREATER() || condition.isGREATER_EQUAL() || condition.isHIGHER() || condition.isHIGHER_EQUAL()) {
      return false;
    }
    else {
      return getMonotonicStrideValue() > 0;
    }
  }
  /**
   * Is the loop iterator a monotonic decreasing value
   */
  boolean isMonotonicDecreasing() {
	 if ((isMonotonic() == false) || condition.isLESS() || condition.isLESS_EQUAL() || condition.isLOWER() || condition.isLOWER_EQUAL()) {
      return false;
    }
    else {
      return getMonotonicStrideValue() < 0;
    }
  }

  /**
   * Does this basic block appear in the loop?
   */
  boolean contains(OPT_BasicBlock block) {
    return (block.getNumber() < loop.length()) && loop.get(block.getNumber());
  }
  // -oO Utility methods Oo-
  /**
   * Converts the annotated loop to a concise string
   */ 
  public String toString() {
    String ifCmpString = "??";
    if ((ifCmpInstr != null)&&(IfCmp.conforms(ifCmpInstr))) {
      ifCmpString = IfCmp.getCond(ifCmpInstr).toString();
    }
    return ("// pred: " + predecessor + "\n" +
            "loop : " +  initialIteratorValue + ";\n" +
            "head {" + header + "}:\n" +
            "   " + phiLoopIterator + "=phi(" + initialIteratorValue + "," + carriedLoopIterator + ");\n" +
            "   " + carriedLoopIterator + "=" + phiLoopIterator + "+" + strideValue + ";\n" +
            "// blocks: " + loop + "\n" +
            "exit {" + exit + "}:\n" +
            "   if(" + carriedLoopIterator + " " + ifCmpString + " " + terminalIteratorValue + ")\n" +
            "      goto head;\n" +
            "// succ: "+ successor + "\n");
  }
  /**
   * Dump a human readable description of the loop
   */
  void dump() {
    VM.sysWrite ("********* START OF SSA LOOP DUMP in OPT_AnnotatedLSTNode FOR " + ir.method + "\n");
    if (isNonRegularLoop()) {
      VM.sysWrite("Non-regular");
    }
    else if (isAffineLoop()) {
      VM.sysWrite("Affine");
    }
    else if (isCountableLoop()) {
      VM.sysWrite("Countable");
    }
    else {
      VM.sysWrite("INVALID");
    }
    VM.sysWrite(" Loop:\n\tIteratorInstr: " + iteratorInstr.toString() +
                "\n\tIfCmpInstr:" + ifCmpInstr.toString() +
                "\n\tTerminalIteratorValue: " + terminalIteratorValue.toString() +
                "\n\tInitialIteratorValue: " + initialIteratorValue.toString() +
                "\n\tCarriedLoopIterator: " + carriedLoopIterator.toString() +
                "\n\tPhiLoopIterator: " + phiLoopIterator.toString() +
                "\n\tStrideValue: " + strideValue.toString() +
                "\n\tLoop: " + getBasicBlocks().toString() + " / " + loop.toString() +
                "\n");

    OPT_BasicBlockEnumeration loopBlocks = getBasicBlocks();   
    loop_over_basic_blocks:
    while(loopBlocks.hasMoreElements()){
      // The current basic block
      OPT_BasicBlock curLoopBB = loopBlocks.next();
      dumpBlock(curLoopBB);
    }
    VM.sysWrite ("*********   END OF SSA LOOP DUMP in OPT_AnnotatedLSTNode FOR " + ir.method + "\n");
  }

  /**
   * Dump a human readable description of a basic block within the loop
   *
   * @param block The basic block to dump
   */
  void dumpBlock(OPT_BasicBlock block) {
    if (block == header) {
      VM.sysWrite("Header ");
    }
    if (block == exit) {
      VM.sysWrite("Exit ");
    }
    VM.sysWrite("Block #" + block.getNumber() + ":\n");
    // Print the instructions
    OPT_IREnumeration.AllInstructionsEnum instructions = new OPT_IREnumeration.AllInstructionsEnum(ir, block);
    while (instructions.hasMoreElements()) {
      OPT_Instruction instr = instructions.next();
      dumpInstruction(ir, instr);
    }
  }

  /**
   * Dump a human readable description of an instruction within a
   * basic block within the loop
   *
   * @param ir    Containing IR
   * @param instr The instruction to dump
   */
  static void dumpInstruction(OPT_IR ir, OPT_Instruction instr) {
    VM.sysWrite(instructionToString(ir, instr));
  }

  /**
   * Convert instruction to String in of AnnotatedLSTNode format
   *
   * @param ir    Containing IR
   * @param instr The instruction to dump
   */
  static String instructionToString(OPT_IR ir, OPT_Instruction instr) {
    StringBuffer sb = new StringBuffer();
    sb.append(instr.bcIndex + "\t" +
              (instr.isPEI() ? "E" : " ") +
              (instr.isGCPoint() ? "G " : "  "));
    if (instr.operator == LABEL) {
      sb.append("LABEL" + Label.getBlock(instr).block.getNumber());
      if (Label.getBlock(instr).block.getInfrequent()) {
        sb.append(" (Infrequent)");
      }
    }
    else {
      OPT_IREnumeration.AllDefsEnum defs = new OPT_IREnumeration.AllDefsEnum(ir, instr);
      OPT_IREnumeration.AllUsesEnum uses = new OPT_IREnumeration.AllUsesEnum(ir, instr);
      sb.append(instr.operator +
                "\n\t     defs: ");
      while(defs.hasMoreElements()) {
        sb.append (defs.next().toString());
        if (defs.hasMoreElements()) {
          sb.append (", ");
        }
      }
      sb.append("\n\t     uses: ");
      while(uses.hasMoreElements()) {
        sb.append (uses.next().toString());
        if (uses.hasMoreElements()) {
          sb.append (", ");
        }
      }
    }
    sb.append ("\n");
    return sb.toString();
  }

  /**
   * Test whether the operand is constant
   *
   * @param op Operand to determine whether it's constant
   * @return Is the operand constant 
   */
   static boolean isConstant(OPT_Operand op){
    return op instanceof OPT_IntConstantOperand;
  }

  /**
	* Is this operand a fixed distance from the phi iterator?
	*
	* @param op the operand to test
	* @result is it a fixed distance
	*/
  boolean isFixedDistanceFromPhiIterator(OPT_Operand op) {
	 if(op.similar(phiLoopIterator)) {
		return true;
	 }
	 else {
		OPT_Instruction opInstr = definingInstruction(op);
		if ((opInstr.operator.opcode == INT_ADD_opcode)||(opInstr.operator.opcode == INT_SUB_opcode)){
		  OPT_Operand val1= Binary.getVal1(opInstr);
		  OPT_Operand val2= Binary.getVal2(opInstr);
		  return ((val1.isConstant() && isFixedDistanceFromPhiIterator(val2)) ||
					 (val2.isConstant() && isFixedDistanceFromPhiIterator(val1)));
		}
		else {
		  return false;
		}
	 }
  }
  /**
	* Get fixed distance from the phi iterator
	*
	* @param op the operand to test
	* @result the fixed distance
	*/
  int getFixedDistanceFromPhiIterator(OPT_Operand op) {
	 if(op.similar(phiLoopIterator)) {
		return 0;
	 }
	 else {
		OPT_Instruction opInstr = definingInstruction(op);
		if (opInstr.operator.opcode == INT_ADD_opcode) {
		  OPT_Operand val1= Binary.getVal1(opInstr);
		  OPT_Operand val2= Binary.getVal2(opInstr);
		  if (val1.isConstant()) {
			 return val1.asIntConstant().value + getFixedDistanceFromPhiIterator(val2);
		  }
		  else {
			 VM._assert(val2.isConstant());
			 return getFixedDistanceFromPhiIterator(val1) + val2.asIntConstant().value;
		  }
		}
		else if (opInstr.operator.opcode == INT_SUB_opcode){
		  OPT_Operand val1= Binary.getVal1(opInstr);
		  OPT_Operand val2= Binary.getVal2(opInstr);
		  if (val1.isConstant()) {
			 return val1.asIntConstant().value - getFixedDistanceFromPhiIterator(val2);
		  }
		  else {
			 VM._assert(val2.isConstant());
			 return getFixedDistanceFromPhiIterator(val1) - val2.asIntConstant().value;
		  }
		}
	 }
	 throw new Error("Value isn't fixed distance from phi iterator");
  }
  /**
   * Test whether operand value will be invariant in a loop by tracing
   * back earlier definitions.  There is similar code in {@link
   * OPT_LoopUnrolling}.
   *
   * @param op     Operand to determine whether it's invariant
   * @param loop   Loop in which we wish to know the invariance of the operand
	* @param header The loop header for determining if PEIs are invariant
   * @return Whether the operand is invariant or not
   */
  private static boolean isLoopInvariant (OPT_Operand op, OPT_BitVector loop, OPT_BasicBlock header) {
	 boolean result;
    if (op.isConstant()) {
		result = true;
    }
	 else if(op.isRegister()) {
		OPT_Instruction instr = definingInstruction(op);
		// Is the instruction that defined this register in the loop?
		if (OPT_CFGTransformations.inLoop (instr.getBasicBlock(), loop) == false) {
		  // No => the value is invariant in the loop
		  result = true;
		}
		else {
		  // Trace op to where invariant/variant values may be defined
		  switch (instr.operator().format) {
		  case OPT_InstructionFormat.Binary_format:
			 result = (isLoopInvariant(Binary.getVal1(instr), loop, header) &&
						  isLoopInvariant(Binary.getVal2(instr), loop, header));
			 break;
		  case OPT_InstructionFormat.BoundsCheck_format:
			 if(isLoopInvariant(BoundsCheck.getRef(instr), loop, header) &&
				 isLoopInvariant(BoundsCheck.getIndex(instr), loop, header)) {
				// Iterate over instructions before the null check
				OPT_Instruction lastInst;
				if (header == instr.getBasicBlock()) {
				  lastInst = instr;
				}
				else {
				  lastInst = header.lastInstruction();
				}
				result = false;
				OPT_Instruction itrInst;
				for(itrInst = header.firstRealInstruction();
					 itrInst != lastInst;
					 itrInst = itrInst.nextInstructionInCodeOrder()) {				  
				  // Check it would be safe for this nullcheck to before
				  // the loop header without changing the exception
				  // semantics
				  if (BoundsCheck.conforms(itrInst) &&
						BoundsCheck.getRef(itrInst).similar(BoundsCheck.getRef(instr)) &&
						BoundsCheck.getIndex(itrInst).similar(BoundsCheck.getIndex(instr))) {
					 // it's safe if there's already an equivalent null check
					 result = true;
					 break;
				  }
				  else if (itrInst.isAllocation() || itrInst.isDynamicLinkingPoint() ||
							  (itrInst.operator.opcode >= ARCH_INDEPENDENT_END_opcode) ||
							  itrInst.isPEI() || itrInst.isThrow()
							  || itrInst.isImplicitLoad() || itrInst.isImplicitStore() ||
							  OPT_GCP.usesOrDefsPhysicalRegisterOrAddressType(itrInst)) {
					 // it's not safe in these circumstances (see OPT_LICM)
					 if (DEBUG) {
						VM.sysWriteln("null check not invariant: "+ itrInst);
					 }
					 break;
				  }
				}
				if (itrInst == instr) {
				  // did we iterate to the end of the instructions and
				  // find instr
				  result = true;
				}
			 }
			 else {
				result = false;
			 }
			 break;
		  case OPT_InstructionFormat.GuardedBinary_format:
			 result = (isLoopInvariant(GuardedBinary.getVal1(instr), loop, header) &&
						  isLoopInvariant(GuardedBinary.getVal2(instr), loop, header) &&
						  isLoopInvariant(GuardedBinary.getGuard(instr), loop, header));
			 break;
		  case OPT_InstructionFormat.GuardedUnary_format:
			 result = (isLoopInvariant(GuardedUnary.getVal(instr), loop, header) &&
						  isLoopInvariant(GuardedUnary.getGuard(instr), loop, header));
			 break;
		  case OPT_InstructionFormat.Move_format:
			 result = isLoopInvariant(Move.getVal(instr), loop, header);
			 break;
		  case OPT_InstructionFormat.NullCheck_format:
			 if(isLoopInvariant(NullCheck.getRef(instr), loop, header)) {
				// Iterate over instructions before the null check
				OPT_Instruction lastInst;
				if (header == instr.getBasicBlock()) {
				  lastInst = instr;
				}
				else {
				  lastInst = header.lastInstruction();
				}
				result = false;
				OPT_Instruction itrInst;
				for(itrInst = header.firstRealInstruction();
					 itrInst != lastInst;
					 itrInst = itrInst.nextInstructionInCodeOrder()) {				  
				  // Check it would be safe for this nullcheck to before
				  // the loop header without changing the exception
				  // semantics
				  if (NullCheck.conforms(itrInst) &&
						NullCheck.getRef(itrInst).similar(NullCheck.getRef(instr))) {
					 // it's safe if there's already an equivalent null check
					 result = true;
					 break;
				  }
				  else if (itrInst.isAllocation() || itrInst.isDynamicLinkingPoint() ||
							  (itrInst.operator.opcode >= ARCH_INDEPENDENT_END_opcode) ||
							  itrInst.isPEI() || itrInst.isThrow()
							  || itrInst.isImplicitLoad() || itrInst.isImplicitStore() ||
							  OPT_GCP.usesOrDefsPhysicalRegisterOrAddressType(itrInst)) {
					 // it's not safe in these circumstances (see OPT_LICM)
					 if (DEBUG) {
						VM.sysWriteln("null check not invariant: "+ itrInst);
					 }
					 break;
				  }
				}
				if (itrInst == instr) {
				  // did we iterate to the end of the instructions and
				  // find instr
				  result = true;
				}
			 }
			 else {
				result = false;
			 }
			 break;
		  case OPT_InstructionFormat.Unary_format:
			 result = isLoopInvariant(Unary.getVal(instr), loop, header);
			 break;
		  default:
			 // Unknown instruction format so leave
			 result = false;
			 break;
		  }
		}
	 }
    else { // Other operand types
		result = false;
    }
	 if (DEBUG) {
		VM.sysWriteln("isLoopInvariant: " +op + (result ? " true" : " false"));
	 }
	 return result;
  }

  /**
	* Loop invariants may not be accessible before a loop, so generate
	* the instructions so they are
	*
	* @param block to generate instructions into
	* @param op the operand we hope to use before the loop
	*/
  OPT_Operand generateLoopInvariantOperand(OPT_BasicBlock block, OPT_Operand op) {
	 OPT_Instruction instr = definingInstruction(op);
	 if (op.isConstant() || OPT_CFGTransformations.inLoop (instr.getBasicBlock(), loop) == false) {
		// the operand is already invariant
		return op;
	 }
	 else {
		OPT_RegisterOperand result;
		OPT_Instruction opInstr = definingInstruction(op);
		// create result of correct type
		if(ResultCarrier.conforms(opInstr)) {
		  result = ResultCarrier.getResult(opInstr).copyRO();
		  result.setRegister(ir.regpool.getReg(result));
		}
		else {
		  if (VM.VerifyAssertions) VM._assert(GuardResultCarrier.conforms(opInstr));
		  result = GuardResultCarrier.getGuardResult(opInstr).copyRO();
		  result.setRegister(ir.regpool.getReg(result));
		}
		OPT_Instruction resultInstruction;
		OPT_Operator operator = instr.operator;
		switch (operator.format) {
		case OPT_InstructionFormat.Binary_format:
		  resultInstruction = Binary.create(operator,
														result,
														generateLoopInvariantOperand(block, Binary.getVal1(instr)),
														generateLoopInvariantOperand(block, Binary.getVal2(instr)));
		  break;
		case OPT_InstructionFormat.BoundsCheck_format:
		  resultInstruction = BoundsCheck.create(operator,
															  result,
															  generateLoopInvariantOperand(block, BoundsCheck.getRef(instr)),
															  generateLoopInvariantOperand(block, BoundsCheck.getIndex(instr)),
															  generateLoopInvariantOperand(block, BoundsCheck.getGuard(instr)));
		  break;
		case OPT_InstructionFormat.GuardedBinary_format:
		  resultInstruction = GuardedBinary.create(operator,
																 result,
																 generateLoopInvariantOperand(block, GuardedBinary.getVal1(instr)),
																 generateLoopInvariantOperand(block, GuardedBinary.getVal2(instr)),
																 generateLoopInvariantOperand(block, GuardedBinary.getGuard(instr)));
		  break;
		case OPT_InstructionFormat.GuardedUnary_format:
		  resultInstruction = GuardedUnary.create(operator,
																result,
																generateLoopInvariantOperand(block, GuardedUnary.getVal(instr)),
																generateLoopInvariantOperand(block, GuardedUnary.getGuard(instr)));
		  break;
		case OPT_InstructionFormat.Move_format:
		  resultInstruction = Move.create(operator,
													 result,
													 generateLoopInvariantOperand(block, Move.getVal(instr)));
		  break;
		case OPT_InstructionFormat.NullCheck_format:
		  resultInstruction = NullCheck.create(operator,
															result,
															generateLoopInvariantOperand(block, NullCheck.getRef(instr)));
		  break;
		case OPT_InstructionFormat.Unary_format:
		  resultInstruction = Unary.create(operator,
													  result,
													  generateLoopInvariantOperand(block, Unary.getVal(instr)));
		  break;
		default:
		  // Unknown instruction format so leave
		  throw new Error("TODO: generate loop invariant for operator " + operator);
		}
		resultInstruction.copyPosition(instr);
		block.appendInstruction(resultInstruction);
		OPT_DefUse.updateDUForNewInstruction(resultInstruction);
		return result.copyRO();
	 }
  }

  /**
   * Follow the operand's definition filtering out moves
   * This code is taken and modified from an old {@link OPT_LoopUnrolling}
   *
   * @param use Operand to follow
   * @return the first defintion of the instruction which isn't a move
   */
  public static OPT_Operand follow (OPT_Operand use) {
    while (true) {
      // Are we still looking at a register operand?
      if (use.isRegister() == false) {
        // No - we're no longer filtering out moves then
        break;
      }
      // Get definitions of register
      OPT_RegisterOperand rop = use.asRegister();
      OPT_RegisterOperandEnumeration defs = OPT_DefUse.defs (rop.register);
      // Does register have definitions?
      if (defs.hasMoreElements() == false){
        // No - Register musn't be defined in this block
        break;
      }
      // Get the 1st instruction that defines the register
      use = defs.next();
      OPT_Instruction def = use.instruction;
      // Was the instruction that defined this register a move?
      if (Move.conforms (def) == false){
        // No - return the register operand from the defining instruction
        break;
      }
      // Does the register have multiple defintions?
      if (defs.hasMoreElements()){
        // Yes - too complex to follow so let's leave
        break;
      }
      use = Move.getVal(def);
    }
    return use;
  }

  /**
   * Find the instruction that defines an operand.
   * If the operand is a register, return the instruction that defines it, else return the operands instruction
   *
   * @param op The operand we're searching for the definition of
   */
  static OPT_Instruction definingInstruction (OPT_Operand op) {
    OPT_Instruction result = op.instruction;
    // Is operand a register?
    if(op instanceof OPT_RegisterOperand){
      // Yes - check the definitions out
      OPT_RegisterOperandEnumeration defs = OPT_DefUse.defs (((OPT_RegisterOperand) op).register);
      // Does this register have any defintions?
      if (!defs.hasMoreElements()) {
        // No - must have been defined in previous block so just return register
      }
      else {
        // Get the instruction that defines the register
        result = defs.next().instruction;
        // Check to see if there are any more definitions
        if (defs.hasMoreElements()) {
          // Multiple definitions of register, just return register to be safe
          result = op.instruction;
        }
      }
    }
    return result;
  }

  /**
   * Is the a particular block in this loop?
   *
   * @return true => block is in the loop, false => block not in loop
   */
  public boolean isInLoop (OPT_BasicBlock block){
    if (OPT_CFGTransformations.inLoop(block, loop) == true) {
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Return an enumeration of basic blocks corresponding to a depth
   * first traversal of the blocks in the loops graphs
   *
   * @param block block to visit
   * @param bbs enumeration so far
   * @param blocksLeftToVisit blocks left to visit
   * @return Blocks in loop with header first and exit last
   */
  private BBEnum getBasicBlocks(OPT_BasicBlock block, BBEnum bbs, OPT_BitVector blocksLeftToVisit) {
    if(block != exit) {
      bbs.add(block);
    }
    blocksLeftToVisit.clear(block.getNumber());
    OPT_BasicBlockEnumeration successors = block.getNormalOut();
    while (successors.hasMoreElements()) {
      block = successors.next();
      if (blocksLeftToVisit.get(block.getNumber())) {
        getBasicBlocks(block, bbs, blocksLeftToVisit);
      }
    }
    return bbs;
  }

  /**
   * Return an enumeration of basic blocks corresponding to a depth
   * first traversal of the blocks in the loops graphs
   *
   * @return Blocks in loop with header first and exit last
   */
  OPT_BasicBlockEnumeration getBasicBlocks() {
    OPT_BitVector blocksLeftToVisit = new OPT_BitVector(loop);
    BBEnum bbs = getBasicBlocks(header, new BBEnum(), blocksLeftToVisit);
    if (exit != null) {
      // place the exit block at the end of the list if we've recognized one
      bbs.add(exit);
    }
    return bbs;
  }

  /**
   * Check the edges out of a block are within the loop
   *
   * @param block to check
   */
  private void checkOutEdgesAreInLoop(OPT_BasicBlock block) throws NonRegularLoopException {
    // The blocks (copy of) that are branched to from this block
    OPT_BasicBlockEnumeration block_outEdges = block.getOut();
    // Check that the blocks that we branch into are all inside the loop
    loop_over_loop_body_block_out_edges:
    while(block_outEdges.hasMoreElements()){
      OPT_BasicBlock curEdgeBB = block_outEdges.next();
      // Is this block in the loop?
      if((isInLoop(curEdgeBB) == false)&&(block != exit)){
        // Block wasn't in the loop
        throw new NonRegularLoopException("Parallelization giving up: edge out of block in loop to a block outside of the loop, and the block wasn't the loop exit" +
                                          ((block == header) ? " (it was the header block though)" : ""));
      }
    } // end of loop_over_loop_body_block_out_edges
  }

  /**
   * Check the edges into a block are from within the loop
   *
   * @param block to check
   */
  private void checkInEdgesAreInLoop(OPT_BasicBlock block) throws NonRegularLoopException {
    // The blocks (copy of) that branch to this block
    OPT_BasicBlockEnumeration block_inEdges = block.getIn();
    // Check that the blocks that branch into this one are all inside the loop too
    loop_over_loop_body_block_in_edges:
    while(block_inEdges.hasMoreElements()){
      OPT_BasicBlock curEdgeBB = block_inEdges.next();
      // Is this block in the loop?
      if((isInLoop(curEdgeBB) == false)&&(block != header)){
        // Block wasn't in the loop
        throw new NonRegularLoopException("Parallelization giving up: edge into a block in the loop from a block outside of the loop, and the block wasn't the loop header" +
                                          ((block == exit) ? " (it was the exit block though)" : ""));
      }
    } // end of loop_over_loop_body_block_in_edges
  }

  // -oO Perform annotation Oo-
  /**
   * Convert node into annotated format
   */
  private void perform() throws OPT_OptimizingCompilerException {    
    // Check we have a loop
    if (loop == null) {
      return;
    }
    // Process the header first as it's the most likely source of non-regularity
    try {
      processHeader();
      // Get the basic blocks that constitute the loop
      OPT_BasicBlockEnumeration loopBlocks = getBasicBlocks();

      // Loop over all blocks within this loop and calculate iterator.. information
      loop_over_basic_blocks:
      while(loopBlocks.hasMoreElements()) {
        // The current basic block
        OPT_BasicBlock curLoopBB = loopBlocks.next();
        
        // Is this block the loop header?
        if(curLoopBB == header){
          // The header was already processed
        }
        else {
          processLoopBlock(curLoopBB);
        }
      }
    }
    catch (NonRegularLoopException e) {
      if (DEBUG){
        VM.sysWrite(e.summary()+"\n");
      }
      // Make sure this loop looks non-regular
      initialIteratorValue = null;
    }
    if (DEBUG && (isNonRegularLoop() == false)) {
      dump();
    }
  }

  /**
   * Process the loop header basic block
   */
  private void processHeader() throws NonRegularLoopException {
    // The blocks (copy of) that branch to this block
    OPT_BasicBlockEnumeration head_inEdges = header.getIn();
    // Loop over blocks that branch to this one
    loop_over_header_in_edges: 
    while(head_inEdges.hasMoreElements()){
      OPT_BasicBlock curEdgeBB = head_inEdges.next();
      // Is this block in the loop?
      if(isInLoop(curEdgeBB)){
        // Yes - must be the exit block
        if (exit != null){
          // we already have an exit block so loop structure is too
          // complex for us to understand
          throw new NonRegularLoopException("Multiple back edges to the header block making exit block undistinguishable.");
        }
        exit = curEdgeBB;
        processExit();
      }
      else {
        // No - this is an out of loop block going into the header
        if (predecessor != null){
          // we already have a predecessor to the header block, more
          // than 1 is beyond this optimisation at the moment
          throw new NonRegularLoopException("Multiple out of loop edges into the header making predecessor block undistinguishable.");
        }
        predecessor = curEdgeBB;
      }
    } // end of loop_over_header_in_edges
    // If the header isn't the exit block, check it doesn't branch outside of the loop
    if(header != exit) {
      checkOutEdgesAreInLoop(header);
    }
  }

  /**
   * Process the loop exit basic block
   */
  private void processExit() throws NonRegularLoopException {
    // If the exit isn't the header block, check it doesn't have in edges from outside the loop
    if(header != exit) {
      checkInEdgesAreInLoop(exit);
    }
    // Check the exit block leaves the loop
    OPT_BasicBlockEnumeration exitBlock_outEdges = exit.getOut();
    boolean exits = false;
    check_exit_block_exits:
    while(exitBlock_outEdges.hasMoreElements()){
      OPT_BasicBlock curExitBlockOutEdgeBB = exitBlock_outEdges.next();
      if(isInLoop(curExitBlockOutEdgeBB)){
        // An in loop out edge from the exit block
      }
      else {
        // An out of loop edge from the exit block
        exits = true;
        successor = curExitBlockOutEdgeBB;
        if (successor == header) {
          throw new NonRegularLoopException("Unimplemented condition - see OPT_LoopUnrolling.java : 240");
        }
      }
    } // end of check_exit_block_exits
    if (exits == false){
      throw new NonRegularLoopException("Exit block (containing back edge to header) doesn't have an out of loop out edge.");
    }
    else {
      // Get the if instruction used to loop in the exit block
      ifCmpInstr = exit.firstBranchInstruction();
      if (ifCmpInstr == null) {
        throw new NonRegularLoopException("Exit block branch doesn't have a (1st) branching instruction.");
      }
      else if (ifCmpInstr.operator.opcode != INT_IFCMP_opcode){
        throw new NonRegularLoopException("branch is int_ifcmp but " + ifCmpInstr.operator + "\n");
      }
      else {
        // Get the terminal and iterator operations
        carriedLoopIterator = follow(IfCmp.getVal1(ifCmpInstr));
        terminalIteratorValue = follow(IfCmp.getVal2(ifCmpInstr));
        condition = (OPT_ConditionOperand)IfCmp.getCond(ifCmpInstr).copy();
        // Check we have them the right way around and that they do the job we expect
        {
          boolean iteratorInvariant      = isLoopInvariant(carriedLoopIterator, loop, header);
          boolean terminalValueInvariant = isLoopInvariant(terminalIteratorValue, loop, header);
          // Is the iterator loop invariant?
          if (iteratorInvariant){
            // Yes - Is the terminal value loop invariant?
            if (terminalValueInvariant){
              // Yes - both parameters to the condition are invariant
              throw new NonRegularLoopException("Exit block condition values are both invariant (single or infinite loop):\n"
                                                + "Loop = " + loop.toString()
                                                + "\nIterator = " + carriedLoopIterator
                                                + "\nTerminal = " + terminalIteratorValue
                                                );
            }
            else {
              // No - swap values over
              OPT_Operand temp = terminalIteratorValue;
              terminalIteratorValue = carriedLoopIterator;
              carriedLoopIterator = temp;
            }
          }
          else {
            // No - Is the terminal value loop invariant?
            if (terminalValueInvariant){
              // Yes - this is the condition we hoped for
            }
            else {
              // No - both loop values are variant and loop is too complex to analyse
              throw new NonRegularLoopException("Exit block condition values are both variant.");
            }
          }
        }
        // Check target of "if" is the header
        if(Label.getBlock(IfCmp.getTarget(ifCmpInstr).target).block != header) {
          // No - can't optimise loop in this format
          // TODO: swap ifxxx around so that branch is to header and fall-through is exit
          throw new NonRegularLoopException("Target of exit block branch isn't the loop header.");
        }
        // Calculate stride value
        OPT_RegisterOperandEnumeration iteratorDefs = OPT_DefUse.defs (((OPT_RegisterOperand)carriedLoopIterator).register);
        // Loop over definitions of the iterator operand ignoring moves
        while (iteratorDefs.hasMoreElements()) {
         OPT_Operand curDef = follow(iteratorDefs.next());
          // Is this definition within the loop?
          if (isInLoop (curDef.instruction.getBasicBlock())) {
            // Yes - have we already got an iterator instruction
            if ((iteratorInstr == null) || (iteratorInstr == curDef.instruction)) {
              // No - record
              iteratorInstr = curDef.instruction;
            }
            else {
              // Yes - loop too complex again
              throw new NonRegularLoopException("Multiple definitions of the iterator.");
            }
          }
        }
        // Did we find an instruction?
        if (iteratorInstr == null) {
          // No => error
          throw new NonRegularLoopException("No iterator definition found.");
        }
        // Is it an instruction we recognise?
        // TODO: support more iterator instructions
        else if ((iteratorInstr.operator.opcode != INT_ADD_opcode)&&(iteratorInstr.operator.opcode != INT_SUB_opcode)){
          throw new NonRegularLoopException("Unrecognized iterator operator " + iteratorInstr.operator);
        }
        // only carry on further analysis if we think we can understand the loop
        else {
          // Does this iterator instruction use the same register as it defines
          OPT_Operand iteratorUse = follow(Binary.getVal1(iteratorInstr));
          // The iterator should be using a phi node of the initial and generated value
          if (((OPT_RegisterOperand)carriedLoopIterator).similar(iteratorUse) == false){
            // SSA ok so far, read PHI node
            OPT_Instruction phiInstr = iteratorUse.instruction;
            if (Phi.conforms(phiInstr) == false) {
              // We didn't find a PHI instruction
              throw new NonRegularLoopException("Iterator (" + iteratorUse +
                                                ") not using a phi instruction but " + phiInstr);
            }
            // We have the SSA we hoped for - tidy up
            strideValue = follow (Binary.getVal2(iteratorInstr));
            initialIteratorValue = follow(Phi.getValue(phiInstr,0));
            phiLoopIterator = iteratorUse;
            if (initialIteratorValue instanceof OPT_BasicBlockOperand) {
              throw new Error("OPT_BasicBlock mess up!");
            }
            if (initialIteratorValue == iteratorUse) {
              initialIteratorValue = follow(Phi.getValue(phiInstr,1));
            }
            if (initialIteratorValue instanceof OPT_BasicBlockOperand) {
              throw new Error("OPT_BasicBlock mess up!2");
            }
          }
          else {
            // Not in SSA form as iterator modifies an operand
            throw new NonRegularLoopException("Iterator modifies (uses and defines) operand " +
                                              iteratorUse + " and is therefore not in SSA form.");
          }
          // Check the initialIteratorValue was defined outside of (before) the loop header or is constant
          if (isLoopInvariant(initialIteratorValue, loop, header) == false) {
            throw new NonRegularLoopException("Initial iterator not constant or defined outside the loop - " + initialIteratorValue);
          }
          // Check the stride value constant
          else if ((strideValue instanceof OPT_ConstantOperand) == false){
            throw new NonRegularLoopException("Stride not constant - " + strideValue);
          }
        }
      }
    }
  }
  
  /**
   * Process a regular block within the loop
   *
   * @param block The basic block to process
   */
  private void processLoopBlock (OPT_BasicBlock block) throws NonRegularLoopException {       
    checkInEdgesAreInLoop(block);
    checkOutEdgesAreInLoop(block);
  }

  /**
	* Get the carried loop iterator
	*
	* @return carried loop iterator
	*/
  public OPT_Operand getCarriedLoopIterator() {
	 return carriedLoopIterator;
  }

  // -oO Utility classes Oo-
  /**
   * Exception thrown when a non-regular loop is encountered
   */
  private class NonRegularLoopException extends Exception {
    /**
     * Brief description of problem
     */
    private String _summary;
    /**
     * Constructor
     */
    NonRegularLoopException(String s) {      
      super(s);
      _summary = s;
    }
    /**
     * A brief description of the error
     */
    String summary () {
      return _summary;
    }
  }

  /**
   * This class implements {@link OPT_BasicBlockEnumeration}. It is
   * used for iterating over basic blocks in a fashion determined by
   * the order in which basic blocks are added.
   */
  static final class BBEnum implements OPT_BasicBlockEnumeration {
    /**
     * ArrayList holding basic blocks
     */
    private ArrayList blocks;
    /**
     * The current block of the iterator
     */
    private int currentBlock;

    /**
     * Constructor
     */
    public BBEnum(){
      blocks = new ArrayList();
    }

    /**
     * Insert a block to the end of the list
     * @param block to insert
     */
    public void add(OPT_BasicBlock block){
      blocks.add(block);
    }
    /**
     * Is the iterator at the end of the vector
     * @return whether there are more elements in the vector
     */
    public boolean hasMoreElements() {
      return currentBlock < blocks.size(); 
    }
    /**
     * Get the next element from the vector and move the current block along
     * @return next element
     */
    public Object nextElement() {
      Object result = blocks.get(currentBlock);
      currentBlock++;
      return result;
    }
    /**
     * Get the next element from the vector and return without requiring a cast
     * @return next element
     */
    public OPT_BasicBlock next() {
      OPT_BasicBlock result = (OPT_BasicBlock)blocks.get(currentBlock);
      currentBlock++;
      return result;
    }
    /**
     * String representation of the object
     * @return string representing the object
     */
    public String toString() {
      return blocks.toString();
    }
  } 
}
