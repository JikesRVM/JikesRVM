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
package org.jikesrvm.osr.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.opt.regalloc.ia32.PhysicalRegisterConstants;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.ia32.ArchConstants;
import org.jikesrvm.osr.OSRConstants;
import org.jikesrvm.osr.EncodedOSRMap;
import org.jikesrvm.osr.ExecutionStateExtractor;
import org.jikesrvm.osr.ExecutionState;
import org.jikesrvm.osr.OSRMapIterator;
import org.jikesrvm.osr.VariableElement;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;
import org.vmmagic.unboxed.WordArray;

/**
 * OptExecutionStateExtractor is a subclass of ExecutionStateExtractor.
 * It extracts the execution state from an optimized activation.
 */
public abstract class OptExecutionStateExtractor extends ExecutionStateExtractor
    implements Constants, ArchConstants, OSRConstants, PhysicalRegisterConstants {

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
    * The threadSwitchFrom method saves all volatiles, nonvolatiles, and
    * scratch registers. All register values for 'foo' can be obtained
    * from the register save area of '<tsfrom>' method.
    */

    byte[] stack = thread.getStack();

    // get registers for the caller ( real method )
    TempRegisters registers = new TempRegisters(thread.contextRegisters);

    if (VM.VerifyAssertions) {
      int foocmid = Magic.getIntAtOffset(stack, methFPoff.plus(STACKFRAME_METHOD_ID_OFFSET));
      if (foocmid != cmid) {
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

    /* Following code is architecture dependent. In IA32, the return address
     * saved in caller stack frames, so use osrFP to get the next instruction
     * address of foo
     */

    // get the next machine code offset of the real method
    VM.disableGC();
    Address osrFP = Magic.objectAsAddress(stack).plus(osrFPoff);
    Address nextIP = Magic.getReturnAddress(osrFP);
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

      // offset in bytes, convert it to stack words from fpIndex
      // SaveVolatile can only be compiled by OPT compiler
      if (VM.VerifyAssertions) VM._assert(bufCM instanceof OptCompiledMethod);
      restoreValuesFromOptSaveVolatile(stack, osrFPoff, registers, regmap, bufCM);
    }

    // return a list of states: from caller to callee
    // if the osr happens in an inlined method, the state is
    // a chain of recoverd methods.
    ExecutionState state =
        getExecStateSequence(thread, stack, ipOffset, methFPoff, cmid, osrFPoff, registers, fooOSRMap);

    // reverse callerState points, it becomes callee -> caller
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
      VM.sysWriteln("OptExecState : recovered states " + thread.toString());
      ExecutionState temp = state;
      do {
        VM.sysWriteln(temp.toString());
        temp = temp.callerState;
      } while (temp != null);
    }

    return state;
  }

  /* OptSaveVolatile has different stack layout from DynamicBridge
    * Have to separately recover them now, but there should be unified
    * later on.
    *
    *    |----------|
    *    |   NON    |
    *    |Volatiles |
    *    |          | <-- volatile offset
    *    |Volatiles |
    *    |          |
    *    |FPR states|
    *    |__________|  ___ FP
  */
  private void restoreValuesFromOptSaveVolatile(byte[] stack, Offset osrFPoff, TempRegisters registers, int regmap,
                                                CompiledMethod cm) {

    OptCompiledMethod tsfromCM = (OptCompiledMethod) cm;

    boolean saveVolatile = tsfromCM.isSaveVolatile();
    if (VM.VerifyAssertions) {
      VM._assert(saveVolatile);
    }

    WordArray gprs = registers.gprs;

    // enter critical section
    // precall methods potientially causing dynamic compilation
    int firstNonVolatile = tsfromCM.getFirstNonVolatileGPR();
    int nonVolatiles = tsfromCM.getNumberOfNonvolatileGPRs();
    int nonVolatileOffset = tsfromCM.getUnsignedNonVolatileOffset() + (nonVolatiles - 1) * BYTES_IN_STACKSLOT;

    VM.disableGC();

    // recover nonvolatile GPRs
    for (int i = firstNonVolatile + nonVolatiles - 1; i >= firstNonVolatile; i--) {
      gprs.set(NONVOLATILE_GPRS[i].value(), Magic.objectAsAddress(stack).loadWord(osrFPoff.minus(nonVolatileOffset)));
      nonVolatileOffset -= BYTES_IN_STACKSLOT;
    }

    // restore with VOLATILES yet
    int volatileOffset = nonVolatileOffset;
    for (int i = NUM_VOLATILE_GPRS - 1; i >= 0; i--) {
      gprs.set(VOLATILE_GPRS[i].value(), Magic.objectAsAddress(stack).loadWord(osrFPoff.minus(volatileOffset)));
      volatileOffset -= BYTES_IN_STACKSLOT;
    }

    // currently, all FPRS are volatile on intel,
    // DO nothing.

    // convert addresses in registers to references, starting from register 0
    // powerPC starts from register 1
    for (int i = 0; i < NUM_GPRS; i++) {
      if (EncodedOSRMap.registerIsSet(regmap, i)) {
        registers.objs[i] = Magic.addressAsObject(registers.gprs.get(i).toAddress());
      }
    }

    VM.enableGC();

    if (VM.TraceOnStackReplacement) {
      for (GPR reg : GPR.values()) {
        VM.sysWrite(reg.toString());
        VM.sysWrite(" = ");
        VM.sysWrite(registers.gprs.get(reg.value()).toAddress());
        VM.sysWrite("\n");
      }
    }
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
    *     ThreadSwitch
    *     threadSwitchFromOsr
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
    int lvalue_one = 0;
    int lvtype_one = 0;

    // now recover execution states
    OSRMapIterator iterator = osrmap.getOsrMapIteratorForMCOffset(ipOffset);
    if (VM.VerifyAssertions) VM._assert(iterator != null);

    ExecutionState state = new ExecutionState(thread, fpOffset, cmid, iterator.getBcIndex(), tsFPOffset);
    MethodReference mref = MemberReference.getMemberRef(iterator.getMethodId()).asMethodReference();
    state.setMethod((NormalMethod) mref.peekResolvedMethod());
    // this is not caller, but the callee, reverse it when outside
    // of this function.
    state.callerState = null;

    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("osr map table of " + state.meth.toString());
    }

    while (iterator.hasMore()) {

      if (iterator.getMethodId() != state.meth.getId()) {
        ExecutionState newstate = new ExecutionState(thread, fpOffset, cmid, iterator.getBcIndex(), tsFPOffset);
        mref = MemberReference.getMemberRef(iterator.getMethodId()).asMethodReference();
        newstate.setMethod((NormalMethod) mref.peekResolvedMethod());
        // this is not caller, but the callee, reverse it when outside
        // of this function.
        newstate.callerState = state;

        state = newstate;

        if (VM.TraceOnStackReplacement) {
          VM.sysWriteln("osr map table of " + state.meth.toString());
        }

      }

      // create a VariableElement for it.
      boolean kind = iterator.getKind();
      char num = iterator.getNumber();
      byte tcode = iterator.getTypeCode();
      byte vtype = iterator.getValueType();
      int value = iterator.getValue();

      iterator.moveToNext();

      if (VM.TraceOnStackReplacement) {
        VM.sysWrite((kind == LOCAL) ? "L" : "S");
        VM.sysWrite((int)num);
        VM.sysWrite(" , ");
        if (vtype == ICONST) {
          VM.sysWrite("ICONST ");
          VM.sysWrite(value);
        } else if (vtype == PHYREG) {
          VM.sysWrite("PHYREG ");
          VM.sysWrite(GPR.lookup(value).toString());
        } else if (vtype == SPILL) {
          VM.sysWrite("SPILL  ");
          VM.sysWrite(value);
        }
        VM.sysWriteln();
      }

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
          lvalue_one = value;
          lvtype_one = vtype;
          break;
        }
        case LONG: {
          long lbits = getLongBitsFrom(lvtype_one, lvalue_one, vtype, value, stack, fpOffset, registers);
          lvalue_one = 0;
          lvtype_one = 0;
          state.add(new VariableElement(kind, num, LONG, lbits));

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
        case WORD: { //KV:TODO
          if (VM.BuildFor64Addr) VM._assert(VM.NOT_REACHED);
          int word = getIntBitsFrom(vtype, value, stack, fpOffset, registers);

          state.add(new VariableElement(kind, num, tcode, word));
          break;
        }
        case REF: {
          Object ref = getObjectFrom(vtype, value, stack, fpOffset, registers);

          state.add(new VariableElement(kind, num, tcode, ref));
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

      return Magic.getIntAtOffset(stack, fpOffset.minus(value));

    } else {
      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
      return -1;
    }
  }

  private static long getLongBitsFrom(int vtypeHigh, int valueHigh, int vtypeLow, int valueLow, byte[] stack, Offset fpOffset,
                                      TempRegisters registers) {

    // for LCONST type, the value is the value
    if (vtypeLow == LCONST || vtypeLow == ACONST) {
      if (VM.VerifyAssertions) VM._assert(vtypeHigh == vtypeLow);
      return ((((long) valueHigh) << 32) | (((long) valueLow) & 0x0FFFFFFFFL));

    } else if (VM.BuildFor32Addr) {
      if (VM.VerifyAssertions) VM._assert(vtypeHigh == PHYREG || vtypeHigh == SPILL);
      if (VM.VerifyAssertions) VM._assert(vtypeLow == PHYREG || vtypeLow == SPILL);
      /* For physical registers, value is the register number.
       * For spilled locals, the value is the spilled position on FOO's stackframe. */
      long lowPart, highPart;

      if (vtypeLow == PHYREG) {
        lowPart = ((long)registers.gprs.get(valueLow).toInt()) & 0x0FFFFFFFFL;
      } else {
        lowPart = ((long)Magic.getIntAtOffset(stack, fpOffset.minus(valueLow))) & 0x0FFFFFFFFL;
      }

      if (vtypeHigh == PHYREG) {
        highPart = ((long)registers.gprs.get(valueHigh).toInt());
      } else {
        highPart = ((long)Magic.getIntAtOffset(stack, fpOffset.minus(valueHigh)));
      }

      return (highPart << 32) | lowPart;
    } else if (VM.BuildFor64Addr) {
      // for physical register type, it is the register number
      // because all registers are saved in threadswitch's stack
      // frame, we get value from it.
      if (vtypeLow == PHYREG) {
        return registers.gprs.get(valueLow).toLong();

        // for spilled locals, the value is the spilled position
        // it is on FOO's stackframe.
        // ASSUMING, spill offset is offset to FP in bytes.
      } else if (vtypeLow == SPILL) {
        return Magic.getLongAtOffset(stack, fpOffset.minus(valueLow));
      }
    }
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return -1L;
  }

  private static double getDoubleFrom(int vtype, int value, byte[] stack, Offset fpOffset,
                                      TempRegisters registers) {
    if (vtype == PHYREG) {
      return registers.fprs[value - FIRST_DOUBLE];

    } else if (vtype == SPILL) {

      long lbits = Magic.getLongAtOffset(stack, fpOffset.minus(value));
      return Magic.longBitsAsDouble(lbits);
      //KV:TODO: why not use getDoubleAtOffset ???

    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      return -1.0;
    }
  }

  private static Object getObjectFrom(int vtype, int value, byte[] stack, Offset fpOffset,
                                      TempRegisters registers) {
    if (vtype == ICONST) { //kv:todo : to become ACONST
      // the only constant object for 64bit addressing is NULL
      if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr || value == 0);
      return Magic.addressAsObject(Address.fromIntSignExtend(value));

    } else if (vtype == PHYREG) {
      return registers.objs[value];

    } else if (vtype == SPILL) {
      return Magic.getObjectAtOffset(stack, fpOffset.minus(value));

    } else {
      VM.sysWrite("fatal error : ( vtype = " + vtype + " )\n");
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      return null;
    }
  }

  @SuppressWarnings("unused")
  private static void dumpStackContent(byte[] stack, Offset fpOffset) {
    int cmid = Magic.getIntAtOffset(stack, fpOffset.plus(STACKFRAME_METHOD_ID_OFFSET));
    OptCompiledMethod cm = (OptCompiledMethod) CompiledMethods.getCompiledMethod(cmid);

    int firstNonVolatile = cm.getFirstNonVolatileGPR();
    int nonVolatiles = cm.getNumberOfNonvolatileGPRs();
    int nonVolatileOffset = cm.getUnsignedNonVolatileOffset() + (nonVolatiles - 1) * BYTES_IN_STACKSLOT;

    VM.sysWriteln("stack of " + cm.getMethod());
    VM.sysWriteln("      fp offset ", fpOffset);
    VM.sysWriteln(" NV area offset ", nonVolatileOffset);
    VM.sysWriteln("   first NV GPR ", firstNonVolatile);

    Address aFP = Magic.objectAsAddress(stack).plus(fpOffset);
    for (Address a = aFP.plus(nonVolatileOffset); a.GE(aFP); a = a.minus(BYTES_IN_STACKSLOT)) {
      Word content = a.loadWord();
      VM.sysWriteHex(a);
      VM.sysWrite("  ");
      VM.sysWrite(content);
      VM.sysWriteln();
    }
  }

  @SuppressWarnings("unused")
  private static void dumpRegisterContent(WordArray gprs) {
    for (GPR reg : GPR.values()) {
      VM.sysWrite(reg.toString());
      VM.sysWrite(" = ");
      VM.sysWriteln(gprs.get(reg.value()));
    }
  }

  /* walk on stack frame, print out methods
   */
  private static void walkOnStack(byte[] stack, Offset fpOffset) {
    VM.disableGC();

    Address fp = Magic.objectAsAddress(stack).plus(fpOffset);

    while (Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
      int cmid = Magic.getCompiledMethodID(fp);

      if (cmid == INVISIBLE_METHOD_ID) {
        VM.sysWriteln(" invisible method ");
      } else {
        CompiledMethod cm = CompiledMethods.getCompiledMethod(cmid);
        fpOffset = fp.diff(Magic.objectAsAddress(stack));
        VM.enableGC();

        VM.sysWriteln(cm.getMethod().toString());

        VM.disableGC();
        fp = Magic.objectAsAddress(stack).plus(fpOffset);
        if (cm.getMethod().getDeclaringClass().hasBridgeFromNativeAnnotation()) {
          fp = RuntimeEntrypoints.unwindNativeStackFrame(fp);
        }
      }

      fp = Magic.getCallerFramePointer(fp);
    }

    VM.enableGC();
  }
}
