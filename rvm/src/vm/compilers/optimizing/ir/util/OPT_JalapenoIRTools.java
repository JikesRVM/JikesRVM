/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * This abstract class contains a bunch of useful static methods for
 * performing Jalapeno or MIR specific operations on IR.
 *
 * @see OPT_IR
 * @see OPT_IRTools
 * 
 * @author Jong-Deok Choi
 * @author Dave Grove
 * @author Mauricio Serrano
 * @author John Whaley
 */
abstract class OPT_JalapenoIRTools extends OPT_IRTools {

  /**
   * Mark the parameter as nonGC and nonPEI and return it.
   * To be used in passthrough expressions like
   * <pre>
   *    instr.insertBack(notPEIGC(Load.create(...)));
   * </pre>
   *
   * @param instr the given instruction
   * @return the given instruction
   */
  static final OPT_Instruction nonPEIGC(OPT_Instruction instr) {
    instr.markAsNonPEINonGCPoint();
    return instr;
  }
  /**
   * Returns a constant operand with the current value of a static field
   * Returns null iff the constant operand type is not yet supported
   * by our IR. TODO: Support all possible types of constant operands.
   *
   * @param field the static field whose current value we want to read
   * @return a constant operand representing the current value of the field.
   *         will return null if it is unable to create a constant operand
   *         of the given type.
   */
  static final OPT_ConstantOperand getStaticFieldValue(VM_Field field) {
    if (!field.isStatic())
      throw new OPT_OptimizingCompilerException("field is not static");

    VM_Type fieldType = field.getType();
    if (fieldType.isIntLikeType()) {
      return getIntStaticFieldValue(field);
    } else if (fieldType.isLongType()) {
      return getLongStaticFieldValue(field);
    } else if (fieldType.isFloatType()) {
      return getFloatStaticFieldValue(field);
    } else if (fieldType.isDoubleType()) {
      return getDoubleStaticFieldValue(field);
    } else {
      // other types not yet supported
      return null;
    }
  }

  /**
   * Returns a constant operand with the current value of a int-like 
   * static field.
   *
   * @param field a static field
   * @return a constant operand representing the current value of the field
   */
  static final OPT_IntConstantOperand getIntStaticFieldValue(VM_Field field) {
    int offset = field.getOffset();
    Object JTOC = VM_Magic.getJTOC();
    int value = VM_Magic.getIntAtOffset(JTOC,offset);
    return new OPT_IntConstantOperand(value);
  }

  /**
   * Returns a constant operand with the current value of a float
   * static field.
   *
   * @param field a static field
   * @return a constant operand representing the current value of the field
   */
  static final OPT_FloatConstantOperand getFloatStaticFieldValue(VM_Field field) {
    int offset = field.getOffset();
    Object JTOC = VM_Magic.getJTOC();
    int bits = VM_Magic.getIntAtOffset(JTOC,offset);
    float value = VM_Magic.intBitsAsFloat(bits);
    return new OPT_FloatConstantOperand(value);
  }

  /**
   * Returns a constant operand with the current value of a long
   * static field.
   *
   * @param field a static field
   * @return a constant operand representing the current value of the field
   */
  static final OPT_LongConstantOperand getLongStaticFieldValue(VM_Field field) {
    int offset = field.getOffset();
    Object JTOC = VM_Magic.getJTOC();
    int highWord= VM_Magic.getIntAtOffset(JTOC,offset);
    int lowWord= VM_Magic.getIntAtOffset(JTOC,offset+4);
    long value = (highWord << 32) + lowWord;
    return new OPT_LongConstantOperand(value);
  }

  /**
   * Returns a constant operand with the current value of a double
   * static field.
   *
   * @param field a static field
   * @return a constant operand representing the current value of the field
   */
  static final OPT_DoubleConstantOperand getDoubleStaticFieldValue(VM_Field field) {
    int offset = field.getOffset();
    Object JTOC = VM_Magic.getJTOC();
    int highWord= VM_Magic.getIntAtOffset(JTOC,offset);
    int lowWord= VM_Magic.getIntAtOffset(JTOC,offset+4);
    long bits = (highWord << 8) + lowWord;
    double value = VM_Magic.longBitsAsDouble(bits);
    return new OPT_DoubleConstantOperand(value);
  }

  /**
   * Might this instruction be a load from a field that is declared 
   * to be volatile?
   *
   * @param s the insruction to check
   * @return <code>true</code> if the instruction might be a load
   *         from a volatile field or <code>false</code> if it 
   *         cannot be a load from a volatile field
   */
  public static boolean mayBeVolatileFieldLoad(OPT_Instruction s) {
    boolean isVolatileLoad = false;
    if (OPT_LocalCSE.isLoadInstruction(s)) {
      OPT_LocationOperand l = LocationCarrier.getLocation(s);
      if (l.isFieldAccess()) {
	VM_Field f = l.getField();
	if (!f.getDeclaringClass().isLoaded()) {
	  // class not yet loaded; conservatively assume
	  // volatile! (yuck)
	  isVolatileLoad = true;
	}
	else if (f.isVolatile()) isVolatileLoad = true;
      }
    }
    return isVolatileLoad;
  }
}
