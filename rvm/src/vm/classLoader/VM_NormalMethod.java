/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
import java.io.DataInputStream;
import java.io.IOException;

//-#if RVM_WITH_OPT_COMPILER
import com.ibm.JikesRVM.opt.*;
//-#endif

/**
 * A method of a java class that has bytecodes.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public final class VM_NormalMethod extends VM_Method {

  /**
   * words needed for local variables (including parameters)
   */
  private final int localWords;          

  /**
   * words needed for operand stack (high water mark)
   */
  //-#if RVM_WITH_OSR
  private int operandWords;        
  //-#else
  private final int operandWords;        
  //-#endif

  /**
   * bytecodes for this method (null --> none)
   */
  private final byte[] bytecodes;           

  /**
   * try/catch/finally blocks for this method (null --> none)
   */
  private final VM_ExceptionHandlerMap exceptionHandlerMap; 

  /**
   * pc to source-line info (null --> none)
   * Each entry contains both the line number (upper 16 bits)
   * and corresponding start PC (lower 16 bits).
   */
  private final int[] lineNumberMap;       

  //-#if RVM_WITH_OSR 
  // Extra fields for on-stack replacement
  // TODO: rework the system so we don't waste space for this on the VM_Method object
  /* bytecode array constists of prologue and original bytecodes */
  private byte[] synthesizedBytecodes = null;
  /* record osr prologue */
  private byte[] osrPrologue = null;
  /* prologue may change the maximum stack height, remember the
   * original stack height */
  private int savedOperandWords;
  //-#endif

  /**
   * @param dc the VM_Class object of the class that declared this field
   * @param mr the cannonical memberReference for this member.
   * @param mo modifiers associated with this member.
   * @param et exceptions thrown by this method.
   * @param lw the number of local words used by the bytecode of this method
   * @param ow the number of operand words used by the bytecode of this method
   * @param bc the bytecodes of this method
   * @param eMap the exception handler map for this method
   * @param lm the line number map for this method
   */
  VM_NormalMethod(VM_Class dc, VM_MemberReference mr,
		  int mo, VM_Type[] et, int lw, int ow, byte[] bc,
		  VM_ExceptionHandlerMap eMap, int[] lm) {
    super(dc, mr, mo, et);
    localWords = lw;
    operandWords = ow;
    bytecodes = bc;
    exceptionHandlerMap = eMap;
    lineNumberMap = lm;

    //-#if RVM_WITH_OPT_COMPILER
    VM_OptMethodSummary.summarizeMethod(this, bytecodes, 
					(modifiers & ACC_SYNCHRONIZED) != 0);
    //-#endif
  }

  /**
   * Generate the code for this method
   */
  protected VM_CompiledMethod genCode() {
    if (VM.VerifyBytecode) {
      VM_Verifier verifier = new VM_Verifier();
      try {
        boolean success = verifier.verifyMethod(this);
        if (!success) {
          VM.sysWrite("Method " + this + " fails bytecode verification!\n");
        }
      } catch(Exception e) {
        VM.sysWrite("Method " + this + " fails bytecode verification!\n");
      }
    }

    if (VM.writingBootImage) {
      return VM_BootImageCompiler.compile(this); 
    } else {
      return VM_RuntimeCompiler.compile(this);
    }
  }
  
  /**
   * Space required by this method for its local variables, in words.
   * Note: local variables include parameters
   */
  public final int getLocalWords() throws VM_PragmaUninterruptible {
    return localWords;
  }

  /**
   * Space required by this method for its operand stack, in words.
   */
  public final int getOperandWords() throws VM_PragmaUninterruptible {
    return operandWords;
  }

  /**
   * Get a representation of the bytecodes in the code attribute of this method.
   * @return object representing the bytecodes
   */
  public final VM_BytecodeStream getBytecodes() {
    return new VM_BytecodeStream(this, bytecodes);
  }  
  
  /**
   * Fill in DynamicLink object for the invoke at the given bytecode index
   * @param dynamicLink the dynamicLink object to initialize
   * @param bcIndex the bcIndex of the invoke instruction
   */
  public final void getDynamicLink(VM_DynamicLink dynamicLink, int bcIndex) throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(bytecodes != null);
    if (VM.VerifyAssertions) VM._assert(bcIndex + 2 < bytecodes.length);
    int bytecode = bytecodes[bcIndex] & 0xFF;
	if (VM.VerifyAssertions) VM._assert((VM_BytecodeConstants.JBC_invokevirtual <= bytecode)
										&& (bytecode <= VM_BytecodeConstants.JBC_invokeinterface));
    int constantPoolIndex = ((bytecodes[bcIndex + 1] & 0xFF) << 8) | (bytecodes[bcIndex + 2] & 0xFF);
    dynamicLink.set(declaringClass.getMethodRef(constantPoolIndex), bytecode);
  }

  /**
   * Size of bytecodes for this method
   */
  public final int getBytecodeLength() {
    return bytecodes.length;
  }

  /**
   * Exceptions caught by this method.
   * @return info (null --> method doesn't catch any exceptions)
   */
  public final VM_ExceptionHandlerMap getExceptionHandlerMap() throws VM_PragmaUninterruptible {
    return exceptionHandlerMap;
  }

  /**
   * Return the line number information for the argument bytecode index.
   */
  public final int getLineNumberForBCIndex(int bci) throws VM_PragmaUninterruptible {
    if (lineNumberMap == null) return 0;
    int idx;
    for (idx = 0; idx<lineNumberMap.length; idx++) {
      int pc = lineNumberMap[idx] & 0xffff; // lower 16 bits are bcIndex
      if (bci < pc) {
	if (idx == 0) idx++; // add 1, so we can subtract 1 below.
	break;
      }
    }
    return lineNumberMap[--idx] >>> 16; // upper 16 bits are line number
  }

  //-#if RVM_WITH_OSR 
  // Extra methods for on-stack replacement
  // VM_BaselineCompiler and OPT_BC2IR should check if a method is
  // for specialization by calling isForOsrSpecialization, the compiler
  // uses synthesized bytecodes (prologue + original bytecodes) for 
  // OSRing method. Other interfaces of method are not changed, therefore,
  // dynamic linking and gc referring to bytecodes are safe.
  
  /**
   * Checks if the method is in state for OSR specialization now 
   * @return true, if it is (with prologue)
   */
  public boolean isForOsrSpecialization() {
    return this.synthesizedBytecodes != null;
  }

  /**
   * Sets method in state for OSR specialization, i.e, the subsequent calls
   * of getBytecodes return the stream of specilized bytecodes.
   * NB: between flag and action, it should not allow GC or threadSwitch happen.
   * @param prologue, the bytecode of prologue
   * @param newStackHeight, the prologue may change the default height of 
   *                        stack
   */
  public void setForOsrSpecialization(byte[] prologue, int newStackHeight) {
    if (VM.VerifyAssertions) VM._assert(this.synthesizedBytecodes == null);

    byte[] newBytecodes = new byte[prologue.length + bytecodes.length];
    System.arraycopy(prologue, 0, newBytecodes, 0, prologue.length);
    System.arraycopy(bytecodes, 0, newBytecodes, prologue.length, bytecodes.length);
   
    this.osrPrologue = prologue;
    this.synthesizedBytecodes = newBytecodes;
    this.savedOperandWords = operandWords;
    if (newStackHeight > operandWords) 
      this.operandWords = newStackHeight;
  }
 
  /**
   * Restores the original state of the method.
   */
  public void finalizeOsrSpecialization() {
    if (VM.VerifyAssertions) VM._assert(this.synthesizedBytecodes != null);
    this.synthesizedBytecodes = null;
    this.osrPrologue  = null;
    this.operandWords = savedOperandWords;
  }

  /**
   * Returns the OSR prologue length for adjusting various tables and maps.
   * @return the length of prologue if the method is in state for OSR,
   *         0 otherwise.
   */
  public int getOsrPrologueLength() {
    return isForOsrSpecialization()?this.osrPrologue.length:0;
  }

  /** 
   * Returns a bytecode stream of osr prologue
   * @return osr prologue bytecode stream
   */
  public VM_BytecodeStream getOsrPrologue() {
    if (VM.VerifyAssertions) VM._assert(synthesizedBytecodes != null);
    return new VM_BytecodeStream(this, osrPrologue);
  }
  
  /**
   * Returns the synthesized bytecode stream with osr prologue
   * @return bytecode stream
   */
  public VM_BytecodeStream getOsrSynthesizedBytecodes() {
    if (VM.VerifyAssertions) VM._assert(synthesizedBytecodes != null);
    return new VM_BytecodeStream(this, synthesizedBytecodes);
  }		 
  //-#endif RVM_WITH_OSR
}
