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

import static org.jikesrvm.SizeConstants.LOG_BYTES_IN_ADDRESS;
import static org.jikesrvm.SizeConstants.LOG_BYTES_IN_INT;
import static org.jikesrvm.compilers.opt.driver.OptConstants.RUNTIME_SERVICES_BCI;
import static org.jikesrvm.compilers.opt.ir.Operators.ARRAYLENGTH;
import static org.jikesrvm.compilers.opt.ir.Operators.BOUNDS_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.BYTE_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.BYTE_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.BYTE_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.BYTE_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.CALL;
import static org.jikesrvm.compilers.opt.ir.Operators.CALL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.CHECKCAST_NOTNULL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.CHECKCAST_UNRESOLVED_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.CHECKCAST_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.GETFIELD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GETSTATIC_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_CLASS_TIB;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_OBJ_TIB;
import static org.jikesrvm.compilers.opt.ir.Operators.GOTO;
import static org.jikesrvm.compilers.opt.ir.Operators.IG_CLASS_TEST_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IG_METHOD_TEST_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INSTANCEOF_NOTNULL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INSTANCEOF_UNRESOLVED_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INSTANCEOF_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_2ADDRSigExt;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_2ADDRZerExt;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ADD;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_IFCMP;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_IFCMP2;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_SHL;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ZERO_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_ZERO_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LOOKUPSWITCH;
import static org.jikesrvm.compilers.opt.ir.Operators.LOOKUPSWITCH_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LOWTABLESWITCH;
import static org.jikesrvm.compilers.opt.ir.Operators.MUST_IMPLEMENT_INTERFACE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.OBJARRAY_STORE_CHECK_NOTNULL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.OBJARRAY_STORE_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PUTFIELD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PUTSTATIC_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_IFCMP;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.RESOLVE;
import static org.jikesrvm.compilers.opt.ir.Operators.RESOLVE_MEMBER_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.SHORT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.SHORT_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.SHORT_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.SHORT_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.SYSCALL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.TABLESWITCH_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.TRAP_IF;
import static org.jikesrvm.compilers.opt.ir.Operators.UBYTE_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.UBYTE_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.USHORT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.USHORT_LOAD;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.NEEDS_DYNAMIC_LINK;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.TIB_INTERFACE_DISPATCH_TABLE_INDEX;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.AosEntrypoints;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.InterfaceInvocation;
import org.jikesrvm.classloader.InterfaceMethodSignature;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.controlflow.BranchOptimizations;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.BoundsCheck;
import org.jikesrvm.compilers.opt.ir.CacheOp;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.GetStatic;
import org.jikesrvm.compilers.opt.ir.Goto;
import org.jikesrvm.compilers.opt.ir.GuardedUnary;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.IfCmp2;
import org.jikesrvm.compilers.opt.ir.InlineGuard;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Load;
import org.jikesrvm.compilers.opt.ir.LookupSwitch;
import org.jikesrvm.compilers.opt.ir.LowTableSwitch;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.Store;
import org.jikesrvm.compilers.opt.ir.TableSwitch;
import org.jikesrvm.compilers.opt.ir.TrapIf;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.ZeroCheck;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TIBConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrapCodeOperand;
import org.jikesrvm.compilers.opt.ir.operand.TypeOperand;
import org.jikesrvm.compilers.opt.specialization.SpecializedMethod;
import org.jikesrvm.mm.mminterface.MemoryManagerConstants;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Converts all remaining instructions with HIR-only operators into
 * an equivalent sequence of LIR operators.
 */
public abstract class ConvertToLowLevelIR extends IRTools {

  /**
   * We have slightly different ideas of what the LIR should look like
   * for IA32 and PowerPC.  The main difference is that for IA32
   * instead of bending over backwards in BURS to rediscover array
   * loads, (where we can use base + index*scale addressing modes),
   * we'll leave array loads in the LIR.
   */
  public static final boolean LOWER_ARRAY_ACCESS = VM.BuildForPowerPC;

  /**
   * Converts the given HIR to LIR.
   *
   * @param ir IR to convert
   */
  static void convert(IR ir, OptOptions options) {
    boolean didArrayStoreCheck = false;
    for (Instruction s = ir.firstInstructionInCodeOrder(); s != null; s = s.nextInstructionInCodeOrder()) {

      switch (s.getOpcode()) {
        case GETSTATIC_opcode: {
          LocationOperand loc = GetStatic.getClearLocation(s);
          RegisterOperand result = GetStatic.getClearResult(s);
          Operand address = ir.regpool.makeJTOCOp(ir, s);
          Operand offset = GetStatic.getClearOffset(s);
          Load.mutate(s, IRTools.getLoadOp(loc.getFieldRef(), true), result, address, offset, loc);
        }
        break;

        case PUTSTATIC_opcode: {
          LocationOperand loc = PutStatic.getClearLocation(s);
          Operand value = PutStatic.getClearValue(s);
          Operand address = ir.regpool.makeJTOCOp(ir, s);
          Operand offset = PutStatic.getClearOffset(s);
          Store.mutate(s, IRTools.getStoreOp(loc.getFieldRef(), true), value, address, offset, loc);
        }
        break;

        case PUTFIELD_opcode: {
          LocationOperand loc = PutField.getClearLocation(s);
          Operand value = PutField.getClearValue(s);
          Operand address = PutField.getClearRef(s);
          Operand offset = PutField.getClearOffset(s);
          Store.mutate(s,
                       IRTools.getStoreOp(loc.getFieldRef(), false),
                       value,
                       address,
                       offset,
                       loc,
                       PutField.getClearGuard(s));
        }
        break;

        case GETFIELD_opcode: {
          LocationOperand loc = GetField.getClearLocation(s);
          RegisterOperand result = GetField.getClearResult(s);
          Operand address = GetField.getClearRef(s);
          Operand offset = GetField.getClearOffset(s);
          Load.mutate(s,
                      IRTools.getLoadOp(loc.getFieldRef(), false),
                      result,
                      address,
                      offset,
                      loc,
                      GetField.getClearGuard(s));
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
          doArrayLoad(s, ir, REF_LOAD, LOG_BYTES_IN_ADDRESS);
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
          doArrayStore(s, ir, REF_STORE, LOG_BYTES_IN_ADDRESS);
          break;

        case BYTE_ASTORE_opcode:
          doArrayStore(s, ir, BYTE_STORE, 0);
          break;

        case SHORT_ASTORE_opcode:
          doArrayStore(s, ir, SHORT_STORE, 1);
          break;

        case CALL_opcode:
          s = callHelper(s, ir);
          break;

        case SYSCALL_opcode:
          // If the SYSCALL is using a symbolic address, convert that to
          // a sequence of loads off the BootRecord to find the appropriate field.
          if (Call.getMethod(s) != null) {
            expandSysCallTarget(s, ir);
          }
          break;

        case TABLESWITCH_opcode:
          s = tableswitch(s, ir);
          break;

        case LOOKUPSWITCH_opcode:
          s = lookup(s, ir);
          break;

        case OBJARRAY_STORE_CHECK_opcode:
          s = DynamicTypeCheckExpansion.arrayStoreCheck(s, ir, true);
          didArrayStoreCheck = true;
          break;

        case OBJARRAY_STORE_CHECK_NOTNULL_opcode:
          s = DynamicTypeCheckExpansion.arrayStoreCheck(s, ir, false);
          didArrayStoreCheck = true;
          break;

        case CHECKCAST_opcode:
        case CHECKCAST_UNRESOLVED_opcode:
          s = DynamicTypeCheckExpansion.checkcast(s, ir);
          break;

        case CHECKCAST_NOTNULL_opcode:
          s = DynamicTypeCheckExpansion.checkcastNotNull(s, ir);
          break;

        case MUST_IMPLEMENT_INTERFACE_opcode:
          s = DynamicTypeCheckExpansion.mustImplementInterface(s, ir);
          break;

        case IG_CLASS_TEST_opcode:
          IfCmp.mutate(s,
                       REF_IFCMP,
                       ir.regpool.makeTempValidation(),
                       getTIB(s, ir, InlineGuard.getClearValue(s), InlineGuard.getClearGuard(s)),
                       getTIB(s, ir, InlineGuard.getGoal(s).asType()),
                       ConditionOperand.NOT_EQUAL(),
                       InlineGuard.getClearTarget(s),
                       InlineGuard.getClearBranchProfile(s));
          break;

        case IG_METHOD_TEST_opcode: {
          MethodOperand methOp = InlineGuard.getClearGoal(s).asMethod();
          Operand t1 = getTIB(s, ir, InlineGuard.getClearValue(s), InlineGuard.getClearGuard(s));
          Operand t2 = getTIB(s, ir, methOp.getTarget().getDeclaringClass());
          IfCmp.mutate(s,
                       REF_IFCMP,
                       ir.regpool.makeTempValidation(),
                       getInstanceMethod(s, ir, t1, methOp.getTarget()),
                       getInstanceMethod(s, ir, t2, methOp.getTarget()),
                       ConditionOperand.NOT_EQUAL(),
                       InlineGuard.getClearTarget(s),
                       InlineGuard.getClearBranchProfile(s));
          break;
        }

        case INSTANCEOF_opcode:
        case INSTANCEOF_UNRESOLVED_opcode:
          s = DynamicTypeCheckExpansion.instanceOf(s, ir);
          break;

        case INSTANCEOF_NOTNULL_opcode:
          s = DynamicTypeCheckExpansion.instanceOfNotNull(s, ir);
          break;

        case INT_ZERO_CHECK_opcode: {
          TrapIf.mutate(s,
                        TRAP_IF,
                        ZeroCheck.getClearGuardResult(s),
                        ZeroCheck.getClearValue(s),
                        IC(0),
                        ConditionOperand.EQUAL(),
                        TrapCodeOperand.DivByZero());
        }
        break;

        case LONG_ZERO_CHECK_opcode: {
          TrapIf.mutate(s,
                        TRAP_IF,
                        ZeroCheck.getClearGuardResult(s),
                        ZeroCheck.getClearValue(s),
                        LC(0),
                        ConditionOperand.EQUAL(),
                        TrapCodeOperand.DivByZero());
        }
        break;

        case BOUNDS_CHECK_opcode: {
          // get array_length from array_ref
          RegisterOperand array_length =
              InsertGuardedUnary(s,
                                 ir,
                                 ARRAYLENGTH,
                                 TypeReference.Int,
                                 BoundsCheck.getClearRef(s),
                                 BoundsCheck.getClearGuard(s));
          //  In UN-signed comparison, a negative index will look like a very
          //  large positive number, greater than array length.
          //  Thus length LLT index is false iff 0 <= index <= length
          TrapIf.mutate(s,
                        TRAP_IF,
                        BoundsCheck.getClearGuardResult(s),
                        array_length.copyD2U(),
                        BoundsCheck.getClearIndex(s),
                        ConditionOperand.LOWER_EQUAL(),
                        TrapCodeOperand.ArrayBounds());
        }
        break;

        case RESOLVE_MEMBER_opcode:
          s = resolveMember(s, ir);
          break;

        default:
          break;
      }
    }
    // Eliminate possible redundant trap block from array store checks
    if (didArrayStoreCheck) {
      branchOpts.perform(ir, true);
    }
  }

  private static BranchOptimizations branchOpts = new BranchOptimizations(-1, true, true);

  /**
   * Expand a tableswitch.
   * @param s the instruction to expand
   * @param ir the containing IR
   * @return the last Instruction in the generated LIR sequence.
   */
  static Instruction tableswitch(Instruction s, IR ir) {

    Instruction s2;
    int lowLimit = TableSwitch.getLow(s).value;
    int highLimit = TableSwitch.getHigh(s).value;
    int number = highLimit - lowLimit + 1;
    if (VM.VerifyAssertions) {
      VM._assert(number > 0);    // also checks that there are < 2^31 targets
    }
    Operand val = TableSwitch.getClearValue(s);
    BranchOperand defaultLabel = TableSwitch.getClearDefault(s);
    if (number < ir.options.CONTROL_TABLESWITCH_CUTOFF) { // convert into a lookupswitch
      Instruction l =
          LookupSwitch.create(LOOKUPSWITCH,
                              val,
                              null,
                              null,
                              defaultLabel,
                              TableSwitch.getClearDefaultBranchProfile(s),
                              number * 3);
      for (int i = 0; i < number; i++) {
        LookupSwitch.setMatch(l, i, IC(lowLimit + i));
        LookupSwitch.setTarget(l, i, TableSwitch.getClearTarget(s, i));
        LookupSwitch.setBranchProfile(l, i, TableSwitch.getClearBranchProfile(s, i));
      }
      s.insertAfter(CPOS(s, l));
      return s.remove();
    }
    RegisterOperand reg = val.asRegister();
    BasicBlock BB1 = s.getBasicBlock();
    BasicBlock BB2 = BB1.splitNodeAt(s, ir);
    BasicBlock defaultBB = defaultLabel.target.getBasicBlock();

    /******* First basic block */
    RegisterOperand t;
    if (lowLimit != 0) {
      t = insertBinary(s, ir, INT_ADD, TypeReference.Int, reg, IC(-lowLimit));
    } else {
      t = reg.copyU2U();
    }
    BranchProfileOperand defaultProb = TableSwitch.getClearDefaultBranchProfile(s);
    s.replace(CPOS(s, IfCmp.create(INT_IFCMP,
                        ir.regpool.makeTempValidation(),
                        t,
                        IC(highLimit - lowLimit),
                        ConditionOperand.HIGHER(),
                        defaultLabel,
                        defaultProb)));
    // Reweight branches to account for the default branch going. If
    // the default probability was ALWAYS then when we recompute the
    // weight to be a proportion of the total number of branches.
    final boolean defaultIsAlways = defaultProb.takenProbability >= 1f;
    final float weight = defaultIsAlways ? 1f / number : 1f / (1f - defaultProb.takenProbability);

    /********** second Basic Block ******/
    s2 = CPOS(s, LowTableSwitch.create(LOWTABLESWITCH, t.copyRO(), number * 2));
    boolean containsDefault = false;
    for (int i = 0; i < number; i++) {
      BranchOperand b = TableSwitch.getClearTarget(s, i);
      LowTableSwitch.setTarget(s2, i, b);
      BranchProfileOperand bp = TableSwitch.getClearBranchProfile(s, i);
      if (defaultIsAlways) {
        bp.takenProbability = weight;
      } else {
        bp.takenProbability *= weight;
      }
      LowTableSwitch.setBranchProfile(s2, i, bp);
      if (b.target == defaultLabel.target) {
        containsDefault = true;
      }
    }
    // Fixup the CFG and code order.
    BB1.insertOut(BB2);
    BB1.insertOut(defaultBB);
    ir.cfg.linkInCodeOrder(BB1, BB2);
    if (!containsDefault) {
      BB2.deleteOut(defaultBB);
    }
    // Simplify a fringe case...
    // if all targets of the LOWTABLESWITCH are the same,
    // then just use a GOTO instead of the LOWTABLESWITCH.
    // This actually happens (very occasionally), and is easy to test for.
    if (BB2.getNumberOfNormalOut() == 1) {
      BB2.appendInstruction(CPOS(s, Goto.create(GOTO, LowTableSwitch.getTarget(s2, 0))));
    } else {
      BB2.appendInstruction(s2);
    }
    // continue at next BB
    s = BB2.lastInstruction();

    return s;
  }

  /**
   * Expand a lookupswitch.
   * @param switchInstr  The instruction to expand
   * @param ir           The containing IR
   * @return the next {@link Instruction} after the generated LIR sequence.
   */
  static Instruction lookup(Instruction switchInstr, IR ir) {
    Instruction bbend = switchInstr.nextInstructionInCodeOrder();
    BasicBlock thisBB = bbend.getBasicBlock();
    BasicBlock nextBB = thisBB.nextBasicBlockInCodeOrder();
    // Blow away the old Normal ControlFlowGraph edges to prepare for new links
    thisBB.deleteNormalOut();
    switchInstr.remove();
    BranchOperand defTarget = LookupSwitch.getClearDefault(switchInstr);
    BasicBlock defaultBB = defTarget.target.getBasicBlock();
    int high = LookupSwitch.getNumberOfTargets(switchInstr) - 1;
    if (high < 0) {
      // no cases in switch; just jump to defaultBB
      thisBB.appendInstruction(Goto.create(GOTO, defTarget));
      thisBB.insertOut(defaultBB);
    } else {
      Operand match = LookupSwitch.getValue(switchInstr);
      if (match.isConstant()) {
        // switch on a constant
        int value = match.asIntConstant().value;
        int numMatches = LookupSwitch.getNumberOfMatches(switchInstr);
        BranchOperand target = LookupSwitch.getDefault(switchInstr);
        for (int i = 0; i < numMatches; i++) {
          if (value == LookupSwitch.getMatch(switchInstr, i).value) {
            target = LookupSwitch.getTarget(switchInstr, i);
            break;
          }
        }
        thisBB.appendInstruction(Goto.create(GOTO, target));
        thisBB.insertOut(target.target.getBasicBlock());
      } else {
        RegisterOperand reg = match.asRegister();

        // If you're not already at the end of the code order
        if (nextBB != null) {
          ir.cfg.breakCodeOrder(thisBB, nextBB);
        }
        // generate the binary search tree into thisBB
        BasicBlock lastNewBB =
            _lookupswitchHelper(switchInstr, reg, defaultBB, ir, thisBB, 0, high, Integer.MIN_VALUE, Integer.MAX_VALUE);
        if (nextBB != null) {
          ir.cfg.linkInCodeOrder(lastNewBB, nextBB);
        }
      }
    }

    // skip all the instrs just inserted by _lookupswitchHelper
    if (nextBB != null) {
      return nextBB.firstInstruction();
    } else {
      return thisBB.lastInstruction();
    }
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
  private static BasicBlock _lookupswitchHelper(Instruction switchInstr, RegisterOperand reg,
                                                    BasicBlock defaultBB, IR ir, BasicBlock curBlock,
                                                    int low, int high, int min, int max) {
    if (VM.VerifyAssertions) {
      VM._assert(low <= high, "broken control logic in _lookupswitchHelper");
    }

    int middle = (low + high) >> 1;             // find middle

    // The following are used below to store the computed branch
    // probabilities for the branches that are created to implement
    // the binary search.  Used only if basic block frequencies available
    float lessProb = 0.0f;
    float greaterProb = 0.0f;
    float equalProb = 0.0f;
    float sum = 0.0f;

    // Sum the probabilities for all targets < middle
    for (int i = low; i < middle; i++) {
      lessProb += LookupSwitch.getBranchProfile(switchInstr, i).takenProbability;
    }

    // Sum the probabilities for all targets > middle
    for (int i = middle + 1; i <= high; i++) {
      greaterProb += LookupSwitch.getBranchProfile(switchInstr, i).takenProbability;
    }
    equalProb = LookupSwitch.getBranchProfile(switchInstr, middle).takenProbability;

    // The default case is a bit of a kludge.  We know the total
    // probability of executing the default case, but we have no
    // idea which paths are taken to get there.  For now, we'll
    // assume that all paths that went to default were because the
    // value was less than the smallest switch value.  This ensures
    // that all basic block appearing in the switch will have the
    // correct weights (but the blocks in the binary switch
    // generated may not).
    if (low == 0) {
      lessProb += LookupSwitch.getDefaultBranchProfile(switchInstr).takenProbability;
    }

    // Now normalize them so they are relative to the sum of the
    // branches being considered in this piece of the subtree
    sum = lessProb + equalProb + greaterProb;
    if (sum > 0) {  // check for divide by zero
      lessProb /= sum;
      equalProb /= sum;
      greaterProb /= sum;
    }

    IntConstantOperand val = LookupSwitch.getClearMatch(switchInstr, middle);
    int value = val.value;
    BasicBlock greaterBlock = middle == high ? defaultBB : curBlock.createSubBlock(0, ir);
    BasicBlock lesserBlock = low == middle ? defaultBB : curBlock.createSubBlock(0, ir);
    // Generate this level of tests
    BranchOperand branch = LookupSwitch.getClearTarget(switchInstr, middle);
    BasicBlock branchBB = branch.target.getBasicBlock();
    curBlock.insertOut(branchBB);
    if (low != high) {
      if (value == min) {
        curBlock.appendInstruction(IfCmp.create(INT_IFCMP,
            ir.regpool.makeTempValidation(),
            reg.copy(),
            val,
            ConditionOperand.EQUAL(),
            branchBB.makeJumpTarget(),
            new BranchProfileOperand(equalProb)));
      } else {

        // To compute the probability of the second compare, the first
        // probability must be removed since the second branch is
        // considered only if the first fails.
        float secondIfProb = 0.0f;
        sum = equalProb + greaterProb;
        if (sum > 0) {
          // if divide by zero, leave as is
          secondIfProb = equalProb / sum;
        }

        curBlock.appendInstruction(IfCmp2.create(INT_IFCMP2,
            ir.regpool.makeTempValidation(),
            reg.copy(),
            val,
            ConditionOperand.LESS(),
            lesserBlock.makeJumpTarget(),
            new BranchProfileOperand(lessProb),
            ConditionOperand.EQUAL(),
            branchBB.makeJumpTarget(),
            new BranchProfileOperand(secondIfProb)));
        curBlock.insertOut(lesserBlock);
      }
    } else {      // Base case: middle was the only case left to consider
      if (min == max) {
        curBlock.appendInstruction(Goto.create(GOTO, branch));
        curBlock.insertOut(branchBB);
      } else {
        curBlock.appendInstruction(IfCmp.create(INT_IFCMP,
            ir.regpool.makeTempValidation(),
            reg.copy(),
            val,
            ConditionOperand.EQUAL(),
            branchBB.makeJumpTarget(),
            new BranchProfileOperand(equalProb)));
        BasicBlock newBlock = curBlock.createSubBlock(0, ir);
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
      curBlock = _lookupswitchHelper(switchInstr, reg, defaultBB, ir, greaterBlock, middle + 1, high, value + 1, max);
    }
    if (low < middle) {
      ir.cfg.linkInCodeOrder(curBlock, lesserBlock);
      curBlock = _lookupswitchHelper(switchInstr, reg, defaultBB, ir, lesserBlock, low, middle - 1, min, value - 1);
    }
    return curBlock;
  }

  /**
   * Expand an array load.
   * @param s the instruction to expand
   * @param ir the containing IR
   * @param op the load operator to use
   * @param logwidth the log base 2 of the element type's size
   */
  public static void doArrayLoad(Instruction s, IR ir, Operator op, int logwidth) {
    if (LOWER_ARRAY_ACCESS) {
      RegisterOperand result = ALoad.getClearResult(s);
      Operand array = ALoad.getClearArray(s);
      Operand index = ALoad.getClearIndex(s);
      Operand offset;
      LocationOperand loc = ALoad.getClearLocation(s);
      if (index instanceof IntConstantOperand) {  // constant propagation
        offset = AC(Address.fromIntZeroExtend(((IntConstantOperand) index).value << logwidth));
      } else {
        if (logwidth != 0) {
          offset = insertBinary(s, ir, INT_SHL, TypeReference.Int, index, IC(logwidth));
          offset = InsertUnary(s, ir, INT_2ADDRZerExt, TypeReference.Offset, offset.copy());
        } else {
          offset = InsertUnary(s, ir, INT_2ADDRZerExt, TypeReference.Offset, index);
        }
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
  public static void doArrayStore(Instruction s, IR ir, Operator op, int logwidth) {
    if (LOWER_ARRAY_ACCESS) {
      Operand value = AStore.getClearValue(s);
      Operand array = AStore.getClearArray(s);
      Operand index = AStore.getClearIndex(s);
      Operand offset;
      LocationOperand loc = AStore.getClearLocation(s);
      if (index instanceof IntConstantOperand) {// constant propagation
        offset = AC(Address.fromIntZeroExtend(((IntConstantOperand) index).value << logwidth));
      } else {
        if (logwidth != 0) {
          offset = insertBinary(s, ir, INT_SHL, TypeReference.Int, index, IC(logwidth));
          offset = InsertUnary(s, ir, INT_2ADDRZerExt, TypeReference.Offset, offset.copy());
        } else {
          offset = InsertUnary(s, ir, INT_2ADDRZerExt, TypeReference.Offset, index);
        }
      }
      Store.mutate(s, op, value, array, offset, loc, AStore.getClearGuard(s));
    }
  }

  /**
   * Helper method for call expansion.
   * @param v the call instruction
   * @param ir the containing IR
   * @return the last expanded instruction
   */
  static Instruction callHelper(Instruction v, IR ir) {
    if (!Call.hasMethod(v)) {
      if (VM.VerifyAssertions) VM._assert(Call.getAddress(v) instanceof RegisterOperand);
      return v; // nothing to do....very low level call to address already in the register.
    }

    MethodOperand methOp = Call.getMethod(v);

    // Handle recursive invocations.
    if (methOp.hasPreciseTarget() && methOp.getTarget() == ir.method) {
      Call.setAddress(v, new BranchOperand(ir.firstInstructionInCodeOrder()));
      return v;
    }

    /* RRB 100500 */
    // generate direct call to specialized method if the method operand
    // has been marked as a specialized call.
    if (VM.runningVM) {
      SpecializedMethod spMethod = methOp.spMethod;
      if (spMethod != null) {
        int smid = spMethod.getSpecializedMethodIndex();
        Call.setAddress(v, getSpecialMethod(v, ir, smid));
        return v;
      }
    }

    // Used mainly (only?) by OSR
    if (methOp.hasDesignatedTarget()) {
      Call.setAddress(v, InsertLoadOffsetJTOC(v, ir, REF_LOAD, TypeReference.CodeArray, methOp.jtocOffset));
      return v;
    }

    if (methOp.isStatic()) {
      if (VM.VerifyAssertions) VM._assert(Call.hasAddress(v));
      Call.setAddress(v, InsertLoadOffsetJTOC(v, ir, REF_LOAD, TypeReference.CodeArray, Call.getClearAddress(v)));
    } else if (methOp.isVirtual()) {
      if (VM.VerifyAssertions) VM._assert(Call.hasAddress(v));
      if (ir.options.H2L_CALL_VIA_JTOC && methOp.hasPreciseTarget()) {
        // Call to precise type can go via JTOC
        RVMMethod target = methOp.getTarget();
        Call.setAddress(v,
                        InsertLoadOffsetJTOC(v,
                                             ir,
                                             REF_LOAD,
                                             TypeReference.CodeArray,
                                             target.findOrCreateJtocOffset()));
      } else {
        Operand tib = getTIB(v, ir, Call.getParam(v, 0).copy(), Call.getGuard(v).copy());
        Call.setAddress(v,
                        InsertLoadOffset(v,
                                         ir,
                                         REF_LOAD,
                                         TypeReference.CodeArray,
                                         tib,
                                         Call.getClearAddress(v),
                                         null,
                                         TG()));
      }
    } else if (methOp.isSpecial()) {
      RVMMethod target = methOp.getTarget();
      if (target == null || target.isObjectInitializer() || target.isStatic()) {
        // target == null => we are calling an unresolved <init> method.
        Call.setAddress(v, InsertLoadOffsetJTOC(v, ir, REF_LOAD, TypeReference.CodeArray, Call.getClearAddress(v)));
      } else {
        if (ir.options.H2L_CALL_VIA_JTOC) {
          Call.setAddress(v,
                          InsertLoadOffsetJTOC(v,
                                               ir,
                                               REF_LOAD,
                                               TypeReference.CodeArray,
                                               target.findOrCreateJtocOffset()));
        } else {
          // invoking a virtual method; do it via TIB of target's declaring class.
          Operand tib = getTIB(v, ir, target.getDeclaringClass());
          Call.setAddress(v,
                          InsertLoadOffset(v,
                                           ir,
                                           REF_LOAD,
                                           TypeReference.CodeArray,
                                           tib,
                                           Call.getClearAddress(v),
                                           null,
                                           TG()));
        }
      }
    } else {
      if (VM.VerifyAssertions) VM._assert(methOp.isInterface());
      if (VM.VerifyAssertions) VM._assert(!Call.hasAddress(v));
      if (VM.BuildForIMTInterfaceInvocation) {
        // SEE ALSO: FinalMIRExpansion (for hidden parameter)
        Operand RHStib = getTIB(v, ir, Call.getParam(v, 0).copy(), Call.getGuard(v).copy());
        InterfaceMethodSignature sig = InterfaceMethodSignature.findOrCreate(methOp.getMemberRef());
        Offset offset = sig.getIMTOffset();
        RegisterOperand address = null;
        RegisterOperand IMT =
          InsertLoadOffset(v,
                           ir,
                           REF_LOAD,
                           TypeReference.IMT,
                           RHStib.copy(),
                           Offset.fromIntZeroExtend(TIB_INTERFACE_DISPATCH_TABLE_INDEX << LOG_BYTES_IN_ADDRESS));
        address = InsertLoadOffset(v, ir, REF_LOAD, TypeReference.CodeArray, IMT.copyD2U(), offset);

        Call.setAddress(v, address);
      } else {
        int itableIndex = -1;
        if (VM.BuildForITableInterfaceInvocation && methOp.hasTarget()) {
          RVMClass I = methOp.getTarget().getDeclaringClass();
          // search ITable variant
          itableIndex =
              InterfaceInvocation.getITableIndex(I,
                                                    methOp.getMemberRef().getName(),
                                                    methOp.getMemberRef().getDescriptor());
        }
        if (itableIndex == -1) {
          // itable index is not known at compile-time.
          // call "invokeinterface" to resolve the object and method id
          // into a method address
          RegisterOperand realAddrReg = ir.regpool.makeTemp(TypeReference.CodeArray);
          RVMMethod target = Entrypoints.invokeInterfaceMethod;
          Instruction vp =
              Call.create2(CALL,
                           realAddrReg,
                           AC(target.getOffset()),
                           MethodOperand.STATIC(target),
                           Call.getParam(v, 0).asRegister().copyU2U(),
                           IC(methOp.getMemberRef().getId()));
          vp.position = v.position;
          vp.bcIndex = RUNTIME_SERVICES_BCI;
          v.insertBefore(vp);
          callHelper(vp, ir);
          Call.setAddress(v, realAddrReg.copyD2U());
          return v;
        } else {
          // itable index is known at compile-time.
          // call "findITable" to resolve object + interface id into
          // itable address
          RegisterOperand iTable = ir.regpool.makeTemp(TypeReference.ITable);
          Operand RHStib = getTIB(v, ir, Call.getParam(v, 0).copy(), Call.getGuard(v).copy());
          RVMMethod target = Entrypoints.findItableMethod;
          Instruction fi =
              Call.create2(CALL,
                           iTable,
                           AC(target.getOffset()),
                           MethodOperand.STATIC(target),
                           RHStib,
                           IC(methOp.getTarget().getDeclaringClass().getInterfaceId()));
          fi.position = v.position;
          fi.bcIndex = RUNTIME_SERVICES_BCI;
          v.insertBefore(fi);
          callHelper(fi, ir);
          RegisterOperand address =
              InsertLoadOffset(v,
                               ir,
                               REF_LOAD,
                               TypeReference.CodeArray,
                               iTable.copyD2U(),
                               Offset.fromIntZeroExtend(itableIndex << LOG_BYTES_IN_ADDRESS));
          Call.setAddress(v, address);
          return v;
        }
      }
    }
    return v;
  }

  /**
   * Generate the code to resolve a member (field/method) reference.
   * @param s the RESOLVE_MEMBER instruction to expand
   * @param ir the containing ir object
   * @return the last expanded instruction
   */
  private static Instruction resolveMember(Instruction s, IR ir) {
    Operand memberOp = Unary.getClearVal(s);
    RegisterOperand offset = Unary.getClearResult(s);
    int dictId;
    if (memberOp instanceof LocationOperand) {
      dictId = ((LocationOperand) memberOp).getFieldRef().getId();
    } else {
      dictId = ((MethodOperand) memberOp).getMemberRef().getId();
    }

    BranchProfileOperand bp = BranchProfileOperand.never();
    BasicBlock predBB = s.getBasicBlock();
    BasicBlock succBB = predBB.splitNodeAt(s.prevInstructionInCodeOrder(), ir);
    BasicBlock testBB = predBB.createSubBlock(s.bcIndex, ir, 1f - bp.takenProbability);
    BasicBlock resolveBB = predBB.createSubBlock(s.bcIndex, ir, bp.takenProbability);
    s.remove();

    // Get the offset from the appropriate RVMClassLoader array
    // and check to see if it is valid
    RegisterOperand offsetTable = getStatic(testBB.lastInstruction(), ir, Entrypoints.memberOffsetsField);
    testBB.appendInstruction(Load.create(INT_LOAD,
                                         offset.copyRO(),
                                         offsetTable,
                                         AC(Offset.fromIntZeroExtend(dictId << LOG_BYTES_IN_INT)),
                                         new LocationOperand(TypeReference.Int),
                                         TG()));
    testBB.appendInstruction(Unary.create(INT_2ADDRSigExt, offset, offset.copy()));
    testBB.appendInstruction(IfCmp.create(REF_IFCMP,
        ir.regpool.makeTempValidation(),
        offset.copy(),
        AC(Address.fromIntSignExtend(NEEDS_DYNAMIC_LINK)),
        ConditionOperand.EQUAL(),
        resolveBB.makeJumpTarget(),
        bp));

    // Handle the offset being invalid
    resolveBB.appendInstruction(CacheOp.mutate(s, RESOLVE, memberOp));
    resolveBB.appendInstruction(testBB.makeGOTO());

    // Put together the CFG links & code order
    predBB.insertOut(testBB);
    ir.cfg.linkInCodeOrder(predBB, testBB);
    testBB.insertOut(succBB);
    testBB.insertOut(resolveBB);
    ir.cfg.linkInCodeOrder(testBB, succBB);
    resolveBB.insertOut(testBB);              // backedge
    ir.cfg.addLastInCodeOrder(resolveBB);  // stick resolution code in outer space.
    return testBB.lastInstruction();
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
  public static RegisterOperand insertBinary(Instruction s, IR ir, Operator operator,
                                                 TypeReference type, Operand o1, Operand o2) {
    RegisterOperand t = ir.regpool.makeTemp(type);
    s.insertBefore(CPOS(s, Binary.create(operator, t, o1, o2)));
    return t.copyD2U();
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
  static RegisterOperand InsertUnary(Instruction s, IR ir, Operator operator, TypeReference type,
                                         Operand o1) {
    RegisterOperand t = ir.regpool.makeTemp(type);
    s.insertBefore(CPOS(s, Unary.create(operator, t, o1)));
    return t.copyD2U();
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
  static RegisterOperand InsertGuardedUnary(Instruction s, IR ir, Operator operator,
                                                TypeReference type, Operand o1, Operand guard) {
    RegisterOperand t = ir.regpool.makeTemp(type);
    s.insertBefore(GuardedUnary.create(operator, t, o1, guard));
    return t.copyD2U();
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
  static RegisterOperand InsertLoadOffsetJTOC(Instruction s, IR ir, Operator operator,
                                                  TypeReference type, Offset offset) {
    return InsertLoadOffset(s,
                            ir,
                            operator,
                            type,
                            ir.regpool.makeJTOCOp(ir, s),
                            AC(offset),
                            new LocationOperand(offset),
                            null);
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
  static RegisterOperand InsertLoadOffsetJTOC(Instruction s, IR ir, Operator operator,
                                                  TypeReference type, Operand offset) {
    return InsertLoadOffset(s, ir, operator, type, ir.regpool.makeJTOCOp(ir, s), offset, null, null);
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
  static RegisterOperand InsertLoadOffset(Instruction s, IR ir, Operator operator,
                                              TypeReference type, Operand reg2, Offset offset) {
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
  static RegisterOperand InsertLoadOffset(Instruction s, IR ir, Operator operator,
                                              TypeReference type, Operand reg2, Offset offset,
                                              Operand guard) {
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
  static RegisterOperand InsertLoadOffset(Instruction s, IR ir, Operator operator,
                                              TypeReference type, Operand reg2, Offset offset,
                                              LocationOperand loc, Operand guard) {
    return InsertLoadOffset(s, ir, operator, type, reg2, AC(offset), loc, guard);
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
  static RegisterOperand InsertLoadOffset(Instruction s, IR ir, Operator operator,
                                              TypeReference type, Operand reg2, Operand offset,
                                              LocationOperand loc, Operand guard) {
    RegisterOperand regTarget = ir.regpool.makeTemp(type);
    Instruction s2 = Load.create(operator, regTarget, reg2, offset, loc, guard);
    s.insertBefore(s2);
    return regTarget.copyD2U();
  }

  /** get the tib from the object pointer to by obj */
  static Operand getTIB(Instruction s, IR ir, Operand obj, Operand guard) {
    if (obj.isObjectConstant()) {
      // NB Constant types must already be resolved
      try {
        RVMType type = obj.getType().resolve();
        return new TIBConstantOperand(type);
      } catch (NoClassDefFoundError e) {
        if (VM.runningVM) throw e;
        // Class not found during bootstrap due to chasing a class
        // only valid in the bootstrap JVM
      }
    }
    RegisterOperand res = ir.regpool.makeTemp(TypeReference.TIB);
    Instruction s2 = GuardedUnary.create(GET_OBJ_TIB, res, obj, guard);
    s.insertBefore(s2);
    return res.copyD2U();
  }

  /** get the class tib for type */
  static Operand getTIB(Instruction s, IR ir, RVMType type) {
    return new TIBConstantOperand(type);
    //return getTIB(s, ir, new TypeOperand(type));
  }

  /** get the class tib for type */
  static Operand getTIB(Instruction s, IR ir, TypeOperand type) {
    RVMType t = type.getVMType();
    if (VM.BuildForIA32 && !MemoryManagerConstants.MOVES_TIBS && VM.runningVM && t != null && t.isResolved()) {
      Address addr = Magic.objectAsAddress(t.getTypeInformationBlock());
      return new AddressConstantOperand(addr);
    } else if (!t.isResolved()) {
      RegisterOperand res = ir.regpool.makeTemp(TypeReference.TIB);
      s.insertBefore(Unary.create(GET_CLASS_TIB, res, type));
      return res.copyD2U();
    } else {
      return new TIBConstantOperand(t);
    }
  }

  /**
   * Get an instance method from a TIB
   */
  static RegisterOperand getInstanceMethod(Instruction s, IR ir, Operand tib, RVMMethod method) {
    return InsertLoadOffset(s, ir, REF_LOAD, TypeReference.CodeArray, tib, method.getOffset());
  }

  /**
   * Load an instance field.
   * @param s
   * @param ir
   * @param obj
   * @param field
   */
  public static RegisterOperand getField(Instruction s, IR ir, RegisterOperand obj, RVMField field) {
    return getField(s, ir, obj, field, null);
  }

  /**
   * Load an instance field.
   * @param s
   * @param ir
   * @param obj
   * @param field
   * @param guard
   */
  static RegisterOperand getField(Instruction s, IR ir, RegisterOperand obj, RVMField field,
                                      Operand guard) {
    return InsertLoadOffset(s,
                            ir,
                            IRTools.getLoadOp(field.getType(), field.isStatic()),
                            field.getType(),
                            obj,
                            field.getOffset(),
                            new LocationOperand(field),
                            guard);
  }

  /*  RRB 100500 */
  /**
   * support for direct call to specialized method.
   */
  static RegisterOperand getSpecialMethod(Instruction s, IR ir, int smid) {
    //  First, get the pointer to the JTOC offset pointing to the
    //  specialized Method table
    RegisterOperand reg =
        InsertLoadOffsetJTOC(s,
                             ir,
                             REF_LOAD,
                             TypeReference.JavaLangObjectArray,
                             AosEntrypoints.specializedMethodsField.getOffset());
    RegisterOperand instr =
        InsertLoadOffset(s,
                         ir,
                         REF_LOAD,
                         TypeReference.CodeArray,
                         reg,
                         Offset.fromIntZeroExtend(smid << LOG_BYTES_IN_INT));
    return instr;
  }

  /**
   * Expand symbolic SysCall target into a chain of loads from the bootrecord to
   * the desired target address.
   */
  public static void expandSysCallTarget(Instruction s, IR ir) {
    MethodOperand sysM = Call.getMethod(s);
    if (sysM.getMemberRef().isFieldReference()) {
      RegisterOperand t1 = getStatic(s, ir, Entrypoints.the_boot_recordField);
      RVMField target = sysM.getMemberRef().asFieldReference().resolve();
      Operand ip = getField(s, ir, t1, target);
      Call.setAddress(s, ip);
    }
  }

  /**
   * Load a static field.
   * @param s
   * @param ir
   * @param field
   */
  public static RegisterOperand getStatic(Instruction s, IR ir, RVMField field) {
    return InsertLoadOffsetJTOC(s,
                                ir,
                                IRTools.getLoadOp(field.getType(), field.isStatic()),
                                field.getType(),
                                field.getOffset());
  }
}
