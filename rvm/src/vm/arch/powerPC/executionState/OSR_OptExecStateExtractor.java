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
 * It extracts the execution state of a optimized activation.
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
   * The <tsfrom> saves all volatiles, nonvolatiles, and scratch
   * registers. All register values for 'foo' can be obtained from
   * the register save area of '<tsfrom>' method.
   */

    int[] stack = thread.stack;

    // get registers for the caller ( real method )
    OSR_TempRegisters registers = 
      new OSR_TempRegisters(thread.contextRegisters);

    if (VM.VerifyAssertions) {
      int foocmid = VM_Magic.getIntAtOffset(stack, 
				methFPoff + STACKFRAME_METHOD_ID_OFFSET);
      if (foocmid != cmid) {
	for (int i=osrFPoff; i>=methFPoff-8; i-=4) {
	  VM.sysWriteHex(VM_Magic.objectAsAddress(stack).add(i));
	  VM.sysWrite(" : "); VM.sysWriteHex(stack[i<<2]); VM.sysWriteln();
	}
	
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
    // get the next machine code offset of the real method
    VM.disableGC();
    VM_Address methFP = VM_Magic.objectAsAddress(stack).add(methFPoff);
    VM_Address nextIP     = VM_Magic.getNextInstructionAddress(methFP);
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

      // OPT_SaveVolatile can only be compiled by OPT compiler
      if (VM.VerifyAssertions) 
	VM._assert(bufCM instanceof VM_OptCompiledMethod);

      restoreValuesFromOptSaveVolatile(stack, osrFPoff, registers, 
				       regmap, bufCM);
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

    // reverse callerState points
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
      VM.sysWriteln("OptExecState : recovered states");
      OSR_ExecutionState temp = state;
      while (temp != null) {
	VM.sysWriteln(temp.toString());
	temp = temp.callerState;
      }
    }

    return state;
  }

  /* VM_OptSaveVolatile has different stack layout from VM_DynamicBridge
   * Have to separately recover them now, but there should be unified 
   * later on. TODO:
   *
   * Current SaveVolatile stack frame:
   *
   *   GPR 3 -- 14 15 16 17 -- 31, cr, xer, ctr, FPR 0 -- 15
   */
  private void restoreValuesFromOptSaveVolatile(int[] stack,
						int osrFPoff,
						OSR_TempRegisters registers,
						int regmap,
						VM_CompiledMethod cm) {

    VM_OptCompiledMethod tsfromCM = (VM_OptCompiledMethod)cm;
    
    int nvArea = osrFPoff + tsfromCM.getUnsignedNonVolatileOffset();
    // stack word width in bytes.
    int SW_WIDTH = 1 << LG_STACKWORD_WIDTH;
    
    VM_WordArray gprs = registers.gprs;
    double[] fprs = registers.fprs;
      
    // temporarialy hold ct, xer, ctr register
    int cr = 0;
    int xer = 0;
    int ctr = 0;

    // enter critical section
    // precall methods potientially causing dynamic compilation
    int firstGPR = tsfromCM.getFirstNonVolatileGPR();

    VM.disableGC();
	
    // recover volatile GPRs.
    int lastVoffset = nvArea; 
    for (int i=LAST_SCRATCH_GPR;
	 i >= FIRST_VOLATILE_GPR;
	 i--) {
      lastVoffset -= SW_WIDTH;
      gprs.set(i, VM_Magic.getMemoryWord(VM_Magic.objectAsAddress(stack).add(lastVoffset)));
    }
            
    // recover nonvolatile GPRs
    if (firstGPR != -1) {
      for (int i=firstGPR;
	   i<=LAST_NONVOLATILE_GPR;
	   i++) {
	gprs.set(i, VM_Magic.getMemoryWord(VM_Magic.objectAsAddress(stack).add(nvArea)));
	nvArea += SW_WIDTH;
      }
    }

    // recover CR, XER, and CTR
    cr  = VM_Magic.getIntAtOffset(stack, nvArea);
    nvArea += SW_WIDTH;
    xer = VM_Magic.getIntAtOffset(stack, nvArea);
    nvArea += SW_WIDTH;
    ctr = VM_Magic.getIntAtOffset(stack, nvArea);
    nvArea += SW_WIDTH;

    /*
    // it should be aligned ready
    // it may have a padding before FPRs.
    int offset = nvArea - osrFPoff;
    offset = (offset + SW_WIDTH) & ~SW_WIDTH;
    nvArea = osrFPoff + offset;
    */

    // recover all volatile FPRs
    for (int i=FIRST_SCRATCH_FPR;
	 i <= LAST_VOLATILE_FPR;
	 i++) {
      long lbits = VM_Magic.getLongAtOffset(stack, nvArea);
      fprs[i] = VM_Magic.longBitsAsDouble(lbits);
      nvArea += 2*SW_WIDTH;
    }	

    // convert addresses in registers to references 
    for (int i=1; i<NUM_GPRS; i++) {
      if (OSR_EncodedOSRMap.registerIsSet(regmap, i)) {
	registers.objs[i] = 
	  VM_Magic.addressAsObject(registers.gprs.get(i).toAddress());
      }
    }
    
    VM.enableGC();

    registers.cr = cr;
    registers.xer = xer;
    registers.ctr = ctr;
  }

  

  private OSR_ExecutionState getExecStateSequence(VM_Thread thread,
						  int[] stack,
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
     *     VM_Thread.ThreadSwitch      
     *     VM_OptSaveVolatile.threadSwitchFromDeopt  
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
    state.callerState = null;
      
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
      } 

      // create a OSR_VariableElement for it.
      int kind  = iterator.getKind();
      int num   = iterator.getNumber();
      int tcode = iterator.getTypeCode();
      int vtype = iterator.getValueType();
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
				    int[] stack,
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

      int offset = fpOffset + value;
      return VM_Magic.getIntAtOffset(stack, offset);

    } else {
      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
      return -1;
    }
  }

  private static float getFloatFrom(int vtype,
				    int value,
				    int[] stack,
				    int fpOffset,
				    OSR_TempRegisters registers) {
    if (vtype == PHYREG) {
      // for FPRs, the index may be FIRST_DOUBLE + regnum
      // it has been changed again
//      return (float)registers.fprs[value - FIRST_DOUBLE];
	return (float)registers.fprs[value];
    } else if (vtype == SPILL) {

      int offset = fpOffset + value;
      long lbits = VM_Magic.getLongAtOffset(stack, offset);
      return (float)VM_Magic.longBitsAsDouble(lbits);

    } else {
      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
      return -1.0f;
    }
  }

  private static double getDoubleFrom(int vtype,
				      int value,
				      int[] stack,
				      int fpOffset,
				      OSR_TempRegisters registers) {
    if (vtype == PHYREG) {
      return registers.fprs[value];
    } else if (vtype == SPILL) {
      int offset = fpOffset + value;
      long lbits = VM_Magic.getLongAtOffset(stack, offset);
      return VM_Magic.longBitsAsDouble(lbits);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      return -1.0;
    }
  }

  private static final Object getObjectFrom(int vtype,
					    int value,
					    int[] stack,
					    int fpOffset,
					    OSR_TempRegisters registers) {
    if (vtype == ICONST) {
      // the only constant object is NULL, I believe.
      if (VM.VerifyAssertions) VM._assert(value == 0);
      return VM_Magic.addressAsObject(VM_Address.fromInt(value));
    } else if (vtype == PHYREG) {
      return registers.objs[value];
    } else if (vtype == SPILL) {
      int offset = fpOffset + value;
      return VM_Magic.getObjectAtOffset(stack, offset);
    } else {
      VM.sysWrite("fatal error : ( vtype = "+vtype+" )\n");
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      return null;
    }
  }

  private static void dumpStackContent(int[] stack, int fpOffset) {
    VM.disableGC();
    VM_Address upper = VM_Magic.getMemoryAddress(VM_Magic.objectAsAddress(stack).add(fpOffset));
    int upOffset = upper.diff(VM_Magic.objectAsAddress(stack)).toInt();
    VM.enableGC();

    int cmid = VM_Magic.getIntAtOffset(stack, 
		 fpOffset + STACKFRAME_METHOD_ID_OFFSET);
    VM_OptCompiledMethod cm = 
      (VM_OptCompiledMethod)VM_CompiledMethods.getCompiledMethod(cmid);

    VM.sysWrite("stack of "+cm.getMethod()+"\n");
    VM.sysWrite(" NV area offset "+cm.getUnsignedNonVolatileOffset()+"\n");
    VM.sysWrite("   first NV GPR "+cm.getFirstNonVolatileGPR()+"\n");
    VM.sysWrite("   first NV FPR "+cm.getFirstNonVolatileFPR()+"\n");

    for (int i=0; i<upOffset-fpOffset; i+=4) {
      int content = VM_Magic.getIntAtOffset(stack, i+fpOffset);
      VM.sysWrite("    0x"+Integer.toHexString(content));
      VM.sysWrite("      "+i+"\n");
    }
  }

  /* walk on stack frame, print out methods
   */
  private static void walkOnStack(int[] stack, int fpOffset) {
    int cmid = STACKFRAME_SENTINEL_FP.toInt();
    do {
      cmid = VM_Magic.getIntAtOffset(stack, 
				       fpOffset+STACKFRAME_METHOD_ID_OFFSET);
      if (cmid == INVISIBLE_METHOD_ID) {
	VM.sysWriteln(" invisible method ");
      } else {
	VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
	VM.sysWriteln(cm.getMethod().toString());
      }
      VM.disableGC();
      VM_Address callerfp = VM_Magic.getMemoryAddress(VM_Magic.objectAsAddress(stack).add(
			       fpOffset+STACKFRAME_FRAME_POINTER_OFFSET));
      fpOffset = callerfp.diff(VM_Magic.objectAsAddress(stack)).toInt();
      VM.enableGC();
    } while (cmid != STACKFRAME_SENTINEL_FP.toInt());
  }
}
