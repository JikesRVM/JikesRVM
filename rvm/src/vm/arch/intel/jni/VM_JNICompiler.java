/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.jni;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

/**
 * This class compiles the prolog and epilog for all code that makes
 * the transition between Java and Native C
 * <pre>
 * 2 cases:
 *  -from Java to C:  all user-defined native methods
 *  -C to Java:  all JNI functions in VM_JNIFunctions.java
 * </pre>
 * @author Ton Ngo
 * @author Steve Smith
 */
public class VM_JNICompiler implements VM_BaselineConstants {

  // offsets to saved regs and addresses in java to C glue frames
  // EDI (JTOC) and EBX are nonvolatile registers in RVM
  //
  private static final int SAVED_GPRS = 5; 
  public static final int EDI_SAVE_OFFSET = STACKFRAME_BODY_OFFSET;
  public static final int EBX_SAVE_OFFSET = STACKFRAME_BODY_OFFSET - WORDSIZE;
  public static final int EBP_SAVE_OFFSET = EBX_SAVE_OFFSET - WORDSIZE;
  public static final int JNI_RETURN_ADDRESS_OFFSET = EBP_SAVE_OFFSET - WORDSIZE;
  public static final int JNI_ENV_OFFSET = JNI_RETURN_ADDRESS_OFFSET - WORDSIZE;

  // following used in prolog & epilog for JNIFunctions
  // offset of saved offset to preceeding java frame
  public static final int SAVED_JAVA_FP_OFFSET = STACKFRAME_BODY_OFFSET;

  // following used in VM_Compiler to compute offset to first local:
  // includes 5 words:
  //   SAVED_JAVA_FP,  VM_JNIEnvironment, S0 (ECX), EBX, and JTOC (EDI)
  public static final int SAVED_GPRS_FOR_JNI = 5;

  /*****************************************************************
   * Handle the Java to C transition:  native methods
   *
   */
  public static synchronized VM_CompiledMethod compile (VM_NativeMethod method) {
    VM_JNICompiledMethod cm = (VM_JNICompiledMethod)VM_CompiledMethods.createCompiledMethod(method, VM_CompiledMethod.JNI);
    VM_Assembler asm     = new VM_Assembler(100);   // some size for the instruction array
    VM_Address nativeIP         = method.getNativeIP();
    // recompute some constants
    int parameterWords   = method.getParameterWords();

    // Meaning of constant offset into frame:
    // STACKFRAME_HEADER_SIZE =  12              (CHECK ??)
    // SAVED_GPRS = 4 words/registers
    // Stack frame:
    //        on entry          after prolog
    //
    //      high address        high address
    //      |          |        |          | Caller frame
    //      |          |        |          |
    // +    |arg 0     |        |arg 0     |    -> firstParameterOffset
    // +    |arg 1     |        |arg 1     |
    // +    |...       |        |...       |
    // +8   |arg n-1   |        |arg n-1   |    
    // +4   |returnAddr|        |returnAddr|
    //  0   +          +        +saved FP  + <---- FP for glue frame
    // -4   |          |        |methodID  |
    // -8   |          |        |saved EDI |    -> STACKFRAME_BODY_OFFSET = -8
    // -C   |          |        |saved EBX |
    // -10  |          |        |saved EBP |
    // -14  |          |        |returnAddr|  (return from OutOfLine to generated epilog)    
    // -18  |          |        |saved ENV |  (VM_JNIEnvironment)
    // -1C  |          |        |arg n-1   |  reordered args to native method
    // -20  |          |        | ...      |  ...
    // -24  |          |        |arg 1     |  ...
    // -28  |          |        |arg 0     |  ...
    // -2C  |          |        |class/obj |  required second arg to native method
    // -30  |          |        |jniEnv    |  required first arg to native method
    // -34  |          |        |          |    
    //      |          |        |          |    
    //      |          |        |          |    
    //       low address         low address


    // TODO:  check and resize stack once on the lowest Java to C transition
    // on the stack.  Not needed if we use the thread original stack

    // Fill in frame header - similar to normal prolog
    prepareStackHeader(asm, method, cm.getId());

    // Process the arguments - specific to method being called
    storeParametersForLintel(asm, method);
    
    // load address of native code to invoke into S0
    asm.emitMOV_Reg_Imm (S0, nativeIP.toInt());  

    // branch to outofline code in bootimage
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.invokeNativeFunctionInstructionsField.getOffset());

    // return here from VM_OutOfLineMachineCode upon return from native code
    // PR and RVM JTOC restored, T0,T1 contain return from native call

    // If the return type is reference, look up the real value in the JNIref array 

    // S0 <- threads' VM_JNIEnvironment
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0,
                                              VM_Entrypoints.activeThreadField.getOffset());
    asm.emitMOV_Reg_RegDisp (S0, S0, VM_Entrypoints.jniEnvField.getOffset());

    if (method.getReturnType().isReferenceType()) {
      asm.emitADD_Reg_RegDisp(T0, S0, VM_Entrypoints.JNIRefsField.getOffset());      // T0 <- address of entry (not index)
      asm.emitMOV_Reg_RegInd (T0, T0);   // get the reference
    } else if (method.getReturnType().isLongType()) {
      asm.emitPUSH_Reg(T1);    // need to use T1 in popJNIrefForEpilog and to swap order T0-T1  
    }

    // pop frame in JNIRefs array (assumes S0 holds VM_JNIEnvironment)
    popJNIrefForEpilog(asm);                                
    
    // then swap order of T0 and T1 for long
    if (method.getReturnType().isLongType()) {
      asm.emitMOV_Reg_Reg(T1, T0);  
      asm.emitPOP_Reg(T0);
    }

    // CHECK EXCEPTION AND BRANCH TO ATHROW CODE OR RETURN NORMALLY

    // get pending exception from JNIEnv
    asm.emitMOV_Reg_RegDisp (EBX, S0, VM_Entrypoints.JNIPendingExceptionField.getOffset());  // EBX <- JNIPendingException
    asm.emitMOV_RegDisp_Imm (S0, VM_Entrypoints.JNIPendingExceptionField.getOffset(), 0);    // clear the current pending exception

    asm.emitCMP_Reg_Imm(EBX, 0);   // check for exception pending:  JNIPendingException = non zero
    VM_ForwardReference fr = asm.forwardJcc(asm.EQ);            // Br if yes

    // if pending exception, discard the return value and current stack frame
    // then jump to athrow 
    asm.emitMOV_Reg_Reg     (T0, EBX);
    asm.emitMOV_Reg_RegDisp (T1, JTOC, VM_Entrypoints.athrowMethod.getOffset()); // acquire jump addr before restoring nonvolatiles

    asm.emitMOV_Reg_Reg     (SP, EBP);                     // discard current stack frame
    asm.emitMOV_Reg_RegDisp (JTOC, SP, EDI_SAVE_OFFSET);   // restore nonvolatile EDI register
    asm.emitMOV_Reg_RegDisp (EBX, SP, EBX_SAVE_OFFSET);    // restore nonvolatile EBX register
    asm.emitMOV_Reg_RegDisp (EBP, SP, EBP_SAVE_OFFSET);    // restore nonvolatile EBP register

    asm.emitPOP_RegDisp     (PR, VM_Entrypoints.framePointerField.getOffset());

    // don't use CALL since it will push on the stack frame the return address to here 
    asm.emitJMP_Reg(T1); // jumps to VM_Runtime.athrow

    fr.resolve(asm);  // branch to here if no exception 

    // no exception, proceed to return to caller    
    asm.emitMOV_Reg_Reg(SP, EBP);                           // discard current stack frame

    asm.emitMOV_Reg_RegDisp (JTOC, SP, EDI_SAVE_OFFSET);   // restore nonvolatile EDI register
    asm.emitMOV_Reg_RegDisp (EBX, SP, EBX_SAVE_OFFSET);    // restore nonvolatile EBX register
    asm.emitMOV_Reg_RegDisp (EBP, SP, EBP_SAVE_OFFSET);    // restore nonvolatile EBP register

    asm.emitPOP_RegDisp     (PR, VM_Entrypoints.framePointerField.getOffset());

    // return to caller 
    // pop parameters from stack (Note that parameterWords does not include "this")
    if (method.isStatic())
      asm.emitRET_Imm(parameterWords << LG_WORDSIZE); 
    else
      asm.emitRET_Imm((parameterWords+1) << LG_WORDSIZE); 

    VM_MachineCode machineCode = new VM_MachineCode(asm.getMachineCodes(), null);
    cm.compileComplete(machineCode.getInstructions());
    return cm;
  }

  /**************************************************************
   * Prepare the stack header for Java to C transition.
   * <pre>
   *         before               after
   *       high address         high address
   *       |          |         |          | Caller frame
   *       |          |         |          |
   *  +    |arg 0     |         |arg 0     |    
   *  +    |arg 1     |         |arg 1     |
   *  +    |...       |         |...       |
   *  +8   |arg n-1   |         |arg n-1   |    
   *  +4   |returnAddr|         |returnAddr|
   *   0   +          +         +saved FP  + <---- FP for glue frame
   *  -4   |          |         |methodID  |
   *  -8   |          |         |saved EDI |  (EDI == JTOC - for baseline methods)  
   *  -C   |          |         |saved EBX |    
   *  -10  |          |         |          |    
   *  
   * </pre>
   */
  static void prepareStackHeader(VM_Assembler asm, VM_Method method, int compiledMethodId) {

    // set 2nd word of header = return address already pushed by CALL
    asm.emitPUSH_RegDisp (PR, VM_Entrypoints.framePointerField.getOffset());

    // start new frame:  set FP to point to the new frame
    VM_ProcessorLocalState.emitMoveRegToField(asm,
                                              VM_Entrypoints.framePointerField.getOffset(),
                                              SP);

    // set first word of header: method ID
    asm.emitMOV_RegDisp_Imm (SP, STACKFRAME_METHOD_ID_OFFSET, compiledMethodId); 

    // save nonvolatile registrs: EDI, EBX, EBP
    asm.emitMOV_RegDisp_Reg (SP, EDI_SAVE_OFFSET, JTOC); 
    asm.emitMOV_RegDisp_Reg (SP, EBX_SAVE_OFFSET, EBX);
    asm.emitMOV_RegDisp_Reg (SP, EBP_SAVE_OFFSET, EBP);
    
    asm.emitMOV_Reg_Reg     (EBP, SP); // Establish EBP as the framepointer for use in the rest of the glue frame

    // restore JTOC with the value saved in VM_Processor.jtoc for use in prolog 
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC,
                                              VM_Entrypoints.jtocField.getOffset());
  }

  /**************************************************************
   * Process the arguments.
   *
   * <pre>
   *   -insert the 2 JNI args
   *   -replace pointers
   *   -reverse the order of the args from Java to fit the C convention
   *   -
   *
   *         before               after
   *
   *       high address         high address
   *       |          |         |          | Caller frame
   *       |          |         |          | 
   *  +    |arg 0     |         |arg 0     |    -> firstParameterOffset
   *  +    |arg 1     |         |arg 1     | 
   *  +    |...       |         |...       | 
   *  +8   |arg n-1   |         |arg n-1   |    
   *  +4   |returnAddr|         |returnAddr| 
   *   0   +saved FP  +         +saved FP  + <---- FP for glue frame
   *  -4   |methodID  |         |methodID  | 
   *  -8   |saved EDI |         |saved EDI |    -> STACKFRAME_BODY_OFFSET = -8
   *  -C   |saved EBX |         |saved EBX |    
   *  -10  |          |         |returnAddr|  (return from OutOfLine to generated epilog)    
   *  -14  |          |         |saved PR  |
   *  -18  |          |         |arg n-1   |  reordered args to native method (firstLocalOffset
   *  -1C  |          |         | ...      |  ...
   *  -20  |          |         |arg 1     |  ...
   *  -24  |          |         |arg 0     |  ...
   *  -28  |          |         |class/obj |  required second arg 
   *  -2C  |          |   SP -> |jniEnv    |  required first arg  (emptyStackOffset)
   *  -30  |          |         |          |    
   *       |          |         |          |    
   *        low address          low address
   * </pre>
   */
  static void storeParametersForLintel(VM_Assembler asm, VM_Method method) {
    VM_Class klass           = method.getDeclaringClass();
    int parameterWords       = method.getParameterWords();
    int savedRegistersSize   = SAVED_GPRS<<LG_WORDSIZE;
    int firstLocalOffset     = STACKFRAME_BODY_OFFSET - savedRegistersSize ;
    int emptyStackOffset     = firstLocalOffset - ((parameterWords+2) << LG_WORDSIZE) + WORDSIZE;
    int firstParameterOffset = STACKFRAME_BODY_OFFSET + STACKFRAME_HEADER_SIZE + (parameterWords<<LG_WORDSIZE);
    int firstActualParameter;


    VM_TypeReference[] types = method.getParameterTypes();   // does NOT include implicit this or class ptr
    int numArguments = types.length;                // number of arguments for this method
    int numRefArguments = 1;                        // initialize with count of 1 for the JNI arg
    int numFloats = 0;                              // number of float or double arguments

    // quick count of number of references
    for (int i=0; i<numArguments; i++) {
      if (types[i].isReferenceType())
        numRefArguments++;
      if (types[i].isFloatType() || types[i].isDoubleType())
        numFloats++;
    }


    // first push the parameters passed in registers back onto the caller frame
    // to free up the registers for use
    // The number of registers holding parameter is 
    // VM_RegisterConstants.NUM_PARAMETER_GPRS
    // Their indices are in VM_RegisterConstants.VOLATILE_GPRS[]
    int gpr = 0;
    // note that firstParameterOffset does not include "this"
    int parameterOffset = firstParameterOffset;   

    // handle the "this" parameter
    if (!method.isStatic()) {
      asm.emitMOV_RegDisp_Reg(EBP, firstParameterOffset+WORDSIZE, 
                              VOLATILE_GPRS[gpr]);
      gpr++;
    }
    
    for (int i=0; i<numArguments && gpr<NUM_PARAMETER_GPRS; i++) {
      if (types[i].isDoubleType()) {
        parameterOffset -= 2*WORDSIZE;
        continue;
      } else if (types[i].isFloatType()) {
        parameterOffset -= WORDSIZE;
        continue;
      } else if (types[i].isLongType()) {
        if (gpr<NUM_PARAMETER_GPRS) {   // get the hi word
          asm.emitMOV_RegDisp_Reg(EBP, parameterOffset, VOLATILE_GPRS[gpr]);
          gpr++;
          parameterOffset -= WORDSIZE;
        }
        if (gpr<NUM_PARAMETER_GPRS) {    // get the lo word
          asm.emitMOV_RegDisp_Reg(EBP, parameterOffset, VOLATILE_GPRS[gpr]);
          gpr++;
          parameterOffset -= WORDSIZE;
        }
      } else {
        if (gpr<NUM_PARAMETER_GPRS) {   // all other types fit in one word
          asm.emitMOV_RegDisp_Reg(EBP, parameterOffset, VOLATILE_GPRS[gpr]);
          gpr++;
          parameterOffset -= WORDSIZE;
        }
      }
    }


    // bump SP to set aside room for the args + 2 additional JNI args
    asm.emitADD_Reg_Imm (SP, emptyStackOffset);                       

    // SP should now point to the bottom of the argument stack, 
    // which is arg[n-1]
    
    // Prepare the side stack to hold new refs
    // Leave S0 holding the threads' VM_JNIEnvironment
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0,
                                              VM_Entrypoints.activeThreadField.getOffset());
    asm.emitMOV_Reg_RegDisp (S0, S0, VM_Entrypoints.jniEnvField.getOffset());        // S0 <- jniEnv

    // save PR in the jniEnv for JNI call from native
    VM_ProcessorLocalState.emitStoreProcessor(asm, S0, VM_Entrypoints.JNIEnvSavedPRField.getOffset());

    // save VM_JNIEnvironemt in stack frame so we can find it when we return
    asm.emitMOV_RegDisp_Reg(EBP, VM_JNICompiler.JNI_ENV_OFFSET, S0);

    // save FP for glue frame in JNI env - used by GC when in C
    asm.emitMOV_RegDisp_Reg (S0, VM_Entrypoints.JNITopJavaFPField.getOffset(), EBP);  // jniEnv.JNITopJavaFP <- FP

    //********************************************
    // Between HERE and THERE, S0 and T0 are in use
    // S0 holds the VM_JNIEnvironemnt for the thread.
    // T0 holds the address to TOP of JNIRefs stack
    //    (set up by startJNIrefForProlog, used by pushJNIRef)
    // >>>> HERE <<<<
    startJNIrefForProlog(asm, numRefArguments);
    
    // Insert the JNIEnv* arg at the first entry:  
    // This is an interior pointer to VM_JNIEnvironment, which is held in S0.
    asm.emitMOV_Reg_Reg     (EBX, S0);
    asm.emitADD_Reg_Imm     (EBX, VM_Entrypoints.JNIExternalFunctionsField.getOffset());
    asm.emitMOV_RegDisp_Reg (EBP, emptyStackOffset, EBX);                  // store as 1st arg

    // Insert the JNI arg at the second entry: class or object as a jref index
    // first reload JTOC,  baseline compiler assumes JTOC register -> jtoc
    // TODO: DAVE: doesn't it already have the JTOC????
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC,
                                              VM_Entrypoints.jtocField.getOffset());
    if (method.isStatic()) {
      // For static method, push on arg stack the VM_Class object
      //    jtoc[tibOffset] -> class TIB ptr -> first TIB entry -> class object -> classForType
      klass.getClassForType();     // ensure the Java class object is created
      int tibOffset = klass.getTibOffset();
      asm.emitMOV_Reg_RegDisp (EBX, JTOC, tibOffset);
      asm.emitMOV_Reg_RegInd  (EBX, EBX);
      asm.emitMOV_Reg_RegDisp (EBX, EBX, VM_Entrypoints.classForTypeField.getOffset());
      firstActualParameter = 0;
    } else {
      // For nonstatic method, "this" pointer should be the first arg in the caller frame,
      // make it the 2nd arg in the glue frame
      asm.emitMOV_Reg_RegDisp (EBX, EBP, firstParameterOffset+WORDSIZE);
      firstActualParameter = 1;
    }

    // Generate the code to push this pointer in ebx on to the JNIRefs stack 
    // and use the JREF index in its place
    // Assume: S0 is the jniEnv pointer (left over from above)
    //         T0 contains the address to TOP of JNIRefs stack
    // Kill value in ebx
    // On return, ebx contains the JREF index
    pushJNIref(asm);
    asm.emitMOV_RegDisp_Reg (EBP, emptyStackOffset + WORDSIZE, EBX);  // store as 2nd arg

    // Now fill in the rest:  copy parameters from caller frame into glue frame 
    // in reverse order for C
    int i=parameterWords - 1;   
    int fpr = numFloats-1;
    for (int argIndex=numArguments-1; argIndex>=0; argIndex--) {

      // for reference, substitute with a jref index
      if (types[argIndex].isReferenceType()) {
        asm.emitMOV_Reg_RegDisp (EBX, EBP, firstParameterOffset - (i*WORDSIZE));
        asm.emitCMP_Reg_Imm(EBX, 0);
        VM_ForwardReference beq = asm.forwardJcc(asm.EQ);
        pushJNIref(asm);
        beq.resolve(asm);
        asm.emitMOV_RegDisp_Reg (EBP, emptyStackOffset + (WORDSIZE*(2+ i)), EBX);
        i--;
      
      // for float and double, the first NUM_PARAMETER_FPRS args have
      // been loaded in the FPU stack, need to pop them from there
      } else if (types[argIndex].isDoubleType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
          // pop this 2-word arg from the FPU stack
          asm.emitFSTP_RegDisp_Reg_Quad(EBP, emptyStackOffset + (WORDSIZE*(2+ i - 1)), FP0);    
        } else {
          // copy this 2-word arg from the caller frame
          asm.emitMOV_Reg_RegDisp (EBX, EBP, firstParameterOffset - (i*WORDSIZE));
          asm.emitMOV_RegDisp_Reg (EBP, emptyStackOffset + (WORDSIZE*(2 + i -1)), EBX);
          asm.emitMOV_Reg_RegDisp (EBX, EBP, firstParameterOffset - ((i-1)*WORDSIZE));
          asm.emitMOV_RegDisp_Reg (EBP, emptyStackOffset + (WORDSIZE*(2 + i)), EBX);      
        }
        i-=2;
        fpr--;
      } else if (types[argIndex].isFloatType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
          // pop this 1-word arg from the FPU stack
          asm.emitFSTP_RegDisp_Reg(EBP, emptyStackOffset + (WORDSIZE*(2+ i)), FP0);
        } else {
          // copy this 1-word arg from the caller frame
          asm.emitMOV_Reg_RegDisp (EBX, EBP, firstParameterOffset - (i*WORDSIZE));
          asm.emitMOV_RegDisp_Reg (EBP, emptyStackOffset + (WORDSIZE*(2+ i)), EBX);
        }
        i--;
        fpr--;
      } else if (types[argIndex].isLongType()) {
        //  copy other 2-word parameters: observe the high/low order when moving
        asm.emitMOV_Reg_RegDisp (EBX, EBP, firstParameterOffset - (i*WORDSIZE));
        asm.emitMOV_RegDisp_Reg (EBP, emptyStackOffset + (WORDSIZE*(2 + i - 1)), EBX);
        asm.emitMOV_Reg_RegDisp (EBX, EBP, firstParameterOffset - ((i-1)*WORDSIZE));
        asm.emitMOV_RegDisp_Reg (EBP, emptyStackOffset + (WORDSIZE*(2 + i)), EBX);
        i-=2;
      } else {
        // copy other 1-word parameters
        asm.emitMOV_Reg_RegDisp (EBX, EBP, firstParameterOffset - (i*WORDSIZE));
        asm.emitMOV_RegDisp_Reg (EBP, emptyStackOffset + (WORDSIZE*(2+ i)), EBX);
        i--;
      }
    }

    // >>>> THERE <<<<
    // End use of T0 and S0
  }

  /**************************************************************
   * Generate code to convert a pointer value to a JREF index.
   * 
   * <pre>
   * This includes the following steps:
   *   (1) start by calling startJNIrefForProlog()
   *   (2) for each reference, put it in ebx and call pushJNIref() 
   *       to convert; the handler will be left in ebx
   *  
   *   +-------+
   *   |       |  <-JNIRefsMax (byte index of last entry)
   *   |       |
   *   |       |
   *   |       |  <-JNIRefsTop (byte index of valid top entry)
   *   |       |
   *   |       |
   *   |FPindex|  <-JNIRefsSavedFP (byte index of Java to C transition)
   *   |       |
   *   |       |
   *   |       |
   *   |       |
   *   |       |
   *   |       |
   *   |       |  <-JNIRefs
   *   +-------+
   * </pre>
   */

  /**
   * Start a new frame for this Java to C transition.
   * <pre>
   * Expect: 
   *    -S0 contains a pointer to the VM_Thread.jniEnv
   * Perform these steps:
   *    -push current SavedFP index 
   *    -set SaveFP index <- current TOP
   * Leave registers ready for more push onto the jniEnv.JNIRefs array
   *    -S0 holds jniEnv so we can update jniEnv.JNIRefsTop and 
   *    -T0 holds address of top =  starting address of jniEnv.JNIRefs array + jniEnv.JNIRefsTop
   *     T0 is to be incremented before each push
   *    S0              ebx                         T0
   *  jniEnv        
   *    .         jniEnv.JNIRefs             jniEnv.JNIRefsTop
   *    .               .                    jniEnv.JNIRefsTop + 4
   *    .         jniEnv.JNIRefsSavedFP            .
   *    .               .                    jniEnv.JNIRefsTop
   *    .               .                    address(JNIRefsTop)             
   *    .
   * </pre>
   */
  static void startJNIrefForProlog(VM_Assembler asm, int numRefsExpected) {

    // on entry, S0 contains a pointer to the VM_Thread.jniEnv
    asm.emitMOV_Reg_RegDisp (EBX, S0, VM_Entrypoints.JNIRefsField.getOffset());    // ebx <- JNIRefs base

    // get and check index of top for overflow
    asm.emitMOV_Reg_RegDisp (T0, S0, VM_Entrypoints.JNIRefsTopField.getOffset());  // T0 <- index of top
    asm.emitADD_Reg_Imm(T0, numRefsExpected * WORDSIZE);                // increment index of top 
    asm.emitCMP_Reg_RegDisp(T0, S0, VM_Entrypoints.JNIRefsMaxField.getOffset());   // check against JNIRefsMax for overflow 
    // TODO:  Do something if overflow!!!

    asm.emitADD_RegDisp_Imm (S0, VM_Entrypoints.JNIRefsTopField.getOffset(), WORDSIZE); // increment index of top
    asm.emitMOV_Reg_RegDisp (T0, S0, VM_Entrypoints.JNIRefsTopField.getOffset());  // T0 <- index of top
    asm.emitADD_Reg_Reg(T0, EBX);                                       // T0 <- address of top (not index)

    // start new frame:  push current JNIRefsSavedFP onto stack and set it to the new top index    
    asm.emitMOV_Reg_RegDisp (EBX, S0, VM_Entrypoints.JNIRefsSavedFPField.getOffset()); // ebx <- jniEnv.JNIRefsSavedFP
    asm.emitMOV_RegInd_Reg  (T0, EBX);                                   // push (T0) <- ebx
    asm.emitMOV_Reg_RegDisp (T0, S0, VM_Entrypoints.JNIRefsTopField.getOffset());   // reload T0 <- index of top
    asm.emitMOV_RegDisp_Reg (S0, VM_Entrypoints.JNIRefsSavedFPField.getOffset(), T0); // jniEnv.JNIRefsSavedFP <- index of top 

    // leave T0 with address pointing to the top of the frame for more push later
    asm.emitADD_Reg_RegDisp(T0, S0, VM_Entrypoints.JNIRefsField.getOffset());       // recompute T0 <- address of top (not index)

    // and go ahead and bump up the Top offset by the amount expected
    asm.emitADD_RegDisp_Imm(S0, VM_Entrypoints.JNIRefsTopField.getOffset(), numRefsExpected * WORDSIZE);
  }

  /**
   * Push a pointer value onto the JNIRefs array.
   *
   * <pre>
   * Expect:
   *   -T0 pointing to the address of the valid top 
   *   -the pointer value in register ebx
   *   -the space in the JNIRefs array has checked for overflow 
   *   by startJNIrefForProlog()
   * Perform these steps:
   *   -increment the JNIRefsTop index in ebx by 4
   *   -push a pointer value in ebx onto the top of the JNIRefs array
   *   -put the JNIRefsTop index into the sourceReg as the replacement for the pointer
   * </pre>
   * Note:  jniEnv.JNIRefsTop is not updated yet
   */
  static void pushJNIref(VM_Assembler asm) {
    asm.emitADD_Reg_Imm (T0, WORDSIZE);                            // increment top address
    asm.emitMOV_RegInd_Reg(T0, EBX);                               // store ref at top
    asm.emitMOV_Reg_Reg (EBX, T0);                                 // replace ref in ebx with top address
    asm.emitSUB_Reg_RegDisp (EBX, S0, VM_Entrypoints.JNIRefsField.getOffset());   // subtract base address to get offset
  }

  /**
   * Generate the code to pop the frame in JNIRefs array for this Java to C transition.
   * <pre>
   * Expect:
   *  -JTOC, PR registers are valid
   *  -S0 contains a pointer to the VM_Thread.jniEnv
   *  -EBX and T1 are available as scratch registers
   * Perform these steps:
   *  -jniEnv.JNIRefsTop <- jniEnv.JNIRefsSavedFP - 4
   *  -jniEnv.JNIRefsSavedFP <- (jniEnv.JNIRefs + jniEnv.JNIRefsSavedFP)
   * </pre>
   */
  static void popJNIrefForEpilog(VM_Assembler asm) {
    // on entry, S0 contains a pointer to the VM_Thread.jniEnv
    // set TOP to point to entry below the last frame
    asm.emitMOV_Reg_RegDisp (T1, S0, VM_Entrypoints.JNIRefsSavedFPField.getOffset());    // ebx <- JNIRefsSavedFP
    asm.emitMOV_RegDisp_Reg (S0, VM_Entrypoints.JNIRefsTopField.getOffset(), T1);        // JNIRefsTop <- ebx
    asm.emitSUB_RegDisp_Imm (S0, VM_Entrypoints.JNIRefsTopField.getOffset(), WORDSIZE);  // JNIRefsTop -= 4

    // load savedFP with the index to the last frame
    asm.emitMOV_Reg_RegDisp (EBX, S0, VM_Entrypoints.JNIRefsField.getOffset());          // ebx <- JNIRefs base
    asm.emitMOV_Reg_RegIdx  (EBX, EBX, T1, asm.BYTE, 0);                                 // ebx <- (JNIRefs base + SavedFP index)
    asm.emitMOV_RegDisp_Reg (S0, VM_Entrypoints.JNIRefsSavedFPField.getOffset(), EBX);   // JNIRefsSavedFP <- ebx
  }
  

  /*****************************************************************
   * Handle the C to Java transition:  JNI methods in VM_JNIFunctions.java.
   * 
   * <pre>
   * NOTE:
   *   -We need PR to access Java environment; we can get it from the 
   *    JNIEnv* (which is an interior pointer to the VM_JNIEnvironment)
   *   -Unlike the powerPC scheme which has a special prolog preceding
   *    the normal Java prolog, the Intel scheme replaces the Java prolog
   *    completely with the special prolog
   *
   *            Stack on entry            Stack at end of prolog after call
   *             high memory                       high memory
   *            |            |                   |            |
   *    EBP ->  |saved FP    |                   |saved FP    |
   *            |  ...       |                   |  ...       |
   *            |            |                   |            |
   *            |arg n-1     |                   |arg n-1     |
   * native     |  ...       |                   |  ...       |       
   * caller     |arg 0       |                   |arg 0       |
   *    ESP ->  |return addr |                   |return addr |
   *            |            |           EBP ->  |saved FP    |
   *            |            |                   |methodID    | normal MethodID for JNI function
   *            |            |                   |saved JavaFP| offset to preceeding java frame
   *            |            |                   |saved edi   | to be used for JTOC
   *            |            |                   |  "   ebx   | to be used for nonvolatile
   *            |            |                   |  "   ecx   | to be used for scrach
   *            |            |                   |  "   esi   | to be used for PR
   *            |            |                   |arg 0       | copied in reverse order
   *            |            |                   |  ...       |
   *            |            |           ESP ->  |arg n-1     |
   *            |            |                   |            | normally compiled Java code continue
   *            |            |                   |            |
   *            |            |                   |            |
   *            |            |                   |            |
   *             low memory                        low memory
   * </pre>
   */
  public static void generateGlueCodeForJNIMethod(VM_Assembler asm, VM_NormalMethod method, int methodID) {
    // set 2nd word of header = return address already pushed by CALL
    // NOTE: C calling convention is that EBP contains the caller's framepointer.
    //       Therefore our C to Java transition frames must follow this protocol,
    //       not the RVM protocol in which the caller's framepointer is in 
    //       pr.framePointer and EBP is a nonvolatile register.
    asm.emitPUSH_Reg(EBP);          

    // start new frame:  set FP to point to the new frame
    asm.emitMOV_Reg_Reg (EBP, SP); 

    // set first word of header: method ID
    asm.emitPUSH_Imm (methodID); 
    asm.emitSUB_Reg_Imm (SP, WORDSIZE);  // leave room for saved -> preceeding java frame, set later

    // save registers that will be used in RVM, to be restored on return to C
    // TODO: I don't think we need to do this: C has no nonvolatile registers on Linux/x86 --dave
    // TODO: DAVE
    asm.emitPUSH_Reg(JTOC); 
    asm.emitPUSH_Reg(EBX);         
    asm.emitPUSH_Reg(S0);         
    VM_ProcessorLocalState.emitPushProcessor(asm);
    
    // Adjust first param from JNIEnv* to VM_JNIEnvironment.
    asm.emitSUB_RegDisp_Imm(EBP, (2*WORDSIZE), VM_Entrypoints.JNIExternalFunctionsField.getOffset());

    // copy the arguments in reverse order
    VM_TypeReference[] types = method.getParameterTypes();   // does NOT include implicit this or class ptr
    int numArguments = types.length;                // number of arguments for this method
    int argOffset = 2;           // add 2 to get to arg area in caller frame
    for (int i=0; i<numArguments; i++) {
      if (types[i].isLongType() || types[i].isDoubleType()) {
        // handle 2-words case:
        asm.emitMOV_Reg_RegDisp (EBX, EBP, ((argOffset+1)*WORDSIZE));  
        asm.emitPUSH_Reg(EBX);
        asm.emitMOV_Reg_RegDisp (EBX, EBP, (argOffset*WORDSIZE));  
        asm.emitPUSH_Reg(EBX);
        argOffset+=2;
      } else {
        // Handle 1-word case:
        asm.emitMOV_Reg_RegDisp (EBX, EBP, (argOffset*WORDSIZE));  
        asm.emitPUSH_Reg(EBX);
        argOffset++;
      }
    }
    
    // START of code sequence to atomically change processor status from IN_NATIVE
    // to IN_JAVA, looping in a call to sysVirtualProcessorYield if BLOCKED_IN_NATIVE
    int retryLabel = asm.getMachineCodeIndex();     // backward branch label

    // Restore PR from VM_JNIEnvironment 
    asm.emitMOV_Reg_RegDisp (EBX, EBP, (2*WORDSIZE));   // pick up arg 0 (from callers frame)
    VM_ProcessorLocalState.emitSetProcessor(asm, EBX, VM_Entrypoints.JNIEnvSavedPRField.getOffset());

    // reload JTOC from vitual processor 
    // NOTE: EDI saved in glue frame is just EDI (opt compiled code uses it as normal non-volatile)
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC,
                                              VM_Entrypoints.jtocField.getOffset());

    // T0 gets PR.statusField
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, T0,
                                              VM_Entrypoints.vpStatusField.getOffset());
    asm.emitCMP_Reg_Imm (T0, VM_Processor.IN_NATIVE);      // jmp if still IN_NATIVE
    VM_ForwardReference fr = asm.forwardJcc(asm.EQ);       // if so, skip 3 instructions

    // blocked in native, do pthread yield
    asm.emitMOV_Reg_RegDisp(T0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());  // T0<-bootrecord addr
    asm.emitCALL_RegDisp(T0, VM_Entrypoints.sysVirtualProcessorYieldIPField.getOffset());
    asm.emitJMP_Imm (retryLabel);                          // retry from beginning

    fr.resolve(asm);      // branch here if IN_NATIVE, attempt to go to IN_JAVA

    // T0 (EAX) contains "old value" (required for CMPXCNG instruction)
    // S0 contains address of status word to be swapped
    asm.emitMOV_Reg_Imm (T1, VM_Processor.IN_JAVA);  // T1<-new value (IN_JAVA)
    VM_ProcessorLocalState.emitCompareAndExchangeField(asm, 
                                                       VM_Entrypoints.vpStatusField.getOffset(),
                                                       T1); // atomic compare-and-exchange
    asm.emitJCC_Cond_Imm(asm.NE,retryLabel);

    // END of code sequence to change state from IN_NATIVE to IN_JAVA

    // status is now IN_JAVA. GC can not occur while we execute on a processor
    // in this state, so it is safe to access fields of objects. 
    // RVM JTOC and PR registers have been restored and EBX contains a pointer to
    // the thread's VM_JNIEnvironment.

    // done saving, bump SP to reserve room for the local variables
    // SP should now be at the point normally marked as emptyStackOffset
    int numLocalVariables = method.getLocalWords() - method.getParameterWords();
    asm.emitSUB_Reg_Imm (SP, (numLocalVariables << LG_WORDSIZE));
   
    // Retrieve -> preceeding "top" java FP from jniEnv and save in current 
    // frame of JNIFunction
    asm.emitMOV_Reg_RegDisp (S0, EBX, VM_Entrypoints.JNITopJavaFPField.getOffset());  
    
    // get offset from current FP and save in hdr of current frame
    asm.emitSUB_Reg_Reg (S0, EBP);    
    asm.emitMOV_RegDisp_Reg (EBP, SAVED_JAVA_FP_OFFSET, S0);

    // put framePointer in VP following Jikes RVM conventions.
    VM_ProcessorLocalState.emitMoveRegToField(asm,
                                              VM_Entrypoints.framePointerField.getOffset(),
                                              EBP);

    // at this point: JTOC and PR have been restored & 
    // processor status = IN_JAVA,
    // arguments for the call have been setup, space on the stack for locals
    // has been acquired.

    // finally proceed with the normal Java compiled code
    // skip the thread switch test for now, see VM_Compiler.genThreadSwitchTest(true)
    asm.emitNOP(); // end of prologue marker
  }

  public static void generateEpilogForJNIMethod(VM_Assembler asm, VM_Method method) {
    // assume RVM PR regs still valid. potentially T1 & T0 contain return
    // values and should not be modified. we use regs saved in prolog and restored
    // before return to do whatever needs to be done.  does not assume JTOC is valid,
    // and may use it as scratch reg.

    // if returning long, switch the order of the hi/lo word in T0 and T1
    if (method.getReturnType().isLongType()) {
      asm.emitPUSH_Reg(T1);    
      asm.emitMOV_Reg_Reg(T1, T0);  
      asm.emitPOP_Reg(T0);      
    }

    // current processor status is IN_JAVA, so we only GC at yieldpoints

    // S0 <- VM_JNIEnvironment
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0, VM_Entrypoints.activeThreadField.getOffset()); 
    asm.emitMOV_Reg_RegDisp(S0, S0, VM_Entrypoints.jniEnvField.getOffset());

    // set jniEnv TopJavaFP using value saved in frame in prolog
    asm.emitMOV_Reg_RegDisp(EDI, EBP, SAVED_JAVA_FP_OFFSET);      // JTOC<-saved TopJavaFP (offset)
    asm.emitADD_Reg_Reg(EDI, EBP);                                // change offset from FP into address
    asm.emitMOV_RegDisp_Reg (S0, VM_Entrypoints.JNITopJavaFPField.getOffset(), EDI); // jniEnv.TopJavaFP <- JTOC

    // in case thread has migrated to different PR, reset saved PRs to current PR
    VM_ProcessorLocalState.emitStoreProcessor(asm, S0,
                                              VM_Entrypoints.JNIEnvSavedPRField.getOffset());

    // change current processor status to IN_NATIVE
    VM_ProcessorLocalState.emitMoveImmToField(asm, VM_Entrypoints.vpStatusField.getOffset(), VM_Processor.IN_NATIVE);

    // reload native/C nonvolatile regs - saved in prolog
    // what about FPRs
    // TODO: DAVE we really don't need to do this.  C has no nonvols on Linux/x86
    VM_ProcessorLocalState.emitPopProcessor(asm);
    asm.emitPOP_Reg(S0);         
    asm.emitPOP_Reg(EBX);         
    asm.emitPOP_Reg(JTOC); 

    // NOTE: C expects the framepointer to be restored to EBP, so 
    //       the epilogue for the C to Java glue code must follow that 
    //       convention, not the RVM one!
    //       Also note that RVM treats EBP is a nonvolatile, so we don't
    //       explicitly save/restore it.
    asm.emitMOV_Reg_Reg(SP, EBP); // discard current stack frame
    asm.emitPOP_Reg(EBP);
    asm.emitRET();                // return to caller
  }
}
