/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR; 

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import java.util.*;

/*
 * OSR_LocalAndStackTypeMatcher does depth first search on a bytecode
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
 * 
 * @author Feng Qian    v0.1   16/06/01
 */

public class OSR_LocalAndStackTypeMatcher 
  implements VM_ClassLoaderConstants, VM_BytecodeConstants, OSR_Constants {
  
  /* donot allow multiple instances */
  private OSR_LocalAndStackTypeMatcher () {}

  /* the singleton instance */
  private static final OSR_LocalAndStackTypeMatcher singleton = 
    new OSR_LocalAndStackTypeMatcher();

  /**
   * Returns the singleton of type matcher
   */
  public static OSR_LocalAndStackTypeMatcher v() {
    return singleton;
  }

  /**
   * Registers a method and an interesting point for future querying.
   * 
   * @param method whose bytecode to be queried
   * @param pc     the bytecode index which is the interesting point
   *               at the mean time, we only support one PC.
   * @return whether the pc is a valid program point of the method
   */
  public static boolean register(VM_Method method, int pc) {
    if (VM.TraceOnStackReplacement) {
      VM.sysWrite("registerMethod : "+method+"\n");
    }

    int localsize = method.getLocalWords();
    ltypes = new byte[localsize];

    if (VM.TraceOnStackReplacement) {
      VM.sysWrite("local size : ");
      VM.sysWrite(localsize);
      VM.sysWrite("\n");
    }

    retaddr = new int[localsize];
    for (int i=0; i<localsize; i++) {
      retaddr[i] = -1;
    }
    addr = -1;

    int stacksize = method.getOperandWords();
    stypes = new byte[stacksize];

    scanMethod(method, pc, ltypes, stypes);

    if (VM.TraceOnStackReplacement) {
      VM.sysWrite("end of registerMethod\n");
    }

    return true;
  }

  /**
   * Returns an array of type information of locals at the registered
   * program point. The size of array is fixed by MAX_LOCALS, the type
   * descriptor can be found in "VM_ClassLoadConstants.java".
   *
   * @return an array of type information, or null
   */
  public static byte[] getLocalTypes() {
    return ltypes;
  }

  /**
   * Returns an array of type information of stacks at a program
   * point.  The size of array is fixed by MAX_STACKS, the type
   * descriptor can be found in "VM_ClassLoadConstants.java".
   *
   * @return an array of type information, or null
   */
  public static byte[] getStackTypes() {
    return stypes;
  }

  /**
   *  Removes the registered method.
   */
  public static void clean() {
    ltypes = null;
    stypes = null;
  }


  ///////////////////
  // IMPLEMENTATION
  //////////////////
   
  private static byte[] ltypes;
  private static byte[] stypes; 

  /* to handle ret address which is not produced by JSR, we need a
   * seperate array to track that.
   */
  private static int[] retaddr;
  private static int   addr;

  /* do a DFS */
  private static byte[] visitedpc;
  private static void scanMethod(VM_Method method, 
				 int pc, 
				 byte[] ltypes, 
				 byte[] stypes) {

    /* set marks for each byte code. */
    byte[] bc = method.getBytecodeArray();	
    visitedpc = new byte[bc.length];

    /* then we initialize all stack and local type as void. */
    for (int i=0, n=ltypes.length; i<n; i++) {
      ltypes[i] = VoidTypeCode;
    }

    int stacksize = stypes.length;
    OSR_TypeStack simstacks = new OSR_TypeStack(stacksize, VoidTypeCode);

    /* initialize local types from method signature.*/
    {
      VM_Type[] ptypes = method.getParameterTypes();
      int lidx = 0;
      if (!method.isStatic()) {
	ltypes[lidx++] = ClassTypeCode;
      }
      for (int i=0, n=ptypes.length; i<n; i++) {
	byte tcode = ptypes[i].getDescriptor().parseForTypeCode();
	ltypes[lidx++] = tcode;
	if ( (tcode == LongTypeCode) 
	     || (tcode == DoubleTypeCode) ) {
	  ltypes[lidx++] = VoidTypeCode;
	}
      }
    }
    
    boolean found = false;
    /* scan start from method entry */
    {
      int startpc = 0;
      found = scanBlocks (method, pc, ltypes, stypes, 
                          startpc, simstacks);
    }
    /* scan for exception handler. */
    if (!found) {
      VM_ExceptionHandlerMap ehmap = method.getExceptionHandlerMap();
      if (ehmap != null) {
	int[] handlerPCs = ehmap.getHandlerPC();
	for (int i=0, n=handlerPCs.length; i<n; i++) {
	  simstacks.clear();
	  simstacks.push(ClassTypeCode);
	  int startpc = handlerPCs[i];
	  found = scanBlocks(method, pc, ltypes, stypes, 
			     startpc, simstacks);
	  if (found) {
	    break;
	  }
	} 		    
      }
    }
    visitedpc = null;
  }

  /* read an unsigned byte value. */
  private static int BYTE(byte[] bc, int pc) {
    return (bc[pc] & 0xFF);
  }

  /* read an unsigned short value. */
  private static int UWORD(byte[] bc, int pc) {
    return ( ((bc[pc]<<8) & 0xFF00) 
	     | (bc[pc+1] & 0xFF) );
  }

  /* read a signed short value. */   
  private static int WORD(byte[] bc, int pc) {
    return ( (bc[pc]<<8) 
	     | (bc[pc+1] & 0xFF) );
  }

  /* read a signed int value. */ 
  private static int DWORD(byte[] bc, int pc) {
    return ( (bc[pc]<<24)
	     |((bc[pc+1]<<16) & 0xFF0000)
	     |((bc[pc+2]<<8) & 0xFF00)
	     |(bc[pc+3] & 0xFF) );
  }

  /* returns type code of the return type from the signature.
   * SEE also : VM_Atom.parseForReturnType
   */       
  private static byte getReturnCodeFromSignature(String sig) {
    byte[] val = sig.getBytes();
    
    int i=0;
    while (val[i++] != ')');
    return (val[i]);
  }
  
  /* return true --> hit the bytecode pointed by PC */
  private static boolean scanBlocks (VM_Method method,
				     int pcs,
				     byte[] ltypes,
				     byte[] stypes,
				     int startpc,
				     OSR_TypeStack S) {

    VM_Class declaringClass = method.getDeclaringClass();
    byte[] bc = method.getBytecodeArray();

    boolean wide = false;
    int pc = startpc;
    int endpc = bc.length;
    int tmpl = 0;
    boolean found = false;

    do {
      if (visitedpc[pc] == 1) {
	return false;
      } else {
	visitedpc[pc] = 1;
      }

      if (pc == pcs) {
	/* make a copy of stack frame and put into stypes. */
	byte[] stack = S.snapshot();
	System.arraycopy(stack, 0, stypes, 0, stack.length);
	return true;
      }

      /* let's continue */
      int bcode = BYTE(bc, pc);
      
      switch (bcode) {
      case JBC_nop:
	pc++;
	break;
      case JBC_aconst_null:
	S.push(ClassTypeCode);
	pc++;
	break;
      case JBC_iconst_m1:
      case JBC_iconst_0:
      case JBC_iconst_1:
      case JBC_iconst_2:
      case JBC_iconst_3:
      case JBC_iconst_4:
      case JBC_iconst_5:
	S.push(IntTypeCode);
	pc++;
	break;
      case JBC_lconst_0:
      case JBC_lconst_1:
	/* we should do the save order as opt compiler */
	S.push(VoidTypeCode);
	S.push(LongTypeCode);
	pc++;
	break;
      case JBC_fconst_0:
      case JBC_fconst_1:
      case JBC_fconst_2:
	S.push(FloatTypeCode);
	pc++;
	break;
      case JBC_dconst_0:
      case JBC_dconst_1:
	S.push(VoidTypeCode);
	S.push(DoubleTypeCode);
	pc++;
	break;
      case JBC_bipush:
	S.push(IntTypeCode);
	pc += 2;
	break;
      case JBC_sipush:
	S.push(IntTypeCode);
	pc += 3;
	break;
      case JBC_ldc:
      case JBC_ldc_w:
	{
	  int cpoolidx = (bcode == JBC_ldc) ? 
	    BYTE(bc, pc+1) : UWORD(bc, pc+1);
	  byte tdesc = declaringClass.getLiteralDescription(cpoolidx);
	  switch (tdesc) {
	  case VM_Statics.INT_LITERAL:
	    S.push(IntTypeCode);
	    break;
	  case VM_Statics.FLOAT_LITERAL:
	    S.push(FloatTypeCode);
	    break;
	  case VM_Statics.STRING_LITERAL:
	    S.push(ClassTypeCode);
	    break;
	  }
	  
	  pc += (bcode == JBC_ldc) ? 2 : 3;
	  break;
	}
      case JBC_ldc2_w:
	{
	  int cpoolidx = UWORD(bc, pc+1);
	  byte tdesc = declaringClass.getLiteralDescription(cpoolidx);
	  S.push(VoidTypeCode);
	  switch (tdesc) {
	  case VM_Statics.LONG_LITERAL:
	    S.push(LongTypeCode);
	    break;
	  case VM_Statics.DOUBLE_LITERAL:
	    S.push(DoubleTypeCode);
	    break;
	  }
	  
	  pc += 3;
	  break;
	}
      case JBC_iload:
	if (wide) {
	  wide = false;
	  /* although we are using type from locals, 
	   * we can assign INT type to both stack and
	   * locals here
	   */
	  S.push (IntTypeCode);
	  // ltypes[UWORD(bc, pc+1)] = IntTypeCode;
	  // S.push (ltypes[UWORD(bc, pc+1]);
	  pc += 3;
	} else {
	  S.push (IntTypeCode);
	  // ltypes[BYTE(bc, pc+1)] = IntTypeCode;
	  // S.push (ltypes[BYTE(bc, pc+1)]);
	  pc += 2;
	}
	break;		    
      case JBC_lload:
	S.push(VoidTypeCode);
	S.push(LongTypeCode);
	if (wide) {
	  wide = false;
	  //		ltypes[UWORD(bc, pc+1)] = LongTypeCode;
	  //		ltypes[UWORD(bc, pc+1)+1] = VoidTypeCode;
	  pc += 3;
	} else {
	  //                ltypes[BYTE(bc, pc+1)] = LongTypeCode;
	  //		ltypes[BYTE(bc, pc+1)+1] = VoidTypeCode;
	  pc += 2;
	}
	break;
      case JBC_fload:
	if (wide) {
	  wide = false;
	  S.push (FloatTypeCode);
	  //		    ltypes[UWORD(bc, pc+1)] = FloatTypeCode;
	  // S.push (ltypes[UWORD(bc, pc+1)]);
	  pc += 3;
	} else {
	  S.push (FloatTypeCode);
	  //		    ltypes[BYTE(bc, pc+1)] = FloatTypeCode;
	  // S.push (ltypes[BYTE(bc, pc+1)]);
	  pc += 2;
	}
	break;
      case JBC_dload:
	S.push(VoidTypeCode);
	S.push(DoubleTypeCode);
	if (wide) {
	  wide = false;
	  //		ltypes[UWORD(bc, pc+1)] = DoubleTypeCode;
	  //		ltypes[UWORD(bc, pc+1)+1] = VoidTypeCode;
	  pc += 3;
	} else {
	  //		ltypes[BYTE(bc, pc+1)] = DoubleTypeCode;
	  //		ltypes[BYTE(bc, pc+1)+1] = VoidTypeCode;
	  pc += 2;
	}
	break;
      case JBC_aload:
	if (wide) {
	  wide = false;
	  S.push (ClassTypeCode);
	  //		    ltypes[UWORD(bc, pc+1)] = ClassTypeCode;
	  // S.push (ltypes[UWORD(bc, pc+1)]);
	  pc += 3;
	} else {
	  S.push (ClassTypeCode);
	  //		    ltypes[BYTE(bc, pc+1)] = ClassTypeCode;
	  // S.push (ltypes[BYTE(bc, pc+1)]);
	  pc += 2;
	}
	break;
      case JBC_iload_0:
      case JBC_iload_1:
      case JBC_iload_2:
      case JBC_iload_3:
	tmpl = bcode - JBC_iload_0;
	// ltypes[tmpl] = IntTypeCode;
	S.push (IntTypeCode);
	// S.push (ltypes[tmpl]);
	pc++;
	break;
      case JBC_lload_0:
      case JBC_lload_1:
      case JBC_lload_2:
      case JBC_lload_3:
	tmpl = bcode - JBC_lload_0;
	//		ltypes[tmpl] = LongTypeCode;
	//		ltypes[tmpl+1] = VoidTypeCode;
	S.push(VoidTypeCode);
	S.push(LongTypeCode);
	pc++;
	break;
      case JBC_fload_0:
      case JBC_fload_1:
      case JBC_fload_2:
      case JBC_fload_3:
	tmpl = bcode - JBC_fload_0;
	//		ltypes[tmpl] = FloatTypeCode;
	S.push (FloatTypeCode);
	// S.push (ltypes[tmpl]);
	pc++;
	break;
      case JBC_dload_0:
      case JBC_dload_1:
      case JBC_dload_2:
      case JBC_dload_3:
	tmpl = bcode - JBC_dload_0;
	//		ltypes[tmpl] = DoubleTypeCode;
	//		ltypes[tmpl+1] = VoidTypeCode;
	S.push(VoidTypeCode);
	S.push(DoubleTypeCode);
	pc++;
	break;
      case JBC_aload_0:
      case JBC_aload_1:
      case JBC_aload_2:
      case JBC_aload_3:
	tmpl = bcode - JBC_aload_0;
	//		ltypes[tmpl] = ClassTypeCode;
	S.push (ClassTypeCode);
	// S.push (ltypes[tmpl]);
	pc++;
	break;
      case JBC_iaload:
      case JBC_baload:
      case JBC_caload:
      case JBC_saload:
	S.pop ();
	S.pop ();
	S.push (IntTypeCode);
	pc++;
	break;
      case JBC_laload:
	S.pop();
	S.pop();
	S.push(VoidTypeCode);
	S.push(LongTypeCode);
	pc++;
	break;
      case JBC_faload:
	S.pop();
	S.pop();
	S.push (FloatTypeCode);
	pc++;
	break;
      case JBC_daload:
	S.pop();
	S.pop();
	S.push(VoidTypeCode);
	S.push(DoubleTypeCode);
	pc++;
	break;
      case JBC_aaload:
	S.pop();
	S.pop();
	S.push(ClassTypeCode);
	pc++;
	break;
      case JBC_istore:
	if (wide) {
	  wide = false;
	  S.pop();
	  ltypes[UWORD(bc, pc+1)] = IntTypeCode;
	  pc += 3;
	} else {
	  S.pop();
	  ltypes[BYTE(bc, pc+1)] = IntTypeCode;
	  pc += 2;
	}
	break;
      case JBC_istore_0:
      case JBC_istore_1:
      case JBC_istore_2:
      case JBC_istore_3:
	tmpl = bcode - JBC_istore_0;
	S.pop();
	ltypes[tmpl] = IntTypeCode;
	pc++;
	break;
      case JBC_lstore:
	S.pop();
	S.pop();
	if (wide) {
	  wide = false;
	  ltypes[UWORD(bc, pc+1)] = LongTypeCode;
	  ltypes[UWORD(bc, pc+1)+1] = VoidTypeCode;
	  pc += 3;
	} else {
	  ltypes[BYTE(bc, pc+1)] = LongTypeCode;
	  ltypes[BYTE(bc, pc+1)+1] = VoidTypeCode;
	  pc += 2;
	}
	break;
      case JBC_lstore_0:
      case JBC_lstore_1:
      case JBC_lstore_2:
      case JBC_lstore_3:
	tmpl = bcode - JBC_lstore_0;
	S.pop();
	S.pop();
	ltypes[tmpl] = LongTypeCode;
	ltypes[tmpl+1] = VoidTypeCode;
	pc++;
	break;
      case JBC_fstore:
	S.pop();
	if (wide) {
	  wide = false;
	  ltypes[UWORD(bc, pc+1)] = FloatTypeCode;
	  pc += 3;
	} else {
	  ltypes[BYTE(bc, pc+1)] = FloatTypeCode;
	  pc += 2;
	}
	break;	
      case JBC_fstore_0:
      case JBC_fstore_1:
      case JBC_fstore_2:
      case JBC_fstore_3:
	tmpl = bcode - JBC_fstore_0;
	S.pop();
	ltypes[tmpl] = FloatTypeCode;
	pc++;
	break;
      case JBC_dstore:
	S.pop();
	S.pop();
	if (wide) {
	  wide = false;
	  ltypes[UWORD(bc, pc+1)] = DoubleTypeCode;
	  ltypes[UWORD(bc, pc+1)+1] = VoidTypeCode;
	  pc += 3;
	} else {
	  ltypes[BYTE(bc, pc+1)] = DoubleTypeCode;
	  ltypes[BYTE(bc, pc+1)+1] = VoidTypeCode;
	  pc += 2;
	}
	break;
      case JBC_dstore_0:
      case JBC_dstore_1:
      case JBC_dstore_2:
      case JBC_dstore_3:
	tmpl = bcode - JBC_dstore_0;
	S.pop();
	S.pop();
	ltypes[tmpl] = DoubleTypeCode;
	ltypes[tmpl+1] = VoidTypeCode;
	pc++;
	break;
      case JBC_astore:
	// caution: astore may save return address type
	tmpl = wide ? UWORD(bc, pc+1) : BYTE(bc, pc+1);
	
	if (wide) {
	  wide = false;
	  pc += 3;
	} else {
	  pc += 2;
	}
	ltypes[tmpl] = S.pop();
	
	// for ret address.
	if (ltypes[tmpl] == AddressTypeCode) {
	  retaddr[tmpl] = addr;
	  addr = -1;
	}
	break;
      case JBC_astore_0:
      case JBC_astore_1:
      case JBC_astore_2:
      case JBC_astore_3:
	tmpl = bcode - JBC_astore_0;
	// caution: astore may save return address type
	ltypes[tmpl] = S.pop();
	
	// for ret address.
	if (ltypes[tmpl] == AddressTypeCode) {
	  retaddr[tmpl] = addr;
	  addr = -1;
	}
	pc++;
	break;
      case JBC_iastore:
      case JBC_bastore:
      case JBC_castore:
      case JBC_sastore:
	S.pop(3);
	pc++;
	break;
      case JBC_lastore:
	S.pop(4);
	pc++;
	break;
      case JBC_fastore:
	S.pop(3);
	pc++;
	break;
      case JBC_dastore:
	S.pop(4);
	pc++;
	break;
      case JBC_aastore:
	S.pop(3);
	pc++;
	break;
      case JBC_pop:
	S.pop();
	pc++;
	break;
      case JBC_pop2:
	S.pop(2);
	pc++;
	break;
      case JBC_dup:
	{
	  byte v1 = S.peek();
	  S.push(v1);
	}
	pc++;
	break;
      case JBC_dup_x1:
	{
	  byte v1 = S.peek();
	  S.pop();
	  byte v2 = S.peek();
	  S.pop();
	  S.push(v1);
	  S.push(v2);
	  S.push(v1);
	}
	pc++;
	break;
      case JBC_dup_x2:
	{
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
	pc++;
	break;
      case JBC_dup2:
	{
	  byte v1 = S.peek();
	  S.pop();
	  byte v2 = S.peek();
	  S.push(v1);
	  S.push(v2);
	  S.push(v1);
	}
	pc++;
	break;
      case JBC_dup2_x1:
	{
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
	pc++;
	break;
      case JBC_dup2_x2:
	{
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
	pc++;
	break;
      case JBC_swap:
	{
	  byte v1 = S.peek();
	  S.pop();
	  byte v2 = S.peek();
	  S.pop();
	  S.push(v1);
	  S.push(v2);
	}
	pc++;
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
	pc++;
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
	pc++;
	break;
      case JBC_lshl:
      case JBC_lshr:
      case JBC_lushr:
	S.pop();
	pc++;
	break;
      case JBC_fadd:
      case JBC_fsub:
      case JBC_fmul:
      case JBC_fdiv:
      case JBC_frem:
	S.pop();
	pc++;
	break;
      case JBC_dadd:
      case JBC_dsub:
      case JBC_dmul:
      case JBC_ddiv:
      case JBC_drem:
	S.pop(2);
	pc++;
	break;
      case JBC_ineg:
      case JBC_lneg:
      case JBC_fneg:
      case JBC_dneg:
	pc++;
	break;
      case JBC_iinc:
	if (wide) {
	  wide = false;
	  ltypes[UWORD(bc, pc+1)] = IntTypeCode;
	  pc += 5;
	} else {
	  ltypes[BYTE(bc, pc+1)] = IntTypeCode;
	  pc += 3;
	}
	break;
      case JBC_i2l:
	S.pop();
	S.push(VoidTypeCode);
	S.push(LongTypeCode);
	pc++;
	break;
      case JBC_i2f:
	S.pop();
	S.push(FloatTypeCode);
	pc++;
	break;
      case JBC_i2d:
	S.pop();
	S.push(VoidTypeCode);
	S.push(DoubleTypeCode);
	pc++;
	break;
      case JBC_l2i:
	S.pop(2);
	S.push(IntTypeCode);
	pc++;
	break;
      case JBC_l2f:
	S.pop(2);
	S.push(FloatTypeCode);
	pc++;
	break;
      case JBC_l2d:
	S.pop(2);
	S.push(VoidTypeCode);
	S.push(DoubleTypeCode);
	pc++;
	break;
      case JBC_f2i:
	S.pop();
	S.push(IntTypeCode);
	pc++;
	break;
      case JBC_f2l:
	S.pop();
	S.push(VoidTypeCode);
	S.push(LongTypeCode);
	pc++;
	break;
      case JBC_f2d:
	S.pop();
	S.push(VoidTypeCode);
	S.push(DoubleTypeCode);
	pc++;
	break;
      case JBC_d2i:
	S.pop(2);
	S.push(IntTypeCode);
	pc++;
	break;
      case JBC_d2l:
	S.pop(2);
	S.push(VoidTypeCode);
	S.push(LongTypeCode);
	pc++;
	break;
      case JBC_d2f:
	S.pop(2);
	S.push(FloatTypeCode);
	pc++;
	break;
      case JBC_int2byte:
      case JBC_int2char:
      case JBC_int2short:
	pc++;
	break;
      case JBC_lcmp:
	S.pop(4);
	S.push(IntTypeCode);
	pc++;
	break;
      case JBC_fcmpl:
      case JBC_fcmpg:
	S.pop(2);
	S.push(IntTypeCode);
	pc++;
	break;
      case JBC_dcmpl:
      case JBC_dcmpg:
	S.pop(4);
	S.push(IntTypeCode);
	pc++;
	break;
      case JBC_ifeq:
      case JBC_ifne:
      case JBC_iflt:
      case JBC_ifge:
      case JBC_ifgt:
      case JBC_ifle:
      case JBC_ifnull:
      case JBC_ifnonnull:
	S.pop();
	
	// flowthrough first
	{
	  int nextpc = pc + 3;
	  // make a copy of ltypes, stypes to pass in
	  byte[] newltypes = new byte[ltypes.length];
	  byte[] newstypes = new byte[stypes.length];
	  System.arraycopy(ltypes, 0, newltypes, 0, ltypes.length);
	  System.arraycopy(stypes, 0, newstypes, 0, stypes.length);
	  found = scanBlocks(method, pcs, newltypes, newstypes,
				     nextpc, new OSR_TypeStack(S));
	  if (found) {
	    // copy back the ltypes and stypes
	    System.arraycopy(newltypes, 0, ltypes, 0, ltypes.length);
	    System.arraycopy(newstypes, 0, stypes, 0, stypes.length);
	    
	    return true;
	  }
	}
	
	pc += WORD(bc, pc+1);
	break;
		
      case JBC_if_icmpeq:
      case JBC_if_icmpne:
      case JBC_if_icmplt:
      case JBC_if_icmpge:
      case JBC_if_icmpgt:
      case JBC_if_icmple:
      case JBC_if_acmpeq:
      case JBC_if_acmpne:
	S.pop(2);
	
	// flowthrough first
	{
	  int nextpc = pc + 3;
	  // make a copy of ltypes, stypes to pass in
	  byte[] newltypes = new byte[ltypes.length];
	  byte[] newstypes = new byte[stypes.length];
	  System.arraycopy(ltypes, 0, newltypes, 0, ltypes.length);
	  System.arraycopy(stypes, 0, newstypes, 0, stypes.length);
	  found = scanBlocks(method, pcs, newltypes, newstypes,
			     nextpc, new OSR_TypeStack(S));
	  if (found) {
	    // copy back the ltypes and stypes
	    System.arraycopy(newltypes, 0, ltypes, 0, ltypes.length);
	    System.arraycopy(newstypes, 0, stypes, 0, stypes.length);

	    return true;
	  }
	}
		
	pc += WORD(bc, pc+1);
	break;

      case JBC_goto:
	pc += WORD(bc, pc+1);
	break;
      case JBC_jsr:
      case JBC_jsr_w:
	// flow through first
	{
	  int nextpc = pc + ((bcode == JBC_jsr)?3:5);
	  
	  // make a copy of ltypes, stypes to pass in
	  byte[] newltypes = new byte[ltypes.length];
	  byte[] newstypes = new byte[stypes.length];
	  System.arraycopy(ltypes, 0, newltypes, 0, ltypes.length);
	  System.arraycopy(stypes, 0, newstypes, 0, stypes.length);
	  found = scanBlocks(method, pcs, newltypes, newstypes,
			     nextpc, new OSR_TypeStack(S));
	  if (found) {
	    // copy back the ltypes and stypes
	    System.arraycopy(newltypes, 0, ltypes, 0, ltypes.length);
	    System.arraycopy(newstypes, 0, stypes, 0, stypes.length);
	    
	    return true;
	  }
	}
	
	// branch to exception handler
	// remember return address for ret.
	addr = pc + ((bcode == JBC_jsr)?3:5);     
	S.push(AddressTypeCode);
	pc += ((bcode == JBC_jsr)?WORD(bc, pc+1):DWORD(bc, pc+1));
	break;
	
      case JBC_ret:
	// the OPT compiler set local to null after _ret instruction,
	// then we should clean it here also, otherwise it will
	// throw a null pointer exception.
	// HOWEVER, it is not part of JVM spec.
	if (wide) {
	  wide = false;
	  tmpl = UWORD(bc, pc+1);
	  //		    ltypes[tmpl] = AddressTypeCode;
	  ltypes[tmpl] = VoidTypeCode;
	  pc += 3;
	} else {
	  tmpl = BYTE(bc, pc+1);
	  //		    ltypes[tmpl] = AddressTypeCode;
	  ltypes[tmpl] = VoidTypeCode;
	  pc += 2;
	}
	
	/* the ret address may be saved by a PSEUDO_LoadAddrConstant
	 */
	if (retaddr[tmpl] != -1) {
	  pc = retaddr[tmpl];
	  retaddr[tmpl] = -1;
	  break;
	} else {
	  // now we hit ret, return out
	  return false;
	}
      case JBC_tableswitch:
	S.pop();
	{
	  int tablepc = pc;
	  // align pc
	  int padding = 3 - pc&0x03;
	  
	  pc++;
	  pc += padding;
	  
	  int def = DWORD(bc, pc);
	  pc += 4;
	  int low = DWORD(bc, pc);
	  pc += 4;
	  int high = DWORD(bc, pc);
	  pc += 4;
	  
	  // make a copy of ltypes, stypes to pass in
	  byte[] newltypes = new byte[ltypes.length];
	  byte[] newstypes = new byte[stypes.length];
	  
	  for (int i=0, n=high-low+1; i<n; i++) {
	    int tgtpc = tablepc + DWORD(bc, pc);
	    pc += 4;
	    
	    System.arraycopy(ltypes, 0, newltypes, 0, 
			     ltypes.length);
	    System.arraycopy(stypes, 0, newstypes, 0, 
			     stypes.length);
	    found = scanBlocks(method, pcs, newltypes, newstypes,
			       tgtpc, new OSR_TypeStack(S));
	    if (found) {
	      // copy back the ltypes and stypes
	      System.arraycopy(newltypes, 0, ltypes, 0, 
			       ltypes.length);
	      System.arraycopy(newstypes, 0, stypes, 0, 
			       stypes.length);
	      
	      return true;
	    }
	  }
	  // default
	  {
	    int tgtpc = tablepc + def;
	    
	    System.arraycopy(ltypes, 0, newltypes, 0, 
			     ltypes.length);
	    System.arraycopy(stypes, 0, newstypes, 0, 
			     stypes.length);
	    found = scanBlocks(method, pcs, newltypes, newstypes,
			       tgtpc, new OSR_TypeStack(S));
	    if (found) {
	      // copy back the ltypes and stypes
	      System.arraycopy(newltypes, 0, ltypes, 0, 
			       ltypes.length);
	      System.arraycopy(newstypes, 0, stypes, 0, 
			       stypes.length);
	    }
	  }
	}
	return found;
      case JBC_lookupswitch:
	S.pop(); // pop the key
	{
	  int lookuppc = pc;
	  // aligh pc
	  int padding = 3 - pc & 0x03;
	  
	  pc++;
	  pc += padding;
	  int def = DWORD(bc, pc);
	  pc += 4;
	  int npairs = DWORD(bc, pc);
	  pc += 4;
		    
	  // make a copy of ltypes, stypes to pass in
	  byte[] newltypes = new byte[ltypes.length];
	  byte[] newstypes = new byte[stypes.length];

	  for (int i=0; i<npairs; i++) {
	    pc += 4; // skip match
	    int offset = DWORD(bc, pc);
	    pc += 4;
	    int tgtpc = lookuppc + offset;
	    
	    System.arraycopy(ltypes, 0, newltypes, 0, 
			     ltypes.length);
	    System.arraycopy(stypes, 0, newstypes, 0, 
			     stypes.length);
	    found = scanBlocks(method, pcs, newltypes, newstypes,
			       tgtpc, new OSR_TypeStack(S));
	    if (found) {
	      // copy back the ltypes and stypes
	      System.arraycopy(newltypes, 0, ltypes, 0, 
			       ltypes.length);
	      System.arraycopy(newstypes, 0, stypes, 0, 
			       stypes.length);

	      return true;
	    }
	  }
	  
	  // default
	  {
	    int tgtpc = lookuppc + def;
	    
	    System.arraycopy(ltypes, 0, newltypes, 0, 
			     ltypes.length);
	    System.arraycopy(stypes, 0, newstypes, 0, 
			     stypes.length);
	    found = scanBlocks(method, pcs, newltypes, newstypes,
			       tgtpc, new OSR_TypeStack(S));
	    if (found) {
	      // copy back the ltypes and stypes
	      System.arraycopy(newltypes, 0, ltypes, 0, 
			       ltypes.length);
	      System.arraycopy(newstypes, 0, stypes, 0, 
			       stypes.length);
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
      case JBC_getstatic:
	{
	  int cpoolidx = UWORD(bc, pc+1);
	  VM_Field field = declaringClass.getFieldRef(cpoolidx);
	  VM_Type ftype = field.getType();
	  byte tcode = ftype.getDescriptor().parseForTypeCode();
	  if ( (tcode == LongTypeCode) || (tcode == DoubleTypeCode) )
	    S.push(VoidTypeCode);
	  S.push(tcode);
	}
	pc += 3;
	break;
      case JBC_putstatic:
	{
	  int cpoolidx = UWORD(bc, pc+1);
	  VM_Field field = declaringClass.getFieldRef(cpoolidx);
	  VM_Type ftype = field.getType();
	  byte tcode = ftype.getDescriptor().parseForTypeCode();
	  if ( (tcode == LongTypeCode) || (tcode == DoubleTypeCode) )
	    S.pop(2);
	  else
	    S.pop();
	}
	pc += 3;
	break;
      case JBC_putfield:
	{
	  int cpoolidx = UWORD(bc, pc+1);
	  VM_Field field = declaringClass.getFieldRef(cpoolidx);
	  VM_Type ftype = field.getType();
	  byte tcode = ftype.getDescriptor().parseForTypeCode();
	  if ( (tcode == LongTypeCode) || (tcode == DoubleTypeCode) )
	    S.pop(2);
	  else
	    S.pop();
	}
	S.pop();
	pc += 3;
	break;
      case JBC_invokevirtual:
      case JBC_invokespecial:
      case JBC_invokestatic:
      case JBC_invokeinterface:
	{ 
	  int cpoolidx = UWORD(bc, pc+1);
	  VM_Method callee = declaringClass.getMethodRef(cpoolidx);
	  
	  int psize = callee.getParameterWords();
		    
	  S.pop(psize);

	  if (bcode != JBC_invokestatic) {
	    S.pop();     // pop the object reference
	  }

	  VM_Type rtype = callee.getReturnType();
	  byte tcode = rtype.getDescriptor().parseForTypeCode();

	  if (tcode == VoidTypeCode) {
	    // nothing to do with void return type
	  } else {
	    if ( (tcode == LongTypeCode) 
		 || (tcode == DoubleTypeCode) ) {
	      S.push(VoidTypeCode);
	    }
	    S.push(tcode);
	  }
	  
	  if (bcode == JBC_invokeinterface) {
	    pc += 5;
	  } else {
	    pc += 3;
	  }
	}
	break;	
      case JBC_xxxunusedxxx:
	pc++;
	break;
	
      case JBC_new:
	S.push(ClassTypeCode);
	pc += 3;
	break;
      case JBC_newarray:
	S.pop();
	S.push(ArrayTypeCode);
	pc += 2;
	break;
      case JBC_anewarray:
	S.pop();
	S.push(ArrayTypeCode);
	pc += 3;
	break;
      case JBC_arraylength:
	S.pop();
	S.push(IntTypeCode);
	pc++;
	break;
      case JBC_athrow:
	S.clear();
	S.push(ClassTypeCode);
	return false;
      case JBC_checkcast:
	pc += 3;
	break;
      case JBC_instanceof:
	S.pop();
	S.push(IntTypeCode);
	pc += 3;
	break;
      case JBC_monitorenter:
      case JBC_monitorexit:
	S.pop();
	pc++;
	break;
      case JBC_wide:
	wide = true;
	pc++;
	break;
      case JBC_multianewarray:
	{
	  int dims = BYTE(bc, pc+3);
	  S.pop(dims);
	  S.push(ArrayTypeCode);
	}
	pc += 4;
	break;
      case JBC_goto_w:
	pc += DWORD(bc, pc+1);
	break;
      case JBC_impdep1: {
	pc++;
	int pseudo_opcode = BYTE(bc, pc);
	pc++;
	switch (pseudo_opcode) {
	case PSEUDO_LoadIntConst:
	  S.push(IntTypeCode);
	  pc += 4;
	  break;
	case PSEUDO_LoadLongConst:
	  S.push(VoidTypeCode);
	  S.push(LongTypeCode);
	  pc += 8;
	  break;
	case PSEUDO_LoadFloatConst:
	  S.push(FloatTypeCode);
	  pc += 4;
	  break;
	case PSEUDO_LoadDoubleConst:
	  S.push(VoidTypeCode);
	  S.push(DoubleTypeCode);
	  pc += 8;
	  break;
	case PSEUDO_LoadAddrConst:
	  S.push(AddressTypeCode);
	  
	  // remember the address for ret.
	  addr = DWORD(bc, pc);
		
	  pc += 4;
	  break;
	case PSEUDO_InvokeStatic: {

	  int mid = DWORD(bc, pc);
	  VM_Method callee = VM_MethodDictionary.getValue(mid);
	  
	  int psize = callee.getParameterWords();
	  
	  S.pop(psize);

	  VM_Type rtype = callee.getReturnType();
	  byte tcode = rtype.getDescriptor().parseForTypeCode();
	  
	  if (tcode == VoidTypeCode) {
	    // nothing to do with void return type
	  } else {
	    if ( (tcode == LongTypeCode) 
		 || (tcode == DoubleTypeCode) ) {
	      S.push(VoidTypeCode);
	    }
	    S.push(tcode);
	  }
	  pc += 4;
	  break;
	}
	case PSEUDO_CheckCast:
	  pc += 4;
	  break;
	case PSEUDO_InvokeCompiledMethod:
	  pc += 8;
	  break;
	case PSEUDO_ParamInitEnd:
	  break;
	default:
	  if (VM.VerifyAssertions) {
	    VM.sysWrite(" Error, no such pseudo code : "
			+pseudo_opcode +"\n");
	    VM._assert(VM.NOT_REACHED);
	  }
	  return false;
	}
	break;
      }
      default:
	VM.sysWrite("Unknown bytecode : "+bcode+"\n");
	return false;
      }
      
    } while (pc < endpc);
    
    /* did not found the PC. */ 
    return false; 
  }
}
    
class OSR_TypeStack {
	private byte[] stack;
	private int top;
	private byte defv;
		
  public OSR_TypeStack (int depth, byte defv) {
    byte[] stk = new byte[depth];
    for (int i=0; i<depth; i++) {
      stk[i] = defv;
    }
	    
    this.stack = stk;
    this.top = 0;
    this.defv = defv;
  }

  public OSR_TypeStack (OSR_TypeStack other) {
    
    int ssize = other.stack.length;
    this.stack = new byte[ssize];
    System.arraycopy(other.stack, 0, this.stack, 0, ssize);
    this.top = other.top;
    this.defv = other.defv;
  }

  public void push (byte v) {
    if (top == stack.length) {
      VM.sysWrite("OSR_TypeStack.push(B) : overflow!\n");
    }
    stack[top++] = v;
  }

  public byte pop() {
    if (top <= 0) {
      VM.sysWrite("OSR_TypeStack.pop() : underflow!\n");
    }
    top--;
    byte v = stack[top];
    stack[top] = defv;

    return v;
  }
	
  public void pop(int n) {
    int newtop = top - n;
    
    if (newtop < 0) {
      VM.sysWrite("OSR_TypeStack.pop(I) : underflow!\n");
    }

    for (int i=top-1; i>=newtop; i--) {
      stack[i] = defv;
    }

    top = newtop;
  }

  public byte peek () {
    return stack[top-1];
  }

  public byte[] snapshot () {
    return stack;
  }
	
  public void clear () {
    top = 0;
    for (int i=0, n=stack.length; i<n; i++) {
      stack[i] = defv;
    }
  }

  public int depth() {
    return top;
  }
}

