/*
 * (C) Copyright IBM Corp. 2001,2003, 2004
 */
//$Id$
package com.ibm.JikesRVM.jni;

import java.lang.reflect.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Platform dependent utility functions called from VM_JNIFunctions
 * (cannot be placed in VM_JNIFunctions because methods 
 * there are specially compiled to be called from native).
 * 
 * @see VM_JNIFunctions
 *
 * @author Dave Grove
 * @author Ton Ngo 
 * @author Steve Smith
 */
public abstract class VM_JNIHelpers extends VM_JNIGenericHelpers implements VM_RegisterConstants,
                                                                            VM_JNIStackframeLayoutConstants {
  
  /**
   * Common code shared by the JNI functions NewObjectA, NewObjectV, NewObject
   * (object creation)
   * @param methodID the method ID for a constructor
   * @return a new object created by the specified constructor
   */
  public static Object invokeInitializer(Class cls, int methodID, Address argAddress, 
                                         boolean isJvalue, boolean isDotDotStyle) 
    throws Exception {

    // get the parameter list as Java class
    VM_Method mth = VM_MemberReference.getMemberRef(methodID).asMethodReference().resolve();
    Constructor constMethod = java.lang.reflect.JikesRVMSupport.createConstructor(mth);
    if (!mth.isPublic()) {
      constMethod.setAccessible(true);
    }

    Object argObjs[];

    if (isJvalue) {
      argObjs = packageParameterFromJValue(mth, argAddress);
    } else {
      if (isDotDotStyle) {
        //-#if RVM_WITH_POWEROPEN_ABI  || RVM_WITH_MACH_O_ABI
        Address varargAddress = pushVarArgToSpillArea(methodID, false);
        argObjs = packageParameterFromVarArg(mth, varargAddress);
        
        //-#elif RVM_WITH_SVR4_ABI
        // pass in the frame pointer of glue stack frames
        // stack frame looks as following:
        //      this method -> 
        //
        //      native to java method ->
        //
        //      glue frame ->
        //
        //      native C method ->
        Address gluefp = VM_Magic.getCallerFramePointer(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer())); 
        argObjs = packageParameterFromDotArgSVR4(mth, gluefp, false);
        //-#endif
      } else {
        // var arg
        //-#if RVM_WITH_POWEROPEN_ABI  || RVM_WITH_MACH_O_ABI
        argObjs = packageParameterFromVarArg(mth, argAddress);
        //-#elif RVM_WITH_SVR4_ABI
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
    throws Exception, NoInlinePragma {

    //-#if RVM_WITH_POWEROPEN_ABI
    Address varargAddress = pushVarArgToSpillArea(methodID, false);    
    return packageAndInvoke(null, methodID, varargAddress, expectReturnType, false, AIX_VARARG);

    //-#elif RVM_WITH_SVR4_ABI
    Address glueFP = VM_Magic.getCallerFramePointer(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()));
    return packageAndInvoke(null, methodID, glueFP, expectReturnType, false, SVR4_DOTARG);
    //-#elif RVM_WITH_MACH_O_ABI
    Address varargAddress = pushVarArgToSpillArea(methodID, false);    
    return packageAndInvoke(null, methodID, varargAddress, expectReturnType, false, OSX_DOTARG);
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
    throws Exception, NoInlinePragma {

    //-#if RVM_WITH_POWEROPEN_ABI
    Address varargAddress = pushVarArgToSpillArea(methodID, skip4Args);    
    return packageAndInvoke(obj, methodID, varargAddress, expectReturnType, skip4Args, AIX_VARARG);

    //-#elif RVM_WITH_SVR4_ABI 
    Address glueFP = VM_Magic.getCallerFramePointer(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()));
    return packageAndInvoke(obj, methodID, glueFP, expectReturnType, skip4Args, SVR4_DOTARG);
    //-#elif RVM_WITH_MACH_O_ABI
    Address varargAddress = pushVarArgToSpillArea(methodID, skip4Args);    
    return packageAndInvoke(obj, methodID, varargAddress, expectReturnType, skip4Args, OSX_DOTARG);
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
   * NOTE:  this method assumes that it is immediately above the 
   * invokeWithDotDotVarArg frame, the JNI frame, the glue frame and 
   * the C caller frame in the respective order.  
   * Therefore, this method will not work if called from anywhere else
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
  //-#if !RVM_WITH_MACH_O_ABI
  private static Address pushVarArgToSpillArea(int methodID, boolean skip4Args) throws Exception, NoInlinePragma {

    int glueFrameSize = JNI_GLUE_FRAME_SIZE;

    // get the FP for this stack frame and traverse 2 frames to get to the glue frame
    Address gluefp = VM_Magic.getFramePointer().add(VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET).loadAddress();
    gluefp = gluefp.add(VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET).loadAddress();
    gluefp = gluefp.add(VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET).loadAddress();

    // compute the offset into the area where the vararg GPR[6-10] and FPR[1-3] are saved
    // skipping the args which are not part of the arguments for the target method
    // For Call<type>Method functions and NewObject, skip 3 args
    // For CallNonvirtual<type>Method functions, skip 4 args
    Offset varargGPROffset = Offset.fromIntSignExtend(VARARG_AREA_OFFSET + (skip4Args ? BYTES_IN_ADDRESS : 0));
    Offset varargFPROffset = varargGPROffset.add(5*BYTES_IN_ADDRESS);

    // compute the offset into the spill area of the native caller frame, 
    // skipping the args which are not part of the arguments for the target method
    // For Call<type>Method functions, skip 3 args
    // For CallNonvirtual<type>Method functions, skip 4 args
    Offset spillAreaLimit  = Offset.fromIntSignExtend(glueFrameSize + NATIVE_FRAME_HEADER_SIZE + 8*BYTES_IN_ADDRESS);
    Offset spillAreaOffset = Offset.fromIntSignExtend(glueFrameSize + NATIVE_FRAME_HEADER_SIZE + 
                          (skip4Args ? 4*BYTES_IN_ADDRESS : 3*BYTES_IN_ADDRESS));

    // address to return pointing to the var arg list
    Address varargAddress = gluefp.add(spillAreaOffset);

    // VM.sysWrite("pushVarArgToSpillArea:  var arg at " + 
    //             VM.intAsHexString(varargAddress) + "\n");
 
    VM_Method targetMethod = VM_MemberReference.getMemberRef(methodID).asMethodReference().resolve();
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;

    for (int i=0; i<argCount && spillAreaOffset.sLT(spillAreaLimit) ; i++) {
      Word hiword, loword;

      if (argTypes[i].isFloatType() || argTypes[i].isDoubleType()) {
        // move 2 words from the vararg FPR save area into the spill area of the caller
        hiword = gluefp.loadWord(varargFPROffset);
        varargFPROffset = varargFPROffset.add(BYTES_IN_ADDRESS);
        if (VM.BuildFor32Addr) {
          loword = gluefp.loadWord(varargFPROffset);
          varargFPROffset = varargFPROffset.add(BYTES_IN_ADDRESS);
        }
        gluefp.store(hiword, spillAreaOffset);
        spillAreaOffset = spillAreaOffset.add(BYTES_IN_ADDRESS);
        if (VM.BuildFor32Addr) {
          gluefp.store(loword, spillAreaOffset);
          spillAreaOffset = spillAreaOffset.add(BYTES_IN_ADDRESS);
        }
      } 

      else if (argTypes[i].isLongType()) {
        // move 2 words from the vararg GPR save area into the spill area of the caller
        hiword = gluefp.loadWord(varargGPROffset);
        varargGPROffset = varargGPROffset.add(BYTES_IN_ADDRESS);
        gluefp.store(hiword, spillAreaOffset);
        spillAreaOffset = spillAreaOffset.add(BYTES_IN_ADDRESS);
        // this covers the case when the long value straddles the spill boundary
        if (VM.BuildFor32Addr && spillAreaOffset.sLT(spillAreaLimit)) {
          loword = gluefp.loadWord(varargGPROffset);
          varargGPROffset = varargGPROffset.add(BYTES_IN_ADDRESS);
          gluefp.store(loword, spillAreaOffset);
          spillAreaOffset = spillAreaOffset.add(BYTES_IN_ADDRESS);
        }
      }

      else {
        hiword = gluefp.loadWord(varargGPROffset);
        varargGPROffset = varargGPROffset.add(BYTES_IN_ADDRESS);
        gluefp.store(hiword, spillAreaOffset);
        spillAreaOffset = spillAreaOffset.add(BYTES_IN_ADDRESS);
      }

    }

    // At this point, all the vararg values should be in the spill area in the caller frame
    // return the address of the beginning of the vararg to use in invoking the target method
    return varargAddress;

  }
  //-#else   // RVM_WITH_MACH_O_ABI
  private static Address pushVarArgToSpillArea(int methodID, boolean skip4Args) throws Exception, NoInlinePragma {


    // get the FP for this stack frame and traverse 2 frames to get to the glue frame
    Address currentfp = VM_Magic.getFramePointer(); 
    Address gluefp = VM_Magic.getFramePointer().add(VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET).loadAddress();
    gluefp = gluefp.add(VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET).loadAddress();
    gluefp = gluefp.add(VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET).loadAddress();
    Address gluecallerfp = gluefp.add(VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET).loadAddress();
    // compute the offset into the spill area of the native caller frame, 
    // skipping the args which are not part of the arguments for the target method

    // VM.sysWrite("pushVarArgToSpillArea:  var arg at " + 
    //             VM.intAsHexString(varargAddress) + "\n");
 
    VM_Method targetMethod = VM_MemberReference.getMemberRef(methodID).asMethodReference().resolve();
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;

    int argSize = 0;

    // The the arguments that fit in registers (r3-r10) have been
    // saved in the spill area of the glue frame Any remaining
    // parameters are stored in the frame of the caller of the glue method.

    int registerBlock = (1+LAST_OS_PARAMETER_GPR-FIRST_OS_PARAMETER_GPR) *
      BYTES_IN_ADDRESS;
    

    for (int i=0; i<argCount; i++) {
      if (argTypes[i].isDoubleType() || argTypes[i].isLongType())
        argSize += 2 * BYTES_IN_ADDRESS;
      else
        argSize +=  BYTES_IN_ADDRESS;
    }

    // The first 3 or 4 registers contain the JNIEnvironment ptr,
    // class id, method id, object instance. These are *not* passed to
    // the Java method itself:
    
    Address targetAddress = gluefp.add(STACKFRAME_HEADER_SIZE+
                                          ((skip4Args?4:3))* BYTES_IN_ADDRESS);

    int spillRequiredAtOffset = registerBlock -
      ((skip4Args?4:3))* BYTES_IN_ADDRESS;

    if (argSize > spillRequiredAtOffset) {
      Word word;
      int targetOffset = 0;
      int srcOffset = 0;
      // gcc puts the extra var arg information is 14 words into the caller
      // frame: 3 for standard header, 8 register spill, and 3
      // unknown.
      
      Address srcAddress = gluecallerfp.add(14 * BYTES_IN_ADDRESS);

      for (targetOffset = spillRequiredAtOffset;
           targetOffset <= argSize;
           srcOffset += BYTES_IN_ADDRESS, targetOffset += BYTES_IN_ADDRESS) {
        word = srcAddress.loadWord(Offset.fromInt(srcOffset));
        targetAddress.store(word, Offset.fromInt(targetOffset));
      }
    }


    // At this point, all the vararg values should be in the spill area in the caller frame
    // return the address of the beginning of the vararg to use in invoking the target method
    return targetAddress;
  }
  //-#endif

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodV
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithVarArg(int methodID, Address argAddress, 
                                        VM_TypeReference expectReturnType) 
    throws Exception {
    //-#if RVM_WITH_POWEROPEN_ABI
    return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, AIX_VARARG);

    //-#elif RVM_WITH_SVR4_ABI || RVM_WITH_MACH_O_ABI
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
  public static Object invokeWithVarArg(Object obj, int methodID, Address argAddress, 
                                        VM_TypeReference expectReturnType, 
                                        boolean skip4Args) 
    throws Exception {

    //-#if RVM_WITH_POWEROPEN_ABI
    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, AIX_VARARG);

    //-#elif RVM_WITH_SVR4_ABI || RVM_WITH_MACH_O_ABI
    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, SVR4_VARARG);
    //-#endif
  }

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodA
   * @param methodID a VM_MemberReference id
   * @param argAddress a raw address for the argument array
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithJValue(int methodID, Address argAddress, 
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
  public static Object invokeWithJValue(Object obj, int methodID, Address argAddress, 
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
  public static final int OSX_DOTARG  = 5;         // Darwin normal

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
   * @param argtype  Type of argument to be packaged.
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object packageAndInvoke(Object obj, int methodID, Address argAddress, 
                                        VM_TypeReference expectReturnType, 
                                        boolean skip4Args, int argtype) 
    throws Exception {
  
    // VM.sysWrite("JNI CallXXXMethod:  method ID " + methodID + " with args at " + 
    //             VM.intAsHexString(argAddress) + "\n");
    
    VM_Method targetMethod = VM_MemberReference.getMemberRef(methodID).asMethodReference().resolve();
    VM_TypeReference returnType = targetMethod.getReturnType();

    // VM.sysWrite("JNI CallXXXMethod:  " + targetMethod.getDeclaringClass().toString() +
    //          "." + targetMethod.getName().toString() + "\n");

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
      //-#if RVM_WITH_SVR4_ABI
    case SVR4_DOTARG:
      // argAddress is the glue frame pointer
      argObjectArray = packageParameterFromDotArgSVR4(targetMethod, argAddress, skip4Args);
      break;
      //-#endif
    case JVALUE_ARG:
      argObjectArray = packageParameterFromJValue(targetMethod, argAddress);
      break;
      //-#if RVM_WITH_SVR4_ABI 
    case SVR4_VARARG:
      argObjectArray = packageParameterFromVarArgSVR4(targetMethod, argAddress);
      break;
      //-#endif
      //-#if RVM_WITH_MACH_O_ABI
    case SVR4_DOTARG:
    case SVR4_VARARG:
      argObjectArray = packageParameterFromVarArg(targetMethod, argAddress);
      break;
    case OSX_DOTARG:
      argObjectArray = packageParameterFromVarArg(targetMethod, argAddress);
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


  //-#if RVM_WITH_SVR4_ABI || RVM_WITH_MACH_O_ABI
  /* The method reads out parameters from registers saved in native->java glue stack frame (glueFP)
   * and the spill area of native stack frame (caller of glueFP).
   * 
   * NOTE: assuming the stack frame won't get moved, (see pushVarArgToSpillArea)
   *       the row address glueFP can be replaced by offset to the stack.
   *
   * @param targetMethod, the call target
   * @param glueFP, the glue stack frame pointer
   */
  static Object[] packageParameterFromDotArgSVR4(VM_Method targetMethod, Address glueFP, boolean skip4Args) {
    // native method's stack frame
    Address nativeFP = VM_Magic.getCallerFramePointer(glueFP);
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();

    // GPR r3 - r10 and FPR f1 - f8 are saved in glue stack frame
    Address regsavearea = glueFP.add(VM_StackframeLayoutConstants.STACKFRAME_HEADER_SIZE);

    // spill area offset
    Address overflowarea = nativeFP.add(NATIVE_FRAME_HEADER_SIZE);
    
    //-#if RVM_FOR_LINUX
    //overflowarea is aligned to 8 bytes
    if (VM.VerifyAssertions) VM._assert(overflowarea.toWord().and(Word.fromIntZeroExtend(0x07)).isZero());
    
    //-#endif
    
    //adjust gpr and fpr to normal numbering, make life easier
    int gpr = (skip4Args) ? 7:6;       // r3 - env, r4 - cls, r5 - method id
    int fpr = 1;
    
    // not set the starting gprs array address 
    // and fpr starting array address, so we can use gpr and fpr to 
    // calculate the right position to get values
    // GPR starts with r3;
    Address gprarray = regsavearea.add(-3*BYTES_IN_ADDRESS); 
    Address fprarray = regsavearea.add(8*BYTES_IN_ADDRESS-2*BYTES_IN_ADDRESS);

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
  static Object[] packageParameterFromVarArgSVR4(VM_Method targetMethod, Address argAddress) {
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];
    
    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();
    
    // the va_list has following layout on PPC/Linux
    // GPR FPR 0 0   (4 bytes)
    // overflowarea  (pointer)
    // reg_save_area (pointer)
    Address va_list_addr = argAddress;
    int word1 = va_list_addr.loadWord().toInt();
    int gpr = word1 >> 24;
    int fpr = (word1 >> 16) & 0x0FF;
    Address overflowarea = va_list_addr.loadAddress(Offset.fromIntSignExtend(BYTES_IN_ADDRESS));
    Address regsavearea = va_list_addr.loadAddress(Offset.fromIntSignExtend(2*BYTES_IN_ADDRESS));
    
    //-#if RVM_FOR_LINUX
    //overflowarea is aligned to 8 bytes
    if (VM.VerifyAssertions) VM._assert(overflowarea.toWord().and(Word.fromIntZeroExtend(0x07)).isZero());
    
    //-#endif
    
    //adjust gpr and fpr to normal numbering, make life easier
    gpr += 3;
    fpr += 1;
    
    // not set the starting gprs array address 
    // and fpr starting array address, so we can use gpr and fpr to 
    // calculate the right position to get values
    // GPR starts with r3;
    Address gprarray = regsavearea.add(-3*BYTES_IN_ADDRESS); 
    Address fprarray = regsavearea.add(8*BYTES_IN_ADDRESS-2*BYTES_IN_ADDRESS);
    
    // call the common function for SVR4
    packageArgumentForSVR4(argTypes, argObjectArray, gprarray, fprarray, overflowarea, gpr, fpr, env); 

    return argObjectArray;
  }

  //-#endif RVM_WITH_SVR4_ABI || RVM_WITH_MACH_O_ABI

  //-#if RVM_WITH_SVR4_ABI
  static void packageArgumentForSVR4(VM_TypeReference[] argTypes, Object[] argObjectArray,
                                     Address gprarray, Address fprarray,
                                     Address overflowarea, int gpr, int fpr,
                                     VM_JNIEnvironment env) {
    // also make overflow offset, we may need to round it
    Offset overflowoffset = Offset.zero();
    int argCount = argTypes.length;

    // now interpret values by types, see PPC ABI
    for (int i=0; i<argCount; i++) {
      if (argTypes[i].isFloatType()
          || argTypes[i].isDoubleType()) {
        int loword, hiword;
        if (fpr > LAST_OS_PARAMETER_FPR) {
          // overflow, OTHER
          // round it, bytes are saved from lowest to highest one, regardless endian
          overflowoffset = overflowoffset.add(7).toWord().and(Word.fromIntSignExtend(-8)).toOffset();
          hiword = overflowarea.loadInt(overflowoffset);
          overflowoffset = overflowoffset.add(BYTES_IN_INT);
          loword = overflowarea.loadInt(overflowoffset);
          overflowoffset = overflowoffset.add(BYTES_IN_INT);
        } else {
          // get value from fpr, increase fpr by 1
          hiword = fprarray.add(fpr*BYTES_IN_DOUBLE).loadInt();
          loword = fprarray.add(fpr*BYTES_IN_DOUBLE + BYTES_IN_INT).loadInt();
          fpr += 1;
        }
        long doubleBits = (((long)hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
        if (argTypes[i].isFloatType()) {
          argObjectArray[i] = VM_Reflection.wrapFloat((float)(Double.longBitsToDouble(doubleBits)));
        } else { // double type
          argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));
        }
        
        //              VM.sysWriteln("double "+Double.longBitsToDouble(doubleBits));
        
      } else if (argTypes[i].isLongType()) {
        int loword, hiword;
        if (gpr > LAST_OS_PARAMETER_GPR-1) {
          // overflow, OTHER
          // round overflowoffset, assuming overflowarea is aligned to 8 bytes
          overflowoffset = overflowoffset.add(7).toWord().and(Word.fromIntSignExtend(-8)).toOffset();
          hiword = overflowarea.loadInt(overflowoffset);
          overflowoffset = overflowoffset.add(BYTES_IN_INT);
          loword = overflowarea.loadInt(overflowoffset);
          overflowoffset = overflowoffset.add(BYTES_IN_INT);
          
          // va-ppc.h makes last gpr useless
          gpr = 11;
        } else {
          gpr += (gpr + 1) & 0x01;  // if gpr is even, gpr += 1
          hiword = gprarray.add(gpr*4).loadInt();
          loword = gprarray.add((gpr+1)*4).loadInt();
          gpr += 2;
        }
        long longBits = (((long)hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
        argObjectArray[i] = VM_Reflection.wrapLong(longBits);
        
        //              VM.sysWriteln("long 0x"+Long.toHexString(longBits));
      } else {
        // int type left now
        int ivalue;
        if (gpr > LAST_OS_PARAMETER_GPR) {
          // overflow, OTHER
          ivalue = overflowarea.loadInt(overflowoffset);
          overflowoffset = overflowoffset.add(4);
        } else {
          ivalue = gprarray.add(gpr*4).loadInt();
          gpr += 1;
        } 
        
        //              VM.sysWriteln("int "+ivalue);
        
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
  //-#elif RVM_WITH_MACH_O_ABI
  static void packageArgumentForSVR4(VM_TypeReference[] argTypes, Object[] argObjectArray,
                                     Address gprarray, Address fprarray,
                                     Address overflowarea, int gpr, int fpr,
                                     VM_JNIEnvironment env) {
    // also make overflow offset, we may need to round it
    Offset overflowoffset = Offset.zero()
    int argCount = argTypes.length;

    // now interpret values by types, see PPC ABI
    for (int i=0; i<argCount; i++) {
      int regIncrementGpr = 1;
      if (argTypes[i].isFloatType()
          || argTypes[i].isDoubleType()) {
        int loword, hiword;
        if (fpr > LAST_OS_PARAMETER_FPR) {
          // overflow, OTHER
          // round it, bytes are saved from lowest to highest one, regardless endian
          // overflowoffset = (overflowoffset + 7) & -8;
          hiword = overflowarea.loadInt(overflowoffset);
          overflowoffset = overflowoffset.add(BYTES_IN_INT);
          loword = overflowarea.loadInt(overflowoffset);
          overflowoffset = overflowoffset.add(BYTES_IN_INT);
        } else {
          // get value from fpr, increase fpr by 1
          hiword = fprarray.add(fpr*BYTES_IN_DOUBLE).loadInt();
          loword = fprarray.add(fpr*BYTES_IN_DOUBLE + BYTES_IN_INT).loadInt();
        }
        long doubleBits = (((long)hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
        if (argTypes[i].isFloatType()) {
          argObjectArray[i] = VM_Reflection.wrapFloat((float)(Double.longBitsToDouble(doubleBits)));
        } else { // double type
          argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));
          regIncrementGpr = 2;
        }
        
        //              VM.sysWriteln("double "+Double.longBitsToDouble(doubleBits));
        
      } else if (argTypes[i].isLongType()) {
        int loword, hiword;
        if (gpr > LAST_OS_PARAMETER_GPR-1) {
          // overflow, OTHER
          // round overflowoffset, assuming overflowarea is aligned to 8 bytes
          //overflowoffset = (overflowoffset + 7) & -8;
          hiword = overflowarea.loadInt(overflowoffset);
          overflowoffset = overflowoffset.add(BYTES_IN_INT);
          loword = overflowarea.loadInt(overflowoffset);
          overflowoffset = overflowoffset.add(BYTES_IN_INT);
          
          // va-ppc.h makes last gpr useless
          regIncrementGpr = 2;
        } else {
          hiword = gprarray.loadInt(Offset.fromInt(gpr*4));
          loword = gprarray.loadInt(Offset.fromInt((gpr+1)*4));
          regIncrementGpr = 2;
        }
        long longBits = (((long)hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
        argObjectArray[i] = VM_Reflection.wrapLong(longBits);
        
      } else {
        // int type left now
        int ivalue;
        if (gpr > LAST_OS_PARAMETER_GPR) {
          // overflow, OTHER
          ivalue = overflowarea.loadInt(overflowoffset);
          overflowoffset = overflowoffset.add(BYTES_IN_INT);
        } else {
          ivalue = gprarray.loadInt(Offset.fromInt(gpr*4));
        } 
        
        //              VM.sysWriteln("int "+ivalue);
        
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
      gpr += regIncrementGpr;
    }
  }
  //-#endif RVM_WITH_MACH_O_ABI


  /**
   * Repackage the arguments passed as a variable argument list into an array of Object,
   * used by the JNI functions CallStatic<type>MethodV
   * @param targetMethod   The target {@link VM_Method}
   * @param argAddress an address into the C space for the array of jvalue unions;  
   *                   each element is 2-word and holds the argument of the appropriate type
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParameterFromVarArg(VM_Method targetMethod, Address argAddress) {
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    // get the VM_JNIEnvironment for this thread in case we need to dereference any object arg
    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();

    // VM.sysWrite("JNI packageParameterFromVarArg: packaging " + argCount + " arguments\n");

    Address addr = argAddress;
    for (int i=0; i<argCount; i++) {
      //-#if RVM_FOR_64_ADDR
      long hiword;
      hiword = addr.loadLong();
      //-#else
      int hiword;
      hiword = addr.loadInt();
      //-#endif

      // VM.sysWrite("JNI packageParameterFromVarArg:  arg " + i + " = " + hiword + 
      // " or " + VM.intAsHexString(hiword) + "\n");

      addr = addr.add(BYTES_IN_ADDRESS);

      // convert and wrap the argument according to the expected type

      if (argTypes[i].isFloatType()) {
        // NOTE:  in VarArg convention, C compiler will expand a float to a double that occupy 2 words
        // so we have to extract it as a double and convert it back to a float
        if (VM.BuildFor32Addr) {
          int loword = addr.loadInt();
          addr = addr.add(BYTES_IN_ADDRESS);                       
          long doubleBits = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
          argObjectArray[i] = VM_Reflection.wrapFloat((float) (Double.longBitsToDouble(doubleBits)));
        } else {
          argObjectArray[i] = VM_Reflection.wrapFloat((float) (Double.longBitsToDouble(hiword)));
        }
      } else if (argTypes[i].isDoubleType()) {
        if (VM.BuildFor32Addr) {
          int loword = addr.loadInt();
          addr = addr.add(BYTES_IN_ADDRESS);
          long doubleBits = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
          argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));
        } else {
          argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(hiword));
        }
      } else if (argTypes[i].isLongType()) { 
        if (VM.BuildFor32Addr) {
          int loword = addr.loadInt();
          addr = addr.add(BYTES_IN_ADDRESS);
          long longValue = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
          argObjectArray[i] = VM_Reflection.wrapLong(longValue);
        } else {
          argObjectArray[i] = VM_Reflection.wrapLong(hiword);
        }
      } else if (argTypes[i].isBooleanType()) {
        // the 0/1 bit is stored in the high byte       
        argObjectArray[i] = VM_Reflection.wrapBoolean((int) hiword);
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
        argObjectArray[i] =  env.getJNIRef((int) hiword);   
      } else if (argTypes[i].isIntType()) {
        argObjectArray[i] = VM_Reflection.wrapInt((int) hiword);
      } else {
        return null;
      }
    }
    return argObjectArray;
  }

  /**
   * Repackage the arguments passed as an array of jvalue into an array of Object,
   * used by the JNI functions CallStatic<type>MethodA
   * @param targetMethod the target {@link VM_Method}
   * @param argAddress an address into the C space for the array of jvalue unions;  
   *                   each element is 2-word and holds the argument of the appropriate type
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParameterFromJValue(VM_Method targetMethod, Address argAddress) {
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    // get the VM_JNIEnvironment for this thread in case we need to dereference any object arg
    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();

    // VM.sysWrite("JNI packageParameterFromJValue: packaging " + argCount + " arguments\n");

    for (int i=0; i<argCount; i++) {
      Address addr = argAddress.add(BYTES_IN_DOUBLE*i);
      //-#if RVM_FOR_64_ADDR
      long hiword;
      hiword = addr.loadLong();
      //-#else
      int hiword;
      hiword = addr.loadInt();
      //-#endif

      // VM.sysWrite("JNI packageParameterFromJValue:  arg " + i + " = " + hiword + 
      //          " or " + VM.intAsHexString(hiword) + "\n");

      // convert and wrap the argument according to the expected type

      if (argTypes[i].isFloatType()) {
        argObjectArray[i] = VM_Reflection.wrapFloat(Float.intBitsToFloat((int)(hiword  >>> (BITS_IN_ADDRESS - BITS_IN_FLOAT))));
      } else if (argTypes[i].isDoubleType()) {
        if (VM.BuildFor32Addr) {
          int loword = addr.add(BYTES_IN_ADDRESS).loadInt();
          long doubleBits = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
          argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));
        } else {
          argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(hiword));
        }
      } else if (argTypes[i].isLongType()) { 
        if (VM.BuildFor32Addr) {
          int loword = addr.add(BYTES_IN_ADDRESS).loadInt();
          long longValue = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
          argObjectArray[i] = VM_Reflection.wrapLong(longValue);
        } else {
          argObjectArray[i] = VM_Reflection.wrapLong(hiword);
        }
      } else if (argTypes[i].isBooleanType()) {
        // the 0/1 bit is stored in the high byte       
        argObjectArray[i] = VM_Reflection.wrapBoolean((int) (hiword  >>> (BITS_IN_ADDRESS - BITS_IN_BOOLEAN)));
      } else if (argTypes[i].isByteType()) {
        // the target byte is stored in the high byte
        argObjectArray[i] = VM_Reflection.wrapByte((byte) (hiword  >>> (BITS_IN_ADDRESS - BITS_IN_BYTE)));
      } else if (argTypes[i].isCharType()) {
        // char is stored in the high 2 bytes
        argObjectArray[i] = VM_Reflection.wrapChar((char) (hiword  >>> (BITS_IN_ADDRESS - BITS_IN_CHAR)));
      } else if (argTypes[i].isShortType()) {
        // short is stored in the high 2 bytes
        argObjectArray[i] = VM_Reflection.wrapShort((short) (hiword  >>> (BITS_IN_ADDRESS - BITS_IN_SHORT)));
      } else if (argTypes[i].isReferenceType()) {
        // for object, the arg is a JREF index, dereference to get the real object
        argObjectArray[i] =  env.getJNIRef((int) hiword);   
      } else if (argTypes[i].isIntType()) {
        argObjectArray[i] = VM_Reflection.wrapInt((int) (hiword  >>> (BITS_IN_ADDRESS - BITS_IN_INT)));
      } else {
        return null;
      }
    }
    return argObjectArray;
  }
}
