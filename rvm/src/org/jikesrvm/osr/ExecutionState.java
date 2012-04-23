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
package org.jikesrvm.osr;

import java.util.LinkedList;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.BytecodeConstants;
import org.jikesrvm.classloader.BytecodeStream;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.osr.bytecodes.AConstNull;
import org.jikesrvm.osr.bytecodes.DoubleStore;
import org.jikesrvm.osr.bytecodes.FloatStore;
import org.jikesrvm.osr.bytecodes.Goto;
import org.jikesrvm.osr.bytecodes.IntStore;
import org.jikesrvm.osr.bytecodes.InvokeCompiledMethod;
import org.jikesrvm.osr.bytecodes.InvokeStatic;
import org.jikesrvm.osr.bytecodes.LoadDoubleConst;
import org.jikesrvm.osr.bytecodes.LoadFloatConst;
import org.jikesrvm.osr.bytecodes.LoadIntConst;
import org.jikesrvm.osr.bytecodes.LoadLongConst;
import org.jikesrvm.osr.bytecodes.LoadRetAddrConst;
import org.jikesrvm.osr.bytecodes.LoadWordConst;
import org.jikesrvm.osr.bytecodes.LongStore;
import org.jikesrvm.osr.bytecodes.Nop;
import org.jikesrvm.osr.bytecodes.ParamInitEnd;
import org.jikesrvm.osr.bytecodes.Pop;
import org.jikesrvm.osr.bytecodes.RefStore;
import org.jikesrvm.osr.bytecodes.PseudoBytecode;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Offset;

public class ExecutionState implements OSRConstants, BytecodeConstants {

  /** the caller's state if this method is an inlinee */
  public ExecutionState callerState = null;

  /** callee's compiled method id */
  public int callee_cmid = -1;

  /** the method of which the execution state belongs to */
  public NormalMethod meth;

  /** the program pointer (bytecode index) */
  public int bcIndex;

  /**
   * runtime values of locals and stack expressions at the bytecode index Each
   * element is an object of VariableElement.
   */
  public LinkedList<VariableElement> varElms;

  /** the thread on which the activation is running */
  public RVMThread thread;

  /** the offset of frame pointer of the activation. */
  public Offset fpOffset;

  /** the callee (threadSwitch)'s frame pointer of this activation. */
  public Offset tsFPOffset;

  /**
   * the compiled method id of the activation (a Java method may have multiple
   * version of compiled method
   */
  public int cmid;

  /**
   * Initializer
   * @param whichThread
   * @param framePointerOffset
   * @param compiledMethodID
   * @param pc
   * @param tsFPOffset
   */
  public ExecutionState(RVMThread whichThread, Offset framePointerOffset, int compiledMethodID, int pc,
                            Offset tsFPOffset) {
    this.thread = whichThread;
    this.fpOffset = framePointerOffset;
    this.cmid = compiledMethodID;
    this.bcIndex = pc;
    this.tsFPOffset = tsFPOffset;

    this.varElms = new LinkedList<VariableElement>();
    this.meth = (NormalMethod) CompiledMethods.getCompiledMethod(cmid).getMethod();
  }

  /////////////////////////////
  // instance methods for construction
  ////////////////////////////

  /** add a VariableElement */

  public void add(VariableElement elm) {
    this.varElms.add(elm);
  }

  /** insert as the first element, for convinience. */
  public void addFirst(VariableElement elm) {
    this.varElms.addFirst(elm);
  }

  /** returns thread. */
  public RVMThread getThread() {
    return this.thread;
  }

  public Offset getFPOffset() {
    return this.fpOffset;
  }

  public void setMethod(NormalMethod m) {
    this.meth = m;
  }

  public NormalMethod getMethod() {
    return this.meth;
  }

  public Offset getTSFPOffset() {
    return this.tsFPOffset;
  }

  /** print the current state for debugging */
  public void printState() {
    VM.sysWriteln("Execution state of " + meth);
    VM.sysWriteln("    thread index : ", thread.getThreadSlot());
    VM.sysWriteln("       FP offset : ", fpOffset);
    VM.sysWriteln("            cmid : ", cmid);
    VM.sysWriteln("         bcIndex : ", bcIndex);

    for (VariableElement var : varElms) {
      VM.sysWrite("  " + var + "\n");
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
    this.rid = ObjectHolder.handinRefs(this.objs);

    PseudoBytecode head = new Nop();
    PseudoBytecode tail = head;

    int elmcount = 0;
    // restore parameters first;
    // restore "this"
    if (!this.meth.isStatic()) {
      VariableElement var = varElms.get(elmcount);
      tail = processElement(var, tail, elmcount);
      elmcount++;

      if (VM.VerifyAssertions) {
        VM._assert(var.isLocal() && (var.getNumber() == 0));
      }
    }
    // restore other parameters,
    int paranum = this.meth.getParameterTypes().length;
    for (int i = 0; i < paranum; i++) {
      VariableElement var = varElms.get(elmcount);
      tail = processElement(var, tail, elmcount);
      elmcount++;
      if (VM.VerifyAssertions) {
        VM._assert(var.isLocal());
        // the number may not match because of long and double type
      }
    }
    // ok, ready to indicate param initialized, thread switch
    // and stack overflow check happens here
    tail.next = new ParamInitEnd();
    tail = tail.next;

    // restore other locals and stack slots, assuming stack element
    // were sorted
    for (; elmcount < size; elmcount++) {
      VariableElement var = varElms.get(elmcount);
      tail = processElement(var, tail, elmcount);
    }// end of for loop

    if (this.objnum != 0) {
      tail.next = new LoadIntConst(this.rid);
      tail = tail.next;

      tail.next = new InvokeStatic(CLEANREFS);
      tail = tail.next;
    } else {
      ObjectHolder.cleanRefs(this.rid);
    }

    // default situation
    int branchTarget = this.bcIndex;

    /* when this method must start with a call of callee,
     * we are using invokeCompiledMethod,
     */
    if (callee_cmid != -1) {
      // remember the callee's cmid, and the index of original index
      tail.next = new InvokeCompiledMethod(callee_cmid, this.bcIndex);
      tail = tail.next;

      // if this method needs a call, than we must jump to
      // the instruction after the call.
      BytecodeStream bcodes = this.meth.getBytecodes();
      bcodes.reset(this.bcIndex);

      int code = bcodes.nextInstruction();

      switch (code) {
        case JBC_invokeinterface: {
          branchTarget = this.bcIndex + 5;
          break;
        }
        case JBC_invokespecial:
        case JBC_invokestatic:
        case JBC_invokevirtual: {
          branchTarget = this.bcIndex + 3;
          break;
        }
        default: {
          if (VM.VerifyAssertions) {
            VM._assert(VM.NOT_REACHED,
                       "ExecutionState: unknown bytecode " + code + " at " + this.bcIndex + "@" + this.meth);
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
      Goto togo = new Goto(branchTarget);
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

  private PseudoBytecode processElement(VariableElement var, PseudoBytecode tail, int i) {
    switch (var.getTypeCode()) {
      case INT: {
        tail.next = new LoadIntConst(var.getIntBits());
        tail = tail.next;

        if (var.isLocal()) {
          tail.next = new IntStore(var.getNumber());
          tail = tail.next;
        }
        break;
      }
      case FLOAT: {
        tail.next = new LoadFloatConst(var.getIntBits());
        tail = tail.next;

        if (var.isLocal()) {
          tail.next = new FloatStore(var.getNumber());
          tail = tail.next;
        }
        break;
      }
      case LONG: {
        tail.next = new LoadLongConst(var.getLongBits());
        tail = tail.next;

        if (var.isLocal()) {
          tail.next = new LongStore(var.getNumber());
          tail = tail.next;
        }
        break;
      }
      case DOUBLE: {
        tail.next = new LoadDoubleConst(var.getLongBits());
        tail = tail.next;

        if (var.isLocal()) {
          tail.next = new DoubleStore(var.getNumber());
          tail = tail.next;
        }
        break;
      }
      case RET_ADDR: {
        tail.next = new LoadRetAddrConst(var.getIntBits());
        tail = tail.next;

        if (var.isLocal()) {
          tail.next = new RefStore(var.getNumber());
          tail = tail.next;
        }
        break;
      }
      case REF: {
        this.objs[i] = var.getObject();

        if (this.objs[i] != null) {

          tail.next = new LoadIntConst(this.rid);
          tail = tail.next;

          tail.next = new LoadIntConst(i);
          tail = tail.next;

          // the opt compiler will adjust the type of
          // return value to the real type of object
          // when it sees the invoke target is GETREFAT
          tail.next = new InvokeStatic(GETREFAT);
          tail = tail.next;
        } else {
          // just give an aconst_null
          tail.next = new AConstNull();
          tail = tail.next;
        }

        if (var.isLocal()) {
          tail.next = new RefStore(var.getNumber());
          tail = tail.next;
        }

        this.objnum++;

        break;
      }
      case WORD: {
        tail.next = new LoadWordConst(var.getWord());
        tail = tail.next;

        if (var.isLocal()) {
          tail.next = new RefStore(var.getNumber());
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

  private short maxStackHeight = 0;

  public short getMaxStackHeight() {
    return this.maxStackHeight;
  }

  private int computeStackHeight(PseudoBytecode head) {
    /* skip the first Nop */
    PseudoBytecode bcode = head.next;
    short height = 0;
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

  private static PseudoBytecode adjustStackHeight(PseudoBytecode last, int height) {
    // append pop
    for (int i = 0; i < height; i++) {
      last.next = new Pop();
      last = last.next;
    }

    return last;
  }

  /* add padding (NOP) at the beginning of pseudo bytecode
   * to make the new bytecode size dividable by 4, then no branch
   * target adjustment is needed in the original code.
   */
  private static int paddingBytecode(PseudoBytecode head) {
    /* skip the first Nop. */
    PseudoBytecode bcode = head.next;

    /* count the total size of prologue code. */
    int bsize = 0;
    while (bcode != null) {
      bsize += bcode.getSize();
      bcode = bcode.next;
    }

    /* insert Nop at the beginning to make the code size of x4. */
    int padding = 3 - (bsize + 3) & 0x03;

    for (int i = 0; i < padding; i++) {
      bcode = new Nop();
      bcode.next = head.next;
      head.next = bcode;
    }

    bsize += padding;

    return bsize;
  }

  /* generating binary code from pseudo code, the size and the code
   * list are padded and well calculated.
   */
  private static byte[] generateBinaries(PseudoBytecode bhead, int bsize) {

    /* patch the LoalAddrConst instruction, and generate codes. */
    byte[] codes = new byte[bsize];

    /* skip the first NOP */
    PseudoBytecode bcode = bhead.next;
    int pos = 0;
    while (bcode != null) {

      int size = bcode.getSize();

      if (bcode instanceof LoadRetAddrConst) {
        LoadRetAddrConst laddr = (LoadRetAddrConst) bcode;

        /* CAUTION: path relative offset only. */
        laddr.patch(laddr.getOffset() + bsize);
      }

      if (VM.TraceOnStackReplacement) VM.sysWriteln(pos + " : " + bcode.toString());

      System.arraycopy(bcode.getBytes(), 0, codes, pos, size);

      pos += size;
      bcode = bcode.next;
    }

    return codes;
  }

  @Override
  public String toString() {
    StringBuffer buf = new StringBuffer("Execution state " + this.bcIndex + "@" + this.meth + " " + this.thread);
    for (int i = 0, n = varElms.size(); i < n; i++) {
      VariableElement var = varElms.get(i);
      buf.append("\n  ");
      buf.append(var);
    }

    return new String(buf);
  }
}
