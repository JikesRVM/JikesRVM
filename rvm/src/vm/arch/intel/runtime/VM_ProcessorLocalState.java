/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * This class provides a layer of abstraction that the rest of the VM must
 * use in order to access the current <code>VM_Processor</code> object.
 *
 * @see VM_Processor
 *
 * @author Stephen Fink
 */
final class VM_ProcessorLocalState {
  
  static byte PROCESSOR_REGISTER = VM_RegisterConstants.ESI;

  /**
   * Return the current VM_Processor object
   */
  static VM_Processor getCurrentProcessor() {
    VM_Magic.pragmaInline();
    return VM_Magic.getESIAsProcessor();
  }

  /**
   * Set the current VM_Processor object
   */
  static void setCurrentProcessor(VM_Processor p) {
    VM_Magic.pragmaInline();
    VM_Magic.setESIAsProcessor(p);
  }

  /**
   * Emit an instruction sequence to move the value of a register into a field 
   * in the current processor offset 
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   * @param reg number of the register supplying the new value
   */
  static void emitMoveRegToField(VM_Assembler asm, int offset, byte reg) {
    VM_Magic.pragmaInline();
    asm.emitMOV_RegDisp_Reg(PROCESSOR_REGISTER,offset,reg);
  }

  /**
   * Emit an instruction sequence to move an immediate value into a field 
   * in the current processor offset 
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   * @param imm immediate value
   */
  static void emitMoveImmToField(VM_Assembler asm, int offset, int imm) {
    VM_Magic.pragmaInline();
    asm.emitMOV_RegDisp_Imm(PROCESSOR_REGISTER,offset,imm);
  }

  /**
   * Emit an instruction sequence to move the value of a field in the 
   * current processor offset to a register
   *
   * @param asm assembler object
   * @param dest number of destination register
   * @param offset of field in the <code>VM_Processor</code> object
   */
  static void emitMoveFieldToReg(VM_Assembler asm, byte dest, int offset) {
    VM_Magic.pragmaInline();
    asm.emitMOV_Reg_RegDisp(dest,PROCESSOR_REGISTER,offset);
  }

  /**
   * Emit an instruction sequence to compare the value of a field in the 
   * current processor offset with an immediate value
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   * @param imm immediate value to compare with
   */
  static void emitCompareFieldWithImm(VM_Assembler asm, int offset, int imm) {
    VM_Magic.pragmaInline();
    asm.emitCMP_RegDisp_Imm(PROCESSOR_REGISTER,offset,imm);
  }
  /**
   * Emit an instruction sequence to decrement the value of a field in the 
   * current processor offset
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   */
  static void emitDecrementField(VM_Assembler asm, int offset) {
    VM_Magic.pragmaInline();
    asm.emitDEC_RegDisp(PROCESSOR_REGISTER,offset);
  }
  /**
   * Emit an instruction sequence to PUSH the value of a field in the 
   * current processor offset
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   */
  static void emitPushField(VM_Assembler asm, int offset) {
    VM_Magic.pragmaInline();
    asm.emitPUSH_RegDisp(PROCESSOR_REGISTER,offset);
  }
  /**
   * Emit an instruction sequence to POP a value into a field in the 
   * current processor offset
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   */
  static void emitPopField(VM_Assembler asm, int offset) {
    VM_Magic.pragmaInline();
    asm.emitPOP_RegDisp(PROCESSOR_REGISTER,offset);
  }

  /**
   * Emit an instruction sequence to set the current VM_Processor 
   * to be the value at [base] + offset
   *
   * <P>TODO: this method is used only by the JNI compiler.  Consider
   * rewriting the JNI compiler to allow us to deprecate this method.
   *
   * @param asm assembler object
   * @param base number of base register
   * @param offset offset
   */
  static void emitSetProcessor(VM_Assembler asm, byte base, int offset) {
    VM_Magic.pragmaInline();
    asm.emitMOV_Reg_RegDisp(PROCESSOR_REGISTER, base, offset);
  }

  /**
   * Emit an instruction sequence to PUSH a pointer to the current VM_Processor
   * object on the stack.
   *
   * @param asm assembler object
   */
  static void emitPushProcessor(VM_Assembler asm) {
    VM_Magic.pragmaInline();
    asm.emitPUSH_Reg(PROCESSOR_REGISTER);
  }

  /**
   * Emit an instruction sequence to POP a value on the stack, and set the
   * current processor reference to be this value.
   *
   * @param asm assembler object
   */
  static void emitPopProcessor(VM_Assembler asm) {
    VM_Magic.pragmaInline();
    asm.emitPOP_Reg(PROCESSOR_REGISTER);
  }

  /**
   * Emit an instruction sequence to store a pointer to the current VM_Processor
   * object at a location defined by [base]+offset
   *
   * @param asm assembler object
   * @param base number of base register
   * @param offset offset
   */
  static void emitStoreProcessor(VM_Assembler asm, byte base, int offset) {
    VM_Magic.pragmaInline();
    asm.emitMOV_RegDisp_Reg(base,offset,PROCESSOR_REGISTER);
  }
  /**
   * Emit an instruction sequence to load current VM_Processor
   * object from a location defined by [base]+offset
   *
   * @param asm assembler object
   * @param base number of base register
   * @param offset offset
   */
  static void emitLoadProcessor(VM_Assembler asm, byte base, int offset) {
    VM_Magic.pragmaInline();
    asm.emitMOV_Reg_RegDisp(PROCESSOR_REGISTER,base,offset);
  }
}
