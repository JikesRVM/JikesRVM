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
import org.jikesrvm.Constants;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.baseline.BaselineCompiledMethod;
import org.jikesrvm.compilers.baseline.ppc.BaselineCompilerImpl;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants;
import org.jikesrvm.osr.BytecodeTraverser;
import org.jikesrvm.osr.OSRConstants;
import org.jikesrvm.osr.ExecutionStateExtractor;
import org.jikesrvm.osr.ExecutionState;
import org.jikesrvm.osr.VariableElement;
import org.jikesrvm.ppc.BaselineConstants;
import org.jikesrvm.ppc.Registers;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;
import org.vmmagic.unboxed.WordArray;

/**
 * BaselineExecutionStateExtractor retrieves the runtime state from a suspended
 * thread whose top method was compiled by a baseline compiler.
 */

public abstract class BaselineExecutionStateExtractor extends ExecutionStateExtractor
    implements Constants, OSRConstants, BaselineConstants, PhysicalRegisterConstants {

  /**
   * Implements ExecutionStateExtractor.extractState.
   */
  @Override
  public ExecutionState extractState(RVMThread thread, Offset tsFromFPoff, Offset methFPoff, int cmid) {

    /* performs architecture and compiler dependent operations here
    *
    * When a thread is hung called from baseline compiled code,
    * the hierarchy of calls on stack looks like follows
    * ( starting from FP in the FP register ):
    *
    *           morph
    *           yield
    *           threadSwitch
    *           threadSwitchFrom[Prologue|Backedge|Epilong]
    *           foo ( real method ).
    *
    * The returned ExecutionState should have following
    *
    *     current thread
    *     compiled method ID of "foo"
    *     fp of foo's stack frame
    *     bytecode index of foo's next instruction
    *     the list of variable,value of foo at that point
    *     which method (foo)
    */

    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("BASE execStateExtractor starting ...");
    }

    Registers contextRegisters = (Registers)thread.getContextRegisters();
    byte[] stack = thread.getStack();

    if (VM.VerifyAssertions) {
      int fooCmid = Magic.getIntAtOffset(stack, methFPoff.plus(STACKFRAME_METHOD_ID_OFFSET));
      VM._assert(fooCmid == cmid);
    }

    BaselineCompiledMethod fooCM = (BaselineCompiledMethod) CompiledMethods.getCompiledMethod(cmid);

    NormalMethod fooM = (NormalMethod) fooCM.getMethod();

    // get the next bc index
    VM.disableGC();
    Address rowIP = Magic.objectAsAddress(stack).loadAddress(methFPoff.plus(STACKFRAME_NEXT_INSTRUCTION_OFFSET));
    Offset ipOffset = fooCM.getInstructionOffset(rowIP);
    VM.enableGC();

    // CAUTION: IP Offset should point to next instruction
    int bcIndex = fooCM.findBytecodeIndexForInstruction(ipOffset.plus(INSTRUCTION_WIDTH));

    // assertions
    if (VM.VerifyAssertions) VM._assert(bcIndex != -1);

    // create execution state object
    ExecutionState state = new ExecutionState(thread, methFPoff, cmid, bcIndex, tsFromFPoff);

    /* extract values for local and stack, but first of all
     * we need to get type information for current PC.
     */
    BytecodeTraverser typer = new BytecodeTraverser();
    typer.computeLocalStackTypes(fooM, bcIndex);
    byte[] localTypes = typer.getLocalTypes();
    byte[] stackTypes = typer.getStackTypes();

    // consult GC reference map again since the type matcher does not complete
    // the flow analysis, it can not distinguish reference or non-reference
    // type. We should remove non-reference type
    for (int i = 0, n = localTypes.length; i < n; i++) {
      // if typer reports a local is reference type, but the GC map says no
      // then set the localType to uninitialized, see VM spec, bytecode verifier
      // CAUTION: gc map uses mc offset in bytes!!!
      boolean gcref = fooCM.referenceMaps.isLocalRefType(fooM, ipOffset.plus(INSTRUCTION_WIDTH), i);
      if (!gcref && (localTypes[i] == ClassTypeCode)) {
        localTypes[i] = VoidTypeCode;   // use gc map as reference
        if (VM.TraceOnStackReplacement) {
          VM.sysWriteln("GC maps disgrees with type matcher at " + i + "th local\n");
        }
      }
    }

    if (VM.TraceOnStackReplacement) {
      Offset ipIndex = ipOffset.toWord().rsha(LG_INSTRUCTION_WIDTH).toOffset();
      VM.sysWrite("BC Index : " + bcIndex + "\n");
      VM.sysWrite("IP Index : ", ipIndex.plus(1), "\n");
      VM.sysWrite("MC Offset : ", ipOffset.plus(INSTRUCTION_WIDTH), "\n");
      VM.sysWrite("Local Types :");
      for (byte localType : localTypes) {
        VM.sysWrite(" " + (char) localType);
      }

      VM.sysWrite("\nStack Types :");
      for (byte stackType : stackTypes) {
        VM.sysWrite(" " + (char) stackType);
      }
      VM.sysWriteln();
    }

    // go through the stack frame and extract values
    // In the variable value list, we keep the order as follows:
    // L0, L1, ..., S0, S1, ....

    // adjust local offset and stack offset
    // NOTE: donot call BaselineCompilerImpl.getFirstLocalOffset(method)
    int bufCMID = Magic.getIntAtOffset(stack, tsFromFPoff.plus(STACKFRAME_METHOD_ID_OFFSET));
    CompiledMethod bufCM = CompiledMethods.getCompiledMethod(bufCMID);
    int cType = bufCM.getCompilerType();

    //restore non-volatile registers that could contain locals; saved by yieldpointfrom methods
    //for the moment disabled OPT compilation of yieldpointfrom, because here we assume baselinecompilation !! TODO
    TempRegisters registers = new TempRegisters(contextRegisters);
    WordArray gprs = registers.gprs;
    double[] fprs = registers.fprs;
    Object[] objs = registers.objs;

    VM.disableGC();
    // method fooCM is always baseline, otherwise we wouldn't be in this code.
    // the threadswitchfrom... method on the other hand can be baseline or opt!
    if (cType == CompiledMethod.BASELINE) {
      if (VM.VerifyAssertions) {
        VM._assert(bufCM.getMethod().hasBaselineSaveLSRegistersAnnotation());
        VM._assert(methFPoff.EQ(tsFromFPoff.plus(BaselineCompilerImpl.getFrameSize((BaselineCompiledMethod) bufCM))));
      }

      Offset currentRegisterLocation = tsFromFPoff.plus(BaselineCompilerImpl.getFrameSize((BaselineCompiledMethod) bufCM));

      for (int i = LAST_FLOAT_STACK_REGISTER; i >= FIRST_FLOAT_LOCAL_REGISTER; --i) {
        currentRegisterLocation = currentRegisterLocation.minus(BYTES_IN_DOUBLE);
        long lbits = Magic.getLongAtOffset(stack, currentRegisterLocation);
        fprs[i] = Magic.longBitsAsDouble(lbits);
      }
      for (int i = LAST_FIXED_STACK_REGISTER; i >= FIRST_FIXED_LOCAL_REGISTER; --i) {
        currentRegisterLocation = currentRegisterLocation.minus(BYTES_IN_ADDRESS);
        Word w = Magic.objectAsAddress(stack).loadWord(currentRegisterLocation);
        gprs.set(i, w);
      }

    } else { //(cType == CompiledMethod.OPT)
      //KV: this code needs to be modified. We need the tsFrom methods to save all NON-VOLATILES in their prolog (as is the case for baseline)
      //This is because we don't know at compile time which registers might be in use and wich not by the caller method at runtime!!
      //For now we disallow tsFrom methods to be opt compiled when the caller is baseline compiled
      //todo: fix this together with the SaveVolatile rewrite
      OptCompiledMethod fooOpt = (OptCompiledMethod) bufCM;
      // foo definitely not save volatile.
      if (VM.VerifyAssertions) {
        boolean saveVolatile = fooOpt.isSaveVolatile();
        VM._assert(!saveVolatile);
      }

      Offset offset = tsFromFPoff.plus(fooOpt.getUnsignedNonVolatileOffset());

      // recover nonvolatile GPRs
      int firstGPR = fooOpt.getFirstNonVolatileGPR();
      if (firstGPR != -1) {
        for (int i = firstGPR; i <= LAST_NONVOLATILE_GPR; i++) {
          Word w = Magic.objectAsAddress(stack).loadWord(offset);
          gprs.set(i, w);
          offset = offset.plus(BYTES_IN_ADDRESS);
        }
      }

      // recover nonvolatile FPRs
      int firstFPR = fooOpt.getFirstNonVolatileFPR();
      if (firstFPR != -1) {
        for (int i = firstFPR; i <= LAST_NONVOLATILE_FPR; i++) {
          long lbits = Magic.getLongAtOffset(stack, offset);
          fprs[i] = Magic.longBitsAsDouble(lbits);
          offset = offset.plus(BYTES_IN_DOUBLE);
        }
      }
    }

    //save objects in registers in register object array
    int size = localTypes.length;
    for (int i = 0; i < size; i++) {
      if ((localTypes[i] == ClassTypeCode) || (localTypes[i] == ArrayTypeCode)) {
        short loc = fooCM.getGeneralLocalLocation(i);
        if (BaselineCompilerImpl.isRegister(loc)) {
          objs[loc] = Magic.addressAsObject(gprs.get(loc).toAddress());
        }
      }
    }

    VM.enableGC();

    // for locals
    getVariableValueFromLocations(stack, methFPoff, localTypes, fooCM, LOCAL, registers, state);

    // for stacks
    Offset stackOffset = methFPoff.plus(fooCM.getEmptyStackOffset());
    getVariableValue(stack, stackOffset, stackTypes, fooCM, STACK, state);

    if (VM.TraceOnStackReplacement) {
      state.printState();
    }

    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("BASE executionStateExtractor done ");
    }
    return state;
  }

  /** go over local/stack array, and build VariableElement. */
  private static void getVariableValueFromLocations(byte[] stack, Offset methFPoff, byte[] types,
                                                    BaselineCompiledMethod compiledMethod, boolean kind,
                                                    TempRegisters registers, ExecutionState state) {
    int start = 0;
    if (kind == LOCAL) {
      start = 0;
    } else {
      VM._assert(false); //implement me for stack
    }
    int size = types.length;
    for (int i = start; i < size; i++) {
      switch (types[i]) {
        case VoidTypeCode:
          break;

        case BooleanTypeCode:
        case ByteTypeCode:
        case ShortTypeCode:
        case CharTypeCode:
        case IntTypeCode: {
          short loc = compiledMethod.getGeneralLocalLocation(i);
          int value;
          if (BaselineCompilerImpl.isRegister(loc)) {
            value = registers.gprs.get(loc).toInt();
          } else {
            value = Magic.getIntAtOffset(stack, methFPoff.plus(BaselineCompilerImpl.locationToOffset(loc) - BYTES_IN_INT));
          }

          state.add(new VariableElement(kind, i, INT, value));
          break;
        }
        case LongTypeCode: {
          short loc = compiledMethod.getGeneralLocalLocation(i);
          long lvalue;
          if (BaselineCompilerImpl.isRegister(loc)) {
            if (VM.BuildFor32Addr) {
              lvalue =
                  ((((long) registers.gprs.get(loc).toInt()) << 32) |
                   (((long) registers.gprs.get(loc + 1).toInt()) & 0x0FFFFFFFFL));
            } else {
              lvalue = registers.gprs.get(loc).toLong();
            }
          } else {
            lvalue = Magic.getLongAtOffset(stack, methFPoff.plus(BaselineCompilerImpl.locationToOffset(loc) - BYTES_IN_LONG));
          }

          state.add(new VariableElement(kind, i, LONG, lvalue));

          if (kind == LOCAL) { //KV:VoidTypeCode is next
            i++; //KV:skip VoidTypeCode
          }
          break;
        }
        case FloatTypeCode: {
          short loc = compiledMethod.getFloatLocalLocation(i);
          int value;
          if (BaselineCompilerImpl.isRegister(loc)) {
            value = Magic.floatAsIntBits((float) registers.fprs[loc]);
          } else {
            value = Magic.getIntAtOffset(stack, methFPoff.plus(BaselineCompilerImpl.locationToOffset(loc) - BYTES_IN_FLOAT));
          }

          state.add(new VariableElement(kind, i, FLOAT, value));
          break;
        }
        case DoubleTypeCode: {
          short loc = compiledMethod.getFloatLocalLocation(i);
          long lvalue;
          if (BaselineCompilerImpl.isRegister(loc)) {
            lvalue = Magic.doubleAsLongBits(registers.fprs[loc]);
          } else {
            lvalue =
                Magic.getLongAtOffset(stack, methFPoff.plus(BaselineCompilerImpl.locationToOffset(loc) - BYTES_IN_DOUBLE));
          }

          state.add(new VariableElement(kind, i, DOUBLE, lvalue));

          if (kind == LOCAL) { //KV:VoidTypeCode is next
            i++; //KV:skip VoidTypeCode
          }
          break;
        }
        case ReturnAddressTypeCode: {
          short loc = compiledMethod.getGeneralLocalLocation(i);
          VM.disableGC();
          Address rowIP;
          if (BaselineCompilerImpl.isRegister(loc)) {
            rowIP = registers.gprs.get(loc).toAddress();
          } else {
            rowIP =
                Magic.objectAsAddress(stack).loadAddress(methFPoff.plus(BaselineCompilerImpl.locationToOffset(loc) -
                                                                           BYTES_IN_ADDRESS));
          }
          Offset ipOffset = compiledMethod.getInstructionOffset(rowIP);
          VM.enableGC();

          if (VM.TraceOnStackReplacement) {
            Offset ipIndex = ipOffset.toWord().rsha(LG_INSTRUCTION_WIDTH).toOffset();
            VM.sysWrite("baseline ret_addr ip ", ipIndex, " --> ");
          }

          int bcIndex = compiledMethod.findBytecodeIndexForInstruction(ipOffset.plus(INSTRUCTION_WIDTH));

          if (VM.TraceOnStackReplacement) {
            VM.sysWrite(" bc " + bcIndex + "\n");
          }

          state.add(new VariableElement(kind, i, RET_ADDR, bcIndex));
          break;
        }

        case ClassTypeCode:
        case ArrayTypeCode: {
          short loc = compiledMethod.getGeneralLocalLocation(i);
          Object ref;
          if (BaselineCompilerImpl.isRegister(loc)) {
            ref = registers.objs[loc];
          } else {
            ref =
                Magic.getObjectAtOffset(stack, methFPoff.plus(BaselineCompilerImpl.locationToOffset(loc) - BYTES_IN_ADDRESS));
          }

          state.add(new VariableElement(kind, i, REF, ref));
          break;
        }
        case WordTypeCode: {
          short loc = compiledMethod.getGeneralLocalLocation(i);
          Word value;
          if (BaselineCompilerImpl.isRegister(loc)) {
            value = registers.gprs.get(loc);
          } else {
            value =
                Magic.getWordAtOffset(stack, methFPoff.plus(BaselineCompilerImpl.locationToOffset(loc) - BYTES_IN_ADDRESS));
          }

          state.add(new VariableElement(kind, i, WORD, value));
          break;
        }
        default:
          if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
          break;
      } // switch
    } // for loop
  }

  /* go over local/stack array, and build VariableElement. */
  private static void getVariableValue(byte[] stack, Offset offset, byte[] types,
                                       BaselineCompiledMethod compiledMethod, boolean kind, ExecutionState state) {
    int size = types.length;
    Offset vOffset = offset;
    for (int i = 0; i < size; i++) {
      switch (types[i]) {
        case VoidTypeCode:
          vOffset = vOffset.minus(BYTES_IN_STACKSLOT);
          break;

        case BooleanTypeCode:
        case ByteTypeCode:
        case ShortTypeCode:
        case CharTypeCode:
        case IntTypeCode:
        case FloatTypeCode: {
          int value = Magic.getIntAtOffset(stack, vOffset.minus(BYTES_IN_INT));
          vOffset = vOffset.minus(BYTES_IN_STACKSLOT);

          byte tcode = (types[i] == FloatTypeCode) ? FLOAT : INT;

          state.add(new VariableElement(kind, i, tcode, value));
          break;
        }
        case LongTypeCode:
        case DoubleTypeCode: {
          //KV: this code would be nicer if VoidTypeCode would always follow a 64-bit value. Rigth now for LOCAL it follows, for STACK it proceeds
          Offset memoff =
              (kind == LOCAL) ? vOffset.minus(BYTES_IN_DOUBLE) : VM.BuildFor64Addr ? vOffset : vOffset.minus(
                  BYTES_IN_STACKSLOT);
          long value = Magic.getLongAtOffset(stack, memoff);

          byte tcode = (types[i] == LongTypeCode) ? LONG : DOUBLE;

          state.add(new VariableElement(kind, i, tcode, value));

          if (kind == LOCAL) { //KV:VoidTypeCode is next
            vOffset = vOffset.minus(2 * BYTES_IN_STACKSLOT);
            i++; //KV:skip VoidTypeCode
          } else {
            vOffset = vOffset.minus(BYTES_IN_STACKSLOT); //KV:VoidTypeCode was already in front
          }

          break;
        }
        case ReturnAddressTypeCode: {
          VM.disableGC();
          Address rowIP = Magic.objectAsAddress(stack).loadAddress(vOffset.minus(BYTES_IN_ADDRESS));
          Offset ipOffset = compiledMethod.getInstructionOffset(rowIP);
          VM.enableGC();

          vOffset = vOffset.minus(BYTES_IN_STACKSLOT);

          if (VM.TraceOnStackReplacement) {
            Offset ipIndex = ipOffset.toWord().rsha(LG_INSTRUCTION_WIDTH).toOffset();
            VM.sysWrite("baseline ret_addr ip ", ipIndex, " --> ");
          }

          int bcIndex = compiledMethod.findBytecodeIndexForInstruction(ipOffset.plus(INSTRUCTION_WIDTH));

          if (VM.TraceOnStackReplacement) {
            VM.sysWrite(" bc " + bcIndex + "\n");
          }

          state.add(new VariableElement(kind, i, RET_ADDR, bcIndex));
          break;
        }

        case ClassTypeCode:
        case ArrayTypeCode: {
          VM.disableGC();
          Object ref = Magic.getObjectAtOffset(stack, vOffset.minus(BYTES_IN_ADDRESS));
          VM.enableGC();

          vOffset = vOffset.minus(BYTES_IN_STACKSLOT);

          state.add(new VariableElement(kind, i, REF, ref));
          break;
        }
        case WordTypeCode: {
          Word value = Magic.getWordAtOffset(stack, vOffset.minus(BYTES_IN_ADDRESS));
          vOffset = vOffset.minus(BYTES_IN_STACKSLOT);

          state.add(new VariableElement(kind, i, WORD, value));
          break;
        }
        default:
          if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
          break;
      } // switch
    } // for loop
  }
}
