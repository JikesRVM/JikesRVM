/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;
import java.util.Enumeration;

/**
 * This class contains IA32 calling conventions
 * The two public methods are:
 *  (1) expandCallingConventions(OPT_IR) which is called by the 
 *  register allocator immediately before allocation to make manifest the 
 *  use of registers by the calling convention.
 *  (2) expandSysCall(OPT_Instruction, OPT_IR) which is called to expand 
 *  a SYSCALL HIR instruction into the appropriate sequence of 
 *  LIR instructions.
 *
 * TODO: Much of this code could still be factored out as
 * architecture-independent.
 *
 * @author Dave Grove
 * @author Stephen Fink
 */
final class OPT_CallingConvention 
    extends OPT_RVMIRTools
    implements OPT_Operators, VM_BaselineConstants,
    OPT_PhysicalRegisterConstants {

  /**
   * Enable a dead-code elimination optimization during prologue
   * expansion?
   * TODO: enable this; this is currently broken: it appears that register
   * lists don't work on MIR for some reason.  This breaks Sieve in
   * symantec if enabled.
   */
  private static final boolean DEAD_CODE_ELIM = false; 

  /**
   * Size of a word, in bytes
   */
  private static final int WORDSIZE = 4;

  /**
   * Expand calling conventions to make physical registers explicit in the
   * IR when required for calls, returns, and the prologue.
   */
  public static void expandCallingConventions(OPT_IR ir)  {
    // expand each call and return instruction
    for (OPT_Instruction inst = ir.firstInstructionInCodeOrder(); 
         inst != null; inst = inst.nextInstructionInCodeOrder()) {
      if (inst.isCall()) {
        callExpand(inst, ir);
      } else if (inst.isReturn()) {
        returnExpand(inst, ir);
      }
    }

    // expand the prologue instruction
    expandPrologue(ir);
  }

  /**
   * Expand the calling convention for a particular call instruction
   */
  private static void callExpand(OPT_Instruction call, OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    boolean isSysCall = call.operator() == IA32_SYSCALL;

    // -1. Clear the floating-point stack if dirty.
    if (ir.options.fclearWithFNINIT()) {
      if (ir.hasFloatingPoint() && call.operator != CALL_SAVE_VOLATILE) {
        call.insertBefore(MIR_Empty.create(IA32_FNINIT));
      }
    }
    
    // 0. Handle the parameters
    int parameterBytes = isSysCall ? expandParametersToSysCall(call,ir) : 
      expandParametersToCall(call,ir);

    // 1. Clear the floating-point stack if dirty.
    if (!ir.options.fclearWithFNINIT() && call.operator != CALL_SAVE_VOLATILE) {
      if (ir.hasFloatingPoint() && call.operator != CALL_SAVE_VOLATILE) {
        int FPRRegisterParams= countFPRParams(call);
        FPRRegisterParams = Math.min(FPRRegisterParams, 
                                     phys.getNumberOfFPRParams());
        call.insertBefore(MIR_UnaryNoRes.create(IA32_FCLEAR,
                                                I(FPRRegisterParams)));
      }
    }
    
    // 2. Restore the frame pointer before making the call.
    OPT_MemoryOperand fpHome = 
      OPT_MemoryOperand.BD(R(phys.getPR()),
			       VM_Entrypoints.framePointerOffset,
			       (byte)WORDSIZE, null, null);
    call.insertBefore(MIR_Move.create(IA32_MOV, R(phys.getFP()), fpHome));

    // 3. Move the return value into a register
    expandResultOfCall(call,ir);
    
    // 4. Adjust ESP immediately before/after the call to account for
    //    the parameters we are passing to the callee.
    //    We do this as the last step in call expansion to ensure 
    //    that the stackpointer adjustment is right next to the call.
    //    Spill code insertion depends on this.
    //    TODO: We should think about using push instructions to pass
    //          parameters on the stack.  This would require some additional
    //          logic in spill code insertion, but would probably result in
    //          reduced code size and maybe marginally better performance.
    if (parameterBytes != 0) {
      // set the stack pointer as if we had pushed each parameter on the
      // stack.
      call.insertBefore(MIR_BinaryAcc.create(IA32_ADD,R(phys.getESP()), 
					     I(parameterBytes)));
      
      if (isSysCall) {
	// set the stack pointer as if we had popped each parameter off 
	// the stack. This is only required for sysCalls; a normal RVM
	// callee will do this subtraction as part of its epilogue.
	call.insertAfter(MIR_BinaryAcc.create(IA32_ADD,R(phys.getESP()),
					      I(-parameterBytes)));
      }
    }
    // 5. If this is an interface invocation, set up the hidden parameter
    //    in the processor object to hold the interface signature method
    //    id.
    if (VM.BuildForIMTInterfaceInvocation) {
      if (MIR_Call.hasMethod(call)) {
        OPT_MethodOperand mo = MIR_Call.getMethod(call);
        if (mo.isInterface()) {
          int signatureId = VM_ClassLoader.
            findOrCreateInterfaceMethodSignatureId(mo.method.getName(), 
                                                   mo.method.getDescriptor());
          OPT_MemoryOperand M = OPT_MemoryOperand.BD
            (R(phys.getPR()), VM_Entrypoints.hiddenSignatureIdOffset, 
             (byte)WORDSIZE, null, null);
          call.insertBefore(MIR_Move.create(IA32_MOV,M,I(signatureId)));
        }
      }
    }
  }

  /**
   * Expand the calling convention for a particular return instruction
   */
  private static void returnExpand(OPT_Instruction ret, OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // Clear floating-point stack if dirty
    if (ir.options.fclearWithFNINIT()) {
      if (ir.hasFloatingPoint()) {
        ret.insertBefore(MIR_Empty.create(IA32_FNINIT));
      }
    }
    
    if (MIR_Return.hasVal(ret)) {
      OPT_Operand symb1 = MIR_Return.getClearVal(ret);
      MIR_Return.setVal(ret, null);
      VM_Type type = symb1.getType();
      if (type.isFloatType() || type.isDoubleType()) {
        OPT_Register r = phys.getReturnFPR();
        OPT_RegisterOperand rOp= new OPT_RegisterOperand(r, type);
	ret.insertBefore(MIR_Move.create(IA32_FMOV, rOp, symb1));
	MIR_Return.setVal(ret, rOp.copyD2U());
      } else {
        OPT_Register r = phys.getFirstReturnGPR();
        OPT_RegisterOperand rOp= new OPT_RegisterOperand(r, type);
	ret.insertBefore(MIR_Move.create(IA32_MOV, rOp, symb1));
	MIR_Return.setVal(ret, rOp.copyD2U());
      }
    }

    if (MIR_Return.hasVal2(ret)) {
      OPT_Operand symb2 = MIR_Return.getClearVal2(ret);
      MIR_Return.setVal2(ret,null);
      VM_Type type = symb2.getType();
      OPT_Register r = phys.getSecondReturnGPR();
      OPT_RegisterOperand rOp= new OPT_RegisterOperand(r, type);
      ret.insertBefore(MIR_Move.create(IA32_MOV, rOp, symb2));
      MIR_Return.setVal2(ret, rOp.copyD2U());
    }

    // Clear the floating-point stack if dirty.
    if (!ir.options.fclearWithFNINIT()) {
      if (ir.hasFloatingPoint()) {
        int nSave=0;
        if (MIR_Return.hasVal(ret)) {
          OPT_Operand symb1 = MIR_Return.getClearVal(ret);
          VM_Type type = symb1.getType();
          if (type.isFloatType() || type.isDoubleType()) {
            nSave=1;
          }
        }
        ret.insertBefore(MIR_UnaryNoRes.create(IA32_FCLEAR,I(nSave)));
      }
    }

    // Set the first 'Val' in the return instruction to hold an integer
    // constant which is the number of words to pop from the stack while 
    // returning from this method.  The assembler obeys this parameter.
    int nWords = ir.method.getParameterWords();
    // getParameterWords() does not include the implicit 'this' parameter.
    if (!ir.method.isStatic()) nWords++;
    MIR_Return.setPopBytes(ret, I(nWords << LG_WORDSIZE)); 
  }

  /**
   * Explicitly copy the result of a call instruction from the result
   * register to the appropriate symbolic register,
   * as defined by the calling convention.
   */
  private static void expandResultOfCall(OPT_Instruction call, OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // copy the first result parameter
    if (MIR_Call.hasResult(call)) {
      OPT_RegisterOperand result1 = MIR_Call.getClearResult(call);
      MIR_Call.setResult(call,null);
      if (result1.type.isFloatType() || result1.type.isDoubleType()) {
        OPT_Register r = phys.getReturnFPR();
        OPT_RegisterOperand physical = 
	  new OPT_RegisterOperand(r, result1.type);
        OPT_Instruction tmp = MIR_Move.create(IA32_FMOV, result1, physical);
        call.insertAfter(tmp);
        MIR_Call.setResult(call, null);
      } else {
        // first GPR result register
        OPT_Register r = phys.getFirstReturnGPR();
        OPT_RegisterOperand physical = 
	  new OPT_RegisterOperand(r, result1.type);
        OPT_Instruction tmp = MIR_Move.create(IA32_MOV, result1, physical);
        call.insertAfter(tmp);
        MIR_Call.setResult(call, null);
      }
    }

    // copy the second result parameter
    if (MIR_Call.hasResult2(call)) {
      OPT_RegisterOperand result2 = MIR_Call.getClearResult2(call);
      MIR_Call.setResult2(call,null);
      // second GPR result register
      OPT_Register r = phys.getSecondReturnGPR();
      OPT_RegisterOperand physical = 
	new OPT_RegisterOperand(r, result2.type);
      OPT_Instruction tmp = MIR_Move.create(IA32_MOV, result2, physical);
      call.insertAfter(tmp);
      MIR_Call.setResult2(call, null);
    }
  }

  /**
   * Explicitly copy parameters to a call into the appropriate physical
   * registers as defined by the calling convention.
   *
   * Note: Assumes that ESP points to the word before the slot where the
   * first parameter should be stored.
   */
  private static int expandParametersToCall(OPT_Instruction call, OPT_IR ir) {
    int nGPRParams = 0;
    int nFPRParams = 0;

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    // count the number FPR parameters in a pre-pass
    int FPRRegisterParams= countFPRParams(call);
    FPRRegisterParams = Math.min(FPRRegisterParams, 
                                 phys.getNumberOfFPRParams());

    // offset, in bytes, from the SP, for the next parameter slot on the
    // stack
    int parameterBytes = 0;
    OPT_Register ESP = phys.getESP();

    // walk over each parameter
    // must save before we start nulling them out!
    int numParams = MIR_Call.getNumberOfParams(call); 
    int nParamsInRegisters = 0;
    for (int i = 0; i < numParams;  i++) {
      OPT_Operand param = MIR_Call.getClearParam(call,i);
      MIR_Call.setParam(call,i,null);
      VM_Type paramType = param.getType();
      if (paramType.isFloatType() || paramType.isDoubleType()) {
	nFPRParams++;
	int size = paramType.isFloatType() ? 4 : 8;
	parameterBytes -= size;
	if (nFPRParams > phys.getNumberOfFPRParams()) {
	  // pass the FP parameter on the stack
	  OPT_MemoryOperand M = OPT_MemoryOperand.
	    BD(R(ESP), parameterBytes, (byte)size, null,null);
	  call.insertBefore(MIR_Move.create(IA32_FMOV, M, param));
	} else {
	  // Pass the parameter in a register.
	  // Note that if k FPRs are passed in registers, 
	  // the 1st goes in F(k-1),
	  // the 2nd goes in F(k-2), etc...
	  OPT_Register phy = 
	    phys.getFPRParam(FPRRegisterParams - nFPRParams);
	  OPT_RegisterOperand real = new OPT_RegisterOperand(phy, paramType);
	  call.insertBefore(MIR_Move.create(IA32_FMOV, real, param));
	  // Record that the call now has a use of the real register.
	  MIR_Call.setParam(call,nParamsInRegisters++,real.copy());
	}
      } else {
	nGPRParams++;
	parameterBytes -= 4;
	if (nGPRParams > phys.getNumberOfGPRParams()) {
	  // Too many parameters to pass in registers.  Write the
	  // parameter into the appropriate stack frame location.
	  OPT_MemoryOperand M = OPT_MemoryOperand.BD(R(ESP),
							     parameterBytes,
							     (byte)WORDSIZE,
							     null,null);
	  call.insertBefore(MIR_Move.create(IA32_MOV, M, param));
	} else {
	  // Pass the parameter in a register.
	  OPT_Register phy = phys.getGPRParam(nGPRParams-1);
	  OPT_RegisterOperand real = new OPT_RegisterOperand(phy, paramType);
	  call.insertBefore(MIR_Move.create(IA32_MOV, real, param));
	  // Record that the call now has a use of the real register.
	  MIR_Call.setParam(call,nParamsInRegisters++,real.copy());       
	}
      }
    }
    return parameterBytes;
  }

  /**
   * Save and restore all nonvolatile registers around a syscall.  
   * We do this in case the sys call does not respect our
   * register conventions.
   *
   * We save/restore all nonvolatiles and the PR, whether
   * or not this routine uses them.  This may be a tad inefficient, but if
   * you're making a system call, you probably don't care.
   *
   * Side effect: changes the operator of the call instruction to
   * IA32_CALL.
   *
   * @param call the sys call
   */
  static void saveNonvolatilesAroundSysCall(OPT_Instruction call, OPT_IR ir) {
    saveNonvolatilesBeforeSysCall(call, ir); 
    restoreNonvolatilesAfterSysCall(call, ir);
    call.operator = IA32_CALL;
  }
  /**
   * Save all nonvolatile registers before a syscall.  
   * We do this in case the sys call does not respect our
   * register conventions.
   *
   * We save/restore all nonvolatiles and the PR, whether
   * or not this routine uses them.  This may be a tad inefficient, but if
   * you're making a system call, you probably don't care.
   *
   * @param call the sys call
   */
  static void saveNonvolatilesBeforeSysCall(OPT_Instruction call, OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_StackManager sm = (OPT_StackManager)ir.stackManager;
    OPT_Instruction result = null;

    // add one to account for the processor register.  
    int nToSave = phys.getNumberOfNonvolatileGPRs() + 1;

    // get the offset into the stack frame of where to stash the first
    // nonvolatile for this case.
    int location = sm.getOffsetForSysCall();
    
    // save each non-volatile
    OPT_Register FP = phys.getFP();
    for (Enumeration e = phys.enumerateNonvolatileGPRs();
         e.hasMoreElements(); ) {
      OPT_Register r = (OPT_Register)e.nextElement();
      OPT_MemoryOperand rHome = OPT_MemoryOperand.BD(R(FP),
                                    -location, (byte)WORDSIZE, null, null);
      call.insertBefore(MIR_Move.create(IA32_MOV, rHome, R(r)));
      location += WORDSIZE;
    }
    
    // save the processor register
    OPT_Register PR = phys.getPR();
    OPT_MemoryOperand rHome = OPT_MemoryOperand.BD(R(FP),
                                    -location, (byte)WORDSIZE, null, null);
    call.insertBefore(MIR_Move.create(IA32_MOV, rHome, R(PR)));
  }
  /**
   * Restore all nonvolatile registers after a syscall.  
   * We do this in case the sys call does not respect our
   * register conventions.
   *
   * We save/restore all nonvolatiles and the PR, whether
   * or not this routine uses them.  This may be a tad inefficient, but if
   * you're making a system call, you probably don't care.
   *
   * @param call the sys call
   */
  static void restoreNonvolatilesAfterSysCall(OPT_Instruction call,
                                              OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_StackManager sm = (OPT_StackManager)ir.stackManager;
    
    // add one to account for the processor register.  
    int nToSave = phys.getNumberOfNonvolatileGPRs() + 1;

    // get the offset into the stack frame of where to stash the first
    // nonvolatile for this case.
    int location = sm.getOffsetForSysCall();
    
    // restore each non-volatile
    OPT_Register FP = phys.getFP();
    for (Enumeration e = phys.enumerateNonvolatileGPRs();
         e.hasMoreElements(); ) {
      OPT_Register r = (OPT_Register)e.nextElement();
      OPT_MemoryOperand rHome = OPT_MemoryOperand.BD(R(FP),
                                    -location, (byte)WORDSIZE, null, null);
      call.insertAfter(MIR_Move.create(IA32_MOV, R(r), rHome));
      location += WORDSIZE;
    }
    
    // restore the processor register
    OPT_Register PR = phys.getPR();
    OPT_MemoryOperand rHome = OPT_MemoryOperand.BD(R(FP),
                                    -location, (byte)WORDSIZE, null, null);
    call.insertAfter(MIR_Move.create(IA32_MOV, R(PR), rHome));
  }

  /**
   * Explicitly copy parameters to a system call into the appropriate physical
   * registers as defined by the calling convention.  Note that for a system
   * call (ie., a call to C), the order of parameters on the stack is
   * <em> reversed </em> compared to the normal RVM calling convention
   *
   * TODO: much of this code is exactly the same as in expandParametersToCall().
   *       factor out the common code.
   *
   * Note: Assumes that ESP points to the word before the slot where the
   * first parameter should be stored.
   */
  private static int expandParametersToSysCall(OPT_Instruction call, OPT_IR ir){
    int nGPRParams = 0;
    int nFPRParams = 0;
    int parameterBytes = 0;
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    OPT_Register ESP = phys.getESP();
    // count the number FPR parameters in a pre-pass
    int FPRRegisterParams= countFPRParams(call);
    FPRRegisterParams = Math.min(FPRRegisterParams, 
                                 phys.getNumberOfFPRParams());
    
    // walk over the parameters in reverse order
    // NOTE: All params to syscall are passed on the stack!
    int numParams = MIR_Call.getNumberOfParams(call); 
    for (int i = numParams-1; i >=0;  i--) {
      OPT_Operand param = MIR_Call.getClearParam(call,i);
      MIR_Call.setParam(call,i,null);
      VM_Type paramType = param.getType();
      if (paramType.isFloatType() || paramType.isDoubleType()) {
	nFPRParams++;
	int size = paramType.isFloatType() ? 4 : 8;
	parameterBytes -= size;
	OPT_MemoryOperand M = OPT_MemoryOperand.
	  BD(R(ESP), parameterBytes, (byte)size, null,null);
	call.insertBefore(MIR_Move.create(IA32_FMOV, M, param));
      } else {
	nGPRParams++;
	parameterBytes -= 4;
	OPT_MemoryOperand M = OPT_MemoryOperand.
	  BD(R(ESP), parameterBytes, (byte)WORDSIZE, null,null);
	call.insertBefore(MIR_Move.create(IA32_MOV, M, param));
      }
    }
    return parameterBytes;
  }

  /**
   * We have to save/restore the non-volatile registers around syscalls,
   * to protect ourselves from malicious C compilers and Linux kernels.
   * 
   * Although the register allocator is not yet ready to insert these
   * spills, allocate space on the stack in preparation.
   *
   * For now, we naively save/restore all nonvolatiles.
   */
  public static void allocateSpaceForSysCall(OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_StackManager sm = (OPT_StackManager)ir.stackManager;
    
    // add one to account for the processor register.  
    int nToSave = phys.getNumberOfNonvolatileGPRs() + 1;

    sm.allocateSpaceForSysCall(nToSave);
  }

  /**
   * Calling convention to implement calls to native (C) routines 
   * using the Linux linkage conventions.
   */
  public static void expandSysCall(OPT_Instruction s, OPT_IR ir) {
    
    // Determine the address of the method to call.
    OPT_RegisterOperand ip = null;
    if (CallSpecial.getMethod(s) != null) {
      OPT_SysMethodOperand sysM = (OPT_SysMethodOperand)CallSpecial.
                                                        getClearMethod(s);
      OPT_RegisterOperand t1 = OPT_ConvertToLowLevelIR.getStatic(s, ir,
                                                                 sysM.record);
      ip = OPT_ConvertToLowLevelIR.getField(s, ir, t1, sysM.ip);
    } else {
      ip = (OPT_RegisterOperand)CallSpecial.getClearAddress(s);
    }

    // Allocate space to save non-volatiles.
    allocateSpaceForSysCall(ir);
    
    // Make sure we allocate enough space for the parameters to this call.
    int numberParams = CallSpecial.getNumberOfParams(s);
    int parameterWords = 0;
    for (int i = 0; i < numberParams; i++) {
      parameterWords++;
      OPT_Operand op = CallSpecial.getParam(s, i);
      if (op instanceof OPT_RegisterOperand) {
        OPT_RegisterOperand reg = (OPT_RegisterOperand)op;
        if ((reg.type == VM_Type.LongType) || (reg.type == VM_Type.DoubleType))
          parameterWords++;
      } else if ((op instanceof OPT_LongConstantOperand) || 
                 (op instanceof OPT_DoubleConstantOperand)) {
        parameterWords++;
      }
    }
    // allocate space for each parameter, plus one word on the stack to
    // hold the address of the callee.
    ir.stackManager.allocateParameterSpace((1 + parameterWords)*4);
                                                   
    // Convert to a SYSCALL instruction with a null method operand.
    CallSpecial.mutate0(s, SYSCALL, CallSpecial.getClearResult(s), ip, null);
  }

  /**
   * Count the number of FPR parameters in a call instruction.
   */
  private static int countFPRParams(OPT_Instruction call) {

    int result = 0;

    // walk over the parameters 
    int numParams = MIR_Call.getNumberOfParams(call); 
    for (int i = 0; i <numParams;  i++) {
      OPT_Operand param = MIR_Call.getParam(call,i);
      if (param.isRegister()) {
        OPT_RegisterOperand symb = (OPT_RegisterOperand)param;
        if (symb.type.isFloatType() || symb.type.isDoubleType()) {
          result++;
        }
      }
    }
    return result;
  }
  /**
   * Count the number of FPR parameters in a prologue instruction.
   */
  private static int countFPRParamsInPrologue(OPT_Instruction p) {

    int result = 0;

    // walk over the parameters 
    for (OPT_OperandEnumeration e = p.getDefs(); e.hasMoreElements(); ) {
      OPT_Operand param = (OPT_Operand)e.nextElement();
      if (param.isRegister()) {
        OPT_RegisterOperand symb = (OPT_RegisterOperand)param;
        if (symb.type.isFloatType() || symb.type.isDoubleType()) {
          result++;
        }
      }
    }
    return result;
  }
  /**
   * Expand the prologue instruction.
   */
  private static void expandPrologue(OPT_IR ir) {
    // set up register lists for dead code elimination.
    if (DEAD_CODE_ELIM && ir.options.SIMPLE_OPT) {
      OPT_DefUse.computeDU(ir);
    }

    OPT_Instruction p = ir.firstInstructionInCodeOrder().
      nextInstructionInCodeOrder();
    if (VM.VerifyAssertions) VM.assert(p.operator == IR_PROLOGUE);
    OPT_Instruction start = p.nextInstructionInCodeOrder();

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    int gprIndex = 0; 
    int fprIndex = 0; 
    int parameterWord = 0;
    
    // count the number of FPR params in a pre-pass
    int FPRRegisterParams= countFPRParamsInPrologue(p);
    FPRRegisterParams = Math.min(FPRRegisterParams, 
                                 phys.getNumberOfFPRParams());


    // deal with each parameter
    for (OPT_OperandEnumeration e = p.getDefs(); e.hasMoreElements(); ) {
      OPT_RegisterOperand symbOp = (OPT_RegisterOperand)e.nextElement();
      VM_Type rType = symbOp.type;
      if (rType.isFloatType() || rType.isDoubleType()) {
        // if optimizing, only define the register if it has uses
        if (!DEAD_CODE_ELIM || !ir.options.SIMPLE_OPT 
            || symbOp.register.useList != null) {
          if (fprIndex < phys.getNumberOfFPRParams()) {
            // insert a MOVE symbolic register = parameter
            // Note that if k FPRs are passed in registers, 
            // the 1st goes in F(k-1),
            // the 2nd goes in F(k-2), etc...
            OPT_Register param = 
	      phys.getFPRParam(FPRRegisterParams - fprIndex - 1);
            start.insertBefore(MIR_Move.create(IA32_FMOV,symbOp.copyRO(),
					       D(param)));
          } else {
            // spilled parameter: We can't insert the load instruction
            // directly, since we don't know the stackframe size yet.
            // Insert a placeholder instruction for clarity, and expand it
            // later
            if (rType.isFloatType()) {
              start.insertBefore(OPT_PhysicalRegisterTools.
                                 makeLoadSpilledFloatParam(symbOp.copyRO(),
                                                           parameterWord));
            } else {
              start.insertBefore(OPT_PhysicalRegisterTools.
                                 makeLoadSpilledDoubleParam(symbOp.copyRO(),
                                                            parameterWord));
            }
          }
        }
        fprIndex++;
        parameterWord += (rType.isFloatType()) ? 1 : 2;
      } else {
        // if optimizing, only define the register if it has uses
        if (!DEAD_CODE_ELIM || !ir.options.SIMPLE_OPT || 
            symbOp.register.useList != null) {
          // t is object, 1/2 of a long, int, short, char, byte, or boolean
          if (gprIndex < phys.getNumberOfGPRParams()) {
            // insert a MOVE symbolic register = parameter
            OPT_Register param = phys.getGPRParam(gprIndex);
	    OPT_RegisterOperand pOp = new OPT_RegisterOperand(param, rType);
            start.insertBefore(OPT_PhysicalRegisterTools.makeMoveInstruction
                               (symbOp.copyRO(),pOp));
          } else {
            // spilled parameter: We can't insert the load instruction
            // directly, since we don't know the stackframe size yet.
            // Insert a placeholder instruction for clarity, and expand it
            // later
            start.insertBefore(OPT_PhysicalRegisterTools.makeLoadSpilledGPRParam
                               (symbOp.copyRO(),parameterWord));
          }
        }
        gprIndex++;
        parameterWord++;
      }
    }

    // Now that we've made the calling convention explicit in the prologue,
    // set IR_PROLOGUE to have no defs.
    p.replace(Prologue.create(IR_PROLOGUE, 0));
  }
}
