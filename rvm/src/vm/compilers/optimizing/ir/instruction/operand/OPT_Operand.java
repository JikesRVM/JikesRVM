
/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.OPT_ClassLoaderProxy;
import com.ibm.JikesRVM.opt.OPT_Constants;
import com.ibm.JikesRVM.opt.OPT_OptimizingCompilerException;
import org.vmmagic.unboxed.Address;

/**
 * An <code>OPT_Operand</code> identifies an operand for an 
 * {@link OPT_Instruction}. A single OPT_Operand object should 
 * not be shared between instructions (or even be used twice in
 * the same instruction).  Operands should not be shared between
 * instructions because we use the 
 * {@link #instruction reference to the operand's containing instruction}
 * to construct use/def chains. We also store program-point specific 
 * information about an {@link OPT_Register symbolic register}
 * in the {@link OPT_RegisterOperand OPT_RegisterOperands} that 
 * {@link OPT_RegisterOperand#register refer} to the 
 * <code>OPT_Register</code>.
 * <p>
 * Operands are divided into several primary categories
 * <ul>
 * <li> {@link OPT_RegisterOperand} represent symbolic and
 *      and physical registers.
 * <li> The subclasses of {@link OPT_ConstantOperand} 
 *      represent various kinds of constant operands.
 * <li> {@link OPT_MethodOperand} represents the targets of CALL instructions.
 * <li> {@link OPT_BranchOperand}, {@link OPT_BasicBlockOperand},
 *      and {@link OPT_BranchOperand} are used to encode CFG
 *      information in LABEL, BBEND, and branch instructions.
 * <li> {@link OPT_ConditionOperand} and {@link OPT_TrapCodeOperand}
 *      encode the conditions tested by conditional branches and
 *      trap instructions.
 * <li> {@link OPT_LocationOperand} represents the memory location
 *      accessed by a load or store operation.
 * <li> {@link OPT_TypeOperand} encodes a {@link VM_Type} for use 
 *      in instructions such as NEW or INSTANCEOF that operate on the
 *      type hierarchy.
 * </ul>
 *
 * @see OPT_Instruction
 * @see OPT_BasicBlockOperand
 * @see OPT_BranchOperand
 * @see OPT_ConditionOperand
 * @see OPT_ConstantOperand
 * @see OPT_DoubleConstantOperand
 * @see OPT_FloatConstantOperand
 * @see OPT_IntConstantOperand
 * @see OPT_LocationOperand
 * @see OPT_LongConstantOperand
 * @see OPT_MethodOperand
 * @see OPT_NullConstantOperand
 * @see OPT_RegisterOperand
 * @see OPT_StringConstantOperand
 * @see OPT_TrapCodeOperand
 * @see OPT_TrueGuardOperand
 * @see OPT_TypeOperand
 *
 * @author Dave Grove
 * @author Mauricio J. Serrano
 * @author John Whaley
 * @modified by Stephen Fink
 * @modified by Igor Pechtchanski
 */
public abstract class OPT_Operand {

  /**
   * Handle back to containing instruction.
   */
  public OPT_Instruction instruction;

  /**
   * clear the {@link #instruction} field (done when moving the 
   * operand from one instruction to another
   * @return <code>this</code>
   */
  public final OPT_Operand clear() { 
    instruction = null; 
    return this;
  }

  /**
   * Is the operand an {@link OPT_RegisterOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_RegisterOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isRegister() { 
    return this instanceof OPT_RegisterOperand; 
  }

  /**
   * Is the operand an {@link OPT_ConstantOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_ConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isConstant() { 
    return this instanceof OPT_ConstantOperand; 
  }

  /**
   * Is the operand an {@link OPT_IntConstantOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_IntConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isIntConstant() { 
    return this instanceof OPT_IntConstantOperand; 
  }

  /**
   * Is the operand an {@link OPT_AddressConstantOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_AddressConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isAddressConstant() { 
    return this instanceof OPT_AddressConstantOperand; 
  }

  /**
   * Is the operand an {@link OPT_FloatConstantOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_FloatConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isFloatConstant() { 
    return this instanceof OPT_FloatConstantOperand; 
  }

  /**
   * Is the operand an {@link OPT_LongConstantOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_LongConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isLongConstant() { 
    return this instanceof OPT_LongConstantOperand; 
  }

  /**
   * Is the operand an {@link OPT_DoubleConstantOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_DoubleConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isDoubleConstant() { 
    return this instanceof OPT_DoubleConstantOperand; 
  }

  /**
   * Is the operand an {@link OPT_StringConstantOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_StringConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isStringConstant() { 
    return this instanceof OPT_StringConstantOperand; 
  }

  /**
   * Is the operand an {@link OPT_NullConstantOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_NullConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isNullConstant() { 
    return this instanceof OPT_NullConstantOperand; 
  }

  /**
   * Is the operand an {@link OPT_TrueGuardOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_TrueGuardOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isTrueGuard() { 
    return this instanceof OPT_TrueGuardOperand; 
  }

  /**
   * Is the operand an {@link OPT_BranchOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_BranchOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isBranch() { 
    return this instanceof OPT_BranchOperand; 
  }

  /**
   * Is the operand an {@link OPT_BasicBlockOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_BasicBlockOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isBlock() { 
    return this instanceof OPT_BasicBlockOperand; 
  }

  /**
   * Is the operand an {@link OPT_MemoryOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_MemoryOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isMemory() { 
    return this instanceof OPT_MemoryOperand; 
  }

  /**
   * Is the operand an {@link OPT_StackLocationOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_StackLocationOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isStackLocation() { 
    return this instanceof OPT_StackLocationOperand; 
  }

  /**
   * Is the operand an {@link OPT_MethodOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_MethodOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isMethod() { 
    return this instanceof OPT_MethodOperand; 
  }

  /**
   * Is the operand an {@link OPT_LocationOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_LocationOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isLocation() { 
    return this instanceof OPT_LocationOperand; 
  }

  /**
   * Is the operand an {@link OPT_TypeOperand}?
   * 
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link OPT_TypeOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isType() { 
    return this instanceof OPT_TypeOperand; 
  }

  
  /**
   * Cast to an {@link OPT_RegisterOperand}.
   * 
   * @return <code>this</code> cast as an {@link OPT_RegisterOperand}
   */
  public final OPT_RegisterOperand asRegister() { 
    return (OPT_RegisterOperand)this; 
  }

  /**
   * Cast to an {@link OPT_IntConstantOperand}.
   * 
   * @return <code>this</code> cast as an {@link OPT_IntConstantOperand}
   */
  public final OPT_IntConstantOperand asIntConstant() { 
    return (OPT_IntConstantOperand)this; 
  }

  /**
   * Cast to an {@link OPT_AddressConstantOperand}.
   * 
   * @return <code>this</code> cast as an {@link OPT_AddressConstantOperand}
   */
  public final OPT_AddressConstantOperand asAddressConstant() { 
    return (OPT_AddressConstantOperand)this; 
  }

  /**
   * Cast to an {@link OPT_FloatConstantOperand}.
   * 
   * @return <code>this</code> cast as an {@link OPT_FloatConstantOperand}
   */
  public final OPT_FloatConstantOperand asFloatConstant() { 
    return (OPT_FloatConstantOperand)this; 
  }

  /**
   * Cast to an {@link OPT_LongConstantOperand}.
   * 
   * @return <code>this</code> cast as an {@link OPT_LongConstantOperand}
   */
  public final OPT_LongConstantOperand asLongConstant() { 
    return (OPT_LongConstantOperand)this; 
  }

  /**
   * Cast to an {@link OPT_DoubleConstantOperand}.
   * 
   * @return <code>this</code> cast as an {@link OPT_DoubleConstantOperand}
   */
  public final OPT_DoubleConstantOperand asDoubleConstant() { 
    return (OPT_DoubleConstantOperand)this; 
  }

  /**
   * Cast to an {@link OPT_StringConstantOperand}.
   * 
   * @return <code>this</code> cast as an {@link OPT_StringConstantOperand}
   */
  public final OPT_StringConstantOperand asStringConstant() { 
    return (OPT_StringConstantOperand)this; 
  }

  /**
   * Cast to an {@link OPT_NullConstantOperand}.
   * 
   * @return <code>this</code> cast as an {@link OPT_NullConstantOperand}
   */
  public final OPT_NullConstantOperand asNullConstant() { 
    return (OPT_NullConstantOperand)this; 
  }

  /**
   * Cast to an {@link OPT_BranchOperand}.
   * 
   * @return <code>this</code> cast as an {@link OPT_BranchOperand}
   */
  public final OPT_BranchOperand asBranch() { 
    return (OPT_BranchOperand)this; 
  }

  /**
   * Cast to an {@link OPT_BasicBlockOperand}.
   * 
   * @return <code>this</code> cast as an {@link OPT_BasicBlockOperand}
   */
  public final OPT_BasicBlockOperand asBlock() { 
    return (OPT_BasicBlockOperand)this; 
  }

  /**
   * Cast to an {@link OPT_MemoryOperand}.
   * 
   * @return <code>this</code> cast as an {@link OPT_MemoryOperand}
   */
  public final OPT_MemoryOperand asMemory() { 
    return (OPT_MemoryOperand)this; 
  }

  /**
   * Cast to an {@link OPT_StackLocationOperand}.
   * 
   * @return <code>this</code> cast as an {@link OPT_StackLocationOperand}
   */
  public final OPT_StackLocationOperand asStackLocation() { 
    return (OPT_StackLocationOperand)this; 
  }

  /**
   * Cast to an {@link OPT_MethodOperand}.
   * 
   * @return <code>this</code> cast as an {@link OPT_MethodOperand}
   */
  public final OPT_MethodOperand asMethod() { 
    return (OPT_MethodOperand)this; 
  }

  /**
   * Cast to an {@link OPT_TypeOperand}.
   * 
   * @return <code>this</code> cast as an {@link OPT_TypeOperand}
   */
  public final OPT_TypeOperand asType() { 
    return (OPT_TypeOperand)this; 
  }

  /**
   * Cast to an {@link OPT_ConditionOperand}.
   * 
   * @return <code>this</code> cast as an {@link OPT_ConditionOperand}
   */
  public final OPT_ConditionOperand asCondition() { 
    return (OPT_ConditionOperand)this; 
  }

  /**
   * Cast to an {@link OPT_LocationOperand}.
   * 
   * @return <code>this</code> cast as an {@link OPT_LocationOperand}
   */
  public final OPT_LocationOperand asLocation() { 
    return (OPT_LocationOperand)this; 
  }

  /**
   * Does the operand represent a value of an int-like data type?
   * 
   * @return <code>true</code> if the data type of <code>this</code> 
   *         is int-like as defined by {@link VM_TypeReference#isIntLikeType}
   *         or <code>false</code> if it is not.
   */
  public final boolean isIntLike() {
    return isIntConstant() || 
      (isRegister() && asRegister().type.isIntLikeType());
  }

  /**
   * Does the operand represent a value of the int data type?
   * 
   * @return <code>true</code> if the data type of <code>this</code> 
   *         is an int as defined by {@link VM_TypeReference#isIntType}
   *         or <code>false</code> if it is not.
   */
  public final boolean isInt() {
    return isIntConstant() || 
      (isRegister() && asRegister().type.isIntType());
  }

  /**
   * Does the operand represent a value of the long data type?
   * 
   * @return <code>true</code> if the data type of <code>this</code> 
   *         is a long as defined by {@link VM_TypeReference#isLongType}
   *         or <code>false</code> if it is not.
   */
  public final boolean isLong() {
    return isLongConstant() || 
      (isRegister() && asRegister().type.isLongType());
  }

  /**
   * Does the operand represent a value of the float data type?
   * 
   * @return <code>true</code> if the data type of <code>this</code> 
   *         is a float as defined by {@link VM_TypeReference#isFloatType}
   *         or <code>false</code> if it is not.
   */
  public final boolean isFloat() {
    return isFloatConstant() || 
      (isRegister() && asRegister().type.isFloatType());
  }

  /**
   * Does the operand represent a value of the double data type?
   * 
   * @return <code>true</code> if the data type of <code>this</code> 
   *         is a double as defined by {@link VM_TypeReference#isDoubleType}
   *         or <code>false</code> if it is not.
   */
  public final boolean isDouble() {
    return isDoubleConstant() || 
      (isRegister() && asRegister().type.isDoubleType());
  }

  /**
   * Does the operand represent a value of the reference data type?
   * 
   * @return <code>true</code> if the data type of <code>this</code> 
   *         is a reference as defined by {@link VM_TypeReference#isReferenceType}
   *         or <code>false</code> if it is not.
   */
  public final boolean isRef() {
    return isStringConstant() || isNullConstant() ||
           (isRegister() && asRegister().type.isReferenceType());
  }

  /**
   * Does the operand represent a value of the address data type?
   * 
   * @return <code>true</code> if the data type of <code>this</code> 
   *         is an address as defined by {@link VM_TypeReference#isWordType}
   *         or <code>false</code> if it is not.
   */
  public final boolean isAddress() {
    return isAddressConstant() || (isRegister() && asRegister().type.isWordType());
  }
  /**
   * Does the operand definitely represent <code>null</code>?
   * 
   * @return <code>true</code> if the operand definitely represents
   *         <code>null</code> or <code>false</code> if it does not.
   */
  public final boolean isDefinitelyNull() {
    return isNullConstant() || 
      (isRegister() && asRegister().type == VM_TypeReference.NULL_TYPE);
  }


  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  public abstract OPT_Operand copy();


  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code> 
   *           if they are not.
   */
  public abstract boolean similar(OPT_Operand op);


  /**
   * Return the {@link VM_TypeReference} of the value represented by the operand.
   * 
   * @return the type of the value represented by the operand.
   */
  public final VM_TypeReference getType() {
    if (isRegister())
      return asRegister().type;
    if (isType())
      return VM_TypeReference.VM_Type;
    if (isIntConstant()) 
      return ((OPT_IntConstantOperand) this).getSpeculativeType();
    if (isAddressConstant())
      return VM_TypeReference.Address;
    if (isNullConstant())
      return VM_TypeReference.NULL_TYPE;
    if (isStringConstant())
      return VM_TypeReference.JavaLangString;
    if (isFloatConstant())
      return VM_TypeReference.Float;
    if (isLongConstant())
      return VM_TypeReference.Long;
    if (isDoubleConstant())
      return VM_TypeReference.Double;
    if (isTrueGuard())
      return VM_TypeReference.VALIDATION_TYPE;
    throw new OPT_OptimizingCompilerException("unknown operand type: "+this);
  }

  
  /**
   * Return the index of the operand in its containing instruction (SLOW).
   * 
   * @return the index of the operand in its containing instruction
   */
  public int getIndexInInstruction() {
    for (int i=0; i<instruction.getNumberOfOperands(); i++) {
      OPT_Operand op = instruction.getOperand(i);
      if (op == this) return i;
    }
    throw new OPT_OptimizingCompilerException("OPT_Operand.getIndexInInstruction");
  }


  /**
   * Compare two operands based on their positions in the operand lattice.
   * For the purposes of doing dataflow analysis, OPT_Operands can be 
   * thought of as forming a lattice.
   * This function compares two operands and returns whether or 
   * not op1 is a conservative approximation of op2.
   * Or in other words, if conservativelyApproximates(op1, op2)
   * then meet(op1, op2) = op1.
   * Note that lattices are partial orders, so it is quite 
   * possible for both conservativelyApproximates(op1, op2)
   * and conservativelyApproximates(op2, op1) to return false.
   * 
   * @param op1 the first operand to compare
   * @param op2 the second operand to compare
   * @return <code>true</code> if op1 conservatively approximates op2 or
   *         <code>false</code> if it does not.
   * 
   * @author Dave Grove
   */
  public static boolean conservativelyApproximates(OPT_Operand op1, 
                                                   OPT_Operand op2) {
    // Step 1: Handle pointer equality and bottom
    if (op1 == op2) {
      if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
        if (op2 == null) {
          VM.sysWrite("operands are both bottom therefore trivially true\n");
        } else {
          VM.sysWrite("operands are identical therefore trivially true\n");
        }
      }
      return true;
    }
    if (op1 == null) {
      if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
        VM.sysWrite("op1 is bottom, therefore trivially true\n");
      }
      return true;
    }
    if (op2 == null) {
      if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
        VM.sysWrite("op2 is bottom, therefore trivially false\n");
      }
      return false;
    }

    // Now, handle the non-trivial cases:

    // Step 2: op1 is a constant but op2 is not the same constant
    if (op1.isConstant()) {
      if (op1.similar(op2)) {
        if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
          VM.sysWrite("operands are similar constants\n");
        }
        return true;
      } else {
        if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
          VM.sysWrite("op1 is a constant but op2 is not the same constant\n");
        }
        return false;
      }
    }

    // Step 3: op1 is a RegisterOperand
    // This case is complicated by the need to ensure that the 
    // various Flag bits are considered as well....
    if (op1.isRegister()) {
      OPT_RegisterOperand rop1 = op1.asRegister();
      VM_TypeReference type1 = rop1.type;
      if (op2.isRegister()) {
        OPT_RegisterOperand rop2 = op2.asRegister();
        VM_TypeReference type2 = rop2.type;
        if (type1 == type2) {
          if (rop1.hasLessConservativeFlags(rop2)) {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("Operands are registers of identical type, but incompatible flags\n");
            }
            return false;
          } else if (OPT_BC2IR.hasLessConservativeGuard(rop1, rop2)) {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("Operands are registers of identical type, but with incompatible non-null guards\n");
            }
            return false;
          } else {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("Operands are compatible register operands\n");
            }
            return true;
          }
        } else if (compatiblePrimitives(type1, type2) ||
                   OPT_ClassLoaderProxy.includesType(type1, type2) == OPT_Constants.YES) {
          // types are ok, only have to worry about the flags
          if (rop1.isPreciseType() || rop1.hasLessConservativeFlags(rop2)) {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("Flag mismatch between type compatible register operands\n");
            }
            return false;
          } else if (OPT_BC2IR.hasLessConservativeGuard(rop1, rop2)) {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("Non-null guard mismatch between type compatible register operands\n");
            }
            return false;
          } else {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("Operands are compatible register operands\n");
            }
            return true;
          }
        } else {
          if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Operands are type incompatible register operands\n");
          }
          return false;
        }
      } else {
        // op2 is not a register
        if (op2 instanceof OPT_BC2IR.ReturnAddressOperand || 
            op2 == OPT_BC2IR.DUMMY) {
          if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Operands are incompatibale values\n");
          }
          return false;
        }
        
        VM_TypeReference type2 = op2.getType();
        if (type1 == type2 || 
            compatiblePrimitives(type1, type2) ||
            (OPT_ClassLoaderProxy.includesType(type1, type2) == OPT_Constants.YES)) {
          // only have to consider state of op1's flags.  Types are ok.
          if (rop1.isPreciseType() && (type1 != type2)) {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("op1 preciseType bit will be incorrect\n");
            }
            return false;
          }
          if ((rop1.scratchObject instanceof OPT_Operand) && 
              ((type2 == VM_TypeReference.NULL_TYPE) ||
               (type2.isIntLikeType() && op2.asIntConstant().value == 0) ||
               (type2.isWordType() && op2.asAddressConstant().value.EQ(Address.zero())) ||
               (type2.isLongType() && op2.asLongConstant().value == 0L))) {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("op1 non null guard will be incorrect");
            }
            return false;
          }
          if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("(Constant) op2 was compatible with register op1\n");
          }
          return true;
        } else {
          if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Op2 not compatible with register op1\n");
          }
          return false;
        }
      }
    }

    // Step 4: op1 is a OPT_IRGEN operand of some form
    if (op1.similar(op2)) {
      if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
        VM.sysWrite("Compatible OPT_BC2IR.* operands\n");
      }
      return true;
    } else {
      if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
        VM.sysWrite("Incompatible OPT_BC2IR.* operands\n");
      }
      return false;
    }
  }


  /**
   * Meet two operands based on their positions in the operand lattice.
   * For the purposes of doing dataflow analysis, OPT_Operands can be 
   * thought of as forming a lattice.
   * This function takes two operands and returns their meet (glb).
   * We use <code>null</code> to stand for bottom (the meet of 
   * the two operands is an illegal value).  For exmaple,
   * meet(5.0, "hi") would evalaute to bottom.
   * Meet returns op1 iff conservativelyApproximates(op1, op2):
   * this is exploited in BC2IR to avoid doing redundant
   * work.  
   * <p>
   * Unfortunately there is a fair amount of code duplication
   * between {@link #conservativelyApproximates} and 
   * {@link #meet}, but factoring out the common control logic
   * is a non-trivial task. 
   *
   * @param op1  the first operand to meet
   * @param op2  the second operand to meet
   * @param reg  the <code>OPT_Register</code> to use to 
   *             create a new <code>OPT_RegisterOperand</code>
   *             if meeting op1 and op2 requires doing so.
   * @return the OPT_Operand that is the meet of op1 and op2.
   *         This function will return <code>null</code> when
   *         the meet evaluates to bottom.  It will return
   *         op1 when conservativelyApproximates(op1, op2)
   *         evaluates to <code>true</code>.
   *
   * @author Dave Grove
   */
  public static OPT_Operand meet(OPT_Operand op1, 
                                 OPT_Operand op2, 
                                 OPT_Register reg) {
    // Step 1: Handler pointer equality and bottom
    if (op1 == op2) {
      if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
        if (op1 == null) {
          VM.sysWrite("Both operands are bottom\n");
        } else {
          VM.sysWrite("Operands are identical\n");
        }
      }
      return op1;
    }
    if (op1 == null) {
      if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
        VM.sysWrite("Op1 was already bottom\n");
      }
      return op1;
    }
    if (op2 == null) {
      if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
        VM.sysWrite("Op2 is bottom (but op1 was not)\n");
      }
      return op2;
    }

    // Now handle the nontrivial cases...

    // Step 2: op1 is <null> (the null constant)
    if (op1 instanceof OPT_NullConstantOperand) {
      if (op2 instanceof OPT_NullConstantOperand) {
        if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
          VM.sysWrite("Both operands are <null>\n");
        }
        return op1;
      } else {
        VM_TypeReference type2 = op2.getType();
        if (type2.isReferenceType()) {
          if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("op1 is <null>, but op2 is other ref type\n");
          }
          return new OPT_RegisterOperand(reg, type2);
        } else {
          if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("op1 is <null>, but op2 is not a ref type\n");
          }
          return null; // bottom
        }
      }
    }

    // Step 3: op1 is some other constant
    if (op1.isConstant()) {
      if (op1.similar(op2)) {
        if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
          VM.sysWrite("op1 and op2 are similar constants\n");
        }
        return op1;
      } else {
        VM_TypeReference superType = 
          OPT_ClassLoaderProxy.findCommonSuperclass(op1.getType(), 
                                                    op2.getType());
        if (superType == null) {
          if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("op1 and op2 have incompatible types\n");
          }
          return null; // bottom
        } else {
          return new OPT_RegisterOperand(reg, superType);
        }
      }
    }

    // Step 4: op1 is a register operand
    // This case is complicated by the need to ensure that 
    // the various Flag bits are considered as well....
    if (op1.isRegister()) {
      OPT_RegisterOperand rop1 = op1.asRegister();
      VM_TypeReference type1 = rop1.type;
      if (op2.isRegister()) {
        OPT_RegisterOperand rop2 = op2.asRegister();
        VM_TypeReference type2 = rop2.type;
        if (type1 == type2) {
          if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Identically typed register operands, checking flags...");
          }
          if (rop1.hasLessConservativeFlags(rop2)) {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("mismatch\n");
            }
            OPT_RegisterOperand res = 
              new OPT_RegisterOperand(reg, type1, rop1.getFlags());
            if (rop1.scratchObject instanceof OPT_Operand &&
                rop2.scratchObject instanceof OPT_Operand &&
                (((OPT_Operand)rop1.scratchObject).similar(((OPT_Operand)rop2.scratchObject)))) {
              res.scratchObject = rop1.scratchObject; // compatible, so preserve onto res
            }
            res.meetInheritableFlags(rop2);
            return res;
          } else if (OPT_BC2IR.hasLessConservativeGuard(rop1, rop2)) {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("Operands are registers of identical type with compatible flags but with incompatible non-null guards\n");
            }
            // by not setting scratchObject we mark as possible null
            return new OPT_RegisterOperand(reg, type1, rop1.getFlags());
          } else {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("match\n");
            }
            return op1;
          }
        } else if (compatiblePrimitives(type1, type2) ||
                   OPT_ClassLoaderProxy.includesType(type1, type2) == OPT_Constants.YES) {
          if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Compatabily typed register operands, checking flags...");
          }
          if (rop1.isPreciseType() || 
              rop1.hasLessConservativeFlags(rop2)) {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("mismatch\n");
            }
            OPT_RegisterOperand res = 
              new OPT_RegisterOperand(reg, type1, rop1.getFlags());
            res.meetInheritableFlags(rop2);
            // even if both op1 & op2 are precise, 
            // op1.type != op2.type, so clear it on res
            res.clearPreciseType();
            if (rop1.scratchObject instanceof OPT_Operand &&
                rop2.scratchObject instanceof OPT_Operand &&
                (((OPT_Operand)rop1.scratchObject).similar(((OPT_Operand)rop2.scratchObject)))) {
              // it matched, so preserve onto res.
              res.scratchObject = rop1.scratchObject; 
            }
            return res;
          } else if (OPT_BC2IR.hasLessConservativeGuard(rop1, rop2)) {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("Operands are registers of compatible type and flags but with incompatible non-null guards\n");
            }
            return new OPT_RegisterOperand(reg, type1, rop1.getFlags());
          } else {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("match\n");
            }
            return op1;
          }
        } else {
          if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Incompatibly typed register operands...("+type1+", "+type2+")...");
          }
          VM_TypeReference resType = OPT_ClassLoaderProxy.findCommonSuperclass(type1, type2);
          if (resType == null) {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("no common supertype, returning bottom\n");
            }
            return null; // bottom
          } else {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("found common supertype\n");
            }
            OPT_RegisterOperand res = 
              new OPT_RegisterOperand(reg, resType, rop1.getFlags());
            res.meetInheritableFlags(rop2);
            res.clearPreciseType();     // invalid on res
            res.clearDeclaredType();    // invalid on res
            if (rop1.scratchObject instanceof OPT_Operand &&
                rop2.scratchObject instanceof OPT_Operand &&
                (((OPT_Operand)rop1.scratchObject).similar(((OPT_Operand)rop2.scratchObject)))) {
              // it matched, so preserve onto res.
              res.scratchObject = rop1.scratchObject;
            }
            return res;
          }
        }
      } else {
        if (op2 instanceof OPT_BC2IR.ReturnAddressOperand || 
            op2 == OPT_BC2IR.DUMMY) {
          if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Incompatabily typed operands");
          }
          return null; // bottom
        }
        VM_TypeReference type2 = op2.getType();
        if (type1 == type2 || 
            compatiblePrimitives(type1, type2) ||
            (OPT_ClassLoaderProxy.includesType(type1, type2) == OPT_Constants.YES)) {
          if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Compatabily typed register & other operand, checking flags...");
          }
          OPT_RegisterOperand res = rop1;
          if (res.isPreciseType() && type1 != type2) {
            res = res.copyU2U();
            res.clearPreciseType();
          }
          if ((rop1.scratchObject instanceof OPT_Operand) && 
              ((type2 == VM_TypeReference.NULL_TYPE) ||
               (type2.isIntLikeType() && op2.asIntConstant().value == 0) ||
               (type2.isWordType() && op2.asAddressConstant().value.isZero()) ||
               (type2.isLongType() && op2.asLongConstant().value == 0L))) {
            res = res.copyU2U();
            res.scratchObject = null;
          }
          if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
            if (res == rop1) {
              VM.sysWrite("match\n");
            } else {
              VM.sysWrite("mismatch\n");
            }
          }
          return res;
        } else {
          if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Incompatabily typed register & other operand...("+type1+", "+type2+")...");
          }
          VM_TypeReference resType = OPT_ClassLoaderProxy.findCommonSuperclass(type1, type2);
          if (resType == null) {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("no common supertype, returning bottom\n");
            }
            return null; // bottom
          } else {
            if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("found common supertype\n");
            }
            return new OPT_RegisterOperand(reg, resType);
          }
        }
      }
    }

    // Step 5: op1 is some OPT_IRGEN operand
    if (op1.similar(op2)) {
      if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
        VM.sysWrite("Compatible OPT_BC2IR.* operands\n");
      }
      return op1;
    } else {
      if (OPT_IRGenOptions.DBG_OPERAND_LATTICE) {
        VM.sysWrite("Incompatible OPT_BC2IR.* operands, returning bottom\n");
      }
      return null; // bottom
    }
  }

  private static boolean compatiblePrimitives(VM_TypeReference type1, VM_TypeReference type2) {
    if (type1.isIntLikeType() && type2.isIntLikeType()) {
      if (type1.isIntType()) {
        return type2.isBooleanType() ||
          type2.isByteType() ||
          type2.isShortType() ||
          type2.isIntType();
      }
      if (type1.isShortType()) {
        return type2.isBooleanType() ||
          type2.isByteType() ||
          type2.isShortType();
      } 
      if (type1.isByteType()) {
        return type2.isBooleanType() || 
          type2.isByteType();
      } if (type1.isBooleanType()) {
        return type2.isBooleanType();
      }
    }
    return false;
  }

}
