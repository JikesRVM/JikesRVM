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

import static org.jikesrvm.classloader.ClassLoaderConstants.ArrayTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.BooleanTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.ByteTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.CharTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.ClassTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.DoubleTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.FloatTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.IntTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.LongTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.ShortTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.VoidTypeCode;
import static org.jikesrvm.ia32.RegisterConstants.INSTRUCTION_WIDTH;
import static org.jikesrvm.ia32.RegisterConstants.LG_INSTRUCTION_WIDTH;
import static org.jikesrvm.ia32.StackframeLayoutConstants.BYTES_IN_STACKSLOT;
import static org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_METHOD_ID_OFFSET;
import static org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_RETURN_ADDRESS_OFFSET;
import static org.jikesrvm.osr.OSRConstants.DOUBLE;
import static org.jikesrvm.osr.OSRConstants.FLOAT;
import static org.jikesrvm.osr.OSRConstants.INT;
import static org.jikesrvm.osr.OSRConstants.LOCAL;
import static org.jikesrvm.osr.OSRConstants.LONG;
import static org.jikesrvm.osr.OSRConstants.REF;
import static org.jikesrvm.osr.OSRConstants.RET_ADDR;
import static org.jikesrvm.osr.OSRConstants.ReturnAddressTypeCode;
import static org.jikesrvm.osr.OSRConstants.STACK;
import static org.jikesrvm.osr.OSRConstants.WORD;
import static org.jikesrvm.osr.OSRConstants.WordTypeCode;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.baseline.ia32.ArchBaselineCompiledMethod;
import org.jikesrvm.compilers.baseline.ia32.BaselineCompilerImpl;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.osr.BytecodeTraverser;
import org.jikesrvm.osr.ExecutionState;
import org.jikesrvm.osr.ExecutionStateExtractor;
import org.jikesrvm.osr.VariableElement;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * A class that retrieves the VM scope descriptor
 * from a suspended thread whose top method was compiled by the
 * baseline compiler.
 */
public final class BaselineExecutionStateExtractor extends ExecutionStateExtractor {

  /**
   * Implements ExecutionStateExtractor.extractState.
   *
   * @param thread : the suspended thread, the registers and stack frames are used.
   * @param osrFPoff : the osr method's stack frame offset
   * @param methFPoff : the real method's stack frame offset
   * @param cmid   : the top application method ( system calls are unwounded ).
   *
   * return a ExecutionStateExtractor object.
   */
  @Override
  public ExecutionState extractState(RVMThread thread, Offset osrFPoff, Offset methFPoff, int cmid) {

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

    byte[] stack = thread.getStack();

    if (VM.VerifyAssertions) {
      int fooCmid = Magic.getIntAtOffset(stack, methFPoff.plus(STACKFRAME_METHOD_ID_OFFSET));

      if (VM.TraceOnStackReplacement) {
        VM.sysWriteln("fooCmid = " + fooCmid);
        VM.sysWriteln("   cmid = " + cmid);
      }

      VM._assert(fooCmid == cmid);
    }

    ArchBaselineCompiledMethod fooCM = (ArchBaselineCompiledMethod) CompiledMethods.getCompiledMethod(cmid);

    NormalMethod fooM = (NormalMethod) fooCM.getMethod();

    VM.disableGC();
    Address rowIP = Magic.objectAsAddress(stack).loadAddress(osrFPoff.plus(STACKFRAME_RETURN_ADDRESS_OFFSET));
    Offset ipOffset = fooCM.getInstructionOffset(rowIP);
    VM.enableGC();

    // CAUTION: IP Offset should point to next instruction
    int bcIndex = fooCM.findBytecodeIndexForInstruction(ipOffset.plus(INSTRUCTION_WIDTH));

    // assertions
    if (VM.VerifyAssertions) {
      if (bcIndex == -1) {

        VM.sysWriteln("osrFPoff = ", osrFPoff);
        VM.sysWriteln("instr_beg = ", Magic.objectAsAddress(fooCM.getEntryCodeArray()));

        for (int i = (osrFPoff.toInt()) - 10; i < (osrFPoff.toInt()) + 10; i++) {
          VM.sysWriteln("  stack[" + i + "] = " + stack[i]);
        }

        Offset ipIndex = ipOffset.toWord().rsha(LG_INSTRUCTION_WIDTH).toOffset();
        VM.sysWriteln("ipIndex : ", ipIndex);
        VM.sysWriteln("bcIndex : " + bcIndex);
      }
      VM._assert(bcIndex != -1);
    }

    // create execution state object
    ExecutionState state = new ExecutionState(thread, methFPoff, cmid, bcIndex, osrFPoff);

    /* extract values for local and stack, but first of all
     * we need to get type information for current PC.
     */
    BytecodeTraverser typer = new BytecodeTraverser();
    typer.computeLocalStackTypes(fooM, bcIndex);
    byte[] localTypes = typer.getLocalTypes();
    byte[] stackTypes = typer.getStackTypes();

    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("BC Index : " + bcIndex);
      VM.sysWrite("Local Types :");
      for (byte localType : localTypes) {
        VM.sysWrite(" " + (char) localType);
      }
      VM.sysWriteln();
      VM.sysWrite("Stack Types :");
      for (byte stackType : stackTypes) {
        VM.sysWrite(" " + (char) stackType);
      }
      VM.sysWriteln();
    }

    // consult GC reference map again since the type matcher does not complete
    // the flow analysis, it can not distinguish reference or non-reference
    // type. We should remove non-reference type
    for (int i = 0, n = localTypes.length; i < n; i++) {
      // if typer reports a local is reference type, but the GC map says no
      // then set the localType to uninitialized, see VM spec, bytecode verifier
      if (localTypes[i] == ClassTypeCode) {
        if (!fooCM.referenceMaps.isLocalRefType(fooM, ipOffset.plus(1 << LG_INSTRUCTION_WIDTH), i)) {
          localTypes[i] = VoidTypeCode;
          if (VM.TraceOnStackReplacement) {
            VM.sysWriteln("GC maps disagrees with type matcher at " + i + "th local");
            VM.sysWriteln();
          }
        }
      }
    }

    // go through the stack frame and extract values
    // In the variable value list, we keep the order as follows:
    // L0, L1, ..., S0, S1, ....

    // adjust local offset and stack offset
    // NOTE: do not call BaselineCompilerImpl.getFirstLocalOffset(method)
    Offset startLocalOffset = methFPoff.plus(BaselineCompilerImpl.locationToOffset(fooCM.getGeneralLocalLocation(0)));

    Offset stackOffset = methFPoff.plus(fooCM.getEmptyStackOffset());

    // for locals
    getVariableValue(stack, startLocalOffset, localTypes, fooCM, LOCAL, state);

    // for stacks
    getVariableValue(stack, stackOffset, stackTypes, fooCM, STACK, state);

    if (VM.TraceOnStackReplacement) {
      state.printState();
    }

    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("BASE executionStateExtractor done ");
    }
    return state;
  }

  /* go over local/stack array, and build VariableElement. */
  private static void getVariableValue(byte[] stack, Offset offset, byte[] types,
                                       ArchBaselineCompiledMethod compiledMethod, boolean kind, ExecutionState state) {
    int size = types.length;
    Offset vOffset = offset;
    for (int i = 0; i < size; i++) {
      if (VM.TraceOnStackReplacement) {
        Word content = Magic.getWordAtOffset(stack, vOffset.minus(BYTES_IN_ADDRESS));
        VM.sysWrite("0x", vOffset.minus(BYTES_IN_ADDRESS), "    0x");
        VM.sysWriteln(content);
        if ((types[i] == LongTypeCode) || (types[i] == DoubleTypeCode)) {
          content = Magic.getWordAtOffset(stack, vOffset.minus(2 * BYTES_IN_ADDRESS));
          VM.sysWrite("0x", vOffset.minus(2 * BYTES_IN_ADDRESS), "    0x");
          VM.sysWriteln(content);
        }
      }

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
          int value = Magic.getIntAtOffset(stack, vOffset.minus(BYTES_IN_STACKSLOT));
          vOffset = vOffset.minus(BYTES_IN_STACKSLOT);

          byte tcode = (types[i] == FloatTypeCode) ? FLOAT : INT;

          state.add(new VariableElement(kind, i, tcode, value));
          break;
        }
        case LongTypeCode:
        case DoubleTypeCode: {
          //KV: this code would be nicer if VoidTypeCode would always follow a 64-bit value. Rigth now for LOCAL it follows, for STACK it proceeds
          Offset memoff = (kind == LOCAL) ? vOffset.minus(2 * BYTES_IN_STACKSLOT) : vOffset.minus(BYTES_IN_STACKSLOT);

          long value = Magic.getLongAtOffset(stack, memoff);

          byte tcode = (types[i] == LongTypeCode) ? LONG : DOUBLE;

          state.add(new VariableElement(kind, i, tcode, value));

          if (kind == LOCAL) { //KV:VoidTypeCode is next
            vOffset = vOffset.minus(2 * BYTES_IN_STACKSLOT);
            i++;
          } else {
            vOffset = vOffset.minus(BYTES_IN_STACKSLOT); //KV:VoidTypeCode was already in front
          }

          break;
        }
        case ReturnAddressTypeCode: {
          VM.disableGC();
          Address rowIP = Magic.objectAsAddress(stack).loadAddress(vOffset);
          Offset ipOffset = compiledMethod.getInstructionOffset(rowIP);
          VM.enableGC();

          vOffset = vOffset.minus(BYTES_IN_STACKSLOT);

          if (VM.TraceOnStackReplacement) {
            Offset ipIndex = ipOffset.toWord().rsha(LG_INSTRUCTION_WIDTH).toOffset();
            VM.sysWrite("baseline ret_addr ip ", ipIndex, " --> ");
          }

          int bcIndex = compiledMethod.findBytecodeIndexForInstruction(ipOffset.plus(INSTRUCTION_WIDTH));

          if (VM.TraceOnStackReplacement) {
            VM.sysWriteln(" bc " + bcIndex);
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
