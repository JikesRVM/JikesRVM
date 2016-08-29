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
package org.jikesrvm.compilers.opt.regalloc;

import static org.jikesrvm.classloader.ClassLoaderConstants.LongTypeCode;
import static org.jikesrvm.compilers.opt.ir.IRDumpTools.dumpIR;
import static org.jikesrvm.osr.OSRConstants.ACONST;
import static org.jikesrvm.osr.OSRConstants.ICONST;
import static org.jikesrvm.osr.OSRConstants.LCONST;
import static org.jikesrvm.osr.OSRConstants.PHYREG;
import static org.jikesrvm.osr.OSRConstants.SPILL;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.osr.LocalRegPair;
import org.jikesrvm.osr.MethodVariables;
import org.jikesrvm.osr.VariableMapElement;
import org.vmmagic.unboxed.Word;

/**
 * Update GC maps after register allocation but before inserting spill
 * code.
 */
public final class UpdateOSRMaps extends CompilerPhase {

  @Override
  public boolean shouldPerform(OptOptions options) {
    return true;
  }

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  @Override
  public String getName() {
    return "Update OSRMaps";
  }

  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   * Iterate over the IR-based OSR map, and update symbolic registers
   * with real reg number or spill locations.
   * Verify there are only two types of operands:
   *    ConstantOperand
   *    RegisterOperand
   *        for integer constant, we save the value of the integer
   *
   * The LONG register has another half part.
   *
   * CodeSpill replaces any allocated symbolic register by
   * physical registers.
   */
  @Override
  public void perform(IR ir) throws OptimizingCompilerException {
    // list of OsrVariableMapElement
    //LinkedList<VariableMapElement> mapList = ir.MIRInfo.osrVarMap.list;
    //for (int numOsrs=0, m=mapList.size(); numOsrs<m; numOsrs++) {
    //  VariableMapElement elm = mapList.get(numOsrs);
    /* for each osr instruction */
    for (VariableMapElement elm : ir.MIRInfo.osrVarMap.list) {

      // for each inlined method
      //LinkedList<MethodVariables> mvarsList = elm.mvars;                   XXX Remove once proven correct
      //for (int numMvars=0, n=mvarsList.size(); numMvars<n; numMvars++) {
      //  MethodVariables mvar = mvarsList.get(numMvars);
      for (MethodVariables mvar : elm.mvars) {

        // for each tuple
        //LinkedList<LocalRegPair> tupleList = mvar.tupleList;
        //for (int numTuple=0, k=tupleList.size(); numTuple<k; numTuple++) {
        //LocalRegPair tuple = tupleList.get(numTuple);
        for (LocalRegPair tuple : mvar.tupleList) {

          Operand op = tuple.operand;
          if (op.isRegister()) {
            Register sym_reg = ((RegisterOperand) op).getRegister();

            setRealPosition(ir, tuple, sym_reg);

            // get another half part of long register
            if (VM.BuildFor32Addr && (tuple.typeCode == LongTypeCode)) {

              LocalRegPair other = tuple._otherHalf;
              Operand other_op = other.operand;

              if (VM.VerifyAssertions) VM._assert(other_op.isRegister());

              Register other_reg = ((RegisterOperand) other_op).getRegister();
              setRealPosition(ir, other, other_reg);
            }
            /* According to ConvertToLowLevelIR, StringConstant, LongConstant,
            * NullConstant, FloatConstant, and DoubleConstant are all materialized
            * The only thing left is the integer constants which could encode
            * non-moveable objects.
            * POTENTIAL DRAWBACKS: since any long, float, and double are moved
            * to register and treated as use, it may consume more registers and
            * add unnecessary MOVEs.
            *
            * Perhaps, ConvertToLowLevelIR can skip OsrPoint instruction.
            */
          } else if (op.isIntConstant()) {
            setTupleValue(tuple, ICONST, ((IntConstantOperand) op).value);
            if (VM.BuildFor32Addr && (tuple.typeCode == LongTypeCode)) {
              LocalRegPair other = tuple._otherHalf;
              Operand other_op = other.operand;

              if (VM.VerifyAssertions) VM._assert(other_op.isIntConstant());
              setTupleValue(other, ICONST, ((IntConstantOperand) other_op).value);
            }
          } else if (op.isAddressConstant()) {
            setTupleValue(tuple, ACONST, ((AddressConstantOperand) op).value.toWord());
          } else if (VM.BuildFor64Addr && op.isLongConstant()) {
            setTupleValue(tuple, LCONST, Word.fromLong(((LongConstantOperand) op).value));
          } else {
            throw new OptimizingCompilerException("LinearScan", "Unexpected operand type at ", op.toString());
          } // for the op type
        } // for each tuple
      } // for each inlined method
    } // for each osr instruction
  }

  void setRealPosition(IR ir, LocalRegPair tuple, Register sym_reg) {
    if (VM.VerifyAssertions) VM._assert(sym_reg != null);

    int REG_MASK = 0x01F;

    // now it is not symbolic register anymore.
    // is is really confusing that sometimes a sym reg is a phy,
    // and sometimes not.
    if (sym_reg.isAllocated()) {
      setTupleValue(tuple, PHYREG, sym_reg.number & REG_MASK);
    } else if (sym_reg.isPhysical()) {
      setTupleValue(tuple, PHYREG, sym_reg.number & REG_MASK);
    } else if (sym_reg.isSpilled()) {
      int spillLocation = ir.MIRInfo.regAllocState.getSpill(sym_reg);
      setTupleValue(tuple, SPILL, spillLocation);
    } else {
      dumpIR(ir, "PANIC");
      throw new RuntimeException("LinearScan PANIC in OSRMAP, " + sym_reg + " is not alive");
    }
  }

  static void setTupleValue(LocalRegPair tuple, byte type, int value) {
    tuple.valueType = type;
    tuple.value = Word.fromIntSignExtend(value);
  }

  static void setTupleValue(LocalRegPair tuple, byte type, Word value) {
    tuple.valueType = type;
    tuple.value = value;
  }
}
