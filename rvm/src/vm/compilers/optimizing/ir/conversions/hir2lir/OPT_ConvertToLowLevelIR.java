/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Converts all remaining instructions with HIR-only operators into 
 * an equivalent sequence of LIR operators.
 *
 * @author John Whaley
 * @author Mauricio J. Serrano
 * @author Jong-Deok Choi
 * @author Dave Grove
 * @author Stephen Fink
 * @author Igor Pechtchanski
 * @modified Peter F. Sweeney
 */
abstract class OPT_ConvertToLowLevelIR extends OPT_IRTools
  implements OPT_Operators, VM_Constants, OPT_Constants {
  // We have slightly different ideas of what the LIR should look like
  // for IA32 and PowerPC.  The main difference is that for IA32 
  // instead of bending over backwards in BURS to rediscover array loads, 
  // (where we can use base + index*scale addressing modes), we'll leave
  // array loads in the LIR.
  static final boolean LOWER_ARRAY_ACCESS = VM.BuildForPowerPC;

  /**
   * Converts the given HIR to LIR.
   *
   * @param ir IR to convert
   */
  static void convert (OPT_IR ir, OPT_Options options) {
    for (OPT_Instruction s = ir.firstInstructionInCodeOrder(); 
	 s != null; 
	 s = s.nextInstructionInCodeOrder()) {

      switch (s.getOpcode()) {
      case GETSTATIC_opcode:case GETSTATIC_UNRESOLVED_opcode:
	{
	  OPT_LocationOperand loc = GetStatic.getClearLocation(s);
	  VM_Field field = loc.field;
	  OPT_RegisterOperand result = GetStatic.getClearResult(s);
	  OPT_Operand address = ir.regpool.makeJTOCOp(ir,s);
	  OPT_Operand offset;
	  if (s.operator() == GETSTATIC_UNRESOLVED) {
	    offset = resolve(loc, s, ir, false);
	  } else {
	    offset = I(field.getOffset());
	  }
	  Load.mutate(s, OPT_IRTools.getLoadOp(field), result, 
		      address, offset, loc);
	}
      break;

      case PUTSTATIC_opcode:case PUTSTATIC_UNRESOLVED_opcode:
	{
	  OPT_LocationOperand loc = PutStatic.getClearLocation(s);
	  VM_Field field = loc.field;
	  OPT_Operand value = PutStatic.getClearValue(s);
	  OPT_Operand address = ir.regpool.makeJTOCOp(ir,s);
	  OPT_Operand offset;
	  if (s.operator() == PUTSTATIC_UNRESOLVED) {
	    offset = resolve(loc, s, ir, false);
	  } else {
	    offset = I(field.getOffset());
	  }
	  Store.mutate(s, OPT_IRTools.getStoreOp(field), value, 
		       address, offset, loc);
	}
      break;

      case PUTFIELD_opcode:case PUTFIELD_UNRESOLVED_opcode:
	{
	  OPT_LocationOperand loc = PutField.getClearLocation(s);
	  VM_Field field = loc.field;
	  OPT_Operand value = PutField.getClearValue(s);
	  OPT_Operand address = PutField.getClearRef(s);
	  OPT_Operand offset;
	  if (s.operator() == PUTFIELD_UNRESOLVED) {
	    // when executing this code, the object is non-null and therefore
	    // the object MUST exist so the offset MUST be valid.
	    // TODO: However as a short-term fix for dynamic linking with
	    //       dummy references we pretend the offset might actually
	    //       be invalid to give us an easy hook to resolve the 
	    //       reference and find the real field declaration.
	    offset = resolve(loc, s, ir, false);
	  } else {
	    offset = I(field.getOffset());
	  }
	  Store.mutate(s, OPT_IRTools.getStoreOp(field), value, 
		       address, offset, loc, PutField.getClearGuard(s));
	}
      break;

      case GETFIELD_opcode:case GETFIELD_UNRESOLVED_opcode:
	{
	  OPT_LocationOperand loc = GetField.getClearLocation(s);
	  VM_Field field = loc.field;
	  OPT_RegisterOperand result = GetField.getClearResult(s);
	  OPT_Operand address = GetField.getClearRef(s);
	  OPT_Operand offset;
	  if (s.operator() == GETFIELD_UNRESOLVED) {
	    // when executing this code, the object is non-null and therefore
	    // the object MUST exist so the offset MUST be valid.
	    // TODO: However as a short-term fix for dynamic linking with
	    //       dummy references we pretend the offset might actually
	    //       be invalid to give us an easy hook to resolve the 
	    //       reference and find the real field declaration.
	    offset = resolve(loc, s, ir, false);
	  } else {
	    offset = I(field.getOffset());
	  }
	  Load.mutate(s, OPT_IRTools.getLoadOp(field), result, 
		      address, offset, loc, GetField.getClearGuard(s));
	}
      break;

      case INT_ALOAD_opcode:
	doArrayLoad(s, ir, INT_LOAD, 2);
	break;

      case LONG_ALOAD_opcode:
	doArrayLoad(s, ir, LONG_LOAD, 3);
	break;

      case FLOAT_ALOAD_opcode:
	doArrayLoad(s, ir, FLOAT_LOAD, 2);
	break;

      case DOUBLE_ALOAD_opcode:
	doArrayLoad(s, ir, DOUBLE_LOAD, 3);
	break;

      case REF_ALOAD_opcode:
	doArrayLoad(s, ir, REF_LOAD, 2);
	break;

      case BYTE_ALOAD_opcode:
	doArrayLoad(s, ir, BYTE_LOAD, 0);
	break;

      case UBYTE_ALOAD_opcode:
	doArrayLoad(s, ir, UBYTE_LOAD, 0);
	break;

      case USHORT_ALOAD_opcode:
	doArrayLoad(s, ir, USHORT_LOAD, 1);
	break;

      case SHORT_ALOAD_opcode:
	doArrayLoad(s, ir, SHORT_LOAD, 1);
	break;

      case INT_ASTORE_opcode:
	doArrayStore(s, ir, INT_STORE, 2);
	break;

      case LONG_ASTORE_opcode:
	doArrayStore(s, ir, LONG_STORE, 3);
	break;

      case FLOAT_ASTORE_opcode:
	doArrayStore(s, ir, FLOAT_STORE, 2);
	break;

      case DOUBLE_ASTORE_opcode:
	doArrayStore(s, ir, DOUBLE_STORE, 3);
	break;

      case REF_ASTORE_opcode:
	doArrayStore(s, ir, REF_STORE, 2);
	break;

      case BYTE_ASTORE_opcode:
	doArrayStore(s, ir, BYTE_STORE, 0);
	break;

      case SHORT_ASTORE_opcode:
	doArrayStore(s, ir, SHORT_STORE, 1);
	break;

	// Calls
      case CALL_opcode:
	s = call(s, ir);
	break;

      case SYSCALL_opcode:
	OPT_CallingConvention.expandSysCall(s, ir);
	break;

      case TABLESWITCH_opcode:
	s = tableswitch(s, ir);
	break;

      case LOOKUPSWITCH_opcode:
	s = lookup(s, ir);
	break;

      case OBJARRAY_STORE_CHECK_opcode:
	s = OPT_DynamicTypeCheckExpansion.arrayStoreCheck(s, ir);
	break;

      case CHECKCAST_opcode:
	s = OPT_DynamicTypeCheckExpansion.checkcast(s, ir);
	break;

      case CHECKCAST_NOTNULL_opcode:
	s = OPT_DynamicTypeCheckExpansion.checkcastNotNull(s, ir);
	break;

      case CHECKCAST_INTERFACE_NOTNULL_opcode:
	s = OPT_DynamicTypeCheckExpansion.checkcastInterfaceNotNull(s, ir);
	break;

      case IG_CLASS_TEST_opcode:
	{
	  IfCmp.mutate(s, INT_IFCMP, null, 
		       getTIB(s, ir, 
			      InlineGuard.getClearValue(s), 
			      InlineGuard.getClearGuard(s)), 
		       getTIB(s, ir, InlineGuard.getGoal(s).asType()), 
		       OPT_ConditionOperand.NOT_EQUAL(), 
		       InlineGuard.getClearTarget(s),
		       InlineGuard.getClearBranchProfile(s));
	  break;
	}

      case IG_METHOD_TEST_opcode:
	{
	  OPT_MethodOperand methOp = InlineGuard.getClearGoal(s).asMethod();
	  OPT_RegisterOperand t1 = 
	    getTIB(s, ir, 
		   InlineGuard.getClearValue(s), 
		   InlineGuard.getClearGuard(s));
	  OPT_RegisterOperand t2 = 
	    getTIB(s, ir, methOp.method.getDeclaringClass());
	  IfCmp.mutate(s, INT_IFCMP, null, 
		       getInstanceMethod(s, ir, t1, methOp), 
		       getInstanceMethod(s, ir, t2, methOp), 
		       OPT_ConditionOperand.NOT_EQUAL(), 
		       InlineGuard.getClearTarget(s),
		       InlineGuard.getClearBranchProfile(s));
	  break;
	}
	
      case INSTANCEOF_opcode:
	s = OPT_DynamicTypeCheckExpansion.instanceOf(s, ir);
	break;

      case INSTANCEOF_NOTNULL_opcode:
	s = OPT_DynamicTypeCheckExpansion.instanceOfNotNull(s, ir);
	break;

      case INT_ZERO_CHECK_opcode:
	{
	  TrapIf.mutate(s, TRAP_IF, 
			ZeroCheck.getClearGuardResult(s),
			ZeroCheck.getClearValue(s), I(0), 
			OPT_ConditionOperand.EQUAL(), 
			OPT_TrapCodeOperand.DivByZero());
	}
	break;

      case LONG_ZERO_CHECK_opcode:
	{
	  TrapIf.mutate(s, TRAP_IF, 
			ZeroCheck.getClearGuardResult(s),
			ZeroCheck.getClearValue(s), I(0), 
			OPT_ConditionOperand.EQUAL(), 
			OPT_TrapCodeOperand.DivByZero());
	}
	break;

      case BOUNDS_CHECK_opcode:
	{
	  // get array_length from array_ref
	  OPT_RegisterOperand array_length = 
	    InsertGuardedUnary(s, ir, ARRAYLENGTH, VM_Type.IntType, 
			       BoundsCheck.getClearRef(s), 
			       BoundsCheck.getClearGuard(s));
	  //  In UN-signed comparison, a negative index will look like a very
	  //  large positive number, greater than array length.
	  //  Thus length LLT index is false iff 0 <= index <= length
	  TrapIf.mutate(s, TRAP_IF, 
			BoundsCheck.getClearGuardResult(s),
			array_length.copyD2U(), 
			BoundsCheck.getClearIndex(s), 
			OPT_ConditionOperand.LOWER_EQUAL(), 
			OPT_TrapCodeOperand.ArrayBounds());
	}
	break;

      case GET_CLASS_OBJECT_opcode:
	{
	  OPT_RegisterOperand TIB = 
	    getTIB(s, ir, (OPT_TypeOperand)Unary.getClearVal(s));
	  OPT_RegisterOperand type = 
	    ir.regpool.makeTemp(OPT_ClassLoaderProxy.VM_Type_type);
	  s.insertBefore(Unary.create(GET_TYPE_FROM_TIB, type, TIB));
	  // Get the java.lang.Class object from the VM_Type object
	  // TODO: Valid location operand?
	  Load.mutate(s, REF_LOAD, Unary.getClearResult(s), type.copyD2U(), 
		      I(VM_Entrypoints.classForTypeField.getOffset()), null);
	}
	break;
      default:
	break;
      }
    }
  }


  /**
   * Expand a tableswitch.
   * @param s the instruction to expand
   * @param ir the containing IR
   * @return the last OPT_Instruction in the generated LIR sequence.
   */
  static OPT_Instruction tableswitch (OPT_Instruction s, OPT_IR ir) {
    

    OPT_Instruction s2;
    int lowLimit = TableSwitch.getLow(s).value;
    int highLimit = TableSwitch.getHigh(s).value;
    int number = highLimit - lowLimit + 1;
    if (VM.VerifyAssertions)
      VM.assert(number > 0);    // also checks that there are < 2^31 targets
    OPT_Operand val = TableSwitch.getClearValue(s);
    OPT_BranchOperand defaultLabel = TableSwitch.getClearDefault(s);
    if (number < 4) {           // convert into a lookupswitch
      OPT_Instruction l = LookupSwitch.create(LOOKUPSWITCH, val, null, 
					      null, defaultLabel, 
					      TableSwitch.getClearDefaultBranchProfile(s),
					      number*3);
      for (int i = 0; i < number; i++) {
        LookupSwitch.setMatch(l, i, I(lowLimit + i));
        LookupSwitch.setTarget(l, i, TableSwitch.getClearTarget(s, i));
        LookupSwitch.setBranchProfile(l, i, 
				      TableSwitch.getClearBranchProfile(s,i));
      }
      s.insertFront(l);
      return  s.remove();
    }
    OPT_RegisterOperand reg = val.asRegister();
    OPT_BasicBlock BB1 = s.getBasicBlock();
    OPT_BasicBlock BB2 = BB1.splitNodeAt(s, ir);
    OPT_BasicBlock defaultBB = defaultLabel.target.getBasicBlock();

    /******* First basic block */
    OPT_RegisterOperand t;
    int last;
    if (lowLimit != 0) {
      t = InsertBinary(s, ir, INT_ADD, VM_Type.IntType, reg, I(-lowLimit));
    } else {
      t = reg.copyU2U();
    }
    s.replace(IfCmp.create(INT_IFCMP, null, t, I(highLimit - lowLimit),
			   OPT_ConditionOperand.HIGHER(), 
			   defaultLabel,TableSwitch.getClearDefaultBranchProfile(s)));

    /********** second Basic Block ******/
    // Insert silly looking move of t to new temporary to 
    // accomodate IA32 definition of LowTableSwitch 
    // where Index is a DU operand.
    OPT_RegisterOperand temp = ir.regpool.makeTempInt();
    BB2.appendInstruction(Move.create(INT_MOVE, temp, t.copyD2U()));
    s2 = LowTableSwitch.create(LOWTABLESWITCH, temp.copyD2U(), number*2);
    boolean containsDefault = false;
    for (int i = 0; i < number; i++) {
      OPT_BranchOperand b = TableSwitch.getClearTarget(s, i);
      LowTableSwitch.setTarget(s2, i, b);
      LowTableSwitch.setBranchProfile(s2, i, 
				      TableSwitch.getClearBranchProfile(s,i));
      if (b.target == defaultLabel.target)
        containsDefault = true;
    }
    // Fixup the CFG and code order.
    BB1.insertOut(BB2);
    BB1.insertOut(defaultBB);
    ir.cfg.linkInCodeOrder(BB1, BB2);
    if (!containsDefault)
      BB2.deleteOut(defaultBB);
    // Simplify a fringe case...
    // if all targets of the LOWTABLESWITCH are the same,
    // then just use a GOTO instead of the LOWTABLESWITCH/COMPUTED_GOTO pair.
    // This actually happens (very occasionally), and is easy to test for.
    if (BB2.getNumberOfNormalOut() == 1) {
      BB2.appendInstruction(Goto.create(GOTO, 
					LowTableSwitch.getTarget(s2, 0)));
    } else {
      BB2.appendInstruction(s2);
    }
    // continue at next BB
    s = BB2.end;

    return  s;
  }

  /**
   * Expand a lookupswitch.
   * @param s the instruction to expand
   * @param ir the containing IR
   * @return the next OPT_Instruction after the generated LIR sequence.
   */
  static OPT_Instruction lookup (OPT_Instruction switchInstr, OPT_IR ir) {
    OPT_Instruction bbend = switchInstr.nextInstructionInCodeOrder();
    OPT_BasicBlock thisBB = bbend.getBasicBlock();
    OPT_BasicBlock nextBB = thisBB.nextBasicBlockInCodeOrder();
    // Blow away the old Normal ControlFlowGraph edges to prepare for new links
    thisBB.deleteNormalOut();
    switchInstr.remove();
    OPT_BranchOperand defTarget = LookupSwitch.getClearDefault(switchInstr);
    OPT_BasicBlock defaultBB = defTarget.target.getBasicBlock();
    int high = LookupSwitch.getNumberOfTargets(switchInstr) - 1;
    if (high < 0) {
      // no cases in switch; just jump to defaultBB
      thisBB.appendInstruction(Goto.create(GOTO, defTarget));
      thisBB.insertOut(defaultBB);
    } else {
      OPT_RegisterOperand reg = 
	LookupSwitch.getValue(switchInstr).asRegister();

      // If you're not already at the end of the code order
      if (nextBB != null) 
	ir.cfg.breakCodeOrder(thisBB, nextBB);
      // generate the binary search tree into thisBB
      OPT_BasicBlock lastNewBB = 
	_lookupswitchHelper(switchInstr, reg, defaultBB, ir, thisBB, 0, 
			    high, Integer.MIN_VALUE, Integer.MAX_VALUE);
      if (nextBB != null)
	ir.cfg.linkInCodeOrder(lastNewBB, nextBB);
    }

    // skip all the instrs just inserted by _lookupswitchHelper
    if (nextBB != null)
      return  nextBB.firstInstruction();
    else
      return thisBB.lastInstruction();
  }

  /**
   * Helper function to generate the binary search tree for 
   * a lookupswitch bytecode
   *
   * @param switchInstr the lookupswitch instruction
   * @param defaultBB the basic block of the default case
   * @param ir the ir object
   * @param curBlock the basic block to insert instructions into
   * @param reg the RegisterOperand that contains the valued being switched on
   * @param low the low index of cases (operands of switchInstr)
   * @param high the high index of cases (operands of switchInstr)
   * @param min 
   * @param max 
   * @return the last basic block created
   */
  private static OPT_BasicBlock _lookupswitchHelper(OPT_Instruction switchInstr, 
						    OPT_RegisterOperand reg, 
						    OPT_BasicBlock defaultBB, 
						    OPT_IR ir, 
						    OPT_BasicBlock curBlock, 
						    int low, 
						    int high, 
						    int min, 
						    int max) {
    if (VM.VerifyAssertions)
      VM.assert(low <= high, "broken control logic in _lookupswitchHelper");

    int middle = (low + high) >> 1;             // find middle

    // The following are used below to store the computed branch
    // probabilities for the branches that are created to implement
    // the binary search.  Used only if basic block frequencies available
    double lessProb = 0.0;
    double greaterProb = 0.0;
    double equalProb = 0.0;
    double sum=0.0;

    // If basic block counts are being used, take the time to compute
    // the branch probabilities of each branch along the binary search
    // tree.
    if (ir.basicBlockFrequenciesAvailable()) {
      // Sum the probabilities for all targets < middle
      for (int i=low; i < middle; i++) {
	lessProb += LookupSwitch.getBranchProfile(switchInstr,i).takenProbability;
      }

      // Sum the probabilities for all targets > middle
      for (int i=middle+1; i <= high; i++) {
	greaterProb += LookupSwitch.getBranchProfile(switchInstr,i).takenProbability;
      }
      equalProb = 
	LookupSwitch.getBranchProfile(switchInstr,middle).takenProbability;

      // The default case is a bit of a kludge.  We know the total
      // probability of executing the default case, but we have no
      // idea which paths are taken to get there.  For now, we'll
      // assume that all paths that went to default were because the
      // value was less than the smallest switch value.  This ensures
      // that all basic block appearing in the switch will have the
      // correct weights (but the blocks in the binary switch
      // generated may not).
      if (low == 0) 
	lessProb += 
	  LookupSwitch.getDefaultBranchProfile(switchInstr).takenProbability;

      // Now normalize them so they are relative to the sum of the
      // branches being considered in this piece of the subtree
      sum = lessProb + equalProb + greaterProb;
      if (sum > 0) {  // check for divide by zero
	lessProb /= sum;
	equalProb /= sum;
	greaterProb /= sum;
      }
    }

    OPT_IntConstantOperand val = 
      LookupSwitch.getClearMatch(switchInstr, middle);
    int value = val.value;
    OPT_BasicBlock greaterBlock = 
      middle == high ? defaultBB : curBlock.createSubBlock(0, ir);
    OPT_BasicBlock lesserBlock = 
      low == middle ? defaultBB : curBlock.createSubBlock(0, ir);
    // Generate this level of tests
    OPT_BranchOperand branch = 
      LookupSwitch.getClearTarget(switchInstr, middle);
    OPT_BasicBlock branchBB = branch.target.getBasicBlock();
    curBlock.insertOut(branchBB);
    if (low != high) {
      if (value == min) {
        curBlock.appendInstruction(IfCmp.create(INT_IFCMP, null, 
						reg.copy(), val,
						OPT_ConditionOperand.EQUAL(), 
						branchBB.makeJumpTarget(),
						new OPT_BranchProfileOperand(equalProb)));
      } else {
	
	// To compute the probability of the second compare, the first
	// probability must be removed since the second branch is
	// considered only if the first fails.
	double secondIfProb = 0.0;
	sum = equalProb + greaterProb;
	if (sum > 0) // if divide by zero, leave as is
	  secondIfProb = equalProb/sum;

        curBlock.appendInstruction(IfCmp2.create(INT_IFCMP2, null, 
						 reg.copy(), val,
						 OPT_ConditionOperand.LESS(), 
						 lesserBlock.makeJumpTarget(),
						 new OPT_BranchProfileOperand(lessProb),
						 OPT_ConditionOperand.EQUAL(), 
						 branchBB.makeJumpTarget(),
						 new OPT_BranchProfileOperand(secondIfProb)));
        curBlock.insertOut(lesserBlock);
      }
    } else {      // Base case: middle was the only case left to consider
      if (min == max) {
        curBlock.appendInstruction(Goto.create(GOTO, branch));
        curBlock.insertOut(branchBB);
      } else {
        curBlock.appendInstruction(IfCmp.create(INT_IFCMP, null,
						reg.copy(), val, 
						OPT_ConditionOperand.EQUAL(), 
						branchBB.makeJumpTarget(),
						new OPT_BranchProfileOperand(equalProb)));
        OPT_BasicBlock newBlock = curBlock.createSubBlock(0, ir);
        curBlock.insertOut(newBlock);
        ir.cfg.linkInCodeOrder(curBlock, newBlock);
        curBlock = newBlock;
        curBlock.appendInstruction(defaultBB.makeGOTO());
        curBlock.insertOut(defaultBB);
      }
    }
    // Generate sublevels as needed and splice together instr & bblist
    if (middle < high) {
      curBlock.insertOut(greaterBlock);
      ir.cfg.linkInCodeOrder(curBlock, greaterBlock);
      curBlock = _lookupswitchHelper(switchInstr, reg, defaultBB, ir, 
				     greaterBlock, middle + 1, high, 
				     value + 1, max);
    }
    if (low < middle) {
      ir.cfg.linkInCodeOrder(curBlock, lesserBlock);
      curBlock = _lookupswitchHelper(switchInstr, reg, defaultBB, ir, 
				     lesserBlock, low, middle - 1, min, 
				     value - 1);
    }
    return  curBlock;
  }

  /**
   * Expand an array load.
   * @param s the instruction to expand
   * @param ir the containing IR
   * @param op the load operator to use
   * @param logwidth the log base 2 of the element type's size 
   */
  static void doArrayLoad (OPT_Instruction s, OPT_IR ir, 
			   OPT_Operator op, 
			   int logwidth) {
    if (LOWER_ARRAY_ACCESS) {
      OPT_RegisterOperand result = ALoad.getClearResult(s);
      OPT_Operand array = ALoad.getClearArray(s);
      OPT_Operand offset = ALoad.getClearIndex(s);
      OPT_LocationOperand loc = ALoad.getClearLocation(s);
      if (logwidth != 0) {
        if (offset instanceof OPT_IntConstantOperand)  // constant propagation
          offset = I(((OPT_IntConstantOperand)offset).value << logwidth); 
        else 
          offset = InsertBinary(s, ir, INT_SHL, VM_Type.IntType, offset, 
				I(logwidth));
      }
      Load.mutate(s, op, result, array, offset, loc, ALoad.getClearGuard(s));
    }
  }

  /**
   * Expand an array store.
   * @param s the instruction to expand
   * @param ir the containing IR
   * @param op the store operator to use
   * @param logwidth the log base 2 of the element type's size 
   */
  static void doArrayStore (OPT_Instruction s, OPT_IR ir, 
			    OPT_Operator op, 
			    int logwidth) {
    if (LOWER_ARRAY_ACCESS) {
      OPT_Operand value = AStore.getClearValue(s);
      OPT_Operand array = AStore.getClearArray(s);
      OPT_Operand offset = AStore.getClearIndex(s);
      OPT_LocationOperand loc = AStore.getClearLocation(s);
      if (logwidth != 0) {
        if (offset instanceof OPT_IntConstantOperand) // constant propagation
          offset = I(((OPT_IntConstantOperand)offset).value << logwidth); 
        else 
          offset = InsertBinary(s, ir, INT_SHL, VM_Type.IntType, offset, 
				I(logwidth));
      }
      Store.mutate(s, op, value, array, offset, loc, AStore.getClearGuard(s));
    }
  }


  /**
   * Expand a call instruction
   * @param s the instruction to expand
   * @param ir the containing IR
   * @return the last instruction in the expansion
   */
  static OPT_Instruction call (OPT_Instruction s, OPT_IR ir) {
    OPT_MethodOperand methOp = Call.getMethod(s);
    boolean unresolved = false;

    if (methOp != null && methOp.unresolved) {
      unresolved = true;
      if (VM.VerifyAssertions)
	VM.assert(Call.getAddress(s) == null, 
		  "Unresolved call with InstrAddr?");
      OPT_RegisterOperand instrAddr = 
	ir.regpool.makeTemp(OPT_ClassLoaderProxy.InstructionArrayType);
      OPT_Operand offset;
      OPT_LocationOperand loc = new OPT_LocationOperand(methOp);
      switch (methOp.type) {
      case OPT_MethodOperand.SPECIAL:
	// See the comments in VM_OptLinker.java.
	// Only a subset of the functionality is actually required.
	{
	  // the <init> methods can't possibly be a dummy ref, so 
	  // it should be safe to use the unguarded references.
	  offset = resolve(methOp, s, ir, true);
	  OPT_Instruction ia_load = Load.create(REF_LOAD, instrAddr, 
						ir.regpool.makeJTOCOp(ir,s), 
						offset, loc);
	  s.insertBefore(ia_load);
	}
	break;
      case OPT_MethodOperand.STATIC:
	{
	  offset = resolve(methOp, s, ir, false);
	  OPT_Instruction ia_load = Load.create(REF_LOAD, instrAddr, 
						ir.regpool.makeJTOCOp(ir,s), 
						offset, loc);
	  s.insertBefore(ia_load);
	}
	break;
      case OPT_MethodOperand.VIRTUAL:
	{
	  // NOTE: Because BC2IR is (incorrectly) inserting 
	  // a nullcheck before all invokevirtuals, we know that the
	  // receiver is non-null, therefore an object allocation has 
	  // occured that forced any necessary classloading, 
	  // therefore the offset must be valid.  
	  // BUT, this does not comply with the JVM spec.  
	  // BC2IR should not be inserting the nullcheck for an unresolved
	  // invokevirtual because we are required to throw 
	  // classloading/linking exceptions even if the receiver is null.  
	  // But, for now we ignore this silly aspect of the spec.
	  // (see def of invokevirtual in JVM spec)
	  // 
	  // TODO: However as a short-term fix for dynamic linking with
	  //       dummy references we pretend the offset might actually
	  //       be invalid to give us an easy hook to resolve the 
	  //       reference and find the real field declaration.
	  offset = resolve(methOp, s, ir, false);
	  OPT_Instruction ia_load = 
	    Load.create(REF_LOAD, instrAddr, 
			getTIB(s, ir, 
			       Call.getParam(s, 0).copy(),
			       Call.getGuard(s).copy()), 
			offset, loc);
	  s.insertBefore(ia_load);
	}
	break;
      }
      // it will be resolved by the time the call is executed.
      methOp.unresolved = false; 
      Call.setAddress(s, instrAddr.copyD2U());
    }
    s = _callHelper(s, ir);
    return  s;
  }

  /**
   * Helper method for call expansion.
   * @param v the call instruction
   * @param ir the containing IR
   * @return the last expanded instruction
   */
  static OPT_Instruction _callHelper (OPT_Instruction v, OPT_IR ir) {
    OPT_SpecializedMethod spMethod;
    int smid;
    if (v.operator() == CALL)
    if (Call.getAddress(v) instanceof OPT_RegisterOperand) {
      // indirect calls do not need any extra processing
      // NOTE: The code for CALL in the main loop handles unresolved calls by
      // inserting the code prefix necessary to turn them into indirect calls.
      return  v;
    }
    OPT_MethodOperand methOp = Call.getMethod(v);
    VM_Method method = methOp.method;

    /* RRB 100500 */
    // generate direct call to specialized method if the method operand
    // has been marked as a specialized call.
    if (VM.runningVM) {
      spMethod = methOp.spMethod;
      if (spMethod != null) {
        smid = spMethod.getSpecializedMethodIndex();
        Call.setAddress(v, getSpecialMethod(v, ir, smid));
        return  v;
      }
    }
    switch (methOp.type) {
    case OPT_MethodOperand.STATIC:
      {
	if (method == ir.method) {            // RECURSION
	  Call.setAddress(v, new OPT_BranchOperand(ir.firstInstructionInCodeOrder()));
	} else if (method != null) {
	  Call.setAddress(v, getStaticMethod(v, ir, methOp));
	} else {                // this is used by some synthetic Java methods
	  Call.setAddress(v, 
			  InsertLoadOffsetJTOC(v, ir, REF_LOAD, 
					       OPT_ClassLoaderProxy.InstructionArrayType, 
					       methOp.offset));
	}
      }
      break;
    case OPT_MethodOperand.SPECIAL:
      {
	VM_Method target = VM_Class.findSpecialMethod(method);
	methOp.method = target;
	if (target.isObjectInitializer() || target.isStatic()) {
	  // invoke via method's jtoc slot
	  OPT_RegisterOperand meth = getStaticMethod(v, ir, target);
	  Call.setAddress(v, meth);
	} else {
	  if (target == ir.method) {          // RECURSION
	    Call.setAddress(v, new OPT_BranchOperand(ir.firstInstructionInCodeOrder()));
	  } else {
	    // invoke via class's tib slot
	    OPT_RegisterOperand tib = 
	      getTIB(v, ir, target.getDeclaringClass());
	    OPT_RegisterOperand m = getInstanceMethod(v, ir, tib, methOp);
	    Call.setAddress(v, m);
	  }
	}
      }
      break;
    case OPT_MethodOperand.VIRTUAL:
      {
	OPT_RegisterOperand tib;
	if (!methOp.isSingleTarget()) {
	  tib = getTIB(v, ir, Call.getParam(v, 0).copy(), Call.getGuard(v).copy());
	} else {
	  if (method == ir.method) {          // RECURSION
	    Call.setAddress(v, new OPT_BranchOperand(ir.firstInstructionInCodeOrder()));
	    break;
	  } else 
	    tib = getTIB(v, ir, Call.getParam(v, 0).copy(), Call.getGuard(v).copy());
	}
	Call.setAddress(v, getInstanceMethod(v, ir, tib, methOp));
      }
      break;
    case OPT_MethodOperand.INTERFACE:
      {
	if (VM.BuildForIMTInterfaceInvocation) {
	  // SEE ALSO: OPT_FinalMIRExpansion (for hidden parameter)
	  OPT_RegisterOperand RHStib = 
	    getTIB(v, ir, Call.getParam(v, 0).copy(), Call.getGuard(v).copy());
	  int signatureId = VM_ClassLoader.
            findOrCreateInterfaceMethodSignatureId(methOp.method.getName(),
                                                   methOp.method.
                                                   getDescriptor());
	  OPT_RegisterOperand address = null;
          if (VM.BuildForEmbeddedIMT) {
	    address = InsertLoadOffset(v, ir, REF_LOAD, 
                                       OPT_ClassLoaderProxy.
                                       InstructionArrayType, 
                                       RHStib.copyD2U(), 
                                       VM_InterfaceMethodSignature.getOffset
                                       (signatureId)); 
          } else {
	    OPT_RegisterOperand IMT = InsertLoadOffset(v, ir, REF_LOAD,
			     OPT_ClassLoaderProxy.JavaLangObjectArrayType,
			     RHStib.copyD2U(),
			     TIB_IMT_TIB_INDEX << 2);
	    address = InsertLoadOffset(v, ir, REF_LOAD,
			     OPT_ClassLoaderProxy.InstructionArrayType,
			     IMT.copyD2U(),
                             VM_InterfaceMethodSignature.getOffset(signatureId)); 

          }
	  Call.setAddress(v, address);
	} else if (VM.BuildForITableInterfaceInvocation && 
		   VM.DirectlyIndexedITables && 
		   methOp.method.getDeclaringClass().isResolved()) {
	  OPT_RegisterOperand RHStib = 
	    getTIB(v, ir, Call.getParam(v, 0).copy(), Call.getGuard(v).copy());
	  OPT_RegisterOperand iTables = 
	    InsertLoadOffset(v, ir, REF_LOAD,
			     OPT_ClassLoaderProxy.JavaLangObjectArrayType,
			     RHStib.copyD2U(),
			     TIB_ITABLES_TIB_INDEX << 2);
	  OPT_RegisterOperand iTable = 
	    InsertLoadOffset(v, ir, REF_LOAD,
			     OPT_ClassLoaderProxy.JavaLangObjectArrayType,
			     iTables.copyD2U(),
			     methOp.method.getDeclaringClass().
                             getInterfaceId()<<2);
	  OPT_RegisterOperand address = 
	    InsertLoadOffset(v, ir, REF_LOAD,
			     OPT_ClassLoaderProxy.InstructionArrayType,
			     iTable.copyD2U(),
			     methOp.method.getDeclaringClass().
                             getITableIndex(methOp.method)<<2);
	  Call.setAddress(v, address);
	} else {
	  VM_Class I = methOp.method.getDeclaringClass();
          int itableIndex = -1; 
	  if (VM.BuildForITableInterfaceInvocation) {
	    // search ITable variant
	    if (I.isLoaded()) {
	      itableIndex = I.getITableIndex(methOp.method);
	    }
	  }
          if (itableIndex == -1) {
            // itable index is not known at compile-time.
            // call "invokeinterface" to resolve the object and method id
            // into a method address
            OPT_RegisterOperand realAddrReg = 
              ir.regpool.makeTemp(OPT_ClassLoaderProxy.InstructionArrayType);
            OPT_Instruction vp = 
              Call.create2(CALL, realAddrReg, null, 
                           OPT_MethodOperand.STATIC
                           (VM_OptLinker.invokeinterfaceMethod), 
                           Call.getParam(v, 0).asRegister().copyU2U(), 
                           I(methOp.method.getDictionaryId()));
            vp.position = v.position;
            vp.bcIndex = RUNTIME_SERVICES_BCI;
            v.insertBack(vp);
            call(vp, ir);
            Call.setAddress(v, realAddrReg.copyD2U());
            return  v;
          } else {
            // itable index is known at compile-time.
            // call "findItable" to resolve object + interface id into
            // itable address
            OPT_RegisterOperand iTable = 
              ir.regpool.makeTemp(OPT_ClassLoaderProxy.JavaLangObjectArrayType);
	    OPT_RegisterOperand RHStib = 
	      getTIB(v, ir, Call.getParam(v, 0).copy(), Call.getGuard(v).copy());
            OPT_Instruction fi = 
              Call.create2(CALL, iTable, null, 
                           OPT_MethodOperand.STATIC
                           (VM_Entrypoints.findItableMethod), 
                           RHStib, I(I.getDictionaryId()));
            fi.position = v.position;
            fi.bcIndex = RUNTIME_SERVICES_BCI;
            v.insertBack(fi);
            call(fi, ir);
            OPT_RegisterOperand address = 
              InsertLoadOffset(v, ir, REF_LOAD,
                               OPT_ClassLoaderProxy.InstructionArrayType,
                               iTable.copyD2U(), itableIndex<<2);
            Call.setAddress(v, address);
            return  v;
          }
	}
      }
    }
    return  v;
  }

  /**
   * Generate the code to resolve a field reference.
   *
   * @param loc the location operand that describes the VM_Field
   *            being dynamically linked
   * @param s the unresolved instruction
   * @param ir the containing OPT_IR object
   * @param mustBeValid will the offset always be valid when the 
   *                    code is executed (getfield)
   * @return OPT_RegisterOperand that represents the resolved offset
   */
  private static final OPT_RegisterOperand resolve (OPT_LocationOperand loc, 
						    OPT_Instruction s, 
						    OPT_IR ir, 
						    boolean mustBeValid) {
    VM_Field offsetTableField = VM_Entrypoints.fieldOffsetsField;
    return  resolve(offsetTableField, loc.copy(), s, ir, mustBeValid, true);
  }

  /**
   * Generate code to resolve a method reference.
   * 
   * @param methOp the method operand that described the VM_Method being
   *               dynamically linked.
   * @param s the unresolved instruction
   * @param ir the containing IR
   * @param mustBeValid will the offset always be valid when the 
   *                    code is executed (invokespecial)
   * @return OPT_RegisterOperand that represents the resolved offset
   */
  private static final OPT_RegisterOperand resolve (OPT_MethodOperand methOp, 
						    OPT_Instruction s, 
						    OPT_IR ir, 
						    boolean mustBeValid) {
    VM_Field offsetTableField = VM_Entrypoints.methodOffsetsField;
    return  resolve(offsetTableField, methOp.copy(), s, ir, mustBeValid, false);
  }

  /**
   * Actually do the work of generating the code to resolve a reference to
   * a VM_Member.
   * @param offsetTableField the offset table
   * @param fieldOrMethod the VM_Member being resolved
   * @param s the unresolved instruction
   * @param ir the containingIR
   * @param mustBeValid will the offset always be valid when the 
   *                    code is executed
   * @param isField are we linking a field or method reference?
   * @return OPT_RegisterOperand that represents the resolved offset
   */
  private static final OPT_RegisterOperand resolve (VM_Field offsetTableField, 
						    Object fieldOrMethod, 
						    OPT_Instruction s, 
						    OPT_IR ir, 
						    boolean mustBeValid, 
						    boolean isField) {
    OPT_Instruction s2;
    int dictID;
    OPT_LocationOperand loc = null;
    OPT_MethodOperand methOp = null;
    VM_Field field = null;
    VM_Method method = null;
    if (isField) {
      loc = (OPT_LocationOperand)fieldOrMethod;
      field = loc.field;
      dictID = field.getDictionaryId();
    } else {
      methOp = (OPT_MethodOperand)fieldOrMethod;
      method = methOp.method;
      dictID = method.getDictionaryId();
    }
    if (mustBeValid) {
      OPT_RegisterOperand offsetTable = getStatic(s, ir, offsetTableField);
      OPT_RegisterOperand offset = InsertLoadOffset(s, ir, INT_LOAD, 
                                                    VM_Type.IntType, 
                                                    offsetTable, dictID << 2);
      return  offset;
    } else {
      OPT_BasicBlock predBB = s.getBasicBlock();  
      OPT_BasicBlock succBB = predBB.splitNodeAt(s.getPrev(), ir); 
      OPT_BasicBlock testBB = predBB.createSubBlock(s.bcIndex, ir);
      OPT_BasicBlock resolveBB = predBB.createSubBlock(s.bcIndex, ir);
      // Get the offset from the appropriate VM_ClassLoader array 
      // and check to see if it is valid
      OPT_RegisterOperand offsetTable = 
	getStatic(testBB.lastInstruction(), ir, offsetTableField);
      OPT_RegisterOperand offset = InsertLoadOffset(testBB.lastInstruction(), 
						    ir, INT_LOAD, 
						    VM_Type.IntType, 
						    offsetTable, dictID << 2);
      testBB.appendInstruction(IfCmp.create(INT_IFCMP, null, 
					    offset.copyU2U(),
					    I(NEEDS_DYNAMIC_LINK),
					    OPT_ConditionOperand.EQUAL(), 
					    resolveBB.makeJumpTarget(),
					    OPT_BranchProfileOperand.unlikely()));
      // Handle the offset being invalid
      if (isField)
        s2 = CacheOp.create(RESOLVE, loc); 
      else 
        s2 = CacheOp.create(RESOLVE, methOp);
      s2.position = s.position;
      s2.bcIndex = s.bcIndex;
      resolveBB.appendInstruction(s2);
      resolveBB.appendInstruction(testBB.makeGOTO()); 
      // Put together the CFG links & code order
      predBB.insertOut(testBB);
      ir.cfg.linkInCodeOrder(predBB, testBB);
      testBB.insertOut(succBB);
      testBB.insertOut(resolveBB);
      ir.cfg.linkInCodeOrder(testBB, succBB);
      resolveBB.insertOut(testBB);              // backedge
      resolveBB.setInfrequent(true);
      ir.cfg.addLastInCodeOrder(resolveBB);  // stick resolution code off 
      // in outer space.
      return  offset;
    }
  }

  /**
   * Insert a binary instruction before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param o1 the first operand
   * @param o2 the second operand
   * @return the result operand of the inserted instruction
   */
  static OPT_RegisterOperand InsertBinary (OPT_Instruction s, OPT_IR ir, 
					   OPT_Operator operator, 
					   VM_Type type, 
					   OPT_Operand o1, 
					   OPT_Operand o2) {
    OPT_RegisterOperand t = ir.regpool.makeTemp(type);
    s.insertBack(Binary.create(operator, t, o1, o2));
    return  t.copyD2U();
  }

  /**
   * Insert a unary instruction before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param o1 the operand
   * @return the result operand of the inserted instruction
   */
  static OPT_RegisterOperand InsertUnary (OPT_Instruction s, OPT_IR ir, 
					  OPT_Operator operator, 
					  VM_Type type, 
					  OPT_Operand o1) {
    OPT_RegisterOperand t = ir.regpool.makeTemp(type);
    s.insertBack(Unary.create(operator, t, o1));
    return  t.copyD2U();
  }

  /**
   * Insert a guarded unary instruction before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param o1 the operand
   * @param guard the guard operand
   * @return the result operand of the inserted instruction
   */
  static OPT_RegisterOperand InsertGuardedUnary (OPT_Instruction s, OPT_IR ir, 
						 OPT_Operator operator, 
						 VM_Type type, 
						 OPT_Operand o1, 
						 OPT_Operand guard) {
    OPT_RegisterOperand t = ir.regpool.makeTemp(type);
    s.insertBack(GuardedUnary.create(operator, t, o1, guard));
    return  t.copyD2U();
  }

  /**
   * Insert a load off the JTOC before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param offset the offset to load at 
   * @return the result operand of the inserted instruction
   */
  static OPT_RegisterOperand InsertLoadOffsetJTOC (OPT_Instruction s, 
						   OPT_IR ir, 
						   OPT_Operator operator,
						   VM_Type type, 
						   int offset) {
    OPT_RegisterOperand res = 
      InsertLoadOffset(s, ir, operator, type, ir.regpool.makeJTOCOp(ir,s), offset);
    OPT_Instruction JTOCload = (OPT_Instruction)s.getPrev();
    Load.setLocation(JTOCload, new OPT_LocationOperand(offset));
    return  res;
  }

  /**
   * Insert a load off before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param reg2 the base to load from
   * @param offset the offset to load at 
   * @return the result operand of the inserted instruction
   */
  static OPT_RegisterOperand InsertLoadOffset (OPT_Instruction s, OPT_IR ir, 
					       OPT_Operator operator, 
					       VM_Type type, 
					       OPT_Operand reg2, 
					       int offset) {
    return InsertLoadOffset(s, ir, operator, type, reg2, offset, null, null);
  }

  /**
   * Insert a load off before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param reg2 the base to load from
   * @param offset the offset to load at 
   * @param guard the guard operand
   * @return the result operand of the inserted instruction
   */
  static OPT_RegisterOperand InsertLoadOffset (OPT_Instruction s, OPT_IR ir, 
					       OPT_Operator operator, 
					       VM_Type type, 
					       OPT_Operand reg2, 
					       int offset, 
					       OPT_Operand guard) {
    return InsertLoadOffset(s, ir, operator, type, reg2, offset, null, guard);
  }

  /**
   * Insert a load off before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param reg2 the base to load from
   * @param offset the offset to load at 
   * @param loc the location operand
   * @param guard the guard operand
   * @return the result operand of the inserted instruction
   */
  static OPT_RegisterOperand InsertLoadOffset (OPT_Instruction s, OPT_IR ir, 
					       OPT_Operator operator, 
					       VM_Type type, 
					       OPT_Operand reg2, 
					       int offset, 
					       OPT_LocationOperand loc, 
					       OPT_Operand guard) {
    OPT_RegisterOperand regTarget = ir.regpool.makeTemp(type);
    OPT_Instruction s2 = Load.create(operator, regTarget, reg2, I(offset), 
				     loc, guard);
    s.insertBack(s2);
    return  regTarget.copyD2U();
  }

  // get the tib from the object pointer to by obj
  static OPT_RegisterOperand getTIB (OPT_Instruction s, OPT_IR ir, 
				     OPT_Operand obj, 
				     OPT_Operand guard) {
    OPT_RegisterOperand res = 
      ir.regpool.makeTemp(OPT_ClassLoaderProxy.JavaLangObjectArrayType);
    OPT_Instruction s2 = GuardedUnary.create(GET_OBJ_TIB, res, obj, guard);
    s.insertBack(s2);
    return  res.copyD2U();
  }

  // get the class tib for type
  static OPT_RegisterOperand getTIB (OPT_Instruction s, OPT_IR ir, 
				     VM_Type type) {
    return  getTIB(s, ir, new OPT_TypeOperand(type));
  }

  // getr the class tib for type
  static OPT_RegisterOperand getTIB (OPT_Instruction s, OPT_IR ir, 
				     OPT_TypeOperand type) {
    OPT_RegisterOperand res = 
      ir.regpool.makeTemp(OPT_ClassLoaderProxy.JavaLangObjectArrayType);
    s.insertBack(Unary.create(GET_CLASS_TIB, res, type));
    return  res.copyD2U();
  }

  /**
   * Emit code to reacquire a pointer to t (a VM_Type object) at runtime.
   * TODO: In a noncopying GC, we could embed the address directly 
   * in the code stream.
   */
  static OPT_RegisterOperand getVMType (OPT_Instruction ptr, OPT_IR ir, 
					VM_Type t) {
    if (VM.VerifyAssertions) VM.assert(!t.isPrimitiveType());
    OPT_RegisterOperand tib = getTIB(ptr, ir, t);
    return  InsertUnary(ptr, ir, GET_TYPE_FROM_TIB, 
			OPT_ClassLoaderProxy.VM_Type_type, tib);
  }

  /**
   * Get an instance method from a TIB
   */
  static OPT_RegisterOperand getInstanceMethod (OPT_Instruction s, OPT_IR ir, 
						OPT_RegisterOperand tib, 
						OPT_MethodOperand methOp) {
    return InsertLoadOffset(s, ir, REF_LOAD, 
			    OPT_ClassLoaderProxy.InstructionArrayType, 
			    tib, methOp.method.getOffset()); 
  }

  /**
   * Load an instance field.
   * @param s
   * @param ir
   * @param obj
   * @param field
   * @return 
   */
  static OPT_RegisterOperand getField (OPT_Instruction s, OPT_IR ir, 
				       OPT_RegisterOperand obj, 
				       VM_Field field) {
    return  getField(s, ir, obj, field, null);
  }

  /**
   * Load an instance field.
   * @param s
   * @param ir
   * @param obj
   * @param field
   * @param guard
   * @return 
   */
  static OPT_RegisterOperand getField (OPT_Instruction s, OPT_IR ir, 
				       OPT_RegisterOperand obj, 
				       VM_Field field, OPT_Operand guard) {
    return InsertLoadOffset(s, ir, OPT_IRTools.getLoadOp(field), 
			    field.getType(), obj, field.getOffset(), 
			    new OPT_LocationOperand(field), guard);
  }

  /**
   * Load a static method.
   * @param s
   * @param ir
   * @param method
   * @return 
   */
  static OPT_RegisterOperand getStaticMethod (OPT_Instruction s, OPT_IR ir, 
					      VM_Method method) {
    return InsertLoadOffsetJTOC(s, ir, REF_LOAD, 
				OPT_ClassLoaderProxy.InstructionArrayType, 
				method.getOffset());
  }

  /**
   * Load a static method.
   * @param s
   * @param ir
   * @param methOp
   * @return 
   */
  static OPT_RegisterOperand getStaticMethod (OPT_Instruction s, OPT_IR ir, 
					      OPT_MethodOperand methOp) {
    VM_Method method = methOp.method;
    return InsertLoadOffsetJTOC(s, ir, REF_LOAD, 
				OPT_ClassLoaderProxy.InstructionArrayType, 
				method.getOffset());
  }

  /*  RRB 100500 */
  /**
   * support for direct call to specialized method.
   */
  static OPT_RegisterOperand getSpecialMethod (OPT_Instruction s, OPT_IR ir, 
					       int smid) {
    //  First, get the pointer to the JTOC offset pointing to the 
    //  specialized Method table
    OPT_RegisterOperand reg = 
      InsertLoadOffsetJTOC(s, ir, REF_LOAD, 
			   OPT_ClassLoaderProxy.JavaLangObjectArrayType, 
			   OPT_Entrypoints.specializedMethodsOffset);
    OPT_RegisterOperand instr = 
      InsertLoadOffset(s, ir, REF_LOAD, 
		       OPT_ClassLoaderProxy.InstructionArrayType, 
		       reg, smid << 2);
    return  instr;
  }

  /**
   * Load a static field.
   * @param s
   * @param ir
   * @param field
   * @return 
   */
  static OPT_RegisterOperand getStatic (OPT_Instruction s, OPT_IR ir, 
					VM_Field field) {
    return InsertLoadOffsetJTOC(s, ir, 
				OPT_IRTools.getLoadOp(field), 
				field.getType(), field.getOffset());
  }
}
