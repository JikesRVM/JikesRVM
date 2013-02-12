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
package org.jikesrvm.compilers.opt.hir2lir;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.DynamicTypeCheck;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.BooleanCmp;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.Goto;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.IfCmp2;
import org.jikesrvm.compilers.opt.ir.InstanceOf;
import org.jikesrvm.compilers.opt.ir.Load;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import static org.jikesrvm.compilers.opt.ir.Operators.ARRAYLENGTH;
import static org.jikesrvm.compilers.opt.ir.Operators.BBEND;
import static org.jikesrvm.compilers.opt.ir.Operators.BOOLEAN_CMP_ADDR;
import static org.jikesrvm.compilers.opt.ir.Operators.CALL;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_ARRAY_ELEMENT_TIB_FROM_TIB;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_DOES_IMPLEMENT_FROM_TIB;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_SUPERCLASS_IDS_FROM_TIB;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_TYPE_FROM_TIB;
import static org.jikesrvm.compilers.opt.ir.Operators.GOTO;
import static org.jikesrvm.compilers.opt.ir.Operators.GUARD_COMBINE;
import static org.jikesrvm.compilers.opt.ir.Operators.GUARD_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_2ADDRZerExt;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_AND;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_IFCMP;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_IFCMP2;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_SHL;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_IFCMP;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.TRAP;
import static org.jikesrvm.compilers.opt.ir.Operators.USHORT_ALOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.USHORT_LOAD;
import org.jikesrvm.compilers.opt.ir.StoreCheck;
import org.jikesrvm.compilers.opt.ir.Trap;
import org.jikesrvm.compilers.opt.ir.TypeCheck;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.NullConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrapCodeOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;
import org.jikesrvm.runtime.Entrypoints;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Expansion of Dynamic Type Checking operations.
 *
 * @see DynamicTypeCheck
 */
abstract class DynamicTypeCheckExpansion extends ConvertToLowLevelIR {

  /**
   * Expand an instanceof instruction into the LIR sequence that implements
   * the dynamic type check.  Ref may contain a null ptr at runtime.
   *
   * @param s an INSTANCEOF or INSTANCEOF_UNRESOLVED instruction to expand
   * @param ir the enclosing IR
   * @return the last Instruction in the generated LIR sequence.
   */
  static Instruction instanceOf(Instruction s, IR ir) {
    RegisterOperand result = InstanceOf.getClearResult(s);
    TypeReference LHStype = InstanceOf.getType(s).getTypeRef();
    Operand ref = InstanceOf.getClearRef(s);
    Instruction next = s.nextInstructionInCodeOrder();
    if (next.operator() == INT_IFCMP &&
        IfCmp.getVal1(next) instanceof RegisterOperand &&
        result.similar(IfCmp.getVal1(next))) {
      // The result of instanceof is being consumed by a conditional branch.
      // Optimize this case by generating a branching type check
      // instead of producing a value.
      // TODO: This is really not safe: suppose the if is NOT the
      // only use of the result of the instanceof.
      // The way to fix this is to add ifInstanceOf and ifNotInstanceOf
      // operators to the IR and have Simple transform
      // instanceof, intIfCmp based on the U/D chains.
      // See defect 2114.
      Operand val2 = IfCmp.getVal2(next);
      if (VM.VerifyAssertions) VM._assert(val2.isIntConstant());
      int ival2 = ((IntConstantOperand) val2).value;
      ConditionOperand cond = IfCmp.getCond(next);
      boolean branchCondition =
          (((ival2 == 0) && (cond.isNOT_EQUAL() || cond.isLESS_EQUAL())) ||
           ((ival2 == 1) && (cond.isEQUAL() || cond.isGREATER_EQUAL())));
      BasicBlock branchBB = next.getBranchTarget();
      RegisterOperand oldGuard = IfCmp.getGuardResult(next);
      next.remove();
      BasicBlock fallThroughBB = fallThroughBB(s, ir);
      BasicBlock falseBranch = branchCondition ? fallThroughBB : branchBB;
      BasicBlock trueBranch = branchCondition ? branchBB : fallThroughBB;
      BranchProfileOperand bp = IfCmp.getClearBranchProfile(next);
      if (branchCondition) bp = bp.flip();
      Instruction nullComp =
          IfCmp.create(REF_IFCMP,
                       oldGuard.copyRO(),
                       ref.copy(),
                       new NullConstantOperand(),
                       ConditionOperand.EQUAL(),
                       falseBranch.makeJumpTarget(),
                       BranchProfileOperand.unlikely());
      s.insertBefore(nullComp);
      BasicBlock myBlock = s.getBasicBlock();
      BasicBlock instanceOfBlock = myBlock.splitNodeAt(nullComp, ir);
      myBlock.insertOut(instanceOfBlock);
      myBlock.insertOut(falseBranch);
      ir.cfg.linkInCodeOrder(myBlock, instanceOfBlock);
      Operand RHStib = getTIB(s, ir, ref, oldGuard.copyRO());
      return generateBranchingTypeCheck(s, ir, ref.copy(), LHStype, RHStib, trueBranch, falseBranch, oldGuard, bp);
    } else {
      // Not a branching pattern
      RegisterOperand guard = ir.regpool.makeTempValidation();
      BasicBlock instanceOfBlock = s.getBasicBlock().segregateInstruction(s, ir);
      BasicBlock prevBB = instanceOfBlock.prevBasicBlockInCodeOrder();
      BasicBlock nextBB = instanceOfBlock.nextBasicBlockInCodeOrder();
      BasicBlock nullCaseBB = instanceOfBlock.createSubBlock(s.bcIndex, ir, .01f);
      prevBB.appendInstruction(IfCmp.create(REF_IFCMP,
                                            guard,
                                            ref.copy(),
                                            new NullConstantOperand(),
                                            ConditionOperand.EQUAL(),
                                            nullCaseBB.makeJumpTarget(),
                                            BranchProfileOperand.unlikely()));
      nullCaseBB.appendInstruction(Move.create(INT_MOVE, result.copyD2D(), IC(0)));
      nullCaseBB.appendInstruction(Goto.create(GOTO, nextBB.makeJumpTarget()));
      // Stitch together the CFG; add nullCaseBB to the end of code array.
      prevBB.insertOut(nullCaseBB);
      nullCaseBB.insertOut(nextBB);
      ir.cfg.addLastInCodeOrder(nullCaseBB);
      Operand RHStib = getTIB(s, ir, ref, guard.copyD2U());
      return generateValueProducingTypeCheck(s, ir, ref.copy(), LHStype, RHStib, result);
    }
  }

  /**
   * Expand an instanceof instruction into the LIR sequence that implements
   * the dynamic type check.  Ref is known to never contain a null ptr at
   * runtime.
   *
   * @param s an INSTANCEOF_NOTNULL instruction to expand
   * @param ir the enclosing IR
   * @return the last Instruction in the generated LIR sequence.
   */
  static Instruction instanceOfNotNull(Instruction s, IR ir) {
    RegisterOperand result = InstanceOf.getClearResult(s);
    TypeReference LHStype = InstanceOf.getType(s).getTypeRef();
    Operand ref = InstanceOf.getClearRef(s);
    Operand guard = InstanceOf.getClearGuard(s);
    Instruction next = s.nextInstructionInCodeOrder();
    if (next.operator() == INT_IFCMP &&
        IfCmp.getVal1(next) instanceof RegisterOperand &&
        result.similar(IfCmp.getVal1(next))) {
      // The result of instanceof is being consumed by a conditional branch.
      // Optimize this case by generating a branching type
      // check instead of producing a value.
      Operand val2 = IfCmp.getVal2(next);
      if (VM.VerifyAssertions) {
        VM._assert(val2.isIntConstant());
      }
      int ival2 = ((IntConstantOperand) val2).value;
      ConditionOperand cond = IfCmp.getCond(next);
      boolean branchCondition =
          (((ival2 == 0) && (cond.isNOT_EQUAL() || cond.isLESS_EQUAL())) ||
           ((ival2 == 1) && (cond.isEQUAL() || cond.isGREATER_EQUAL())));
      BasicBlock branchBB = next.getBranchTarget();
      RegisterOperand oldGuard = IfCmp.getGuardResult(next);
      next.remove();
      BasicBlock fallThroughBB = fallThroughBB(s, ir);
      Operand RHStib = getTIB(s, ir, ref, guard);
      if (branchCondition) {
        return generateBranchingTypeCheck(s,
                                          ir,
                                          ref.copy(),
                                          LHStype,
                                          RHStib,
                                          branchBB,
                                          fallThroughBB,
                                          oldGuard,
                                          IfCmp.getClearBranchProfile(next).flip());
      } else {
        return generateBranchingTypeCheck(s,
                                          ir,
                                          ref.copy(),
                                          LHStype,
                                          RHStib,
                                          fallThroughBB,
                                          branchBB,
                                          oldGuard,
                                          IfCmp.getClearBranchProfile(next));
      }
    } else {
      // Not a branching pattern
      Operand RHStib = getTIB(s, ir, ref, guard);
      return generateValueProducingTypeCheck(s, ir, ref.copy(), LHStype, RHStib, result);
    }
  }

  /**
   * Expand a checkcast instruction into the LIR sequence that implements the
   * dynamic type check, raising a ClassCastException when the type check
   * fails. Ref may contain a null ptr at runtime.
   *
   * @param s a CHECKCAST or CHECKCAST_UNRESOLVED instruction to expand
   * @param ir the enclosing IR
   * @return the last Instruction in the generated LIR sequence.
   */
  static Instruction checkcast(Instruction s, IR ir) {
    Operand ref = TypeCheck.getClearRef(s);
    TypeReference LHStype = TypeCheck.getType(s).getTypeRef();
    RegisterOperand guard = ir.regpool.makeTempValidation();
    Instruction nullCond =
        IfCmp.create(REF_IFCMP,
                     guard,
                     ref.copy(),
                     new NullConstantOperand(),
                     ConditionOperand.EQUAL(),
                     null,
                     // KLUDGE...we haven't created the block yet!
                     new BranchProfileOperand());
    s.insertBefore(nullCond);
    BasicBlock myBlock = s.getBasicBlock();
    BasicBlock failBlock = myBlock.createSubBlock(s.bcIndex, ir, .0001f);
    BasicBlock instanceOfBlock = myBlock.splitNodeAt(nullCond, ir);
    BasicBlock succBlock = instanceOfBlock.splitNodeAt(s, ir);
    succBlock.firstInstruction().insertAfter(Move.create(REF_MOVE, TypeCheck.getClearResult(s), ref.copy()));
    IfCmp.setTarget(nullCond, succBlock.makeJumpTarget()); // fixup KLUDGE
    myBlock.insertOut(instanceOfBlock);
    myBlock.insertOut(succBlock);
    instanceOfBlock.insertOut(failBlock);
    instanceOfBlock.insertOut(succBlock);
    ir.cfg.linkInCodeOrder(myBlock, instanceOfBlock);
    ir.cfg.linkInCodeOrder(instanceOfBlock, succBlock);
    ir.cfg.addLastInCodeOrder(failBlock);
    Instruction raiseError = Trap.create(TRAP, null, TrapCodeOperand.CheckCast());
    raiseError.copyPosition(s);
    failBlock.appendInstruction(raiseError);
    Operand RHStib = getTIB(s, ir, ref, guard.copyD2U());
    return generateBranchingTypeCheck(s,
                                      ir,
                                      ref.copy(),
                                      LHStype,
                                      RHStib,
                                      succBlock,
                                      failBlock,
                                      guard.copyRO(),
                                      BranchProfileOperand.never());
  }

  /**
   * Expand a checkcast instruction into the LIR sequence that implements the
   * dynamic type check, raising a ClassCastException when the type check
   * fails. Ref is known to never contain a null ptr at runtime.
   *
   * @param s a CHECKCAST_NOTNULL instruction to expand
   * @param ir the enclosing IR
   * @return the last Instruction in the generated LIR sequence.
   */
  static Instruction checkcastNotNull(Instruction s, IR ir) {
    Operand ref = TypeCheck.getClearRef(s);
    TypeReference LHStype = TypeCheck.getType(s).getTypeRef();
    Operand guard = TypeCheck.getClearGuard(s);
    BasicBlock myBlock = s.getBasicBlock();
    BasicBlock failBlock = myBlock.createSubBlock(s.bcIndex, ir, .0001f);
    BasicBlock succBlock = myBlock.splitNodeAt(s, ir);
    succBlock.firstInstruction().insertAfter(Move.create(REF_MOVE, TypeCheck.getClearResult(s), ref.copy()));
    myBlock.insertOut(failBlock);
    myBlock.insertOut(succBlock);
    ir.cfg.linkInCodeOrder(myBlock, succBlock);
    ir.cfg.addLastInCodeOrder(failBlock);
    Instruction raiseError = Trap.create(TRAP, null, TrapCodeOperand.CheckCast());
    raiseError.copyPosition(s);
    failBlock.appendInstruction(raiseError);
    Operand RHStib = getTIB(s, ir, ref, guard);
    return generateBranchingTypeCheck(s,
                                      ir,
                                      ref.copy(),
                                      LHStype,
                                      RHStib,
                                      succBlock,
                                      failBlock,
                                      ir.regpool.makeTempValidation(),
                                      BranchProfileOperand.never());
  }

  /**
   * Expand a checkcastInterface instruction into the LIR sequence that
   * implements the dynamic type check, raising an IncompataibleClassChangeError
   * if the type check fails.
   * Ref is known to never contain a null ptr at runtime.
   *
   * @param s a MUST_IMPLEMENT_INTERFACE instruction to expand
   * @param ir the enclosing IR
   * @return the last Instruction in the generated LIR sequence.
   */
  static Instruction mustImplementInterface(Instruction s, IR ir) {
    Operand ref = TypeCheck.getClearRef(s);
    RVMClass LHSClass = (RVMClass) TypeCheck.getType(s).getVMType();
    if (VM.VerifyAssertions) VM._assert(LHSClass != null, "Should be resolvable...");
    int interfaceIndex = LHSClass.getDoesImplementIndex();
    int interfaceMask = LHSClass.getDoesImplementBitMask();
    Operand guard = TypeCheck.getClearGuard(s);
    BasicBlock myBlock = s.getBasicBlock();
    BasicBlock failBlock = myBlock.createSubBlock(s.bcIndex, ir, .0001f);
    BasicBlock succBlock = myBlock.splitNodeAt(s, ir);
    succBlock.firstInstruction().insertAfter(Move.create(REF_MOVE, TypeCheck.getClearResult(s), ref.copy()));
    myBlock.insertOut(failBlock);
    myBlock.insertOut(succBlock);
    ir.cfg.linkInCodeOrder(myBlock, succBlock);
    ir.cfg.addLastInCodeOrder(failBlock);
    Instruction raiseError = Trap.create(TRAP, null, TrapCodeOperand.MustImplement());
    raiseError.copyPosition(s);
    failBlock.appendInstruction(raiseError);

    Operand RHStib = getTIB(s, ir, ref, guard);
    RegisterOperand doesImpl = InsertUnary(s, ir, GET_DOES_IMPLEMENT_FROM_TIB, TypeReference.IntArray, RHStib);

    if (DynamicTypeCheck.MIN_DOES_IMPLEMENT_SIZE <= interfaceIndex) {
      RegisterOperand doesImplLength =
          InsertGuardedUnary(s, ir, ARRAYLENGTH, TypeReference.Int, doesImpl.copyD2U(), TG());
      Instruction lengthCheck =
          IfCmp.create(INT_IFCMP,
              ir.regpool.makeTempValidation(),
              doesImplLength,
              IC(interfaceIndex),
              ConditionOperand.LESS_EQUAL(),
              failBlock.makeJumpTarget(),
              BranchProfileOperand.never());
      s.insertBefore(lengthCheck);
      myBlock.splitNodeWithLinksAt(lengthCheck, ir);
      myBlock.insertOut(failBlock); // required due to splitNode!
    }
    RegisterOperand entry =
        InsertLoadOffset(s,
                         ir,
                         INT_LOAD,
                         TypeReference.Int,
                         doesImpl,
                         Offset.fromIntZeroExtend(interfaceIndex << 2),
                         new LocationOperand(TypeReference.Int),
                         TG());
    RegisterOperand bit = insertBinary(s, ir, INT_AND, TypeReference.Int, entry, IC(interfaceMask));
    IfCmp.mutate(s,
                 INT_IFCMP,
                 ir.regpool.makeTempValidation(),
                 bit,
                 IC(0),
                 ConditionOperand.EQUAL(),
                 failBlock.makeJumpTarget(),
                 BranchProfileOperand.never());
    return s;
  }

  /**
   * Expand an object array store check into the LIR sequence that
   * implements it.
   *
   * @param s an OBJARRAY_STORE_CHECK instruction to expand
   * @param ir the enclosing IR
   * @param couldBeNull is it possible that the element being stored is null?
   * @return the last Instruction in the generated LIR sequence.
   */
  static Instruction arrayStoreCheck(Instruction s, IR ir, boolean couldBeNull) {
    RegisterOperand guardResult = StoreCheck.getGuardResult(s);
    Operand arrayRef = StoreCheck.getClearRef(s);
    Operand elemRef = StoreCheck.getClearVal(s);
    Operand guard = StoreCheck.getClearGuard(s);
    if (elemRef instanceof NullConstantOperand) {
      Instruction continueAt = s.prevInstructionInCodeOrder();
      s.remove();
      return continueAt;
    }
    BasicBlock myBlock = s.getBasicBlock();
    BasicBlock contBlock = myBlock.splitNodeAt(s, ir);
    BasicBlock trapBlock = myBlock.createSubBlock(s.bcIndex, ir, .0001f);
    BasicBlock curBlock = myBlock;
    Move.mutate(s, GUARD_MOVE, guardResult, new TrueGuardOperand());

    // Set up a block with a trap instruction that we can jump to if the
    // store check fails
    Instruction trap = Trap.create(TRAP, null, TrapCodeOperand.StoreCheck());
    trap.copyPosition(s);
    trapBlock.appendInstruction(trap);
    ir.cfg.addLastInCodeOrder(trapBlock);

    Operand rhsGuard = guard;
    if (couldBeNull) {
      // if rhs is null, then the checkcast succeeds
      rhsGuard = ir.regpool.makeTempValidation();
      contBlock.prependInstruction(Binary.create(GUARD_COMBINE,
                                                 guardResult.copyRO(),
                                                 guardResult.copyRO(),
                                                 rhsGuard.copy()));
      curBlock.appendInstruction(IfCmp.create(REF_IFCMP,
                                              rhsGuard.asRegister(),
                                              elemRef,
                                              new NullConstantOperand(),
                                              ConditionOperand.EQUAL(),
                                              contBlock.makeJumpTarget(),
                                              new BranchProfileOperand()));
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
    RVMType compType = arrayRef.getType().peekType();
    if (compType != null && !compType.isJavaLangObjectType()) {
      // optionally (1) from above
      if (compType.getDimensionality() == 1) {
        RVMClass etc = (RVMClass) compType.asArray().getElementType();
        if (etc.isResolved() && etc.isFinal()) {
          if (VM.VerifyAssertions) VM._assert(!etc.isInterface());
          Operand rhsTIB = getTIB(curBlock.lastInstruction(), ir, elemRef.copy(), rhsGuard.copy());
          Operand etTIB = getTIB(curBlock.lastInstruction(), ir, etc);
          curBlock.appendInstruction(IfCmp.create(REF_IFCMP,
                                                  guardResult.copyRO(),
                                                  rhsTIB,
                                                  etTIB,
                                                  ConditionOperand.NOT_EQUAL(),
                                                  trapBlock.makeJumpTarget(),
                                                  BranchProfileOperand.never()));
          curBlock.insertOut(trapBlock);
          curBlock.insertOut(contBlock);
          ir.cfg.linkInCodeOrder(curBlock, contBlock);
          return curBlock.lastInstruction();
        }
      }

      // optionally (2) from above
      Operand lhsTIB = getTIB(curBlock.lastInstruction(), ir, arrayRef, guard);
      if (((arrayRef instanceof RegisterOperand) && ((RegisterOperand) arrayRef).isDeclaredType()) ||
          compType == RVMType.JavaLangObjectArrayType) {
        Operand declTIB = getTIB(curBlock.lastInstruction(), ir, compType);
        curBlock.appendInstruction(IfCmp.create(REF_IFCMP,
                                                guardResult.copyRO(),
                                                declTIB,
                                                lhsTIB,
                                                ConditionOperand.EQUAL(),
                                                contBlock.makeJumpTarget(),
                                                new BranchProfileOperand()));
        curBlock.insertOut(contBlock);
        curBlock = advanceBlock(s.bcIndex, curBlock, ir);
      }

      // On our way to doing (3) from above attempt another short-circuit.
      // If lhsElemTIB == rhsTIB, then we are done.
      Operand rhsTIB = getTIB(curBlock.lastInstruction(), ir, elemRef.copy(), rhsGuard.copy());
      RegisterOperand lhsElemTIB =
          InsertUnary(curBlock.lastInstruction(),
                      ir,
                      GET_ARRAY_ELEMENT_TIB_FROM_TIB,
                      TypeReference.TIB,
                      lhsTIB.copy());
      curBlock.appendInstruction(IfCmp.create(REF_IFCMP,
                                              guardResult.copyRO(),
                                              rhsTIB,
                                              lhsElemTIB,
                                              ConditionOperand.EQUAL(),
                                              contBlock.makeJumpTarget(),
                                              new BranchProfileOperand()));
      curBlock.insertOut(contBlock);
      curBlock = advanceBlock(s.bcIndex, curBlock, ir);

      // Optionally (3) from above
      if (compType.getDimensionality() == 1) {
        RVMClass etc = (RVMClass) compType.asArray().getElementType();
        if (etc.isResolved() && !etc.isInterface() && !etc.isJavaLangObjectType()) {
          RegisterOperand lhsElemType =
              InsertUnary(curBlock.lastInstruction(),
                          ir,
                          GET_TYPE_FROM_TIB,
                          TypeReference.Type,
                          lhsElemTIB.copyU2U());
          RegisterOperand rhsSuperclassIds =
              InsertUnary(curBlock.lastInstruction(),
                          ir,
                          GET_SUPERCLASS_IDS_FROM_TIB,
                          TypeReference.ShortArray,
                          rhsTIB.copy());
          RegisterOperand lhsElemDepth =
              getField(curBlock.lastInstruction(), ir, lhsElemType, Entrypoints.depthField, TG());
          RegisterOperand rhsSuperclassIdsLength =
              InsertGuardedUnary(curBlock.lastInstruction(),
                                 ir,
                                 ARRAYLENGTH,
                                 TypeReference.Int,
                                 rhsSuperclassIds.copyD2U(),
                                 TG());
          curBlock.appendInstruction(IfCmp.create(INT_IFCMP,
                                                  guardResult.copyRO(),
                                                  lhsElemDepth,
                                                  rhsSuperclassIdsLength,
                                                  ConditionOperand.GREATER_EQUAL(),
                                                  trapBlock.makeJumpTarget(),
                                                  BranchProfileOperand.never()));
          curBlock.insertOut(trapBlock);
          curBlock = advanceBlock(s.bcIndex, curBlock, ir);

          RegisterOperand lhsElemId =
              getField(curBlock.lastInstruction(), ir, lhsElemType.copyD2U(), Entrypoints.idField, TG());
          RegisterOperand refCandidate = ir.regpool.makeTemp(TypeReference.Short);
          LocationOperand loc = new LocationOperand(TypeReference.Short);
          if (LOWER_ARRAY_ACCESS) {
            RegisterOperand lhsDepthOffset =
                insertBinary(curBlock.lastInstruction(),
                             ir,
                             INT_SHL,
                             TypeReference.Int,
                             lhsElemDepth.copyD2U(),
                             IC(1));
            lhsDepthOffset =
                InsertUnary(curBlock.lastInstruction(),
                            ir,
                            INT_2ADDRZerExt,
                            TypeReference.Offset,
                            lhsDepthOffset.copy());
            curBlock.appendInstruction(Load.create(USHORT_LOAD,
                                                   refCandidate,
                                                   rhsSuperclassIds,
                                                   lhsDepthOffset,
                                                   loc,
                                                   TG()));
          } else {
            curBlock.appendInstruction(ALoad.create(USHORT_ALOAD,
                                                    refCandidate,
                                                    rhsSuperclassIds,
                                                    lhsElemDepth.copyRO(),
                                                    loc,
                                                    TG()));
          }
          curBlock.appendInstruction(IfCmp.create(INT_IFCMP,
                                                  guardResult.copyRO(),
                                                  refCandidate.copyD2U(),
                                                  lhsElemId,
                                                  ConditionOperand.NOT_EQUAL(),
                                                  trapBlock.makeJumpTarget(),
                                                  BranchProfileOperand.never()));
          curBlock.insertOut(trapBlock);
          curBlock.insertOut(contBlock);
          ir.cfg.linkInCodeOrder(curBlock, contBlock);
          return curBlock.lastInstruction();
        }
      }
    }

    // Call RuntimeEntrypoints.checkstore.
    RVMMethod target = Entrypoints.checkstoreMethod;
    Instruction call =
        Call.create2(CALL,
                     null,
                     AC(target.getOffset()),
                     MethodOperand.STATIC(target),
                     rhsGuard.copy(),
                     arrayRef.copy(),
                     elemRef.copy());
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
   * @param s        The Instruction that is to be replaced by
   *                  a value producing type check
   * @param ir       The IR containing the instruction to be expanded.
   * @param RHSobj   The RegisterOperand containing the rhs object.
   * @param LHStype  The RVMType to be tested against.
   * @param RHStib   The Operand containing the TIB of the rhs.
   * @param result   The RegisterOperand that the result of dynamic
   *                 type check is to be stored in.
   * @return the opt instruction immediately before the
   *         instruction to continue expansion.
   */
  private static Instruction generateValueProducingTypeCheck(Instruction s, IR ir, Operand RHSobj,
                                                                 TypeReference LHStype, Operand RHStib,
                                                                 RegisterOperand result) {
    // Is LHStype a class?
    if (LHStype.isClassType()) {
      RVMClass LHSclass = (RVMClass) LHStype.peekType();
      if (LHSclass != null && LHSclass.isResolved()) {
        // Cases 4, 5, and 6 of DynamicTypeCheck: LHSclass is a
        // resolved class or interface
        if (LHSclass.isInterface()) {
          // A resolved interface (case 4)
          int interfaceIndex = LHSclass.getDoesImplementIndex();
          int interfaceMask = LHSclass.getDoesImplementBitMask();
          RegisterOperand doesImpl =
              InsertUnary(s, ir, GET_DOES_IMPLEMENT_FROM_TIB, TypeReference.IntArray, RHStib);
          RegisterOperand entry =
              InsertLoadOffset(s,
                               ir,
                               INT_LOAD,
                               TypeReference.Int,
                               doesImpl,
                               Offset.fromIntZeroExtend(interfaceIndex << 2),
                               new LocationOperand(TypeReference.Int),
                               TG());
          RegisterOperand bit = insertBinary(s, ir, INT_AND, TypeReference.Int, entry, IC(interfaceMask));
          //save to use the cheaper ADDR version of BOOLEAN_CMP
          s.insertBefore(BooleanCmp.create(BOOLEAN_CMP_ADDR,
                                           result,
                                           bit,
                                           AC(Address.zero()),
                                           ConditionOperand.NOT_EQUAL(),
                                           new BranchProfileOperand()));

          if (DynamicTypeCheck.MIN_DOES_IMPLEMENT_SIZE <= interfaceIndex) {
            RegisterOperand doesImplLength =
                InsertGuardedUnary(s, ir, ARRAYLENGTH, TypeReference.Int, doesImpl.copy(), TG());
            RegisterOperand boundscheck = ir.regpool.makeTempInt();
            //save to use the cheaper ADDR version of BOOLEAN_CMP
            s.insertBefore(BooleanCmp.create(BOOLEAN_CMP_ADDR,
                                             boundscheck,
                                             doesImplLength,
                                             AC(Address.fromIntSignExtend(interfaceIndex)),
                                             ConditionOperand.GREATER(),
                                             new BranchProfileOperand()));
            s.insertBefore(Binary.create(INT_AND, result.copyD2D(), result.copyD2U(), boundscheck.copyD2U()));
          }
          Instruction continueAt = s.prevInstructionInCodeOrder();
          s.remove();
          return continueAt;
        } else {
          // A resolved class (cases 5 and 6 in DynamicTypeCheck)
          if (LHSclass.isFinal()) {
            // For a final class, we can do a PTR compare of
            // rhsTIB and the TIB of the class
            Operand classTIB = getTIB(s, ir, LHSclass);
            BooleanCmp.mutate(s,
                              BOOLEAN_CMP_ADDR,
                              result,
                              RHStib,
                              classTIB,
                              ConditionOperand.EQUAL(),
                              new BranchProfileOperand());
            return s.prevInstructionInCodeOrder();
          } else {
            // Do the full blown case 5 or 6 typecheck.
            int LHSDepth = LHSclass.getTypeDepth();
            int LHSId = LHSclass.getId();
            RegisterOperand superclassIds =
                InsertUnary(s, ir, GET_SUPERCLASS_IDS_FROM_TIB, TypeReference.ShortArray, RHStib);
            RegisterOperand refCandidate =
                InsertLoadOffset(s,
                                 ir,
                                 USHORT_LOAD,
                                 TypeReference.Short,
                                 superclassIds,
                                 Offset.fromIntZeroExtend(LHSDepth << 1),
                                 new LocationOperand(TypeReference.Short),
                                 TG());
            //save to use the cheaper ADDR version of BOOLEAN_CMP
            s.insertBefore(BooleanCmp.create(BOOLEAN_CMP_ADDR,
                                             result,
                                             refCandidate,
                                             AC(Address.fromIntZeroExtend(LHSId)),
                                             ConditionOperand.EQUAL(),
                                             new BranchProfileOperand()));
            if (DynamicTypeCheck.MIN_SUPERCLASS_IDS_SIZE <= LHSDepth) {
              RegisterOperand superclassIdsLength =
                  InsertGuardedUnary(s, ir, ARRAYLENGTH, TypeReference.Int, superclassIds.copyD2U(), TG());
              RegisterOperand boundscheck = ir.regpool.makeTempInt();
              //save to use the cheaper ADDR version of BOOLEAN_CMP
              s.insertBefore(BooleanCmp.create(BOOLEAN_CMP_ADDR,
                                               boundscheck,
                                               superclassIdsLength,
                                               AC(Address.fromIntSignExtend(LHSDepth)),
                                               ConditionOperand.GREATER(),
                                               new BranchProfileOperand()));
              s.insertBefore(Binary.create(INT_AND, result.copyD2D(), result.copyD2U(), boundscheck.copyD2U()));
            }
            Instruction continueAt = s.prevInstructionInCodeOrder();
            s.remove();
            return continueAt;
          }
        }
      } else {
        // A non-resolved class or interface.
        // We expect these to be extremely uncommon in opt code in AOS.
        // Mutate s into a call to RuntimeEntrypoints.instanceOf
        RVMMethod target = Entrypoints.instanceOfMethod;
        Call.mutate2(s,
                     CALL,
                     result,
                     AC(target.getOffset()),
                     MethodOperand.STATIC(target),
                     RHSobj,
                     IC(LHStype.getId()));
        return callHelper(s, ir);
      }
    }
    if (LHStype.isArrayType()) {
      // Case 2 of DynamicTypeCheck: LHS is an array.
      RVMArray LHSArray = (RVMArray) LHStype.peekType();
      if (LHSArray != null) {
        RVMType innermostElementType = LHSArray.getInnermostElementType();
        if (innermostElementType.isPrimitiveType() || innermostElementType.isUnboxedType() ||
            (innermostElementType.asClass().isResolved() && innermostElementType.asClass().isFinal())) {
          // [^k of primitive or [^k of final class. Just like final classes,
          // a PTR compare of rhsTIB and the TIB of the class gives the answer.
          Operand classTIB = getTIB(s, ir, LHSArray);
          BooleanCmp.mutate(s,
                            BOOLEAN_CMP_ADDR,
                            result,
                            RHStib,
                            classTIB,
                            ConditionOperand.EQUAL(),
                            new BranchProfileOperand());
          return s;
        }
      }
      // We're going to have to branch anyways, so reduce to a branching case
      // and do the real work there.
      return convertToBranchingTypeCheck(s, ir, RHSobj, LHStype, RHStib, result);
    }
    OptimizingCompilerException.UNREACHABLE();
    return null;
  }

  /**
   * Generate wrapper around branching type check to get a
   * value producing type check.
   * @param s        The Instruction that is to be replaced by
   *                  a value producing type check
   * @param ir       The IR containing the instruction to be expanded.
   * @param RHSobj   The RegisterOperand containing the rhs object.
   * @param LHStype  The TypeReference to be tested against.
   * @param RHStib   The Operand containing the TIB of the rhs.
   * @param result   The RegisterOperand that the result of dynamic
   * @return the opt instruction immediately before the instruction to
   *         continue expansion.
   */
  private static Instruction convertToBranchingTypeCheck(Instruction s, IR ir, Operand RHSobj,
                                                             TypeReference LHStype, Operand RHStib,
                                                             RegisterOperand result) {
    BasicBlock myBlock = s.getBasicBlock();
    BasicBlock contBlock = myBlock.splitNodeAt(s, ir);
    BasicBlock trueBlock = myBlock.createSubBlock(s.bcIndex, ir);
    BasicBlock falseBlock = myBlock.createSubBlock(s.bcIndex, ir);
    myBlock.insertOut(trueBlock);
    myBlock.insertOut(falseBlock);
    trueBlock.insertOut(contBlock);
    falseBlock.insertOut(contBlock);
    ir.cfg.linkInCodeOrder(myBlock, trueBlock);
    ir.cfg.linkInCodeOrder(trueBlock, falseBlock);
    ir.cfg.linkInCodeOrder(falseBlock, contBlock);
    trueBlock.appendInstruction(Move.create(INT_MOVE, result, IC(1)));
    trueBlock.appendInstruction(Goto.create(GOTO, contBlock.makeJumpTarget()));
    falseBlock.appendInstruction(Move.create(INT_MOVE, result.copyD2D(), IC(0)));
    return generateBranchingTypeCheck(s,
                                      ir,
                                      RHSobj,
                                      LHStype,
                                      RHStib,
                                      trueBlock,
                                      falseBlock,
                                      ir.regpool.makeTempValidation(),
                                      new BranchProfileOperand());
  }

  /**
   * Generate a branching dynamic type check.
   * This routine assumes that the CFG and code order are already
   * correctly established.
   * This routine must either remove s or mutate it.
   *
   * @param s          The Instruction that is to be replaced by a
   *                   branching type check
   * @param ir         The IR containing the instruction to be expanded.
   * @param RHSobj     The RegisterOperand containing the rhs object.
   * @param LHStype    The TypeReference to be tested against.
   * @param RHStib     The Operand containing the TIB of the rhs.
   * @param trueBlock  The BasicBlock to continue at if the typecheck
   *                   evaluates to true
   * @param falseBlock The BasicBlock to continue at if the typecheck
   *                   evaluates to false.
   * @param falseProb   The probability that typecheck will branch to the falseBlock
   * @return the opt instruction immediately before the instruction to
   *         continue expansion.
   */
  private static Instruction generateBranchingTypeCheck(Instruction s, IR ir, Operand RHSobj,
                                                            TypeReference LHStype, Operand RHStib,
                                                            BasicBlock trueBlock, BasicBlock falseBlock,
                                                            RegisterOperand oldGuard,
                                                            BranchProfileOperand falseProb) {
    Instruction continueAt = Goto.create(GOTO, trueBlock.makeJumpTarget());
    continueAt.copyPosition(s);
    s.insertBefore(continueAt);
    s.remove();

    if (LHStype.isClassType()) {
      RVMClass LHSclass = (RVMClass) LHStype.peekType();
      if (LHSclass != null && LHSclass.isResolved()) {
        // Cases 4, 5, and 6 of DynamicTypeCheck: LHSclass is a resolved
        // class or interface
        if (LHSclass.isInterface()) {
          // A resolved interface (case 4)
          int interfaceIndex = LHSclass.getDoesImplementIndex();
          int interfaceMask = LHSclass.getDoesImplementBitMask();
          RegisterOperand doesImpl =
              InsertUnary(continueAt, ir, GET_DOES_IMPLEMENT_FROM_TIB, TypeReference.IntArray, RHStib);

          if (DynamicTypeCheck.MIN_DOES_IMPLEMENT_SIZE <= interfaceIndex) {
            RegisterOperand doesImplLength =
                InsertGuardedUnary(continueAt, ir, ARRAYLENGTH, TypeReference.Int, doesImpl.copyD2U(), TG());
            Instruction lengthCheck =
                IfCmp.create(INT_IFCMP,
                             oldGuard,
                             doesImplLength,
                             IC(interfaceIndex),
                             ConditionOperand.LESS_EQUAL(),
                             falseBlock.makeJumpTarget(),
                             BranchProfileOperand.unlikely());
            if (oldGuard != null) {
              oldGuard = oldGuard.copyD2D();
            }
            continueAt.insertBefore(lengthCheck);
            BasicBlock oldBlock = continueAt.getBasicBlock();
            oldBlock.splitNodeWithLinksAt(lengthCheck, ir);
            oldBlock.insertOut(falseBlock); // required due to splitNode!
          }
          RegisterOperand entry =
              InsertLoadOffset(continueAt,
                               ir,
                               INT_LOAD,
                               TypeReference.Int,
                               doesImpl,
                               Offset.fromIntZeroExtend(interfaceIndex << 2),
                               new LocationOperand(TypeReference.Int),
                               TG());
          RegisterOperand bit =
              insertBinary(continueAt, ir, INT_AND, TypeReference.Int, entry, IC(interfaceMask));
          continueAt.insertBefore(IfCmp.create(INT_IFCMP,
                                               oldGuard,
                                               bit,
                                               IC(0),
                                               ConditionOperand.EQUAL(),
                                               falseBlock.makeJumpTarget(),
                                               falseProb));
          return continueAt;
        } else {
          // A resolved class (cases 5 and 6 in DynamicTypeCheck)
          if (LHSclass.isFinal()) {
            // For a final class, we can do a PTR compare of
            // rhsTIB and the TIB of the class
            Operand classTIB = getTIB(continueAt, ir, LHSclass);
            continueAt.insertBefore(IfCmp.create(REF_IFCMP,
                                                 oldGuard,
                                                 RHStib,
                                                 classTIB,
                                                 ConditionOperand.NOT_EQUAL(),
                                                 falseBlock.makeJumpTarget(),
                                                 falseProb));
            return continueAt;
          } else {
            // Do the full blown case 5 or 6 typecheck.
            int LHSDepth = LHSclass.getTypeDepth();
            int LHSId = LHSclass.getId();
            RegisterOperand superclassIds =
                InsertUnary(continueAt, ir, GET_SUPERCLASS_IDS_FROM_TIB, TypeReference.ShortArray, RHStib);
            if (DynamicTypeCheck.MIN_SUPERCLASS_IDS_SIZE <= LHSDepth) {
              RegisterOperand superclassIdsLength =
                  InsertGuardedUnary(continueAt, ir, ARRAYLENGTH, TypeReference.Int, superclassIds.copyD2U(), TG());
              Instruction lengthCheck =
                  IfCmp.create(INT_IFCMP,
                               oldGuard,
                               superclassIdsLength,
                               IC(LHSDepth),
                               ConditionOperand.LESS(),
                               falseBlock.makeJumpTarget(),
                               BranchProfileOperand.unlikely());
              if (oldGuard != null) {
                oldGuard = oldGuard.copyD2D();
              }
              continueAt.insertBefore(lengthCheck);
              BasicBlock oldBlock = continueAt.getBasicBlock();
              oldBlock.splitNodeWithLinksAt(lengthCheck, ir);
              oldBlock.insertOut(falseBlock); // required due to splitNode!
            }
            RegisterOperand refCandidate =
                InsertLoadOffset(continueAt,
                                 ir,
                                 USHORT_LOAD,
                                 TypeReference.Short,
                                 superclassIds,
                                 Offset.fromIntZeroExtend(LHSDepth << 1),
                                 new LocationOperand(TypeReference.Short),
                                 TG());
            continueAt.insertBefore(IfCmp.create(INT_IFCMP,
                                                 oldGuard,
                                                 refCandidate,
                                                 IC(LHSId),
                                                 ConditionOperand.NOT_EQUAL(),
                                                 falseBlock.makeJumpTarget(),
                                                 falseProb));
            return continueAt;
          }
        }
      } else {
        // A non-resolved class or interface. Case 3 of DynamicTypeCheck
        // Branch on the result of a call to
        // RuntimeEntrypoints.instance
        RegisterOperand result = ir.regpool.makeTempInt();
        RVMMethod target = Entrypoints.instanceOfMethod;
        Instruction call =
            Call.create2(CALL,
                         result,
                         AC(target.getOffset()),
                         MethodOperand.STATIC(target),
                         RHSobj,
                         IC(LHStype.getId()));
        call.copyPosition(continueAt);
        continueAt.insertBefore(call);
        call = callHelper(call, ir);
        continueAt.insertBefore(IfCmp.create(INT_IFCMP,
                                             oldGuard,
                                             result.copyD2U(),
                                             IC(0),
                                             ConditionOperand.EQUAL(),
                                             falseBlock.makeJumpTarget(),
                                             falseProb));
        return continueAt;
      }
    }

    if (LHStype.isArrayType()) {
      // Case 2 of DynamicTypeCheck: LHS is an array.
      RVMArray LHSArray = (RVMArray) LHStype.peekType();
      if (LHSArray != null) {
        Operand classTIB = getTIB(continueAt, ir, LHSArray);
        RVMType innermostElementType = LHSArray.getInnermostElementType();
        if (innermostElementType.isPrimitiveType() || innermostElementType.isUnboxedType() ||
            (innermostElementType.asClass().isResolved() && innermostElementType.asClass().isFinal())) {
          // [^k of primitive or [^k of final class. Just like final classes,
          // a PTR compare of rhsTIB and the TIB of the class gives the answer.
          continueAt.insertBefore(IfCmp.create(REF_IFCMP,
                                               oldGuard,
                                               RHStib,
                                               classTIB,
                                               ConditionOperand.NOT_EQUAL(),
                                               falseBlock.makeJumpTarget(),
                                               falseProb));
          return continueAt;
        }
        // TODO: branch probability calculation is somewhat bogus for this case.
        Instruction shortcircuit =
            IfCmp.create(REF_IFCMP,
                         oldGuard,
                         RHStib,
                         classTIB,
                         ConditionOperand.EQUAL(),
                         trueBlock.makeJumpTarget(),
                         new BranchProfileOperand());
        if (oldGuard != null) {
          oldGuard = oldGuard.copyD2D();
        }
        continueAt.insertBefore(shortcircuit);
        BasicBlock myBlock = shortcircuit.getBasicBlock();
        BasicBlock mainBlock = myBlock.splitNodeWithLinksAt(shortcircuit, ir);
        myBlock.insertOut(trueBlock);       // must come after the splitNodeAt
        RegisterOperand rhsType =
            InsertUnary(continueAt, ir, GET_TYPE_FROM_TIB, TypeReference.Type, RHStib.copy());
        if (innermostElementType.isJavaLangObjectType()) {
          IntConstantOperand lhsDimension = IC(LHStype.getDimensionality());
          RegisterOperand rhsDimension = getField(continueAt, ir, rhsType, Entrypoints.dimensionField);
          Instruction dimTest =
              IfCmp2.create(INT_IFCMP2,
                            oldGuard,
                            rhsDimension,
                            lhsDimension,
                            ConditionOperand.GREATER(),
                            trueBlock.makeJumpTarget(),
                            ((BranchProfileOperand) falseProb.copy()).flip(),
                            ConditionOperand.LESS(),
                            falseBlock.makeJumpTarget(),
                            (BranchProfileOperand) falseProb.copy());
          if (oldGuard != null) {
            oldGuard = oldGuard.copyD2D();
          }
          continueAt.insertBefore(dimTest);
          //BasicBlock testBlock =
          mainBlock.splitNodeWithLinksAt(dimTest, ir);
          mainBlock.insertOut(trueBlock);
          mainBlock.insertOut(falseBlock);
          RegisterOperand rhsInnermostElementTypeDimension =
              getField(continueAt, ir, rhsType.copyU2U(), Entrypoints.innermostElementTypeDimensionField);
          continueAt.insertBefore(IfCmp.create(INT_IFCMP,
                                               oldGuard,
                                               rhsInnermostElementTypeDimension,
                                               IC(0),
                                               ConditionOperand.NOT_EQUAL(),
                                               falseBlock.makeJumpTarget(),
                                               falseProb));
          return continueAt;
        }
      }

      // Not a case we want to handle inline
      RVMMethod target = Entrypoints.instanceOfMethod;
      RegisterOperand callResult = ir.regpool.makeTempInt();
      Instruction call =
          Call.create2(CALL,
                       callResult,
                       AC(target.getOffset()),
                       MethodOperand.STATIC(target),
                       RHSobj,
                       IC(LHStype.getId()));
      call.copyPosition(continueAt);
      continueAt.insertBefore(call);
      call = callHelper(call, ir);
      continueAt.insertBefore(IfCmp.create(INT_IFCMP,
                                           oldGuard,
                                           callResult.copyD2U(),
                                           IC(0),
                                           ConditionOperand.EQUAL(),
                                           falseBlock.makeJumpTarget(),
                                           falseProb));
      return continueAt;
    }
    OptimizingCompilerException.UNREACHABLE();
    return null;
  }

  // helper routine.
  // s is a conditional branch; Make it the last instruction in its block
  // if it isn't already and return the fallthrough block.
  private static BasicBlock fallThroughBB(Instruction s, IR ir) {
    Instruction next = s.nextInstructionInCodeOrder();
    if (next.operator() == BBEND) {
      return next.getBasicBlock().nextBasicBlockInCodeOrder();
    } else if (next.operator() == GOTO) {
      BasicBlock target = next.getBranchTarget();
      next.remove();
      return target;
    } else {
      BasicBlock myBlock = s.getBasicBlock();
      BasicBlock succBlock = myBlock.splitNodeAt(s, ir);
      myBlock.insertOut(succBlock);
      ir.cfg.linkInCodeOrder(myBlock, succBlock);
      return succBlock;
    }
  }

  private static BasicBlock advanceBlock(int bcIndex, BasicBlock curBlock, IR ir) {
    BasicBlock newBlock = curBlock.createSubBlock(bcIndex, ir);
    curBlock.insertOut(newBlock);
    ir.cfg.linkInCodeOrder(curBlock, newBlock);
    return newBlock;
  }

}
