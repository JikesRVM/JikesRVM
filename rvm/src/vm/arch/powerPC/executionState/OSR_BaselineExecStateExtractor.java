/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;

import org.vmmagic.unboxed.*;

/**
 * OSR_BaselineExecStateExtractor retrieves the runtime state from a suspended
 * thread whose top method was compiled by a baseline compiler.
 *
 * @author Feng Qian
 */

public final class OSR_BaselineExecStateExtractor 
  extends OSR_ExecStateExtractor 
  implements VM_Constants,
             OSR_Constants, 
             OPT_PhysicalRegisterConstants {

  /**
   * Implements OSR_ExecStateExtractor.extractState.
   */
  public OSR_ExecutionState extractState(VM_Thread thread,
                                         Offset tsFromFPoff,
                                         Offset methFPoff, 
                                         int cmid) {

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
   * The returned OSR_ExecutionState should have following
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

    VM_Registers contextRegisters = thread.contextRegisters;
    byte[] stack = thread.stack;

    if (VM.VerifyAssertions) {
      int fooCmid     = VM_Magic.getIntAtOffset(stack, 
                              methFPoff.add(STACKFRAME_METHOD_ID_OFFSET));
      VM._assert(fooCmid == cmid);
    }

    VM_BaselineCompiledMethod fooCM = 
      (VM_BaselineCompiledMethod)VM_CompiledMethods.getCompiledMethod(cmid);

    VM_NormalMethod fooM = (VM_NormalMethod)fooCM.getMethod();

    // get the next bc index 
    VM.disableGC();
    Address rowIP     = VM_Magic.objectAsAddress(stack).loadAddress(methFPoff.add(STACKFRAME_NEXT_INSTRUCTION_OFFSET));
    Offset ipOffset = fooCM.getInstructionOffset(rowIP);
    VM.enableGC();

    // CAUTION: IP Offset should point to next instruction
    int bcIndex = fooCM.findBytecodeIndexForInstruction(ipOffset.add(INSTRUCTION_WIDTH));

    // assertions
    if (VM.VerifyAssertions) VM._assert(bcIndex != -1);

    // create execution state object
    OSR_ExecutionState state = new OSR_ExecutionState(thread,
                                                      methFPoff,
                                                      cmid,
                                                      bcIndex,
                                                      tsFromFPoff);

    /* extract values for local and stack, but first of all
     * we need to get type information for current PC.
     */    
    OSR_BytecodeTraverser typer = new OSR_BytecodeTraverser();
    typer.computeLocalStackTypes(fooM, bcIndex);
    byte[] localTypes = typer.getLocalTypes();
    byte[] stackTypes = typer.getStackTypes();

    // consult GC reference map again since the type matcher does not complete
    // the flow analysis, it can not distinguish reference or non-reference 
    // type. We should remove non-reference type
    for (int i=0, n=localTypes.length; i<n; i++) {
      // if typer reports a local is reference type, but the GC map says no
      // then set the localType to uninitialized, see VM spec, bytecode verifier
      // CAUTION: gc map uses mc offset in bytes!!!
      boolean gcref = fooCM.referenceMaps.isLocalRefType(fooM, ipOffset.add(INSTRUCTION_WIDTH), i);
      if (!gcref && (localTypes[i] == ClassTypeCode)) {
        localTypes[i] = VoidTypeCode;   // use gc map as reference
        if (VM.TraceOnStackReplacement) {
          VM.sysWriteln("GC maps disgrees with type matcher at "+i+"th local\n");
        }
      }
    }

    if (VM.TraceOnStackReplacement) {
      Offset ipIndex = ipOffset.toWord().rsha(LG_INSTRUCTION_WIDTH).toOffset();
      VM.sysWrite("BC Index : "+bcIndex+"\n");
          VM.sysWrite("IP Index : ", ipIndex.add(1), "\n");
          VM.sysWrite("MC Offset : ", ipOffset.add(INSTRUCTION_WIDTH), "\n");
      VM.sysWrite("Local Types :");
      for (int i=0; i<localTypes.length; i++) {
        VM.sysWrite(" "+(char)localTypes[i]);
      }

      VM.sysWrite("\nStack Types :");
      for (int i=0; i<stackTypes.length; i++) {
        VM.sysWrite(" "+(char)stackTypes[i]);
      }
      VM.sysWriteln();
    }
    
    // go through the stack frame and extract values
    // In the variable value list, we keep the order as follows:
    // L0, L1, ..., S0, S1, ....
    
    // adjust local offset and stack offset
    // NOTE: donot call VM_Compiler.getFirstLocalOffset(method)     
    Offset startLocalOffset = methFPoff.add(fooCM.getStartLocalOffset());

    Offset stackOffset = methFPoff.add(fooCM.getEmptyStackOffset());
    
    // for locals
    getVariableValue(stack, 
                     startLocalOffset, 
                     localTypes,
                     fooCM,
                     LOCAL,
                     state);

    // for stacks
    getVariableValue(stack,
                     stackOffset,
                     stackTypes,
                     fooCM,
                     STACK,
                     state);

    if (VM.TraceOnStackReplacement) {
      state.printState();
    }
                 
    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("BASE executionStateExtractor done ");    
    }
    return state;
  }
  
  /* go over local/stack array, and build OSR_VariableElement. */
  private static void getVariableValue(byte[] stack,
                                       Offset   offset,
                                       byte[] types,
                                       VM_BaselineCompiledMethod compiledMethod,
                                       int   kind,
                                       OSR_ExecutionState state) {
    int size = types.length;
    Offset vOffset = offset;
    for (int i=0; i<size; i++) {
      switch (types[i]) {
      case VoidTypeCode:
        vOffset = vOffset.sub(BYTES_IN_STACKSLOT);
        break;

      case BooleanTypeCode:
      case ByteTypeCode:
      case ShortTypeCode:
      case CharTypeCode:
      case IntTypeCode:
      case FloatTypeCode:{
        int value = VM_Magic.getIntAtOffset(stack, vOffset.sub(BYTES_IN_INT));
        vOffset = vOffset.sub(BYTES_IN_STACKSLOT);
          
        int tcode = (types[i] == FloatTypeCode) ? FLOAT : INT;

        state.add(new OSR_VariableElement(kind,
                                         i,
                                         tcode,
                                         value));
        break;
      }
      case LongTypeCode: 
      case DoubleTypeCode: {
      //KV: this code would be nicer if VoidTypeCode would always follow a 64-bit value. Rigth now for LOCAL it follows, for STACK it proceeds
        Offset memoff = 
          (kind == LOCAL) ? vOffset.sub(BYTES_IN_DOUBLE) : VM.BuildFor64Addr? vOffset : vOffset.sub(BYTES_IN_STACKSLOT);
        long value = VM_Magic.getLongAtOffset(stack, memoff);
        
        int tcode = (types[i] == LongTypeCode) ? LONG : DOUBLE;

        state.add(new OSR_VariableElement(kind,
                                         i,
                                         tcode,
                                         value));

        if (kind == LOCAL) { //KV:VoidTypeCode is next
          vOffset = vOffset.sub(2*BYTES_IN_STACKSLOT);
          i++; //KV:skip VoidTypeCode
        } else vOffset = vOffset.sub( BYTES_IN_STACKSLOT); //KV:VoidTypeCode was already in front

        break;
      }
      case ReturnAddressTypeCode: {
        VM.disableGC();
        Address rowIP = VM_Magic.objectAsAddress(stack).loadAddress(vOffset.sub(BYTES_IN_ADDRESS));
        Offset ipOffset = compiledMethod.getInstructionOffset(rowIP);
        VM.enableGC();

        vOffset = vOffset.sub(BYTES_IN_STACKSLOT);


        if (VM.TraceOnStackReplacement) {
          Offset ipIndex = ipOffset.toWord().rsha(LG_INSTRUCTION_WIDTH).toOffset();
          VM.sysWrite("baseline ret_addr ip ", ipIndex, " --> ");
        }
        
        int bcIndex = 
          compiledMethod.findBytecodeIndexForInstruction(ipOffset.add(INSTRUCTION_WIDTH));

        if (VM.TraceOnStackReplacement) {
          VM.sysWrite(" bc "+ bcIndex+"\n");
        }
        
        state.add(new OSR_VariableElement(kind,
                                         i,
                                         RET_ADDR,
                                         bcIndex));
        break;
      }

      case ClassTypeCode: 
      case ArrayTypeCode: {
        VM.disableGC();
        Object ref = VM_Magic.getObjectAtOffset(stack, vOffset.sub(BYTES_IN_ADDRESS));
        VM.enableGC();

        vOffset = vOffset.sub(BYTES_IN_STACKSLOT);

        state.add(new OSR_VariableElement(kind,
                                         i,
                                         REF,
                                         ref));
        break;
      }
      case WordTypeCode: {
        Word value = VM_Magic.getWordAtOffset(stack, vOffset.sub(BYTES_IN_ADDRESS));
        vOffset = vOffset.sub(BYTES_IN_STACKSLOT);
          
        state.add(new OSR_VariableElement(kind,
                                         i,
                                         WORD,
                                         value));
        break;
      }
      default:
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
        break;
      } // switch 
    } // for loop
  }  
}
