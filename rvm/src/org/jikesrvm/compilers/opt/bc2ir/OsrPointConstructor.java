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
package org.jikesrvm.compilers.opt.bc2ir;

import static org.jikesrvm.classloader.ClassLoaderConstants.VoidTypeCode;
import static org.jikesrvm.compilers.opt.ir.Operators.OSR_BARRIER_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.OSR_BARRIER;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedList;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.controlflow.BranchOptimizations;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.OsrBarrier;
import org.jikesrvm.compilers.opt.ir.OsrPoint;
import org.jikesrvm.compilers.opt.ir.operand.InlinedOsrTypeInfoOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.OsrTypeInfoOperand;

/**
 * A phase in the OPT compiler for construction OsrPoint instructions
 * after inlining.
 */
public class OsrPointConstructor extends CompilerPhase {

  @Override
  public final boolean shouldPerform(OptOptions options) {
    return VM.runningVM && options.OSR_GUARDED_INLINING;
  }

  @Override
  public final String getName() {
    return "OsrPointConstructor";
  }

  @Override
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  private static final Constructor<CompilerPhase> constructor =
      getCompilerPhaseConstructor(OsrPointConstructor.class);

  /**
   * Need to run branch optimizations after
   */
  private final BranchOptimizations branchOpts;

  private Collection<Instruction> osrBarriers;

  private LinkedList<Instruction> osrPoints;

  /**
   * Constructor
   */
  public OsrPointConstructor() {
    branchOpts = new BranchOptimizations(-1, false, false);
  }

  /**
   * Goes through each instruction, reconstruct OsrPoint instructions.
   */
  @Override
  public void perform(IR ir) {
    // 1. collecting OsrPoint and OsrBarrier instructions
    collectOsrPointsAndBarriers(ir);

    //    new IRPrinter("before renovating osrs").perform(ir);

    // 2. trace OsrBarrier for each OsrPoint, and rebuild OsrPoint
    renovateOsrPoints(ir);

    //    new IRPrinter("before removing barriers").perform(ir);

    // 3. remove OsrBarriers
    removeOsrBarriers(ir);

    //    new IRPrinter("after removing barriers").perform(ir);

    // 4. reconstruct CFG, cut off pieces after OsrPoint.
    fixupCFGForOsr(ir);
/*
        if (VM.TraceOnStackReplacement && (0 != osrs.size())) {
      new IRPrinter("After OsrPointConstructor").perform(ir);
    }
*/
/*
    if (VM.TraceOnStackReplacement && (0 != osrs.size())) {
      verifyNoOsrBarriers(ir);
    }
*/
    branchOpts.perform(ir);
  }

  /**
   * Iterates over all instructions in the IR and builds a list of
   * OsrPoint instructions and OsrBarrier instructions.
   *
   * @param ir the IR
   */
  private void collectOsrPointsAndBarriers(IR ir) {
    osrPoints = new LinkedList<Instruction>();
    osrBarriers = new LinkedList<Instruction>();

    Enumeration<Instruction> instenum = ir.forwardInstrEnumerator();
    while (instenum.hasMoreElements()) {
      Instruction inst = instenum.nextElement();

      if (OsrPoint.conforms(inst)) {
        osrPoints.add(inst);
      } else if (inst.operator() == OSR_BARRIER) {
        osrBarriers.add(inst);
      }
    }
  }

  /**
   * For each OsrPoint instruction, traces its OsrBarriers created by
   * inlining. rebuild OsrPoint instruction to hold all necessary
   * information to recover from inlined activation.
   * @param ir the IR
   */
  private void renovateOsrPoints(IR ir) {

    for (int osrIdx = 0, osrSize = osrPoints.size(); osrIdx < osrSize; osrIdx++) {
      Instruction osr = osrPoints.get(osrIdx);
      LinkedList<Instruction> barriers = new LinkedList<Instruction>();

      // Step 1: collect barriers put before inlined method
      //         in the order of from inner to outer
      {
        GenerationContext gc = ir.getGc();
        Instruction bar = gc.getOSRBarrierFromInst(osr);

        if (osr.position() == null) osr.setPosition(bar.position());

        adjustBCIndex(osr);

        while (bar != null) {

          barriers.add(bar);

          // verify each barrier is clean
          if (VM.VerifyAssertions) {
            if (!isBarrierClean(bar)) {
              VM.sysWriteln("Barrier " + bar + " is not clean!");
            }
            VM._assert(isBarrierClean(bar));
          }

          Instruction callsite = bar.position().getCallSite();
          if (callsite != null) {
            bar = gc.getOSRBarrierFromInst(callsite);

            if (bar == null) {
              VM.sysWrite("call site :" + callsite);
              if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
            }

            adjustBCIndex(bar);
          } else {
            bar = null;
          }
        }
      }

      int inlineDepth = barriers.size();

      if (VM.VerifyAssertions) {
        if (inlineDepth == 0) {
          VM.sysWriteln("Inlining depth for " + osr + " is 0!");
        }
        VM._assert(inlineDepth != 0);
      }

      // Step 2: make a new InlinedOsrTypeOperand from barriers
      int[] methodids = new int[inlineDepth];
      int[] bcindexes = new int[inlineDepth];
      byte[][] localTypeCodes = new byte[inlineDepth][];
      byte[][] stackTypeCodes = new byte[inlineDepth][];

      int totalOperands = 0;
      // first iteration, count the size of total locals and stack sizes
      for (int barIdx = 0, barSize = barriers.size(); barIdx < barSize; barIdx++) {

        Instruction bar = barriers.get(barIdx);
        methodids[barIdx] = bar.position().method.getId();
        bcindexes[barIdx] = bar.getBytecodeIndex();

        OsrTypeInfoOperand typeInfo = OsrBarrier.getTypeInfo(bar);
        localTypeCodes[barIdx] = typeInfo.localTypeCodes;
        stackTypeCodes[barIdx] = typeInfo.stackTypeCodes;

        // count the number of operand, but ignore VoidTypeCode
        totalOperands += OsrBarrier.getNumberOfElements(bar);

        /*
        if (VM.TraceOnStackReplacement) {
          VM.sysWriteln("OsrBarrier : "+bar.bcIndex
                        +"@"+bar.position.method
                        +" "+typeInfo);
        }
        */
      }

      // new make InlinedOsrTypeInfoOperand
      InlinedOsrTypeInfoOperand typeInfo =
          new InlinedOsrTypeInfoOperand(methodids, bcindexes, localTypeCodes, stackTypeCodes);

      OsrPoint.mutate(osr, osr.operator(), typeInfo, totalOperands);

      // Step 3: second iteration, copy operands
      int opIndex = 0;
      for (int barIdx = 0, barSize = barriers.size(); barIdx < barSize; barIdx++) {

        Instruction bar = barriers.get(barIdx);
        for (int elmIdx = 0, elmSize = OsrBarrier.getNumberOfElements(bar); elmIdx < elmSize; elmIdx++) {

          Operand op = OsrBarrier.getElement(bar, elmIdx);

          if (VM.VerifyAssertions) {
            if (op == null) {
              VM.sysWriteln(elmIdx + "th Operand of " + bar + " is null!");
            }
            VM._assert(op != null);
          }

          if (op.isRegister()) {
            op = op.asRegister().copyU2U();
          } else {
            op = op.copy();
          }

          OsrPoint.setElement(osr, opIndex, op);
          opIndex++;
        }
      }
/*
      if (VM.TraceOnStackReplacement) {
        VM.sysWriteln("renovated OsrPoint instruction "+osr);
        VM.sysWriteln("  position "+osr.bcIndex+"@"+osr.position.method);
      }
*/
      // the last OsrBarrier should in the current method
      if (VM.VerifyAssertions) {
        Instruction lastBar = barriers.getLast();
        if (ir.method != lastBar.position().method) {
          VM.sysWriteln("The last barrier is not in the same method as osr:");
          VM.sysWriteln(lastBar + "@" + lastBar.position().method);
          VM.sysWriteln("current method @" + ir.method);
        }
        VM._assert(ir.method == lastBar.position().method);

        if (opIndex != totalOperands) {
          VM.sysWriteln("opIndex and totalOperands do not match:");
          VM.sysWriteln("opIndex = " + opIndex);
          VM.sysWriteln("totalOperands = " + totalOperands);
        }
        VM._assert(opIndex == totalOperands);
      } // end of assertion
    } // end of for loop
  }

  /**
   * The OsrBarrier instruction is not in IR, so the bc index was not
   * adjusted in OSR_AdjustBCIndex.
   *
   * @param barrier the OSR barrier instruction
   */
  private void adjustBCIndex(Instruction barrier) {
    NormalMethod source = barrier.position().method;
    if (source.isForOsrSpecialization()) {
      barrier.adjustBytecodeIndex(-source.getOsrPrologueLength());
    }
  }

  private void removeOsrBarriers(IR ir) {
    for (Instruction inst : osrBarriers) {
      inst.remove();
    }
    ir.getGc().discardOSRBarrierInformation();
  }

  @SuppressWarnings("unused")
  // it's a debugging tool
  private void verifyNoOsrBarriers(IR ir) {
    VM.sysWrite("Verifying no osr barriers");
    Enumeration<Instruction> instenum = ir.forwardInstrEnumerator();
    while (instenum.hasMoreElements()) {
      Instruction inst = instenum.nextElement();
      if (inst.getOpcode() == OSR_BARRIER_opcode) {
        VM.sysWriteln(" NOT SANE");
        VM.sysWriteln(inst.toString());
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
        break;
      }
    }
    VM.sysWriteln(" SANE");
  }

  /**
   * Determines if the barrier is clean by checking the number of valid operands.
   * @param barrier the instruction to verifiy
   * @return {@code true} if and only if the barrier is clean
   */
  private boolean isBarrierClean(Instruction barrier) {
    OsrTypeInfoOperand typeInfo = OsrBarrier.getTypeInfo(barrier);
    int totalOperands = countNonVoidTypes(typeInfo.localTypeCodes);
    totalOperands += countNonVoidTypes(typeInfo.stackTypeCodes);
    return (totalOperands == OsrBarrier.getNumberOfElements(barrier));
  }

  private int countNonVoidTypes(byte[] typeCodes) {
    int count = 0;
    for (int idx = 0, size = typeCodes.length; idx < size; idx++) {
      if (typeCodes[idx] != VoidTypeCode) {
        count++;
      }
    }
    return count;
  }

  /**
   * Splits each OsrPoint, and connects it to the exit point.
   * @param ir the IR
   */
  private void fixupCFGForOsr(IR ir) {
    for (int i = 0, n = osrPoints.size(); i < n; i++) {
      Instruction osr = osrPoints.get(i);
      BasicBlock bb = osr.getBasicBlock();
      BasicBlock newBB = bb.segregateInstruction(osr, ir);
      bb.recomputeNormalOut(ir);
      newBB.recomputeNormalOut(ir);
    }
  }
}
