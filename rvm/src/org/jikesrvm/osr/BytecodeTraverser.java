/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.osr;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.BytecodeConstants;
import org.jikesrvm.classloader.BytecodeStream;
import org.jikesrvm.classloader.ClassLoaderConstants;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.ExceptionHandlerMap;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.osr.bytecodes.InvokeStatic;

/**
 * BytecodeTraverser does depth first search on a bytecode
 * array, determines the type information of locals and stacks at
 * insteresting point.
 *
 * This class only intends to provide type information for on-stack
 * replacement, which needs to know the type of a value.  This class
 * can only tells basic type information such as : REFERENCE, LONG,
 * DOUBLE, FLOAT, INT, and ReturnAddress.  Not like GCMap which tells
 * GC a value is REFERENCE or NON-REFERENCE we also want to know it
 * is INT or DOUBLE, and takes two words value or one word.
 *
 * The produced type information has to be adjusted by consulting
 * GC maps because two different types may merge at one program point
 * (REF and non-REF types). Bytecode verifier will make the type info
 * undefined in that case. But this class won't know. So the caller
 * should check the GC map to validate a REF type variable.
 *
 * More or less, this class needs to do the same work as a bytecode
 * verifier, which tells the type and size of each locals and stacks.
 * The JSR/RET instructions pose the difficulty to our case. However,
 * we can assume the bytecode is verified. We use following assumptions:
 *   1. After JSR, the stack was not changed, only local variable
 *      type needs to merge with FINALLY clause.
 *   2. We need program-point specific stack type, but only need
 *      the summary of local types. Thus, after analysis, local
 *      types are same for all PCs.
 */
public class BytecodeTraverser implements BytecodeConstants, ClassLoaderConstants, OSRConstants {

  /////// COMMON
  /* to handle ret address which is not produced by JSR, we need a
   * separate array to track that.
   */
  private int[] retaddr;
  private int addr;

  private byte[] visitedpc;
  private boolean TRACE = false;

  // when computing infor for partial bytecodes
  // donot following bytecodes out of range
  private boolean ignoreGotos = false;
  private BytecodeStream bytecodes;

  /////// COMPUTING_TYPE_INFO
  /* type information of local variables and stack slots */
  private byte[] ltypes;
  private byte[] stypes;

  ///////////////////////////
  // COMPUTE TYPE INFORMATION
  //////////////////////////
  /**
   * Computes types of local variable and stack slots at an interesting point
   * for future querying. Computing type info and retrieval should not be
   * reentered. The type info of local variable is not accurate about reference
   * types, see JVM SPEC (2nd edition) p 146.  The caller can consult GC map
   * to verify if a local is a reference or not.
   *
   * @param method whose bytecode to be queried
   * @param bcpoint the bytecode index which is the interesting point
   *               at the mean time, we only support one PC.
   * @return whether the pc is a valid program point of the method
   */
  public boolean computeLocalStackTypes(NormalMethod method, int bcpoint) {
    if (VM.TraceOnStackReplacement) {
      VM.sysWrite("computing local and stack types of " + method + "\n");
    }

    int localsize = method.getLocalWords();
    ltypes = new byte[localsize];

    if (VM.TraceOnStackReplacement) {
      VM.sysWrite("local size : ");
      VM.sysWrite(localsize);
      VM.sysWrite("\n");
    }

    retaddr = new int[localsize];
    for (int i = 0; i < localsize; i++) {
      retaddr[i] = -1;
    }
    addr = -1;

    int stacksize = method.getOperandWords();
    stypes = new byte[stacksize];

    /* set marks for each byte code. */
    // always operate on original method
    this.bytecodes = method.getBytecodes();

    visitedpc = new byte[bytecodes.length()];

    /* then we initialize all stack and local type as void. */
    for (int i = 0, n = ltypes.length; i < n; i++) {
      ltypes[i] = VoidTypeCode;
    }

    TypeStack simstacks = new TypeStack(stacksize, VoidTypeCode);

    /* initialize local types from method signature.*/
    {
      TypeReference[] ptypes = method.getParameterTypes();
      int lidx = 0;
      if (!method.isStatic()) {
        ltypes[lidx++] = ClassTypeCode;
      }
      for (int i = 0, n = ptypes.length; i < n; i++) {
        byte tcode = ptypes[i].getName().parseForTypeCode();
        ltypes[lidx++] = tcode;
        if ((tcode == LongTypeCode) || (tcode == DoubleTypeCode)) {
          ltypes[lidx++] = VoidTypeCode;
        }
      }
    }

    /* scan start from method entry */
    boolean found = scanBlocks(method, bytecodes, true, bcpoint, ltypes, stypes, 0, simstacks, null);
    /* scan for exception handler. */
    if (!found) {
      ExceptionHandlerMap ehmap = method.getExceptionHandlerMap();
      if (ehmap != null) {
        int[] handlerPCs = ehmap.getHandlerPC();
        for (int i = 0, n = handlerPCs.length; i < n; i++) {
          simstacks.clear();
          simstacks.push(ClassTypeCode);
          int startpc = handlerPCs[i];
          found = scanBlocks(method, bytecodes, true, bcpoint, ltypes, stypes, startpc, simstacks, null);
          if (found) {
            break;
          }
        }
      }
    }
    visitedpc = null;

    return true;
  }

  /**
   * Returns an array of type information of locals at the registered
   * program point. The size of array is fixed by MAX_LOCALS, the type
   * descriptor can be found in "ClassLoadConstants.java".
   *
   * @return an array of type information, or null
   */
  public byte[] getLocalTypes() {
    return ltypes;
  }

  /**
   * Returns an array of type information of stacks at a program
   * point.  The size of array is fixed by MAX_STACKS, the type
   * descriptor can be found in "ClassLoadConstants.java".
   *
   * @return an array of type information, or null
   */
  public byte[] getStackTypes() {
    return stypes;
  }

  //////////////////////////
  // COMPUTE STACK HEIGHTS
  //////////////////////////
  public void computeStackHeights(NormalMethod method, BytecodeStream bcodes, int[] stackHeights,
                                  boolean adjustExptable) {
    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("computing stack heights of method " + method.toString());
    }

    /* set marks for each byte code. */
    // this may be the specialized method
    bytecodes = bcodes;
    visitedpc = new byte[bytecodes.length()];

    int localsize = method.getLocalWords();
    retaddr = new int[localsize];
    for (int i = 0; i < localsize; i++) {
      retaddr[i] = -1;
    }
    addr = -1;

    int stacksize = method.getOperandWords();
    TypeStack simstacks = new TypeStack(stacksize, VoidTypeCode);

    /* scan start from method entry */
    {
      int startpc = 0;
      scanBlocks(method, bytecodes, false, -1, null, null, startpc, simstacks, stackHeights);
    }
    /* scan for exception handler. */
    {
      ExceptionHandlerMap ehmap = method.getExceptionHandlerMap();
      if (ehmap != null) {
        int[] handlerPCs = ehmap.getHandlerPC();

        for (int i = 0, n = handlerPCs.length; i < n; i++) {
          int startpc = handlerPCs[i];

          /* for baseline compilation, the SpecialCompiler
           * didnot adjust exception table, we has to adjust it
           * here.
           */
          if (adjustExptable && method.isForOsrSpecialization()) {
            startpc += method.getOsrPrologueLength();
          }

          simstacks.clear();
          simstacks.push(ClassTypeCode);
          scanBlocks(method, bytecodes, false, -1, null, null, startpc, simstacks, stackHeights);
        }
      }
    }
    visitedpc = null;
  }

  /**
   * Compute stack heights of bytecode stream (used for osr prologue)
   */
  public void prologueStackHeights(NormalMethod method, BytecodeStream bcodes, int[] stackHeights) {
    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("computing stack heights of method " + method.toString());
    }

    /* set marks for each byte code. */
    // this may be the specialized method
    bytecodes = bcodes;
    visitedpc = new byte[bytecodes.length()];

    ignoreGotos = true;

    int localsize = method.getLocalWords();
    retaddr = new int[localsize];
    for (int i = 0; i < localsize; i++) {
      retaddr[i] = -1;
    }
    addr = -1;

    int stacksize = method.getOperandWords();
    TypeStack simstacks = new TypeStack(stacksize, VoidTypeCode);

    /* scan start from method entry */
    {
      int startpc = 0;
      scanBlocks(method, bytecodes, false, -1, null, null, startpc, simstacks, stackHeights);
    }
    visitedpc = null;
  }

  /* returns type code of the return type from the signature.
  * SEE also : Atom.parseForReturnType
  */
  @SuppressWarnings("unused")
  private byte getReturnCodeFromSignature(String sig) {
    byte[] val = sig.getBytes();

    int i = 0;
    while (val[i++] != ')') ;
    return (val[i]);
  }

  ////////////////////////////
  // IMPLEMENTATION
  ///////////////////////////

  /* return true --> hit the bytecode pointed by PC */

  private boolean scanBlocks(NormalMethod method,    // which method
                             BytecodeStream bytecodes,         // the bytecodes
                             boolean doDFS,       // do a DFS or one-pass scan
                             int pcs,             // the target pcs, if doDFS
                             byte[] ltypes,       // the local types if doDFS
                             byte[] stypes,       // the stack types if doDFS
                             int startpc,         // start pc
                             TypeStack S,     // stack
                             int[] stackHeights) { // the stack height if not doDFS

    int localsize = method.getLocalWords() - 1;
    RVMClass declaringClass = method.getDeclaringClass();
    bytecodes.reset(startpc);

    boolean found = false;

    while (bytecodes.hasMoreBytecodes()) {
      int pc = bytecodes.index(); // get current pc
      if (visitedpc[pc] == 1) {
        return false;
      } else {
        visitedpc[pc] = 1;
      }

      if (doDFS && (pc == pcs)) {
        /* make a copy of stack frame and put into stypes. */
        byte[] stack = S.snapshot();
        System.arraycopy(stack, 0, stypes, 0, stack.length);
        return true;
      }

      if (!doDFS) {
        // record stack heights
        stackHeights[pc] = localsize + S.depth();
      }

      /* let's continue */
      int bcode = bytecodes.nextInstruction();

      if (TRACE) {
        if (bcode <= JBC_jsr_w) {
          VM.sysWriteln(pc + " : " + S.depth() + " : " + JBC_name[bcode]);
        } else {
          VM.sysWriteln(pc + " : " + S.depth() + " : impdep1");
        }
      }

      switch (bcode) {
        case JBC_nop:
          break;
        case JBC_aconst_null:
          S.push(ClassTypeCode);
          break;
        case JBC_iconst_m1:
        case JBC_iconst_0:
        case JBC_iconst_1:
        case JBC_iconst_2:
        case JBC_iconst_3:
        case JBC_iconst_4:
        case JBC_iconst_5:
          S.push(IntTypeCode);
          break;
        case JBC_lconst_0:
        case JBC_lconst_1:
          /* we should do the save order as opt compiler */
          S.push(VoidTypeCode);
          S.push(LongTypeCode);
          break;
        case JBC_fconst_0:
        case JBC_fconst_1:
        case JBC_fconst_2:
          S.push(FloatTypeCode);
          break;
        case JBC_dconst_0:
        case JBC_dconst_1:
          S.push(VoidTypeCode);
          S.push(DoubleTypeCode);
          break;
        case JBC_bipush:
          bytecodes.getByteValue();
          S.push(IntTypeCode);
          break;
        case JBC_sipush:
          bytecodes.getShortValue();
          S.push(IntTypeCode);
          break;
        case JBC_ldc:
        case JBC_ldc_w: {
          int cpoolidx = (bcode == JBC_ldc) ? bytecodes.getConstantIndex() : bytecodes.getWideConstantIndex();
          byte tdesc = declaringClass.getLiteralDescription(cpoolidx);
          switch (tdesc) {
            case CP_INT:
              S.push(IntTypeCode);
              break;
            case CP_FLOAT:
              S.push(FloatTypeCode);
              break;
            case CP_STRING:
              S.push(ClassTypeCode);
              break;
            case CP_CLASS:
              S.push(ClassTypeCode);
              break;
            default:
              if (VM.TraceOnStackReplacement) VM.sysWriteln("ldc unknown type " + tdesc);
              if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
              break;
          }  // end of switch
        }
        break;
        case JBC_ldc2_w: {
          int cpoolidx = bytecodes.getWideConstantIndex();
          byte tdesc = declaringClass.getLiteralDescription(cpoolidx);
          S.push(VoidTypeCode);
          switch (tdesc) {
            case CP_LONG:
              S.push(LongTypeCode);
              break;
            case CP_DOUBLE:
              S.push(DoubleTypeCode);
              break;
            default:
              if (VM.TraceOnStackReplacement) VM.sysWriteln("ldc2_w unknown type " + tdesc);
              if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
              break;
          }  // end of switch
        }
        break;
        case JBC_iload:
          bytecodes.getLocalNumber(); // skip local
          S.push(IntTypeCode);
          break;
        case JBC_lload:
          bytecodes.getLocalNumber(); // skip local
          S.push(VoidTypeCode);
          S.push(LongTypeCode);
          break;
        case JBC_fload:
          bytecodes.getLocalNumber(); // skip local
          S.push(FloatTypeCode);
          break;
        case JBC_dload:
          bytecodes.getLocalNumber();
          S.push(VoidTypeCode);
          S.push(DoubleTypeCode);
          break;
        case JBC_aload:
          bytecodes.getLocalNumber();
          S.push(ClassTypeCode);
          break;
        case JBC_iload_0:
        case JBC_iload_1:
        case JBC_iload_2:
        case JBC_iload_3:
          S.push(IntTypeCode);
          break;
        case JBC_lload_0:
        case JBC_lload_1:
        case JBC_lload_2:
        case JBC_lload_3:
          S.push(VoidTypeCode);
          S.push(LongTypeCode);
          break;
        case JBC_fload_0:
        case JBC_fload_1:
        case JBC_fload_2:
        case JBC_fload_3:
          S.push(FloatTypeCode);
          break;
        case JBC_dload_0:
        case JBC_dload_1:
        case JBC_dload_2:
        case JBC_dload_3:
          S.push(VoidTypeCode);
          S.push(DoubleTypeCode);
          break;
        case JBC_aload_0:
        case JBC_aload_1:
        case JBC_aload_2:
        case JBC_aload_3:
          S.push(ClassTypeCode);
          break;
        case JBC_iaload:
        case JBC_baload:
        case JBC_caload:
        case JBC_saload:
          S.pop();
          S.pop();
          S.push(IntTypeCode);
          break;
        case JBC_laload:
          S.pop();
          S.pop();
          S.push(VoidTypeCode);
          S.push(LongTypeCode);
          break;
        case JBC_faload:
          S.pop();
          S.pop();
          S.push(FloatTypeCode);
          break;
        case JBC_daload:
          S.pop();
          S.pop();
          S.push(VoidTypeCode);
          S.push(DoubleTypeCode);
          break;
        case JBC_aaload:
          S.pop();
          S.pop();
          S.push(ClassTypeCode);
          break;
        case JBC_istore: {
          S.pop();
          int index = bytecodes.getLocalNumber();
          if (doDFS) ltypes[index] = IntTypeCode;
        }
        break;
        case JBC_istore_0:
        case JBC_istore_1:
        case JBC_istore_2:
        case JBC_istore_3: {
          S.pop();
          int index = bcode - JBC_istore_0;
          if (doDFS) ltypes[index] = IntTypeCode;
        }
        break;
        case JBC_lstore: {
          S.pop();
          S.pop();
          int index = bytecodes.getLocalNumber();
          if (doDFS) {
            ltypes[index] = LongTypeCode;
            ltypes[index + 1] = VoidTypeCode;
          }
        }
        break;
        case JBC_lstore_0:
        case JBC_lstore_1:
        case JBC_lstore_2:
        case JBC_lstore_3: {
          S.pop();
          S.pop();
          int index = bcode - JBC_lstore_0;
          if (doDFS) {
            ltypes[index] = LongTypeCode;
            ltypes[index + 1] = VoidTypeCode;
          }
        }
        break;
        case JBC_fstore: {
          S.pop();
          int index = bytecodes.getLocalNumber();
          if (doDFS) ltypes[index] = FloatTypeCode;
        }
        break;
        case JBC_fstore_0:
        case JBC_fstore_1:
        case JBC_fstore_2:
        case JBC_fstore_3: {
          S.pop();
          int index = bcode - JBC_fstore_0;
          if (doDFS) ltypes[index] = FloatTypeCode;
        }
        break;
        case JBC_dstore: {
          S.pop();
          S.pop();
          int index = bytecodes.getLocalNumber();
          if (doDFS) {
            ltypes[index] = DoubleTypeCode;
            ltypes[index + 1] = VoidTypeCode;
          }
        }
        break;
        case JBC_dstore_0:
        case JBC_dstore_1:
        case JBC_dstore_2:
        case JBC_dstore_3: {
          S.pop();
          S.pop();
          int index = bcode - JBC_dstore_0;
          if (doDFS) {
            ltypes[index] = DoubleTypeCode;
            ltypes[index + 1] = VoidTypeCode;
          }
        }
        break;
        case JBC_astore: {
          // caution: astore may save return address type
          int index = bytecodes.getLocalNumber();
          byte tcode = S.pop();

          if (doDFS) ltypes[index] = tcode;

          // for ret address.
          if (tcode == ReturnAddressTypeCode) {
            retaddr[index] = addr;
            addr = -1;
          }
        }
        break;
        case JBC_astore_0:
        case JBC_astore_1:
        case JBC_astore_2:
        case JBC_astore_3: {
          // caution: astore may save return address type
          int index = bcode - JBC_astore_0;
          byte tcode = S.pop();

          if (doDFS) ltypes[index] = tcode;

          // for ret address.
          if (tcode == ReturnAddressTypeCode) {
            retaddr[index] = addr;
            addr = -1;
          }
        }
        break;
        case JBC_iastore:
        case JBC_bastore:
        case JBC_castore:
        case JBC_sastore:
          S.pop(3);
          break;
        case JBC_lastore:
          S.pop(4);
          break;
        case JBC_fastore:
          S.pop(3);
          break;
        case JBC_dastore:
          S.pop(4);
          break;
        case JBC_aastore:
          S.pop(3);
          break;
        case JBC_pop:
          S.pop();
          break;
        case JBC_pop2:
          S.pop(2);
          break;
        case JBC_dup: {
          byte v1 = S.peek();
          S.push(v1);
        }
        break;
        case JBC_dup_x1: {
          byte v1 = S.peek();
          S.pop();
          byte v2 = S.peek();
          S.pop();
          S.push(v1);
          S.push(v2);
          S.push(v1);
        }
        break;
        case JBC_dup_x2: {
          byte v1 = S.peek();
          S.pop();
          byte v2 = S.peek();
          S.pop();
          byte v3 = S.peek();
          S.pop();
          S.push(v1);
          S.push(v3);
          S.push(v2);
          S.push(v1);
        }
        break;
        case JBC_dup2: {
          byte v1 = S.peek();
          S.pop();
          byte v2 = S.peek();
          S.push(v1);
          S.push(v2);
          S.push(v1);
        }
        break;
        case JBC_dup2_x1: {
          byte v1 = S.peek();
          S.pop();
          byte v2 = S.peek();
          S.pop();
          byte v3 = S.peek();
          S.pop();
          S.push(v2);
          S.push(v1);
          S.push(v3);
          S.push(v2);
          S.push(v1);
        }
        break;
        case JBC_dup2_x2: {
          byte v1 = S.peek();
          S.pop();
          byte v2 = S.peek();
          S.pop();
          byte v3 = S.peek();
          S.pop();
          byte v4 = S.peek();
          S.pop();
          S.push(v2);
          S.push(v1);
          S.push(v4);
          S.push(v3);
          S.push(v2);
          S.push(v1);
        }
        break;
        case JBC_swap: {
          byte v1 = S.peek();
          S.pop();
          byte v2 = S.peek();
          S.pop();
          S.push(v1);
          S.push(v2);
        }
        break;
        case JBC_iadd:
        case JBC_isub:
        case JBC_imul:
        case JBC_idiv:
        case JBC_irem:
        case JBC_iand:
        case JBC_ior:
        case JBC_ixor:
        case JBC_ishl:
        case JBC_ishr:
        case JBC_iushr:
          S.pop();
          break;
        case JBC_ladd:
        case JBC_lsub:
        case JBC_lmul:
        case JBC_ldiv:
        case JBC_lrem:
        case JBC_land:
        case JBC_lor:
        case JBC_lxor:
          S.pop(2);
          break;
        case JBC_lshl:
        case JBC_lshr:
        case JBC_lushr:
          S.pop();
          break;
        case JBC_fadd:
        case JBC_fsub:
        case JBC_fmul:
        case JBC_fdiv:
        case JBC_frem:
          S.pop();
          break;
        case JBC_dadd:
        case JBC_dsub:
        case JBC_dmul:
        case JBC_ddiv:
        case JBC_drem:
          S.pop(2);
          break;
        case JBC_ineg:
        case JBC_lneg:
        case JBC_fneg:
        case JBC_dneg:
          break;
        case JBC_iinc: {
          int index = bytecodes.getLocalNumber();
          /* int value = */
          bytecodes.getIncrement();
          if (doDFS) ltypes[index] = IntTypeCode;
        }
        break;
        case JBC_i2l:
          S.pop();
          S.push(VoidTypeCode);
          S.push(LongTypeCode);
          break;
        case JBC_i2f:
          S.pop();
          S.push(FloatTypeCode);
          break;
        case JBC_i2d:
          S.pop();
          S.push(VoidTypeCode);
          S.push(DoubleTypeCode);
          break;
        case JBC_l2i:
          S.pop(2);
          S.push(IntTypeCode);
          break;
        case JBC_l2f:
          S.pop(2);
          S.push(FloatTypeCode);
          break;
        case JBC_l2d:
          S.pop(2);
          S.push(VoidTypeCode);
          S.push(DoubleTypeCode);
          break;
        case JBC_f2i:
          S.pop();
          S.push(IntTypeCode);
          break;
        case JBC_f2l:
          S.pop();
          S.push(VoidTypeCode);
          S.push(LongTypeCode);
          break;
        case JBC_f2d:
          S.pop();
          S.push(VoidTypeCode);
          S.push(DoubleTypeCode);
          break;
        case JBC_d2i:
          S.pop(2);
          S.push(IntTypeCode);
          break;
        case JBC_d2l:
          S.pop(2);
          S.push(VoidTypeCode);
          S.push(LongTypeCode);
          break;
        case JBC_d2f:
          S.pop(2);
          S.push(FloatTypeCode);
          break;
        case JBC_int2byte:
        case JBC_int2char:
        case JBC_int2short:
          break;
        case JBC_lcmp:
          S.pop(4);
          S.push(IntTypeCode);
          break;
        case JBC_fcmpl:
        case JBC_fcmpg:
          S.pop(2);
          S.push(IntTypeCode);
          break;
        case JBC_dcmpl:
        case JBC_dcmpg:
          S.pop(4);
          S.push(IntTypeCode);
          break;
        case JBC_ifeq:
        case JBC_ifne:
        case JBC_iflt:
        case JBC_ifge:
        case JBC_ifgt:
        case JBC_ifle:
        case JBC_ifnull:
        case JBC_ifnonnull: {
          S.pop();

          // flowthrough first
          int nextpc = pc + 3;
          int target = pc + bytecodes.getBranchOffset();
          if (doDFS) {
            // make a copy of ltypes, stypes to pass in
            byte[] newltypes = new byte[ltypes.length];
            byte[] newstypes = new byte[stypes.length];
            System.arraycopy(ltypes, 0, newltypes, 0, ltypes.length);
            System.arraycopy(stypes, 0, newstypes, 0, stypes.length);
            found = scanBlocks(method, bytecodes, true, pcs, newltypes, newstypes, nextpc, new TypeStack(S), null);
            if (found) {
              // copy back the ltypes and stypes
              System.arraycopy(newltypes, 0, ltypes, 0, ltypes.length);
              System.arraycopy(newstypes, 0, stypes, 0, stypes.length);

              return true;
            }
          } else {
            found = scanBlocks(method, bytecodes, false, -1, null, null, nextpc, new TypeStack(S), stackHeights);
          }
          bytecodes.reset(target);
        }
        break;
        case JBC_if_icmpeq:
        case JBC_if_icmpne:
        case JBC_if_icmplt:
        case JBC_if_icmpge:
        case JBC_if_icmpgt:
        case JBC_if_icmple:
        case JBC_if_acmpeq:
        case JBC_if_acmpne: {
          S.pop(2);

          // flowthrough first
          int nextpc = pc + 3;
          int target = pc + bytecodes.getBranchOffset();

          if (doDFS) {
            // make a copy of ltypes, stypes to pass in
            byte[] newltypes = new byte[ltypes.length];
            byte[] newstypes = new byte[stypes.length];
            System.arraycopy(ltypes, 0, newltypes, 0, ltypes.length);
            System.arraycopy(stypes, 0, newstypes, 0, stypes.length);
            found = scanBlocks(method, bytecodes, true, pcs, newltypes, newstypes, nextpc, new TypeStack(S), null);
            if (found) {
              // copy back the ltypes and stypes
              System.arraycopy(newltypes, 0, ltypes, 0, ltypes.length);
              System.arraycopy(newstypes, 0, stypes, 0, stypes.length);

              return true;
            }
          } else {
            found = scanBlocks(method, bytecodes, false, -1, null, null, nextpc, new TypeStack(S), stackHeights);
          }

          bytecodes.reset(target);
        }
        break;
        case JBC_goto: {
          int offset = bytecodes.getBranchOffset();
          if (!ignoreGotos) {
            bytecodes.reset(pc + offset);
          }
        }
        break;
        case JBC_goto_w: {
          int offset = bytecodes.getWideBranchOffset();
          if (!ignoreGotos) {
            bytecodes.reset(pc + offset);
          }
        }
        break;
        case JBC_jsr:
        case JBC_jsr_w: {
          // flow through firs
          int nextpc = pc + ((bcode == JBC_jsr) ? 3 : 5);
          int target = pc + ((bcode == JBC_jsr) ? bytecodes.getBranchOffset() : bytecodes.getWideBranchOffset());

          if (doDFS) {
            // make a copy of ltypes, stypes to pass in
            byte[] newltypes = new byte[ltypes.length];
            byte[] newstypes = new byte[stypes.length];
            System.arraycopy(ltypes, 0, newltypes, 0, ltypes.length);
            System.arraycopy(stypes, 0, newstypes, 0, stypes.length);
            found = scanBlocks(method, bytecodes, true, pcs, newltypes, newstypes, nextpc, new TypeStack(S), null);
            if (found) {
              // copy back the ltypes and stypes
              System.arraycopy(newltypes, 0, ltypes, 0, ltypes.length);
              System.arraycopy(newstypes, 0, stypes, 0, stypes.length);

              return true;
            }
          } else {
            found = scanBlocks(method, bytecodes, false, -1, null, null, nextpc, new TypeStack(S), stackHeights);
          }

          // branch to jsr subroutine
          // remember return address for ret.
          addr = pc + ((bcode == JBC_jsr) ? 3 : 5);
          S.push(ReturnAddressTypeCode);
          bytecodes.reset(target);
        }
        break;
        case JBC_ret: {
          // the OPT compiler set local to null after _ret instruction,
          // then we should clean it here also, otherwise it will
          // throw a null pointer exception.
          // HOWEVER, it is not part of JVM spec.
          int index = bytecodes.getLocalNumber();

          if (doDFS) ltypes[index] = VoidTypeCode;

          /* the ret address may be saved by a PSEUDO_LoadRetAddrConstant
          */
          if (retaddr[index] != -1) {
            bytecodes.reset(retaddr[index]);
            retaddr[index] = -1;
          } else {
            // now we hit ret, return out
            return false;
          }
        }
        break;
        case JBC_tableswitch: {
          S.pop();
          bytecodes.alignSwitch();
          int defaultval = bytecodes.getDefaultSwitchOffset();
          int low = bytecodes.getLowSwitchValue();
          int high = bytecodes.getHighSwitchValue();

          // write down a list of targets
          int npairs = high - low + 1;
          int[] offsets = new int[npairs];
          for (int i = 0; i < npairs; i++) {
            offsets[i] = bytecodes.getTableSwitchOffset(i);
          }

          for (int i = 0; i < npairs; i++) {
            int tgtpc = pc + offsets[i];

            if (doDFS) {
              // make a copy of ltypes, stypes to pass in
              byte[] newltypes = new byte[ltypes.length];
              byte[] newstypes = new byte[stypes.length];

              System.arraycopy(ltypes, 0, newltypes, 0, ltypes.length);
              System.arraycopy(stypes, 0, newstypes, 0, stypes.length);
              found = scanBlocks(method, bytecodes, true, pcs, newltypes, newstypes, tgtpc, new TypeStack(S), null);
              if (found) {
                // copy back the ltypes and stypes
                System.arraycopy(newltypes, 0, ltypes, 0, ltypes.length);
                System.arraycopy(newstypes, 0, stypes, 0, stypes.length);

                return true;
              }
            } else {
              found = scanBlocks(method, bytecodes, false, -1, null, null, tgtpc, new TypeStack(S), stackHeights);
            }
          }

          // default
          {
            int tgtpc = pc + defaultval;

            if (doDFS) {
              // make a copy of ltypes, stypes to pass in
              byte[] newltypes = new byte[ltypes.length];
              byte[] newstypes = new byte[stypes.length];
              System.arraycopy(ltypes, 0, newltypes, 0, ltypes.length);
              System.arraycopy(stypes, 0, newstypes, 0, stypes.length);
              found = scanBlocks(method, bytecodes, true, pcs, newltypes, newstypes, tgtpc, new TypeStack(S), null);
              if (found) {
                // copy back the ltypes and stypes
                System.arraycopy(newltypes, 0, ltypes, 0, ltypes.length);
                System.arraycopy(newstypes, 0, stypes, 0, stypes.length);
              }
            } else {
              found = scanBlocks(method, bytecodes, false, -1, null, null, tgtpc, new TypeStack(S), stackHeights);
            }
            return found;
          }
        }
        case JBC_lookupswitch: {
          S.pop(); // pop the key
          bytecodes.alignSwitch();
          int defaultval = bytecodes.getDefaultSwitchOffset();
          int npairs = bytecodes.getSwitchLength();

          int[] matches = new int[npairs];
          int[] offsets = new int[npairs];
          for (int i = 0; i < npairs; i++) {
            matches[i] = bytecodes.getLookupSwitchValue(i);
            offsets[i] = bytecodes.getLookupSwitchOffset(i);
          }

          for (int i = 0; i < npairs; i++) {
            //int match = matches[i];
            int offset = offsets[i];

            int tgtpc = pc + offset;

            if (doDFS) {
              // make a copy of ltypes, stypes to pass in
              byte[] newltypes = new byte[ltypes.length];
              byte[] newstypes = new byte[stypes.length];

              System.arraycopy(ltypes, 0, newltypes, 0, ltypes.length);
              System.arraycopy(stypes, 0, newstypes, 0, stypes.length);
              found = scanBlocks(method, bytecodes, true, pcs, newltypes, newstypes, tgtpc, new TypeStack(S), null);
              if (found) {
                // copy back the ltypes and stypes
                System.arraycopy(newltypes, 0, ltypes, 0, ltypes.length);
                System.arraycopy(newstypes, 0, stypes, 0, stypes.length);

                return true;
              }
            } else {
              found = scanBlocks(method, bytecodes, false, -1, null, null, tgtpc, new TypeStack(S), stackHeights);
            }
          }

          // default
          {
            int tgtpc = pc + defaultval;

            if (doDFS) {
              // make a copy of ltypes, stypes to pass in
              byte[] newltypes = new byte[ltypes.length];
              byte[] newstypes = new byte[stypes.length];

              System.arraycopy(ltypes, 0, newltypes, 0, ltypes.length);
              System.arraycopy(stypes, 0, newstypes, 0, stypes.length);
              found = scanBlocks(method, bytecodes, true, pcs, newltypes, newstypes, tgtpc, new TypeStack(S), null);
              if (found) {
                // copy back the ltypes and stypes
                System.arraycopy(newltypes, 0, ltypes, 0, ltypes.length);
                System.arraycopy(newstypes, 0, stypes, 0, stypes.length);
              }
            } else {
              found = scanBlocks(method, bytecodes, false, -1, null, null, tgtpc, new TypeStack(S), stackHeights);
            }
          }
        }
        return found;
        case JBC_ireturn:
          S.pop();
          return false;
        case JBC_lreturn:
          S.pop(2);
          return false;
        case JBC_freturn:
          S.pop();
          return false;
        case JBC_dreturn:
          S.pop(2);
          return false;
        case JBC_areturn:
          S.pop();
          return false;
        case JBC_return:
          return false;
        case JBC_getfield:
          S.pop();
        case JBC_getstatic: {
          FieldReference fieldRef = bytecodes.getFieldReference();
          TypeReference ftype = fieldRef.getFieldContentsType();
          byte tcode = ftype.getName().parseForTypeCode();
          if ((tcode == LongTypeCode) || (tcode == DoubleTypeCode)) {
            S.push(VoidTypeCode);
          }
          S.push(tcode);
        }
        break;
        case JBC_putstatic: {
          FieldReference fieldRef = bytecodes.getFieldReference();
          TypeReference ftype = fieldRef.getFieldContentsType();
          byte tcode = ftype.getName().parseForTypeCode();
          if ((tcode == LongTypeCode) || (tcode == DoubleTypeCode)) {
            S.pop(2);
          } else {
            S.pop();
          }
        }
        break;
        case JBC_putfield: {
          FieldReference fieldRef = bytecodes.getFieldReference();
          TypeReference ftype = fieldRef.getFieldContentsType();
          byte tcode = ftype.getName().parseForTypeCode();
          if ((tcode == LongTypeCode) || (tcode == DoubleTypeCode)) {
            S.pop(2);
          } else {
            S.pop();
          }
        }
        S.pop();
        break;
        case JBC_invokevirtual:
        case JBC_invokespecial:
        case JBC_invokestatic:
        case JBC_invokeinterface: {
          MethodReference callee = bytecodes.getMethodReference();

          int psize = callee.getParameterWords();

          S.pop(psize);

          if (bcode != JBC_invokestatic) {
            S.pop();     // pop the object reference
          }

          TypeReference rtype = callee.getReturnType();
          byte tcode = rtype.getName().parseForTypeCode();

          if (tcode == VoidTypeCode) {
            // nothing to do with void return type
          } else {
            if ((tcode == LongTypeCode) || (tcode == DoubleTypeCode)) {
              S.push(VoidTypeCode);
            }
            S.push(tcode);
          }

          if (bcode == JBC_invokeinterface) {
            bytecodes.alignInvokeInterface();
          }
        }
        break;
        case JBC_xxxunusedxxx:
          break;

        case JBC_new:
          bytecodes.getTypeReference(); // skip cpi of type
          S.push(ClassTypeCode);
          break;
        case JBC_newarray:
          S.pop();
          S.push(ArrayTypeCode);
          bytecodes.getArrayElementType(); // skip cpi of element type
          break;
        case JBC_anewarray:
          S.pop();
          S.push(ArrayTypeCode);
          bytecodes.getTypeReference(); // skip cpi of reference type
          break;
        case JBC_arraylength:
          S.pop();
          S.push(IntTypeCode);
          break;
        case JBC_athrow:
          S.clear();
          S.push(ClassTypeCode);
          return false;
        case JBC_checkcast:
          bytecodes.getTypeReference(); // skip cpi of reference type
          break;
        case JBC_instanceof:
          S.pop();
          S.push(IntTypeCode);
          bytecodes.getTypeReference(); // skip cpi of reference type
          break;
        case JBC_monitorenter:
        case JBC_monitorexit:
          S.pop();
          break;
        case JBC_wide: {
          int widecode = bytecodes.getWideOpcode();
          int index = bytecodes.getWideLocalNumber();
          switch (widecode) {
            case JBC_iload:
              S.push(IntTypeCode);
              break;
            case JBC_lload:
              S.push(LongTypeCode);
              break;
            case JBC_fload:
              S.push(FloatTypeCode);
              break;
            case JBC_dload:
              S.push(DoubleTypeCode);
              break;
            case JBC_aload:
              S.push(ClassTypeCode);
              break;
            case JBC_istore:
              S.pop();
              if (doDFS) ltypes[index] = IntTypeCode;
              break;
            case JBC_lstore:
              S.pop();
              if (doDFS) ltypes[index] = LongTypeCode;
              break;
            case JBC_fstore:
              S.pop();
              if (doDFS) ltypes[index] = FloatTypeCode;
              break;
            case JBC_dstore:
              S.pop();
              if (doDFS) ltypes[index] = DoubleTypeCode;
              break;
            case JBC_astore: {
              byte tcode = S.pop();
              if (doDFS) ltypes[index] = tcode;

              // for ret address.
              if (tcode == ReturnAddressTypeCode) {
                retaddr[index] = addr;
                addr = -1;
              }
            }
            break;
            case JBC_iinc: {
              bytecodes.getWideIncrement(); // skip increment
              if (doDFS) ltypes[index] = IntTypeCode;
            }
            break;
            case JBC_ret:
              if (doDFS) ltypes[index] = VoidTypeCode;

              if (retaddr[index] != -1) {
                bytecodes.reset(retaddr[index]);
                retaddr[index] = -1;
              } else {
                // now we hit ret, return out
                return false;
              }
              break;
            default:
              if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
              break;
          }
          break;
        }
        case JBC_multianewarray: {
          bytecodes.getTypeReference(); // skip type reference
          int dims = bytecodes.getArrayDimension();
          S.pop(dims);
          S.push(ArrayTypeCode);
        }
        break;
        case JBC_impdep1: {
          int pseudo_opcode = bytecodes.nextPseudoInstruction();
          switch (pseudo_opcode) {
            case PSEUDO_LoadIntConst:
              bytecodes.readIntConst(); // skip value
              S.push(IntTypeCode);
              break;
            case PSEUDO_LoadLongConst:
              bytecodes.readLongConst(); // skip value
              S.push(VoidTypeCode);
              S.push(LongTypeCode);
              break;
            case PSEUDO_LoadWordConst:
              if (VM.BuildFor32Addr) {
                bytecodes.readIntConst();
              } else {
                bytecodes.readLongConst(); // skip value
              }
              S.push(WordTypeCode);
              break;
            case PSEUDO_LoadFloatConst:
              bytecodes.readIntConst(); // skip value
              S.push(FloatTypeCode);
              break;
            case PSEUDO_LoadDoubleConst:
              bytecodes.readLongConst(); // skip value
              S.push(VoidTypeCode);
              S.push(DoubleTypeCode);
              break;
            case PSEUDO_LoadRetAddrConst:
              // remember the address for ret.
              addr = bytecodes.readIntConst(); // get address
              S.push(ReturnAddressTypeCode);
              break;
            case PSEUDO_InvokeStatic: {
              int mid = bytecodes.readIntConst(); // get METHIDX
              RVMMethod callee = InvokeStatic.targetMethod(mid);

              int psize = callee.getParameterWords();
              S.pop(psize);

              TypeReference rtype = callee.getReturnType();
              byte tcode = rtype.getName().parseForTypeCode();

              if (tcode == VoidTypeCode) {
                // nothing to do with void return type
              } else {
                if ((tcode == LongTypeCode) || (tcode == DoubleTypeCode)) {
                  S.push(VoidTypeCode);
                }
                S.push(tcode);
              }
              break;
            }
/*
        case PSEUDO_CheckCast:
          bytecodes.readIntConst(); // skip type id
          break;
*/
            case PSEUDO_InvokeCompiledMethod:
              int cmid = bytecodes.readIntConst(); // cmid
              bytecodes.readIntConst(); // skip bcindex

              RVMMethod callee = CompiledMethods.getCompiledMethod(cmid).getMethod();
              int psize = callee.getParameterWords();

              S.pop(psize);

              if (!callee.isStatic()) {
                S.pop();   // pop receiver
              }

              TypeReference rtype = callee.getReturnType();
              byte tcode = rtype.getName().parseForTypeCode();

              if (tcode == VoidTypeCode) {
                // nothing to do with void return type
              } else {
                if ((tcode == LongTypeCode) || (tcode == DoubleTypeCode)) {
                  S.push(VoidTypeCode);
                }
                S.push(tcode);
              }
              break;
            case PSEUDO_ParamInitEnd:
              break;
            default:
              if (VM.VerifyAssertions) {
                VM.sysWrite(" Error, no such pseudo code : " + pseudo_opcode + "\n");
                VM._assert(VM.NOT_REACHED);
              }
              return false;
          }
          break;
        }
        default:
          VM.sysWrite("Unknown bytecode : " + bcode + "\n");
          return false;
      }
    }

    /* did not found the PC. */
    return false;
  }
}

