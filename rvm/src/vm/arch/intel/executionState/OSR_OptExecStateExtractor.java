/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;
import java.util.*;

/**
 * OSR_OptExecStateExtractor is a subclass of OSR_ExecStateExtractor. 
 * It extracts the execution state from an optimized activation.
 *
 * @author Feng Qian
 */
public final class OSR_OptExecStateExtractor 
  extends OSR_ExecStateExtractor 
  implements VM_Constants, 
             OSR_Constants,
             OPT_PhysicalRegisterConstants {
  
  public OSR_ExecutionState extractState(VM_Thread thread,
                                         int osrFPoff,
                                         int methFPoff,
                                         int cmid) {

  /* perform machine and compiler dependent operations here
   * osrFPoff is the fp offset of 
   * VM_OptSaveVolatile.OPT_threadSwithFrom<...>
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

    byte[] stack = thread.stack;

    // get registers for the caller ( real method )
    OSR_TempRegisters registers = 
      new OSR_TempRegisters(thread.contextRegisters);

    if (VM.VerifyAssertions) {
      int foocmid = VM_Magic.getIntAtOffset(stack, 
                                            methFPoff + STACKFRAME_METHOD_ID_OFFSET);
      if (foocmid != cmid) {
        VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
        VM.sysWriteln("unmatch method, it should be "+cm.getMethod());
        VM_CompiledMethod foo = VM_CompiledMethods.getCompiledMethod(foocmid);
        VM.sysWriteln("but now it is "+foo.getMethod());
        walkOnStack(stack, osrFPoff);   
      }
      VM._assert(foocmid == cmid);
    }

    VM_OptCompiledMethod fooCM = 
      (VM_OptCompiledMethod)VM_CompiledMethods.getCompiledMethod(cmid);

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
    VM_CodeArray instructions = fooCM.getInstructions();
    VM.disableGC();
    VM_Address osrFP = VM_Magic.objectAsAddress(stack).add(osrFPoff);
    VM_Address nextIP = VM_Magic.getReturnAddress(osrFP);
    int ipOffset = fooCM.getInstructionOffset(nextIP);
    VM.enableGC();

    VM_OptMachineCodeMap fooMCmap = fooCM.getMCMap();
    
    OSR_EncodedOSRMap fooOSRMap = fooCM.getOSRMap();

    /* get register reference map from OSR map
     * we are using this map to convert addresses to objects,
     * thus we can operate objects out of GC section.
     */
    int regmap = fooOSRMap.getRegisterMapForMCOffset(ipOffset);

    {
      int bufCMID = VM_Magic.getIntAtOffset(stack,
                                            osrFPoff + STACKFRAME_METHOD_ID_OFFSET);
      VM_CompiledMethod bufCM = 
        VM_CompiledMethods.getCompiledMethod(bufCMID);

      // offset in bytes, convert it to stack words from fpIndex
      // OPT_SaveVolatile can only be compiled by OPT compiler
      if (VM.VerifyAssertions) VM._assert(bufCM instanceof VM_OptCompiledMethod);
      restoreValuesFromOptSaveVolatile(stack, osrFPoff, registers, regmap, bufCM);
    }

    // return a list of states: from caller to callee
    // if the osr happens in an inlined method, the state is
    // a chain of recoverd methods. 
    OSR_ExecutionState state = getExecStateSequence(thread,
                                                    stack,
                                                    ipOffset,
                                                    methFPoff,
                                                    cmid,
                                                    osrFPoff,
                                                    registers,
                                                    fooOSRMap);

    // reverse callerState points, it becomes callee -> caller
    OSR_ExecutionState prevState = null;
    OSR_ExecutionState nextState = state;       
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
      VM.sysWriteln("OptExecState : recovered states " +thread.toString());
      OSR_ExecutionState temp = state;
      do {
        VM.sysWriteln(temp.toString());
        temp = temp.callerState;
      } while (temp != null);
    }

    return state;
  }


  /* VM_OptSaveVolatile has different stack layout from VM_DynamicBridge
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
   * 
   */
  private void restoreValuesFromOptSaveVolatile(byte[] stack,
                                                int osrFPoff,
                                                OSR_TempRegisters registers,
                                                int regmap,
                                                VM_CompiledMethod cm) {

    VM_OptCompiledMethod tsfromCM = (VM_OptCompiledMethod)cm;
    
    boolean saveVolatile = tsfromCM.isSaveVolatile();
    if (VM.VerifyAssertions) {
      VM._assert(saveVolatile);
    }

    // stack word width in bytes.
    int SW_WIDTH = 1 << LG_STACKWORD_WIDTH;
    
    VM_WordArray gprs = registers.gprs;
      
    // enter critical section
    // precall methods potientially causing dynamic compilation
    int firstNonVolatile = tsfromCM.getFirstNonVolatileGPR();
    int nonVolatiles     = tsfromCM.getNumberOfNonvolatileGPRs();
    int nonVolatileOffset = tsfromCM.getUnsignedNonVolatileOffset() + (nonVolatiles -1)*SW_WIDTH;

    VM.disableGC();
        
    // recover nonvolatile GPRs
    for (int i = firstNonVolatile + nonVolatiles - 1;
             i >= firstNonVolatile; 
             i--) {
      gprs.set(NONVOLATILE_GPRS[i], 
               VM_Magic.getMemoryWord(VM_Magic.objectAsAddress(stack).add(osrFPoff - nonVolatileOffset)));
      nonVolatileOffset -= SW_WIDTH;
    }

    // restore with VOLATILES yet
    int volatileOffset = nonVolatileOffset;
    for (int i = NUM_VOLATILE_GPRS - 1; 
             i >= 0;
             i --) {
      gprs.set(VOLATILE_GPRS[i], 
               VM_Magic.getMemoryWord(VM_Magic.objectAsAddress(stack).add(osrFPoff - volatileOffset)));
      volatileOffset -= SW_WIDTH;
    }

    // currently, all FPRS are volatile on intel, 
    // DO nothing.
    
    // convert addresses in registers to references, starting from register 0
    // powerPC starts from register 1
    for (int i=0; i<NUM_GPRS; i++) {
      if (OSR_EncodedOSRMap.registerIsSet(regmap, i)) {
        registers.objs[i] = 
          VM_Magic.addressAsObject(registers.gprs.get(i).toAddress());
      }
    }
    
    VM.enableGC();

    if (VM.TraceOnStackReplacement) {
      for (int i=0; i<NUM_GPRS; i++) {
        VM.sysWrite(GPR_NAMES[i]);
        VM.sysWrite(" : ");
        VM.sysWriteHex(registers.gprs.get(i).toAddress());
        VM.sysWriteln();
      }
    }
  }  

  private OSR_ExecutionState getExecStateSequence(VM_Thread thread,
                                                  byte[] stack,
                                                  int   ipOffset,
                                                  int   fpOffset,
                                                  int   cmid,
                                                  int   tsFPOffset,
                                                  OSR_TempRegisters registers,
                                                  OSR_EncodedOSRMap osrmap) {

    // go through the stack frame and extract values
    // In the variable value list, we keep the order as follows:
    // L0, L1, ..., S0, S1, ....
      
    /* go over osr map element, build list of OSR_VariableElement. 
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

    // for the long type value which has two int parts.
    // this hold the first part for LONG1.
    long lpart_one = 0;

    // now recover execution states
    OSR_MapIterator iterator = 
      osrmap.getOsrMapIteratorForMCOffset(ipOffset);
    if (VM.VerifyAssertions) VM._assert(iterator != null);

    OSR_ExecutionState state = new OSR_ExecutionState(thread,
                                                      fpOffset,
                                                      cmid,
                                                      iterator.getBcIndex(),
                                                      tsFPOffset);
    VM_MethodReference mref = VM_MemberReference.getMemberRef(iterator.getMethodId()).asMethodReference();
    state.setMethod((VM_NormalMethod)mref.peekResolvedMethod());
    // this is not caller, but the callee, reverse it when outside
    // of this function.
    state.callerState = null;

        if (VM.TraceOnStackReplacement) {
          VM.sysWriteln("osr map table of "+state.meth.toString());
        }
        
    while (iterator.hasMore()) {
      
      if (iterator.getMethodId() != state.meth.getId()) {       
        OSR_ExecutionState newstate = new OSR_ExecutionState(thread,
                                                             fpOffset,
                                                             cmid,
                                                             iterator.getBcIndex(),
                                                             tsFPOffset);
        mref = VM_MemberReference.getMemberRef(iterator.getMethodId()).asMethodReference();
        newstate.setMethod((VM_NormalMethod)mref.peekResolvedMethod());
        // this is not caller, but the callee, reverse it when outside
        // of this function.
        newstate.callerState = state;

        state = newstate;

                if (VM.TraceOnStackReplacement) {
                  VM.sysWriteln("osr map table of "+state.meth.toString());
                }
        
      } 

      // create a OSR_VariableElement for it.
      int kind  = iterator.getKind();
      int num   = iterator.getNumber();
      int tcode = iterator.getTypeCode();
      int vtype = iterator.getValueType();
      int value = iterator.getValue();

      iterator.moveToNext();

          if (VM.TraceOnStackReplacement) {
                VM.sysWrite( (kind == LOCAL)?"L":"S");
                VM.sysWrite( num);
                VM.sysWrite(" , ");
                if (vtype == ICONST) {
                  VM.sysWrite("ICONST ");
                  VM.sysWrite(value);
                } else if (vtype == PHYREG) { 
                  VM.sysWrite("PHYREG ");
                  VM.sysWrite(GPR_NAMES[value]);
                } else if (vtype == SPILL) { 
                  VM.sysWrite("SPILL  ");
                  VM.sysWrite(value);
                }
                VM.sysWriteln();
          }
        
      switch (tcode) {
      case INT: {
        int ibits = getIntBitsFrom(vtype,
                                   value,
                                   stack,
                                   fpOffset,
                                   registers);
        state.add(new OSR_VariableElement(kind,
                                         num,
                                         tcode,
                                         ibits));
        break;
      }
      case FLOAT: {
        float fv = getFloatFrom(vtype,
                                value,
                                stack,
                                fpOffset,
                                registers);
        int ibits = VM_Magic.floatAsIntBits(fv);
        state.add(new OSR_VariableElement(kind,
                                         num,
                                         tcode,
                                         ibits));
        break;
      }
      case LONG1: {
        lpart_one = getIntBitsFrom(vtype,
                                   value,
                                   stack,
                                   fpOffset,
                                   registers);
        break;
      }
      case LONG2: {
        long lpart_two = getIntBitsFrom(vtype,
                                        value,
                                        stack,
                                        fpOffset,
                                        registers);
        long lbits = (lpart_one << 32) | (lpart_two & 0x0FFFFFFFF);
        state.add(new OSR_VariableElement(kind,
                                         num,
                                         LONG,    // not use LONG2, 
                                         lbits));

        lpart_one = 0;
        break;
      }
      case DOUBLE: {
        double dv = getDoubleFrom(vtype,
                                  value,
                                  stack,
                                  fpOffset,
                                  registers);
        long lbits = VM_Magic.doubleAsLongBits(dv);
        state.add(new OSR_VariableElement(kind,
                                         num,
                                         tcode,
                                         lbits));
        break;
      }
      // I believe I did not handle return address correctly because
      // the opt compiler did inlining of JSR/RET.
      // To be VERIFIED.
      case ADDR: {
        int bcIndex  = getIntBitsFrom(vtype,
                                      value,
                                      stack,
                                      fpOffset,
                                      registers);       
        state.add(new OSR_VariableElement(kind,
                                         num,
                                         tcode,
                                         bcIndex));
        break;
      }
      case REF: {
        Object ref = getObjectFrom(vtype,
                                   value,
                                   stack,
                                   fpOffset,
                                   registers);

        state.add(new OSR_VariableElement(kind,
                                         num,
                                         tcode,
                                         ref));
        break;
      }
      default:
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
        break;
      } // switch 
    } // for loop

    return state;
  }
  

  /* auxillary functions to get value from different places.
   */
  private static int getIntBitsFrom(int vtype,
                                    int value,
                                    byte[] stack,
                                    int fpOffset,
                                    OSR_TempRegisters registers) {
    // for INT_CONST type, the value is the value
    if (vtype == ICONST) {
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

      int offset = fpOffset - value;
      return VM_Magic.getIntAtOffset(stack, offset);

    } else {
      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
      return -1;
    }
  }

  private static float getFloatFrom(int vtype,
                                    int value,
                                    byte[] stack,
                                    int fpOffset,
                                    OSR_TempRegisters registers) {
    if (vtype == PHYREG) {
      // for FPRs, the index is FIRST_DOUBLE + regnum
      // it has been changed again
      return (float)registers.fprs[value - FIRST_DOUBLE];
    } else if (vtype == SPILL) {
      int offset = fpOffset - value;
      long lbits = VM_Magic.getLongAtOffset(stack, offset);
      return (float)VM_Magic.longBitsAsDouble(lbits);

    } else {
      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
      return -1.0f;
    }
  }

  private static double getDoubleFrom(int vtype,
                                      int value,
                                      byte[] stack,
                                      int fpOffset,
                                      OSR_TempRegisters registers) {
    if (vtype == PHYREG) {
      return registers.fprs[value - FIRST_DOUBLE];

    } else if (vtype == SPILL) {

      int offset = fpOffset - value;
      long lbits = VM_Magic.getLongAtOffset(stack, offset);
      return VM_Magic.longBitsAsDouble(lbits);

    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      return -1.0;
    }
  }

  private static final Object getObjectFrom(int vtype,
                                            int value,
                                            byte[] stack,
                                            int fpOffset,
                                            OSR_TempRegisters registers) {
    if (vtype == ICONST) {
      // the only constant object is NULL, I believe.
      if (VM.VerifyAssertions) VM._assert(value == 0);
      return VM_Magic.addressAsObject(VM_Address.fromInt(value));

    } else if (vtype == PHYREG) {

      return registers.objs[value];

    } else if (vtype == SPILL) {

      int offset = fpOffset - value;
      return VM_Magic.getObjectAtOffset(stack, offset);

    } else {
      VM.sysWrite("fatal error : ( vtype = "+vtype+" )\n");
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      return null;
    }
  }

  private static void dumpStackContent(byte[] stack, int fpOffset) {
    VM.disableGC();
    VM_Address upper = VM_Magic.getMemoryAddress(VM_Magic.objectAsAddress(stack).add(fpOffset));
    VM.enableGC();
    int upOffset = upper.diff(VM_Magic.objectAsAddress(stack)).toInt();

    int cmid = VM_Magic.getIntAtOffset(stack, fpOffset + STACKFRAME_METHOD_ID_OFFSET);
    VM_OptCompiledMethod cm = 
      (VM_OptCompiledMethod)VM_CompiledMethods.getCompiledMethod(cmid);

    int SW_WIDTH = 4;
    int firstNonVolatile = cm.getFirstNonVolatileGPR();
    int nonVolatiles     = cm.getNumberOfNonvolatileGPRs();
    int nonVolatileOffset = cm.getUnsignedNonVolatileOffset() + (nonVolatiles -1)*SW_WIDTH;

    VM.sysWrite("stack of "+cm.getMethod()+"\n");
    VM.sysWrite("      fp offset "+fpOffset+"\n");
    VM.sysWrite(" NV area offset "+nonVolatileOffset+"\n");
    VM.sysWrite("   first NV GPR "+firstNonVolatile+"\n");

    for (int i=nonVolatileOffset; i>=0; i-=SW_WIDTH) {
      int content = VM_Magic.getIntAtOffset(stack, fpOffset-i);
      VM.sysWriteHex(VM_Magic.objectAsAddress(stack).add(fpOffset - i));
      VM.sysWrite("  ");
      VM.sysWriteHex(content);
      VM.sysWriteln();
    }
  }

  private static void dumpRegisterContent(VM_WordArray gprs) {
    for (int i=0, n=gprs.length(); i<n; i++) {
      VM.sysWriteln(GPR_NAMES[i] + " = " + Integer.toHexString(gprs.get(i).toInt()));
    }
  }

  /* walk on stack frame, print out methods
   */
  private static void walkOnStack(byte[] stack, int fpOffset) {
    VM.disableGC();
    
    VM_Address fp = VM_Magic.objectAsAddress(stack).add(fpOffset);
        
    while (VM_Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP) ){
      int cmid = VM_Magic.getCompiledMethodID(fp);
      
      if (cmid == INVISIBLE_METHOD_ID) {
        VM.sysWriteln(" invisible method ");
      } else {
        VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
        fpOffset = fp.diff(VM_Magic.objectAsAddress(stack)).toInt();
        VM.enableGC();
        
        VM.sysWriteln(cm.getMethod().toString());
                
        VM.disableGC();
        fp = VM_Magic.objectAsAddress(stack).add(fpOffset);
        if (cm.getMethod().getDeclaringClass().isBridgeFromNative()) {
          fp = VM_Runtime.unwindNativeStackFrame(fp);
        }
      }
      
      fp = VM_Magic.getCallerFramePointer(fp);
    }
    
    VM.enableGC();
  }
}
