/*
 * (C) Copyright IBM Corp. 2001, 2003
 */
//$Id$
package com.ibm.JikesRVM.jni;

import com.ibm.JikesRVM.*;

/**
 * Define the stackframes used for JNI transition frames.
 * There are two kinds of transitions Java -> native method
 * and native method -> JNIFunction.
 * 
 * @author Dave Grove
 * @author Ton Ngo 
 * @author Steve Smith
 */
public interface VM_JNIStackframeLayoutConstants extends VM_RegisterConstants,
                                                         VM_StackframeLayoutConstants {

  /////////////////////////////////////////////////////////////
  //  Java to native function transition
  // VM_JNICompiler.compile(VM_NativeMethod)
  /////////////////////////////////////////////////////////////
  
  // number of volatile registers that may carry parameters to the
  // native code
  // GPR4-10 = 7 words  (does not include R3)
  // FPR1-6  = 12 words
  public static final int JNI_OS_PARAMETER_REGISTER_SIZE   =  
    (LAST_OS_PARAMETER_GPR - (FIRST_OS_PARAMETER_GPR + 1) + 1)*BYTES_IN_ADDRESS
	+ (LAST_OS_VARARG_PARAMETER_FPR - FIRST_OS_PARAMETER_FPR + 1)*BYTES_IN_DOUBLE ;   
  
  // offset into the Java to Native glue frame, relative to the Java caller frame
  // the definitions are chained to the first one, JNI_JTOC_OFFSET
  // saved R17-R31 + JNIEnv + GCflag + affinity + saved JTOC
  public static final int JNI_RVM_NONVOLATILE_OFFSET       = BYTES_IN_ADDRESS;
  public static final int JNI_ENV_OFFSET                   = JNI_RVM_NONVOLATILE_OFFSET + 
    ((LAST_NONVOLATILE_GPR - FIRST_NONVOLATILE_GPR + 1) * BYTES_IN_ADDRESS);
  public static final int JNI_OS_PARAMETER_REGISTER_OFFSET = JNI_ENV_OFFSET + BYTES_IN_ADDRESS;

  //-#if RVM_FOR_AIX
  public static final int JNI_PROLOG_RETURN_ADDRESS_OFFSET  = JNI_OS_PARAMETER_REGISTER_OFFSET + JNI_OS_PARAMETER_REGISTER_SIZE;
  public static final int JNI_GC_FLAG_OFFSET                = JNI_PROLOG_RETURN_ADDRESS_OFFSET  + BYTES_IN_ADDRESS;          // 108
  public static final int JNI_SAVE_AREA_SIZE                = JNI_GC_FLAG_OFFSET;
  //-#endif

  //-#if RVM_FOR_LINUX || RVM_FOR_OSX
  // LINUX saves prologue address in lr slot of glue frame (1), see picture blow
  public static final int JNI_GC_FLAG_OFFSET                = JNI_OS_PARAMETER_REGISTER_OFFSET + JNI_OS_PARAMETER_REGISTER_SIZE;
  public static final int JNI_MINI_FRAME_POINTER_OFFSET     = 
    VM_Memory.alignUp(JNI_GC_FLAG_OFFSET + STACKFRAME_HEADER_SIZE, STACKFRAME_ALIGNMENT);

  public static final int JNI_SAVE_AREA_SIZE = JNI_MINI_FRAME_POINTER_OFFSET;
  // Linux uses different transition scheme, in Java-to-Native transition
  // stackframe, it has two mini frames. Comparing to AIX transition frame,
  // Linux version inserts a RVM frame header right above JNI_SAVE_AREA
  //
  //   |------------|
  //   | fp         | <- Java to C glue frame (2)
  //   | lr         |
  //   | 0          | <- spill area, see VM_Compiler.getFrameSize
  //   | 1          |
  //   |.......     |
  //   |------------| 
  //   | fp         | <- Java to C glue frame (1)
  //   | cmid       | 
  //   | lr         |
  //   | padding    |
  //   | GC flag    |
  //   | Affinity   |
  //   | ........   |
  //   |------------| 
  //   | fp         | <- Java caller frame
  //   | mid        |
  //
  // VM_Runtime.unwindNativeStackFrame will return a pointer to glue frame (1)
  // lr slot of frame (2) holds out of line machine code which should be in 
  // bootimage, I believe GC won't move that part. JNIGCIterator would 
  // return lr or frame (2) as the result of getReturnAddressAddress
  //-#endif
  
  /////////////////////////////////////////////////////////
  // Native code to JNI Function (Java) glue frame
  // VM_JNICompiler.generateGlueCodeForJNIMethod
  /////////////////////////////////////////////////////////
  
  //   Volatile GPR 3-10 save area  -  8 * BYTES_IN_ADDRESS
  //   Volatile FPR 1-6  save area  -  6 * BYTES_IN_DOUBLE
  public static final int JNI_GLUE_SAVED_VOL_SIZE  = 
    (LAST_OS_PARAMETER_GPR - FIRST_OS_PARAMETER_GPR + 1)* BYTES_IN_ADDRESS
    +(LAST_OS_VARARG_PARAMETER_FPR - FIRST_OS_PARAMETER_FPR + 1) * BYTES_IN_DOUBLE;

  public static final int JNI_GLUE_RVM_EXTRA_GPRS_SIZE =
    (LAST_RVM_RESERVED_NV_GPR - FIRST_RVM_RESERVED_NV_GPR + 1) * BYTES_IN_ADDRESS;
  
  // offset to previous to java frame 1 (* BYTES_IN_ADDRESS)
  public static final int JNI_GLUE_FRAME_OTHERS  = 1 * BYTES_IN_ADDRESS;
  
  public static final int JNI_GLUE_FRAME_SIZE =               
    VM_Memory.alignUp(STACKFRAME_HEADER_SIZE
                      + JNI_GLUE_SAVED_VOL_SIZE                      
                      + JNI_GLUE_RVM_EXTRA_GPRS_SIZE
                      + JNI_GLUE_FRAME_OTHERS,
                      STACKFRAME_ALIGNMENT);
  
  // offset to caller, where to store offset to previous java frame 
  public static final int JNI_GLUE_OFFSET_TO_PREV_JFRAME = - JNI_GLUE_FRAME_OTHERS;
	
  // offset into the vararg save area within the native to Java glue frame
  // to saved regs GPR 6-10 & FPR 1-6, the volatile regs containing vararg arguments
  //
  public static final int VARARG_AREA_OFFSET = STACKFRAME_HEADER_SIZE + (3*BYTES_IN_ADDRESS);    // the RVM link area and saved GPR 3-5

}
