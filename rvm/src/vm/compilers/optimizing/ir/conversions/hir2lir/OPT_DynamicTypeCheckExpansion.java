/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Expansion of Dynamic Type Checking operations.
 *
 * @see VM_DynamicTypeCheck
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Martin Trapp
 */
abstract class OPT_DynamicTypeCheckExpansion extends OPT_ConvertToLowLevelIR {

  //////////////////
  // Entrypoints for expanding each kind of DTC instruction into 
  // LIR instructions
  //////////////////

  /**
   * Expand an instanceof instruction into the LIR sequence that implements
   * the dynamic type check.  Ref may contain a null ptr at runtime.
   * 
   * @param s an INSTANCEOF instruction to expand 
   * @param ir the enclosing OPT_IR
   * @return the last OPT_Instruction in the generated LIR sequence.
   */
  static OPT_Instruction instanceOf (OPT_Instruction s, OPT_IR ir) {
    OPT_RegisterOperand result = InstanceOf.getClearResult(s);
    VM_Type LHStype = InstanceOf.getType(s).type;
    OPT_RegisterOperand ref = (OPT_RegisterOperand)InstanceOf.getClearRef(s);
    OPT_RegisterOperand guard = ir.regpool.makeTempValidation();
    OPT_Instruction next = s.nextInstructionInCodeOrder();
    if (next.operator() == INT_IFCMP && 
	IfCmp.getVal1(next) instanceof OPT_RegisterOperand && 
	result.similar(IfCmp.getVal1(next))) {
      // The result of instanceof is being consumed by a conditional branch.
      // Optimize this case by generating a branching type check 
      // instead of producing a value.
      // TODO: This is really not safe: suppose the if is NOT the 
      // only use of the result of the instanceof.  
      // The way to fix this is to add ifInstanceOf and ifNotInstanceOf
      // operators to the IR and have OPT_Simple transform 
      // instanceof, intIfCmp based on the U/D chains.
      // See CMVC defect 166860.
      OPT_Operand val2 = IfCmp.getVal2(next);
      if (VM.VerifyAssertions) VM.assert(val2.isIntConstant());
      int ival2 = ((OPT_IntConstantOperand)val2).value;
      OPT_ConditionOperand cond = IfCmp.getCond(next);
      boolean branchCondition = 
	(((ival2 == 0) && (cond.isNOT_EQUAL() || cond.isLESS_EQUAL())) ||
	 ((ival2 == 1) && (cond.isEQUAL() || cond.isGREATER_EQUAL())));
      OPT_BasicBlock branchBB = next.getBranchTarget();
      OPT_RegisterOperand oldGuard = IfCmp.getGuardResult(next);
      next.remove();
      OPT_BasicBlock fallThroughBB = fallThroughBB(s, ir);
      OPT_BasicBlock falseBranch = 
	branchCondition ? fallThroughBB : branchBB;
      OPT_BasicBlock trueBranch = 
	branchCondition ? branchBB : fallThroughBB;
      OPT_Instruction nullComp = 
	IfCmp.create(REF_IFCMP, guard, ref.copyU2U(), 
		     new OPT_NullConstantOperand(),
		     OPT_ConditionOperand.EQUAL(), 
		     falseBranch.makeJumpTarget(),
		     new OPT_BranchProfileOperand());
      s.insertBefore(nullComp);
      OPT_BasicBlock myBlock = s.getBasicBlock();
      OPT_BasicBlock instanceOfBlock = myBlock.splitNodeAt(nullComp, ir);
      myBlock.insertOut(instanceOfBlock);
      myBlock.insertOut(falseBranch);
      ir.cfg.linkInCodeOrder(myBlock, instanceOfBlock);
      OPT_RegisterOperand RHStib = getTIB(s, ir, ref, guard.copyD2U());
      return generateBranchingTypeCheck(s, ir, ref, LHStype, RHStib, 
					trueBranch, falseBranch, oldGuard);
    } else {
      // Not a branching pattern
      OPT_BasicBlock instanceOfBlock = 
	s.getBasicBlock().segregateInstruction(s, ir);
      OPT_BasicBlock prevBB = instanceOfBlock.prevBasicBlockInCodeOrder();
      OPT_BasicBlock nextBB = instanceOfBlock.nextBasicBlockInCodeOrder();
      OPT_BasicBlock nullCaseBB = 
	instanceOfBlock.createSubBlock(s.bcIndex, ir);
      prevBB.appendInstruction(IfCmp.create(REF_IFCMP, guard, 
					    ref.copyU2U(), 
					    new OPT_NullConstantOperand(),
					    OPT_ConditionOperand.EQUAL(), 
					    nullCaseBB.makeJumpTarget(),
					    new OPT_BranchProfileOperand()));
      nullCaseBB.appendInstruction(Move.create(INT_MOVE, result.copyD2D(), I(0)));
      nullCaseBB.appendInstruction(Goto.create(GOTO, nextBB.makeJumpTarget()));
      // Stitch together the CFG; add nullCaseBB to the end of code array.
      prevBB.insertOut(nullCaseBB);
      nullCaseBB.insertOut(nextBB);
      nullCaseBB.setInfrequent(true);
      ir.cfg.addLastInCodeOrder(nullCaseBB);
      OPT_RegisterOperand RHStib = getTIB(s, ir, ref, guard.copyD2U());
      return generateValueProducingTypeCheck(s, ir, ref, LHStype, RHStib, 
					     result);
    }
  }


  /**
   * Expand an instanceof instruction into the LIR sequence that implements 
   * the dynamic type check.  Ref is known to never contain a null ptr at 
   * runtime.
   * 
   * @param s an INSTANCEOF_NOTNULL instruction to expand 
   * @param ir the enclosing OPT_IR
   * @return the last OPT_Instruction in the generated LIR sequence.
   */
  static OPT_Instruction instanceOfNotNull (OPT_Instruction s, OPT_IR ir) {
    OPT_RegisterOperand result = InstanceOf.getClearResult(s);
    VM_Type LHStype = InstanceOf.getType(s).type;
    OPT_RegisterOperand ref = (OPT_RegisterOperand)InstanceOf.getClearRef(s);
    OPT_Operand guard = InstanceOf.getClearGuard(s);
    OPT_Instruction next = s.nextInstructionInCodeOrder();
    if (next.operator() == INT_IFCMP && 
	IfCmp.getVal1(next) instanceof OPT_RegisterOperand
	&& result.similar(IfCmp.getVal1(next))) {
      // The result of instanceof is being consumed by a conditional branch.
      // Optimize this case by generating a branching type 
      // check instead of producing a value.
      OPT_Operand val2 = IfCmp.getVal2(next);
      if (VM.VerifyAssertions)
	VM.assert(val2.isIntConstant());
      int ival2 = ((OPT_IntConstantOperand)val2).value;
      OPT_ConditionOperand cond = IfCmp.getCond(next);
      boolean branchCondition = 
	(((ival2 == 0) && (cond.isNOT_EQUAL() || cond.isLESS_EQUAL())) || 
	 ((ival2 == 1) && (cond.isEQUAL() || cond.isGREATER_EQUAL())));
      OPT_BasicBlock branchBB = next.getBranchTarget();
      OPT_RegisterOperand oldGuard = IfCmp.getGuardResult(next);
      next.remove();
      OPT_BasicBlock fallThroughBB = fallThroughBB(s, ir);
      OPT_RegisterOperand RHStib = getTIB(s, ir, ref, guard);
      if (branchCondition) {
	return generateBranchingTypeCheck(s, ir, ref, LHStype, RHStib, branchBB, 
					  fallThroughBB, oldGuard);
      } else {
	return generateBranchingTypeCheck(s, ir, ref, LHStype, RHStib, 
					  fallThroughBB, branchBB, oldGuard);
      }
    } else {
      // Not a branching pattern
      OPT_RegisterOperand RHStib = getTIB(s, ir, ref, guard);
      return generateValueProducingTypeCheck(s, ir, ref, LHStype, RHStib, 
					     result);
    }
  }


  /**
   * Expand a checkcast instruction into the LIR sequence that implements the 
   * dynamic type check, raising a ClassCastException when the type check 
   * fails. Ref may contain a null ptr at runtime.
   * 
   * @param s a CHECKCAST instruction to expand 
   * @param ir the enclosing OPT_IR
   * @return the last OPT_Instruction in the generated LIR sequence.
   */
  static OPT_Instruction checkcast (OPT_Instruction s, OPT_IR ir) {
    OPT_RegisterOperand ref = (OPT_RegisterOperand)TypeCheck.getClearRef(s);
    VM_Type LHStype = TypeCheck.getType(s).type;
    OPT_RegisterOperand guard = ir.regpool.makeTempValidation();
    OPT_Instruction nullCond = 
      IfCmp.create(REF_IFCMP, guard, ref.copyU2U(), 
		   new OPT_NullConstantOperand(),
		   OPT_ConditionOperand.EQUAL(), 
		   null, // KLUDGE...we haven't created the block yet!
		   new OPT_BranchProfileOperand());
    s.insertBefore(nullCond);
    OPT_BasicBlock myBlock = s.getBasicBlock();
    OPT_BasicBlock failBlock = myBlock.createSubBlock(s.bcIndex, ir);
    OPT_BasicBlock instanceOfBlock = myBlock.splitNodeAt(nullCond, ir);
    OPT_BasicBlock succBlock = instanceOfBlock.splitNodeAt(s, ir);
    IfCmp.setTarget(nullCond, succBlock.makeJumpTarget()); // fixup KLUDGE
    myBlock.insertOut(instanceOfBlock);
    myBlock.insertOut(succBlock);
    instanceOfBlock.insertOut(failBlock);
    instanceOfBlock.insertOut(succBlock);
    ir.cfg.linkInCodeOrder(myBlock, instanceOfBlock);
    ir.cfg.linkInCodeOrder(instanceOfBlock, succBlock);
    failBlock.setInfrequent(true);
    ir.cfg.addLastInCodeOrder(failBlock);
    OPT_Instruction raiseError = 
      Trap.create(TRAP, null, OPT_TrapCodeOperand.CheckCast());
    raiseError.copyPosition(s);
    failBlock.appendInstruction(raiseError);
    OPT_RegisterOperand RHStib = getTIB(s, ir, ref, guard.copyD2U());
    return generateBranchingTypeCheck(s, ir, ref, LHStype, RHStib, succBlock, 
				      failBlock, null);
  }


  /**
   * Expand a checkcast instruction into the LIR sequence that implements the 
   * dynamic type check, raising a ClassCastException when the type check 
   * fails. Ref is known to never contain a null ptr at runtime.
   *
   * @param s a CHECKCAST_NOTNULL instruction to expand 
   * @param ir the enclosing OPT_IR
   * @return the last OPT_Instruction in the generated LIR sequence.
   */
  static OPT_Instruction checkcastNotNull (OPT_Instruction s, OPT_IR ir) {
    OPT_RegisterOperand ref = (OPT_RegisterOperand)TypeCheck.getClearRef(s);
    VM_Type LHStype = TypeCheck.getType(s).type;
    OPT_Operand guard = TypeCheck.getClearGuard(s);
    OPT_BasicBlock myBlock = s.getBasicBlock();
    OPT_BasicBlock failBlock = myBlock.createSubBlock(s.bcIndex, ir);
    OPT_BasicBlock succBlock = myBlock.splitNodeAt(s, ir);
    myBlock.insertOut(failBlock);
    myBlock.insertOut(succBlock);
    ir.cfg.linkInCodeOrder(myBlock, succBlock);
    failBlock.setInfrequent(true);
    ir.cfg.addLastInCodeOrder(failBlock);
    OPT_Instruction raiseError = 
      Trap.create(TRAP, null, OPT_TrapCodeOperand.CheckCast());
    raiseError.copyPosition(s);
    failBlock.appendInstruction(raiseError);
    OPT_RegisterOperand RHStib = getTIB(s, ir, ref, guard);
    return generateBranchingTypeCheck(s, ir, ref, LHStype, RHStib, succBlock, 
				      failBlock, null);
  }


  /**
   * Expand a checkcastInterface instruction into the LIR sequence that 
   * implements the dynamic type check, raising an IncompataibleClassChangeError
   * if the type check fails. 
   * Ref is known to never contain a null ptr at runtime.
   *
   * @param s a CHECKCAST_NOTNULL instruction to expand 
   * @param ir the enclosing OPT_IR
   * @return the last OPT_Instruction in the generated LIR sequence.
   */
  static OPT_Instruction checkcastInterfaceNotNull (OPT_Instruction s, 
						    OPT_IR ir) {
    OPT_RegisterOperand ref = (OPT_RegisterOperand)TypeCheck.getClearRef(s);
    VM_Type LHStype = TypeCheck.getType(s).type;
    OPT_Operand guard = TypeCheck.getClearGuard(s);
    int LHSInterfaceId = LHStype.asClass().getInterfaceId();

    OPT_BasicBlock testBlock = s.getBasicBlock();
    OPT_BasicBlock outOfLineBlock = testBlock.createSubBlock(s.bcIndex, ir);
    OPT_BasicBlock succBlock = testBlock.splitNodeAt(s, ir);
    testBlock.insertOut(outOfLineBlock);
    testBlock.insertOut(succBlock);
    outOfLineBlock.insertOut(succBlock);
    ir.cfg.linkInCodeOrder(testBlock, succBlock);
    outOfLineBlock.setInfrequent(true);
    ir.cfg.addLastInCodeOrder(outOfLineBlock);

    s.remove();

    // insert the inline portion of the dynamic type check into testBlock;
    OPT_Instruction ptr = testBlock.lastInstruction();
    OPT_RegisterOperand RHStib = getTIB(ptr, ir, ref.copyU2U(), guard.copy());
    OPT_RegisterOperand trits = 
      InsertUnary(ptr, ir, GET_IMPLEMENTS_TRITS_FROM_TIB, 
		  OPT_ClassLoaderProxy.ByteArrayType, RHStib);
    if (VM_DynamicTypeCheck.MIN_IMPLEMENTS_TRITS_SIZE <= LHSInterfaceId){
      OPT_RegisterOperand tritsLength = 
	InsertGuardedUnary(ptr, ir, ARRAYLENGTH, VM_Type.IntType, trits, TG());
      OPT_Instruction lengthCheck = 
	IfCmp.create(INT_IFCMP, null, 
		     tritsLength, I(LHSInterfaceId),
		     OPT_ConditionOperand.LESS_EQUAL(), 
		     outOfLineBlock.makeJumpTarget(),
		     OPT_BranchProfileOperand.unlikely());
      ptr.insertBefore(lengthCheck);
      OPT_BasicBlock oldBlock = testBlock;
      testBlock = testBlock.splitNodeWithLinksAt(lengthCheck, ir);
      oldBlock.insertOut(outOfLineBlock); // required due to splitNode!
      ptr = testBlock.lastInstruction();
    }
    OPT_RegisterOperand trit = 
      InsertLoadOffset(ptr, ir, UBYTE_LOAD, VM_Type.ByteType,
		       trits.copyU2U(), LHSInterfaceId);
    ptr.insertBefore(IfCmp.create(INT_IFCMP, null, 
				  trit, I(VM_DynamicTypeCheck.YES),
				  OPT_ConditionOperand.NOT_EQUAL(), 
				  outOfLineBlock.makeJumpTarget(),
				  OPT_BranchProfileOperand.unlikely()));

    // insert code in outOfLineBlock to handle the type check 
    // returning MAYBE or NO.
    ptr = outOfLineBlock.lastInstruction();
    OPT_RegisterOperand LHSRuntimeClass = getVMType(ptr, ir, LHStype);
    OPT_Instruction tcCall = 
      Call.create2(CALL, null, null, 
		   OPT_MethodOperand.STATIC(VM_Entrypoints.mandatoryInstanceOfInterfaceMethod),
		   LHSRuntimeClass, RHStib.copyD2U());
    tcCall.copyPosition(s);
    ptr.insertBefore(tcCall);
    ptr.insertBefore(Goto.create(GOTO, succBlock.makeJumpTarget()));

    return testBlock.lastInstruction();
  }


  /**
   * Expand an object array store check into the LIR sequence that 
   * implements it.
   *
   * @param s an OBJARRAY_STORE_CHECK instruction to expand 
   * @param ir the enclosing OPT_IR
   * @return the last OPT_Instruction in the generated LIR sequence.
   */
  static OPT_Instruction arrayStoreCheck (OPT_Instruction s, OPT_IR ir) {
    if (VM.BuildForFastDynamicTypeCheck) {
      OPT_RegisterOperand guardResult = StoreCheck.getClearGuardResult(s);
      OPT_RegisterOperand arrayRef = StoreCheck.getClearRef(s).asRegister();
      OPT_Operand elemRef = StoreCheck.getClearVal(s);
      OPT_Operand guard = StoreCheck.getClearGuard(s);
      if (elemRef instanceof OPT_NullConstantOperand) {
        OPT_Instruction continueAt = s.prevInstructionInCodeOrder();
        s.remove();
        return continueAt;
      }
      OPT_InlineSequence position = s.position;
      int bcIndex = s.bcIndex;
      OPT_BasicBlock myBlock = s.getBasicBlock();
      OPT_BasicBlock contBlock = myBlock.splitNodeAt(s, ir);
      OPT_BasicBlock shortCircuitBlock1 = myBlock.createSubBlock(s.bcIndex, ir);
      OPT_BasicBlock shortCircuitBlock2 = myBlock.createSubBlock(s.bcIndex, ir);
      OPT_BasicBlock shortCircuitBlock3 = myBlock.createSubBlock(s.bcIndex, ir);
      OPT_BasicBlock shortCircuitBlock4 = myBlock.createSubBlock(s.bcIndex, ir);
      OPT_BasicBlock testBlock = myBlock.createSubBlock(s.bcIndex, ir);

      myBlock.insertOut(shortCircuitBlock1);
      myBlock.insertOut(contBlock);
      shortCircuitBlock1.insertOut(shortCircuitBlock2);
      shortCircuitBlock2.insertOut(shortCircuitBlock3);
      shortCircuitBlock2.insertOut(contBlock);
      shortCircuitBlock3.insertOut(shortCircuitBlock4);
      shortCircuitBlock4.insertOut(testBlock);
      testBlock.insertOut(contBlock);
      ir.cfg.linkInCodeOrder(myBlock, shortCircuitBlock1);
      ir.cfg.linkInCodeOrder(shortCircuitBlock1, shortCircuitBlock2);
      ir.cfg.linkInCodeOrder(shortCircuitBlock2, shortCircuitBlock3);
      ir.cfg.linkInCodeOrder(shortCircuitBlock3, shortCircuitBlock4);
      ir.cfg.linkInCodeOrder(shortCircuitBlock4, testBlock);
      ir.cfg.linkInCodeOrder(testBlock, contBlock);
      
      // (0) Set up guard combine in contBlock
      OPT_RegisterOperand rhsGuard = ir.regpool.makeTempValidation();
      contBlock.prependInstruction(Binary.create(GUARD_COMBINE, 
						 guardResult, 
						 guardResult.copyRO(), 
						 rhsGuard.copyRO()));

      // (1) is rhs null?
      IfCmp.mutate(s, REF_IFCMP, rhsGuard, elemRef, 
		   new OPT_NullConstantOperand(),
		   OPT_ConditionOperand.EQUAL(), 
		   contBlock.makeJumpTarget(), new OPT_BranchProfileOperand());
      // (2) is lhs runtime type the same as the declared compile 
      //     time type of the lhs?
      //     or if the declared type is unknown, we can still 
      //     attempt to short circuit with a compare to [Object.
      OPT_Instruction continueAt = shortCircuitBlock1.lastInstruction();
      OPT_RegisterOperand lhsTIB = getTIB(continueAt, ir, arrayRef, guard);
      VM_Type compType = arrayRef.type;
      if (compType == VM_Type.JavaLangObjectType) {
	// we lost type info due to unloaded classes causing imprecise meets.
        compType = OPT_ClassLoaderProxy.JavaLangObjectArrayType;
      }
      if (arrayRef.isDeclaredType() || 
	  compType == OPT_ClassLoaderProxy.JavaLangObjectArrayType) {
	OPT_RegisterOperand declTIB = getTIB(continueAt, ir, compType);
	continueAt.insertBefore(IfCmp.create(REF_IFCMP, guardResult.copyRO(), 
					     declTIB, lhsTIB,
					     OPT_ConditionOperand.EQUAL(), 
					     contBlock.makeJumpTarget(),
					     new OPT_BranchProfileOperand()));
	shortCircuitBlock1.insertOut(contBlock);
      }
      // (3) is lhs element type the same as rhs?
      continueAt = shortCircuitBlock2.lastInstruction();
      OPT_RegisterOperand rhsTIB = 
	getTIB(continueAt, ir, elemRef.copy(), rhsGuard.copyD2U());
      OPT_RegisterOperand lhsElemTIB = 
	InsertUnary(continueAt, ir, GET_ARRAY_ELEMENT_TIB_FROM_TIB, 
		    OPT_ClassLoaderProxy.JavaLangObjectArrayType, 
		    lhsTIB.copyU2U());
      continueAt.insertBefore(IfCmp.create(REF_IFCMP, guardResult.copyRO(), 
					   rhsTIB, lhsElemTIB,
					   OPT_ConditionOperand.EQUAL(), 
					   contBlock.makeJumpTarget(),
					   new OPT_BranchProfileOperand()));
      // (4) is runtime lhs element type a class? 
      //     if so, is the rhs an instance of the lhs element type?
      //     If we know at compile time that the dimensionality of 
      //     compType is more than 1, then don't bother with this test, 
      //     since the only case in which this short-circuit
      //     can work with multi-dimensional arrays is caught by case (3).
      //     Futhermore, don't bother when compType's element type is 
      //     known to be an interface (short-circuit always fails).
      if (compType.getDimensionality() == 1) {
        VM_Class et = compType.asArray().getElementType().asClass();
	// must be a class type (dim == 1 & !prim)
        if (et.isResolved() && !et.isFinal() && !et.isInterface()) { 
	  // et is a resolved proper class with subclasses, 
	  // check if rhsType is one of them.
          continueAt = shortCircuitBlock3.lastInstruction();
          OPT_RegisterOperand lhsElemType = 
	    InsertUnary(continueAt, ir, 
			GET_TYPE_FROM_TIB, OPT_ClassLoaderProxy.VM_Type_type, 
			lhsElemTIB.copyU2U());
          OPT_RegisterOperand rhsSuperclassIds = 
	    InsertUnary(continueAt, ir, GET_SUPERCLASS_IDS_FROM_TIB, 
			OPT_ClassLoaderProxy.ShortArrayType, rhsTIB.copyD2U());
          OPT_RegisterOperand lhsElemDepth = 
	    getField(continueAt, ir, lhsElemType, VM_Entrypoints.depthField, TG());
          OPT_RegisterOperand rhsSuperclassIdsLength = 
	    InsertGuardedUnary(continueAt, ir, ARRAYLENGTH, VM_Type.IntType,
			       rhsSuperclassIds.copyD2U(), TG());
          continueAt.insertBefore(IfCmp.create(INT_IFCMP, guardResult.copyRO(), 
					       lhsElemDepth, 
					       rhsSuperclassIdsLength,
					       OPT_ConditionOperand.GREATER_EQUAL(), 
					       testBlock.makeJumpTarget(),
					       new OPT_BranchProfileOperand()));
	  shortCircuitBlock3.insertOut(testBlock);

	  continueAt = shortCircuitBlock4.lastInstruction();
          OPT_RegisterOperand lhsElemId = 
	    getField(continueAt, ir, lhsElemType.copyD2U(), VM_Entrypoints.idField, TG());
          OPT_RegisterOperand refCandidate = 
	    ir.regpool.makeTemp(VM_Type.ShortType);
          OPT_LocationOperand loc = new OPT_LocationOperand(VM_Type.ShortType);
          if (LOWER_ARRAY_ACCESS) {
            OPT_RegisterOperand lhsDepthOffset = 
	      InsertBinary(continueAt, ir, INT_SHL, VM_Type.IntType, 
			   lhsElemDepth.copyD2U(), I(1));
            continueAt.insertBefore(Load.create(SHORT_LOAD, refCandidate, 
						rhsSuperclassIds, 
						lhsDepthOffset, loc, TG()));
          } else {
            continueAt.insertBefore(ALoad.create(SHORT_ALOAD, refCandidate, 
						 rhsSuperclassIds, 
						 lhsElemDepth, loc, TG()));
          }
          continueAt.insertBefore(IfCmp.create(INT_IFCMP, guardResult.copyRO(),
					       refCandidate.copyD2U(), 
					       lhsElemId,
					       OPT_ConditionOperand.EQUAL(), 
					       contBlock.makeJumpTarget(),
					       new OPT_BranchProfileOperand()));
	  shortCircuitBlock4.insertOut(contBlock);
        }
      }
      // (5) Sigh.  Make the call to checkstore
      continueAt = testBlock.lastInstruction();
      OPT_RegisterOperand rhsTYPE = 
	InsertUnary(continueAt, ir, GET_TYPE_FROM_TIB, 
		    OPT_ClassLoaderProxy.VM_Type_type, rhsTIB.copyU2U());
      OPT_RegisterOperand lhsElemTYPE = 
	InsertUnary(continueAt, ir, GET_TYPE_FROM_TIB, 
		    OPT_ClassLoaderProxy.VM_Type_type, lhsElemTIB.copyU2U());
      VM_Method target;
      if (compType.asArray().getInnermostElementType() == VM_Type.JavaLangObjectType)
        target = VM_Entrypoints.checkstorePossibleArrayOfPrimitiveMethod; 
      else 
        target = VM_Entrypoints.checkstoreNotArrayOfPrimitiveMethod;
      OPT_Instruction call = Call.create2(CALL, null, null,
					  OPT_MethodOperand.STATIC(target), 
					  lhsElemTYPE, rhsTYPE);
      continueAt.insertBefore(call);
      call.position = position;
      call.bcIndex = bcIndex;
      call = _callHelper(call, ir);
      return continueAt;
    } else {
      Call.mutate2(s, CALL, null, null, 
		   OPT_MethodOperand.STATIC(VM_Entrypoints.checkstoreMethod), 
		   StoreCheck.getGuard(s).copy(), StoreCheck.getClearRef(s), 
		   StoreCheck.getClearVal(s));
      call(s, ir);
      return s;
    }
  }

  /////////////////////////////////////////////////
  // NEW IMPLEMENTATION
  /////////////////////////////////////////////////
  /** 
   * Generate a value-producing dynamic type check.
   * This routine assumes that the CFG and code order are 
   * already correctly established.
   * This routine must either remove s or mutuate it.  
   * 
   * @param s        The OPT_Instruction that is to be replaced by 
   *                  a value producing type check
   * @param ir       The OPT_IR containing the instruction to be expanded.
   * @param RHSobj   The OPT_RegisterOperand containing the rhs object.
   * @param LHStype  The VM_Type to be tested against.
   * @param RHStib   The OPT_RegisterOperand containing the TIB of the rhs.
   * @param result   The OPT_RegisterOperand that the result of dynamic 
   *                 type check is to be stored in.
   * @return the opt instruction immediately before the 
   *         instruction to continue expansion.
   */
  private static OPT_Instruction generateValueProducingTypeCheck(OPT_Instruction s, 
								 OPT_IR ir, 
								 OPT_RegisterOperand RHSobj, 
								 VM_Type LHStype, 
								 OPT_RegisterOperand RHStib, 
								 OPT_RegisterOperand result) {
    if (VM.BuildForFastDynamicTypeCheck) {
      // Is LHStype a class?
      if (LHStype.isClassType()) {
	VM_Class LHSclass = LHStype.asClass();
	if (LHSclass.isResolved()) {
	  // Cases 4, 5, and 6 of VM_DynamicTypeCheck: LHSclass is a 
	  // resolved class or interface
	  if (LHSclass.isInterface()) {
	    // A resolved interface (case 4)
	    int LHSInterfaceId = LHSclass.getInterfaceId();
	    OPT_BasicBlock myBlock = s.getBasicBlock();
	    OPT_BasicBlock contBlock = myBlock.splitNodeAt(s, ir);
	    OPT_BasicBlock maybeBlock = myBlock.createSubBlock(s.bcIndex, ir);
	    myBlock.insertOut(contBlock);
	    myBlock.insertOut(maybeBlock);
	    maybeBlock.insertOut(contBlock);
	    ir.cfg.linkInCodeOrder(myBlock, contBlock);
	    maybeBlock.setInfrequent(true);
	    ir.cfg.addLastInCodeOrder(maybeBlock);
	    OPT_RegisterOperand trits = 
	      InsertUnary(s, ir,  GET_IMPLEMENTS_TRITS_FROM_TIB, 
			  OPT_ClassLoaderProxy.ByteArrayType, RHStib);
	    if (VM_DynamicTypeCheck.MIN_IMPLEMENTS_TRITS_SIZE <= LHSInterfaceId){
	      OPT_RegisterOperand tritsLength = 
		InsertGuardedUnary(s, ir, ARRAYLENGTH, VM_Type.IntType, 
				   trits, TG());
	      OPT_Instruction lengthCheck = 
		IfCmp.create(INT_IFCMP, null, 
			     tritsLength, I(LHSInterfaceId),
			     OPT_ConditionOperand.LESS_EQUAL(), 
			     maybeBlock.makeJumpTarget(),
			     OPT_BranchProfileOperand.unlikely());
	      s.insertBefore(lengthCheck);
	      OPT_BasicBlock oldBlock = myBlock;
	      myBlock = myBlock.splitNodeWithLinksAt(lengthCheck, ir);
	      oldBlock.insertOut(maybeBlock); // required due to splitNode!
	    }
	    // NOTE: Assuming that VM_OptDynamicTypeCheck.NO = 0, 
	    // YES = 1, MAYBE = 2.
	    s.insertBefore(Load.create(UBYTE_LOAD, result, trits.copyU2U(), 
				       I(LHSInterfaceId), 
				       new OPT_LocationOperand(VM_Type.ByteType),
				       TG()));
	    OPT_Instruction continueAt = 
	      IfCmp.create(INT_IFCMP, null,
			   result.copyD2U(), I(VM_DynamicTypeCheck.MAYBE),
			   OPT_ConditionOperand.EQUAL(), 
			   maybeBlock.makeJumpTarget(),
			   OPT_BranchProfileOperand.unlikely());
	    s.insertBefore(continueAt);
	    s.remove();
	    OPT_RegisterOperand LHSRuntimeClass = 
	      getVMType(maybeBlock.lastInstruction(), ir, LHSclass);
	    OPT_Instruction maybeCall = 
	      Call.create2(CALL, result.copyD2D(), null, 
			   OPT_MethodOperand.STATIC(VM_Entrypoints.initialInstanceOfInterfaceMethod), 
			   LHSRuntimeClass, RHStib.copyD2U());
	    maybeCall.copyPosition(s);
	    maybeBlock.appendInstruction(maybeCall);
	    maybeBlock.appendInstruction(Goto.create(GOTO, 
						     contBlock.makeJumpTarget()));
	    return continueAt;
	  } else {
	    // A resolved class (cases 5 and 6 in VM_DynamicTypeCheck)
	    if (LHSclass.isFinal()) {
	      // For a final class, we can do a PTR compare of 
	      // rhsTIB and the TIB of the class
	      OPT_RegisterOperand classTIB = getTIB(s, ir, LHSclass);
	      BooleanCmp.mutate(s, BOOLEAN_CMP, result, RHStib, classTIB, 
				OPT_ConditionOperand.EQUAL(),
				new OPT_BranchProfileOperand());
	      return s.prevInstructionInCodeOrder();
	    } else {
	      // Do the full blown case 5 or 6 typecheck.
	      int LHSDepth = LHSclass.getTypeDepth();
	      int LHSId = LHSclass.getDictionaryId();
	      OPT_RegisterOperand superclassIds = 
		InsertUnary(s, ir, GET_SUPERCLASS_IDS_FROM_TIB, 
			    OPT_ClassLoaderProxy.ShortArrayType, RHStib);
	      OPT_RegisterOperand refCandidate = 
		InsertLoadOffset(s, ir, SHORT_LOAD, VM_Type.ShortType, 
				 superclassIds, LHSDepth << 1, 
				 new OPT_LocationOperand(VM_Type.ShortType), 
				 TG());
	      s.insertBefore(BooleanCmp.create(BOOLEAN_CMP, result, 
					       refCandidate, 
					       I(LHSId), 
					       OPT_ConditionOperand.EQUAL(),
					       new OPT_BranchProfileOperand()));
	      if (VM_DynamicTypeCheck.MIN_SUPERCLASS_IDS_SIZE <= LHSDepth) {
		OPT_RegisterOperand superclassIdsLength = 
		  InsertGuardedUnary(s, ir, ARRAYLENGTH, VM_Type.IntType, 
				     superclassIds.copyD2U(), TG());
		OPT_RegisterOperand boundscheck = ir.regpool.makeTempInt();
		s.insertBefore(BooleanCmp.create(BOOLEAN_CMP, boundscheck, 
						 superclassIdsLength, 
						 I(LHSDepth), 
						 OPT_ConditionOperand.GREATER(),
						 new OPT_BranchProfileOperand()));
		s.insertBefore(Binary.create(INT_AND, result.copyD2D(), 
					     result.copyD2U(), boundscheck));
	      }
	      OPT_Instruction continueAt = s.prevInstructionInCodeOrder();
	      s.remove();
	      return continueAt;
	    }
	  }
	} else {
	  // A non-resolved class or interface. Case 3 of VM_DynamicTypeCheck.
	  // Mutate s into a call to VM_DynamicTypeCheck.instanceOfUnresolved
	  OPT_RegisterOperand LHSRuntimeClass = getVMType(s, ir, LHSclass);
	  Call.mutate2(s, CALL, result, null, 
		       OPT_MethodOperand.STATIC(VM_Entrypoints.instanceOfUnresolvedMethod), 
		       LHSRuntimeClass, RHStib);
	  s = _callHelper(s, ir);
	  return s;
	}
      }
      if (LHStype.isArrayType()) {
	// Case 2 of VM_DynamicTypeCheck: LHS is an array.
	VM_Array LHSArray = LHStype.asArray();
	VM_Type innermostElementType = LHSArray.getInnermostElementType();
	if (innermostElementType.isPrimitiveType() || 
	    (innermostElementType.asClass().isResolved() && 
	     innermostElementType.asClass().isFinal())) {
	  // [^k of primitive or [^k of final class. Just like final classes, 
	  // a PTR compare of rhsTIB and the TIB of the class gives the answer.
	  OPT_RegisterOperand classTIB = getTIB(s, ir, LHSArray);
	  BooleanCmp.mutate(s, BOOLEAN_CMP, result, RHStib, classTIB, 
			    OPT_ConditionOperand.EQUAL(),new OPT_BranchProfileOperand());
	  return s;
	}
	// We're going to have to branch anyways, so reduce to a branching case 
	// and do the real work there.
	return convertToBranchingTypeCheck(s, ir, RHSobj, LHStype, RHStib, result);
      }
    } else { // !BuildForFastDynamicTypeCheck
      return convertToBranchingTypeCheck(s, ir, RHSobj, LHStype, RHStib, result);
    }
    OPT_OptimizingCompilerException.UNREACHABLE();
    return null;
  }


  /**
   * Generate wrapper around branching type check to get a 
   * value producing type check. 
   * @param s        The OPT_Instruction that is to be replaced by 
   *                  a value producing type check
   * @param ir       The OPT_IR containing the instruction to be expanded.
   * @param RHSobj   The OPT_RegisterOperand containing the rhs object.
   * @param LHStype  The VM_Type to be tested against.
   * @param RHStib   The OPT_RegisterOperand containing the TIB of the rhs.
   * @param result   The OPT_RegisterOperand that the result of dynamic 
   * @return the opt instruction immediately before the instruction to 
   *         continue expansion.
   */
  private static OPT_Instruction convertToBranchingTypeCheck(OPT_Instruction s,
							     OPT_IR ir,
							     OPT_RegisterOperand RHSobj, 
							     VM_Type LHStype,
							     OPT_RegisterOperand RHStib,
							     OPT_RegisterOperand result) {
    OPT_BasicBlock myBlock = s.getBasicBlock();
    OPT_BasicBlock contBlock = myBlock.splitNodeAt(s, ir);
    OPT_BasicBlock trueBlock = myBlock.createSubBlock(s.bcIndex, ir);
    OPT_BasicBlock falseBlock = myBlock.createSubBlock(s.bcIndex, ir);
    myBlock.insertOut(trueBlock);
    myBlock.insertOut(falseBlock);
    trueBlock.insertOut(contBlock);
    falseBlock.insertOut(contBlock);
    ir.cfg.linkInCodeOrder(myBlock, trueBlock);
    ir.cfg.linkInCodeOrder(trueBlock, falseBlock);
    ir.cfg.linkInCodeOrder(falseBlock, contBlock);
    trueBlock.appendInstruction(Move.create(INT_MOVE, result, I(1)));
    trueBlock.appendInstruction(Goto.create(GOTO, 
					    contBlock.makeJumpTarget()));
    falseBlock.appendInstruction(Move.create(INT_MOVE, result.copyD2D(), 
					     I(0)));
    return generateBranchingTypeCheck(s, ir, RHSobj, LHStype, RHStib, trueBlock, 
				      falseBlock, null);
  }

  /** 
   * Generate a branching dynamic type check.
   * This routine assumes that the CFG and code order are already 
   * correctly established.
   * This routine must either remove s or mutuate it. 
   * 
   * @param s          The OPT_Instruction that is to be replaced by a 
   *                   branching type check
   * @param ir         The OPT_IR containing the instruction to be expanded.
   * @param RHSobj     The OPT_RegisterOperand containing the rhs object.
   * @param LHStype    The VM_Type to be tested against.
   * @param RHStib     The OPT_RegisterOperand containing the TIB of the rhs.
   * @param trueBlock  The OPT_BasicBlock to continue at if the typecheck 
   *                   evaluates to true
   * @param falseBlock The OPT_BasicBlock to continue at if the typecheck 
   *                   evaluates to false.
   * @return the opt instruction immediately before the instruction to 
   *         continue expansion.
   */
  private static OPT_Instruction generateBranchingTypeCheck(OPT_Instruction s, 
							    OPT_IR ir, 
							    OPT_RegisterOperand RHSobj,
							    VM_Type LHStype, 
							    OPT_RegisterOperand RHStib, 
							    OPT_BasicBlock trueBlock, 
							    OPT_BasicBlock falseBlock,
                                                            OPT_RegisterOperand 
oldGuard) {
    OPT_Instruction continueAt = Goto.create(GOTO, trueBlock.makeJumpTarget());
    continueAt.copyPosition(s);
    s.insertBefore(continueAt);
    s.remove();

    if (VM.BuildForFastDynamicTypeCheck) {
      if (LHStype.isClassType()) {
	VM_Class LHSclass = LHStype.asClass();
	if (LHSclass.isResolved()) {
	  // Cases 4, 5, and 6 of VM_DynamicTypeCheck: LHSclass is a resolved 
	  // class or interface
	  if (LHSclass.isInterface()) {
	    // A resolved interface (case 4)
	    int LHSInterfaceId = LHSclass.getInterfaceId();
	    OPT_BasicBlock myBlock = continueAt.getBasicBlock();
	    OPT_BasicBlock maybeBlock = 
	      myBlock.createSubBlock(continueAt.bcIndex, ir);
	    myBlock.insertOut(maybeBlock);
	    maybeBlock.insertOut(trueBlock);
	    maybeBlock.insertOut(falseBlock);
	    maybeBlock.setInfrequent(true);
	    ir.cfg.addLastInCodeOrder(maybeBlock);
	    OPT_RegisterOperand trits = 
	      InsertUnary(continueAt, ir, GET_IMPLEMENTS_TRITS_FROM_TIB, 
			  OPT_ClassLoaderProxy.ByteArrayType, RHStib);
	    if (VM_DynamicTypeCheck.MIN_IMPLEMENTS_TRITS_SIZE <= LHSInterfaceId){
	      OPT_RegisterOperand tritsLength = 
		InsertGuardedUnary(continueAt, ir, ARRAYLENGTH, VM_Type.IntType,
				   trits, TG());
	      OPT_Instruction lengthCheck = 
		IfCmp.create(INT_IFCMP, oldGuard, tritsLength, I(LHSInterfaceId),
			     OPT_ConditionOperand.LESS_EQUAL(), 
			     maybeBlock.makeJumpTarget(),
			     OPT_BranchProfileOperand.unlikely());
	      continueAt.insertBefore(lengthCheck);
	      OPT_BasicBlock oldBlock = myBlock;
	      myBlock = myBlock.splitNodeWithLinksAt(lengthCheck, ir);
	      oldBlock.insertOut(maybeBlock); // required due to splitNode!
	    }
	    // NOTE: Assuming that VM_DynamicTypeCheck.NO = 0, YES = 1, MAYBE = 2
	    OPT_RegisterOperand trit = 
	      InsertLoadOffset(continueAt, ir, UBYTE_LOAD, VM_Type.ByteType, 
			       trits.copyU2U(), LHSInterfaceId, 
			       new OPT_LocationOperand(VM_Type.ByteType), TG());
	    continueAt.insertBefore(IfCmp2.create(INT_IFCMP2, oldGuard, trit, 
						  I(VM_DynamicTypeCheck.YES),
						  OPT_ConditionOperand.GREATER(),
						  maybeBlock.makeJumpTarget(),
						  new OPT_BranchProfileOperand(),
						  OPT_ConditionOperand.LESS(), 
						  falseBlock.makeJumpTarget(),
						  new OPT_BranchProfileOperand()));
	    OPT_RegisterOperand LHSRuntimeClass = 
	      getVMType(maybeBlock.lastInstruction(), ir, LHSclass);
	    OPT_Instruction maybeCall = 
	      Call.create2(CALL, trit.copyD2D(), null, 
			   OPT_MethodOperand.STATIC(VM_Entrypoints.initialInstanceOfInterfaceMethod), 
			   LHSRuntimeClass, RHStib.copyD2U());
	    maybeCall.copyPosition(s);
	    maybeBlock.appendInstruction(maybeCall);
	    maybeBlock.appendInstruction(IfCmp.create(INT_IFCMP, oldGuard, 
						      trit.copyD2U(), I(0),
						      OPT_ConditionOperand.EQUAL(), 
						      falseBlock.makeJumpTarget(),
						      new OPT_BranchProfileOperand()));
	    maybeBlock.appendInstruction(Goto.create(GOTO, 
						     trueBlock.makeJumpTarget()));
	    return continueAt;
	  } else {
	    // A resolved class (cases 5 and 6 in VM_DynamicTypeCheck)
	    if (LHSclass.isFinal()) {
	      // For a final class, we can do a PTR compare of 
	      // rhsTIB and the TIB of the class
	      OPT_RegisterOperand classTIB = getTIB(continueAt, ir, LHSclass);
	      continueAt.insertBefore(IfCmp.create(INT_IFCMP, oldGuard, 
						   RHStib, classTIB,
						   OPT_ConditionOperand.NOT_EQUAL(), 
						   falseBlock.makeJumpTarget(),
						   new OPT_BranchProfileOperand()));
	      return continueAt;
	    } else {
	      // Do the full blown case 5 or 6 typecheck.
	      int LHSDepth = LHSclass.getTypeDepth();
	      int LHSId = LHSclass.getDictionaryId();
	      OPT_RegisterOperand superclassIds = 
		InsertUnary(continueAt, ir, GET_SUPERCLASS_IDS_FROM_TIB, 
			    OPT_ClassLoaderProxy.ShortArrayType, RHStib);
	      if (VM_DynamicTypeCheck.MIN_SUPERCLASS_IDS_SIZE <= LHSDepth) {
		OPT_RegisterOperand superclassIdsLength = 
		  InsertGuardedUnary(continueAt, 
				     ir, ARRAYLENGTH, VM_Type.IntType, 
				     superclassIds.copyD2U(), TG());
		OPT_Instruction lengthCheck = 
		  IfCmp.create(INT_IFCMP, oldGuard, superclassIdsLength, I(LHSDepth),
			       OPT_ConditionOperand.LESS(), 
			       falseBlock.makeJumpTarget(),
			       new OPT_BranchProfileOperand());
		continueAt.insertBefore(lengthCheck);
		OPT_BasicBlock oldBlock = continueAt.getBasicBlock();
		oldBlock.splitNodeWithLinksAt(lengthCheck, ir);
		oldBlock.insertOut(falseBlock); // required due to splitNode!
	      }
	      OPT_RegisterOperand refCandidate = 
		InsertLoadOffset(continueAt, ir, SHORT_LOAD, VM_Type.ShortType,
				 superclassIds, LHSDepth << 1, 
				 new OPT_LocationOperand(VM_Type.ShortType), 
				 TG());
	      continueAt.insertBefore(IfCmp.create(INT_IFCMP, oldGuard, 
						   refCandidate, I(LHSId),
						   OPT_ConditionOperand.NOT_EQUAL(), 
						   falseBlock.makeJumpTarget(),
						   new OPT_BranchProfileOperand()));
	      return continueAt;
	    }
	  }
	} else {
	  // A non-resolved class or interface. Case 3 of VM_DynamicTypeCheck
	  // Branch on the result of a call to 
	  // VM_DynamicTypeCheck.instanceOfUnresolved
	  OPT_RegisterOperand LHSRuntimeClass = 
	    getVMType(continueAt, ir, LHSclass);
	  OPT_RegisterOperand result = ir.regpool.makeTempInt();
	  OPT_Instruction call = Call.create2(CALL, result, null, 
					      OPT_MethodOperand.STATIC(VM_Entrypoints.instanceOfUnresolvedMethod), 
					      LHSRuntimeClass, RHStib);
	  call.copyPosition(continueAt);
	  continueAt.insertBefore(call);
	  call = _callHelper(call, ir);
	  continueAt.insertBefore(IfCmp.create(INT_IFCMP, oldGuard, 
					       result.copyD2U(), I(0),
					       OPT_ConditionOperand.EQUAL(), 
					       falseBlock.makeJumpTarget(),
					       new OPT_BranchProfileOperand()));
	  return continueAt;
	}
      }
      if (LHStype.isArrayType()) {
	// Case 2 of VM_DynamicTypeCheck: LHS is an array.
	VM_Array LHSArray = LHStype.asArray();
	OPT_RegisterOperand classTIB = getTIB(continueAt, ir, LHSArray);
	VM_Type innermostElementType = LHSArray.getInnermostElementType();
	if (innermostElementType.isPrimitiveType() || 
	    (innermostElementType.asClass().isResolved() && 
	     innermostElementType.asClass().isFinal())) {
	  // [^k of primitive or [^k of final class. Just like final classes, 
	  // a PTR compare of rhsTIB and the TIB of the class gives the answer.
	  continueAt.insertBefore(IfCmp.create(REF_IFCMP, oldGuard, 
					       RHStib, classTIB,
					       OPT_ConditionOperand.NOT_EQUAL(), 
					       falseBlock.makeJumpTarget(),
					       new OPT_BranchProfileOperand()));
	  return continueAt;
	}
	OPT_Instruction shortcircuit = 
	  IfCmp.create(REF_IFCMP, oldGuard, RHStib, classTIB,
		       OPT_ConditionOperand.EQUAL(), 
		       trueBlock.makeJumpTarget(),
		       new OPT_BranchProfileOperand());
	continueAt.insertBefore(shortcircuit);
	OPT_BasicBlock myBlock = shortcircuit.getBasicBlock();
	OPT_BasicBlock mainBlock = 
	  myBlock.splitNodeWithLinksAt(shortcircuit, ir);
	myBlock.insertOut(trueBlock);       // must come after the splitNodeAt
	OPT_Instruction call;
	OPT_RegisterOperand rhsType = 
	  InsertUnary(continueAt, ir, GET_TYPE_FROM_TIB, 
		      OPT_ClassLoaderProxy.VM_Type_type, RHStib.copyD2U());
	OPT_RegisterOperand callResult = ir.regpool.makeTempInt();
	if (innermostElementType == VM_Type.JavaLangObjectType) {
	  OPT_IntConstantOperand lhsDimension = I(LHSArray.getDimensionality());
	  OPT_RegisterOperand rhsDimension = 
	    getField(continueAt, ir, rhsType, VM_Entrypoints.dimensionField);
	  OPT_Instruction dimTest = 
	    IfCmp2.create(INT_IFCMP2, oldGuard, rhsDimension, lhsDimension,
			  OPT_ConditionOperand.GREATER(), 
			  trueBlock.makeJumpTarget(),
			  new OPT_BranchProfileOperand(),
			  OPT_ConditionOperand.LESS(), 
			  falseBlock.makeJumpTarget(),
			  new OPT_BranchProfileOperand());
	  continueAt.insertBefore(dimTest);
	  OPT_BasicBlock testBlock = 
	    mainBlock.splitNodeWithLinksAt(dimTest, ir);
	  mainBlock.insertOut(trueBlock);
	  mainBlock.insertOut(falseBlock);
	  OPT_RegisterOperand rhsInnermostElementType = 
	    getField(continueAt,ir,rhsType.copyU2U(),VM_Entrypoints.innermostElementTypeField);
	  OPT_RegisterOperand rhsInnermostElementTypeDimension = 
	    getField(continueAt, ir, rhsInnermostElementType, VM_Entrypoints.dimensionField);
	  continueAt.insertBefore(IfCmp.create(INT_IFCMP, oldGuard, 
					       rhsInnermostElementTypeDimension,
					       I(0),
					       OPT_ConditionOperand.NOT_EQUAL(), 
					       falseBlock.makeJumpTarget(),
					       new OPT_BranchProfileOperand()));
	  return continueAt;
	} else {
	  OPT_RegisterOperand lhsInnermostElementType = 
	    getVMType(continueAt, ir, innermostElementType);
	  VM_Method target = 
	    innermostElementType.isResolved() ? VM_Entrypoints.instanceOfArrayMethod : 
	    VM_Entrypoints.instanceOfUnresolvedArrayMethod;
	  call = Call.create3(CALL, callResult, null, 
			      OPT_MethodOperand.STATIC(target), 
			      lhsInnermostElementType, 
			      I(LHSArray.getDimensionality()), 
			      rhsType);
	  call.copyPosition(continueAt);
	  continueAt.insertBefore(call);
	  call = _callHelper(call, ir);
	  continueAt.insertBefore(IfCmp.create(INT_IFCMP, oldGuard, 
					       callResult.copyD2U(), I(0),
					       OPT_ConditionOperand.EQUAL(), 
					       falseBlock.makeJumpTarget(),
					       new OPT_BranchProfileOperand()));
	  return continueAt;
	}
      }
    } else { // !VM.BuildForFastDynamicTypeCheck
      OPT_BasicBlock myBlock = continueAt.getBasicBlock();
      OPT_BasicBlock callBlock = myBlock.splitNodeAt(continueAt.prevInstructionInCodeOrder(), ir);
      OPT_BasicBlock cacheBlock = myBlock.createSubBlock(continueAt.bcIndex, ir);

      myBlock.insertOut(cacheBlock);
      myBlock.insertOut(trueBlock);
      cacheBlock.insertOut(trueBlock);
      cacheBlock.insertOut(callBlock);
      ir.cfg.linkInCodeOrder(myBlock, cacheBlock);
      ir.cfg.linkInCodeOrder(cacheBlock, callBlock);

      if (LHStype.isResolved()) { 
	// type equality test
	OPT_Instruction t = myBlock.lastInstruction();
	OPT_RegisterOperand LHStib = getTIB(t, ir, LHStype);
	t.insertBefore(IfCmp.create(REF_IFCMP, oldGuard, 
				    RHStib, 
				    LHStib.copyD2U(),
				    OPT_ConditionOperand.EQUAL(),
				    trueBlock.makeJumpTarget(),
				    new OPT_BranchProfileOperand()));

	// cache check
	t = cacheBlock.lastInstruction();
	OPT_RegisterOperand cacheEntry = 
	  InsertLoadOffset(t, ir, REF_LOAD,
			   OPT_ClassLoaderProxy.JavaLangObjectArrayType,
			   RHStib.copyD2U(),
			   TIB_TYPE_CACHE_TIB_INDEX << 2);
	t.insertBefore(IfCmp.create(REF_IFCMP, oldGuard, 
				    cacheEntry,
				    LHStib.copyD2U(),
				    OPT_ConditionOperand.EQUAL(),
				    trueBlock.makeJumpTarget(),
				    new OPT_BranchProfileOperand()));
      }
      
      // call general out-of-line type checking routine.
      OPT_RegisterOperand result = ir.regpool.makeTempInt();
      OPT_Instruction call = Call.create2(CALL, result, null, 
					  OPT_MethodOperand.STATIC(VM_Entrypoints.instanceOfMethod), 
					  RHSobj.copyU2U(), I(LHStype.getTibOffset()));
      call.copyPosition(continueAt);
      continueAt.insertBefore(call);
      call = _callHelper(call, ir);
      continueAt.insertBefore(IfCmp.create(INT_IFCMP, oldGuard, 
					   result.copyD2U(), I(0),
					   OPT_ConditionOperand.EQUAL(), 
					   falseBlock.makeJumpTarget(),
					   new OPT_BranchProfileOperand()));
      return continueAt;
    }
    OPT_OptimizingCompilerException.UNREACHABLE();
    return null;
  }

  // helper routine.
  // s is a conditional branch; Make it the last instruction in its block
  // if it isn't already and return the fallthrough block.
  private static OPT_BasicBlock fallThroughBB (OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction next = s.nextInstructionInCodeOrder();
    if (next.operator() == BBEND) {
      return next.getBasicBlock().nextBasicBlockInCodeOrder();
    } else if (next.operator() == GOTO) {
      OPT_BasicBlock target = next.getBranchTarget();
      next.remove();
      return target;
    } else {
      OPT_BasicBlock myBlock = s.getBasicBlock();
      OPT_BasicBlock succBlock = myBlock.splitNodeAt(s, ir);
      myBlock.insertOut(succBlock);
      ir.cfg.linkInCodeOrder(myBlock, succBlock);
      return succBlock;
    }
  }

}
