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
package org.jikesrvm.jni.ppc;

import java.lang.reflect.Constructor;
import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.jni.JNIEnvironment;
import org.jikesrvm.jni.JNIGenericHelpers;
import org.jikesrvm.ppc.RegisterConstants;
import org.jikesrvm.ppc.StackframeLayoutConstants;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Platform dependent utility functions called from JNIFunctions
 * (cannot be placed in JNIFunctions because methods
 * there are specially compiled to be called from native).
 *
 * @see org.jikesrvm.jni.JNIFunctions
 */
public abstract class JNIHelpers extends JNIGenericHelpers
    implements RegisterConstants, JNIStackframeLayoutConstants {

  /**
   * Common code shared by the JNI functions NewObjectA, NewObjectV, NewObject
   * (object creation)
   * @param methodID the method ID for a constructor
   * @return a new object created by the specified constructor
   */
  public static Object invokeInitializer(Class<?> cls, int methodID, Address argAddress, boolean isJvalue,
                                         boolean isDotDotStyle) throws Exception {

    // get the parameter list as Java class
    MemberReference mr = MemberReference.getMemberRef(methodID);
    TypeReference tr = java.lang.JikesRVMSupport.getTypeForClass(cls).getTypeRef();
    RVMMethod mth = MemberReference.findOrCreate(tr, mr.getName(), mr.getDescriptor()).asMethodReference().resolve();

    Constructor<?> constMethod = java.lang.reflect.JikesRVMSupport.createConstructor(mth);
    if (!mth.isPublic()) {
      constMethod.setAccessible(true);
    }

    Object[] argObjs;

    if (isJvalue) {
      argObjs = packageParameterFromJValue(mth, argAddress);
    } else {
      if (isDotDotStyle) {
        if (VM.BuildForPowerOpenABI || VM.BuildForMachOABI) {
          Address varargAddress = pushVarArgToSpillArea(methodID, false);
          argObjs = packageParameterFromVarArg(mth, varargAddress);
        } else {
          if (VM.VerifyAssertions) VM._assert(VM.BuildForSVR4ABI);
          // pass in the frame pointer of glue stack frames
          // stack frame looks as following:
          //      this method ->
          //
          //      native to java method ->
          //
          //      glue frame ->
          //
          //      native C method ->
          Address gluefp = Magic.getCallerFramePointer(Magic.getCallerFramePointer(Magic.getFramePointer()));
          argObjs = packageParameterFromDotArgSVR4(mth, gluefp, false);
        }
      } else {
        // var arg
        if (VM.BuildForPowerOpenABI || VM.BuildForMachOABI) {
          argObjs = packageParameterFromVarArg(mth, argAddress);
        } else {
          if (VM.VerifyAssertions) VM._assert(VM.BuildForSVR4ABI);
          argObjs = packageParameterFromVarArgSVR4(mth, argAddress);
        }
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
  @NoInline
  public static Object invokeWithDotDotVarArg(int methodID, TypeReference expectReturnType) throws Exception {

    if (VM.BuildForPowerOpenABI) {
      Address varargAddress = pushVarArgToSpillArea(methodID, false);
      return packageAndInvoke(null, methodID, varargAddress, expectReturnType, false, AIX_VARARG);
    } else if (VM.BuildForSVR4ABI) {
      Address glueFP = Magic.getCallerFramePointer(Magic.getCallerFramePointer(Magic.getFramePointer()));
      return packageAndInvoke(null, methodID, glueFP, expectReturnType, false, SVR4_DOTARG);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForMachOABI);
      Address varargAddress = pushVarArgToSpillArea(methodID, false);
      return packageAndInvoke(null, methodID, varargAddress, expectReturnType, false, OSX_DOTARG);
    }
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
  @NoInline
  public static Object invokeWithDotDotVarArg(Object obj, int methodID, TypeReference expectReturnType,
                                              boolean skip4Args) throws Exception {

    if (VM.BuildForPowerOpenABI) {
      Address varargAddress = pushVarArgToSpillArea(methodID, skip4Args);
      return packageAndInvoke(obj, methodID, varargAddress, expectReturnType, skip4Args, AIX_VARARG);
    } else if (VM.BuildForSVR4ABI) {
      Address glueFP = Magic.getCallerFramePointer(Magic.getCallerFramePointer(Magic.getFramePointer()));
      return packageAndInvoke(obj, methodID, glueFP, expectReturnType, skip4Args, SVR4_DOTARG);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForMachOABI);
      Address varargAddress = pushVarArgToSpillArea(methodID, skip4Args);
      return packageAndInvoke(obj, methodID, varargAddress, expectReturnType, skip4Args, OSX_DOTARG);
    }
  }

  /**
   * This method supports var args passed from C.<p>
   *
   * In the AIX C convention, the caller keeps the first 8 words in registers and
   * the rest in the spill area in the caller frame.  The callee will push the values
   * in registers out to the spill area of the caller frame and use the beginning
   * address of this spill area as the var arg address.<p>
   *
   * For the JNI functions that takes var args, their prolog code will save the
   * var arg in the glue frame because the values in the register may be lost by
   * subsequent calls.<p>
   *
   * This method copies the var arg values that were saved earlier in glue frame into
   * the spill area of the original caller, thereby doing the work that the callee
   * normally performs in the AIX C convention..<p>
   *
   * NOTE:  this method assumes that it is immediately above the
   * invokeWithDotDotVarArg frame, the JNI frame, the glue frame and
   * the C caller frame in the respective order.
   * Therefore, this method will not work if called from anywhere else
   * <pre>
   *
   *   |  fp  | <- JNIEnvironment.pushVarArgToSpillArea
   *   | mid  |
   *   | xxx  |
   *   |      |
   *   |      |
   *   |------|
   *   |  fp  | <- JNIEnvironment.invokeWithDotDotVarArg frame
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
   * </pre>
   *
   * @param methodID a MemberReference id
   * @param skip4Args if true, the calling JNI function has 4 args before the vararg
   *                  if false, the calling JNI function has 3 args before the vararg
   * @return the starting address of the vararg in the caller stack frame
   */
  @NoInline
  private static Address pushVarArgToSpillArea(int methodID, boolean skip4Args) throws Exception {
    if (VM.BuildForPowerOpenABI || VM.BuildForSVR4ABI) {
      int glueFrameSize = JNI_GLUE_FRAME_SIZE;

      // get the FP for this stack frame and traverse 2 frames to get to the glue frame
      Address gluefp =
          Magic.getFramePointer().plus(ArchitectureSpecific.ArchConstants.STACKFRAME_FRAME_POINTER_OFFSET).loadAddress();
      gluefp = gluefp.plus(ArchitectureSpecific.ArchConstants.STACKFRAME_FRAME_POINTER_OFFSET).loadAddress();
      gluefp = gluefp.plus(ArchitectureSpecific.ArchConstants.STACKFRAME_FRAME_POINTER_OFFSET).loadAddress();

      // compute the offset into the area where the vararg GPR[6-10] and FPR[1-3] are saved
      // skipping the args which are not part of the arguments for the target method
      // For Call<type>Method functions and NewObject, skip 3 args
      // For CallNonvirtual<type>Method functions, skip 4 args
      Offset varargGPROffset = Offset.fromIntSignExtend(VARARG_AREA_OFFSET + (skip4Args ? BYTES_IN_ADDRESS : 0));
      Offset varargFPROffset = varargGPROffset.plus(5 * BYTES_IN_ADDRESS);

      // compute the offset into the spill area of the native caller frame,
      // skipping the args which are not part of the arguments for the target method
      // For Call<type>Method functions, skip 3 args
      // For CallNonvirtual<type>Method functions, skip 4 args
      Offset spillAreaLimit = Offset.fromIntSignExtend(glueFrameSize + NATIVE_FRAME_HEADER_SIZE + 8 * BYTES_IN_ADDRESS);
      Offset spillAreaOffset =
          Offset.fromIntSignExtend(glueFrameSize +
                                   NATIVE_FRAME_HEADER_SIZE +
                                   (skip4Args ? 4 * BYTES_IN_ADDRESS : 3 * BYTES_IN_ADDRESS));

      // address to return pointing to the var arg list
      Address varargAddress = gluefp.plus(spillAreaOffset);

      // VM.sysWrite("pushVarArgToSpillArea:  var arg at " +
      //             VM.intAsHexString(varargAddress) + "\n");

      RVMMethod targetMethod = MemberReference.getMemberRef(methodID).asMethodReference().resolve();
      TypeReference[] argTypes = targetMethod.getParameterTypes();
      int argCount = argTypes.length;

      for (int i = 0; i < argCount && spillAreaOffset.sLT(spillAreaLimit); i++) {
        Word hiword, loword;

        if (argTypes[i].isFloatType() || argTypes[i].isDoubleType()) {
          // move 2 words from the vararg FPR save area into the spill area of the caller
          hiword = gluefp.loadWord(varargFPROffset);
          varargFPROffset = varargFPROffset.plus(BYTES_IN_ADDRESS);
          if (VM.BuildFor32Addr) {
            loword = gluefp.loadWord(varargFPROffset);
            varargFPROffset = varargFPROffset.plus(BYTES_IN_ADDRESS);
          }
          gluefp.store(hiword, spillAreaOffset);
          spillAreaOffset = spillAreaOffset.plus(BYTES_IN_ADDRESS);
          if (VM.BuildFor32Addr) {
            gluefp.store(loword, spillAreaOffset);
            spillAreaOffset = spillAreaOffset.plus(BYTES_IN_ADDRESS);
          }
        } else if (argTypes[i].isLongType()) {
          // move 2 words from the vararg GPR save area into the spill area of the caller
          hiword = gluefp.loadWord(varargGPROffset);
          varargGPROffset = varargGPROffset.plus(BYTES_IN_ADDRESS);
          gluefp.store(hiword, spillAreaOffset);
          spillAreaOffset = spillAreaOffset.plus(BYTES_IN_ADDRESS);
          // this covers the case when the long value straddles the spill boundary
          if (VM.BuildFor32Addr && spillAreaOffset.sLT(spillAreaLimit)) {
            loword = gluefp.loadWord(varargGPROffset);
            varargGPROffset = varargGPROffset.plus(BYTES_IN_ADDRESS);
            gluefp.store(loword, spillAreaOffset);
            spillAreaOffset = spillAreaOffset.plus(BYTES_IN_ADDRESS);
          }
        } else {
          hiword = gluefp.loadWord(varargGPROffset);
          varargGPROffset = varargGPROffset.plus(BYTES_IN_ADDRESS);
          gluefp.store(hiword, spillAreaOffset);
          spillAreaOffset = spillAreaOffset.plus(BYTES_IN_ADDRESS);
        }

      }

      // At this point, all the vararg values should be in the spill area in the caller frame
      // return the address of the beginning of the vararg to use in invoking the target method
      return varargAddress;

    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForMachOABI);
      // get the FP for this stack frame and traverse 2 frames to get to the glue frame
      Address currentfp = Magic.getFramePointer();
      Address gluefp =
          Magic.getFramePointer().plus(ArchitectureSpecific.ArchConstants.STACKFRAME_FRAME_POINTER_OFFSET).loadAddress();
      gluefp = gluefp.plus(ArchitectureSpecific.ArchConstants.STACKFRAME_FRAME_POINTER_OFFSET).loadAddress();
      gluefp = gluefp.plus(ArchitectureSpecific.ArchConstants.STACKFRAME_FRAME_POINTER_OFFSET).loadAddress();
      Address gluecallerfp =
          gluefp.plus(ArchitectureSpecific.ArchConstants.STACKFRAME_FRAME_POINTER_OFFSET).loadAddress();
      // compute the offset into the spill area of the native caller frame,
      // skipping the args which are not part of the arguments for the target method

      // VM.sysWrite("pushVarArgToSpillArea:  var arg at " +
      //             VM.intAsHexString(varargAddress) + "\n");

      RVMMethod targetMethod = MemberReference.getMemberRef(methodID).asMethodReference().resolve();
      TypeReference[] argTypes = targetMethod.getParameterTypes();
      int argCount = argTypes.length;

      int argSize = 0;

      // The the arguments that fit in registers (r3-r10) have been
      // saved in the spill area of the glue frame Any remaining
      // parameters are stored in the frame of the caller of the glue method.

      int registerBlock = (1 + LAST_OS_PARAMETER_GPR - FIRST_OS_PARAMETER_GPR) * BYTES_IN_ADDRESS;

      for (int i = 0; i < argCount; i++) {
        if (argTypes[i].isDoubleType() || argTypes[i].isLongType()) {
          argSize += 2 * BYTES_IN_ADDRESS;
        } else {
          argSize += BYTES_IN_ADDRESS;
        }
      }

      // The first 3 or 4 registers contain the JNIEnvironment ptr,
      // class id, method id, object instance. These are *not* passed to
      // the Java method itself:

      Address targetAddress = gluefp.plus(STACKFRAME_HEADER_SIZE + ((skip4Args ? 4 : 3)) * BYTES_IN_ADDRESS);

      int spillRequiredAtOffset = registerBlock - ((skip4Args ? 4 : 3)) * BYTES_IN_ADDRESS;

      if (argSize > spillRequiredAtOffset) {
        Word word;
        int targetOffset = 0;
        int srcOffset = 0;
        // gcc puts the extra var arg information is 14 words into the caller
        // frame: 3 for standard header, 8 register spill, and 3
        // unknown.

        Address srcAddress = gluecallerfp.plus(14 * BYTES_IN_ADDRESS);

        for (targetOffset = spillRequiredAtOffset; targetOffset <= argSize; srcOffset +=
            BYTES_IN_ADDRESS, targetOffset += BYTES_IN_ADDRESS) {
          word = srcAddress.loadWord(Offset.fromIntZeroExtend(srcOffset));
          targetAddress.store(word, Offset.fromIntZeroExtend(targetOffset));
        }
      }

      // At this point, all the vararg values should be in the spill area in the caller frame
      // return the address of the beginning of the vararg to use in invoking the target method
      return targetAddress;
    }
  }

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodV
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @return an object that may be the return object or a wrapper for the primitive return value
   */
  public static Object invokeWithVarArg(int methodID, Address argAddress, TypeReference expectReturnType)
      throws Exception {
    if (VM.BuildForPowerOpenABI) {
      return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, AIX_VARARG);
    } else {
      return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, SVR4_VARARG);
    }
  }

  /**
   * Common code shared by the JNI functions Call<type>MethodV
   * @param obj the object instance
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args received from the JNI function, passed on to Reflection.invoke()
   * @return an object that may be the return object or a wrapper for the primitive return value
   */
  public static Object invokeWithVarArg(Object obj, int methodID, Address argAddress, TypeReference expectReturnType,
                                        boolean skip4Args) throws Exception {
    if (VM.BuildForPowerOpenABI) {
      return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, AIX_VARARG);
    } else {
      return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, SVR4_VARARG);
    }
  }

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodA
   * @param methodID a MemberReference id
   * @param argAddress a raw address for the argument array
   * @return an object that may be the return object or a wrapper for the primitive return value
   */
  public static Object invokeWithJValue(int methodID, Address argAddress, TypeReference expectReturnType)
      throws Exception {
    return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, JVALUE_ARG);
  }

  /**
   * Common code shared by the JNI functions Call<type>MethodA
   * @param obj the object instance
   * @param methodID a MemberReference id
   * @param argAddress a raw address for the argument array
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args received from the JNI function, passed on to Reflection.invoke()
   * @return an object that may be the return object or a wrapper for the primitive return value
   */
  public static Object invokeWithJValue(Object obj, int methodID, Address argAddress, TypeReference expectReturnType,
                                        boolean skip4Args) throws Exception {
    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, JVALUE_ARG);
  }

  public static final int SVR4_DOTARG = 0;         // Linux/PPC SVR4 normal
  public static final int AIX_DOTARG = 1;         // AIX normal
  public static final int JVALUE_ARG = 2;         // javlue
  public static final int SVR4_VARARG = 3;         // Linux/PPC SVR4 vararg
  public static final int AIX_VARARG = 4;         // AIX vararg
  public static final int OSX_DOTARG = 5;         // Darwin normal

  /**
   * Common code shared by invokeWithJValue, invokeWithVarArg and invokeWithDotDotVarArg
   * @param obj the object instance
   * @param methodID a MemberReference id
   * @param argAddress a raw address for the argument array
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args This flag is received from the JNI function and passed directly to
   *                     Reflection.invoke().
   *                     It is true if the actual method is to be invoked, which could be
   *                     from the superclass.
   *                     It is false if the method from the real class of the object
   *                     is to be invoked, which may not be the actual method specified by methodID
   * @param argtype  Type of argument to be packaged.
   * @return an object that may be the return object or a wrapper for the primitive return value
   */
  public static Object packageAndInvoke(Object obj, int methodID, Address argAddress, TypeReference expectReturnType,
                                        boolean skip4Args, int argtype) throws Exception {

    // VM.sysWrite("JNI CallXXXMethod:  method ID " + methodID + " with args at " +
    //             VM.intAsHexString(argAddress) + "\n");

    RVMMethod targetMethod = MemberReference.getMemberRef(methodID).asMethodReference().resolve();
    TypeReference returnType = targetMethod.getReturnType();

    // VM.sysWrite("JNI CallXXXMethod:  " + targetMethod.getDeclaringClass().toString() +
    //          "." + targetMethod.getName().toString() + "\n");

    if (expectReturnType == null) {   // for reference return type
      if (!returnType.isReferenceType()) {
        throw new Exception("Wrong return type for method: expect reference type instead of " + returnType);
      }
    } else {    // for primitive return type
      if (returnType != expectReturnType) {
        throw new Exception("Wrong return type for method: expect " + expectReturnType + " instead of " + returnType);
      }
    }

    // Repackage the arguments into an array of objects based on the signature of this method
    Object[] argObjectArray;

    if (argtype == JVALUE_ARG) {
      argObjectArray = packageParameterFromJValue(targetMethod, argAddress);
    } else {
      // TODO: The MachO and SVR4 arms of this if/then/else seem overly complicated
      //       but I'm hesitant to simplify/restructure without being able to fully test them.
      //       So, for now, I simply cleaned up preprocessor directives without changing the set
      //       of argtypes tested on each platform.
      //       But, this can't actually be the way this code should be written...
      if (VM.BuildForPowerOpenABI) {
        if (VM.VerifyAssertions) VM._assert(argtype == AIX_VARARG);
        argObjectArray = packageParameterFromVarArg(targetMethod, argAddress);
      } else if (VM.BuildForSVR4ABI) {
        switch (argtype) {
          case SVR4_DOTARG:
            // argAddress is the glue frame pointer
            argObjectArray = packageParameterFromDotArgSVR4(targetMethod, argAddress, skip4Args);
            break;
          case SVR4_VARARG:
            argObjectArray = packageParameterFromVarArgSVR4(targetMethod, argAddress);
            break;
          case AIX_VARARG:
            // TODO: Is this branch actually reachable code???
            argObjectArray = packageParameterFromVarArg(targetMethod, argAddress);
            break;
          default:
            argObjectArray = null;
            if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
        }
      } else if (VM.BuildForMachOABI) {
        switch (argtype) {
          case SVR4_DOTARG:
          case SVR4_VARARG:
          case OSX_DOTARG:
          case AIX_VARARG:
            argObjectArray = packageParameterFromVarArg(targetMethod, argAddress);
            break;
          default:
            argObjectArray = null;
            if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
        }
      }
    }

    // now invoke the method
    return Reflection.invoke(targetMethod, null, obj, argObjectArray, skip4Args);
  }

  /* The method reads out parameters from registers saved in native->java glue stack frame (glueFP)
  * and the spill area of native stack frame (caller of glueFP).
  *
  * NOTE: assuming the stack frame won't get moved, (see pushVarArgToSpillArea)
  *       the row address glueFP can be replaced by offset to the stack.
  *
  * @param targetMethod, the call target
  * @param glueFP, the glue stack frame pointer
  */
  static Object[] packageParameterFromDotArgSVR4(RVMMethod targetMethod, Address glueFP, boolean skip4Args) {
    if (VM.BuildForSVR4ABI || VM.BuildForMachOABI) {
      // native method's stack frame
      Address nativeFP = Magic.getCallerFramePointer(glueFP);
      TypeReference[] argTypes = targetMethod.getParameterTypes();
      int argCount = argTypes.length;
      Object[] argObjectArray = new Object[argCount];

      JNIEnvironment env = RVMThread.getCurrentThread().getJNIEnv();

      // GPR r3 - r10 and FPR f1 - f8 are saved in glue stack frame
      Address regsavearea = glueFP.plus(StackframeLayoutConstants.STACKFRAME_HEADER_SIZE);

      // spill area offset
      Address overflowarea = nativeFP.plus(NATIVE_FRAME_HEADER_SIZE);

      if (VM.BuildForSVR4ABI) {
        //overflowarea is aligned to 8 bytes
        if (VM.VerifyAssertions) VM._assert(overflowarea.toWord().and(Word.fromIntZeroExtend(0x07)).isZero());
      }

      //adjust gpr and fpr to normal numbering, make life easier
      int gpr = (skip4Args) ? 7 : 6;       // r3 - env, r4 - cls, r5 - method id
      int fpr = 1;

      // not set the starting gprs array address
      // and fpr starting array address, so we can use gpr and fpr to
      // calculate the right position to get values
      // GPR starts with r3;
      Address gprarray = regsavearea.plus(-3 * BYTES_IN_ADDRESS);
      Address fprarray = regsavearea.plus(8 * BYTES_IN_ADDRESS - 2 * BYTES_IN_ADDRESS);

      // call the common function for SVR4
      packageArgumentForSVR4(argTypes, argObjectArray, gprarray, fprarray, overflowarea, gpr, fpr, env);

      return argObjectArray;
    } else {
      if (VM.VerifyAssertions) VM._assert(false);
      return null;
    }
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
  static Object[] packageParameterFromVarArgSVR4(RVMMethod targetMethod, Address argAddress) {
    if (VM.BuildForSVR4ABI || VM.BuildForMachOABI) {
      TypeReference[] argTypes = targetMethod.getParameterTypes();
      int argCount = argTypes.length;
      Object[] argObjectArray = new Object[argCount];

      JNIEnvironment env = RVMThread.getCurrentThread().getJNIEnv();

      // the va_list has following layout on PPC/Linux
      // GPR FPR 0 0   (4 bytes)
      // overflowarea  (pointer)
      // reg_save_area (pointer)
      Address va_list_addr = argAddress;
      int word1 = va_list_addr.loadWord().toInt();
      int gpr = word1 >> 24;
      int fpr = (word1 >> 16) & 0x0FF;
      Address overflowarea = va_list_addr.loadAddress(Offset.fromIntSignExtend(BYTES_IN_ADDRESS));
      Address regsavearea = va_list_addr.loadAddress(Offset.fromIntSignExtend(2 * BYTES_IN_ADDRESS));

      if (VM.BuildForSVR4ABI) {
        //overflowarea is aligned to 8 bytes
        if (VM.VerifyAssertions) VM._assert(overflowarea.toWord().and(Word.fromIntZeroExtend(0x07)).isZero());
      }

      //adjust gpr and fpr to normal numbering, make life easier
      gpr += 3;
      fpr += 1;

      // not set the starting gprs array address
      // and fpr starting array address, so we can use gpr and fpr to
      // calculate the right position to get values
      // GPR starts with r3;
      Address gprarray = regsavearea.plus(-3 * BYTES_IN_ADDRESS);
      Address fprarray = regsavearea.plus(8 * BYTES_IN_ADDRESS - 2 * BYTES_IN_ADDRESS);

      // call the common function for SVR4
      packageArgumentForSVR4(argTypes, argObjectArray, gprarray, fprarray, overflowarea, gpr, fpr, env);

      return argObjectArray;
    } else {
      if (VM.VerifyAssertions) VM._assert(false);
      return null;
    }
  }

  static void packageArgumentForSVR4(TypeReference[] argTypes, Object[] argObjectArray, Address gprarray,
                                     Address fprarray, Address overflowarea, int gpr, int fpr, JNIEnvironment env) {
    if (VM.BuildForSVR4ABI) {
      // also make overflow offset, we may need to round it
      Offset overflowoffset = Offset.zero();
      int argCount = argTypes.length;

      // now interpret values by types, see PPC ABI
      for (int i = 0; i < argCount; i++) {
        if (argTypes[i].isFloatType() || argTypes[i].isDoubleType()) {
          int loword, hiword;
          if (fpr > LAST_OS_PARAMETER_FPR) {
            // overflow, OTHER
            // round it, bytes are saved from lowest to highest one, regardless endian
            overflowoffset = overflowoffset.plus(7).toWord().and(Word.fromIntSignExtend(-8)).toOffset();
            hiword = overflowarea.loadInt(overflowoffset);
            overflowoffset = overflowoffset.plus(BYTES_IN_INT);
            loword = overflowarea.loadInt(overflowoffset);
            overflowoffset = overflowoffset.plus(BYTES_IN_INT);
          } else {
            // get value from fpr, increase fpr by 1
            hiword = fprarray.plus(fpr * BYTES_IN_DOUBLE).loadInt();
            loword = fprarray.plus(fpr * BYTES_IN_DOUBLE + BYTES_IN_INT).loadInt();
            fpr += 1;
          }
          long doubleBits = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
          if (argTypes[i].isFloatType()) {
            argObjectArray[i] = Reflection.wrapFloat((float) (Double.longBitsToDouble(doubleBits)));
          } else { // double type
            argObjectArray[i] = Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));
          }

          //              VM.sysWriteln("double "+Double.longBitsToDouble(doubleBits));

        } else if (argTypes[i].isLongType()) {
          int loword, hiword;
          if (gpr > LAST_OS_PARAMETER_GPR - 1) {
            // overflow, OTHER
            // round overflowoffset, assuming overflowarea is aligned to 8 bytes
            overflowoffset = overflowoffset.plus(7).toWord().and(Word.fromIntSignExtend(-8)).toOffset();
            hiword = overflowarea.loadInt(overflowoffset);
            overflowoffset = overflowoffset.plus(BYTES_IN_INT);
            loword = overflowarea.loadInt(overflowoffset);
            overflowoffset = overflowoffset.plus(BYTES_IN_INT);

            // va-ppc.h makes last gpr useless
            gpr = 11;
          } else {
            gpr += (gpr + 1) & 0x01;  // if gpr is even, gpr += 1
            hiword = gprarray.plus(gpr * 4).loadInt();
            loword = gprarray.plus((gpr + 1) * 4).loadInt();
            gpr += 2;
          }
          long longBits = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
          argObjectArray[i] = Reflection.wrapLong(longBits);

          //              VM.sysWriteln("long 0x"+Long.toHexString(longBits));
        } else {
          // int type left now
          int ivalue;
          if (gpr > LAST_OS_PARAMETER_GPR) {
            // overflow, OTHER
            ivalue = overflowarea.loadInt(overflowoffset);
            overflowoffset = overflowoffset.plus(4);
          } else {
            ivalue = gprarray.plus(gpr * 4).loadInt();
            gpr += 1;
          }

          //              VM.sysWriteln("int "+ivalue);

          if (argTypes[i].isBooleanType()) {
            argObjectArray[i] = Reflection.wrapBoolean(ivalue);
          } else if (argTypes[i].isByteType()) {
            argObjectArray[i] = Reflection.wrapByte((byte) ivalue);
          } else if (argTypes[i].isShortType()) {
            argObjectArray[i] = Reflection.wrapShort((short) ivalue);
          } else if (argTypes[i].isCharType()) {
            argObjectArray[i] = Reflection.wrapChar((char) ivalue);
          } else if (argTypes[i].isIntType()) {
            argObjectArray[i] = Reflection.wrapInt(ivalue);
          } else if (argTypes[i].isReferenceType()) {
            argObjectArray[i] = env.getJNIRef(ivalue);
          } else {
            if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
          }
        }
      }
    } else if (VM.BuildForMachOABI) {
      // also make overflow offset, we may need to round it
      Offset overflowoffset = Offset.zero();
      int argCount = argTypes.length;

      // now interpret values by types, see PPC ABI
      for (int i = 0; i < argCount; i++) {
        int regIncrementGpr = 1;
        if (argTypes[i].isFloatType() || argTypes[i].isDoubleType()) {
          int loword, hiword;
          if (fpr > LAST_OS_PARAMETER_FPR) {
            // overflow, OTHER
            // round it, bytes are saved from lowest to highest one, regardless endian
            // overflowoffset = (overflowoffset + 7) & -8;
            hiword = overflowarea.loadInt(overflowoffset);
            overflowoffset = overflowoffset.plus(BYTES_IN_INT);
            loword = overflowarea.loadInt(overflowoffset);
            overflowoffset = overflowoffset.plus(BYTES_IN_INT);
          } else {
            // get value from fpr, increase fpr by 1
            hiword = fprarray.plus(fpr * BYTES_IN_DOUBLE).loadInt();
            loword = fprarray.plus(fpr * BYTES_IN_DOUBLE + BYTES_IN_INT).loadInt();
          }
          long doubleBits = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
          if (argTypes[i].isFloatType()) {
            argObjectArray[i] = Reflection.wrapFloat((float) (Double.longBitsToDouble(doubleBits)));
          } else { // double type
            argObjectArray[i] = Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));
            regIncrementGpr = 2;
          }

          //              VM.sysWriteln("double "+Double.longBitsToDouble(doubleBits));

        } else if (argTypes[i].isLongType()) {
          int loword, hiword;
          if (gpr > LAST_OS_PARAMETER_GPR - 1) {
            // overflow, OTHER
            // round overflowoffset, assuming overflowarea is aligned to 8 bytes
            //overflowoffset = (overflowoffset + 7) & -8;
            hiword = overflowarea.loadInt(overflowoffset);
            overflowoffset = overflowoffset.plus(BYTES_IN_INT);
            loword = overflowarea.loadInt(overflowoffset);
            overflowoffset = overflowoffset.plus(BYTES_IN_INT);

            // va-ppc.h makes last gpr useless
            regIncrementGpr = 2;
          } else {
            hiword = gprarray.loadInt(Offset.fromIntZeroExtend(gpr * 4));
            loword = gprarray.loadInt(Offset.fromIntZeroExtend((gpr + 1) * 4));
            regIncrementGpr = 2;
          }
          long longBits = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
          argObjectArray[i] = Reflection.wrapLong(longBits);

        } else {
          // int type left now
          int ivalue;
          if (gpr > LAST_OS_PARAMETER_GPR) {
            // overflow, OTHER
            ivalue = overflowarea.loadInt(overflowoffset);
            overflowoffset = overflowoffset.plus(BYTES_IN_INT);
          } else {
            ivalue = gprarray.loadInt(Offset.fromIntZeroExtend(gpr * 4));
          }

          //              VM.sysWriteln("int "+ivalue);

          if (argTypes[i].isBooleanType()) {
            argObjectArray[i] = Reflection.wrapBoolean(ivalue);
          } else if (argTypes[i].isByteType()) {
            argObjectArray[i] = Reflection.wrapByte((byte) ivalue);
          } else if (argTypes[i].isShortType()) {
            argObjectArray[i] = Reflection.wrapShort((short) ivalue);
          } else if (argTypes[i].isCharType()) {
            argObjectArray[i] = Reflection.wrapChar((char) ivalue);
          } else if (argTypes[i].isIntType()) {
            argObjectArray[i] = Reflection.wrapInt(ivalue);
          } else if (argTypes[i].isReferenceType()) {
            argObjectArray[i] = env.getJNIRef(ivalue);
          } else {
            if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
          }
        }
        gpr += regIncrementGpr;
      }
    } else {
      if (VM.VerifyAssertions) VM._assert(false);
    }
  }

  /**
   * Repackage the arguments passed as a variable argument list into an array of Object,
   * used by the JNI functions CallStatic<type>MethodV
   * @param targetMethod   The target {@link RVMMethod}
   * @param argAddress an address into the C space for the array of jvalue unions;
   *                   each element is 2-word and holds the argument of the appropriate type
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParameterFromVarArg(RVMMethod targetMethod, Address argAddress) {
    TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    // get the JNIEnvironment for this thread in case we need to dereference any object arg
    JNIEnvironment env = RVMThread.getCurrentThread().getJNIEnv();

    // VM.sysWrite("JNI packageParameterFromVarArg: packaging " + argCount + " arguments\n");

    Address addr = argAddress;
    for (int i = 0; i < argCount; i++) {
      long hiword = VM.BuildFor64Addr ? addr.loadLong() : (long) addr.loadInt();

      // VM.sysWrite("JNI packageParameterFromVarArg:  arg " + i + " = " + hiword +
      // " or " + VM.intAsHexString(hiword) + "\n");

      addr = addr.plus(BYTES_IN_ADDRESS);

      // convert and wrap the argument according to the expected type

      if (argTypes[i].isFloatType()) {
        // NOTE:  in VarArg convention, C compiler will expand a float to a double that occupy 2 words
        // so we have to extract it as a double and convert it back to a float
        if (VM.BuildFor32Addr) {
          int loword = addr.loadInt();
          addr = addr.plus(BYTES_IN_ADDRESS);
          long doubleBits = (hiword << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
          argObjectArray[i] = Reflection.wrapFloat((float) (Double.longBitsToDouble(doubleBits)));
        } else {
          argObjectArray[i] = Reflection.wrapFloat((float) (Double.longBitsToDouble(hiword)));
        }
      } else if (argTypes[i].isDoubleType()) {
        if (VM.BuildFor32Addr) {
          int loword = addr.loadInt();
          addr = addr.plus(BYTES_IN_ADDRESS);
          long doubleBits = (hiword << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
          argObjectArray[i] = Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));
        } else {
          argObjectArray[i] = Reflection.wrapDouble(Double.longBitsToDouble(hiword));
        }
      } else if (argTypes[i].isLongType()) {
        if (VM.BuildFor32Addr) {
          int loword = addr.loadInt();
          addr = addr.plus(BYTES_IN_ADDRESS);
          long longValue = (hiword << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
          argObjectArray[i] = Reflection.wrapLong(longValue);
        } else {
          argObjectArray[i] = Reflection.wrapLong(hiword);
        }
      } else if (argTypes[i].isBooleanType()) {
        // the 0/1 bit is stored in the high byte
        argObjectArray[i] = Reflection.wrapBoolean((int) hiword);
      } else if (argTypes[i].isByteType()) {
        // the target byte is stored in the high byte
        argObjectArray[i] = Reflection.wrapByte((byte) hiword);
      } else if (argTypes[i].isCharType()) {
        // char is stored in the high 2 bytes
        argObjectArray[i] = Reflection.wrapChar((char) hiword);
      } else if (argTypes[i].isShortType()) {
        // short is stored in the high 2 bytes
        argObjectArray[i] = Reflection.wrapShort((short) hiword);
      } else if (argTypes[i].isReferenceType()) {
        // for object, the arg is a JREF index, dereference to get the real object
        argObjectArray[i] = env.getJNIRef((int) hiword);
      } else if (argTypes[i].isIntType()) {
        argObjectArray[i] = Reflection.wrapInt((int) hiword);
      } else {
        return null;
      }
    }
    return argObjectArray;
  }

  /**
   * Repackage the arguments passed as an array of jvalue into an array of Object,
   * used by the JNI functions CallStatic<type>MethodA
   * @param targetMethod the target {@link RVMMethod}
   * @param argAddress an address into the C space for the array of jvalue unions;
   *                   each element is 2-word and holds the argument of the appropriate type
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParameterFromJValue(RVMMethod targetMethod, Address argAddress) {
    TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    // get the JNIEnvironment for this thread in case we need to dereference any object arg
    JNIEnvironment env = RVMThread.getCurrentThread().getJNIEnv();

    // VM.sysWrite("JNI packageParameterFromJValue: packaging " + argCount + " arguments\n");

    for (int i = 0; i < argCount; i++) {
      Address addr = argAddress.plus(BYTES_IN_DOUBLE * i);
      long hiword = VM.BuildFor64Addr ? addr.loadLong() : (long) addr.loadInt();

      // VM.sysWrite("JNI packageParameterFromJValue:  arg " + i + " = " + hiword +
      //          " or " + VM.intAsHexString(hiword) + "\n");

      // convert and wrap the argument according to the expected type

      if (argTypes[i].isFloatType()) {
        argObjectArray[i] =
            Reflection.wrapFloat(Float.intBitsToFloat((int) (hiword >>> (BITS_IN_ADDRESS - BITS_IN_FLOAT))));
      } else if (argTypes[i].isDoubleType()) {
        if (VM.BuildFor32Addr) {
          int loword = addr.plus(BYTES_IN_ADDRESS).loadInt();
          long doubleBits = (hiword << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
          argObjectArray[i] = Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));
        } else {
          argObjectArray[i] = Reflection.wrapDouble(Double.longBitsToDouble(hiword));
        }
      } else if (argTypes[i].isLongType()) {
        if (VM.BuildFor32Addr) {
          int loword = addr.plus(BYTES_IN_ADDRESS).loadInt();
          long longValue = (hiword << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
          argObjectArray[i] = Reflection.wrapLong(longValue);
        } else {
          argObjectArray[i] = Reflection.wrapLong(hiword);
        }
      } else if (argTypes[i].isBooleanType()) {
        // the 0/1 bit is stored in the high byte
        argObjectArray[i] = Reflection.wrapBoolean((int) (hiword >>> (BITS_IN_ADDRESS - BITS_IN_BOOLEAN)));
      } else if (argTypes[i].isByteType()) {
        // the target byte is stored in the high byte
        argObjectArray[i] = Reflection.wrapByte((byte) (hiword >>> (BITS_IN_ADDRESS - BITS_IN_BYTE)));
      } else if (argTypes[i].isCharType()) {
        // char is stored in the high 2 bytes
        argObjectArray[i] = Reflection.wrapChar((char) (hiword >>> (BITS_IN_ADDRESS - BITS_IN_CHAR)));
      } else if (argTypes[i].isShortType()) {
        // short is stored in the high 2 bytes
        argObjectArray[i] = Reflection.wrapShort((short) (hiword >>> (BITS_IN_ADDRESS - BITS_IN_SHORT)));
      } else if (argTypes[i].isReferenceType()) {
        // for object, the arg is a JREF index, dereference to get the real object
        argObjectArray[i] = env.getJNIRef((int) hiword);
      } else if (argTypes[i].isIntType()) {
        argObjectArray[i] = Reflection.wrapInt((int) (hiword >>> (BITS_IN_ADDRESS - BITS_IN_INT)));
      } else {
        return null;
      }
    }
    return argObjectArray;
  }
}
