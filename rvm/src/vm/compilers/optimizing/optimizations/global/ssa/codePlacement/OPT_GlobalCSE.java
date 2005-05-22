/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import  java.util.*;
import  com.ibm.JikesRVM.opt.ir.*;

/**
 * This class provides global common sub expression elimination.
 *
 * @author Martin Trapp
 * @modified Stephen Fink
 */
class OPT_GlobalCSE extends OPT_CompilerPhase implements OPT_Operators {

  public boolean verbose = false;

  /**
   * Redefine shouldPerform so that none of the subphases will occur
   * unless we pass through this test.
   */
  public boolean shouldPerform (OPT_Options options) {
    return  options.GCSE;
  }

  /**
   * Returns the name of the phase
   */
  public String getName () {
    return  "Global CSE";
  }
  

  public void perform (OPT_IR ir) {
    if (ir.hasReachableExceptionHandlers() || OPT_GCP.tooBig(ir)) return;
    verbose = OPT_LICM.verbose;
    this.ir = ir;
    dominator = ir.HIRInfo.dominatorTree;
    (new OPT_GlobalValueNumber()).perform(ir);
    valueNumbers = ir.HIRInfo.valueNumbers;
    if (true || ir.IRStage == OPT_IR.LIR) {
      if (verbose) VM.sysWrite ("in GCSE for "+ir.method+"\n");
      OPT_DefUse.computeDU(ir);
      OPT_Simple.copyPropagation(ir);
      OPT_DefUse.computeDU(ir);
      GlobalCSE(ir.firstBasicBlockInCodeOrder());
      if (VM.VerifyAssertions) {
        VM._assert(avail.size() == 0, avail.toString());
      }
      ir.actualSSAOptions.setScalarValid(false);
    }
  }
  
  private OPT_IR ir;
  private static java.util.HashMap avail = new java.util.HashMap();
  private OPT_GlobalValueNumberState valueNumbers;

  /**
   * Do a global CSE for all instructions of block b using the given
   * value numbers 
   * @param b
   */
  private void GlobalCSE (OPT_BasicBlock b) {
    OPT_Instruction next, inst;
    //VM.sysWrite ("Entering Block "+b+"\n");
    inst = b.firstInstruction();
    while (!BBend.conforms(inst)) {
      next = inst.nextInstructionInCodeOrder();
      if (!shouldCSE(inst)) {
        inst = next;
        continue;
      }
      OPT_RegisterOperand result = getResult(inst);
      if (result == null) {
        inst = next;
        continue;
      }
      int vn = valueNumbers.getValueNumber(result);
      if (vn < 0) {
        inst = next;
        continue;
      }
      Integer Vn = new Integer(vn);
      OPT_Instruction former = (OPT_Instruction)avail.get(Vn);
      if (former != null) {
        // instead of trying to repair Heap SSA, we rebuild it after CSE 
        
        // relink scalar dependencies
        OPT_RegisterOperand formerDef = getResult(former);
        OPT_Register reg = result.register;
        formerDef.register.setSpansBasicBlock();
        OPT_RegisterOperandEnumeration uses = OPT_DefUse.uses(reg);
        while (uses.hasMoreElements()) {
          OPT_RegisterOperand use = uses.next();
          OPT_DefUse.transferUse(use, formerDef);
        }
        if (verbose) {
          VM.sysWrite("using      " + former + "\n" + "instead of " + 
                      inst + "\n");
        }
        inst.remove();
      } 
      else {
        //if (verbose) VM.sysWrite ("adding ("+b+") ["+vn+"]"+inst+"\n");
        avail.put(Vn, inst);
      }
      inst = next;
    }
    Enumeration e = dominator.getChildren(b);
    while (e.hasMoreElements()) {
      OPT_DominatorTreeNode n = (OPT_DominatorTreeNode)e.nextElement();
      OPT_BasicBlock bl = n.getBlock();
      if (ir.options.FREQ_FOCUS_EFFORT && bl.getInfrequent()) continue;
      GlobalCSE(bl);
    }
    inst = b.firstInstruction();
    while (!BBend.conforms(inst)) {
      next = inst.nextInstructionInCodeOrder();
      if (!shouldCSE(inst)) {
        inst = next;
        continue;
      }
      OPT_RegisterOperand result = getResult(inst);
      if (result == null) {
        inst = next;
        continue;
      }
      int vn = valueNumbers.getValueNumber(result);
      if (vn < 0) {
        inst = next;
        continue;
      }
      Integer Vn = new Integer(vn);
      OPT_Instruction former = (OPT_Instruction)avail.get(Vn);
      if (former == inst) {
        avail.remove(Vn);
        //if (verbose) VM.sysWrite ("removing ("+b+"): "+inst+"\n");
      }
      inst = next;
    }
    //VM.sysWrite ("Leaving Block "+b+"\n");
  }
  
  /**
   * Get the result operand of the instruction
   * @param inst
   */
  OPT_RegisterOperand getResult (OPT_Instruction inst) {
    if (ResultCarrier.conforms(inst))
      return  ResultCarrier.getResult(inst);
    if (GuardResultCarrier.conforms(inst))
      return  GuardResultCarrier.getGuardResult(inst);
    return  null;
  }


  /**
   * should this instruction be cse'd  ?
   * @param inst
   */
  boolean shouldCSE (OPT_Instruction inst) {
    
    if ((  inst.isAllocation())
        || inst.isDynamicLinkingPoint()
        || inst.isLoad()
        || inst.isStore()
        || inst.operator.opcode >= ARCH_INDEPENDENT_END_opcode)
      return false;
    
    switch (inst.operator.opcode) {
    case INT_MOVE_opcode:
    case LONG_MOVE_opcode:
      //  OPT_Operand ival = Move.getVal(inst);
      //if (ival instanceof OPT_ConstantOperand)
      //        return  false;
      // fall through
    case GET_CLASS_OBJECT_opcode:
    case CHECKCAST_opcode:
    case CHECKCAST_NOTNULL_opcode:
    case CHECKCAST_UNRESOLVED_opcode:
    case MUST_IMPLEMENT_INTERFACE_opcode:
    case INSTANCEOF_opcode:
    case INSTANCEOF_NOTNULL_opcode:
    case INSTANCEOF_UNRESOLVED_opcode:
    case PI_opcode:
    case FLOAT_MOVE_opcode:
    case DOUBLE_MOVE_opcode:
    case REF_MOVE_opcode:
    case GUARD_MOVE_opcode:
    case GUARD_COMBINE_opcode:
    case TRAP_IF_opcode:
    case REF_ADD_opcode:
    case INT_ADD_opcode:
    case LONG_ADD_opcode:
    case FLOAT_ADD_opcode:
    case DOUBLE_ADD_opcode:
    case REF_SUB_opcode:
    case INT_SUB_opcode:
    case LONG_SUB_opcode:
    case FLOAT_SUB_opcode:
    case DOUBLE_SUB_opcode:
    case INT_MUL_opcode:
    case LONG_MUL_opcode:
    case FLOAT_MUL_opcode:
    case DOUBLE_MUL_opcode:
    case INT_DIV_opcode:
    case LONG_DIV_opcode:
    case FLOAT_DIV_opcode:
    case DOUBLE_DIV_opcode:
    case INT_REM_opcode:
    case LONG_REM_opcode:
    case FLOAT_REM_opcode:
    case DOUBLE_REM_opcode:
    case INT_NEG_opcode:
    case LONG_NEG_opcode:
    case FLOAT_NEG_opcode:
    case DOUBLE_NEG_opcode:
    case REF_SHL_opcode:
    case INT_SHL_opcode:
    case LONG_SHL_opcode:
    case REF_SHR_opcode:
    case INT_SHR_opcode:
    case LONG_SHR_opcode:
    case REF_USHR_opcode:
    case INT_USHR_opcode:
    case LONG_USHR_opcode:
    case REF_AND_opcode:
    case INT_AND_opcode:
    case LONG_AND_opcode:
    case REF_OR_opcode:
    case INT_OR_opcode:
    case LONG_OR_opcode:
    case REF_XOR_opcode:
    case INT_XOR_opcode:
    case REF_NOT_opcode:
    case INT_NOT_opcode:
    case LONG_NOT_opcode:
    case LONG_XOR_opcode:
    case INT_2LONG_opcode:
    case INT_2FLOAT_opcode:
    case INT_2DOUBLE_opcode:
    case INT_2ADDRSigExt_opcode:
    case INT_2ADDRZerExt_opcode:
  //-#if RVM_FOR_64_ADDR
    case LONG_2ADDR_opcode:
  //-#endif
    case ADDR_2INT_opcode:
    case ADDR_2LONG_opcode:
    case LONG_2INT_opcode:
    case LONG_2FLOAT_opcode:
    case LONG_2DOUBLE_opcode:
    case FLOAT_2INT_opcode:
    case FLOAT_2LONG_opcode:
    case FLOAT_2DOUBLE_opcode:
    case DOUBLE_2INT_opcode:
    case DOUBLE_2LONG_opcode:
    case DOUBLE_2FLOAT_opcode:
    case INT_2BYTE_opcode:
    case INT_2USHORT_opcode:
    case INT_2SHORT_opcode:
    case LONG_CMP_opcode:
    case FLOAT_CMPL_opcode:
    case FLOAT_CMPG_opcode:
    case DOUBLE_CMPL_opcode:
    case DOUBLE_CMPG_opcode:
    case NULL_CHECK_opcode:
    case BOUNDS_CHECK_opcode:
    case INT_ZERO_CHECK_opcode:
    case LONG_ZERO_CHECK_opcode:
    case OBJARRAY_STORE_CHECK_opcode:
    case OBJARRAY_STORE_CHECK_NOTNULL_opcode:
    case BOOLEAN_NOT_opcode:
    case BOOLEAN_CMP_INT_opcode:
    case BOOLEAN_CMP_ADDR_opcode:
    case FLOAT_AS_INT_BITS_opcode:
    case INT_BITS_AS_FLOAT_opcode:
    case DOUBLE_AS_LONG_BITS_opcode:
    case LONG_BITS_AS_DOUBLE_opcode:
    case ARRAYLENGTH_opcode:
    case GET_OBJ_TIB_opcode:
    case GET_CLASS_TIB_opcode:
    case GET_TYPE_FROM_TIB_opcode:
    case GET_SUPERCLASS_IDS_FROM_TIB_opcode:
    case GET_DOES_IMPLEMENT_FROM_TIB_opcode:
    case GET_ARRAY_ELEMENT_TIB_FROM_TIB_opcode:
      return !(OPT_GCP.usesOrDefsPhysicalRegisterOrAddressType(inst));
    }
    return false;
  }

  private OPT_DominatorTree dominator;
}
