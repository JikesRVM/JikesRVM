/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * This class compiles the prolog and epilog for all code that makes
 * the transition between Java and Native C
 * 2 cases:
 *  -from Java to C:  all user-defined native methods
 *  -C to Java:  all JNI functions in VM_JNIFunctions.java
 *
 * @author Ton Ngo
 * @author Steve Smith
 */
public class VM_JNICompiler implements VM_JNILinuxConstants, VM_BaselineConstants {

  // offsets to saved regs and addresses in java to C glue frames
  // EDI (JTOC) and EBX are nonvolatile registers in RVM
  //
  private static final int SAVED_GPRS = 5; 
  static final int EDI_SAVE_OFFSET = STACKFRAME_BODY_OFFSET;
  static final int EBX_SAVE_OFFSET = STACKFRAME_BODY_OFFSET - WORDSIZE;
  static final int EBP_SAVE_OFFSET = EBX_SAVE_OFFSET - WORDSIZE;
  static final int JNI_RETURN_ADDRESS_OFFSET = EBP_SAVE_OFFSET - WORDSIZE;
  static final int JNI_PR_OFFSET = JNI_RETURN_ADDRESS_OFFSET - WORDSIZE;

  // following used in prolog & epilog for JNIFunctions
  // offset of saved offset to preceeding java frame
  static final int SAVED_JAVA_FP_OFFSET = STACKFRAME_BODY_OFFSET;

  // following used in VM_Compiler to compute offset to first local:
  // includes 5 words:
  //   SAVED_JAVA_FP,  PR (ESI), S0 (ECX), EBX, and JTOC (EDI)
  static final int SAVED_GPRS_FOR_JNI = 5;

  /*****************************************************************
   * Handle the Java to C transition:  native methods
   *
   */

  static VM_MachineCode generateGlueCodeForNative ( int compiledMethodId, VM_Method method ) {
    VM_Assembler asm	 = new VM_Assembler(100);   // some size for the instruction array
    int nativeIP         = method.getNativeIP();
    // recompute some constants
    int parameterWords   = method.getParameterWords();

    // Meaning of constant offset into frame:
    // STACKFRAME_HEADER_SIZE =  12              (CHECK ??)
    // SAVED_GPRS = 4 words/registers
    // Stack frame:
    //        on entry          after prolog
    //
    //      high address	high address
    //      |          |	|          | Caller frame
    //      |          |	|          |
    // +    |arg 0     |	|arg 0     |    -> firstParameterOffset
    // +    |arg 1     |	|arg 1     |
    // +    |...       |	|...       |
    // +8   |arg n-1   |	|arg n-1   |    
    // +4   |returnAddr|	|returnAddr|
    //  0   +	       +	+saved FP  + <---- FP for glue frame
    // -4   |	       |	|methodID  |
    // -8   |	       |	|saved EDI |    -> STACKFRAME_BODY_OFFSET = -8
    // -C   |	       |	|saved EBX |
    // -10  |	       |	|saved EBP |
    // -14  |	       |	|returnAddr|  (return from OutOfLine to generated epilog)    
    // -18  |	       |	|saved PR  |
    // -1C  |	       |	|arg n-1   |  reordered args to native method
    // -20  |	       |	| ...      |  ...
    // -24  |	       |	|arg 1     |  ...
    // -28  |	       |	|arg 0     |  ...
    // -2C  |	       |	|class/obj |  required second arg to native method
    // -30  |	       |	|jniEnv    |  required first arg to native method
    // -34  |	       |	|          |    
    //      |	       |	|          |    
    //      |	       |	|          |    
    //       low address	 low address


    if (VM.TraceCompilation)
      VM.sysWrite("VM_JNICompiler: begin compiling native " + method + "\n");

    // TODO:  check and resize stack once on the lowest Java to C transition
    // on the stack.  Not needed if we use the thread original stack

    // Fill in frame header - similar to normal prolog
    prepareStackHeader(asm, method, compiledMethodId);

    // Process the arguments - specific to method being called
    storeParametersForLintel(asm, method);
    
    // load address of native into S0
    asm.emitMOV_Reg_Imm (S0, nativeIP);  

    // branch to outofline code in bootimage
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.invokeNativeFunctionInstructionsField.getOffset());

    // return here from VM_OutOfLineMachineCode upon return from native code

    // PR and RVM JTOC restored, T0,T1 contain return from native call

    //If the return type is reference, look up the real value in the JNIref array 

    // S0 <- VM_Thread
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0,
                                              VM_Entrypoints.activeThreadField.getOffset());
    asm.emitMOV_Reg_RegDisp (S0, S0, VM_Entrypoints.jniEnvField.getOffset());        // S0 <- jniEnv    
    if (method.getReturnType().isReferenceType()) {
      asm.emitADD_Reg_RegDisp(T0, S0, VM_Entrypoints.JNIRefsField.getOffset());      // T0 <- address of entry (not index)
      asm.emitMOV_Reg_RegInd (T0, T0);   // get the reference
    } else if (method.getReturnType().isLongType()) {
      asm.emitPUSH_Reg(T1);    // need to use T1 in popJNIrefForEpilog and to swap order T0-T1  
    }

    // CHECK - may not do anything - leave below will remove whole frame
    // asm.emitPUSH_Reg(T0);                // push the return value onto the stack

    // pop frame in JNIRefs array (need to use
    // S0 <- VM_Thread
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0,
                                              VM_Entrypoints.activeThreadField.getOffset());
    asm.emitMOV_Reg_RegDisp (S0, S0, VM_Entrypoints.jniEnvField.getOffset());        // S0 <- jniEnv    
    popJNIrefForEpilog(asm);                                
    
    // then swap order of T0 and T1 for long
    if (method.getReturnType().isLongType()) {
      asm.emitMOV_Reg_Reg(T1, T0);  
      asm.emitPOP_Reg(T0);
    }

    // CHECK EXCEPTION AND BRANCH TO ATHROW CODE OR RETURN NORMALLY

    // get pending exception from JNIEnv
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0,
                                              VM_Entrypoints.activeThreadField.getOffset());
    asm.emitMOV_Reg_RegDisp (S0,  S0, VM_Entrypoints.jniEnvField.getOffset());        	  // S0 <- jniEnv    
    asm.emitMOV_Reg_RegDisp (EBX, S0, VM_Entrypoints.JNIPendingExceptionField.getOffset());  // EBX <- JNIPendingException
    asm.emitMOV_RegDisp_Imm (S0, VM_Entrypoints.JNIPendingExceptionField.getOffset(), 0);    // clear the current pending exception

    asm.emitCMP_Reg_Imm(EBX, 0);   // check for exception pending:  JNIPendingException = non zero
    VM_ForwardReference fr = asm.forwardJcc(asm.EQ);            // Br if yes

    // if pending exception, discard the return value and current stack frame
    // then jump to athrow 
    asm.emitMOV_Reg_Reg     (T0, EBX);
    asm.emitMOV_Reg_RegDisp (T1, JTOC, VM_Entrypoints.athrowMethod.getOffset()); // acquire jump addr before restoring nonvolatiles

    asm.emitMOV_Reg_Reg     (SP, EBP);                      // discard current stack frame
    asm.emitMOV_Reg_RegDisp (JTOC, SP, EDI_SAVE_OFFSET);   // restore nonvolatile EDI/JTOC register
    asm.emitMOV_Reg_RegDisp (EBX, SP, EBX_SAVE_OFFSET);    // restore nonvolatile EBX register
    asm.emitMOV_Reg_RegDisp (EBP, SP, EBP_SAVE_OFFSET);    // restore nonvolatile EBP register

    asm.emitPOP_RegDisp     (PR, VM_Entrypoints.framePointerField.getOffset());

    // don't use CALL since it will push on the stack frame the return address to here 
    asm.emitJMP_Reg(T1); // jumps to VM_Runtime.athrow

    fr.resolve(asm);  // branch to here if no exception 

    // no exception, proceed to return to caller    
    asm.emitMOV_Reg_Reg(SP, EBP);                           // discard current stack frame

    asm.emitMOV_Reg_RegDisp (JTOC, SP, EDI_SAVE_OFFSET);   // restore nonvolatile EDI/JTOC register
    asm.emitMOV_Reg_RegDisp (EBX, SP, EBX_SAVE_OFFSET);    // restore nonvolatile EBX register
    asm.emitMOV_Reg_RegDisp (EBP, SP, EBP_SAVE_OFFSET);    // restore nonvolatile EBP register

    asm.emitPOP_RegDisp     (PR, VM_Entrypoints.framePointerField.getOffset());

    // return to caller 
    // pop parameters from stack (Note that parameterWords does not include "this")
    if (method.isStatic())
      asm.emitRET_Imm(parameterWords << LG_WORDSIZE); 
    else
      asm.emitRET_Imm((parameterWords+1) << LG_WORDSIZE); 

    if (VM.TraceCompilation)
      VM.sysWrite("VM_Compiler: end compiling native " + method + "\n");
    
    // return asm.makeMachineCode();
    return new VM_MachineCode(asm.getMachineCodes(), null);

  }

  /**************************************************************
   * Prepare the stack header for Java to C transition
   *         before               after
   *	   high address		high address
   *	   |          |		|          | Caller frame
   *	   |          |		|          |
   *  +    |arg 0     |		|arg 0     |    
   *  +    |arg 1     |		|arg 1     |
   *  +    |...       |		|...       |
   *  +8   |arg n-1   |		|arg n-1   |    
   *  +4   |returnAddr|		|returnAddr|
   *   0   +	      +		+saved FP  + <---- FP for glue frame
   *  -4   |	      |		|methodID  |
   *  -8   |	      |		|saved EDI |  (EDI == JTOC - for baseline methods)  
   *  -C   |	      |		|saved EBX |    
   *  -10  |	      |	        |	   |	
   *  
   *  
   *  
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


    // save nonvolatile registrs: JTOC/EDI, EBX, EBP
    asm.emitMOV_RegDisp_Reg (SP, EDI_SAVE_OFFSET, JTOC); 
    asm.emitMOV_RegDisp_Reg (SP, EBX_SAVE_OFFSET, EBX);
    asm.emitMOV_RegDisp_Reg (SP, EBP_SAVE_OFFSET, EBP);
    
    asm.emitMOV_Reg_Reg     (EBP, SP); // Establish EBP as the framepointer for use in the rest of the glue frame

    // restore JTOC with the value saved in VM_Processor.jtoc for use in prolog 
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC,
                                              VM_Entrypoints.jtocField.getOffset());
  }

  /**************************************************************
   * Process the arguments:
   *   -insert the 2 JNI args
   *   -replace pointers
   *   -reverse the order of the args from Java to fit the C convention
   *   -
   *
   *         before               after
   *
   *	   high address		high address
   *	   |          | 	|          | Caller frame
   *	   |          |		|          | 
   *  +    |arg 0     | 	|arg 0     | 	-> firstParameterOffset
   *  +    |arg 1     |		|arg 1     | 
   *  +    |...       |		|...       | 
   *  +8   |arg n-1   | 	|arg n-1   | 	
   *  +4   |returnAddr|		|returnAddr| 
   *   0   +saved FP  + 	+saved FP  + <---- FP for glue frame
   *  -4   |methodID  |		|methodID  | 
   *  -8   |saved EDI | 	|saved EDI | 	-> STACKFRAME_BODY_OFFSET = -8
   *  -C   |saved EBX | 	|saved EBX | 	
   *  -10  |	      | 	|returnAddr|  (return from OutOfLine to generated epilog)    
   *  -14  |	      |	        |saved PR  |
   *  -18  |	      |	        |arg n-1   |  reordered args to native method (firstLocalOffset
   *  -1C  |	      |	        | ...      |  ...
   *  -20  |	      |  	|arg 1     |  ...
   *  -24  |	      |	        |arg 0     |  ...
   *  -28  |	      |	        |class/obj |  required second arg 
   *  -2C  |	      |   SP -> |jniEnv    |  required first arg  (emptyStackOffset)
   *  -30  |	      |	        |          |    
   *	   |          |  	|          | 	
   *	    low address		 low address
   */
  static void storeParametersForLintel(VM_Assembler asm, VM_Method method) {
    VM_Class klass	     = method.getDeclaringClass();
    int parameterWords       = method.getParameterWords();
    int savedRegistersSize   = SAVED_GPRS<<LG_WORDSIZE;
    int firstLocalOffset     = STACKFRAME_BODY_OFFSET - savedRegistersSize ;
    int emptyStackOffset     = firstLocalOffset - ((parameterWords+2) << LG_WORDSIZE) + WORDSIZE;
    int firstParameterOffset = STACKFRAME_BODY_OFFSET + STACKFRAME_HEADER_SIZE + (parameterWords<<LG_WORDSIZE);
    int firstActualParameter;


    VM_Type[] types = method.getParameterTypes();   // does NOT include implicit this or class ptr
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
    // Leave S0 holding the jniEnv pointer
    // S0 <- VM_Thread
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0,
                                              VM_Entrypoints.activeThreadField.getOffset());

    asm.emitMOV_Reg_RegDisp (S0, S0, VM_Entrypoints.jniEnvField.getOffset());        // S0 <- jniEnv

    // save PR in the jniEnv for JNI call from native
    VM_ProcessorLocalState.emitStoreProcessor(asm, S0, VM_Entrypoints.JNIEnvSavedPRField.getOffset());

    // save FP for glue frame in JNI env - used by GC when in C
    asm.emitMOV_RegDisp_Reg (S0, VM_Entrypoints.JNITopJavaFPField.getOffset(), EBP);  // jniEnv.JNITopJavaFP <- FP

    //********************************************
    // Between HERE and THERE, S0 and T0 are in use
    // >>>> HERE <<<<
    startJNIrefForProlog(asm, numRefArguments);
    
    // Insert the JNI arg at the first entry:  JNI_Environment as the pointer to 
    // the JNI functions array
    // pr -> thread -> jniEnv -> function array
    asm.emitMOV_Reg_RegDisp (EBX, S0, VM_Entrypoints.JNIEnvAddressField.getOffset()); // ebx <- JNIEnvAddress
    asm.emitMOV_RegDisp_Reg (EBP, emptyStackOffset, EBX);                  // store as 1st arg

    // added 7/05 - check later SES
    // store current processors status word address in word after 
    // passed ->JNIFunctions (in EBX). upon return or reentry to java this
    // word is tested & it is wrong to use the processor object to find its address
    // since it may have been moved by GC while in native code.
    //
    // ASSUME T1 (EDX) is available ??? looks like PR still valid
    // COULD use JTOC since it is reloaded immediately below - 
    
    // T1<-addr or processor statusword 
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, T1,
                                              VM_Entrypoints.vpStatusAddressField.getOffset());
    asm.emitMOV_RegDisp_Reg (EBX, WORDSIZE, T1);

    // Insert the JNI arg at the second entry: class or object as a jref index
    // first reload JTOC,  baseline compiler assumes JTOC register -> jtoc
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC,
                                              VM_Entrypoints.jtocField.getOffset());
    if (method.isStatic()) {
      // For static method, push on arg stack the VM_Class object
      //    jtoc[tibOffset] -> class TIB ptr -> first TIB entry -> class object -> classForType
      klass.getClassForType();     // ensure the Java class object is created
      int tibOffset = klass.getTibOffset();
      asm.emitMOV_Reg_RegDisp (EBX, JTOC, tibOffset);
      asm.emitMOV_Reg_RegInd (EBX, EBX);
      asm.emitMOV_Reg_RegDisp (EBX, EBX, VM_Entrypoints.classForTypeField.getOffset());
      firstActualParameter = 0;
    } else {
      // For nonstatic method, "this" pointer should be the first arg in the caller frame,
      // make it the 2nd arg in the glue frame
      asm.emitMOV_Reg_RegDisp (EBX, EBP, firstParameterOffset);
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

    // VM.sysWrite("VM_JNICompiler:  processing args "); 
    // VM.sysWrite(numArguments);
    // VM.sysWrite("\n");

    // Now fill in the rest:  copy parameters from caller frame into glue frame 
    // in reverse order for C
    int i=parameterWords - 1;   
    int fpr = numFloats-1;
    for (int argIndex=numArguments-1; argIndex>=0; argIndex--) {

      // for reference, substitute with a jref index
      if (types[argIndex].isReferenceType()) {
	asm.emitMOV_Reg_RegDisp (EBX, EBP, firstParameterOffset - (i*WORDSIZE));
	pushJNIref(asm);
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

    // don't need any more since the top was bumped at the beginning
    // endJNIrefForProlog(asm);

    // >>>> THERE <<<<
    // End use of T0 and S0
  }

  /**************************************************************
   * Generate code to convert a pointer value to a JREF index
   * This includes the following steps:
   *   (1) start by calling startJNIrefForProlog()
   *   (2) for each reference, put it in ebx and call pushJNIref() 
   *       to convert; the handler will be left in ebx
   *   (3) finish by calling endJNIrefForProlog()
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
   *
   */

  /**
   * Start a new frame for this Java to C transition:
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
   */
  static void startJNIrefForProlog(VM_Assembler asm, int numRefsExpected) {

    // on entry, S0 contains a pointer to the VM_Thread.jniEnv
    asm.emitMOV_Reg_RegDisp (EBX, S0, VM_Entrypoints.JNIRefsField.getOffset());    // ebx <- JNIRefs base

    // get and check index of top for overflow
    asm.emitMOV_Reg_RegDisp (T0, S0, VM_Entrypoints.JNIRefsTopField.getOffset());  // T0 <- index of top
    asm.emitADD_Reg_Imm(T0, numRefsExpected * WORDSIZE);                // increment index of top 
    asm.emitCMP_Reg_RegDisp(T0, S0, VM_Entrypoints.JNIRefsMaxField.getOffset());   // check against JNIRefsMax for overflow 
    // TODO:  Do something if overflow!!!

    // get and increment index of top 
    // asm.emitMOV_Reg_RegDisp (T0, S0, VM_Entrypoints.JNIRefsTopOffset);  // T0 <- index of top
    // asm.emitADD_Reg_Imm(T0, WORDSIZE);                                  // increment index of top        
    // asm.emitMOV_RegDisp_Reg (S0, VM_Entrypoints.JNIRefsTopOffset, T0);  // jniEnv.JNIRefsTop <- T0
    

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
   * Push a pointer value onto the JNIRefs array, 
   * Expect:
   *   -T0 pointing to the address of the valid top 
   *   -the pointer value in register ebx
   *   -the space in the JNIRefs array has checked for overflow 
   *   by startJNIrefForProlog()
   * Perform these steps:
   *   -increment the JNIRefsTop index in ebx by 4
   *   -push a pointer value in ebx onto the top of the JNIRefs array
   *   -put the JNIRefsTop index into the sourceReg as the replacement for the pointer
   * Note:  jniEnv.JNIRefsTop is not updated yet
   *
   */
  static void pushJNIref(VM_Assembler asm) {
    asm.emitADD_Reg_Imm (T0, WORDSIZE);                            // increment top address
    asm.emitMOV_RegInd_Reg(T0, EBX);                               // store ref at top
    asm.emitMOV_Reg_Reg (EBX, T0);                                 // replace ref in ebx with top address
    asm.emitSUB_Reg_RegDisp (EBX, S0, VM_Entrypoints.JNIRefsField.getOffset());   // subtract base address to get index
  }

  /**
   * Wrap up the access to the JNIRefs array
   * Expect:
   *   -T0 pointing to the address of the valid top 
   *   -S0 holding the pointer to jniEnv
   * Perform these steps:
   *   -recompute value in T0 as byte offset from jniEnv.JNIRefs base
   *   -store value in T0 back into jniEnv.JNIRefsTop
   *
   */
  // static void endJNIrefForProlog(VM_Assembler asm) {
  //   asm.emitMOV_Reg_RegDisp (EBX, S0, VM_Entrypoints.JNIRefsField.getOffset());    // ebx <- JNIRefs base
  //   asm.emitSUB_Reg_Reg     (T0, EBX);                                  // S0 <- index of top
  //   asm.emitMOV_RegDisp_Reg (S0, VM_Entrypoints.JNIRefsTopField.getOffset(), T0);  // jniEnv.JNIRefsTop <- T0
  // }

  /**
   * Generate the code to pop the frame in JNIRefs array for this Java to C transition
   * Expect:
   *  -JTOC, PR registers are valid
   *  -S0 contains a pointer to the VM_Thread.jniEnv
   *  -EBX and T1 are available as scratch registers
   * Perform these steps:
   *  -jniEnv.JNIRefsTop <- jniEnv.JNIRefsSavedFP - 4
   *  -jniEnv.JNIRefsSavedFP <- (jniEnv.JNIRefs + jniEnv.JNIRefsSavedFP)
   *
   */
  static void popJNIrefForEpilog(VM_Assembler asm) {
    
    // on entry, S0 contains a pointer to the VM_Thread.jniEnv
    // set TOP to point to entry below the last frame
    asm.emitMOV_Reg_RegDisp (T1, S0, VM_Entrypoints.JNIRefsSavedFPField.getOffset());    // ebx <- JNIRefsSavedFP
    asm.emitMOV_RegDisp_Reg (S0, VM_Entrypoints.JNIRefsTopField.getOffset(), T1);        // JNIRefsTop <- ebx
    asm.emitSUB_RegDisp_Imm (S0, VM_Entrypoints.JNIRefsTopField.getOffset(), WORDSIZE);  // JNIRefsTop -= 4

    // load savedFP with the index to the last frame
    asm.emitMOV_Reg_RegDisp (EBX, S0, VM_Entrypoints.JNIRefsField.getOffset());    // ebx <- JNIRefs base
    asm.emitMOV_Reg_RegIdx  (EBX, EBX, T1, asm.BYTE, 0);                // ebx <- (JNIRefs base + SavedFP index)
    asm.emitMOV_RegDisp_Reg (S0, VM_Entrypoints.JNIRefsSavedFPField.getOffset(), EBX);  // JNIRefsSavedFP <- ebx

  }
  

  /*****************************************************************
   * Handle the C to Java transition:  JNI methods in VM_JNIFunctions.java
   * NOTE:
   *   -We need PR to access Java environment, but not certain whether
   *    Linux C treats it as nonvolatile and restores it before calling, 
   *    so for now it is saved in the JNIenv and restored from there.
   *   -Unlike the powerPC scheme which has a special prolog preceding
   *    the normal Java prolog, the Intel scheme replaces the Java prolog
   *    completely with the special prolog
   *
   *            Stack on entry            Stack at end of prolog after call
   *             high memory 			   high memory
   *            |            |                   |            |
   *	EBP ->	|saved FP    | 			 |saved FP    |
   *            |  ...       |                   |  ...       |
   *            |            |                   |            |
   *		|arg n-1     | 			 |arg n-1     |
   * native    	|  ...       | 			 |  ...       |       
   * caller    	|arg 0       | 			 |arg 0       |
   *	ESP -> 	|return addr |        		 |return addr |
   *            |            |           EBP ->  |saved FP    |
   *            |            |                   |methodID    | normal MethodID for JNI function
   *            |            |                   |saved JavaFP| offset to preceeding java frame
   *            |            |                   |saved edi   |	to be used for JTOC
   *            |            |                   |  "   ebx   |	to be used for nonvolatile
   *            |            |                   |  "   ecx   |	to be used for scrach
   *            |            |                   |  "   esi   |	to be used for PR
   *            |            |                   |arg 0       | copied in reverse order
   *            |            |                   |  ...       |
   *            |            |           ESP ->  |arg n-1     |
   *            |            |                   |            | normally compiled Java code continue
   *            |            |                   |            |
   *            |            |                   |            |
   *            |            |                   |            |
   *             low memory                        low memory
   *
   */

  static void generateGlueCodeForJNIMethod(VM_Assembler asm, VM_Method method, int methodID) {
    int bootRecordAddress = VM_Magic.objectAsAddress(VM_BootRecord.the_boot_record);

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
    asm.emitPUSH_Reg(JTOC); 
    asm.emitPUSH_Reg(EBX);         
    asm.emitPUSH_Reg(S0);         
    VM_ProcessorLocalState.emitPushProcessor(asm);
    
     // copy the arguments in reverse order
    VM_Type[] types = method.getParameterTypes();   // does NOT include implicit this or class ptr
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
        // add 2 to get to arg area in caller frame
        asm.emitMOV_Reg_RegDisp (EBX, EBP, (argOffset*WORDSIZE));  
        asm.emitPUSH_Reg(EBX);
        argOffset++;
      }
    }

    // START of code sequence to atomically change processor status from IN_NATIVE
    // to IN_JAVA, looping in a call to sysVirtualProcessorYield if BLOCKED_IN_NATIVE

    int retryLabel = asm.getMachineCodeIndex();     // backward branch label

    // Restore JTOC through the JNIEnv passed back from the C code as the first parameter:
    // an extra entry at the end of the JNIFunctions array contains the RVM JTOC
    //
    // NOTE - we need the JTOC here only to get the sysYield.. entry point out of the
    // bootrecord. we could alternatively also put the bootrecord address at the end
    // of JNIFunctions or put it there INSTEAD of the JTOC

    asm.emitMOV_Reg_RegDisp (EBX, EBP, (2*WORDSIZE));   // pick up arg 0 (from callers frame)
    asm.emitMOV_Reg_RegDisp (JTOC, EBX, 0);                         // JTOC<-addr of JNIFunctions[0]
    asm.emitMOV_Reg_RegDisp (JTOC, JTOC, JNIFUNCTIONS_JTOC_OFFSET); // JTOC<-JNIFunctions[saved JTOC]

    // address of current processors status word is stored at jniEnv (first arg) + 4;
    asm.emitMOV_Reg_RegDisp (S0, EBX, WORDSIZE);     // S0 <- addr of status word

    asm.emitMOV_Reg_RegInd(T0,S0);                         // T0<-contents of statusword 
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
    asm.emitCMPXCHG_RegInd_Reg(S0,T1);               // atomic compare-and-exchange
    asm.emitJCC_Cond_Imm(asm.NE,retryLabel);

    // END of code sequence to change state from IN_NATIVE to IN_JAVA

    // status is now IN_JAVA. GC can not occur while we execute on a processor
    // in this state, so it is safe to access fields of objects
    // JTOC reg has been restored to RVM JTOC

    // done saving, bump SP to reserve room for the local variables
    // SP should now be at the point normally marked as emptyStackOffset
    int numLocalVariables = method.getLocalWords() - method.getParameterWords();
    asm.emitSUB_Reg_Imm (SP, (numLocalVariables << LG_WORDSIZE));
   
    // Compute the byte offset for this thread (offset into the VM_Scheduler.threads array)
    asm.emitMOV_Reg_RegDisp (S0, JTOC, VM_Entrypoints.JNIFunctionPointersField.getOffset());   // S0 <- base addr
    asm.emitSUB_Reg_Reg (EBX, S0);                     // ebx <- offset
    asm.emitSHR_Reg_Imm (EBX, 1);                      // byte offset:  divide by 2 (=> off in threads array)
    // then get the thread and its real JNIEnv
    asm.emitMOV_Reg_RegDisp (S0, JTOC, VM_Entrypoints.threadsField.getOffset());   // S0 <- VM_Thread array
    asm.emitMOV_Reg_RegIdx  (S0, S0, EBX, asm.BYTE, 0);                 // S0 <- VM_Thread object
    asm.emitMOV_Reg_RegDisp (EBX, S0, VM_Entrypoints.jniEnvField.getOffset());     // ebx <- JNIenv

    // now EBX -> jniEnv for thread

    // Retrieve -> preceeding "top" java FP from jniEnv and save in current 
    // frame of JNIFunction
    asm.emitMOV_Reg_RegDisp (ESI, EBX, VM_Entrypoints.JNITopJavaFPField.getOffset());  
    // asm.emitMOV_Reg_RegDisp (PR, EBX, VM_Entrypoints.JNITopJavaFPField.getOffset());  
    
    // get offset from current FP (PR <- PR - FP)
    asm.emitSUB_Reg_Reg (ESI, EBP);    

    asm.emitMOV_RegDisp_Reg (EBP, SAVED_JAVA_FP_OFFSET, ESI);                // save in hdr of current frame 

    // Restore the VM_Processor value saved on the Java to C transition
    VM_ProcessorLocalState.emitSetProcessor(asm, EBX, 
					    VM_Entrypoints.JNIEnvSavedPRField.getOffset());


    VM_ProcessorLocalState.emitMoveRegToField(asm,
                                              VM_Entrypoints.framePointerField.getOffset(),
                                              EBP);
    
    // Test if calling Java JNIFunction on a RVM processor or 
    // a Native processor.
    // at this point: JTOC and PR have been restored & 
    // processor status = IN_JAVA,
    // arguments for the call have been setup, space on the stack for locals
    // has been acquired.

    // load mode of current processor for testing (RVM or NATIVE)
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, T0,
                                              VM_Entrypoints.processorModeField.getOffset());

    asm.emitCMP_Reg_Imm (T0, VM_Processor.RVM);           // test for RVM
    VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);     // Br if yes

    // If here, on a native processor, it is necessary to transfer back to a
    // RVM processor before executing the Java JNI Function.

    // !!! what about saving regs, especially FPRs ??? (CHECK)

    // branch to becomeRVMThread to make the transfer.

    // If GC occurs while we are on the transfer queue of the RVM processor,
    // what will the MapIterator do, will we appear to be in the prolog of the 
    // Java JNI Function? Will there be any refs to report? any saved regs to report?

    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.becomeRVMThreadMethod.getOffset());

    // execution here is now on the RVM processor, and on a different
    // os pThread. PR now points to the new RVM processor we have
    // been transferred to.  JTOC was set when we were re-dispatched.

    // XXX Restoring regs? especially FPRs ??? (CHECK)

    fr1.resolve(asm);  // branch to here if returning on a RVM processor

    // finally proceed with the normal Java compiled code
    // skip the thread switch test for now, see VM_Compiler.genThreadSwitchTest(true)

    asm.emitNOP(); // end of prologue marker

  }

  static void generateEpilogForJNIMethod(VM_Assembler asm, VM_Method method) {

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

    // current processor status should be IN_JAVA, implying no GC and being safe
    // to reference java objects and use PR

    // S0<-addr activethread
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0,
                                              VM_Entrypoints.activeThreadField.getOffset()); 
    asm.emitMOV_Reg_RegDisp(S0, S0, VM_Entrypoints.jniEnvField.getOffset());       // S0<-addr threads jniEnv

    // set jniEnv TopJavaFP using value saved in frame in prolog
    asm.emitMOV_Reg_RegDisp(JTOC, EBP, SAVED_JAVA_FP_OFFSET);      // JTOC<-saved TopJavaFP (offset)
    asm.emitADD_Reg_Reg(JTOC, EBP);                                // change offset from FP into address
    asm.emitMOV_RegDisp_Reg (S0, VM_Entrypoints.JNITopJavaFPField.getOffset(), JTOC); // jniEnv.TopJavaFP <- JTOC

    // in case thread has migrated to different PR, reset saved PRs to current PR
    // first reset PR saved in jniEnv
    VM_ProcessorLocalState.emitStoreProcessor(asm, S0,
                                              VM_Entrypoints.JNIEnvSavedPRField.getOffset());

    // now save PR saved in preceeding JavaToNative transition frame, whose FP
    // is now in JTOC
    VM_ProcessorLocalState.emitStoreProcessor(asm, JTOC, JNI_PR_OFFSET);

    // get address of current processors statusword
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, EBX,
                                              VM_Entrypoints.vpStatusAddressField.getOffset());
    // if processor changed, then statusword address, stored after the threads
    // jniEnv JNIFunction ptr needs to be changed. reuse JTOC as scratch
    asm.emitMOV_Reg_RegDisp(JTOC, S0, VM_Entrypoints.JNIEnvAddressField.getOffset());  // JTOC<-jniEnv.JNIEnvAddress
    asm.emitMOV_RegDisp_Reg (JTOC, WORDSIZE, EBX);         // [JTOC+4] <- processor statusword addr

    // change current processor status to IN_NATIVE
    asm.emitMOV_RegInd_Imm(EBX, VM_Processor.IN_NATIVE);

    // reload native/C nonvolatile regs - saved in prolog
    // what about FPRs
    VM_ProcessorLocalState.emitPopProcessor(asm);
    asm.emitPOP_Reg(S0);         
    asm.emitPOP_Reg(EBX);         
    asm.emitPOP_Reg(JTOC); 

    // NOTE: C expects the framepointer to be restored to EBP, so 
    //       the epilogue for the C to Java glue code must follow that 
    //       convention, not the RVM one!
    //       Also note that RVM treats EBP is a nonvolatile, so we don't
    //       explicitly save/restore it.
    asm.emitMOV_Reg_Reg(SP, EBP);                           // discard current stack frame
    asm.emitPOP_Reg(EBP);
    asm.emitRET();              // return to caller
  }
}
