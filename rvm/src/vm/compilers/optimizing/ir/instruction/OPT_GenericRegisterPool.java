/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Pool of symbolic registers.
 * Each IR contains has exactly one register pool object associated with it.
 * 
 * @see OPT_Register
 * 
 * @author Dave Grove
 * @author Mauricio J. Serrano
 * @author John Whaley
 * @modified Vivek Sarkar
 * @modified Peter Sweeney
 */
abstract class OPT_GenericRegisterPool {

  protected OPT_PhysicalRegisterSet physical = new OPT_PhysicalRegisterSet(); 

  OPT_PhysicalRegisterSet getPhysicalRegisterSet() {
    return physical;
  }

  /* inlined behavior of DoublyLinkedList */
  private OPT_Register start, end;

  OPT_Register getFirstRegister() {
    return start;
  }

  private void registerListappend(OPT_Register reg) {
    if (start == null) {
      start = end = reg;
    } else {
      end.append(reg);
      end = reg;
    }
  }

  private void registerListremove(OPT_Register e) {
    if (e == start) {
      if (e == end) {
	start = end = null;
      } else {
	OPT_Register next = e.next;
	start = next;
	next.prev = null;
      }
    } else if (e == end) {
      OPT_Register prev = e.prev;
      end = prev;
      prev.next = null;
    } else {
      e.remove();
    }
  }
  /* end of inlined behavior */

  /**
   * All registers are assigned unique numbers; currentNum is the counter
   * containing the next available register number.
   */
  private int currentNum;

  /**
   * Initializes a new register pool for the method meth.
   * 
   * @param meth the VM_Method of the outermost method
   */
  OPT_GenericRegisterPool(VM_Method meth) {
    // currentNum is assigned an initial value to avoid overlap of
    // physical and symbolic registers.
    currentNum = OPT_PhysicalRegisterSet.getSize();
  }

  /**
   * Return the number of symbolic registers (doesn't count physical ones)
   * @return the number of synbloic registers allocated by the pool
   */
  public int getNumberOfSymbolicRegisters() {
    int start = OPT_PhysicalRegisterSet.getSize();
    return currentNum - start;
  }

  private OPT_Register makeNewReg(boolean spans) {
    OPT_Register reg = new OPT_Register(currentNum);
    currentNum++;
    registerListappend(reg);
    reg.putSpansBasicBlock(spans);
    return reg;
  }


  /**
   * Release a now unused register.
   * NOTE: It is the CALLERS responsibility to ensure that the register is no
   * longer used!!!!
   * @param r the register to release
   */
  public void release(OPT_RegisterOperand r) {
    OPT_Register reg = r.register;
    if (reg.number == currentNum -1) {
      currentNum--;
      registerListremove(end);
    }
  }


  /**
   * Remove register from register pool.
   */
  void removeRegister(OPT_Register reg) {
    registerListremove(reg);
  }


  /**
   * Gets a new integer register.
   *
   * @param spanBasicBlock whether the register spans a basic block
   * @return the newly created register object
   */
  public OPT_Register getInteger(boolean spanBasicBlock) {
    OPT_Register reg = makeNewReg(spanBasicBlock);
    reg.setInteger();
    return reg;
  }

  /**
   * Gets a new float register.
   *
   * @param spanBasicBlock whether the register spans a basic block
   * @return the newly created register object
   */
  public OPT_Register getFloat(boolean spanBasicBlock) {
    OPT_Register reg = makeNewReg(spanBasicBlock);
    reg.setFloat();
    return reg;
  }

  /**
   * Gets a new double register.
   *
   * @param spanBasicBlock whether the register spans a basic block
   * @return the newly created register object
   */
  public OPT_Register getDouble(boolean spanBasicBlock) {
    OPT_Register reg;
    reg = makeNewReg(spanBasicBlock);
    reg.setDouble();
    return reg;
  }

  /**
   * Gets a new condition register.
   *
   * @param spanBasicBlock whether the register spans a basic block
   * @return the newly created register object
   */
  public OPT_Register getCondition(boolean spanBasicBlock) {
    OPT_Register reg = makeNewReg(spanBasicBlock);
    reg.setCondition();
    return reg;
  }

  /**
   * Gets a new long register.
   *
   * @param spanBasicBlock whether the register spans a basic block
   * @return the newly created register object
   */
  public OPT_Register getLong(boolean spanBasicBlock) {
    OPT_Register reg;
    reg = makeNewReg(spanBasicBlock);
    reg.setLong();
    return reg;
  }

  /**
   * Gets a new validation register.
   *
   * @param spanBasicBlock whether the register spans a basic block
   * @return the newly created register object
   */
  public OPT_Register getValidation(boolean spanBasicBlock) {
    OPT_Register reg = makeNewReg(spanBasicBlock);
    reg.setValidation();
    return reg;
  }


  /**
   * Get a new register of the same type as the argument register
   * 
   * @param template the register to get the type from
   * @return the newly created register object 
   */
  public OPT_Register getReg(OPT_Register template) {
    switch(template.getType()) {
    case OPT_Register.INTEGER_TYPE:
      return getInteger(template.spansBasicBlock());
    case OPT_Register.FLOAT_TYPE:
      return getFloat(template.spansBasicBlock());
    case OPT_Register.DOUBLE_TYPE:
      return getDouble(template.spansBasicBlock());
    case OPT_Register.CONDITION_TYPE:
      return getCondition(template.spansBasicBlock());
    case OPT_Register.LONG_TYPE:
      return getLong(template.spansBasicBlock());
    case OPT_Register.VALIDATION_TYPE: 
      return getValidation(template.spansBasicBlock());
    }
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Get a new register of the same type as the argument RegisterOperand
   * 
   * @param template the register operand to get the type from
   * @return the newly created register object 
   */
  public OPT_Register getReg(OPT_RegisterOperand template) {
    return getReg(template.register);
  }


  /**
   * Get a new register of the appropriate type to hold values of 'type'
   * 
   * @param type the type of values that the register will hold
   * @param spanBasicBlock does the registers live range span (cross)
   *                       a basic block boundary?
   * @return the newly created register object 
   */
  public OPT_Register getReg(VM_Type type, boolean spanBasicBlock) {
    if (type == VM_Type.LongType)
      return getLong(spanBasicBlock);
    else if (type == VM_Type.DoubleType)
      return getDouble(spanBasicBlock);
    else if (type == VM_Type.FloatType)
      return getFloat(spanBasicBlock);
    else if (type == OPT_ClassLoaderProxy.VALIDATION_TYPE)
      return getValidation(spanBasicBlock);
    else
      return getInteger(spanBasicBlock);
  }

  private java.util.HashMap _regPairs = new java.util.HashMap();
  /**
   * MIR: Get the other half of the register pair that is 
   * associated with the argument register.
   * Note: this isn't incredibly general, but all architectures we're currently
   * targeting need at most 2 machine registers to hold Java data values, so
   * for now don't bother implementing a general mechanism.
   * 
   * @param reg a register that may already be part of a register pair
   * @return the register that is the other half of the register pair,
   *         if the pairing doesn't already exist then it is created.
   */
  public OPT_Register getSecondReg(OPT_Register reg) {
    OPT_Register otherHalf = (OPT_Register)_regPairs.get(reg);
    if (otherHalf == null) {
      otherHalf = getReg(reg);
      _regPairs.put(reg, otherHalf);
      if (reg.isLocal()) otherHalf.setLocal();
      if (reg.isLocalInCatch()) otherHalf.setLocalInCatch();
      if (reg.spansBasicBlock()) otherHalf.setSpansBasicBlock();
      if (reg.isSSA()) otherHalf.setSSA();
    }
    return otherHalf;
  }


  /**
   * Make a temporary register operand to hold values of the specified type
   * (a new register is allocated).
   * 
   * @param type the type of values to be held in the temp register
   * @return the new temp
   */
  public OPT_RegisterOperand makeTemp(VM_Type type) {
    return new OPT_RegisterOperand(getReg(type, false), type);
  }

  /**
   * Make a temporary register operand that is similar to the argument.
   * 
   * @param template the register operand to use as a template.
   * @return the new temp
   */
  public OPT_RegisterOperand makeTemp(OPT_RegisterOperand template) {
    OPT_RegisterOperand temp = 
      new OPT_RegisterOperand(getReg(template), template.type);
    temp.addFlags(template.getFlags());
    return temp;
  }

  /**
   * Make a temporary register operand that can hold the values 
   * implied by the passed operand.
   * 
   * @param op the operand to use as a template.
   * @return the new temp
   */
  public OPT_RegisterOperand makeTemp(OPT_Operand op) {
    OPT_RegisterOperand result;
    if (op instanceof OPT_RegisterOperand) {
      result = makeTemp((OPT_RegisterOperand)op);
    }
    else if (op instanceof OPT_NullConstantOperand) {
      result = makeTemp(OPT_ClassLoaderProxy.NULL_TYPE);
    }
    else if (op instanceof OPT_StringConstantOperand) {
      result = makeTemp(VM_Type.JavaLangStringType);
      result.setPreciseType();
    }
    else if (op instanceof OPT_IntConstantOperand) {
      int value = ((OPT_IntConstantOperand)op).value;
      VM_Type type;
      if ((value == 0) || (value == 1))
	type = VM_Type.BooleanType;
      else if (-128 <= value && value <= 127)
	type = VM_Type.ByteType;
      else if (-32768 <= value && value <= 32767)
	type = VM_Type.ShortType;
      else
	type = VM_Type.IntType;
      result = makeTemp(type);
      if (value >  0) result.setPositiveInt();
    }
    else if (op instanceof OPT_LongConstantOperand) {
      result = makeTemp(VM_Type.LongType);
    }
    else if (op instanceof OPT_FloatConstantOperand) {
      result = makeTemp(VM_Type.FloatType);
    }
    else if (op instanceof OPT_DoubleConstantOperand) {
      result = makeTemp(VM_Type.DoubleType);
    } else {
      result = null;
      OPT_OptimizingCompilerException.UNREACHABLE("unknown operand type: "+op);
    }
    return result;
  }

  /**
   * Make a temporary to hold an int (allocating a new register).
   * 
   * @return the newly created temporary
   */
  public OPT_RegisterOperand makeTempInt() {
    return new OPT_RegisterOperand(getInteger(false), VM_Type.IntType);
  }

  /**
   * Make a temporary to hold a boolean (allocating a new register).
   * 
   * @return the newly created temporary
   */
  public OPT_RegisterOperand makeTempBoolean() {
    return new OPT_RegisterOperand(getInteger(false), VM_Type.BooleanType);
  }

  /**
   * Make a temporary to hold a float (allocating a new register).
   * 
   * @return the newly created temporary
   */
  public OPT_RegisterOperand makeTempFloat() {
    return new OPT_RegisterOperand(getFloat(false), VM_Type.FloatType);
  }

  /**
   * Make a temporary to hold a double (allocating a new register).
   * 
   * @return the newly created temporary
   */
  public OPT_RegisterOperand makeTempDouble() {
    return new OPT_RegisterOperand(getDouble(false), VM_Type.DoubleType);
  }

  /**
   * Make a temporary to hold a long (allocating a new register).
   * 
   * @return the newly created temporary
   */
  public OPT_RegisterOperand makeTempLong() {
    return new OPT_RegisterOperand(getLong(false), VM_Type.LongType);
  }

  /**
   * Make a temporary to hold a condition code (allocating a new register).
   * 
   * @return the newly created temporary
   */
  public OPT_RegisterOperand makeTempCondition() {
    OPT_Register reg = getCondition(false);
    return new OPT_RegisterOperand(reg, VM_Type.IntType);
  }

  /**
   * Make a temporary to hold a guard (validation) (allocating a new register).
   * 
   * @return the newly created temporary
   */
  public OPT_RegisterOperand makeTempValidation() {
    OPT_Register reg = getValidation(false);
    reg.setValidation();
    return new OPT_RegisterOperand(reg, OPT_ClassLoaderProxy.VALIDATION_TYPE);
  }

  /**
   * Get the Framepointer (FP)
   * 
   * @return the FP register
   */ 
  public OPT_Register getFP() {
    return physical.getFP();
  }

  /**
   * Get a temporary that represents the FP register
   * 
   * @return the temp
   */ 
  public OPT_RegisterOperand makeFPOp() {
    return new OPT_RegisterOperand(getFP(),VM_Type.IntType);
  }

  /**
   * Get a temporary that represents the PR register
   * 
   * @return the temp
   */ 
  public OPT_RegisterOperand makePROp() {
    return new OPT_RegisterOperand(physical.getPR(),
				   OPT_ClassLoaderProxy.VM_ProcessorType);
  }
}
