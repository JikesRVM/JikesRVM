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
package org.jikesrvm.osr.ppc;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants;
import org.jikesrvm.osr.OSRConstants;
import org.jikesrvm.osr.EncodedOSRMap;
import org.jikesrvm.osr.ExecutionStateExtractor;
import org.jikesrvm.osr.ExecutionState;
import org.jikesrvm.osr.OSRMapIterator;
import org.jikesrvm.osr.VariableElement;
import org.jikesrvm.ppc.ArchConstants;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;
import org.vmmagic.unboxed.WordArray;

/**
 * OptExecutionStateExtractor is a subclass of ExecutionStateExtractor.
 * It extracts the execution state of a optimized activation.
 */
public abstract class OptExecutionStateExtractor extends ExecutionStateExtractor
    implements ArchConstants, OSRConstants, PhysicalRegisterConstants {

  @Override
  public ExecutionState extractState(RVMThread thread, Offset osrFPoff, Offset methFPoff, int cmid) {

    /* perform machine and compiler dependent operations here
    * osrFPoff is the fp offset of
    * OptSaveVolatile.threadSwithFrom<...>
    *
    *  (stack grows downward)
    *          foo
    *     |->     <-- methFPoff
    *     |
    *     |    <tsfrom>
    *     |--     <-- osrFPoff
    *
    *
    * The <tsfrom> saves all volatiles, nonvolatiles, and scratch
    * registers. All register values for 'foo' can be obtained from
    * the register save area of '<tsfrom>' method.
    */

    byte[] stack = thread.getStack();

    // get registers for the caller ( real method )
    TempRegisters registers = new TempRegisters(thread.getContextRegisters());

    if (VM.VerifyAssertions) {
      int foocmid = Magic.getIntAtOffset(stack, methFPoff.plus(STACKFRAME_METHOD_ID_OFFSET));
      if (foocmid != cmid) {
        for (Offset o = osrFPoff; o.sGE(methFPoff.minus(2 * BYTES_IN_ADDRESS)); o = o.minus(BYTES_IN_ADDRESS)) {
          VM.sysWriteHex(Magic.objectAsAddress(stack).plus(o));
          VM.sysWrite(" : ");
          VM.sysWriteHex(Magic.getWordAtOffset(stack, o).toAddress());
          VM.sysWriteln();
        }

        CompiledMethod cm = CompiledMethods.getCompiledMethod(cmid);
        VM.sysWriteln("unmatch method, it should be " + cm.getMethod());
        CompiledMethod foo = CompiledMethods.getCompiledMethod(foocmid);
        VM.sysWriteln("but now it is " + foo.getMethod());
        walkOnStack(stack, osrFPoff);
      }
      VM._assert(foocmid == cmid);
    }

    OptCompiledMethod fooCM = (OptCompiledMethod) CompiledMethods.getCompiledMethod(cmid);

    /* Following code get the machine code offset to the
     * next instruction. All operation of the stack frame
     * are kept in GC critical section.
     * All code in the section should not cause any GC
     * activities, and avoid lazy compilation.
     */
    // get the next machine code offset of the real method
    VM.disableGC();
    Address methFP = Magic.objectAsAddress(stack).plus(methFPoff);
    Address nextIP = Magic.getNextInstructionAddress(methFP);
    Offset ipOffset = fooCM.getInstructionOffset(nextIP);
    VM.enableGC();

    EncodedOSRMap fooOSRMap = fooCM.getOSRMap();

    /* get register reference map from OSR map
     * we are using this map to convert addresses to objects,
     * thus we can operate objects out of GC section.
     */
    int regmap = fooOSRMap.getRegisterMapForMCOffset(ipOffset);

    {
      int bufCMID = Magic.getIntAtOffset(stack, osrFPoff.plus(STACKFRAME_METHOD_ID_OFFSET));
      CompiledMethod bufCM = CompiledMethods.getCompiledMethod(bufCMID);

      // SaveVolatile can only be compiled by OPT compiler
      if (VM.VerifyAssertions) {
        VM._assert(bufCM instanceof OptCompiledMethod);
      }

      restoreValuesFromOptSaveVolatile(stack, osrFPoff, registers, regmap, bufCM);
    }

    // return a list of states: from caller to callee
    // if the osr happens in an inlined method, the state is
    // a chain of recoverd methods.
    ExecutionState state =
        getExecStateSequence(thread, stack, ipOffset, methFPoff, cmid, osrFPoff, registers, fooOSRMap);

    // reverse callerState points
    ExecutionState prevState = null;
    ExecutionState nextState = state;
    while (nextState != null) {
      // 1. current node
      state = nextState;
      // 1. hold the next state first
      nextState = nextState.callerState;
      // 2. redirect pointer
      state.callerState = prevState;
      // 3. move prev to current
      prevState = state;
    }

    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("OptExecutionState : recovered states");
      ExecutionState temp = state;
      while (temp != null) {
        VM.sysWriteln(temp.toString());
        temp = temp.callerState;
      }
    }

    return state;
  }

  /* OptSaveVolatile has different stack layout from DynamicBridge
   * Have to separately recover them now, but there should be unified
   * later on. TODO:
   *
   * Current SaveVolatile stack frame:
   *
   *   GPR 3 -- 14 15 16 17 -- 31, cr, xer, ctr, FPR 0 -- 15
   */
  private void restoreValuesFromOptSaveVolatile(byte[] stack, Offset osrFPoff, TempRegisters registers, int regmap,
                                                CompiledMethod cm) {

    OptCompiledMethod tsfromCM = (OptCompiledMethod) cm;

    Offset nvArea = osrFPoff.plus(tsfromCM.getUnsignedNonVolatileOffset());

    WordArray gprs = registers.gprs;
    double[] fprs = registers.fprs;

    // temporarialy hold ct, xer, ctr register
    int cr = 0;
    int xer = 0;
    Word ctr = Word.zero();

    // enter critical section
    // precall methods potientially causing dynamic compilation
    int firstGPR = tsfromCM.getFirstNonVolatileGPR();

    VM.disableGC();

    // recover volatile GPRs.
    Offset lastVoffset = nvArea;
    for (int i = LAST_SCRATCH_GPR; i >= FIRST_VOLATILE_GPR; i--) {
      lastVoffset = lastVoffset.minus(BYTES_IN_STACKSLOT);
      gprs.set(i, Magic.objectAsAddress(stack).loadWord(lastVoffset));
    }

    // recover nonvolatile GPRs
    if (firstGPR != -1) {
      for (int i = firstGPR; i <= LAST_NONVOLATILE_GPR; i++) {
        gprs.set(i, Magic.objectAsAddress(stack).loadWord(nvArea));
        nvArea = nvArea.plus(BYTES_IN_STACKSLOT);
      }
    }

    // recover CR, XER, and CTR
    cr = Magic.getIntAtOffset(stack, nvArea);
    nvArea = nvArea.plus(BYTES_IN_STACKSLOT);
    xer = Magic.getIntAtOffset(stack, nvArea);
    nvArea = nvArea.plus(BYTES_IN_STACKSLOT);
    ctr = Magic.getWordAtOffset(stack, nvArea);
    nvArea = nvArea.plus(BYTES_IN_STACKSLOT);

    /*
    // it should be aligned ready
    // it may have a padding before FPRs.
    int offset = nvArea - osrFPoff;
    offset = (offset + BYTES_IN_STACKSLOT) & ~BYTES_IN_STACKSLOT;
    nvArea = osrFPoff + offset;
    */

    // recover all volatile FPRs
    for (int i = FIRST_SCRATCH_FPR; i <= LAST_VOLATILE_FPR; i++) {
      long lbits = Magic.getLongAtOffset(stack, nvArea);
      fprs[i] = Magic.longBitsAsDouble(lbits);
      nvArea = nvArea.plus(BYTES_IN_DOUBLE);
    }

    // convert addresses in registers to references
    for (int i = 1; i < NUM_GPRS; i++) {
      if (EncodedOSRMap.registerIsSet(regmap, i)) {
        registers.objs[i] = Magic.addressAsObject(registers.gprs.get(i).toAddress());
      }
    }

    VM.enableGC();

    registers.cr = cr;
    registers.xer = xer;
    registers.ctr = ctr;
  }

  private ExecutionState getExecStateSequence(RVMThread thread, byte[] stack, Offset ipOffset, Offset fpOffset,
                                                  int cmid, Offset tsFPOffset, TempRegisters registers,
                                                  EncodedOSRMap osrmap) {

    // go through the stack frame and extract values
    // In the variable value list, we keep the order as follows:
    // L0, L1, ..., S0, S1, ....

    /* go over osr map element, build list of VariableElement.
    * assuming iterator has ordered element as
    *     L0, L1, ..., S0, S1, ...
    *
    *     RVMThread.ThreadSwitch
    *     OptSaveVolatile.threadSwitchFromDeopt
    *     FOO                                        <-- fpOffset
    *
    * Also, all registers saved by threadSwitchFromDeopt method
    * is restored in "registers", address for object is converted
    * back to object references.
    *
    * This method should be called in non-GC critical section since
    * it allocates many objects.
    */

    // for 64-bit type values which have two int parts.
    // this holds the high part.
    int lpart_one = 0;

    // now recover execution states
    OSRMapIterator iterator = osrmap.getOsrMapIteratorForMCOffset(ipOffset);
    if (VM.VerifyAssertions) VM._assert(iterator != null);

    ExecutionState state = new ExecutionState(thread, fpOffset, cmid, iterator.getBcIndex(), tsFPOffset);

    MethodReference mref = MemberReference.getMemberRef(iterator.getMethodId()).asMethodReference();
    state.setMethod((NormalMethod) mref.peekResolvedMethod());
    state.callerState = null;

    while (iterator.hasMore()) {

      if (iterator.getMethodId() != state.meth.getId()) {
        ExecutionState newstate = new ExecutionState(thread, fpOffset, cmid, iterator.getBcIndex(), tsFPOffset);
        mref = MemberReference.getMemberRef(iterator.getMethodId()).asMethodReference();
        newstate.setMethod((NormalMethod) mref.peekResolvedMethod());
        // this is not caller, but the callee, reverse it when outside
        // of this function.
        newstate.callerState = state;

        state = newstate;
      }

      // create a VariableElement for it.
      boolean kind = iterator.getKind();
      int num = iterator.getNumber();
      byte tcode = iterator.getTypeCode();
      byte vtype = iterator.getValueType();
      int value = iterator.getValue();

      iterator.moveToNext();

      /*
      System.out.println("kind  "+kind);
      System.out.println("num   "+num);
      System.out.println("tcode "+tcode);
      System.out.println("vtype "+vtype);
      System.out.println("value "+value);
      */

      switch (tcode) {
        case INT: {
          int ibits = getIntBitsFrom(vtype, value, stack, fpOffset, registers);
          state.add(new VariableElement(kind, num, tcode, ibits));
          break;
        }
        case FLOAT: {
          float fv = (float) getDoubleFrom(vtype, value, stack, fpOffset, registers);
          int ibits = Magic.floatAsIntBits(fv);
          state.add(new VariableElement(kind, num, tcode, ibits));
          break;
        }
        case HIGH_64BIT: {
          lpart_one = value;
          break;
        }
        case LONG: {
          long lbits = getLongBitsFrom(vtype, lpart_one, value, stack, fpOffset, registers);
          lpart_one = 0;
          state.add(new VariableElement(kind, num, LONG,    // not use LONG2,
                                            lbits));

          break;
        }
        case DOUBLE: {
          double dv = getDoubleFrom(vtype, value, stack, fpOffset, registers);
          long lbits = Magic.doubleAsLongBits(dv);
          state.add(new VariableElement(kind, num, tcode, lbits));
          break;
        }
        // I believe I did not handle return address correctly because
        // the opt compiler did inlining of JSR/RET.
        // To be VERIFIED.
        case RET_ADDR: {
          int bcIndex = getIntBitsFrom(vtype, value, stack, fpOffset, registers);
          state.add(new VariableElement(kind, num, tcode, bcIndex));
          break;
        }
        case REF: {
          Object ref = getObjectFrom(vtype, value, stack, fpOffset, registers);

          state.add(new VariableElement(kind, num, tcode, ref));
          break;
        }
        case WORD: {
          if (VM.BuildFor32Addr) {
            int word = getIntBitsFrom(vtype, value, stack, fpOffset, registers);
            state.add(new VariableElement(kind, num, tcode, word));
          } else {
            long word = getLongBitsFrom(vtype, lpart_one, value, stack, fpOffset, registers);
            lpart_one = 0;
            state.add(new VariableElement(kind, num, tcode, word));
          }
          break;
        }
        default:
          if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
          break;
      } // switch
    } // for loop

    return state;
  }

  /** auxillary functions to get value from different places. */
  private static int getIntBitsFrom(int vtype, int value, byte[] stack, Offset fpOffset, TempRegisters registers) {
    // for INT_CONST type, the value is the value
    if (vtype == ICONST || vtype == ACONST) {
      return value;

      // for physical register type, it is the register number
      // because all registers are saved in threadswitch's stack
      // frame, we get value from it.
    } else if (vtype == PHYREG) {
      return registers.gprs.get(value).toInt();

      // for spilled locals, the value is the spilled position
      // it is on FOO's stackframe.
      // ASSUMING, spill offset is offset to FP in bytes.
    } else if (vtype == SPILL) {

      Offset offset = fpOffset.plus(value + BYTES_IN_STACKSLOT - BYTES_IN_INT);
      return Magic.getIntAtOffset(stack, offset);

    } else {
      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
      return -1;
    }
  }

  private static long getLongBitsFrom(int vtype, int valueHigh, int valueLow, byte[] stack, Offset fpOffset,
                                      TempRegisters registers) {

    // for LCONST type, the value is the value
    if (vtype == LCONST || vtype == ACONST) {
      return ((((long) valueHigh) << 32) | (((long) valueLow) & 0x0FFFFFFFF));

    } else if (VM.BuildFor32Addr) {
      // for physical register type, it is the register number
      // because all registers are saved in threadswitch's stack
      // frame, we get value from it.
      if (vtype == PHYREG) {
        return ((((long) registers.gprs.get(valueHigh).toInt()) << 32) |
                ((registers.gprs.get(valueLow).toInt()) & 0x0FFFFFFFFL));

        // for spilled locals, the value is the spilled position
        // it is on FOO's stackframe.
        // ASSUMING, spill offset is offset to FP in bytes.
      } else if (vtype == SPILL) {
        long lvalue = ((long) Magic.getIntAtOffset(stack, fpOffset.plus(valueHigh))) << 32;
        return (lvalue | ((Magic.getIntAtOffset(stack, fpOffset.plus(valueLow))) & 0x0FFFFFFFFL));
      }

    } else if (VM.BuildFor64Addr) {
      // for physical register type, it is the register number
      // because all registers are saved in threadswitch's stack
      // frame, we get value from it.
      if (vtype == PHYREG) {
        return registers.gprs.get(valueLow).toLong();

        // for spilled locals, the value is the spilled position
        // it is on FOO's stackframe.
        // ASSUMING, spill offset is offset to FP in bytes.
      } else if (vtype == SPILL) {
        return Magic.getLongAtOffset(stack, fpOffset.plus(valueLow));
      }
    }
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return -1L;
  }

  private static double getDoubleFrom(int vtype, int value, byte[] stack, Offset fpOffset,
                                      TempRegisters registers) {
    if (vtype == PHYREG) {
      return registers.fprs[value];
    } else if (vtype == SPILL) {
      long lbits = Magic.getLongAtOffset(stack, fpOffset.plus(value));
      return Magic.longBitsAsDouble(lbits);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      return -1.0;
    }
  }

  private static Object getObjectFrom(int vtype, int value, byte[] stack, Offset fpOffset,
                                      TempRegisters registers) {
    if (vtype == ACONST) {
      // the only constant object is NULL, I believe.
      if (VM.VerifyAssertions) VM._assert(value == 0);
      return Magic.addressAsObject(Address.zero());
    } else if (vtype == PHYREG) {
      return registers.objs[value];
    } else if (vtype == SPILL) {
      return Magic.getObjectAtOffset(stack, fpOffset.plus(value));
    } else {
      VM.sysWrite("fatal error : ( vtype = " + vtype + " )\n");
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      return null;
    }
  }

  @SuppressWarnings("unused")
  private static void dumpStackContent(byte[] stack, Offset fpOffset) {
    VM.disableGC();
    Address upper = Magic.objectAsAddress(stack).loadAddress(fpOffset);
    Offset upOffset = upper.diff(Magic.objectAsAddress(stack));
    VM.enableGC();

    int cmid = Magic.getIntAtOffset(stack, fpOffset.plus(STACKFRAME_METHOD_ID_OFFSET));
    OptCompiledMethod cm = (OptCompiledMethod) CompiledMethods.getCompiledMethod(cmid);

    VM.sysWrite("stack of " + cm.getMethod() + "\n");
    VM.sysWrite(" NV area offset " + cm.getUnsignedNonVolatileOffset() + "\n");
    VM.sysWrite("   first NV GPR " + cm.getFirstNonVolatileGPR() + "\n");
    VM.sysWrite("   first NV FPR " + cm.getFirstNonVolatileFPR() + "\n");

    for (int i = 0; fpOffset.sLT(upOffset); i += BYTES_IN_STACKSLOT, fpOffset = fpOffset.plus(BYTES_IN_STACKSLOT)) {
      Word content = Magic.getWordAtOffset(stack, fpOffset);
      VM.sysWrite("    0x");
      VM.sysWrite(content);
      VM.sysWrite("      " + i + "\n");
    }
  }

  /* walk on stack frame, print out methods
   */
  private static void walkOnStack(byte[] stack, Offset fpOffset) {
    int cmid = STACKFRAME_SENTINEL_FP.toInt();
    do {
      cmid = Magic.getIntAtOffset(stack, fpOffset.plus(STACKFRAME_METHOD_ID_OFFSET));
      if (cmid == INVISIBLE_METHOD_ID) {
        VM.sysWriteln(" invisible method ");
      } else {
        CompiledMethod cm = CompiledMethods.getCompiledMethod(cmid);
        VM.sysWriteln(cm.getMethod().toString());
      }
      VM.disableGC();
      Address callerfp = Magic.objectAsAddress(stack).loadAddress(fpOffset.plus(STACKFRAME_FRAME_POINTER_OFFSET));
      fpOffset = callerfp.diff(Magic.objectAsAddress(stack));
      VM.enableGC();
    } while (cmid != STACKFRAME_SENTINEL_FP.toInt());
  }
}
