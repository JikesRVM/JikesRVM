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

import static org.jikesrvm.ppc.RegisterConstants.FIRST_NONVOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_OS_PARAMETER_FPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_OS_PARAMETER_GPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_RVM_RESERVED_NV_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_NONVOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_OS_PARAMETER_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_OS_VARARG_PARAMETER_FPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_RVM_RESERVED_NV_GPR;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_ALIGNMENT;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_HEADER_SIZE;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_DOUBLE;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.Memory;

/**
 * Define the stackframes used for JNI transition frames.
 * There are two kinds of transitions Java -> native method
 * and native method -> JNIFunction.
 */
public final class JNIStackframeLayoutConstants {

  /////////////////////////////////////////////////////////////
  //  Java to native function transition
  // JNICompiler.compile(NativeMethod)
  /////////////////////////////////////////////////////////////

  public static final int NATIVE_FRAME_HEADER_SIZE =
      VM.BuildForPower64ELF_ABI ? 6 * BYTES_IN_ADDRESS /* fp + cr + lr + res + res + toc */ :
      2 * BYTES_IN_ADDRESS /* fp + lr */;

  // number of volatile registers that may carry parameters to the
  // native code
  // GPR4-10 = 7 words  (does not include R3)
  // FPR1-6  = 12 words
  public static final int JNI_OS_PARAMETER_REGISTER_SIZE =
      (LAST_OS_PARAMETER_GPR.value() - (FIRST_OS_PARAMETER_GPR.value() + 1) + 1) * BYTES_IN_ADDRESS +
      (LAST_OS_VARARG_PARAMETER_FPR.value() - FIRST_OS_PARAMETER_FPR.value() + 1) * BYTES_IN_DOUBLE;

  // offsets into the "non-OS" calling convention portion of the
  // Java to Native glue frame, relative to the Java caller frame.
  // The contents of this part of the stack frame are:
  // saved RNONVOLATILE_GPRs (for updating by GC) + JNIEnv + GCflag + standard RVM stackframe header
  public static final int JNI_RVM_NONVOLATILE_OFFSET = BYTES_IN_ADDRESS;
  public static final int JNI_ENV_OFFSET =
      JNI_RVM_NONVOLATILE_OFFSET + ((LAST_NONVOLATILE_GPR.value() - FIRST_NONVOLATILE_GPR.value() + 1) * BYTES_IN_ADDRESS);
  public static final int JNI_OS_PARAMETER_REGISTER_OFFSET = JNI_ENV_OFFSET + BYTES_IN_ADDRESS;

  public static final int JNI_GC_FLAG_OFFSET = JNI_OS_PARAMETER_REGISTER_OFFSET + JNI_OS_PARAMETER_REGISTER_SIZE;

  public static final int JNI_MINI_FRAME_POINTER_OFFSET = Memory.alignUp(JNI_GC_FLAG_OFFSET + STACKFRAME_HEADER_SIZE, STACKFRAME_ALIGNMENT);

  public static final int JNI_SAVE_AREA_SIZE = JNI_MINI_FRAME_POINTER_OFFSET;

  /////////////////////////////////////////////////////////
  // Native code to JNI Function (Java) glue frame
  // JNICompiler.generateGlueCodeForJNIMethod
  /////////////////////////////////////////////////////////

  //   Volatile GPR 3-10 save area  -  8 * BYTES_IN_ADDRESS
  //   Volatile FPR 1-6  save area  -  6 * BYTES_IN_DOUBLE
  public static final int JNI_GLUE_SAVED_VOL_SIZE =
      (LAST_OS_PARAMETER_GPR.value() - FIRST_OS_PARAMETER_GPR.value() + 1) * BYTES_IN_ADDRESS +
      (LAST_OS_VARARG_PARAMETER_FPR.value() - FIRST_OS_PARAMETER_FPR.value() + 1) * BYTES_IN_DOUBLE;

  public static final int JNI_GLUE_RVM_EXTRA_GPRS_SIZE = (LAST_RVM_RESERVED_NV_GPR.value() - FIRST_RVM_RESERVED_NV_GPR.value() + 1) * BYTES_IN_ADDRESS;

  // offset to previous to java frame 1 (* BYTES_IN_ADDRESS)
  public static final int JNI_GLUE_FRAME_OTHERS = 1 * BYTES_IN_ADDRESS;

  public static final int JNI_GLUE_FRAME_SIZE =
      Memory.alignUp(STACKFRAME_HEADER_SIZE +
                        JNI_GLUE_SAVED_VOL_SIZE +
                        JNI_GLUE_RVM_EXTRA_GPRS_SIZE +
                        JNI_GLUE_FRAME_OTHERS, STACKFRAME_ALIGNMENT);

  // offset to caller, where to store offset to previous java frame
  public static final int JNI_GLUE_OFFSET_TO_PREV_JFRAME = -JNI_GLUE_FRAME_OTHERS;

  // offset into the vararg save area within the native to Java glue frame
  // to saved regs GPR 6-10 & FPR 1-6, the volatile regs containing vararg arguments
  //
  public static final int VARARG_AREA_OFFSET = STACKFRAME_HEADER_SIZE + (3 * BYTES_IN_ADDRESS);    // the RVM link area and saved GPR 3-5

  private JNIStackframeLayoutConstants() {
    // prevent instantiation
  }

}
