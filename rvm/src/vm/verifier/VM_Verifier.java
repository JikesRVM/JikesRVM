/*
 * (C) Copyright IBM Corp. 2002, 2003
 */
//$Id$
package com.ibm.JikesRVM;

// import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.classloader.VM_Array;
import com.ibm.JikesRVM.classloader.VM_BytecodeConstants;
import com.ibm.JikesRVM.classloader.VM_BytecodeStream;
import com.ibm.JikesRVM.classloader.VM_Class;
import com.ibm.JikesRVM.classloader.VM_ClassLoader;
import com.ibm.JikesRVM.classloader.VM_ExceptionHandlerMap;
import com.ibm.JikesRVM.classloader.VM_Field;
import com.ibm.JikesRVM.classloader.VM_FieldReference;
import com.ibm.JikesRVM.classloader.VM_Method;
import com.ibm.JikesRVM.classloader.VM_MethodReference;
import com.ibm.JikesRVM.classloader.VM_NormalMethod;
import com.ibm.JikesRVM.classloader.VM_Type;
import com.ibm.JikesRVM.classloader.VM_TypeReference;

import java.util.Stack;

/**
 * This class is used to verify the bytecode of a method or a class.
 * <p><b>Does Not Work</b>
 * This class does not work.  It goes into an infinite loop when trying to
 * verify a simple "Hello, World" program.  Ugh.
 * <p> 
 * Note: the verifier will try to load classes when necessary, breaking
 * lazy class loading here.
 * 
 * @author Lingli Zhang  6/20/02
 *
 * @see VM_BasicBlock
 * @see VM_BuildBB
 */
public class VM_Verifier 
  implements VM_BytecodeConstants, VM_SizeConstants 
{
  //type of local variable and stack cell
  static final private int V_NULL = 0; 
  static final private int V_INT = -1;
  static final private int V_FLOAT = -2;
  static final private int V_RETURNADDR = -3;
  static final private int V_UNDEF = -4;
  static final private int V_VOID = -5;
  static final private int V_LONG =-6;
  static final private int V_DOUBLE = -7;
  static final private int V_REF =1;


  static final private byte ONEWORD = 1;
  static final private byte DOUBLEWORD = 2;

  static final private boolean TRACE = false;

  //work list top pointer
  private int workStkTop;
  //work list
  private short[] workStk = null;

  private int currBBNum ;
  private int []currBBMap;
  private int currBBStkEmpty ;
  private int currBBStkTop ;
  private VM_PendingJSRInfo currPendingJsr;
  private String currMethodName;
  private boolean [] newObjectInfo; 

  private int   opcode;

  private boolean processNextBlock;
  private boolean inJSRSub = false;

  private VM_BasicBlock[] basicBlocks= null;
  private short[] byteToBlockMap = null;
  private int jsrCount =0;
  private int typeMaps[][] = null;
  private int[] blockStkTop = null;
  private VM_PendingJSRInfo[] bbPendingJsrs = null;

  private int currentByteCodeIndex = -1;
  VM_BytecodeStream bcodes;
  
  private VM_Class declaringClass;

  private void verificationFailure(String message) {
    verificationFailure(message, (Throwable) null);
  }

  private void verificationFailure(String message, Throwable t) {
    StringBuffer sb = new StringBuffer("Verification error: in method ")
      .append(declaringClass).append(".").append(currMethodName);

    if (currentByteCodeIndex != -1) {
      sb.append(" at index ").append(currentByteCodeIndex);
    }
    sb.append(": instruction").append(JBC_name[ opcode ]);
    
    sb.append(": ").append(message);
    VerifyError ve = new VerifyError(sb.toString());
    if (t != null)
      ve.initCause(t);          // don't chain it, since initCause() returns a
                                // Throwable (ugh).
    throw ve;
  }

  private VM_Type resolve(VM_TypeReference tr) {
    try {
      return tr.resolve();
    } catch (NoClassDefFoundError e) {
      verificationFailure("Unable to resolve a VM_TypeReference named " + tr, e);
      return null;              // this will never be executed.
    }
  }

  private VM_Field resolve(VM_FieldReference fr) {
    return fr.resolve();
  }

  private VM_Method resolve(VM_MethodReference mr) {
    return mr.resolve();
  }

  /**
   * Verify the bytecode of a given class. If the class hasn't been loaded, this method
   * will try to load it. It will verify the declared methods of this class one by one.
   *
   * @param cls the class to be verify
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  public void verifyClass(VM_Class cls) 
    throws VerifyError
  {
    if (cls == null) {
      throw new IllegalArgumentException("No class to be verified");
    }
    VM_Method methods[] = cls.getDeclaredMethods();
    for (int i =0; i < methods.length; i++) {
      VM_Method method = methods[i];
      if (!method.isNative() && !method.isAbstract()) {
        verifyMethod((VM_NormalMethod) method);
      }
    }
  }

              
  void checkOffset(int offset) {
    int dest = currentByteCodeIndex + offset;
    if (dest < 0 || dest >= bcodes.length()) {
      verificationFailure("invalid branch offset (dest. is " + dest 
                          + "; must be >= 0 && < " + bcodes.length() + ")");
    }
  }

  /**
   * Verify the bytecode of a given method.
   *
   * <p> This verifier does abstract interpretation on the type stack. It uses
   * the basic dataflow analysis algorithm (worklist to reach fix points) to
   * check the validity of the bytecodes. Each basic block has one IN type map
   * and one OUT type map. The IN map is gotten by merging the maps from all
   * its predecessors.  The OUT map is calculated by simulating the execution
   * of bytecodes on types.
   *
   * <p> At any program point, if the conditions described in The Java Virtual
   * Machine Specification are broken, bytecode verification fails.
   *
   * @param method the method to be verified
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  public void verifyMethod(VM_NormalMethod method) 
    throws VerifyError
  {
    currMethodName = method.toString();

    if (TRACE)
      VM.sysWrite("Start to verify method " + currMethodName + "\n");
    bcodes = method.getBytecodes();
    /* unused: */
    // int paramCount = method.getParameterWords();

    declaringClass = method.getDeclaringClass();
    /* unused: */
    // if (!method.isStatic()) paramCount ++;

    //basic block information from VM_BuildBB()
    VM_BuildBB buildBB = new VM_BuildBB();
    buildBB.determineTheBasicBlocks(method);

    basicBlocks = buildBB.basicBlocks;
    byteToBlockMap = buildBB.byteToBlockMap;
    jsrCount = buildBB.numJsrs;

    //hold type dictionary id if typemap is reference
    typeMaps = new int[basicBlocks.length +1][];
    //stack height of each basic block entry
    blockStkTop = new int[typeMaps.length];

    //initialize the local register and stack
    currBBStkEmpty = method.getLocalWords()-1;
    currBBMap = new int[method.getOperandWords() + currBBStkEmpty +1];
    newObjectInfo = new boolean[method.getOperandWords()];
    for (int k =0; k < newObjectInfo.length; k++)
      newObjectInfo[k] = false;

    //step 1 --parameter types
    VM_TypeReference[] parameterTypes = method.getParameterTypes();
    int paramStart;
    if ( method.isStatic()) {
      paramStart = 0;
    } else {
      currBBMap[0] = declaringClass.getTypeRef().getId();
      paramStart =1;
    }

    for (int i = 0; i < parameterTypes.length; i++ , paramStart++) {
      VM_TypeReference paramType = parameterTypes[i];
      if (paramType.isIntLikeType())
        currBBMap[paramStart] = V_INT;
      else if (paramType.isLongType())
        currBBMap[paramStart] = currBBMap[paramStart+1] = V_LONG;
      else if (paramType.isFloatType())
        currBBMap[paramStart] = V_FLOAT;
      else if (paramType.isDoubleType())
        currBBMap[paramStart] = currBBMap[paramStart+1] = V_DOUBLE;
      else if (paramType.isReferenceType())
        currBBMap[paramStart] = paramType.getId();

      if (paramType.getStackWords() == DOUBLEWORD)
        ++paramStart;
    }

    //step 2  -- others local variables: set to UNDEF

    for (int k =paramStart; k <method.getLocalWords(); k++)
      currBBMap[k] = V_UNDEF;


    //step 3 -- handle the exceptions
    VM_ExceptionHandlerMap exceptions;
    int tryStartPC[];
    int tryEndPC[];
    int tryHandlerPC[];
    int tryHandlerLength;
    int reachableHandlerBBNums[];
    int reachableHandlersCount;
    boolean     handlerProcessed[];
    boolean handlersAllDone;


    exceptions       = method.getExceptionHandlerMap();
    if (exceptions != null) {
      tryStartPC       = exceptions.getStartPC();
      tryEndPC         = exceptions.getEndPC();
      tryHandlerPC     = exceptions.getHandlerPC();
      tryHandlerLength = tryHandlerPC.length;

      reachableHandlerBBNums = new int[tryStartPC.length];
      handlerProcessed       = new boolean[tryStartPC.length];
      if (jsrCount > 0) 
        bbPendingJsrs = new VM_PendingJSRInfo[typeMaps.length];

      handlersAllDone = (tryHandlerLength == 0);

      // write poison values to help distinguish different errors
      for (int ii = 0; ii < reachableHandlerBBNums.length; ii++)
        reachableHandlerBBNums[ii] = -1;

    } else {
      tryHandlerLength       = 0;
      handlersAllDone        = true;
      tryStartPC             = null; 
      tryEndPC               = null;
      tryHandlerPC           = null;
      reachableHandlerBBNums = null;
      handlerProcessed       = null;
    }
    reachableHandlersCount = 0;

    //step 4 -- start to interpret the first block
    workStk = new short [10 + tryHandlerLength];
    workStkTop = 0;
    workStk[workStkTop] = byteToBlockMap[0];

    currBBStkTop = currBBStkEmpty;
    typeMaps[byteToBlockMap[0]]=currBBMap;
    blockStkTop[byteToBlockMap[0]] = currBBStkTop;

    if (TRACE) {
       VM.sysWrite("initial currBBMap: \n");
       for (int l = 0; l < currBBMap.length; l++)
         VM.sysWrite("currBBMap["+l+"] = " + currBBMap[l] + "\n");
    }
    
    currBBMap = new int[currBBMap.length];

    //keep doing until worklist is empty
    while(workStkTop > -1) {
      currBBNum = workStk[workStkTop];
      workStkTop--;

      inJSRSub = false;
      if (typeMaps[currBBNum] == null)
        verificationFailure("found a block on work stack without starting map.\n");
      currBBStkTop = blockStkTop[currBBNum];
      for (int k = 0; k<=currBBStkTop; k++)
        currBBMap[k] = typeMaps[currBBNum][k];

      if (jsrCount>0 && basicBlocks[currBBNum].isInJSR())
        inJSRSub = true;


      int start = basicBlocks[currBBNum].getStart();
      int end = basicBlocks[currBBNum].getEnd();

      if (jsrCount > 0 && inJSRSub) {
        currPendingJsr = bbPendingJsrs[currBBNum];

        if (basicBlocks[currBBNum].isTryStart()) {
          if (currPendingJsr == null)
            currPendingJsr = bbPendingJsrs[currBBNum] = bbPendingJsrs[basicBlocks[currBBNum].pred1];
          for (int k = 0; k < tryHandlerLength; k++) {
            if (tryStartPC[k] == start && exceptions.getExceptionType(k) != null) {
              int handlerBBNum = byteToBlockMap[tryHandlerPC[k]];
              bbPendingJsrs[handlerBBNum] = currPendingJsr;
            }
          }
        }
      }
      else
        currPendingJsr = null;

      boolean inTryBlock;
      reachableHandlersCount = 0;
      if (basicBlocks[currBBNum].isTryBlock()) {
        inTryBlock = true;
        for (int i = 0; i <tryHandlerLength; i++)
          if (tryStartPC[i] <= start && tryEndPC[i] >= end && exceptions.getExceptionType(i)!=null) {
            reachableHandlerBBNums[reachableHandlersCount] = byteToBlockMap[tryHandlerPC[i]];
            reachableHandlersCount++;

            int handlerBBNum = byteToBlockMap[tryHandlerPC[i]];
            if (typeMaps[handlerBBNum] == null) {
              typeMaps[handlerBBNum] = new int[currBBMap.length];
              for (int k = 0; k<=currBBStkEmpty; k++)
                typeMaps[handlerBBNum][k] = currBBMap[k];
              typeMaps[handlerBBNum][currBBStkEmpty+1] = exceptions.getExceptionType(i).getId();
              blockStkTop[handlerBBNum] = currBBStkEmpty+1;
            }
          }
      }
      else
        inTryBlock = false;


      processNextBlock = true;
      bcodes.reset(start);
      while (bcodes.index() <= end) {
        currentByteCodeIndex = bcodes.index();
        opcode = bcodes.nextInstruction();

        if (TRACE) {
          VM.sysWrite("#" + currentByteCodeIndex + ": " + opcode + "\n");
          VM.sysWrite("currBBStkTop: "+ currBBStkTop + "\n");
          if (currBBStkTop != -1)
            VM.sysWrite("currBBMap[Top]: "+ currBBMap[currBBStkTop] + "\n");
        }

        switch(opcode) {
          case JBC_nop:
            break;
            //reference kind of load
          case JBC_aconst_null:
            load_like(V_NULL, -1, 1, false);
            break;
          case JBC_aload_0:
            load_like(V_REF, 0, 1, true);
            break;
          case JBC_aload_1:
            load_like(V_REF, 1, 1, true);
            break;
          case JBC_aload_2:
            load_like(V_REF, 2, 1, true);
            break;
          case JBC_aload_3:
            load_like(V_REF, 3, 1, true);
            break;
          case JBC_aload:{
            int index =bcodes.getLocalNumber();
            load_like(V_REF, index, 1, true);
            break;
          }
          //int kind of load
          case JBC_iconst_m1:
          case JBC_iconst_0:
          case JBC_iconst_1:
          case JBC_iconst_2:
          case JBC_iconst_3:
          case JBC_iconst_4:
          case JBC_iconst_5:
          case JBC_bipush:
          case JBC_sipush:
            load_like(V_INT, -1, 1, false);
            bcodes.skipInstruction();
            break;
          case JBC_iload_0:
            load_like(V_INT, 0, 1, true);
            break;
          case JBC_iload_1:
            load_like(V_INT, 1, 1, true);
            break;
          case JBC_iload_2:
            load_like(V_INT, 2, 1, true);
            break;
          case JBC_iload_3:
            load_like(V_INT, 3, 1, true);
            break;
          case JBC_iload:{
            int index = bcodes.getLocalNumber();
            load_like(V_INT, index, 1, true);
            break;
          }
          //float kind of load
          case JBC_fconst_0:
          case JBC_fconst_1:
          case JBC_fconst_2:
            load_like(V_FLOAT, -1, 1, false);
            break;
          case JBC_fload_0:
            load_like(V_FLOAT, 0, 1,true);
            break;
          case JBC_fload_1:
            load_like(V_FLOAT, 1, 1,true);
            break;
          case JBC_fload_2:
            load_like(V_FLOAT, 2, 1, true);
            break;
          case JBC_fload_3:
            load_like(V_FLOAT, 3,  1, true);
            break;
          case JBC_fload:{
            int index = bcodes.getLocalNumber();
            load_like(V_FLOAT, index, 1, true);
            break;
          }
          //long kind of load
          case JBC_lconst_0:
          case JBC_lconst_1:
            load_like(V_LONG, -1, 2, false);
            break;
          case JBC_lload_0:
            load_like(V_LONG, 0, 2, true);
            break;
          case JBC_lload_1:
            load_like(V_LONG, 1, 2, true);
            break;
          case JBC_lload_2:
            load_like(V_LONG, 2, 2, true);
            break;
          case JBC_lload_3:
            load_like(V_LONG, 3, 2, true);
            break;
          case JBC_lload:{
            int index = bcodes.getLocalNumber();
            load_like(V_LONG, index, 2, true);
            break;
          }
          //double kind of load
          case JBC_dconst_0:
          case JBC_dconst_1:
            load_like(V_DOUBLE, -1, 2, false);
            break;
          case JBC_dload_0:
            load_like(V_DOUBLE, 0, 2, true);
            break;
          case JBC_dload_1:
            load_like(V_DOUBLE, 1, 2, true);
            break;
          case JBC_dload_2:
            load_like(V_DOUBLE, 2, 2, true);
            break;
          case JBC_dload_3:
            load_like(V_DOUBLE, 3, 2, true);
            break;
          case JBC_dload:{
            int index = bcodes.getLocalNumber();
            load_like(V_DOUBLE, index, 2, true);
            break;
          }
          //cast_like bytecode
          case JBC_int2byte:
          case JBC_int2char:
          case JBC_int2short:
            cast_like(V_INT,V_INT,1,1);
            break;
          case JBC_i2l:
            cast_like(V_INT,V_LONG, 1, 2);
            break;
          case JBC_i2f:
            cast_like(V_INT,V_FLOAT, 1, 1);
            break;
          case JBC_i2d:
            cast_like(V_INT,V_DOUBLE, 1, 2);
            break;
          case JBC_l2i:
            cast_like(V_LONG,V_INT, 2, 1);
            break;
          case JBC_l2f:
            cast_like(V_LONG,V_FLOAT, 2, 1);
            break;
          case JBC_l2d:
            cast_like(V_LONG,V_DOUBLE, 2, 2);
            break;
          case JBC_f2i:
            cast_like(V_FLOAT,V_INT, 1, 1);
            break;
          case JBC_f2l:
            cast_like(V_FLOAT,V_LONG, 1, 2);
            break;
          case JBC_f2d:
            cast_like(V_FLOAT,V_DOUBLE, 1, 2);
            break;
          case JBC_d2i:
            cast_like(V_DOUBLE,V_INT, 2, 1);
            break;
          case JBC_d2l:
            cast_like(V_DOUBLE,V_LONG, 2, 2);
            break;
          case JBC_d2f:
            cast_like(V_DOUBLE,V_FLOAT, 2, 1);
            break;

            //store like bytecodes
          case JBC_istore_0:
            store_like(V_INT, 1, 0, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_istore_1:
            store_like(V_INT, 1, 1, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_istore_2:
            store_like(V_INT, 1, 2, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_istore_3:
            store_like(V_INT, 1, 3, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_istore:{
            int index = bcodes.getLocalNumber();
            store_like(V_INT, 1, index, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          }
          case JBC_fstore_0:
            store_like(V_FLOAT, 1, 0, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_fstore_1:
            store_like(V_FLOAT, 1, 1, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_fstore_2:
            store_like(V_FLOAT, 1, 2, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_fstore_3:
            store_like(V_FLOAT, 1, 3, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_fstore:{
            int index = bcodes.getLocalNumber();
            store_like(V_FLOAT, 1, index, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          }
          case JBC_dstore_0:
            store_like(V_DOUBLE, 2, 0, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_dstore_1:
            store_like(V_DOUBLE, 2, 1, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_dstore_2:
            store_like(V_DOUBLE, 2, 2, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_dstore_3:
            store_like(V_DOUBLE, 2, 3, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_dstore:{
            int index = bcodes.getLocalNumber();
            store_like(V_DOUBLE, 2, index, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          }
          case JBC_lstore_0:
            store_like(V_LONG, 2, 0, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_lstore_1:
            store_like(V_LONG, 2, 1, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_lstore_2:
            store_like(V_LONG, 2, 2, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_lstore_3:
            store_like(V_LONG, 2, 3, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_lstore:{
            int index = bcodes.getLocalNumber();
            store_like(V_LONG, 2, index, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          }
          case JBC_astore_0:
            store_like(V_REF, 1, 0, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_astore_1:
            store_like(V_REF, 1, 1, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_astore_2:
            store_like(V_REF, 1, 2, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_astore_3:
            store_like(V_REF, 1, 3, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          case JBC_astore:{
            int index = bcodes.getLocalNumber();
            store_like(V_REF, 1, index, reachableHandlerBBNums, reachableHandlersCount); 
            break;
          }
          //stack manipulate bytecode
          case JBC_pop:
            currBBStkTop--;
            if (currBBStkTop < currBBStkEmpty) {
              verificationFailure("stack overflow ");
            }
            break;
          case JBC_pop2:
            currBBStkTop-=2;
            if (currBBStkTop < currBBStkEmpty) {
              verificationFailure("stack overflow");
            }
            break;
          case JBC_dup:{
            dup_like(1,0);
            //###if this "dup" is after "new", set new object info for it
            if (newObjectInfo[currBBStkTop-currBBStkEmpty-2 ])
              newObjectInfo[currBBStkTop-currBBStkEmpty-1] = true; 
            break;
          }
          case JBC_dup_x1:
            dup_like(1,1);
            break;
          case JBC_dup_x2:
            dup_like(1,2);
            break;
          case JBC_dup2:
            dup_like(2,0);
            break;
          case JBC_dup2_x1:
            dup_like(2,1);
            break;
          case JBC_dup2_x2:
            dup_like(2,2);
            break;

          case JBC_swap:{
            //check stack underflow
            if (currBBStkTop -1 <= currBBStkEmpty) {
              verificationFailure("stack underflow");
            }
            //check type, can't be 64-bits data
            if (currBBMap[currBBStkTop]<=V_LONG || currBBMap[currBBStkTop-1] <= V_LONG) {
              verificationFailure("stack has wrong type");
            }
            //swap the type
            int temp = currBBMap[currBBStkTop-1];
            currBBMap[currBBStkTop-1] = currBBMap[currBBStkTop];
            currBBMap[currBBStkTop] = temp;
            break;
          }
          //arithmetic bytecodes
          case JBC_iadd:
          case JBC_isub:
          case JBC_imul:
          case JBC_idiv:
          case JBC_irem:
          case JBC_ishl:
          case JBC_ishr:
          case JBC_iushr:
          case JBC_iand:
          case JBC_ior:
          case JBC_ixor:
            arith_like(V_INT, 2, 1);
            break;

          case JBC_ladd:
          case JBC_lsub:
          case JBC_lmul:
          case JBC_ldiv:
          case JBC_lrem:
          case JBC_land:
          case JBC_lor:
          case JBC_lxor:
            arith_like(V_LONG, 2, 2);
            break;

          case JBC_lshl:
          case JBC_lshr:
          case JBC_lushr:
            {
              /* Since these two bytecodes are a little special: the stack is supposed to
               * be ...V_LONG, V_LONG, V_INT => ...V_LONG, V_LONG
               * Handle "V_INT" first to use arith_like
               */
              //check stack underflow
              if (currBBStkTop <= currBBStkEmpty) {
                verificationFailure("stack underflow");
              }

              if (currBBMap[currBBStkTop] != V_INT) {
                verificationFailure("stack has wrong type");
              }

              currBBStkTop--;
              arith_like(V_LONG, 1, 2);
              break;
            }

          case JBC_fadd:
          case JBC_fsub:
          case JBC_fmul:
          case JBC_fdiv:
          case JBC_frem:
            arith_like(V_FLOAT, 2, 1);
            break;

          case JBC_dadd:
          case JBC_dsub:
          case JBC_dmul:
          case JBC_ddiv:
          case JBC_drem:
            arith_like(V_DOUBLE, 2, 2);
            break;

          case JBC_ineg:
            arith_like(V_INT, 1, 1);
            break;

          case JBC_lneg:
            arith_like(V_LONG, 1, 2);
            break;

          case JBC_fneg:
            arith_like(V_FLOAT, 1, 1);
            break;

          case JBC_dneg:
            arith_like(V_DOUBLE, 1, 2);
            break;

          case JBC_iinc:{
            //check index validity
            int index = bcodes.getLocalNumber();
            int val = bcodes.getIncrement();
            if (index <0 || index > currBBStkEmpty) {
              verificationFailure("invalid register index (" + index + ")");
            }
            //check type in the register
            if (currBBMap[index]!=V_INT) {
              verificationFailure("register " + index +" has wrong type");
            }

            //####
            if (inJSRSub && currPendingJsr != null && !currPendingJsr.updateOnce)
              currPendingJsr.setUsed(index);
            break; 
          }

          //return like bytecode
          case JBC_return:
            return_like(V_VOID, 0, method);
            break;
          case JBC_ireturn:
            return_like(V_INT, 1, method);
            break;
          case JBC_lreturn:
            return_like(V_LONG, 2, method);
            break;
          case JBC_freturn:
            return_like(V_FLOAT, 1, method);
            break;
          case JBC_dreturn:
            return_like(V_DOUBLE, 2, method);
            break;
          case JBC_areturn:
            return_like(V_REF, 1, method);
            break;

            //*aload bytecode
          case JBC_iaload:
          case JBC_baload:
          case JBC_caload:
          case JBC_saload:
            aaload_like(V_INT, 1);
            break;
          case JBC_laload:
            aaload_like(V_LONG, 2);
            break;
          case JBC_faload:
            aaload_like(V_FLOAT, 1);
            break;
          case JBC_daload:
            aaload_like(V_DOUBLE, 2);
            break;
          case JBC_aaload:
            aaload_like(V_REF, 1);
            break;

            //*astore bytecode
          case JBC_iastore:
          case JBC_bastore:
          case JBC_castore:
          case JBC_sastore:
            aastore_like(V_INT, 1);
            break;
          case JBC_lastore:
            aastore_like(V_LONG, 2);
            break;
          case JBC_fastore:
            aastore_like(V_FLOAT, 1);
            break;
          case JBC_dastore:
            aastore_like(V_DOUBLE, 2);
            break;
          case JBC_aastore:
            aastore_like(V_REF, 1);
            break;

            //ldc* bytecode
          case JBC_ldc:{
            int cpindex = bcodes.getConstantIndex();
            ldc_like(1, cpindex, declaringClass);
            break;
          }
          case JBC_ldc_w:{
            int cpindex = bcodes.getWideConstantIndex();
            ldc_like(1, cpindex, declaringClass);
            break;
          }
          case JBC_ldc2_w:{
            int cpindex = bcodes.getWideConstantIndex();
            ldc_like(2, cpindex, declaringClass);
            break;
          }
          //cmp, no branch bytecode
          case JBC_lcmp:
            cmp_like(V_LONG, 2, 2, 1);
            break;
          case JBC_fcmpl:
            cmp_like(V_FLOAT, 1, 2, 1);
            break;
          case JBC_fcmpg:
            cmp_like(V_FLOAT,1, 2, 1);
            break;
          case JBC_dcmpl:
            cmp_like(V_DOUBLE, 2, 2, 1);
            break;
          case JBC_dcmpg:
            cmp_like(V_DOUBLE, 2, 2, 1);
            break;
            
            //ifnull like bytecode
          case JBC_ifnull:
          case JBC_ifnonnull:{
            cmp_like(V_REF, 1, 1, 0);
            int offset = bcodes.getBranchOffset();
            checkOffset(offset);

            if ( offset < 0) {  //backward branch
              short NextBBNum = byteToBlockMap[currentByteCodeIndex+3];
              processBranchBB(NextBBNum);
              processNextBlock = false;
            }
            short brBBNum = byteToBlockMap[currentByteCodeIndex+offset];
            processBranchBB(brBBNum);
            break;
          }

          //ifeq like bytecode
          case JBC_ifeq:
          case JBC_ifne:
          case JBC_iflt:
          case JBC_ifle:
          case JBC_ifgt:
          case JBC_ifge:{
            cmp_like(V_INT, 1,1, 0);
            int offset = bcodes.getBranchOffset();
            checkOffset(offset);

            if ( offset < 0) {  //backward branch
              short NextBBNum = byteToBlockMap[currentByteCodeIndex+3];
              processBranchBB(NextBBNum);
              processNextBlock = false;
            }
            short brBBNum = byteToBlockMap[currentByteCodeIndex+offset];
            processBranchBB(brBBNum);
            break;
          }

          //cmp_branch like bytecode
          case JBC_if_icmpeq:   
          case JBC_if_icmpne:
          case JBC_if_icmplt:
          case JBC_if_icmpge:
          case JBC_if_icmpgt:
          case JBC_if_icmple:{
            cmp_like(V_INT, 1,2, 0);
            int offset = bcodes.getBranchOffset();
            checkOffset(offset);
            
            if ( offset < 0) {  //backward branch
              short NextBBNum = byteToBlockMap[currentByteCodeIndex+3];
              processBranchBB(NextBBNum);
              processNextBlock = false;
            }
            short brBBNum = byteToBlockMap[currentByteCodeIndex+offset];
            processBranchBB(brBBNum);
            break;
          }

          case JBC_if_acmpeq:
          case JBC_if_acmpne:{
            cmp_like(V_REF, 1,2, 0);
            int offset = bcodes.getBranchOffset();

            checkOffset(offset);

            if ( offset < 0) {  //backward branch
              short NextBBNum = byteToBlockMap[currentByteCodeIndex+3];
              processBranchBB(NextBBNum);
              processNextBlock = false;
            }
            short brBBNum = byteToBlockMap[currentByteCodeIndex+offset];
            processBranchBB(brBBNum);
            break;
          }

          //goto instructions:
          case JBC_goto:{
            int offset = bcodes.getBranchOffset();
            checkOffset(offset);

            short brBBNum = byteToBlockMap[currentByteCodeIndex+offset];
            processBranchBB(brBBNum);
            processNextBlock = false;
            break;
          }

          case JBC_goto_w:{
            int offset = bcodes.getWideBranchOffset();
            checkOffset(offset);

            short brBBNum = byteToBlockMap[currentByteCodeIndex+offset];
            processBranchBB(brBBNum);
            processNextBlock = false;
            break;
          }

          //switch
          case JBC_tableswitch : {
            //check stack underflow
            if (currBBStkTop <= currBBStkEmpty) {
              verificationFailure("stack underflow");
            }
            //top of stack: index must be int
            if (currBBMap[currBBStkTop]!=V_INT) {
              verificationFailure("stack has wrong type object on top");
            }
            currBBStkTop--; 
            bcodes.alignSwitch();
            int def = bcodes.getDefaultSwitchOffset();
            // offset
            checkOffset(def);
            processBranchBB(byteToBlockMap[currentByteCodeIndex+def]);

            int low = bcodes.getLowSwitchValue();
            int high = bcodes.getHighSwitchValue();
            int n = high - low + 1;
            for (int k = 0; k < n; k++) {
              int offset = bcodes.getTableSwitchOffset(k);
              checkOffset(offset);
              processBranchBB(byteToBlockMap[currentByteCodeIndex+offset]);
            }
            processNextBlock = false;       
            bcodes.skipTableSwitchOffsets(n);
            break;
          }

          case JBC_lookupswitch : {
            //check stack underflow
            if (currBBStkTop <= currBBStkEmpty) {
              verificationFailure("stack underflow");
            }
            //top of stack: key must be int
            if (currBBMap[currBBStkTop]!=V_INT) {
              verificationFailure("stack has wrong type of object at top (needs an int)");
            }
            currBBStkTop--; 
            bcodes.alignSwitch();
            // get default offset and generate basic block at default offset
            int def = bcodes.getDefaultSwitchOffset();
            checkOffset(def);
            processBranchBB(byteToBlockMap[currentByteCodeIndex+def]);

            int npairs = bcodes.getSwitchLength();
            // generate label for each offset in table
            for (int k = 0; k < npairs; k++) {
              int offset = bcodes.getLookupSwitchOffset(k);
              checkOffset(offset);
              processBranchBB(byteToBlockMap[currentByteCodeIndex+offset]);
            }
            processNextBlock = false;
            bcodes.skipLookupSwitchPairs(npairs);
            break;
          }

          //jsr
          case JBC_jsr : {
            processNextBlock = false;
            int offset = bcodes.getBranchOffset();
            currBBStkTop++;
            //check stack overflow
            if (currBBStkTop >= currBBMap.length) {
              verificationFailure("stack overflow");
            }
            currBBMap[currBBStkTop] = V_RETURNADDR; 
            checkOffset(offset);

            //#### 
            short brBBNum = byteToBlockMap[currentByteCodeIndex+offset];
            short nextBBNum = byteToBlockMap[currentByteCodeIndex+3];

            if (bbPendingJsrs[brBBNum]==null)
              bbPendingJsrs[brBBNum] = new VM_PendingJSRInfo(currentByteCodeIndex+offset, currBBStkEmpty,
                                                             currBBMap, currBBStkTop, currPendingJsr);
            else{
              //compute type map for the instruction right after "jsr" if
              //the jsr subroutine is already processed once
              int[] endMap = bbPendingJsrs[brBBNum].endMap;
              if (typeMaps[nextBBNum]==null && endMap != null) {
                typeMaps[nextBBNum] = new int[endMap.length];
                boolean[] used = bbPendingJsrs[brBBNum].getUsedMap();
                for (int j =0; j <= currBBStkEmpty; j++) {
                  if (used[j])
                    typeMaps[nextBBNum][j] = endMap[j];
                  else
                    typeMaps[nextBBNum][j] = currBBMap[j];
                }       
                for (int j = currBBStkEmpty+1; j <= currBBStkTop; j++)
                  typeMaps[nextBBNum][j] = endMap[j];
                //-1 to get rid of the return address on the top of stack now
                blockStkTop[nextBBNum] = currBBStkTop -1;
                
                addToWorkStk(nextBBNum);
              }
            }

            bbPendingJsrs[brBBNum].addSitePair(currBBMap, nextBBNum);
            
            if (currPendingJsr!= null)
              bbPendingJsrs[nextBBNum] = currPendingJsr;
            
            processBranchBB(byteToBlockMap[currentByteCodeIndex+offset]);
            break;
          }

          case JBC_jsr_w : {
            processNextBlock = false;
            int offset = bcodes.getWideBranchOffset();
            currBBStkTop++;
            //check stack overflow
            if (currBBStkTop >= currBBMap.length) {
              verificationFailure("stack overflow");
            }
            currBBMap[currBBStkTop] = V_RETURNADDR; 
            checkOffset(offset);
            //#### 
            short brBBNum = byteToBlockMap[currentByteCodeIndex+offset];
            short nextBBNum = byteToBlockMap[currentByteCodeIndex+3];
            
            if (bbPendingJsrs[brBBNum]==null)
              bbPendingJsrs[brBBNum] = new VM_PendingJSRInfo(currentByteCodeIndex+offset, currBBStkEmpty,
                                                             currBBMap, currBBStkTop, currPendingJsr);
            else{
              //compute type map for the instruction right after "jsr" if
              //the jsr subroutine is already processed once
              int[] endMap = bbPendingJsrs[brBBNum].endMap;
              if (typeMaps[nextBBNum]==null && endMap != null) {
                typeMaps[nextBBNum] = new int[endMap.length];
                boolean[] used = bbPendingJsrs[brBBNum].getUsedMap();
                for (int j =0; j <= currBBStkEmpty; j++) {
                  if (used[j])
                    typeMaps[nextBBNum][j] = endMap[j];
                  else
                    typeMaps[nextBBNum][j] = currBBMap[j];
                }       
                for (int j = currBBStkEmpty+1; j <= currBBStkTop; j++)
                  typeMaps[nextBBNum][j] = endMap[j];
                
                //-1 to get rid of the return address on the top of stack now
                blockStkTop[nextBBNum] = currBBStkTop -1;
                addToWorkStk(nextBBNum);
              }
            }
            
            bbPendingJsrs[brBBNum].addSitePair(currBBMap, nextBBNum);
            
            if (currPendingJsr!= null)
              bbPendingJsrs[nextBBNum] = currPendingJsr;
            
            processBranchBB(byteToBlockMap[currentByteCodeIndex+offset]);
            
            break;
          }

        case JBC_ret:{
          //#### 
          //index of local variable (unsigned byte)
          int index = bcodes.getLocalNumber();
          //can not be used again as a return addr.
          currBBMap[index] = V_UNDEF;
          processNextBlock = false;

          currPendingJsr.updateOnce = true;
          computeJSRNextMaps();
          
          break;
        }

        //invoke like bytecodes
        case JBC_invokespecial:
        case JBC_invokevirtual: {
          VM_MethodReference target = bcodes.getMethodReference();
          processInvoke(target,false);
          break;
        }

        case JBC_invokeinterface:{
          VM_MethodReference target = bcodes.getMethodReference();
          bcodes.alignInvokeInterface();
          processInvoke(target,false);
          break;
        }

        case JBC_invokestatic:{
          VM_MethodReference target = bcodes.getMethodReference();
          processInvoke(target,true);
          break;
        }

                                //get
        case JBC_getstatic:{
          VM_FieldReference field = bcodes.getFieldReference();
          get_like(field, true);
          break;
        }

        case JBC_getfield:{
          VM_FieldReference field = bcodes.getFieldReference();
          get_like(field, false);
          break;
        }

        //put
        case JBC_putstatic:{
          VM_FieldReference field = bcodes.getFieldReference();
          put_like(field, true);
          break;
        }

        case JBC_putfield:{
          VM_FieldReference field = bcodes.getFieldReference();
          put_like(field, false);
          break;
        }

        case JBC_checkcast:{
          //check whether toType is a reference type
          VM_TypeReference toType = bcodes.getTypeReference();
          if (!toType.isReferenceType()) {
            verificationFailure("checkcast dest type isn't reference type");
          }

          //check stack underflow
          if (currBBStkTop <= currBBStkEmpty) {
            verificationFailure("stack underflow");
          }

          //check whether fromType is a reference type
          if (currBBMap[currBBStkTop]<0) {
            verificationFailure("checkcast from a type isn't a reference type");
          }

          //check whether fromType is assignable to the totype
          //Note: if toType is subclass of fromType, it should be passed by verifier
          if (currBBMap[currBBStkTop]!=V_NULL 
              && !VM_Runtime.isAssignableWith(resolve(VM_TypeReference.getTypeRef(currBBMap[currBBStkTop])), resolve(toType))
              && !VM_Runtime.isAssignableWith(resolve(toType), resolve(VM_TypeReference.getTypeRef(currBBMap[currBBStkTop]))))
            {
              verificationFailure("checkcast: fromType isn't assignable to toType\n"
                                  + "====> toType: " + toType + " id:" + toType.getId()
                                  + "\n====> fromType: "+ VM_TypeReference.getTypeRef(currBBMap[currBBStkTop])
                                  + "\n====> id: " + currBBMap[currBBStkTop] );
            }
          currBBMap[currBBStkTop] = toType.getId();

          break;
        }

        case JBC_instanceof:{
                                //check whether toType is a reference type
          VM_TypeReference type = bcodes.getTypeReference();
          if (!type.isReferenceType()) {
            verificationFailure("an instanceof instruction's dest type isn't a"
                              + " reference type");
          }

          //check stack underflow
          if (currBBStkTop <= currBBStkEmpty) {
            verificationFailure("stack underflow from an instanceof"
                                + " instruction");
          }

          //check whether fromType is a reference type
          if (currBBMap[currBBStkTop]<0) {
            verificationFailure("an instanceof instruction's from type isn't a reference type");
            
          }

          //pop fromtype from the stack
          currBBStkTop--;
          //push the int result onto the stack
          currBBMap[++currBBStkTop] = V_INT;
          break;
        }

        //new
          case JBC_new:{
            // the type in constant pool must be a class
            VM_TypeReference newType = bcodes.getTypeReference();
            if (!newType.isClassType()) {
              verificationFailure("the 'new type' argument to a 'new' instruction isn't a class type");
            }
            //check stack overflow
            currBBStkTop ++;
            if (currBBStkTop >= currBBMap.length) {
              verificationFailure("stack overflow");
            }
            //push the class type onto the stack
            currBBMap[currBBStkTop] = newType.getId(); 
            
            //####use the bytecode index as the label of uninitiated new object
            newObjectInfo[currBBStkTop-currBBStkEmpty -1] = true;
            
            break;
          }

          case JBC_newarray:{
            //check stack underflow
            if (currBBStkTop <= currBBStkEmpty) {
              verificationFailure("stack underflow");
            }
            //check whether the top of stack is int
            if (currBBMap[currBBStkTop]!=V_INT) {
              verificationFailure("stack has wrong type (need an int)");
            }
            //pop the count
            currBBStkTop--;

            //push the array type
            int atype = bcodes.getArrayElementType();
            if (atype<4 || atype >11) {
              verificationFailure("invalid atype argument (" + atype + ") to 'newarray' instruction");
            }
            currBBMap[++currBBStkTop] = VM_Array.getPrimitiveArrayType(atype).getTypeRef().getId();
            break;
          }

        case JBC_anewarray:{
          //check stack underflow
          if (currBBStkTop <= currBBStkEmpty) {
            verificationFailure("stack underflow");
          }
          //check whether the top of stack is int
          if (currBBMap[currBBStkTop]!=V_INT) {
            verificationFailure("stack has wrong type (need an int)");
          }
          //pop the count
          currBBStkTop--;

          // the type in constant pool must be a reference type 
          VM_TypeReference newType = bcodes.getTypeReference();
          if (!newType.isReferenceType()) {
            verificationFailure("an anewarray instruction has a type argument that isn't a reference type");
          }

          //push the new reference array onto the stack
          currBBMap[++currBBStkTop] = newType.getArrayTypeForElementType().getId();
          
          break;
        }

          case JBC_multianewarray:{
            // the type in constant pool must be a reference type 
            VM_TypeReference newType = bcodes.getTypeReference();
            if (!newType.isReferenceType()) {
              verificationFailure("a multianewarray instruction's type argument isn't a reference type");
            }

            int dimension = bcodes.getArrayDimension();
            //check stack underflow
            if (currBBStkTop - dimension < currBBStkEmpty) {
              verificationFailure("stack underflow");
            }

            for (int k = 0; k<dimension; k++) {
              //check whether the top of stack is int
              if (currBBMap[currBBStkTop] != V_INT) {
                verificationFailure("stack top has wrong type (need an int)");
              }
              //pop the count
              currBBStkTop--;
            }

            //push the new reference array onto the stack
            currBBMap[++currBBStkTop] = newType.getId();
            break;
          }

          case JBC_arraylength:
            //check stack underflow
            if (currBBStkTop <= currBBStkEmpty) {
              verificationFailure("stack underflow");
            }

            //check whether stack top is an array reference
            if (currBBMap[currBBStkTop]<=0 || 
               !VM_TypeReference.getTypeRef(currBBMap[currBBStkTop]).isArrayType()) {
              verificationFailure("stack has wrong type (need an array reference)");
            }

            //push int type (length) onto stack
            currBBMap[currBBStkTop] = V_INT;
            break;

        case JBC_athrow:
          {
            //check whether type of top stack is a subclass of Throwable
            if (currBBMap[currBBStkTop] < 0) {  //not a reference
              verificationFailure("stack has wrong type (need a subclass of Throwable)");
            }
            int typeId = currBBMap[currBBStkTop];
            if (typeId == V_NULL) {
              currBBStkTop = currBBStkEmpty +1;
              currBBMap[currBBStkTop] = typeId;
              processNextBlock = false;
              break;
            }
            VM_Class cls = null; /* init it to shut up a compiler warning;
                                    verificationFailure will never return. */ 
            
            try {
              cls = (VM_Class) VM_TypeReference.getTypeRef(typeId).resolve();
            } catch (NoClassDefFoundError ncdf) {
              verificationFailure("Unable to find the class of an 'athrow' instruction's argument: " + ncdf, ncdf);
            } catch (ClassCastException cce) {
              verificationFailure("We tried to get the class of an 'athrow' instruction's argument, but it wasn't a class: " + cce.getMessage(), cce);
            }

            if (!cls.isClassType()) {   // not a object reference
              verificationFailure("stack needs an object ref. on top");
            }

            VM_Type throwType = VM_Type.JavaLangThrowableType;

            while(cls!= null && cls!= throwType)
              cls = cls.getSuperClass();

            if (cls==null) {
              verificationFailure("has an argument that isn't Throwable");
            }
            currBBStkTop = currBBStkEmpty +1;
            currBBMap[currBBStkTop] = typeId;
            processNextBlock = false;
            break;
          }
          case JBC_monitorenter:
          case JBC_monitorexit:
            if (currBBMap[currBBStkTop] < 0) {  // not a reference
              verificationFailure("stack has a non-reference type on top");
            }
            currBBStkTop--;
            break;

          case JBC_wide:{
            int widecode = bcodes.getWideOpcode();
            int index = bcodes.getWideLocalNumber();
            switch(widecode) {
            case JBC_iload:
              load_like(V_INT, index, 1, true);
              break;
            case JBC_fload:
              load_like(V_FLOAT, index, 1, true);
              break;
            case JBC_lload:
              load_like(V_LONG, index, 2, true);
              break;
            case JBC_dload:
              load_like(V_DOUBLE, index, 2, true);
              break;
            case JBC_aload: 
              load_like(V_REF, index, 1, true);
              break;
            case JBC_istore:
              store_like(V_INT, 1, index, reachableHandlerBBNums, reachableHandlersCount); 
              break;
            case JBC_fstore:
              store_like(V_FLOAT, 1, index, reachableHandlerBBNums, reachableHandlersCount); 
              break;
            case JBC_lstore:
              store_like(V_LONG, 2, index, reachableHandlerBBNums, reachableHandlersCount); 
              break;
            case JBC_dstore:
              store_like(V_DOUBLE, 2, index, reachableHandlerBBNums, reachableHandlersCount); 
              break;
            case JBC_astore:
              store_like(V_REF, 1, index, reachableHandlerBBNums, reachableHandlersCount); 
              break;
            case JBC_iinc:
              int val = bcodes.getWideIncrement();
              if (index <0 || index > currBBStkEmpty) {
                verificationFailure("invalid register index; index: " + index);
              }
              //check type in the register
              if (currBBMap[index]!=V_INT) {
                verificationFailure("register " + index +" has wrong type (need an int)");
              }

              //####
              if (inJSRSub && currPendingJsr != null && !currPendingJsr.updateOnce)
                currPendingJsr.setUsed(index);
              break;
            case JBC_ret: {
              //#### 
              //can not be used again as a return addr.
              if (currBBMap[index] != V_RETURNADDR) {
                verificationFailure("wrong register type at a 'ret' instruction");
              }
              currBBMap[index] = V_UNDEF;
              processNextBlock = false;
              
              currPendingJsr.updateOnce = true;
              computeJSRNextMaps();
              break;
            }
            default: // switch on widecode
              if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
            }
          break;
        }  // case JBC_wide:
        default:{
          verificationFailure("found an unknown opcode #" + opcode);
        }
        }//end of switch

      } // end of while

      if (processNextBlock) {
        short nextBBNum = byteToBlockMap[bcodes.index()];
        processBranchBB(nextBBNum);
      }

      if ((workStkTop ==-1) && !handlersAllDone) {
        int ii;
        for (ii =0; ii < tryHandlerLength; ii++) {
          if (handlerProcessed[ii] || typeMaps[byteToBlockMap[tryHandlerPC[ii]]]== null)
            continue;
          else
            break;
        }
        if (ii == tryHandlerLength)
          handlersAllDone = true;
        else{
          int considerIndex = ii;
          // XXX FIXME Infinite Loop
          while (ii != tryHandlerLength) {

            int tryStart = tryStartPC[considerIndex];
            int tryEnd   = tryEndPC[considerIndex];

            for (ii = 0; ii <tryHandlerLength; ii++)
              // For every handler that has not yet been processed, 
              // but already has a known starting map,
              // make sure it is not in the try block part of the handler
              // we are considering working on. 
              if (!handlerProcessed[ii] && tryStart <= tryHandlerPC[ii] &&
                  tryHandlerPC[ii] < tryEnd && typeMaps[byteToBlockMap[tryHandlerPC[ii]]] != null)
                break;

            if (ii != tryHandlerLength)

              considerIndex = ii;

          }//end while

          short blockNum = byteToBlockMap[tryHandlerPC[considerIndex]];
          handlerProcessed[considerIndex] = true;
          addToWorkStk(blockNum);
        } // end else
      }// end if

    }//end of while workStk


    //set initial value to work data structure
    workStkTop =0;
    currBBNum  =0;
    currBBMap  = null;
    currBBStkEmpty  = 0;
    currBBStkTop  = 0;
    currMethodName = null;
    currPendingJsr = null;
    processNextBlock = true;
  }

  /**
   * Process bytecodes like "load", "const" and bipush, sipush, and so on. 
   *
   * @param expectType the type this bytecode expects. 
   *                   Could be: V_INT, V_FLOAT, V_DOUBLE, V_LONG, V_REF
   * @param index  the register index, -1 for "const" set bytecodes
   * @param stackWords  1 for int/float/reference, 2 for double/long
   * @param checkIndex  whether need to check the validity of index or not
   *
   * @return nothing
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void load_like(int expectType, int index, int stackWords, boolean checkIndex)
    throws VerifyError 
  { 
      currBBStkTop += stackWords;
      //check stack overflow   ---- must be done for all load like instructions
      if (currBBStkTop >= currBBMap.length) {
        verificationFailure("stack overflow");
      }

      if (checkIndex == true) {                 //*load_<n>*
        //check register index
        if (index > currBBStkEmpty) {
          verificationFailure("invalid register index;  index: " + index);
        }
        //check register type
        boolean correct = true;
        if (expectType == V_REF)
          correct = (currBBMap[index]>=0 && currBBMap[index +stackWords -1] >= 0);      
        else
          correct = (currBBMap[index]==expectType && currBBMap[index+stackWords -1] == expectType);

        if (correct == false) {
          verificationFailure("register " + index +" has wrong type");
        }
        //update type states
        currBBMap[currBBStkTop-stackWords +1] = currBBMap[index]; 
        currBBMap[currBBStkTop] = currBBMap[index];

        //if in JSR, set this register as used
        if (inJSRSub && currPendingJsr != null && !currPendingJsr.updateOnce)
          currPendingJsr.setUsed(index);
      }
      else                      //*const_*
        //update type states
        currBBMap[currBBStkTop] = currBBMap[currBBStkTop-stackWords +1] = expectType;

    }

  /**
   * Process bytecodes like "ldc", etc.. 
   *
   * @param numOfWord  1 for int/float/reference, 2 for double/long
   * @param cpindex  the constant pool index
   * @param declaringClass  the deClaring class of the method been verified 
   *
   * @return nothing
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void ldc_like(int numOfWord, int cpindex, VM_Class declaringClass)
    throws VerifyError 
  {
      currBBStkTop +=numOfWord;
      //check stack overflow 
      if (currBBStkTop >= currBBMap.length) {
        verificationFailure("stack overflow");
      }

      //check constant pool cell type
      byte cpType = declaringClass.getLiteralDescription(cpindex);
      if ((numOfWord == 1 && cpType != VM_Statics.INT_LITERAL && 
          cpType != VM_Statics.FLOAT_LITERAL && cpType!=VM_Statics.STRING_LITERAL)
         ||(numOfWord == 2 && 
            cpType != VM_Statics.LONG_LITERAL && cpType!=VM_Statics.DOUBLE_LITERAL)) {
        verificationFailure("wrong constant pool type");
      }

      //update stack top type
      switch(cpType) {
        case VM_Statics.INT_LITERAL:
          currBBMap[currBBStkTop]= V_INT;
          break;
        case VM_Statics.FLOAT_LITERAL:
          currBBMap[currBBStkTop]= V_FLOAT;
          break;
        case VM_Statics.STRING_LITERAL:
          currBBMap[currBBStkTop]= VM_Type.JavaLangStringType.getTypeRef().getId();
          break;
        case VM_Statics.LONG_LITERAL:
          currBBMap[currBBStkTop]= currBBMap[currBBStkTop-1] = V_LONG;
          break;
        case VM_Statics.DOUBLE_LITERAL:
          currBBMap[currBBStkTop]= currBBMap[currBBStkTop-1] = V_DOUBLE;
          break;
        default:
          verificationFailure("unknown constant pool type #" + cpType + " found");
      }
    }

  /**
   * process type converting bytecodes like "i2l", etc.. 
   *
   * @param fromType the type converted from, could be V_INT, V_LONG, V_FLOAT OR V_DOUBLE
   * @param toType the type converted to, could be V_INT, V_LONG, V_FLOAT OR V_DOUBLE
   * @param fromWord  1 for int/float, 2 for double/long
   * @param toWord  1 for int/float, 2 for double/long
   *
   * @return nothing
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void cast_like(int fromType, int toType, int fromWord, int toWord)
    throws VerifyError 
  {
    boolean correct = true;
    //check from type on the top of stack
    for (int i = 0; i < fromWord; i++) {
      if (currBBMap[currBBStkTop-i] != fromType)
        verificationFailure("stack has wrong type element on top");
    }
    //check stack underflow
    if (currBBStkTop - fromWord + 1 <= currBBStkEmpty) {
      verificationFailure("stack underflow");
    }
    //check stack overflow
    if (fromWord < toWord && currBBStkTop +1 >= currBBMap.length)
      verificationFailure("stack overflow");

    //update type states
    //pop fromType 
    for (int i = 0; i < fromWord; ++i)
      currBBMap[currBBStkTop--] = 0;

    //push toType
    for (int i = 0; i < toWord; ++i)
      currBBMap[ ++currBBStkTop] = toType;
  }

  /**
   * process bytecodes like "istore", etc.. 
   *
   * @param expectType the type this bytecode expects. 
   *                   Could be: V_INT, V_FLOAT, V_DOUBLE, V_LONG, V_REF
   * @param storeWords  1 for int/float/reference, 2 for double/long
   * @param index  the register index 
   * @param reachableHandlerBBNums the basic block numbers of all reachable 
   *                                  exception handlers from this basic block
   * @param reachableHandlersCount the number of reachable exception handlers from this basic block
   *
   * @return nothing
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void store_like(int expectType, int storeWord, int index, 
                          int[] reachableHandlerBBNums, 
                          int reachableHandlersCount) 
    throws VerifyError 
  {
    //check storeType
    for (int i = 0; i <storeWord; ++i) {
      boolean correct;
      if (expectType == V_REF)
        correct = !newObjectInfo[currBBStkTop-currBBStkEmpty-1-i] 
          && ((currBBMap[currBBStkTop-i]>=0) 
              || (currBBMap[currBBStkTop-i]==V_RETURNADDR));
      else 
        correct = (currBBMap[currBBStkTop-i] == expectType);

      if (!correct)
        verificationFailure("stack has wrong type on top" );
    }
    //check validity of index
    if (index + storeWord -1 > currBBStkEmpty || index <0 ) {
      verificationFailure("invalid register index @ index: " + index);
      
    }

    //check stack underflow
    if (currBBStkTop-storeWord +1 <= currBBStkEmpty) {
      verificationFailure("stack underflow");
    }

    //update type states
    for (int i = 0; i < storeWord; i++)
      currBBMap[index+i] = currBBMap[currBBStkTop--];

    //if in JSR, set the register to be used
    if (inJSRSub && currPendingJsr != null && !currPendingJsr.updateOnce) {
      currPendingJsr.setUsed(index);
      if (currBBMap[index]==V_RETURNADDR)
        currPendingJsr.updateReturnAddressLocation(index);
    }

    setHandlersMaps(currBBMap[index], index, storeWord, 
                    reachableHandlerBBNums, reachableHandlersCount); 

  }

  /**
   * process bytecodes like "dup", etc.. 
   *
   * @param numTodup the number of stack cells to be duplicated
   * @param numTodown the distance from the stack cells duplicated to stack
   *                    top   
   *
   * @return nothing
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void dup_like(int numTodup, int numTodown) 
    throws VerifyError 
  {
    //check stack overflow
    if (currBBStkTop +numTodup >= currBBMap.length) {   
      verificationFailure("stack overflow");
    }
    //check the type
    if ((numTodup == 1 && currBBMap[currBBStkTop] <= V_LONG)
       || (numTodup + numTodown > 1 && 
           currBBMap[currBBStkTop-numTodup - numTodown +1]<=V_LONG &&
           currBBMap[currBBStkTop-numTodup - numTodown +2]>V_LONG )) {
      verificationFailure("stack has wrong type");
    }
    //update the stack
    for (int i =0; i < numTodown+numTodup; i++)
      currBBMap[currBBStkTop+numTodup-i] = currBBMap[currBBStkTop-i];
    for (int j = 0; j < numTodup; j++)
      currBBMap[currBBStkTop - numTodown -j] = currBBMap[currBBStkTop+numTodup-j];
    currBBStkTop += numTodup;
  }

  /**
   * process all arithmetic and logic bytecodes like "iadd", etc.. 
   *
   * @param expectType the type this bytecode expects. 
   *                   Could be: V_INT, V_FLOAT, V_DOUBLE, V_LONG
   * @param numOfOpd the number of operands, could be 1 for unary bytecode like ineg 
   * @param numOfWord 1 for int/float, 2 for double/long  
   *
   * @return nothing
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void arith_like(int expectType, int numOfOpd, int numOfWord)
    throws VerifyError
  {
    //check stack underflow
    if (currBBStkTop-numOfWord +1 <= currBBStkEmpty) {
      verificationFailure("stack underflow");
    }

    //check type
    for (int i = 0; i < numOfWord*numOfOpd; ++i)
      if (currBBMap[currBBStkTop-i] != expectType)
        verificationFailure("stack has wrong type");
    
    //update the stack, pop operands, push result
    if (numOfOpd != 1)
      currBBStkTop = currBBStkTop - numOfWord;
  }

  /**
   * process all bytecodes like "return", etc.. 
   *
   * @param expectType the type this bytecode expects. 
   *                   Could be: V_INT, V_FLOAT, V_DOUBLE, V_LONG, V_REF
   * @param numOfWord 1 for int/float/reference, 2 for double/long  
   * @param method the method is being processed, used to verify the return type
   *
   * @return nothing
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void return_like(int expectType, int numOfWord, VM_Method method)
    throws VerifyError 
  {
    boolean correct;
    
    //check stack underflow
    if (currBBStkTop-numOfWord +1 <= currBBStkEmpty) {
      verificationFailure("stack underflow");
    }

    //check stack type
    for (int i = 0; i < numOfWord; i++) {
      if (expectType == V_REF)
        correct = !newObjectInfo[currBBStkTop-currBBStkEmpty-1-i] && (currBBMap[currBBStkTop-i]>=0);
      else
        correct = (currBBMap[currBBStkTop-i]==expectType);
      if (!correct)
        verificationFailure("stack has wrong type on top");
    }
    correct = false;

      //check return type
    VM_TypeReference returnType = method.getReturnType();
    switch(expectType) {
    case V_VOID:
      correct = returnType.isVoidType();
      break;
    case V_INT:
      correct = returnType.isIntLikeType();
      break;
    case V_LONG:
      correct = returnType.isLongType();
      break;
    case V_FLOAT:
      correct = returnType.isFloatType();
      break;
    case V_DOUBLE:
      correct = returnType.isDoubleType();
      break;
    case V_REF:
      if (currBBMap[currBBStkTop]==V_NULL)
        correct = returnType.isReferenceType();
      else
        correct = VM_Runtime.isAssignableWith(
                                              resolve(returnType),
                                              resolve(VM_TypeReference.getTypeRef(currBBMap[currBBStkTop])));
      break;
    default:
      verificationFailure("invalid return type #" + expectType);
    }
    if (! correct) {
      verificationFailure("stack has wrong type");
    }
    
    currBBStkTop-= numOfWord;
    processNextBlock = false;
  }

  /**
   * process all array load bytecodes like "aaload", etc.. 
   *
   * @param expectType the type this bytecode expects. 
   *                   Could be: V_INT, V_FLOAT, V_DOUBLE, V_LONG, V_REF
   * @param numOfWord 1 for int/float/reference, 2 for double/long  
   *
   * @return nothing
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void aaload_like(int expectType, int numOfWord) 
    throws VerifyError 
  {
    //check stack underflow
    if ((currBBStkTop-2)  < currBBStkEmpty) {
      verificationFailure("stack underflow");
    }
    //check stack type
    if (currBBMap[currBBStkTop]!=V_INT || currBBMap[currBBStkTop-1]<= 0) { 
      verificationFailure("stack has wrong type");
    }   

    //check whether the second top of stack is an arrayType
    VM_TypeReference arrayType = VM_TypeReference.getTypeRef(currBBMap[currBBStkTop-1]);
    if (!arrayType.isArrayType()) {
      verificationFailure("one down on the stack should be an arrayRef");
    }

    //check the compatibility of the expectType and the element type of array
    VM_TypeReference eleType = arrayType.getArrayElementType();
    if ((eleType.isIntLikeType() && expectType != V_INT) ||
       (eleType.isLongType() && expectType != V_LONG) ||
       (eleType.isFloatType() && expectType != V_FLOAT) ||
       (eleType.isDoubleType() && expectType != V_DOUBLE) ||
       (eleType.isReferenceType() && expectType != V_REF)) {
      verificationFailure("incompatible element type");
    }

    //update the stack type
    currBBStkTop -= 2;
    for (int i = 0; i < numOfWord; i++)
      if (expectType ==V_REF)
        currBBMap[++currBBStkTop] = eleType.getId();
      else
        currBBMap[++currBBStkTop] = expectType;

  }

  /**
   * process all array store bytecodes like "aastore", etc.. 
   *
   * @param expectType the type this bytecode expects. 
   *                   Could be: V_INT, V_FLOAT, V_DOUBLE, V_LONG, V_REF
   * @param numOfWord 1 for int/float/reference, 2 for double/long  
   *
   * @return nothing
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void aastore_like(int expectType, int numOfWord)
    throws VerifyError
  {
      //check stack underflow
      if ((currBBStkTop-2-numOfWord)  < currBBStkEmpty) {
        verificationFailure("stack underflow");
      }

      //check the value type
      for (int i = 0; i <numOfWord; ++i) {
        boolean correct;
        if (expectType == V_REF)
          correct = !newObjectInfo[currBBStkTop-currBBStkEmpty-1-i] 
            && (currBBMap[currBBStkTop-i]>= 0);
        else 
          correct = (currBBMap[currBBStkTop-i] == expectType);

        if (!correct)
          verificationFailure("stack has wrong type");
      }
      //check index and arrayRef type
      if (currBBMap[currBBStkTop-numOfWord]!=V_INT || currBBMap[currBBStkTop-numOfWord-1]<= 0) { 
        verificationFailure("stack has wrong type");
      } 

      //check whether the third top of stack is an arrayType
      VM_TypeReference arrayType = VM_TypeReference.getTypeRef(currBBMap[currBBStkTop- numOfWord - 1]);
      if (!arrayType.isArrayType()) {
        verificationFailure("two down from the stack top should be an arrayRef");
      }

      //check the compatibility of the expectType and the element type of array
      VM_TypeReference eleType = arrayType.getArrayElementType();

      if ((eleType.isIntLikeType() && expectType != V_INT) 
          || (eleType.isLongType() && expectType != V_LONG) 
          || (eleType.isFloatType() && expectType != V_FLOAT) 
          || (eleType.isDoubleType() && expectType != V_DOUBLE)
          || (eleType.isReferenceType() && (expectType != V_REF))) 
        verificationFailure("incompatible element type");
      if (eleType.isReferenceType() && currBBMap[currBBStkTop] != V_NULL) {
        VM_Type eTr = resolve(eleType);         // eleType.resolve()
        VM_Type cbmTr = resolve(VM_TypeReference.getTypeRef(currBBMap[currBBStkTop]));
        if ( ! VM_Runtime.isAssignableWith(eTr, cbmTr)) {
          verificationFailure("incompatible reference type argument");
        }
      }
      //update the stack type, pop all three 
      currBBStkTop = currBBStkTop - 2 - numOfWord;
  }

  /**
   * process all comparation based bytecodes, either branch or non-branch, like "ifeq", "fcmpl", etc.. 
   *
   * @param expectType the type this bytecode expects. 
   *                   Could be: V_INT, V_FLOAT, V_DOUBLE, V_LONG, V_REF
   * @param numOfWord 1 for int/float/reference, 2 for double/long  
   * @param numOfOpd the number of operands
   * @param pushWord the word number of the result pushed back to the stack by the bytecode 
   *
   * @return nothing
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void cmp_like(int expectType, int numOfWord, int numOfOpd, int pushWord)
    throws VerifyError 
  {
      //check stack underflow
      if ((currBBStkTop-numOfWord*numOfOpd)  < currBBStkEmpty) {
        verificationFailure("stack underflow");
      }
      //check stack type
      boolean correct = true;
      for (int i = 0; i < numOfWord*numOfOpd; i++)
        if (expectType == V_REF)
          correct = (currBBMap[currBBStkTop-i] >= 0);
        else
          correct = (currBBMap[currBBStkTop-i]==expectType);
      if (! correct) {
        verificationFailure("stack has wrong type on top");
      }
      //update the stack
      currBBStkTop -= numOfWord*numOfOpd;
      if (pushWord == 1)
        currBBMap[++currBBStkTop] = V_INT;
    }

  /**
   * process bytecodes getstatic and getfield 
   *
   * @param field the field reference that need to get
   * @param isStatic whether this field is static(true) or not(false)
   *
   * @return nothing
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void get_like(VM_FieldReference field, boolean isStatic)
    throws VerifyError
  {

      //if not static, check whether object type is compatible with field's declaring class
      if (!isStatic) {
        //check stack underflow
        if (currBBStkTop-1 < currBBStkEmpty) {
          verificationFailure("stack underflow");
        }
        //check the compatibility
        if (currBBMap[currBBStkTop]<0 || currBBMap[currBBStkTop]!=V_NULL
           && !VM_Runtime.isAssignableWith(resolve(field).getDeclaringClass(),
                                           resolve(VM_TypeReference.getTypeRef(currBBMap[currBBStkTop])))) {
          verificationFailure("incompatible object reference");
        }

        if (newObjectInfo[currBBStkTop-currBBStkEmpty-1]) {     //uninitialized object
          verificationFailure("uninitialized object reference trying to get field " + field);
        }
        //pop the "this" reference
        currBBStkTop --;
      }

      VM_Type fieldType = resolve(field.getFieldContentsType());
      
      //check stack overflow
      currBBStkTop += fieldType.getStackWords();
      if (currBBStkTop >= currBBMap.length) {
        verificationFailure("stack overflow");
      }
      //push the field onto the stack
      if (fieldType.isIntLikeType())
        currBBMap[currBBStkTop] = V_INT;
      else if (fieldType.isFloatType())
        currBBMap[currBBStkTop] = V_FLOAT;
      else if (fieldType.isLongType())
        currBBMap[currBBStkTop] = currBBMap[currBBStkTop-1] = V_LONG ;
      else if (fieldType.isDoubleType())
        currBBMap[currBBStkTop] = currBBMap[currBBStkTop-1] = V_DOUBLE ;
      else if (fieldType.isReferenceType())
        currBBMap[currBBStkTop] = fieldType.getTypeRef().getId(); 

    }

  /**
   * process bytecodes putstatic and putfield 
   *
   * @param field the field reference that need to get
   * @param isStatic whether this field is static(true) or not(false)
   *
   * @return nothing
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void put_like(VM_FieldReference field, boolean isStatic)
    throws VerifyError
  {

      VM_Type fieldType = resolve(field.getFieldContentsType());
      //check stack underflow
      if (currBBStkTop-fieldType.getStackWords() < currBBStkEmpty) {
        verificationFailure("stack underflow");
      }

      //pop the field from the stack
      boolean correct = true;
      if (fieldType.isIntLikeType())
        correct = (currBBMap[currBBStkTop] == V_INT);
      else if (fieldType.isFloatType())
        correct = (currBBMap[currBBStkTop] == V_FLOAT);
      else if (fieldType.isLongType())
        correct = (currBBMap[currBBStkTop] == V_LONG && currBBMap[currBBStkTop-1] == V_LONG );
      else if (fieldType.isDoubleType())
        correct = (currBBMap[currBBStkTop] == V_DOUBLE && currBBMap[currBBStkTop-1] == V_DOUBLE) ;
      else if (fieldType.isReferenceType())
        correct = !newObjectInfo[currBBStkTop-currBBStkEmpty -1] && 
          ((currBBMap[currBBStkTop] == V_NULL || 
            VM_Runtime.isAssignableWith(fieldType, resolve(VM_TypeReference.getTypeRef(currBBMap[currBBStkTop])))));
      if ( ! correct) {
        verificationFailure("incompatible field type");
      }
      currBBStkTop -= fieldType.getStackWords();

      //if not static, check whether object type is compatible with field's declaring class
      if (!isStatic) {
        //check stack underflow
        if (currBBStkTop-1 < currBBStkEmpty)
          verificationFailure("stack underflow");
        //check the compatibility
        if (currBBMap[currBBStkTop]<0 
            || !VM_Runtime.isAssignableWith(resolve(field).getDeclaringClass(), 
                                            resolve(VM_TypeReference.getTypeRef(currBBMap[currBBStkTop]))))
          verificationFailure("incompatible object reference");
          

        if (newObjectInfo[currBBStkTop-currBBStkEmpty-1]) {     //uninitialized object
          verificationFailure("uninitialized object reference when putfield " + 
                      field + " instruction");
        }
        //pop the "this" reference
        currBBStkTop --;
      }


    }

  /**
   * Merge a new type map to a basic block's old map. 
   * if the result type map different from the old map, add this block to work list 
   *
   * @param brBBNum  the destination basic block number
   * @param newBBMap the new type map that need to be merged
   * @param newBBStkTop the new type map's stack height
   *
   * @return nothing
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void MergeMaps(short brBBNum, int[] newBBMap, int newBBStkTop)  
    throws VerifyError
 {

    //if the destination block doesn't already have a map, then use this map as its map
    if (typeMaps[brBBNum] == null) {
      typeMaps[brBBNum] = new int[newBBMap.length];
      for (int i = 0; i <=newBBStkTop; i++)
        typeMaps[brBBNum][i] = newBBMap[i];
      blockStkTop[brBBNum] = newBBStkTop;
      addToWorkStk(brBBNum);
    } else { 
      /* if the destination block already has a map, then first check the
         height of the stack */
      if (blockStkTop[brBBNum] != newBBStkTop) {
        verificationFailure("different stack height when merging type maps");
      }

      boolean changed = false;
      /* Second,  compare each cell of the map, use the least common type as
         new map cell */
      for (int j = 0; j<=newBBStkTop; j++) {
        int newType = newBBMap[j];
        int originalType = typeMaps[brBBNum][j];
        if (newType == originalType)
          continue;
        int resultType = MergeOneCell(newType, originalType);
        if (resultType != originalType) {
          typeMaps[brBBNum][j]=resultType;
          changed = true;
        }
        /*
        //exactly the same
        if (typeMaps[brBBNum][j]==newBBMap[j])
        continue;

        //one of them is V_NULL, use the not null type
        if (typeMaps[brBBNum][j]==V_NULL && newBBMap[j]>0) {
        typeMaps[brBBNum][j] = newBBMap[j];
        changed = true;
        continue;
        }
        if (newBBMap[j] == V_NULL && typeMaps[brBBNum][j]>0)
        continue;

        //both are reference type
        if (typeMaps[brBBNum][j]>0 && newBBMap[j]>0) {
        int oldtype = typeMaps[brBBNum][j];
        typeMaps[brBBNum][j] = findCommonSuperClassId(typeMaps[brBBNum][j], newBBMap[j]);
        if (oldtype != typeMaps[brBBNum][j])
        changed = true;
        continue;
        }
        //other situation, set to undefined
        if (typeMaps[brBBNum][j] == V_UNDEF)
        continue;

        typeMaps[brBBNum][j] = V_UNDEF;
        changed = true;
         */
      } // end of for

      if (changed) {
        addToWorkStk(brBBNum);
      }
    }//end if else

  }

  /**
   * Merge two types. If they are incompatible, return V_UDEF, otherwise return 
   * the smallest common type.
   *
   * @param newType the new type that need to be merged
   * @param originalType the original type
   *
   * @return result type, V_UDEF if incompatible, otherwise the smallest common type 
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private int MergeOneCell(int newType, int originalType) 
    throws VerifyError
  {

    //exactly the same
    if (originalType == newType)
      return originalType;

    //one of them is V_NULL, use the not null type
    if (originalType ==V_NULL && newType >0)
      return newType;

    if (newType == V_NULL && originalType>0)
      return originalType;

    //both are reference type
    if (originalType > 0 && newType>0)
      return findCommonSuperClassId(originalType, newType);

    //other situation, set to undefined
    if (originalType == V_UNDEF)
      return originalType;

    return V_UNDEF;
  }

  /**
   * process branch, merge the type map of destination basic block with current type map   
   * 
   *
   * @param brBBNum the branch destination basic block number 
   *
   * @return nothing 
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void processBranchBB(short brBBNum) 
    throws VerifyError
  {


    MergeMaps(brBBNum, currBBMap, currBBStkTop);
    //####
    if (inJSRSub && currPendingJsr != null && bbPendingJsrs[brBBNum] == null)
      bbPendingJsrs[brBBNum] = currPendingJsr;

  }


  /**
   * computer type maps for the instructions right after "jsr" using currPendingJsr.
   * called from a "ret" is processed
   * 
   * @return nothing 
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void computeJSRNextMaps() 
    throws VerifyError
  {

    currPendingJsr.newEndMap(currBBMap, currBBStkTop);

    for (int i = 0; i < currPendingJsr.successorLength; ++i) {
      short successorBBNum = currPendingJsr.getSuccessorBBNum(i);               
      int[] preMap = currPendingJsr.getSuccessorPreMap(i);              
      int[] newMap = new int[currBBMap.length];
      boolean[] used = currPendingJsr.getUsedMap();
      for (int j =0; j <= currBBStkEmpty; j++) {
        if (used[j])
          newMap[j] = currBBMap[j];
        else
          newMap[j] = preMap[j];
      } 
      for (int j = currBBStkEmpty+1; j <= currBBStkTop; j++)
        newMap[j] = currBBMap[j];

      currPendingJsr.addUsedInfoToParent();

      MergeMaps(successorBBNum, newMap, currBBStkTop); 
    }
  }

  /**
   * add a new block number to the top of the work list
   *
   * @param blockNum the id number of the basic block to be added
   *
   * @return nothing 
   *
   */
  private void addToWorkStk(short blockNum) {
    workStkTop++;
    if (workStkTop >= workStk.length) {
      short[] biggerQ = new short[workStk.length + 20];
      for (int i = 0; i <workStk.length; i++) {
        biggerQ[i] = workStk[i];
      }
      workStk = biggerQ;
      biggerQ = null;
    }
    workStk[workStkTop] = blockNum;
    if (TRACE)
      VM.sysWrite("-----------add " + blockNum + " to worklist\n");
  }

  /*private void addUniqueToWorkStk(short blockNum) {
    if ((workStkTop+1) >= workStk.length) {
    short[] biggerQ = new short[workStk.length + 20];
    boolean matchFound = false;
    for (int i = 0; i <workStk.length; i++) {
    biggerQ[i] = workStk[i];
    matchFound =  (workStk[i] == blockNum);
    }
    workStk = biggerQ;
    biggerQ = null;
    if (matchFound) return ;
    }
    else {
    for (int i = 0; i <=workStkTop; i++) {
    if (workStk[i] == blockNum)
    return;
    }
    }
    workStkTop++;
    workStk[workStkTop] = blockNum;
    return;
    }
   */

  /**
   * get an integer from bytecodes
   *
   * @param index the bytecode index of opcode
   * @param bytecodes the method's bytecodes array
   *
   * @return the integer offset
   *
   */
  private int getIntOffset(int index, byte[] bytecodes) {
    return (int)((((int)bytecodes[index+1])<<(3*BITS_IN_BYTE)) |
                 ((((int)bytecodes[index+2])&0xFF)<<(2*BITS_IN_BYTE)) |
                 ((((int)bytecodes[index+3])&0xFF)<<BITS_IN_BYTE) |
                 (((int)bytecodes[index+4])&0xFF));
  }

  /**
   * find the common super class's dictionary id for two classes
   *
   * assumptions: 
   * 1.both id1 and id2 are reference type 
   * 2.they are not the same type
   * 3.none of them is null type
   *
   * @param id1 the type dictionary id for the first class
   * @param id2 the type dictionary id for the second class
   *
   * @return the common super class's dictionary id for the input classes
   */
  private int findCommonSuperClassId(int id1, int id2) 
  {
    VM_TypeReference tr1 = VM_TypeReference.getTypeRef(id1);
    VM_TypeReference tr2 = VM_TypeReference.getTypeRef(id2);
    VM_Type t1 =resolve(tr1), t2 = resolve(tr2);

    // Strip off all array junk.
    int arrayDimensions = 0;
    while (t1.isArrayType() && t2.isArrayType()) {
      ++arrayDimensions;
      t1 = ((VM_Array)t1).getElementType();
      t2 = ((VM_Array)t2).getElementType();
    }
      // at this point, they are not both array types.
      // if one is a primitive, then we want an object array of one less
      // dimensionality

    if (t1.isPrimitiveType() || t2.isPrimitiveType()) {
      VM_Type type = VM_Type.JavaLangObjectType;
      --arrayDimensions;
      while (arrayDimensions-- > 0)
        type = type.getArrayTypeForElementType();
      return  type.getTypeRef().getId();
    }

      // neither is a primitive, and they are not both array types.
    if (!t1.isClassType() || !t2.isClassType()) {
      // one is a class type, while the other isn't.
      VM_Type type = VM_Type.JavaLangObjectType;
      while (arrayDimensions-- > 0)
        type = type.getArrayTypeForElementType();
      return  type.getTypeRef().getId();
    }

      // they both must be class types.
      // technique: push heritage of each type on a separate stack,
      // then find the highest point in the stack where they differ.
      VM_Class c1 = (VM_Class)t1;
      VM_Class c2 = (VM_Class)t2;

      Stack s1 = new Stack();
      do {
        s1.push(c1);
        c1 = c1.getSuperClass();
      } while (c1 != null);

      Stack s2 = new Stack();
      do {
        s2.push(c2);
        c2 = c2.getSuperClass();
      }while (c2 != null);

      VM_Type best = VM_Type.JavaLangObjectType;
      while (!s1.empty() && !s2.empty()) {
        VM_Class temp = (VM_Class)s1.pop();
        if (temp == s2.pop())
          best = temp; 
        else 
          break;
      }
      while (arrayDimensions-- > 0)
        best = best.getArrayTypeForElementType();
      return  best.getTypeRef().getId();
    }

  /**
   * process bytecodes invokespecial, invokevirtual, invokestatic and
   *                   invokeinterface  
   *
   * @param calledMethod the called method's reference
   * @param isStatic   true if this method is static; false otherwise
   *
   * @return nothing
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void processInvoke(VM_MethodReference calledMethod, boolean isStatic)
    throws VerifyError 
  {

      VM_TypeReference[] parameterTypes = calledMethod.getParameterTypes();
      int paramNum = parameterTypes.length;

      //pop the arguments and check the type at the same time
      for (int i = paramNum-1; i>=0; i--) {
        int numOfWord = parameterTypes[i].getStackWords();
        //check stack underflow
        if (currBBStkTop-numOfWord < currBBStkEmpty) {
          verificationFailure("stack underflow");
        }
        //check parameter type
        boolean correct = true;
        if (parameterTypes[i].isIntLikeType())
          correct = (currBBMap[currBBStkTop] == V_INT);
        else if (parameterTypes[i].isFloatType())
          correct = (currBBMap[currBBStkTop] == V_FLOAT);
        else if (parameterTypes[i].isLongType())
          correct = (currBBMap[currBBStkTop] == V_LONG && currBBMap[currBBStkTop-1] == V_LONG );
        else if (parameterTypes[i].isDoubleType())
          correct = (currBBMap[currBBStkTop] == V_DOUBLE && currBBMap[currBBStkTop-1] == V_DOUBLE) ;
        else if (parameterTypes[i].isReferenceType())
          correct = (currBBMap[currBBStkTop] == V_NULL 
                     || VM_Runtime.isAssignableWith(resolve(parameterTypes[i]),
                                                    resolve(VM_TypeReference.getTypeRef(currBBMap[currBBStkTop]))));

        if (! correct) {
          verificationFailure("incompatible parameter when calling the method "
                              + calledMethod);
        }

        //pop this argument
        currBBStkTop -= numOfWord;
      }//end of for

      //if not static, check call object type
      if (!isStatic) {
        //check stack underflow
        if (currBBStkTop-1 < currBBStkEmpty) {
          verificationFailure("stack underflow");
        }
        
        //this isn't a reference type or isn't a compatible reference type
        if (currBBMap[currBBStkTop] < 0 
            || !VM_Runtime.isAssignableWith(
                    resolve(calledMethod).getDeclaringClass(),
                    resolve(VM_TypeReference.getTypeRef(
                               currBBMap[currBBStkTop])))) {
          verificationFailure("incompatible 'this' reference when 'call' to method "
                              + calledMethod);
        }

        if (calledMethod.getName() != VM_ClassLoader.StandardObjectInitializerMethodName) {
          if (newObjectInfo[currBBStkTop-currBBStkEmpty-1]) {
            //uninitialized object
            verificationFailure("uninitialized object reference when calling the method " + 
                                calledMethod);
          }
        } else { //set the new object to be initialized
          if ( newObjectInfo[currBBStkTop - currBBStkEmpty -1]) {
            newObjectInfo[currBBStkTop - currBBStkEmpty -1] = false;
            if ((currBBStkTop-currBBStkEmpty) >= 2)
              newObjectInfo[currBBStkTop - currBBStkEmpty -2] = false;
          }
        }
        //pop this reference
        currBBStkTop--;
      } //end if static

      //add the return type to the stack
      VM_TypeReference returnType = calledMethod.getReturnType();
      if (!returnType.isVoidType()) {
        currBBStkTop += returnType.getStackWords();
        //check stack overflow
        if (currBBStkTop >= currBBMap.length) {
          verificationFailure("stack overflow");
        }

        if (returnType.isIntLikeType())
          currBBMap[currBBStkTop] = V_INT;
        else if (returnType.isFloatType())
          currBBMap[currBBStkTop] = V_FLOAT;
        else if (returnType.isLongType())
          currBBMap[currBBStkTop] = currBBMap[currBBStkTop-1] = V_LONG ;
        else if (returnType.isDoubleType())
          currBBMap[currBBStkTop] = currBBMap[currBBStkTop-1] = V_DOUBLE ;
        else if (returnType.isReferenceType())
          currBBMap[currBBStkTop] = returnType.getId(); 
      }

    }

  /**
   * used for basic blocks in try...catch... 
   * The type map of exception handler's must be the result of merging all possible paths to this handler.
   *
   * @param newType the new type need to be merged
   * @param localVariable the index of the register whose type is going to be merged
   * @param wordCount the new type's word number
   * @param reachablehandlerBBNums  the basic block id numbers of all handlers reachable from current basic block
   * @param reachablehandlersCount  the number of all handlers reachable from current basic block
   * 
   * @return nothing
   *
   * @exception VerifyError
   *        If the verifier catches any error in the byte code, it will throw
   *        an instance of VerifyError.  This includes a failure to load some
   *        class B that <code>method</code> uses.
   */
  private void setHandlersMaps(int newType, int localVariable, 
                               int wordCount, int[] reachableHandlerBBNums, 
                               int reachableHandlersCount)  
    throws VerifyError
  {

    for (int i = 0; i <reachableHandlersCount; i++) {
      for (int j = 0; j < wordCount; j++) {
        int originalType = typeMaps[reachableHandlerBBNums[i]][localVariable +j];
        if ( originalType == newType)
          continue;
        typeMaps[reachableHandlerBBNums[i]][localVariable+j] = MergeOneCell(newType, originalType);
      }
    }
  }
}
