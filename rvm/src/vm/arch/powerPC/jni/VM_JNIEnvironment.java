/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import java.lang.reflect.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;
import com.ibm.JikesRVM.classloader.*;

/**
 * Platform dependent aspects of the JNIEnvironment.
 *
 * @author Dave Grove
 * @author Ton Ngo 
 * @author Steve Smith
 */
public final class VM_JNIEnvironment extends VM_JNIGenericEnvironment implements VM_JNIAIXConstants {

  /**
   * This is the JNI function table, the address of this array will be
   * passed to the native code
   * TODO: This is horrible and must be rewritten.
   *       These are NOT int[][]'s by any stretch of the imagination!!!!
   */
  //-#if RVM_FOR_LINUX
  private static int[][]   JNIFunctions;
  //-#elif RVM_FOR_AIX
  private static int[][][] JNIFunctions;
  //-#endif
  
  /**
   * This is a table of pointers to the shared JNI function table.  All entries 
   * point to the same function table.  Each thread uses the pointer at its thread id
   * offset to allow us to determine a threads id from the pointer it is using.
   * Needed when native calls Java (JNIFunctions) and passes its JNIEnv pointer.
   * Its offset into the JNIFunctionPts array is the same as the threads offset
   * in the Scheduler.threads array.
   */
  static int[] JNIFunctionPointers;        // made public so vpStatus could be set 11/16/00 SES
                                           // maybe need set & get functions ??

  // allocate the first dimension of the function array in the boot image so that
  // we have an address pointing to it.  This is necessary for thread creation
  // since the VM_JNIEnvironment object will contain a field pointing to this array
  public static void init() {
    //-#if RVM_FOR_LINUX
    JNIFunctions = new int[FUNCTIONCOUNT+1][];
    //-#elif RVM_FOR_AIX
    JNIFunctions = new int[FUNCTIONCOUNT][][];
    //-#endif
	
    // 2 words for each thread
    JNIFunctionPointers = new int[VM_Scheduler.MAX_THREADS * 2];
  }

  /**
   *  Initialize the array of JNI functions
   *  To be called from VM_DynamicLibrary.java when a library is loaded,
   *  expecting native calls to be made
   */
  public static void boot() {

    if (initialized)
      return;

    // fill an array of JNI names
    setNames();

    //-#if RVM_FOR_AIX
    // fill in the TOC entries for each AIX linkage triplet
    for (int i=0; i<JNIFunctions.length; i++) {
      JNIFunctions[i] = new int[3][];
      JNIFunctions[i][TOC] = VM_Statics.getSlotsAsIntArray();   // the JTOC value: address of TOC
    }
    //-#endif

    // fill in the IP entries for each AIX linkage triplet
    VM_TypeReference tRef = VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(), 
							  VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_JNIFunctions;"));
    VM_Class cls = (VM_Class)tRef.peekResolvedType();
    VM_Method[] mths = cls.getDeclaredMethods();
    // VM.sysWrite("VM_JNIEnvironment:  scanning " + mths.length + " methods\n");
    for (int i=0; i<mths.length; i++) {
      String methodName = mths[i].getName().toString();
      int jniIndex = indexOf(methodName);
      if (jniIndex!=-1) {
	//-#if RVM_FOR_LINUX
	JNIFunctions[jniIndex]     = mths[i].getCurrentInstructions();
	//-#elif RVM_FOR_AIX
	// GACK.  We need this horrible kludge because the array is not well typed.
	Object array = JNIFunctions[jniIndex];
	VM_Magic.setObjectAtOffset(array, IP, mths[i].getCurrentInstructions());
	//-#endif
      } 
    }

    //-#if RVM_FOR_AIX
    VM_Address functionAddress = VM_Magic.objectAsAddress(JNIFunctions[NEWINTARRAY][IP]);
    // VM.sysWrite("   NewIntArray is at " + VM.intAsHexString(functionAddress) + "\n");
    functionAddress = VM_Magic.objectAsAddress(JNIFunctions[NEWINTARRAY][TOC]);
    // VM.sysWrite("   TOC is stored at " + VM.intAsHexString(functionAddress) + "\n");
    //-#endif

    //-#if RVM_FOR_LINUX
    // set JTOC content, how about GC ? will it move JTOC ?
    VM_Magic.setMemoryAddress(VM_Magic.objectAsAddress(JNIFunctions).add(JNIFUNCTIONS_JTOC_OFFSET),
			      VM_Magic.getTocPointer());
    //-#endif
    
    initialized = true;
  }

  // Instance:  create a thread specific JNI environment.  threadSlot = creating threads
  // thread id == index of its entry in Scheduler.threads array
  //
  public VM_JNIEnvironment(int threadSlot) {
    // as of 8/22 SES - let JNIEnvAddress be the address of the JNIFunctionPtr to be
    // used by the creating thread.  Passed as first arg (JNIEnv) to native C functions.

    // uses 2 words for each thread, the first is the function pointer
    // to be used when making native calls
    JNIFunctionPointers[threadSlot * 2] = VM_Magic.objectAsAddress(JNIFunctions).toInt();
    JNIFunctionPointers[(threadSlot * 2)+1] = 0;  // later contains addr of processor vpStatus word
    JNIEnvAddress = VM_Magic.objectAsAddress(JNIFunctionPointers).add(threadSlot*8);
  }

  public int[] getInstructions(int id) {    
    //-#if RVM_FOR_AIX
    return JNIFunctions[id][IP];
    //-#elif RVM_FOR_LINUX
    return JNIFunctions[id];
    //-#endif
  }

  /*****************************************************************************
   * Utility function called from VM_JNIFunction
   * (cannot be placed in VM_JNIFunction because methods there are specially compiled
   * to be called from native)
   *****************************************************************************/
  

  /**
   * Common code shared by the JNI functions NewObjectA, NewObjectV, NewObject
   * (object creation)
   * @param methodID the method ID for a constructor
   * @return a new object created by the specified constructor
   */
  public static Object invokeInitializer(Class cls, int methodID, VM_Address argAddress, 
					 boolean isJvalue, boolean isDotDotStyle) 
    throws Exception {

    // get the parameter list as Java class
    VM_Method mth = VM_MemberReference.getMemberRef(methodID).asMethodReference().resolve();
    VM_TypeReference[] argTypes = mth.getParameterTypes();
    Class[]   argClasses = new Class[argTypes.length];
    for (int i=0; i<argClasses.length; i++) {
      argClasses[i] = argTypes[i].resolve().getClassForType();
    }

    Constructor constMethod = cls.getConstructor(argClasses);
    if (constMethod==null)
      throw new Exception("Constructor not found");
    
    Object argObjs[];

    if (isJvalue) {
      argObjs = packageParameterFromJValue(mth, argAddress);
    } else {
      if (isDotDotStyle) {
	//-#if RVM_FOR_AIX
	VM_Address varargAddress = pushVarArgToSpillArea(methodID, false);
	argObjs = packageParameterFromVarArg(mth, varargAddress);
	//-#endif
	
	//-#if RVM_FOR_LINUX
	// pass in the frame pointer of glue stack frames
	// stack frame looks as following:
	//      this method -> 
	//
	//      native to java method ->
	//
	//      glue frame ->
	//
	//      native C method ->
	VM_Address gluefp = VM_Magic.getCallerFramePointer(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer())); 
	argObjs = packageParameterFromDotArgSVR4(mth, gluefp, false);
	//-#endif
      } else {
	// var arg
	//-#if RVM_FOR_AIX
	argObjs = packageParameterFromVarArg(mth, argAddress);
	//-#endif
	//-#if RVM_FOR_LINUX
	argObjs = packageParameterFromVarArgSVR4(mth, argAddress);
	//-#endif
      }
    }

    // construct the new object
    return constMethod.newInstance(argObjs);
  }


  /**
   * Common code shared by the JNI functions CallStatic<type>Method
   * (static method invocation)
   * @param methodID the method ID
   * @param expectReturnType the return type of the method to be invoked
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithDotDotVarArg(int methodID, 
					      VM_TypeReference expectReturnType)
    throws Exception {

    //-#if RVM_FOR_AIX
    VM_Address varargAddress = pushVarArgToSpillArea(methodID, false);    
    return packageAndInvoke(null, methodID, varargAddress, expectReturnType, false, AIX_VARARG);
    //-#endif

    //-#if RVM_FOR_LINUX
    VM_Address glueFP = VM_Magic.getCallerFramePointer(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()));
    return packageAndInvoke(null, methodID, glueFP, expectReturnType, false, SVR4_DOTARG);
    //-#endif
  }

  /**
   * Common code shared by the JNI functions Call<type>Method
   * (virtual method invocation)
   * @param obj the object instance 
   * @param methodID the method ID
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args  true if the calling JNI Function takes 4 args before the vararg
   *                   false if the calling JNI Function takes 3 args before the vararg
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithDotDotVarArg(Object obj, int methodID, 
					      VM_TypeReference expectReturnType, boolean skip4Args)
    throws Exception {

    //-#if RVM_FOR_AIX
    VM_Address varargAddress = pushVarArgToSpillArea(methodID, skip4Args);    
    return packageAndInvoke(obj, methodID, varargAddress, expectReturnType, skip4Args, AIX_VARARG);
    //-#endif

    //-#if RVM_FOR_LINUX
    VM_Address glueFP = VM_Magic.getCallerFramePointer(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()));
    return packageAndInvoke(obj, methodID, glueFP, expectReturnType, skip4Args, SVR4_DOTARG);
    //-#endif
  }


  /**
   * This method supports var args passed from C
   *
   * In the AIX C convention, the caller keeps the first 8 words in registers and 
   * the rest in the spill area in the caller frame.  The callee will push the values
   * in registers out to the spill area of the caller frame and use the beginning 
   * address of this spill area as the var arg address
   *
   * For the JNI functions that takes var args, their prolog code will save the
   * var arg in the glue frame because the values in the register may be lost by 
   * subsequent calls.
   *
   * This method copies the var arg values that were saved earlier in glue frame into
   * the spill area of the original caller, thereby doing the work that the callee
   * normally performs in the AIX C convention.
   *
   * NOTE: This method contains internal stack pointer.
   * For now we assume that the stack will not be relocatable while native code is running
   * because native code can hold an address into the stack, so this code is OK,
   * but this is an issue to be resolved later
   *
   * NOTE:  this method assumes that it is immediately above the 
   * invokeWithDotDotVarArg frame, the JNI frame, the glue frame and 
   * the C caller frame in the respective order.  
   * Therefore, this method will not work if called from anywhere else
   *
   *
   *
   *   |  fp  | <- VM_JNIEnvironment.pushVarArgToSpillArea
   *   | mid  |
   *   | xxx  |
   *   |      |
   *   |      |
   *   |------|   
   *   |  fp  | <- VM_JNIEnvironment.invokeWithDotDotVarArg frame
   *   | mid  |
   *   | xxx  |
   *   |      |
   *   |      |
   *   |      |
   *   |------|   
   *   |  fp  | <- JNI method frame
   *   | mid  |
   *   | xxx  |
   *   |      |
   *   |      |
   *   |      |
   *   |------|
   *   |  fp  | <- glue frame
   *   | mid  |
   *   + xxx  +
   *   | r3   |   volatile save area
   *   | r4   |
   *   | r5   |
   *   | r6   |   vararg GPR[6-10]save area   <- VARARG_AREA_OFFSET
   *   | r7   |
   *   | r8   |
   *   | r9   |
   *   | r10  |
   *   | fpr1 |   vararg FPR[1-3] save area (also used as volatile FPR[1-6] save area)
   *   | fpr2 |
   *   | fpr3 |
   *   | fpr4 |
   *   | fpr5 |
   *   + fpr6 +
   *   | r13  |   nonvolatile GPR[13-31] save area
   *   | ...  |
   *   + r31  +
   *   | fpr14|   nonvolatile FPR[14-31] save area
   *   | ...  |
   *   | fpr31|
   *   |topjav|   offset to preceding Java to C glue frame
   *   |------|  
   *   | fp   | <- Native C caller frame
   *   | cr   |
   *   | lr   |
   *   | resv |
   *   | resv |
   *   + toc  +
   *   |   0  |    spill area initially not filled
   *   |   1  |    to be filled by this method
   *   |   2  |
   *   |   3  |
   *   |   4  |
   *   |   5  |
   *   |   6  |
   *   |   7  |
   *   |   8  |    spill area already filled by caller
   *   |   9  |
   *   |      |
   *   |      |
   *   |      |
   *
   * @param methodID a VM_MemberReference id
   * @param skip4Args if true, the calling JNI function has 4 args before the vararg
   *                  if false, the calling JNI function has 3 args before the vararg
   * @return the starting address of the vararg in the caller stack frame
   */
  private static VM_Address pushVarArgToSpillArea(int methodID, boolean skip4Args) throws Exception {

    int glueFrameSize = JNI_GLUE_FRAME_SIZE;

    // get the FP for this stack frame and traverse 2 frames to get to the glue frame
    VM_Address gluefp = VM_Magic.getMemoryAddress(VM_Magic.getFramePointer().add(VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET));
    gluefp = VM_Magic.getMemoryAddress(gluefp.add(VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET));
    gluefp = VM_Magic.getMemoryAddress(gluefp.add(VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET));

    // compute the offset into the area where the vararg GPR[6-10] and FPR[1-3] are saved
    // skipping the args which are not part of the arguments for the target method
    // For Call<type>Method functions and NewObject, skip 3 args
    // For CallNonvirtual<type>Method functions, skip 4 args
    int varargGPROffset = VARARG_AREA_OFFSET + (skip4Args ? 4 : 0);
    int varargFPROffset = varargGPROffset + 5*4 ;

    // compute the offset into the spill area of the native caller frame, 
    // skipping the args which are not part of the arguments for the target method
    // For Call<type>Method functions, skip 3 args
    // For CallNonvirtual<type>Method functions, skip 4 args
    int spillAreaLimit  = glueFrameSize + NATIVE_FRAME_HEADER_SIZE + 8*4;
    int spillAreaOffset = glueFrameSize + NATIVE_FRAME_HEADER_SIZE + 
                          (skip4Args ? 4*4 : 3*4);

    // address to return pointing to the var arg list
    VM_Address varargAddress = gluefp.add(spillAreaOffset);

    // VM.sysWrite("pushVarArgToSpillArea:  var arg at " + 
    // 		   VM.intAsHexString(varargAddress) + "\n");
 
    VM_Method targetMethod = VM_MemberReference.getMemberRef(methodID).asMethodReference().resolve();
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;

    for (int i=0; i<argCount && spillAreaOffset<spillAreaLimit ; i++) {
      int hiword, loword;

      if (argTypes[i].isFloatType() || argTypes[i].isDoubleType()) {
	// move 2 words from the vararg FPR save area into the spill area of the caller
	hiword = VM_Magic.getMemoryInt(gluefp.add(varargFPROffset));
	varargFPROffset+=4;
	loword = VM_Magic.getMemoryInt(gluefp.add(varargFPROffset));
	varargFPROffset+=4;
	VM_Magic.setMemoryInt(gluefp.add(spillAreaOffset), hiword);
	spillAreaOffset+=4;
	VM_Magic.setMemoryInt(gluefp.add(spillAreaOffset), loword);
	spillAreaOffset+=4;
      } 

      else if (argTypes[i].isLongType()) {
	// move 2 words from the vararg GPR save area into the spill area of the caller
	hiword = VM_Magic.getMemoryInt(gluefp.add(varargGPROffset));
	varargGPROffset+=4;
	VM_Magic.setMemoryInt(gluefp.add(spillAreaOffset), hiword);
	spillAreaOffset+=4;
	// this covers the case when the long value straddles the spill boundary
	if (spillAreaOffset<spillAreaLimit) {
	  loword = VM_Magic.getMemoryInt(gluefp.add(varargGPROffset));
	  varargGPROffset+=4;
	  VM_Magic.setMemoryInt(gluefp.add(spillAreaOffset), loword);
	  spillAreaOffset+=4;
	}
      }

      else {
	hiword = VM_Magic.getMemoryInt(gluefp.add(varargGPROffset));
	varargGPROffset+=4;
	VM_Magic.setMemoryInt(gluefp.add(spillAreaOffset), hiword);
	spillAreaOffset+=4;
      }

    }

    // At this point, all the vararg values should be in the spill area in the caller frame
    // return the address of the beginning of the vararg to use in invoking the target method
    return varargAddress;

  }

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodV
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithVarArg(int methodID, VM_Address argAddress, 
					VM_TypeReference expectReturnType) 
    throws Exception {
    //-#if RVM_FOR_AIX
    return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, AIX_VARARG);
    //-#endif

    //-#if RVM_FOR_LINUX
    return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, SVR4_VARARG);
    //-#endif
  }

  /**
   * Common code shared by the JNI functions Call<type>MethodV
   * @param obj the object instance 
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args received from the JNI function, passed on to VM_Reflection.invoke()
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithVarArg(Object obj, int methodID, VM_Address argAddress, 
					VM_TypeReference expectReturnType, 
					boolean skip4Args) 
    throws Exception {

    //-#if RVM_FOR_AIX
    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, AIX_VARARG);
    //-#endif

    //-#if RVM_FOR_LINUX
    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, SVR4_VARARG);
    //-#endif
  }

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodA
   * @param methodID a VM_MemberReference id
   * @param argAddress a raw address for the argument array
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithJValue(int methodID, VM_Address argAddress, 
					VM_TypeReference expectReturnType) 
    throws Exception {
    return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, JVALUE_ARG);
  }

  /**
   * Common code shared by the JNI functions Call<type>MethodA
   * @param obj the object instance 
   * @param methodID a VM_MemberReference id
   * @param argAddress a raw address for the argument array
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args received from the JNI function, passed on to VM_Reflection.invoke()
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithJValue(Object obj, int methodID, VM_Address argAddress, 
					VM_TypeReference expectReturnType, 
					boolean skip4Args) 
    throws Exception {
    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, JVALUE_ARG);
  }

  public static final int SVR4_DOTARG = 0;         // Linux/PPC SVR4 normal
  public static final int AIX_DOTARG  = 1;         // AIX normal 
  public static final int JVALUE_ARG  = 2;         // javlue
  public static final int SVR4_VARARG = 3;         // Linux/PPC SVR4 vararg
  public static final int AIX_VARARG  = 4;         // AIX vararg

  /**
   * Common code shared by invokeWithJValue, invokeWithVarArg and invokeWithDotDotVarArg
   * @param obj the object instance 
   * @param methodID a VM_MemberReference id
   * @param argAddress a raw address for the argument array
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args This flag is received from the JNI function and passed directly to 
   *                     VM_Reflection.invoke().  
   *                     It is true if the actual method is to be invoked, which could be
   *                     from the superclass.
   *                     It is false if the method from the real class of the object 
   *                     is to be invoked, which may not be the actual method specified by methodID
   * @param isVarArg  This flag describes whether the array of parameters is in var arg format or
   *                  jvalue format
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object packageAndInvoke(Object obj, int methodID, VM_Address argAddress, 
					VM_TypeReference expectReturnType, 
					boolean skip4Args, int argtype) 
    throws Exception {
  
    // VM.sysWrite("JNI CallXXXMethod:  method ID " + methodID + " with args at " + 
    // 		   VM.intAsHexString(argAddress) + "\n");
    
    VM_Method targetMethod = VM_MemberReference.getMemberRef(methodID).asMethodReference().resolve();
    VM_TypeReference returnType = targetMethod.getReturnType();

    // VM.sysWrite("JNI CallXXXMethod:  " + targetMethod.getDeclaringClass().toString() +
    //		"." + targetMethod.getName().toString() + "\n");

    if (expectReturnType==null) {   // for reference return type 
      if (!returnType.isReferenceType())
	throw new Exception("Wrong return type for method: expect reference type instead of " + returnType);      
    } 
    else {    // for primitive return type
      if (returnType!=expectReturnType) 
	throw new Exception("Wrong return type for method: expect " + expectReturnType + 
			    " instead of " + returnType);
    }  

    // Repackage the arguments into an array of objects based on the signature of this method
    Object[] argObjectArray;
    
    switch (argtype) {
      //-#if RVM_FOR_LINUX
    case SVR4_DOTARG:
      // argAddress is the glue frame pointer
      argObjectArray = packageParameterFromDotArgSVR4(targetMethod, argAddress, skip4Args);
      break;
      //-#endif
    case JVALUE_ARG:
      argObjectArray = packageParameterFromJValue(targetMethod, argAddress);
      break;
      //-#if RVM_FOR_LINUX
    case SVR4_VARARG:
      argObjectArray = packageParameterFromVarArgSVR4(targetMethod, argAddress);
      break;
      //-#endif
    case AIX_VARARG:
      argObjectArray = packageParameterFromVarArg(targetMethod, argAddress);
      break;
    default:
      argObjectArray = null;
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }

    // now invoke the method
    Object returnObj = VM_Reflection.invoke(targetMethod, obj, argObjectArray, skip4Args);
    
    return returnObj;
  }


  //-#if RVM_FOR_LINUX 
  /* The method reads out parameters from registers saved in native->java glue stack frame (glueFP)
   * and the spill area of native stack frame (caller of glueFP).
   * 
   * NOTE: assuming the stack frame won't get moved, (see pushVarArgToSpillArea)
   *       the row address glueFP can be replaced by offset to the stack.
   *
   * @param targetMethod, the call target
   * @param glueFP, the glue stack frame pointer
   */
  static Object[] packageParameterFromDotArgSVR4(VM_Method targetMethod, VM_Address glueFP, boolean skip4Args) {
    // native method's stack frame
    VM_Address nativeFP = VM_Magic.getCallerFramePointer(glueFP);
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();

    // GPR r3 - r10 and FPR f1 - f8 are saved in glue stack frame
    VM_Address regsavearea = glueFP.add(VM_StackframeLayoutConstants.STACKFRAME_HEADER_SIZE);

    // spill area offset
    VM_Address overflowarea = nativeFP.add(NATIVE_FRAME_HEADER_SIZE);
    
    // overflowarea is aligned to 8 bytes
    if (VM.VerifyAssertions) VM._assert((overflowarea.toInt() & 0x07) == 0);
    
    // adjust gpr and fpr to normal numbering, make life easier
    int gpr = (skip4Args) ? 7:6;       // r3 - env, r4 - cls, r5 - method id
    int fpr = 1;
    
    // not set the starting gprs array address 
    // and fpr starting array address, so we can use gpr and fpr to 
    // calculate the right position to get values
    // GPR starts with r3;
    VM_Address gprarray = regsavearea.add(-3*4); 
    VM_Address fprarray = regsavearea.add(8*4-2*4);

    // call the common function for SVR4
    packageArgumentForSVR4(argTypes, argObjectArray, gprarray, fprarray, overflowarea, gpr, fpr, env); 
    
    return argObjectArray;
  }

  // linux has totally different layout of va_list
  // see /usr/lib/gcc-lib/powerpc-linux/xxxx/include/va-ppc.h
  //
  // va_list is defined as following
  //
  // struct {
  //   char unsigned gpr;    // compiled to 1 byte, index of gprs in saved area
  //                         // 0 -> r3, 1 -> r4, ....
  //   char unsigned fpr;    // compiled to 1 byte, index to fprs in saved area
  //                         // 0 -> fr1, 1 -> fr2, ....
  //   char * over_flow_area;
  //   char * reg_save_area;
  // }
  //
  // The interpretation of data can be found in PowerPC Processor ABI Supplement
  //
  // The reg_save area lays out r3 - r10, f1 - f8
  // 
  // I am not sure if GCC understand the ABI in a right way, it saves GPRs 1 - 10
  // in the area, while only gprs starting from r3 are used.
  //
  // -- Feng
  // 
  static Object[] packageParameterFromVarArgSVR4(VM_Method targetMethod, VM_Address argAddress) {
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];
    
    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();
    
    // the va_list has following layout on PPC/Linux
    // GPR FPR 0 0   (4 bytes)
    // overflowarea  (pointer)
    // reg_save_area (pointer)
    VM_Address va_list_addr = argAddress;
    int word1 = VM_Magic.getMemoryInt(va_list_addr);
    int gpr = word1 >> 24;
    int fpr = (word1 >> 16) & 0x0FF;
    va_list_addr = va_list_addr.add(4);
    VM_Address overflowarea = VM_Magic.getMemoryAddress(va_list_addr);
    va_list_addr = va_list_addr.add(4);
    VM_Address regsavearea = VM_Magic.getMemoryAddress(va_list_addr);
    
    // overflowarea is aligned to 8 bytes
    if (VM.VerifyAssertions) VM._assert((overflowarea.toInt() & 0x07) == 0);
    
    // adjust gpr and fpr to normal numbering, make life easier
    gpr += 3;
    fpr += 1;
    
    // not set the starting gprs array address 
    // and fpr starting array address, so we can use gpr and fpr to 
    // calculate the right position to get values
    // GPR starts with r3;
    VM_Address gprarray = regsavearea.add(-3*4); 
    VM_Address fprarray = regsavearea.add(8*4-2*4);
    
    // call the common function for SVR4
    packageArgumentForSVR4(argTypes, argObjectArray, gprarray, fprarray, overflowarea, gpr, fpr, env); 

    return argObjectArray;
  }

  static void packageArgumentForSVR4(VM_TypeReference[] argTypes, Object[] argObjectArray,
				     VM_Address gprarray, VM_Address fprarray,
				     VM_Address overflowarea, int gpr, int fpr,
				     VM_JNIEnvironment env) {
    // also make overflow offset, we may need to round it
    int overflowoffset = 0;
    int argCount = argTypes.length;

    // now interpret values by types, see PPC ABI
    for (int i=0; i<argCount; i++) {
      if (argTypes[i].isFloatType()
	  || argTypes[i].isDoubleType()) {
	int loword, hiword;
	if (fpr > LAST_OS_PARAMETER_FPR) {
	  // overflow, OTHER
	  // round it, bytes are saved from lowest to highest one, regardless endian
	  overflowoffset = (overflowoffset + 7) & -8;
	  hiword = VM_Magic.getMemoryInt(overflowarea.add(overflowoffset));
	  overflowoffset += BYTES_IN_INT;
	  loword = VM_Magic.getMemoryInt(overflowarea.add(overflowoffset));
	  overflowoffset += BYTES_IN_INT;
	} else {
	  // get value from fpr, increase fpr by 1
	  hiword = VM_Magic.getMemoryInt(fprarray.add(fpr*BYTES_IN_DOUBLE));
	  loword = VM_Magic.getMemoryInt(fprarray.add(fpr*BYTES_IN_DOUBLE + BYTES_IN_INT));
	  fpr += 1;
	}
	long doubleBits = (((long)hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
	if (argTypes[i].isFloatType()) {
	  argObjectArray[i] = VM_Reflection.wrapFloat((float)(Double.longBitsToDouble(doubleBits)));
	} else { // double type
	  argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));
	}
	
	//		VM.sysWriteln("double "+Double.longBitsToDouble(doubleBits));
	
      } else if (argTypes[i].isLongType()) {
	int loword, hiword;
	if (gpr > LAST_OS_PARAMETER_GPR-1) {
	  // overflow, OTHER
	  // round overflowoffset, assuming overflowarea is aligned to 8 bytes
	  overflowoffset = (overflowoffset + 7) & -8;
	  hiword = VM_Magic.getMemoryInt(overflowarea.add(overflowoffset));
	  overflowoffset += BYTES_IN_INT;
	  loword = VM_Magic.getMemoryInt(overflowarea.add(overflowoffset));
	  overflowoffset += BYTES_IN_INT;
	  
	  // va-ppc.h makes last gpr useless
	  gpr = 11;
	} else {
	  gpr += (gpr + 1) & 0x01;  // if gpr is even, gpr += 1
	  hiword = VM_Magic.getMemoryInt(gprarray.add(gpr*4));
	  loword = VM_Magic.getMemoryInt(gprarray.add((gpr+1)*4));
	  gpr += 2;
	}
	long longBits = (((long)hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapLong(longBits);
	
	//		VM.sysWriteln("long 0x"+Long.toHexString(longBits));
      } else {
	// int type left now
	int ivalue;
	if (gpr > LAST_OS_PARAMETER_GPR) {
	  // overflow, OTHER
	  ivalue = VM_Magic.getMemoryInt(overflowarea.add(overflowoffset));
	  overflowoffset += 4;
	} else {
	  ivalue = VM_Magic.getMemoryInt(gprarray.add(gpr*4));
	  gpr += 1;
	} 
	
	//		VM.sysWriteln("int "+ivalue);
	
	if (argTypes[i].isBooleanType()) {
	  argObjectArray[i] = VM_Reflection.wrapBoolean(ivalue);
	} else if (argTypes[i].isByteType()) {
	  argObjectArray[i] = VM_Reflection.wrapByte((byte)ivalue);
	} else if (argTypes[i].isShortType()) {
	  argObjectArray[i] = VM_Reflection.wrapShort((short)ivalue);
	} else if (argTypes[i].isCharType()) {
	  argObjectArray[i] = VM_Reflection.wrapChar((char)ivalue);
	} else if (argTypes[i].isIntType()) {
	  argObjectArray[i] = VM_Reflection.wrapInt(ivalue);
	} else if (argTypes[i].isReferenceType()) {
	  argObjectArray[i] = env.getJNIRef(ivalue);
	} else {
	  if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
	}
      }
    }
  }
  //-#endif  RVM_FOR_LINUX


  /**
   * Repackage the arguments passed as a variable argument list into an array of Object,
   * used by the JNI functions CallStatic<type>MethodV
   * @param mth the target VM_Method
   * @param argAddress an address into the C space for the array of jvalue unions;  
   *                   each element is 2-word and holds the argument of the appropriate type
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParameterFromVarArg(VM_Method targetMethod, VM_Address argAddress) {
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    // get the VM_JNIEnvironment for this thread in case we need to dereference any object arg
    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();

    // VM.sysWrite("JNI packageParameterFromVarArg: packaging " + argCount + " arguments\n");

    VM_Address addr = argAddress;
    for (int i=0; i<argCount; i++) {
      int loword, hiword;
      hiword = VM_Magic.getMemoryInt(addr);

      // VM.sysWrite("JNI packageParameterFromVarArg:  arg " + i + " = " + hiword + 
      // " or " + VM.intAsHexString(hiword) + "\n");

      addr = addr.add(4);

      // convert and wrap the argument according to the expected type

      if (argTypes[i].isFloatType()) {
	// NOTE:  in VarArg convention, C compiler will expand a float to a double that occupy 2 words
	// so we have to extract it as a double and convert it back to a float
	loword = VM_Magic.getMemoryInt(addr);
	addr = addr.add(4);                       
	long doubleBits = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapFloat((float) (Double.longBitsToDouble(doubleBits)));
      } else if (argTypes[i].isDoubleType()) {
	loword = VM_Magic.getMemoryInt(addr);
	addr = addr.add(4);
	long doubleBits = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));
      } else if (argTypes[i].isLongType()) { 
	loword = VM_Magic.getMemoryInt(addr);
	addr = addr.add(4);
	long longValue = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapLong(longValue);
      } else if (argTypes[i].isBooleanType()) {
	// the 0/1 bit is stored in the high byte	
	argObjectArray[i] = VM_Reflection.wrapBoolean(hiword);
      } else if (argTypes[i].isByteType()) {
	// the target byte is stored in the high byte
	argObjectArray[i] = VM_Reflection.wrapByte((byte) hiword);
      } else if (argTypes[i].isCharType()) {
	// char is stored in the high 2 bytes
	argObjectArray[i] = VM_Reflection.wrapChar((char) hiword);
      } else if (argTypes[i].isShortType()) {
	// short is stored in the high 2 bytes
	argObjectArray[i] = VM_Reflection.wrapShort((short) hiword);
      } else if (argTypes[i].isReferenceType()) {
	// for object, the arg is a JREF index, dereference to get the real object
	argObjectArray[i] =  env.getJNIRef(hiword);   
      } else if (argTypes[i].isIntType()) {
	argObjectArray[i] = VM_Reflection.wrapInt(hiword);
      } else {
	return null;
      }
    }
    return argObjectArray;
  }

  /**
   * Repackage the arguments passed as an array of jvalue into an array of Object,
   * used by the JNI functions CallStatic<type>MethodA
   * @param mth the target VM_Method
   * @param argAddress an address into the C space for the array of jvalue unions;  
   *                   each element is 2-word and holds the argument of the appropriate type
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParameterFromJValue(VM_Method targetMethod, VM_Address argAddress) {
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    // get the VM_JNIEnvironment for this thread in case we need to dereference any object arg
    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();

    // VM.sysWrite("JNI packageParameterFromJValue: packaging " + argCount + " arguments\n");

    for (int i=0; i<argCount; i++) {
      VM_Address addr = argAddress.add(8*i);
      int hiword = VM_Magic.getMemoryInt(addr);
      int loword = VM_Magic.getMemoryInt(addr.add(4));

      // VM.sysWrite("JNI packageParameterFromJValue:  arg " + i + " = " + hiword + 
      //	  " or " + VM.intAsHexString(hiword) + "\n");

      // convert and wrap the argument according to the expected type

      if (argTypes[i].isFloatType()) {
	argObjectArray[i] = VM_Reflection.wrapFloat(Float.intBitsToFloat(hiword));
      } else if (argTypes[i].isDoubleType()) {
	long doubleBits = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));
      } else if (argTypes[i].isLongType()) { 
	long longValue = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapLong(longValue);
      } else if (argTypes[i].isBooleanType()) {
	// the 0/1 bit is stored in the high byte	
	argObjectArray[i] = VM_Reflection.wrapBoolean((hiword & 0xFF000000) >>> 24);
      } else if (argTypes[i].isByteType()) {
	// the target byte is stored in the high byte
	argObjectArray[i] = VM_Reflection.wrapByte((byte) ((hiword & 0xFF000000) >>> 24));
      } else if (argTypes[i].isCharType()) {
	// char is stored in the high 2 bytes
	argObjectArray[i] = VM_Reflection.wrapChar((char) ((hiword & 0xFFFF0000) >>> 16));
      } else if (argTypes[i].isShortType()) {
	// short is stored in the high 2 bytes
	argObjectArray[i] = VM_Reflection.wrapShort((short) ((hiword & 0xFFFF0000) >>> 16));
      } else if (argTypes[i].isReferenceType()) {
	// for object, the arg is a JREF index, dereference to get the real object
	argObjectArray[i] =  env.getJNIRef(hiword);   
      } else if (argTypes[i].isIntType()) {
	argObjectArray[i] = VM_Reflection.wrapInt(hiword);
      } else {
	return null;
      }
    }
    return argObjectArray;
  }
}
