/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

/** 
 * JVM scope descriptor (execution state) of a method activation. 
 * A descriptor has two parts: JVM architecture-depend, and execution context. 
 * 
 * @author Feng Qian
 */

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import java.util.LinkedList;

import org.vmmagic.unboxed.*;

public class OSR_ExecutionState implements OSR_Constants, VM_BytecodeConstants{
 
  /* the caller's state if this method is an inlinee */
  public OSR_ExecutionState callerState = null;

  /* callee's compiled method id */
  public int callee_cmid = -1;

  /* the method of which the execution state belongs to */
  public VM_NormalMethod meth;

  /* the program pointer (bytecode index) */
  public int  bcIndex; 

  /* runtime values of locals and stack expressions at
   * the bytecode index
   * Each element is an object of OSR_VariableElement. 
   */
  public LinkedList varElms; 

  /* the thread on which the activation is running */
  public VM_Thread thread;

  /* the offset of frame pointer of the activation. */
  public Offset fpOffset; 

  /* the callee (threadSwitch)'s frame pointer of this activation. */
  public Offset tsFPOffset;

  /* the compiled method id of the activation (a Java method
   * may have multiple version of compiled method */
  public int cmid;


  //////////////////////////////
  // initializer
  /////////////////////////////
  public OSR_ExecutionState(VM_Thread whichThread,
                           Offset framePointerOffset,
                           int compiledMethodID,
                           int pc,
                           Offset tsFPOffset) {
    this.thread   = whichThread;
    this.fpOffset = framePointerOffset;
    this.cmid     = compiledMethodID;
    this.bcIndex  = pc;
    this.tsFPOffset = tsFPOffset;

    this.varElms  = new LinkedList();
    this.meth     = (VM_NormalMethod)VM_CompiledMethods.getCompiledMethod(cmid).getMethod();
  }

  /////////////////////////////
  // instance methods for construction
  ////////////////////////////

  /* add a OSR_VariableElement */
  public void add(OSR_VariableElement elm) {
    this.varElms.add(elm);
  }

  /* insert as the first element, for convinience. */
  public void addFirst(OSR_VariableElement elm) {
    this.varElms.addFirst(elm);
  }
 
  /* returns thread. */
  public VM_Thread getThread() {
    return this.thread;
  }

  public Offset getFPOffset() {
    return this.fpOffset;
  }

  public void setMethod(VM_NormalMethod m) {
    this.meth = m;
  }

  public VM_NormalMethod getMethod() {
    return this.meth;
  }

  public Offset getTSFPOffset() {
    return this.tsFPOffset;
  }

  /* print the current state for debugging */
  public void printState() {
    VM.sysWriteln("Execution state of "+meth);
    VM.sysWriteln("    thread index : ",thread.getIndex());
    VM.sysWriteln("       FP offset : ",fpOffset);
    VM.sysWriteln("            cmid : ",cmid);
    VM.sysWriteln("         bcIndex : ",bcIndex);

    for (int i=0, n=varElms.size(); i<n; i++) {
      OSR_VariableElement var = (OSR_VariableElement)varElms.get(i);
      VM.sysWrite("  "+var+"\n");
    }
  }

  //////////////////////////////////////
  // interface to recompilation
  /////////////////////////////////////


  private Object[] objs;
  private int objnum;    
  private int rid;
  /**
   * Goes through variable elements and produces specialized 
   * prologue using pseudo-bytecode.
   */
  public byte[] generatePrologue() {

    int size = varElms.size();

    this.objs = new Object[size];
    this.objnum = 0;    
    this.rid = OSR_ObjectHolder.handinRefs(this.objs);

    OSR_PseudoBytecode head = new BC_Nop();
    OSR_PseudoBytecode tail = head;

    int elmcount = 0;
    // restore parameters first;
    // restore "this"
    if (!this.meth.isStatic()) {
      OSR_VariableElement var = (OSR_VariableElement)varElms.get(elmcount);
      tail = processElement(var, tail, elmcount);
      elmcount++;

      if (VM.VerifyAssertions) {
        VM._assert(var.isLocal() && (var.getNumber() == 0));
      }
    }
    // restore other parameters, 
    int paranum = this.meth.getParameterTypes().length;
    for (int i=0; i<paranum; i++) {
      OSR_VariableElement var = (OSR_VariableElement)varElms.get(elmcount);
      tail = processElement(var, tail, elmcount);
      elmcount ++;
      if (VM.VerifyAssertions) {
        VM._assert(var.isLocal()); 
        // the number may not match because of long and double type
      }
    }
    // ok, ready to indicate param initialized, thread swith
    // and stack overflow check happends here
    tail.next = new BC_ParamInitEnd();
    tail = tail.next;

    // restore other locals and stack slots, assuming stack element 
    // were sorted
    for (; elmcount < size; elmcount++) {
      OSR_VariableElement var = 
        (OSR_VariableElement)varElms.get(elmcount);
      tail = processElement(var, tail, elmcount);
    }// end of for loop
    
    if (this.objnum != 0) {
      tail.next = new BC_LoadIntConst(this.rid);
      tail = tail.next;
      
      tail.next = new BC_InvokeStatic(CLEANREFS);
      tail = tail.next;
    } else {
      OSR_ObjectHolder.cleanRefs(this.rid);
    }

    // default situation
    int branchTarget = this.bcIndex;

    /* when this method must start with a call of callee, 
     * we are using invokeCompiledMethod,
     */
    if (callee_cmid != -1) {
          // remember the callee's cmid, and the index of original index
      tail.next = new BC_InvokeCompiledMethod(callee_cmid, this.bcIndex);
      tail = tail.next;      

      // if this method needs a call, than we must jump to 
      // the instruction after the call.
      VM_BytecodeStream bcodes = this.meth.getBytecodes();
          bcodes.reset(this.bcIndex);
          
      int code = bcodes.nextInstruction();

      switch (code) {
      case JBC_invokeinterface: {
        branchTarget = this.bcIndex + 5;
        break;
      }
      case JBC_invokespecial: 
      case JBC_invokestatic:
      case JBC_invokevirtual:  {
        branchTarget = this.bcIndex + 3;
        break;
      }
      default: {
        if (VM.VerifyAssertions) {
          VM._assert(VM.NOT_REACHED,
                     "OSR_ExecutionState: unknown bytecode " + code
                        + " at " + this.bcIndex + "@" + this.meth);
        }
        break;
      }
      }
    }

    // add goto statement, be careful, after goto
    // there may be several pop instructions
    int pops = computeStackHeight(head);
    branchTarget += pops;  // preserve space 
    {
      BC_Goto togo = new BC_Goto(branchTarget);
      int osize = togo.getSize();
      togo.patch(branchTarget + osize);
      int nsize = togo.getSize();
      if (nsize != osize) {
        togo.patch(branchTarget + nsize);
      }
 
      tail.next = togo;
      tail = tail.next;
    }    

    // compute stack heights and padding pops
    tail = adjustStackHeight(tail, pops);

    int bsize = paddingBytecode(head);
    byte[] prologue = generateBinaries(head, bsize);

    // clean fields
    this.objs = null;
    this.objnum = 0;

    return prologue;
  }// end of method

  private OSR_PseudoBytecode processElement(OSR_VariableElement var,
                                            OSR_PseudoBytecode tail,
                                            int i) {
    switch (var.getTypeCode()) {
    case INT: {
      tail.next = new BC_LoadIntConst(var.getIntBits());
      tail = tail.next;
      
      if (var.isLocal()) {
        tail.next = new BC_IntStore(var.getNumber());
        tail = tail.next;
      }
      break;
    }
    case FLOAT: {
      tail.next = new BC_LoadFloatConst(var.getIntBits());
      tail = tail.next;
      
      if (var.isLocal()) {
        tail.next = new BC_FloatStore(var.getNumber());
        tail = tail.next;
      }
      break;
    }
    case LONG: {
      tail.next = new BC_LoadLongConst(var.getLongBits());
      tail = tail.next;
      
      if (var.isLocal()) {
        tail.next = new BC_LongStore(var.getNumber());
        tail = tail.next;
      }
      break;
    }
    case DOUBLE: {
      tail.next = new BC_LoadDoubleConst(var.getLongBits());
      tail = tail.next;
      
      if (var.isLocal()) {
        tail.next = new BC_DoubleStore(var.getNumber());
        tail = tail.next;
      }
      break;
    }
    case RET_ADDR: {
      tail.next = new BC_LoadRetAddrConst(var.getIntBits());
      tail = tail.next;
      
      if (var.isLocal()) {
        tail.next = new BC_RefStore(var.getNumber());
        tail = tail.next;
      }
      break;
    }
    case REF: {
      this.objs[i] = var.getObject();
        
      if (this.objs[i] != null) {
        
        tail.next = new BC_LoadIntConst(this.rid);
        tail = tail.next;
        
        tail.next = new BC_LoadIntConst(i);
        tail = tail.next;
        
        // the opt compiler will adjust the type of
        // return value to the real type of object
        // when it sees the invoke target is GETREFAT
        tail.next = new BC_InvokeStatic(GETREFAT);
        tail = tail.next;
      } else {
        // just give an aconst_null
        tail.next = new BC_AConstNull();
        tail = tail.next;
      }
      
      if (var.isLocal()) {
        tail.next = new BC_RefStore(var.getNumber());
        tail = tail.next;
      }
      
      this.objnum++;

      break;
    }
    case WORD: {
      tail.next = new BC_LoadWordConst(var.getWord());
      tail = tail.next;
      
      if (var.isLocal()) {
        tail.next = new BC_RefStore(var.getNumber());
        tail = tail.next;
      }
      break;
    }
    default:
      if (VM.VerifyAssertions) {
        VM._assert(VM.NOT_REACHED);
      }
      break;
    } // end of switch

    return tail;
  }
  
  private int maxStackHeight = 0;
  public int getMaxStackHeight() {
        return this.maxStackHeight;
  }
  
  private int computeStackHeight(OSR_PseudoBytecode head) {
    /* skip the first Nop */
    OSR_PseudoBytecode bcode = head.next;
    int height = 0;
    while (bcode != null) {
      height += bcode.stackChanges();
      if (height > this.maxStackHeight) {
        this.maxStackHeight = height;
      }
      bcode = bcode.next;
    }
    
    if (VM.VerifyAssertions) VM._assert(height >= 0);
    return height;
  }

  private static OSR_PseudoBytecode adjustStackHeight(OSR_PseudoBytecode last, int height) {
    // append pop
    for (int i=0; i<height; i++) {
      last.next = new BC_Pop();
      last = last.next;
    }

    return last;
  }

  /* add padding (NOP) at the beginning of pseudo bytecode
   * to make the new bytecode size dividable by 4, then no branch
   * target adjustment is needed in the original code.
   */
  private static int paddingBytecode(OSR_PseudoBytecode head) {
    /* skip the first Nop. */
    OSR_PseudoBytecode bcode = head.next;

    /* count the total size of prologue code. */
    int bsize = 0;
    while (bcode != null) {
      bsize += bcode.getSize();
      bcode = bcode.next;
    }

    /* insert Nop at the beginning to make the code size of x4. */
    int padding = 3 - (bsize+3) & 0x03;

    for (int i=0; i<padding; i++) {
      bcode = new BC_Nop();
      bcode.next = head.next;
      head.next = bcode;
    }

    bsize += padding;

    return bsize;
  }

  /* generating binary code from pseudo code, the size and the code
   * list are padded and well calculated.
   */
  private static byte[] generateBinaries(OSR_PseudoBytecode bhead, 
                                         int bsize) {

    /* patch the LoalAddrConst instruction, and generate codes. */
    byte[] codes = new byte[bsize];
    
    /* skip the first NOP */
    OSR_PseudoBytecode bcode = bhead.next;
    int pos = 0;
    while (bcode != null) {

      int size = bcode.getSize();

      if (bcode instanceof BC_LoadRetAddrConst) {
        BC_LoadRetAddrConst laddr = (BC_LoadRetAddrConst)bcode;

        /* CAUTION: path relative offset only. */
        laddr.patch(laddr.getOffset()+bsize);
      }         

      if (VM.TraceOnStackReplacement) VM.sysWriteln(pos+" : "+bcode.toString());

      System.arraycopy(bcode.getBytes(), 0, codes, pos, size);

      pos += size;
      bcode = bcode.next;
    }

    return codes;
  }    

  public String toString() {
    StringBuffer buf = new StringBuffer("Execution state "
                                        +this.bcIndex+"@"+this.meth
                                        +" "+this.thread);
    for (int i=0, n=varElms.size(); i<n; i++) {
      OSR_VariableElement var = 
        (OSR_VariableElement)varElms.get(i);
      buf.append("\n  "+var);
    }

    return new String(buf);
  }
} 
