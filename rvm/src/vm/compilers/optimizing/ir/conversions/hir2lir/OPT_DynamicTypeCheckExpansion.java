/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import org.vmmagic.unboxed.Address;
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

  /**
   * Expand an instanceof instruction into the LIR sequence that implements
   * the dynamic type check.  Ref may contain a null ptr at runtime.
   * 
   * @param s an INSTANCEOF or INSTANCEOF_UNRESOLVED instruction to expand 
   * @param ir the enclosing OPT_IR
   * @return the last OPT_Instruction in the generated LIR sequence.
   */
  static OPT_Instruction instanceOf(OPT_Instruction s, OPT_IR ir) {
    OPT_RegisterOperand result = InstanceOf.getClearResult(s);
    VM_TypeReference LHStype = InstanceOf.getType(s).getTypeRef();
    OPT_Operand ref = InstanceOf.getClearRef(s);
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
      // See defect 2114.
      OPT_Operand val2 = IfCmp.getVal2(next);
      if (VM.VerifyAssertions) VM._assert(val2.isIntConstant());
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
      OPT_BranchProfileOperand bp = IfCmp.getClearBranchProfile(next);
      if (branchCondition) bp = bp.flip();
      OPT_Instruction nullComp = 
        IfCmp.create(REF_IFCMP, oldGuard.copyRO(), ref.copy(), 
                     new OPT_NullConstantOperand(),
                     OPT_ConditionOperand.EQUAL(), 
                     falseBranch.makeJumpTarget(),
                     OPT_BranchProfileOperand.unlikely());
      s.insertBefore(nullComp);
      OPT_BasicBlock myBlock = s.getBasicBlock();
      OPT_BasicBlock instanceOfBlock = myBlock.splitNodeAt(nullComp, ir);
      myBlock.insertOut(instanceOfBlock);
      myBlock.insertOut(falseBranch);
      ir.cfg.linkInCodeOrder(myBlock, instanceOfBlock);
      OPT_RegisterOperand RHStib = getTIB(s, ir, ref, oldGuard.copyRO());
      return generateBranchingTypeCheck(s, ir, ref, LHStype, RHStib, 
                                        trueBranch, falseBranch, oldGuard, bp);
    } else {
      // Not a branching pattern
      OPT_RegisterOperand guard = ir.regpool.makeTempValidation();
      OPT_BasicBlock instanceOfBlock = 
        s.getBasicBlock().segregateInstruction(s, ir);
      OPT_BasicBlock prevBB = instanceOfBlock.prevBasicBlockInCodeOrder();
      OPT_BasicBlock nextBB = instanceOfBlock.nextBasicBlockInCodeOrder();
      OPT_BasicBlock nullCaseBB = 
        instanceOfBlock.createSubBlock(s.bcIndex, ir, .01f);
      prevBB.appendInstruction(IfCmp.create(REF_IFCMP, guard, 
                                            ref.copy(), 
                                            new OPT_NullConstantOperand(),
                                            OPT_ConditionOperand.EQUAL(), 
                                            nullCaseBB.makeJumpTarget(),
                                            OPT_BranchProfileOperand.unlikely()));
      nullCaseBB.appendInstruction(Move.create(INT_MOVE, result.copyD2D(), IC(0)));
      nullCaseBB.appendInstruction(Goto.create(GOTO, nextBB.makeJumpTarget()));
      // Stitch together the CFG; add nullCaseBB to the end of code array.
      prevBB.insertOut(nullCaseBB);
      nullCaseBB.insertOut(nextBB);
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
  static OPT_Instruction instanceOfNotNull(OPT_Instruction s, OPT_IR ir) {
    OPT_RegisterOperand result = InstanceOf.getClearResult(s);
    VM_TypeReference LHStype = InstanceOf.getType(s).getTypeRef();
    OPT_Operand ref = InstanceOf.getClearRef(s);
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
        VM._assert(val2.isIntConstant());
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
                                          fallThroughBB, oldGuard,
					  IfCmp.getClearBranchProfile(next).flip());
      } else {
        return generateBranchingTypeCheck(s, ir, ref, LHStype, RHStib, 
                                          fallThroughBB, branchBB, oldGuard,
					  IfCmp.getClearBranchProfile(next));
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
   * @param s a CHECKCAST or CHECKCAST_UNRESOLVED instruction to expand 
   * @param ir the enclosing OPT_IR
   * @return the last OPT_Instruction in the generated LIR sequence.
   */
  static OPT_Instruction checkcast(OPT_Instruction s, OPT_IR ir) {
    OPT_Operand ref = TypeCheck.getClearRef(s);
    VM_TypeReference LHStype = TypeCheck.getType(s).getTypeRef();
    OPT_RegisterOperand guard = ir.regpool.makeTempValidation();
    OPT_Instruction nullCond = 
      IfCmp.create(REF_IFCMP, guard, ref.copy(), 
                   new OPT_NullConstantOperand(),
                   OPT_ConditionOperand.EQUAL(), 
                   null, // KLUDGE...we haven't created the block yet!
                   new OPT_BranchProfileOperand());
    s.insertBefore(nullCond);
    OPT_BasicBlock myBlock = s.getBasicBlock();
    OPT_BasicBlock failBlock = myBlock.createSubBlock(s.bcIndex, ir, .0001f);
    OPT_BasicBlock instanceOfBlock = myBlock.splitNodeAt(nullCond, ir);
    OPT_BasicBlock succBlock = instanceOfBlock.splitNodeAt(s, ir);
    IfCmp.setTarget(nullCond, succBlock.makeJumpTarget()); // fixup KLUDGE
    myBlock.insertOut(instanceOfBlock);
    myBlock.insertOut(succBlock);
    instanceOfBlock.insertOut(failBlock);
    instanceOfBlock.insertOut(succBlock);
    ir.cfg.linkInCodeOrder(myBlock, instanceOfBlock);
    ir.cfg.linkInCodeOrder(instanceOfBlock, succBlock);
    ir.cfg.addLastInCodeOrder(failBlock);
    OPT_Instruction raiseError = 
      Trap.create(TRAP, null, OPT_TrapCodeOperand.CheckCast());
    raiseError.copyPosition(s);
    failBlock.appendInstruction(raiseError);
    OPT_RegisterOperand RHStib = getTIB(s, ir, ref, guard.copyD2U());
    return generateBranchingTypeCheck(s, ir, ref, LHStype, RHStib, succBlock, 
                                      failBlock, null, OPT_BranchProfileOperand.never());
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
  static OPT_Instruction checkcastNotNull(OPT_Instruction s, OPT_IR ir) {
    OPT_Operand ref = TypeCheck.getClearRef(s);
    VM_TypeReference LHStype = TypeCheck.getType(s).getTypeRef();
    OPT_Operand guard = TypeCheck.getClearGuard(s);
    OPT_BasicBlock myBlock = s.getBasicBlock();
    OPT_BasicBlock failBlock = myBlock.createSubBlock(s.bcIndex, ir, .0001f);
    OPT_BasicBlock succBlock = myBlock.splitNodeAt(s, ir);
    myBlock.insertOut(failBlock);
    myBlock.insertOut(succBlock);
    ir.cfg.linkInCodeOrder(myBlock, succBlock);
    ir.cfg.addLastInCodeOrder(failBlock);
    OPT_Instruction raiseError = 
      Trap.create(TRAP, null, OPT_TrapCodeOperand.CheckCast());
    raiseError.copyPosition(s);
    failBlock.appendInstruction(raiseError);
    OPT_RegisterOperand RHStib = getTIB(s, ir, ref, guard);
    return generateBranchingTypeCheck(s, ir, ref, LHStype, RHStib, succBlock, 
                                      failBlock, null, OPT_BranchProfileOperand.never());
  }


  /**
   * Expand a checkcastInterface instruction into the LIR sequence that 
   * implements the dynamic type check, raising an IncompataibleClassChangeError
   * if the type check fails. 
   * Ref is known to never contain a null ptr at runtime.
   *
   * @param s a MUST_IMPLEMENT_INTERFACE instruction to expand 
   * @param ir the enclosing OPT_IR
   * @return the last OPT_Instruction in the generated LIR sequence.
   */
  static OPT_Instruction mustImplementInterface(OPT_Instruction s, OPT_IR ir) {
    OPT_Operand ref = TypeCheck.getClearRef(s);
    VM_Class LHSClass = (VM_Class)TypeCheck.getType(s).getVMType();
    if (VM.VerifyAssertions) VM._assert(LHSClass != null, "Should be resolvable...");
    int interfaceIndex = LHSClass.getDoesImplementIndex();
    int interfaceMask = LHSClass.getDoesImplementBitMask();
    OPT_Operand guard = TypeCheck.getClearGuard(s);
    OPT_BasicBlock myBlock = s.getBasicBlock();
    OPT_BasicBlock failBlock = myBlock.createSubBlock(s.bcIndex, ir, .0001f);
    OPT_BasicBlock succBlock = myBlock.splitNodeAt(s, ir);
    myBlock.insertOut(failBlock);
    myBlock.insertOut(succBlock);
    ir.cfg.linkInCodeOrder(myBlock, succBlock);
    ir.cfg.addLastInCodeOrder(failBlock);
    OPT_Instruction raiseError = 
      Trap.create(TRAP, null, OPT_TrapCodeOperand.MustImplement());
    raiseError.copyPosition(s);
    failBlock.appendInstruction(raiseError);
    
    OPT_RegisterOperand RHStib = getTIB(s, ir, ref, guard);
    OPT_RegisterOperand doesImpl = 
      InsertUnary(s, ir, GET_DOES_IMPLEMENT_FROM_TIB, 
                  VM_TypeReference.IntArray, RHStib);

    if (VM_DynamicTypeCheck.MIN_DOES_IMPLEMENT_SIZE <= interfaceIndex) {
      OPT_RegisterOperand doesImplLength = 
        InsertGuardedUnary(s, ir, ARRAYLENGTH, VM_TypeReference.Int, 
                           doesImpl.copyD2U(), TG());
      OPT_Instruction lengthCheck = 
        IfCmp.create(INT_IFCMP, null, doesImplLength, IC(interfaceIndex),
                     OPT_ConditionOperand.LESS_EQUAL(), 
                     failBlock.makeJumpTarget(),
                     OPT_BranchProfileOperand.never());
      s.insertBefore(lengthCheck);
      myBlock.splitNodeWithLinksAt(lengthCheck, ir);
      myBlock.insertOut(failBlock); // required due to splitNode!
    }
    OPT_RegisterOperand entry = 
      InsertLoadOffset(s, ir, INT_LOAD, VM_TypeReference.Int,
                       doesImpl, interfaceIndex << 2, 
                       new OPT_LocationOperand(VM_TypeReference.Int), 
                       TG());
    OPT_RegisterOperand bit = InsertBinary(s, ir, INT_AND, VM_TypeReference.Int, entry, IC(interfaceMask));
    IfCmp.mutate(s, INT_IFCMP, null, bit, IC(0),
                 OPT_ConditionOperand.EQUAL(), 
                 failBlock.makeJumpTarget(),
                 OPT_BranchProfileOperand.never());
    return s;
  }


  /**
   * Expand an object array store check into the LIR sequence that 
   * implements it.
   *
   * @param s an OBJARRAY_STORE_CHECK instruction to expand 
   * @param ir the enclosing OPT_IR
   * @param couldBeNull is it possible that the element being stored is null?
   * @return the last OPT_Instruction in the generated LIR sequence.
   */
  static OPT_Instruction arrayStoreCheck(OPT_Instruction s, OPT_IR ir, boolean couldBeNull) {
    OPT_RegisterOperand guardResult = StoreCheck.getClearGuardResult(s);
    OPT_Operand arrayRef = StoreCheck.getClearRef(s);
    OPT_Operand elemRef = StoreCheck.getClearVal(s);
    OPT_Operand guard = StoreCheck.getClearGuard(s);
    if (elemRef instanceof OPT_NullConstantOperand) {
      OPT_Instruction continueAt = s.prevInstructionInCodeOrder();
      s.remove();
      return continueAt;
    }
    OPT_BasicBlock myBlock = s.getBasicBlock();
    OPT_BasicBlock contBlock = myBlock.splitNodeAt(s, ir);
    OPT_BasicBlock trapBlock = myBlock.createSubBlock(s.bcIndex, ir, .0001f);
    OPT_BasicBlock curBlock = myBlock;
    s.remove();

      // Set up a block with a trap instruction that we can jump to if the 
      // store check fails
    OPT_Instruction trap = Trap.create(TRAP, null, OPT_TrapCodeOperand.StoreCheck());
    trap.copyPosition(s);
    trapBlock.appendInstruction(trap);
    ir.cfg.addLastInCodeOrder(trapBlock);

    OPT_Operand rhsGuard = guard;
    if (couldBeNull) {
      // if rhs is null, then the checkcast succeeds
      rhsGuard = ir.regpool.makeTempValidation();
      contBlock.prependInstruction(Binary.create(GUARD_COMBINE, 
                                                 guardResult, 
                                                 guardResult.copyRO(), 
                                                 rhsGuard.copy()));
      curBlock.appendInstruction(IfCmp.create(REF_IFCMP, rhsGuard.asRegister(), 
                                              elemRef, 
                                              new OPT_NullConstantOperand(),
                                              OPT_ConditionOperand.EQUAL(), 
                                              contBlock.makeJumpTarget(), 
                                              new OPT_BranchProfileOperand()));
      curBlock.insertOut(contBlock);
      curBlock = advanceBlock(s.bcIndex, curBlock, ir);
    }
          
    // Find out what we think the compile time type of the lhs is.
    // Based on this, we can do one of several things:
    //  (1) If the compile time element type is a final proper class, then a 
    //      TIB comparision of the runtime elemRef type and the 
    //      compile time element type is definitive.
    //  (2) If the compile time type is known to be the declared type,
    //      then inject a short-circuit test to see if the 
    //      runtime lhs type is the same as the compile-time lhs type.
    //  (3) If the compile time element type is a proper class other than 
    //      java.lang.Object, then a subclass test of the runtime LHS elem type 
    //      and the runtime elemRef type is definitive.  Note: we must exclude
    //      java.lang.Object because if the compile time element type is 
    //      java.lang.Object, then the runtime-element type might actually be 
    //      an interface (ie not a proper class), and we won't be testing the right thing!
    // If we think the compile time type is JavaLangObjectType then
    // we lost type information due to unloaded classes causing
    // imprecise meets.  This should only happen once in a blue moon,
    // so don't bother trying anything clever when it does.
    VM_Type compType = arrayRef.getType().peekResolvedType();
    if (compType != null && !compType.isJavaLangObjectType()) {
      // optionally (1) from above
      if (compType.getDimensionality() == 1) {
        VM_Class etc = (VM_Class)compType.asArray().getElementType();
        if (etc.isResolved() && etc.isFinal()) {
          if (VM.VerifyAssertions) VM._assert(!etc.isInterface());
          OPT_RegisterOperand rhsTIB = getTIB(curBlock.lastInstruction(), ir, elemRef.copy(), rhsGuard.copy());
          OPT_Operand etTIB = getTIB(curBlock.lastInstruction(), ir, etc);
          curBlock.appendInstruction(IfCmp.create(REF_IFCMP, guardResult.copyRO(), 
                                                  rhsTIB, etTIB,
                                                  OPT_ConditionOperand.NOT_EQUAL(), 
                                                  trapBlock.makeJumpTarget(),
                                                  OPT_BranchProfileOperand.never()));
          curBlock.insertOut(trapBlock);
          curBlock.insertOut(contBlock);
          ir.cfg.linkInCodeOrder(curBlock, contBlock);
          return curBlock.lastInstruction();
        }
      }

      // optionally (2) from above
      OPT_RegisterOperand lhsTIB = getTIB(curBlock.lastInstruction(), ir, arrayRef, guard);
      if (((arrayRef instanceof OPT_RegisterOperand)&&((OPT_RegisterOperand)arrayRef).isDeclaredType()) || compType == VM_Type.JavaLangObjectArrayType) {
        OPT_Operand declTIB = getTIB(curBlock.lastInstruction(), ir, compType);
        curBlock.appendInstruction(IfCmp.create(REF_IFCMP, guardResult.copyRO(), 
                                                declTIB, lhsTIB,
                                                OPT_ConditionOperand.EQUAL(), 
                                                contBlock.makeJumpTarget(),
                                                new OPT_BranchProfileOperand()));
        curBlock.insertOut(contBlock);
        curBlock = advanceBlock(s.bcIndex, curBlock, ir);
      }

      // On our way to doing (3) from above attempt another short-circuit.
      // If lhsElemTIB == rhsTIB, then we are done.
      OPT_RegisterOperand rhsTIB = getTIB(curBlock.lastInstruction(), ir, elemRef.copy(), rhsGuard.copy());
      OPT_RegisterOperand lhsElemTIB = 
        InsertUnary(curBlock.lastInstruction(), ir, GET_ARRAY_ELEMENT_TIB_FROM_TIB, 
                    VM_TypeReference.JavaLangObjectArray, 
                    lhsTIB.copyRO());
      curBlock.appendInstruction(IfCmp.create(REF_IFCMP, guardResult.copyRO(), 
                                              rhsTIB, lhsElemTIB,
                                              OPT_ConditionOperand.EQUAL(), 
                                              contBlock.makeJumpTarget(),
                                              new OPT_BranchProfileOperand()));
      curBlock.insertOut(contBlock);
      curBlock = advanceBlock(s.bcIndex, curBlock, ir);

      // Optionally (3) from above 
      if (compType.getDimensionality() == 1) {
        VM_Class etc = (VM_Class)compType.asArray().getElementType();
        if (etc.isResolved() && !etc.isInterface() && !etc.isJavaLangObjectType()) {
          OPT_RegisterOperand lhsElemType = 
            InsertUnary(curBlock.lastInstruction(), ir, 
                        GET_TYPE_FROM_TIB, VM_TypeReference.VM_Type, 
                        lhsElemTIB.copyU2U());
          OPT_RegisterOperand rhsSuperclassIds = 
            InsertUnary(curBlock.lastInstruction(), ir, GET_SUPERCLASS_IDS_FROM_TIB, 
                        VM_TypeReference.ShortArray, rhsTIB.copyD2U());
          OPT_RegisterOperand lhsElemDepth = 
            getField(curBlock.lastInstruction(), ir, lhsElemType, VM_Entrypoints.depthField, TG());
          OPT_RegisterOperand rhsSuperclassIdsLength = 
            InsertGuardedUnary(curBlock.lastInstruction(), ir, 
                               ARRAYLENGTH, VM_TypeReference.Int,
                               rhsSuperclassIds.copyD2U(), TG());
          curBlock.appendInstruction(IfCmp.create(INT_IFCMP, guardResult.copyRO(), 
                                                  lhsElemDepth, 
                                                  rhsSuperclassIdsLength,
                                                  OPT_ConditionOperand.GREATER_EQUAL(), 
                                                  trapBlock.makeJumpTarget(),
                                                  OPT_BranchProfileOperand.never()));
          curBlock.insertOut(trapBlock);
          curBlock = advanceBlock(s.bcIndex, curBlock, ir);

          OPT_RegisterOperand lhsElemId = 
            getField(curBlock.lastInstruction(), ir, lhsElemType.copyD2U(), VM_Entrypoints.idField, TG());
          OPT_RegisterOperand refCandidate = ir.regpool.makeTemp(VM_TypeReference.Short);
          OPT_LocationOperand loc = new OPT_LocationOperand(VM_TypeReference.Short);
          if (LOWER_ARRAY_ACCESS) {
            OPT_RegisterOperand lhsDepthOffset = 
              InsertBinary(curBlock.lastInstruction(), ir, INT_SHL, VM_TypeReference.Int,
                           lhsElemDepth.copyD2U(), IC(1));
            curBlock.appendInstruction(Load.create(USHORT_LOAD, refCandidate, 
                                                   rhsSuperclassIds, 
                                                   lhsDepthOffset, loc, TG()));
          } else {
            curBlock.appendInstruction(ALoad.create(USHORT_ALOAD, refCandidate, 
                                                    rhsSuperclassIds, 
                                                    lhsElemDepth, loc, TG()));
          }
          curBlock.appendInstruction(IfCmp.create(INT_IFCMP, guardResult.copyRO(),
                                                  refCandidate.copyD2U(), 
                                                  lhsElemId,
                                                  OPT_ConditionOperand.NOT_EQUAL(), 
                                                  trapBlock.makeJumpTarget(),
                                                  OPT_BranchProfileOperand.never()));
          curBlock.insertOut(trapBlock);
          curBlock.insertOut(contBlock);
          ir.cfg.linkInCodeOrder(curBlock, contBlock);
          return curBlock.lastInstruction();
        }
      }
    }

    // Call VM_Runtime.checkstore.
    VM_Method target = VM_Entrypoints.checkstoreMethod;
    OPT_Instruction call = Call.create2(CALL, null, IC(target.getOffset()),
                                        OPT_MethodOperand.STATIC(target),
                                        rhsGuard.copy(), arrayRef.copy(), elemRef.copy());
    call.copyPosition(s);
    curBlock.appendInstruction(call);
    curBlock.insertOut(contBlock);
    ir.cfg.linkInCodeOrder(curBlock, contBlock);
    return callHelper(call, ir);
  }


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
                                                                 OPT_Operand RHSobj, 
                                                                 VM_TypeReference LHStype, 
                                                                 OPT_RegisterOperand RHStib, 
                                                                 OPT_RegisterOperand result) {
    // Is LHStype a class?
    if (LHStype.isClassType()) {
      VM_Class LHSclass = (VM_Class)LHStype.peekResolvedType();
      if (LHSclass != null && LHSclass.isResolved()) {
        // Cases 4, 5, and 6 of VM_DynamicTypeCheck: LHSclass is a 
        // resolved class or interface
        if (LHSclass.isInterface()) {
          // A resolved interface (case 4)
          int interfaceIndex = LHSclass.getDoesImplementIndex();
          int interfaceMask = LHSclass.getDoesImplementBitMask();
          OPT_RegisterOperand doesImpl = 
            InsertUnary(s, ir,  GET_DOES_IMPLEMENT_FROM_TIB, 
                        VM_TypeReference.IntArray, RHStib);
          OPT_RegisterOperand entry = 
            InsertLoadOffset(s, ir, INT_LOAD, VM_TypeReference.Int, 
                             doesImpl, interfaceIndex << 2, 
                             new OPT_LocationOperand(VM_TypeReference.Int), 
                             TG());
          OPT_RegisterOperand bit = InsertBinary(s, ir, INT_AND, VM_TypeReference.Int,
                                                 entry, IC(interfaceMask));
			 //save to use the cheaper ADDR version of BOOLEAN_CMP
          s.insertBefore(BooleanCmp.create(BOOLEAN_CMP_ADDR, result, 
                                           bit,
                                           AC(Address.zero()),
                                           OPT_ConditionOperand.NOT_EQUAL(),
                                           new OPT_BranchProfileOperand()));

          if (VM_DynamicTypeCheck.MIN_DOES_IMPLEMENT_SIZE <= interfaceIndex) {
            OPT_RegisterOperand doesImplLength = 
              InsertGuardedUnary(s, ir, ARRAYLENGTH, VM_TypeReference.Int, doesImpl.copy(), TG());
            OPT_RegisterOperand boundscheck = ir.regpool.makeTempInt();
			   //save to use the cheaper ADDR version of BOOLEAN_CMP
            s.insertBefore(BooleanCmp.create(BOOLEAN_CMP_ADDR, boundscheck, 
                                             doesImplLength,
                                             AC(Address.fromIntSignExtend(interfaceIndex)),
                                             OPT_ConditionOperand.GREATER(),
                                             new OPT_BranchProfileOperand()));
            s.insertBefore(Binary.create(INT_AND, result.copyD2D(), 
                                         result.copyD2U(), boundscheck));
          }
          OPT_Instruction continueAt = s.prevInstructionInCodeOrder();
          s.remove();
          return continueAt;
        } else {
          // A resolved class (cases 5 and 6 in VM_DynamicTypeCheck)
          if (LHSclass.isFinal()) {
            // For a final class, we can do a PTR compare of 
            // rhsTIB and the TIB of the class
            OPT_Operand classTIB = getTIB(s, ir, LHSclass);
            BooleanCmp.mutate(s, BOOLEAN_CMP_ADDR, result, RHStib, classTIB, 
                              OPT_ConditionOperand.EQUAL(),
                              new OPT_BranchProfileOperand());
            return s.prevInstructionInCodeOrder();
          } else {
            // Do the full blown case 5 or 6 typecheck.
            int LHSDepth = LHSclass.getTypeDepth();
            int LHSId = LHSclass.getId();
            OPT_RegisterOperand superclassIds = 
              InsertUnary(s, ir, GET_SUPERCLASS_IDS_FROM_TIB, 
                          VM_TypeReference.ShortArray, RHStib);
            OPT_RegisterOperand refCandidate = 
              InsertLoadOffset(s, ir, USHORT_LOAD, VM_TypeReference.Short, 
                               superclassIds, LHSDepth << 1, 
                               new OPT_LocationOperand(VM_TypeReference.Short), 
                               TG());
			   //save to use the cheaper ADDR version of BOOLEAN_CMP
            s.insertBefore(BooleanCmp.create(BOOLEAN_CMP_ADDR, result, 
                                             refCandidate, 
                                             AC(Address.fromIntZeroExtend(LHSId)), 
                                             OPT_ConditionOperand.EQUAL(),
                                             new OPT_BranchProfileOperand()));
            if (VM_DynamicTypeCheck.MIN_SUPERCLASS_IDS_SIZE <= LHSDepth) {
              OPT_RegisterOperand superclassIdsLength = 
                InsertGuardedUnary(s, ir, ARRAYLENGTH, VM_TypeReference.Int, 
                                   superclassIds.copyD2U(), TG());
              OPT_RegisterOperand boundscheck = ir.regpool.makeTempInt();
			     //save to use the cheaper ADDR version of BOOLEAN_CMP
              s.insertBefore(BooleanCmp.create(BOOLEAN_CMP_ADDR, boundscheck, 
                                               superclassIdsLength, 
                                               AC(Address.fromIntSignExtend(LHSDepth)), 
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
        // A non-resolved class or interface. 
        // We expect these to be extremely uncommon in opt code in AOS.
        // Mutate s into a call to VM_Runtime.instanceOf
        VM_Method target = VM_Entrypoints.instanceOfMethod;
        Call.mutate2(s, CALL, result, IC(target.getOffset()), 
                     OPT_MethodOperand.STATIC(target),
                     RHSobj, IC(LHStype.getId()));
        return callHelper(s, ir);
      }
    }
    if (LHStype.isArrayType()) {
      // Case 2 of VM_DynamicTypeCheck: LHS is an array.
      VM_Array LHSArray = (VM_Array)LHStype.peekResolvedType();
      if (LHSArray != null) {
        VM_Type innermostElementType = LHSArray.getInnermostElementType();
        if (innermostElementType.isPrimitiveType() || 
            (innermostElementType.asClass().isResolved() && 
             innermostElementType.asClass().isFinal())) {
          // [^k of primitive or [^k of final class. Just like final classes, 
          // a PTR compare of rhsTIB and the TIB of the class gives the answer.
          OPT_Operand classTIB = getTIB(s, ir, LHSArray);
          BooleanCmp.mutate(s, BOOLEAN_CMP_ADDR, result, RHStib, classTIB, 
                            OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand());
          return s;
        }
      }
      // We're going to have to branch anyways, so reduce to a branching case 
      // and do the real work there.
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
   * @param LHStype  The VM_TypeReference to be tested against.
   * @param RHStib   The OPT_RegisterOperand containing the TIB of the rhs.
   * @param result   The OPT_RegisterOperand that the result of dynamic 
   * @return the opt instruction immediately before the instruction to 
   *         continue expansion.
   */
  private static OPT_Instruction convertToBranchingTypeCheck(OPT_Instruction s,
                                                             OPT_IR ir,
                                                             OPT_Operand RHSobj, 
                                                             VM_TypeReference LHStype,
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
    trueBlock.appendInstruction(Move.create(INT_MOVE, result, IC(1)));
    trueBlock.appendInstruction(Goto.create(GOTO, 
                                            contBlock.makeJumpTarget()));
    falseBlock.appendInstruction(Move.create(INT_MOVE, result.copyD2D(), 
                                             IC(0)));
    return generateBranchingTypeCheck(s, ir, RHSobj, LHStype, RHStib, trueBlock, 
                                      falseBlock, null, new OPT_BranchProfileOperand());
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
   * @param LHStype    The VM_TypeReference to be tested against.
   * @param RHStib     The OPT_RegisterOperand containing the TIB of the rhs.
   * @param trueBlock  The OPT_BasicBlock to continue at if the typecheck 
   *                   evaluates to true
   * @param falseBlock The OPT_BasicBlock to continue at if the typecheck 
   *                   evaluates to false.
   * @param falseProb   The probability that typecheck will branch to the falseBlock
   * @return the opt instruction immediately before the instruction to 
   *         continue expansion.
   */
  private static OPT_Instruction generateBranchingTypeCheck(OPT_Instruction s, 
                                                            OPT_IR ir, 
                                                            OPT_Operand RHSobj,
                                                            VM_TypeReference LHStype, 
                                                            OPT_RegisterOperand RHStib, 
                                                            OPT_BasicBlock trueBlock, 
                                                            OPT_BasicBlock falseBlock,
                                                            OPT_RegisterOperand oldGuard,
							    OPT_BranchProfileOperand falseProb) {
    OPT_Instruction continueAt = Goto.create(GOTO, trueBlock.makeJumpTarget());
    continueAt.copyPosition(s);
    s.insertBefore(continueAt);
    s.remove();

    if (LHStype.isClassType()) {
      VM_Class LHSclass = (VM_Class)LHStype.peekResolvedType();
      if (LHSclass != null && LHSclass.isResolved()) {
        // Cases 4, 5, and 6 of VM_DynamicTypeCheck: LHSclass is a resolved 
        // class or interface
        if (LHSclass.isInterface()) {
          // A resolved interface (case 4)
          int interfaceIndex = LHSclass.getDoesImplementIndex();
          int interfaceMask = LHSclass.getDoesImplementBitMask();
          OPT_RegisterOperand doesImpl = 
            InsertUnary(continueAt, ir, GET_DOES_IMPLEMENT_FROM_TIB, 
                        VM_TypeReference.IntArray, RHStib);

          if (VM_DynamicTypeCheck.MIN_DOES_IMPLEMENT_SIZE <= interfaceIndex) {
            OPT_RegisterOperand doesImplLength = 
              InsertGuardedUnary(continueAt, 
                                 ir, ARRAYLENGTH, VM_TypeReference.Int, 
                                 doesImpl.copyD2U(), TG());
            OPT_Instruction lengthCheck = 
              IfCmp.create(INT_IFCMP, oldGuard, doesImplLength, IC(interfaceIndex),
                           OPT_ConditionOperand.LESS_EQUAL(), 
                           falseBlock.makeJumpTarget(),
                           OPT_BranchProfileOperand.unlikely());
            continueAt.insertBefore(lengthCheck);
            OPT_BasicBlock oldBlock = continueAt.getBasicBlock();
            oldBlock.splitNodeWithLinksAt(lengthCheck, ir);
            oldBlock.insertOut(falseBlock); // required due to splitNode!
          }
          OPT_RegisterOperand entry = 
            InsertLoadOffset(continueAt, ir, INT_LOAD, VM_TypeReference.Int,
                             doesImpl, interfaceIndex << 2, 
                             new OPT_LocationOperand(VM_TypeReference.Int), 
                             TG());
          OPT_RegisterOperand bit = 
            InsertBinary(continueAt, ir, INT_AND, VM_TypeReference.Int, entry, IC(interfaceMask));
          continueAt.insertBefore(IfCmp.create(INT_IFCMP, oldGuard, 
                                               bit, IC(0),
                                               OPT_ConditionOperand.EQUAL(), 
                                               falseBlock.makeJumpTarget(),
                                               falseProb));
          return continueAt;
        } else {
          // A resolved class (cases 5 and 6 in VM_DynamicTypeCheck)
          if (LHSclass.isFinal()) {
            // For a final class, we can do a PTR compare of 
            // rhsTIB and the TIB of the class
            OPT_Operand classTIB = getTIB(continueAt, ir, LHSclass);
            continueAt.insertBefore(IfCmp.create(INT_IFCMP, oldGuard, 
                                                 RHStib, classTIB,
                                                 OPT_ConditionOperand.NOT_EQUAL(), 
                                                 falseBlock.makeJumpTarget(),
                                                 falseProb));
            return continueAt;
          } else {
            // Do the full blown case 5 or 6 typecheck.
            int LHSDepth = LHSclass.getTypeDepth();
            int LHSId = LHSclass.getId();
            OPT_RegisterOperand superclassIds = 
              InsertUnary(continueAt, ir, GET_SUPERCLASS_IDS_FROM_TIB, 
                          VM_TypeReference.ShortArray, RHStib);
            if (VM_DynamicTypeCheck.MIN_SUPERCLASS_IDS_SIZE <= LHSDepth) {
              OPT_RegisterOperand superclassIdsLength = 
                InsertGuardedUnary(continueAt, 
                                   ir, ARRAYLENGTH, VM_TypeReference.Int, 
                                   superclassIds.copyD2U(), TG());
              OPT_Instruction lengthCheck = 
                IfCmp.create(INT_IFCMP, oldGuard, superclassIdsLength, IC(LHSDepth),
                             OPT_ConditionOperand.LESS(), 
                             falseBlock.makeJumpTarget(),
                             OPT_BranchProfileOperand.unlikely());
              continueAt.insertBefore(lengthCheck);
              OPT_BasicBlock oldBlock = continueAt.getBasicBlock();
              oldBlock.splitNodeWithLinksAt(lengthCheck, ir);
              oldBlock.insertOut(falseBlock); // required due to splitNode!
            }
            OPT_RegisterOperand refCandidate = 
              InsertLoadOffset(continueAt, ir, USHORT_LOAD, VM_TypeReference.Short,
                               superclassIds, LHSDepth << 1, 
                               new OPT_LocationOperand(VM_TypeReference.Short), 
                               TG());
            continueAt.insertBefore(IfCmp.create(INT_IFCMP, oldGuard, 
                                                 refCandidate, IC(LHSId),
                                                 OPT_ConditionOperand.NOT_EQUAL(), 
                                                 falseBlock.makeJumpTarget(),
                                                 falseProb));
            return continueAt;
          }
        }
      } else {
        // A non-resolved class or interface. Case 3 of VM_DynamicTypeCheck
        // Branch on the result of a call to 
        // VM_Runtime.instance
        OPT_RegisterOperand result = ir.regpool.makeTempInt();
        VM_Method target = VM_Entrypoints.instanceOfMethod;
        OPT_Instruction call = Call.create2(CALL, result, IC(target.getOffset()),
                                            OPT_MethodOperand.STATIC(target),
                                            RHSobj, IC(LHStype.getId()));
        call.copyPosition(continueAt);
        continueAt.insertBefore(call);
        call = callHelper(call, ir);
        continueAt.insertBefore(IfCmp.create(INT_IFCMP, oldGuard, 
                                             result.copyD2U(), IC(0),
                                             OPT_ConditionOperand.EQUAL(), 
                                             falseBlock.makeJumpTarget(),
                                             falseProb));
        return continueAt;
      }
    }

    if (LHStype.isArrayType()) {
      // Case 2 of VM_DynamicTypeCheck: LHS is an array.
      VM_Array LHSArray = (VM_Array)LHStype.peekResolvedType();
      if (LHSArray != null) {
        OPT_Operand classTIB = getTIB(continueAt, ir, LHSArray);
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
                                               falseProb));
          return continueAt;
        }
	// TODO: branch probability calculation is somewhat bogus for this case.
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
        OPT_RegisterOperand rhsType = 
          InsertUnary(continueAt, ir, GET_TYPE_FROM_TIB, 
                      VM_TypeReference.VM_Type, RHStib.copyD2U());
        if (innermostElementType.isJavaLangObjectType()) {
          OPT_IntConstantOperand lhsDimension = IC(LHStype.getDimensionality());
          OPT_RegisterOperand rhsDimension = 
            getField(continueAt, ir, rhsType, VM_Entrypoints.dimensionField);
          OPT_Instruction dimTest = 
            IfCmp2.create(INT_IFCMP2, oldGuard, rhsDimension, lhsDimension,
                          OPT_ConditionOperand.GREATER(), 
                          trueBlock.makeJumpTarget(),
                          ((OPT_BranchProfileOperand)falseProb.copy()).flip(),
                          OPT_ConditionOperand.LESS(), 
                          falseBlock.makeJumpTarget(),
                          falseProb);
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
                                               IC(0),
                                               OPT_ConditionOperand.NOT_EQUAL(), 
                                               falseBlock.makeJumpTarget(),
                                               falseProb));
          return continueAt;
        }
      } 

      // Not a case we want to handle inline
      VM_Method target = VM_Entrypoints.instanceOfMethod;
      OPT_RegisterOperand callResult = ir.regpool.makeTempInt();
      OPT_Instruction call = Call.create2(CALL, callResult, IC(target.getOffset()), OPT_MethodOperand.STATIC(target), 
                                          RHSobj, IC(LHStype.getId()));
      call.copyPosition(continueAt);
      continueAt.insertBefore(call);
      call = callHelper(call, ir);
      continueAt.insertBefore(IfCmp.create(INT_IFCMP, oldGuard, 
                                           callResult.copyD2U(), IC(0),
                                           OPT_ConditionOperand.EQUAL(), 
                                           falseBlock.makeJumpTarget(),
                                           falseProb));
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

  private static final OPT_BasicBlock advanceBlock(int bcIndex, OPT_BasicBlock curBlock, OPT_IR ir) {
    OPT_BasicBlock newBlock = curBlock.createSubBlock(bcIndex, ir);
    curBlock.insertOut(newBlock);
    ir.cfg.linkInCodeOrder(curBlock, newBlock);
    return newBlock;
  }

}
