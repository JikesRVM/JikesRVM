/**
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import java.util.*;
/*
 * OSR_BytecodeTraverser traverses bytecode once (DFS), collecting stack heights information
 * 
 * @author Feng Qian    v0.1   29/11/02
 */

public class OSR_BytecodeTraverser
  implements VM_ClassLoaderConstants, VM_BytecodeConstants, OSR_Constants {
  
  private static byte[] visitedpc;

  /* do a DFS */
  public static void computeStackHeights(VM_Method method, 
					 int[] stackHeights,
					 boolean adjustExptable ) {

    /* set marks for each byte code. */
    byte[] bc = method.getBytecodeArray();	
    visitedpc = new byte[bc.length];

    /* scan start from method entry */
    {
      int startpc = 0;
      stackHeights[startpc] = method.getLocalWords()-1;
      scanBlocks (method, startpc, stackHeights);
    }
    /* scan for exception handler. */
    {
      VM_ExceptionHandlerMap ehmap = method.getExceptionHandlerMap();
      if (ehmap != null) {
	int[] handlerPCs = ehmap.getHandlerPC();
	
	for (int i=0, n=handlerPCs.length; i<n; i++) {
	  int startpc = handlerPCs[i];

	  /* for baseline compilation, the OSR_SpecialCompiler 
	   * didnot adjust exception table, we has to adjust it 
	   * here.
	   */
	  if (adjustExptable && method.isForSpecialization()) {
	    startpc += method.getOsrPrologueLength();
	  }

	  // local words plus an exception object, donot -1 anymore
	  stackHeights[startpc] = method.getLocalWords(); 
	  scanBlocks(method, startpc, stackHeights);
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
  
  /* recursively visits basic blocks */
  private static void scanBlocks (VM_Method method,
				  int startpc,
			          int[] stackHeights) {

    VM_Class declaringClass = method.getDeclaringClass();
    byte[] bc = method.getBytecodeArray();

    boolean wide = false;
    int pc = startpc;
    int endpc = bc.length;
	
    // initialize current stack top
    int curStkTop = stackHeights[startpc];

    do {
      if (visitedpc[pc] == 1) {
	return;
      } else {
	visitedpc[pc] = 1;
      }

      stackHeights[pc] = curStkTop;

      /* let's continue */
      int bcode = BYTE(bc, pc);
	 
	  if (VM.TraceOnStackReplacement) {
		if (bcode < JBC_name.length) {
		  VM.sysWriteln(pc + " " + curStkTop+" "+JBC_name[bcode]);
		} else {
		  VM.sysWriteln(pc + " " + curStkTop+" customized code "+bcode);
		}
	  }
	  
	  switch (bcode) {
      case JBC_nop:
	pc++;
	break;
      case JBC_aconst_null:
	curStkTop++; // S.push(ClassTypeCode);
	pc++;
	break;
      case JBC_iconst_m1:
      case JBC_iconst_0:
      case JBC_iconst_1:
      case JBC_iconst_2:
      case JBC_iconst_3:
      case JBC_iconst_4:
      case JBC_iconst_5:
	curStkTop++; // S.push(IntTypeCode);
	pc++;
	break;
      case JBC_lconst_0:
      case JBC_lconst_1:
	/* we should do the save order as opt compiler */
	curStkTop++; // S.push(VoidTypeCode);
	curStkTop++; // S.push(LongTypeCode);
	pc++;
	break;
      case JBC_fconst_0:
      case JBC_fconst_1:
      case JBC_fconst_2:
	curStkTop++; // S.push(FloatTypeCode);
	pc++;
	break;
      case JBC_dconst_0:
      case JBC_dconst_1:
	curStkTop++; // S.push(VoidTypeCode);
	curStkTop++; // S.push(DoubleTypeCode);
	pc++;
	break;
      case JBC_bipush:
	curStkTop++; // S.push(IntTypeCode);
	pc += 2;
	break;
      case JBC_sipush:
	curStkTop++; // S.push(IntTypeCode);
	pc += 3;
	break;
      case JBC_ldc:
      case JBC_ldc_w:
	{
	  curStkTop++;  
	  /*
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
	  */
 
	  pc += (bcode == JBC_ldc) ? 2 : 3;
	  break;
	}
      case JBC_ldc2_w:
	{
	  curStkTop++; // 
	  curStkTop++; // 
	  /*
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
	  */
	  pc += 3;
	  break;
	}
      case JBC_iload:
	if (wide) {
	  wide = false;
	  curStkTop++; // S.push (IntTypeCode);
	  pc += 3;
	} else {
	  curStkTop++; // S.push (IntTypeCode);
	  pc += 2;
	}
	break;		    
      case JBC_lload:
	curStkTop++; // S.push(VoidTypeCode);
	curStkTop++; // S.push(LongTypeCode);
	if (wide) {
	  wide = false;
	  pc += 3;
	} else {
	  pc += 2;
	}
	break;
      case JBC_fload:
	if (wide) {
	  wide = false;
	  curStkTop++; // S.push (FloatTypeCode);
	  pc += 3;
	} else {
	  curStkTop++; // S.push (FloatTypeCode);
	  pc += 2;
	}
	break;
      case JBC_dload:
	curStkTop++; // S.push(VoidTypeCode);
	curStkTop++; // S.push(DoubleTypeCode);
	if (wide) {
	  wide = false;
	  pc += 3;
	} else {
	  pc += 2;
	}
	break;
      case JBC_aload:
	if (wide) {
	  wide = false;
	  pc += 3;
	} else {
	  pc += 2;
	}
	curStkTop++;
	break;
      case JBC_iload_0:
      case JBC_iload_1:
      case JBC_iload_2:
      case JBC_iload_3:
	//	tmpl = bcode - JBC_iload_0;
	// ltypes[tmpl] = IntTypeCode;
	curStkTop++; // S.push (IntTypeCode);
	// S.push (ltypes[tmpl]);
	pc++;
	break;
      case JBC_lload_0:
      case JBC_lload_1:
      case JBC_lload_2:
      case JBC_lload_3:
	//		tmpl = bcode - JBC_lload_0;
		//		ltypes[tmpl] = LongTypeCode;
		//		ltypes[tmpl+1] = VoidTypeCode;
	curStkTop++; // S.push(VoidTypeCode);
	curStkTop++; // S.push(LongTypeCode);
	pc++;
	break;
      case JBC_fload_0:
      case JBC_fload_1:
      case JBC_fload_2:
      case JBC_fload_3:
	// tmpl = bcode - JBC_fload_0;
	//		ltypes[tmpl] = FloatTypeCode;
	curStkTop++; // S.push (FloatTypeCode);
	// S.push (ltypes[tmpl]);
	pc++;
	break;
      case JBC_dload_0:
      case JBC_dload_1:
      case JBC_dload_2:
      case JBC_dload_3:
	// tmpl = bcode - JBC_dload_0;
	//		ltypes[tmpl] = DoubleTypeCode;
	//		ltypes[tmpl+1] = VoidTypeCode;
	curStkTop++; // S.push(VoidTypeCode);
	curStkTop++; // S.push(DoubleTypeCode);
	pc++;
	break;
      case JBC_aload_0:
      case JBC_aload_1:
      case JBC_aload_2:
      case JBC_aload_3:
	// tmpl = bcode - JBC_aload_0;
	//		ltypes[tmpl] = ClassTypeCode;
	curStkTop++; // S.push (ClassTypeCode);
	// S.push (ltypes[tmpl]);
	pc++;
	break;
      case JBC_iaload:
      case JBC_baload:
      case JBC_caload:
      case JBC_saload:
	curStkTop--; // S.pop ();
	/*
	curStkTop--; // S.pop ();
	curStkTop++; // S.push (IntTypeCode);
	*/
	pc++;
	break;
      case JBC_laload:
	/*
	curStkTop--; // S.pop();
	curStkTop--; // S.pop();
	curStkTop++; // S.push(VoidTypeCode);
	curStkTop++; // S.push(LongTypeCode);
	*/
	pc++;
	break;
      case JBC_faload:
	curStkTop--; // S.pop();
	/*
	curStkTop--; // S.pop();
	curStkTop++; // S.push (FloatTypeCode);
	*/
	pc++;
	break;
      case JBC_daload:
	/*
	curStkTop--; // S.pop();
	curStkTop--; // S.pop();
	curStkTop++; // S.push(VoidTypeCode);
	curStkTop++; // S.push(DoubleTypeCode);
	*/
	pc++;
	break;
      case JBC_aaload:
	curStkTop--; // S.pop();
	/*
	curStkTop--; // S.pop();
	curStkTop++; // S.push(ClassTypeCode);
	*/
	pc++;
	break;
      case JBC_istore:
	if (wide) {
	  wide = false;
	  curStkTop--; // S.pop();
	  // ltypes[UWORD(bc, pc+1)] = IntTypeCode;
	  pc += 3;
	} else {
	  curStkTop--; // S.pop();
	  // ltypes[BYTE(bc, pc+1)] = IntTypeCode;
	  pc += 2;
	}
	break;
      case JBC_istore_0:
      case JBC_istore_1:
      case JBC_istore_2:
      case JBC_istore_3:
	// tmpl = bcode - JBC_istore_0;
	curStkTop--; // S.pop();
	// ltypes[tmpl] = IntTypeCode;
	pc++;
	break;
      case JBC_lstore:
	curStkTop--; // S.pop();
	curStkTop--; // S.pop();
	if (wide) {
	  wide = false;
	  //	  ltypes[UWORD(bc, pc+1)] = LongTypeCode;
	  //	  ltypes[UWORD(bc, pc+1)+1] = VoidTypeCode;
	  pc += 3;
	} else {
	  //	  ltypes[BYTE(bc, pc+1)] = LongTypeCode;
	  //	  ltypes[BYTE(bc, pc+1)+1] = VoidTypeCode;
	  pc += 2;
	}
	break;
      case JBC_lstore_0:
      case JBC_lstore_1:
      case JBC_lstore_2:
      case JBC_lstore_3:
	// tmpl = bcode - JBC_lstore_0;
	curStkTop--; // S.pop();
	curStkTop--; // S.pop();
	//	ltypes[tmpl] = LongTypeCode;
	//	ltypes[tmpl+1] = VoidTypeCode;
	pc++;
	break;
      case JBC_fstore:
	curStkTop--; // S.pop();
	if (wide) {
	  wide = false;
	  //	  ltypes[UWORD(bc, pc+1)] = FloatTypeCode;
	  pc += 3;
	} else {
	  //	  ltypes[BYTE(bc, pc+1)] = FloatTypeCode;
	  pc += 2;
	}
	break;	
      case JBC_fstore_0:
      case JBC_fstore_1:
      case JBC_fstore_2:
      case JBC_fstore_3:
	//	tmpl = bcode - JBC_fstore_0;
	curStkTop--; // S.pop();
	//	ltypes[tmpl] = FloatTypeCode;
	pc++;
	break;
      case JBC_dstore:
	curStkTop--; // S.pop();
	curStkTop--; // S.pop();
	if (wide) {
	  wide = false;
	  //	  ltypes[UWORD(bc, pc+1)] = DoubleTypeCode;
	  //	  ltypes[UWORD(bc, pc+1)+1] = VoidTypeCode;
	  pc += 3;
	} else {
	  //	  ltypes[BYTE(bc, pc+1)] = DoubleTypeCode;
	  //	  ltypes[BYTE(bc, pc+1)+1] = VoidTypeCode;
	  pc += 2;
	}
	break;
      case JBC_dstore_0:
      case JBC_dstore_1:
      case JBC_dstore_2:
      case JBC_dstore_3:
	//	tmpl = bcode - JBC_dstore_0;
	curStkTop--; // S.pop();
	curStkTop--; // S.pop();
	//	ltypes[tmpl] = DoubleTypeCode;
	//	ltypes[tmpl+1] = VoidTypeCode;
	pc++;
	break;
      case JBC_astore:
	// caution: astore may save return address type
	//	tmpl = wide ? UWORD(bc, pc+1) : BYTE(bc, pc+1);
	
	if (wide) {
	  wide = false;
	  pc += 3;
	} else {
	  pc += 2;
	}
	curStkTop--; // 	ltypes[tmpl] = S.pop();
		
	// for ret address.
	/*
	if (ltypes[tmpl] == AddressTypeCode) {
	  retaddr[tmpl] = addr;
	  addr = -1;
	}
	*/
	break;
      case JBC_astore_0:
      case JBC_astore_1:
      case JBC_astore_2:
      case JBC_astore_3:
	//	tmpl = bcode - JBC_astore_0;
	// caution: astore may save return address type
	curStkTop--; // ltypes[tmpl] = S.pop();
	      
	// for ret address.
	/*
	if (ltypes[tmpl] == AddressTypeCode) {
	  retaddr[tmpl] = addr;
	  addr = -1;
	}
	*/
	pc++;
	break;
      case JBC_iastore:
      case JBC_bastore:
      case JBC_castore:
      case JBC_sastore:
	curStkTop -= 3; // S.pop(3);
	pc++;
	break;
      case JBC_lastore:
	curStkTop -= 4; // S.pop(4);
	pc++;
	break;
      case JBC_fastore:
	curStkTop -= 3; // S.pop(3);
	pc++;
	break;
      case JBC_dastore:
	curStkTop -=4; // S.pop(4);
	pc++;
	break;
      case JBC_aastore:
	curStkTop -=3; // S.pop(3);
	pc++;
	break;
      case JBC_pop:
	curStkTop--; // S.pop();
	pc++;
	break;
      case JBC_pop2:
	curStkTop -= 2; // S.pop(2);
	pc++;
	break;
      case JBC_dup:
	{
	  //	  byte v1 = S.peek();
	  curStkTop++; // 	  S.push(v1);
	}
	pc++;
	break;
      case JBC_dup_x1:
	{
	  /*
	  byte v1 = S.peek();
	  S.pop();
	  byte v2 = S.peek();
	  S.pop();
	  S.push(v1);
	  S.push(v2);
	  S.push(v1);
	  */
	  curStkTop++;
	}
	pc++;
	break;
      case JBC_dup_x2:
	{
	  /*
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
	  */
	  curStkTop++;
	}
	pc++;
	break;
      case JBC_dup2:
	{
	  /*
	  byte v1 = S.peek();
	  S.pop();
	  byte v2 = S.peek();
	  S.push(v1);
	  S.push(v2);
	  S.push(v1);
	  */
	  curStkTop += 2;
	}
	pc++;
	break;
      case JBC_dup2_x1:
	{
	  /*
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
	  */
	  curStkTop += 2;
	}
	pc++;
	break;
      case JBC_dup2_x2:
	{
	  /*
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
	  */
	  curStkTop += 2;
	}
	pc++;
	break;
      case JBC_swap:
	{
	  /*
	  byte v1 = S.peek();
	  S.pop();
	  byte v2 = S.peek();
	  S.pop();
	  S.push(v1);
	  S.push(v2);
	  */
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
	curStkTop--; //   S.pop();
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
	curStkTop -= 2; //  S.pop(2);
	pc++;
	break;
      case JBC_lshl:
      case JBC_lshr:
      case JBC_lushr:
	curStkTop--; //  S.pop();
	pc++;
	break;
      case JBC_fadd:
      case JBC_fsub:
      case JBC_fmul:
      case JBC_fdiv:
      case JBC_frem:
	curStkTop--; //  S.pop();
	pc++;
	break;
      case JBC_dadd:
      case JBC_dsub:
      case JBC_dmul:
      case JBC_ddiv:
      case JBC_drem:
	curStkTop -= 2; //  S.pop(2);
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
	  //	  ltypes[UWORD(bc, pc+1)] = IntTypeCode;
	  pc += 5;
	} else {
	  //	  ltypes[BYTE(bc, pc+1)] = IntTypeCode;
	  pc += 3;
	}
	break;
      case JBC_i2l:
	/*
	S.pop();
	S.push(VoidTypeCode);
	S.push(LongTypeCode);
	*/
	curStkTop++;
	pc++;
	break;
      case JBC_i2f:
	/*
	S.pop();
	S.push(FloatTypeCode);
	*/
	pc++;
	break;
      case JBC_i2d:
	/*
	S.pop();
	S.push(VoidTypeCode);
	S.push(DoubleTypeCode);
	*/
	curStkTop ++;
	pc++;
	break;
      case JBC_l2i:
	/*
	S.pop(2);
	S.push(IntTypeCode);
	*/
	curStkTop--; 
	pc++;
	break;
      case JBC_l2f:
	/*
	S.pop(2);
	S.push(FloatTypeCode);
	*/
	curStkTop--; 
	pc++;
	break;
      case JBC_l2d:
	/*
	S.pop(2);
	S.push(VoidTypeCode);
	S.push(DoubleTypeCode);
	*/
	pc++;
	break;
      case JBC_f2i:
	/*
	S.pop();
	S.push(IntTypeCode);
	*/
	pc++;
	break;
      case JBC_f2l:
	/*
	S.pop();
	S.push(VoidTypeCode);
	S.push(LongTypeCode);
	*/
 	curStkTop++; 
	pc++;
	break;
      case JBC_f2d:
	/*
	S.pop();
	S.push(VoidTypeCode);
	S.push(DoubleTypeCode);
	*/
 	curStkTop++; 
	pc++;
	break;
      case JBC_d2i:
	/*
	S.pop(2);
	S.push(IntTypeCode);
	*/
 	curStkTop--; 	
	pc++;
	break;
      case JBC_d2l:
	/*
	S.pop(2);
	S.push(VoidTypeCode);
	S.push(LongTypeCode);
	*/
	pc++;
	break;
      case JBC_d2f:
	/*
	S.pop(2);
	S.push(FloatTypeCode);
	*/
 	curStkTop--; 
	pc++;
	break;
      case JBC_int2byte:
      case JBC_int2char:
      case JBC_int2short:
	pc++;
	break;
      case JBC_lcmp:
	/*
	S.pop(4);
	S.push(IntTypeCode);
	*/
 	curStkTop -= 3; 
	pc++;
	break;
      case JBC_fcmpl:
      case JBC_fcmpg:
	/*
	S.pop(2);
	S.push(IntTypeCode);
	*/
	curStkTop--;
	pc++;
	break;
      case JBC_dcmpl:
      case JBC_dcmpg:
	/*
	S.pop(4);
	S.push(IntTypeCode);
	*/
	curStkTop -= 3;
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
	/*
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
	*/

	curStkTop --;
	{
	  int nextpc = pc + 3;
	  // assign value early
	  stackHeights[nextpc] = curStkTop;
	  scanBlocks(method, nextpc, stackHeights);
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
	/*
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
	*/
	curStkTop -= 2;
	{
	  int nextpc = pc + 3;
	  stackHeights[nextpc] = curStkTop;
	  scanBlocks(method, nextpc, stackHeights);
	}
	pc += WORD(bc, pc+1);
	break;
	
      case JBC_goto:
	pc += WORD(bc, pc+1);
	break;
      case JBC_jsr:
      case JBC_jsr_w:
	/*
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
	*/
	{
	  int nextpc = pc + ((bcode == JBC_jsr)?3:5);
	  stackHeights[nextpc] = curStkTop + 1;  // put an address object on the stack
	  scanBlocks(method, nextpc, stackHeights);
	}
	pc += ((bcode == JBC_jsr)?WORD(bc, pc+1):DWORD(bc, pc+1));
	break;

      case JBC_ret:
	/*
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

	// the ret address may be saved by a PSEUDO_LoadAddrConstant
	if (retaddr[tmpl] != -1) {
	  pc = retaddr[tmpl];
	  retaddr[tmpl] = -1;
	  break;
	} else {
	  // now we hit ret, return out
	  return false;
	}
	*/
	if (wide) {
	  wide = false;
	  pc += 3;
	} else {
	  pc += 2;
	}
	return;
      case JBC_tableswitch:
	curStkTop--; // S.pop();
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
	  //	  byte[] newltypes = new byte[ltypes.length];
	  //	  byte[] newstypes = new byte[stypes.length];
	  
	  for (int i=0, n=high-low+1; i<n; i++) {
	    int tgtpc = tablepc + DWORD(bc, pc);
	    pc += 4;
	    stackHeights[tgtpc] = curStkTop;
	    scanBlocks(method, tgtpc, stackHeights);
	  }

	  // default
	  {
	    int tgtpc = tablepc + def;
	    stackHeights[tgtpc] = curStkTop;	    
	    scanBlocks(method, tgtpc, stackHeights);
	  }
	}
	return;
      case JBC_lookupswitch:
	curStkTop--; // S.pop(); 
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
	  
	  for (int i=0; i<npairs; i++) {
	    pc += 4; // skip match
	    int offset = DWORD(bc, pc);
	    pc += 4;
	    int tgtpc = lookuppc + offset;

	    stackHeights[tgtpc] = curStkTop;
	    scanBlocks(method, tgtpc, stackHeights);
	  }

	  // default
	  {
	    int tgtpc = lookuppc + def;
	    
	    stackHeights[tgtpc] = curStkTop;
	    scanBlocks(method, tgtpc, stackHeights);
	  }			
	}
	return ;
      case JBC_ireturn:
	curStkTop --; // S.pop();
	return;
      case JBC_lreturn:
	curStkTop -= 2; // S.pop(2);
	return;
      case JBC_freturn:
	curStkTop --; //  S.pop();
	return;
      case JBC_dreturn:
	curStkTop -= 2; //  S.pop(2);
	return;
      case JBC_areturn:
	curStkTop --; // S.pop();
	return;
      case JBC_return:
	return;
      case JBC_getfield:
	curStkTop--; //  S.pop();
      case JBC_getstatic:
	{
	  int cpoolidx = UWORD(bc, pc+1);
	  VM_Field field = declaringClass.getFieldRef(cpoolidx);
	  VM_Type ftype = field.getType();
	  byte tcode = ftype.getDescriptor().parseForTypeCode();
	  if ( (tcode == LongTypeCode) || (tcode == DoubleTypeCode) )
	    curStkTop++; // S.push(VoidTypeCode);
	  curStkTop++; // S.push(tcode);
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
	    curStkTop -= 2; // S.pop(2);
	  else
	    curStkTop--; // S.pop();
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
	    curStkTop -= 2; // S.pop(2);
	  else
	    curStkTop--; // 	S.pop();
	}
	curStkTop--; // 	  S.pop();
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
	  
	  curStkTop -= psize; // 	    S.pop(psize);
	  
	  if (bcode != JBC_invokestatic) {
	    curStkTop--; // S.pop();     // pop the object reference
	  }
	  
	  VM_Type rtype = callee.getReturnType();
	  byte tcode = rtype.getDescriptor().parseForTypeCode();
	  
	  if (tcode == VoidTypeCode) {
	    // nothing to do with void return type
	  } else {
	    if ( (tcode == LongTypeCode) 
		 || (tcode == DoubleTypeCode) ) {
	      curStkTop++; // S.push(VoidTypeCode);
	    }
	    curStkTop++; // S.push(tcode);
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
	curStkTop++; // S.push(ClassTypeCode);
	pc += 3;
	break;
      case JBC_newarray:
	/*
	  S.pop();
	  S.push(ArrayTypeCode);
	*/
	pc += 2;
	break;
      case JBC_anewarray:
	/*
	  S.pop();
	  S.push(ArrayTypeCode);
	*/
	pc += 3;
	break;
      case JBC_arraylength:
	/*
	  S.pop();
	  S.push(IntTypeCode);
	*/
	pc++;
	break;
      case JBC_athrow:
	// only keep local variables
	curStkTop = method.getLocalWords(); //	  S.clear();
	curStkTop++; // 	S.push(ClassTypeCode);
	return;
      case JBC_checkcast:
	pc += 3;
	break;
      case JBC_instanceof:
	/*
	  S.pop();
	  S.push(IntTypeCode);
	*/
	pc += 3;
	break;
      case JBC_monitorenter:
      case JBC_monitorexit:
	curStkTop--; // S.pop();
	pc++;
	break;
      case JBC_wide:
	wide = true;
	pc++;
	break;
      case JBC_multianewarray:
	{
	  int dims = BYTE(bc, pc+3);
	  curStkTop -= dims; // S.pop(dims);
	  curStkTop++; // S.push(ArrayTypeCode);
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
	  curStkTop++;   // S.push(IntTypeCode);
	  pc += 4;
	  break;
	case PSEUDO_LoadLongConst:
	  curStkTop++;   //S.push(VoidTypeCode);
	  curStkTop++;   //	    S.push(LongTypeCode);
	  pc += 8;
	  break;
	case PSEUDO_LoadFloatConst:
	  curStkTop++;   //	    S.push(FloatTypeCode);
	  pc += 4;
	  break;
	case PSEUDO_LoadDoubleConst:
	  curStkTop++;   //S.push(VoidTypeCode);
	  curStkTop++;   //S.push(DoubleTypeCode);
	  pc += 8;
	  break;
	case PSEUDO_LoadAddrConst:
	  curStkTop++;   //S.push(AddressTypeCode);
	  // remember the address for ret.
	  //	    addr = DWORD(bc, pc);
	  pc += 4;
	  break;
	case PSEUDO_InvokeStatic: {
	  
	  int mid = DWORD(bc, pc);
	  VM_Method callee = VM_MethodDictionary.getValue(mid);
	  
	  int psize = callee.getParameterWords();

	  curStkTop -= psize; // S.pop(psize);
	  
	  VM_Type rtype = callee.getReturnType();
	  byte tcode = rtype.getDescriptor().parseForTypeCode();
	  
	  if (tcode == VoidTypeCode) {
	    // nothing to do with void return type
	  } else {
	    if ( (tcode == LongTypeCode) 
		 || (tcode == DoubleTypeCode) ) {
	      curStkTop ++; // S.push(VoidTypeCode);
	    }
	    curStkTop ++; // S.push(tcode);
	  }
	  pc += 4;
	  break;
	}
	case PSEUDO_CheckCast:
	  pc += 4;
	  break;
	case PSEUDO_InvokeCompiledMethod: {
	  // !!!! adjust stack height here
	  int cmid = DWORD(bc, pc);
	  VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
	  VM_Method callee = cm.getMethod();
	  
	  int psize = callee.getParameterWords();

	  curStkTop -= psize; // S.pop(psize);

	  if (!callee.isStatic()) {
	    curStkTop --;  // pop the receiver
	  }
	  
	  VM_Type rtype = callee.getReturnType();
	  byte tcode = rtype.getDescriptor().parseForTypeCode();
	  
	  if (tcode == VoidTypeCode) {
	    // nothing to do with void return type
	  } else {
	    if ( (tcode == LongTypeCode) 
		 || (tcode == DoubleTypeCode) ) {
	      curStkTop ++; // S.push(VoidTypeCode);
	    }
	    curStkTop ++; // S.push(tcode);
	  }
	  pc += 8;
	  break;
	}
    case PSEUDO_ParamInitEnd:
	 break;
	default:
	  if (VM.VerifyAssertions) {
	    VM.sysWrite(" Error, no such pseudo code : "
			+pseudo_opcode +"\n");
	    VM._assert(VM.NOT_REACHED);
	  }
	  return;
	}
	break;
      }
      default:
	if (VM.VerifyAssertions) {
	  VM.sysWrite("Unknown bytecode : "+bcode+" of "+pc+"@"+method.toString()+"\n");
	  VM._assert(VM.NOT_REACHED);
	}
	return;
      }
      
    } while (pc < endpc);
    
    return; 
  }
}
    

