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
package org.jikesrvm.compilers.opt.ir.operand;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ClassLoaderProxy;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.bc2ir.BC2IR;
import org.jikesrvm.compilers.opt.bc2ir.IRGenOptions;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.vmmagic.unboxed.Address;

/**
 * An <code>Operand</code> identifies an operand for an
 * {@link Instruction}. A single Operand object should
 * not be shared between instructions (or even be used twice in
 * the same instruction).  Operands should not be shared between
 * instructions because we use the
 * {@link #instruction reference to the operand's containing instruction}
 * to construct use/def chains. We also store program-point specific
 * information about an {@link Register symbolic register}
 * in the {@link RegisterOperand RegisterOperands} that
 * {@link RegisterOperand#register refer} to the
 * <code>Register</code>.
 * <p>
 * Operands are divided into several primary categories
 * <ul>
 * <li> {@link RegisterOperand} represent symbolic and
 *      and physical registers.
 * <li> The subclasses of {@link ConstantOperand}
 *      represent various kinds of constant operands.
 * <li> {@link MethodOperand} represents the targets of CALL instructions.
 * <li> {@link BranchOperand}, {@link BasicBlockOperand},
 *      and {@link BranchOperand} are used to encode CFG
 *      information in LABEL, BBEND, and branch instructions.
 * <li> {@link ConditionOperand} and {@link TrapCodeOperand}
 *      encode the conditions tested by conditional branches and
 *      trap instructions.
 * <li> {@link LocationOperand} represents the memory location
 *      accessed by a load or store operation.
 * <li> {@link TypeOperand} encodes a {@link org.jikesrvm.classloader.RVMType} for use
 *      in instructions such as NEW or INSTANCEOF that operate on the
 *      type hierarchy.
 * </ul>
 *
 * @see Instruction
 * @see BasicBlockOperand
 * @see BranchOperand
 * @see ConditionOperand
 * @see ConstantOperand
 * @see DoubleConstantOperand
 * @see FloatConstantOperand
 * @see IntConstantOperand
 * @see LocationOperand
 * @see LongConstantOperand
 * @see MethodOperand
 * @see NullConstantOperand
 * @see RegisterOperand
 * @see StringConstantOperand
 * @see TrapCodeOperand
 * @see TrueGuardOperand
 * @see TypeOperand
 */
public abstract class Operand {

  /**
   * Handle back to containing instruction.
   */
  public Instruction instruction;

  /**
   * Is the operand an {@link RegisterOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link RegisterOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isRegister() {
    return this instanceof RegisterOperand;
  }

  /**
   * Is the operand an {@link ConstantOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link ConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isConstant() {
    return this instanceof ConstantOperand;
  }

  /**
   * Is the operand an {@link IntConstantOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link IntConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isIntConstant() {
    return this instanceof IntConstantOperand;
  }

  /**
   * Is the operand an {@link AddressConstantOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link AddressConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isAddressConstant() {
    return this instanceof AddressConstantOperand;
  }

  /**
   * Is the operand an {@link FloatConstantOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link FloatConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isFloatConstant() {
    return this instanceof FloatConstantOperand;
  }

  /**
   * Is the operand an {@link LongConstantOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link LongConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isLongConstant() {
    return this instanceof LongConstantOperand;
  }

  /**
   * Is the operand an {@link DoubleConstantOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link DoubleConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isDoubleConstant() {
    return this instanceof DoubleConstantOperand;
  }

  /**
   * Is the operand an {@link StringConstantOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link StringConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isStringConstant() {
    return this instanceof StringConstantOperand;
  }

  /**
   * Is the operand an {@link ClassConstantOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link ClassConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isClassConstant() {
    return this instanceof ClassConstantOperand;
  }

  /**
   * Is the operand an {@link ObjectConstantOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link ObjectConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isObjectConstant() {
    return this instanceof ObjectConstantOperand;
  }

  /**
   * Is the operand a movable {@link ObjectConstantOperand}?
   *
   * @return false
   */
  public boolean isMovableObjectConstant() {
    return false;
  }

  /**
   * Is the operand an {@link TIBConstantOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link TIBConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isTIBConstant() {
    return this instanceof TIBConstantOperand;
  }

  /**
   * Is the operand an {@link NullConstantOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link NullConstantOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isNullConstant() {
    return this instanceof NullConstantOperand;
  }

  /**
   * Is the operand an {@link TrueGuardOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link TrueGuardOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isTrueGuard() {
    return this instanceof TrueGuardOperand;
  }

  /**
   * Is the operand an {@link BranchOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link BranchOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isBranch() {
    return this instanceof BranchOperand;
  }

  /**
   * Is the operand an {@link BasicBlockOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link BasicBlockOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isBlock() {
    return this instanceof BasicBlockOperand;
  }

  /**
   * Is the operand an {@link MemoryOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link MemoryOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isMemory() {
    return this instanceof MemoryOperand;
  }

  /**
   * Is the operand an {@link StackLocationOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link StackLocationOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isStackLocation() {
    return this instanceof StackLocationOperand;
  }

  /**
   * Is the operand an {@link MethodOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link MethodOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isMethod() {
    return this instanceof MethodOperand;
  }

  /**
   * Is the operand an {@link LocationOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link LocationOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isLocation() {
    return this instanceof LocationOperand;
  }

  /**
   * Is the operand an {@link TypeOperand}?
   *
   * @return <code>true</code> if <code>this</code> is an
   *         <code>instanceof</code> an {@link TypeOperand}
   *         or <code>false</code> if it is not.
   */
  public final boolean isType() {
    return this instanceof TypeOperand;
  }

  /**
   * Cast to an {@link RegisterOperand}.
   *
   * @return <code>this</code> cast as an {@link RegisterOperand}
   */
  public final RegisterOperand asRegister() {
    return (RegisterOperand) this;
  }

  /**
   * Cast to an {@link IntConstantOperand}.
   *
   * @return <code>this</code> cast as an {@link IntConstantOperand}
   */
  public final IntConstantOperand asIntConstant() {
    return (IntConstantOperand) this;
  }

  /**
   * Cast to an {@link AddressConstantOperand}.
   *
   * @return <code>this</code> cast as an {@link AddressConstantOperand}
   */
  public final AddressConstantOperand asAddressConstant() {
    return (AddressConstantOperand) this;
  }

  /**
   * Cast to an {@link FloatConstantOperand}.
   *
   * @return <code>this</code> cast as an {@link FloatConstantOperand}
   */
  public final FloatConstantOperand asFloatConstant() {
    return (FloatConstantOperand) this;
  }

  /**
   * Cast to an {@link LongConstantOperand}.
   *
   * @return <code>this</code> cast as an {@link LongConstantOperand}
   */
  public final LongConstantOperand asLongConstant() {
    return (LongConstantOperand) this;
  }

  /**
   * Cast to an {@link DoubleConstantOperand}.
   *
   * @return <code>this</code> cast as an {@link DoubleConstantOperand}
   */
  public final DoubleConstantOperand asDoubleConstant() {
    return (DoubleConstantOperand) this;
  }

  /**
   * Cast to an {@link StringConstantOperand}.
   *
   * @return <code>this</code> cast as an {@link StringConstantOperand}
   */
  public final StringConstantOperand asStringConstant() {
    return (StringConstantOperand) this;
  }

  /**
   * Cast to an {@link ClassConstantOperand}.
   *
   * @return <code>this</code> cast as an {@link ClassConstantOperand}
   */
  public final ClassConstantOperand asClassConstant() {
    return (ClassConstantOperand) this;
  }

  /**
   * Cast to an {@link ObjectConstantOperand}.
   *
   * @return <code>this</code> cast as an {@link ObjectConstantOperand}
   */
  public final ObjectConstantOperand asObjectConstant() {
    return (ObjectConstantOperand) this;
  }

  /**
   * Cast to an {@link TIBConstantOperand}.
   *
   * @return <code>this</code> cast as an {@link TIBConstantOperand}
   */
  public final TIBConstantOperand asTIBConstant() {
    return (TIBConstantOperand) this;
  }

  /**
   * Cast to an {@link NullConstantOperand}.
   *
   * @return <code>this</code> cast as an {@link NullConstantOperand}
   */
  public final NullConstantOperand asNullConstant() {
    return (NullConstantOperand) this;
  }

  /**
   * Cast to an {@link BranchOperand}.
   *
   * @return <code>this</code> cast as an {@link BranchOperand}
   */
  public final BranchOperand asBranch() {
    return (BranchOperand) this;
  }

  /**
   * Cast to an {@link BasicBlockOperand}.
   *
   * @return <code>this</code> cast as an {@link BasicBlockOperand}
   */
  public final BasicBlockOperand asBlock() {
    return (BasicBlockOperand) this;
  }

  /**
   * Cast to an {@link MemoryOperand}.
   *
   * @return <code>this</code> cast as an {@link MemoryOperand}
   */
  public final MemoryOperand asMemory() {
    return (MemoryOperand) this;
  }

  /**
   * Cast to an {@link StackLocationOperand}.
   *
   * @return <code>this</code> cast as an {@link StackLocationOperand}
   */
  public final StackLocationOperand asStackLocation() {
    return (StackLocationOperand) this;
  }

  /**
   * Cast to an {@link MethodOperand}.
   *
   * @return <code>this</code> cast as an {@link MethodOperand}
   */
  public final MethodOperand asMethod() {
    return (MethodOperand) this;
  }

  /**
   * Cast to an {@link TypeOperand}.
   *
   * @return <code>this</code> cast as an {@link TypeOperand}
   */
  public final TypeOperand asType() {
    return (TypeOperand) this;
  }

  /**
   * Cast to an {@link ConditionOperand}.
   *
   * @return <code>this</code> cast as an {@link ConditionOperand}
   */
  public final ConditionOperand asCondition() {
    return (ConditionOperand) this;
  }

  /**
   * Cast to an {@link LocationOperand}.
   *
   * @return <code>this</code> cast as an {@link LocationOperand}
   */
  public final LocationOperand asLocation() {
    return (LocationOperand) this;
  }

  /**
   * Does the operand represent a value of an int-like data type?
   *
   * @return <code>true</code> if the data type of <code>this</code>
   *         is int-like as defined by {@link TypeReference#isIntLikeType}
   *         or <code>false</code> if it is not.
   */
  public boolean isIntLike() {
    // default to false and then override in subclasses
    return false;
  }

  /**
   * Does the operand represent a value of the int data type?
   *
   * @return <code>true</code> if the data type of <code>this</code>
   *         is an int as defined by {@link TypeReference#isIntType}
   *         or <code>false</code> if it is not.
   */
  public boolean isInt() {
    // default to false and then override in subclasses
    return false;
  }

  /**
   * Does the operand represent a value of the long data type?
   *
   * @return <code>true</code> if the data type of <code>this</code>
   *         is a long as defined by {@link TypeReference#isLongType}
   *         or <code>false</code> if it is not.
   */
  public boolean isLong() {
    // default to false and then override in subclasses
    return false;
  }

  /**
   * Does the operand represent a value of the float data type?
   *
   * @return <code>true</code> if the data type of <code>this</code>
   *         is a float as defined by {@link TypeReference#isFloatType}
   *         or <code>false</code> if it is not.
   */
  public boolean isFloat() {
    // default to false and then override in subclasses
    return false;
  }

  /**
   * Does the operand represent a value of the double data type?
   *
   * @return <code>true</code> if the data type of <code>this</code>
   *         is a double as defined by {@link TypeReference#isDoubleType}
   *         or <code>false</code> if it is not.
   */
  public boolean isDouble() {
    // default to false and then override in subclasses
    return false;
  }

  /**
   * Does the operand represent a value of the reference data type?
   *
   * @return <code>true</code> if the data type of <code>this</code>
   *         is a reference as defined by {@link TypeReference#isReferenceType}
   *         or <code>false</code> if it is not.
   */
  public boolean isRef() {
    // default to false and then override in subclasses
    return false;
  }

  /**
   * Does the operand represent a value of the address data type?
   *
   * @return <code>true</code> if the data type of <code>this</code>
   *         is an address as defined by {@link TypeReference#isWordType}
   *         or <code>false</code> if it is not.
   */
  public boolean isAddress() {
    // default to false and then override in subclasses
    return false;
  }

  /**
   * Does the operand definitely represent <code>null</code>?
   *
   * @return <code>true</code> if the operand definitely represents
   *         <code>null</code> or <code>false</code> if it does not.
   */
  public boolean isDefinitelyNull() {
    // default to false and then override in subclasses
    return false;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   *
   * @return a copy of <code>this</code>
   */
  public abstract Operand copy();

  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code>
   *           if they are not.
   */
  public abstract boolean similar(Operand op);

  /**
   * Return the {@link TypeReference} of the value represented by the operand.
   *
   * @return the type of the value represented by the operand
   */
  public TypeReference getType() {
    // by default throw OptimizingCompilerException as not all
    // operands have a type.
    throw new OptimizingCompilerException("Getting the type for this operand has no defined meaning: " + this);
  }

  /**
   * Return the index of the operand in its containing instruction (SLOW).
   *
   * @return the index of the operand in its containing instruction
   */
  public int getIndexInInstruction() {
    for (int i = 0; i < instruction.getNumberOfOperands(); i++) {
      Operand op = instruction.getOperand(i);
      if (op == this) return i;
    }
    throw new OptimizingCompilerException("Operand.getIndexInInstruction");
  }

  /**
   * Compare two operands based on their positions in the operand lattice.
   * For the purposes of doing dataflow analysis, Operands can be
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
   */
  public static boolean conservativelyApproximates(Operand op1, Operand op2) {
    // Step 1: Handle pointer equality and bottom
    if (op1 == op2) {
      if (IRGenOptions.DBG_OPERAND_LATTICE) {
        if (op2 == null) {
          VM.sysWrite("operands are both bottom therefore trivially true\n");
        } else {
          VM.sysWrite("operands are identical therefore trivially true\n");
        }
      }
      return true;
    }
    if (op1 == null) {
      if (IRGenOptions.DBG_OPERAND_LATTICE) {
        VM.sysWrite("op1 is bottom, therefore trivially true\n");
      }
      return true;
    }
    if (op2 == null) {
      if (IRGenOptions.DBG_OPERAND_LATTICE) {
        VM.sysWrite("op2 is bottom, therefore trivially false\n");
      }
      return false;
    }

    // Now, handle the non-trivial cases:

    // Step 2: op1 is a constant but op2 is not the same constant
    if (op1.isConstant()) {
      if (op1.similar(op2)) {
        if (IRGenOptions.DBG_OPERAND_LATTICE) {
          VM.sysWrite("operands are similar constants\n");
        }
        return true;
      } else {
        if (IRGenOptions.DBG_OPERAND_LATTICE) {
          VM.sysWrite("op1 is a constant but op2 is not the same constant\n");
        }
        return false;
      }
    }

    // Step 3: op1 is a RegisterOperand
    // This case is complicated by the need to ensure that the
    // various Flag bits are considered as well....
    if (op1.isRegister()) {
      RegisterOperand rop1 = op1.asRegister();
      TypeReference type1 = rop1.getType();
      if (op2.isRegister()) {
        RegisterOperand rop2 = op2.asRegister();
        TypeReference type2 = rop2.getType();
        if (type1 == type2) {
          if (rop1.hasLessConservativeFlags(rop2)) {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("Operands are registers of identical type, but incompatible flags\n");
            }
            return false;
          } else if (BC2IR.hasLessConservativeGuard(rop1, rop2)) {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("Operands are registers of identical type, but with incompatible non-null guards\n");
            }
            return false;
          } else {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("Operands are compatible register operands\n");
            }
            return true;
          }
        } else if (compatiblePrimitives(type1, type2) ||
                   ClassLoaderProxy.includesType(type1, type2) == OptConstants.YES) {
          // types are ok, only have to worry about the flags
          if (rop1.isPreciseType() || rop1.hasLessConservativeFlags(rop2)) {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("Flag mismatch between type compatible register operands\n");
            }
            return false;
          } else if (BC2IR.hasLessConservativeGuard(rop1, rop2)) {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("Non-null guard mismatch between type compatible register operands\n");
            }
            return false;
          } else {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("Operands are compatible register operands\n");
            }
            return true;
          }
        } else {
          if (IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Operands are type incompatible register operands\n");
          }
          return false;
        }
      } else {
        // op2 is not a register
        if (op2 instanceof BC2IR.ReturnAddressOperand || op2 == BC2IR.DUMMY) {
          if (IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Operands are incompatibale values\n");
          }
          return false;
        }

        TypeReference type2 = op2.getType();
        if (type1 == type2 ||
            compatiblePrimitives(type1, type2) ||
            (ClassLoaderProxy.includesType(type1, type2) == OptConstants.YES)) {
          // only have to consider state of op1's flags.  Types are ok.
          if (rop1.isPreciseType() && (type1 != type2)) {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("op1 preciseType bit will be incorrect\n");
            }
            return false;
          }
          if ((rop1.scratchObject instanceof Operand) &&
              ((type2 == TypeReference.NULL_TYPE) ||
               (type2.isIntLikeType() && op2.asIntConstant().value == 0) ||
               (type2.isWordType() && op2.asAddressConstant().value.EQ(Address.zero())) ||
               (type2.isLongType() && op2.asLongConstant().value == 0L))) {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("op1 non null guard will be incorrect");
            }
            return false;
          }
          if (IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("(Constant) op2 was compatible with register op1\n");
          }
          return true;
        } else {
          if (IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Op2 not compatible with register op1\n");
          }
          return false;
        }
      }
    }

    // Step 4: op1 is a IRGEN operand of some form
    if (op1.similar(op2)) {
      if (IRGenOptions.DBG_OPERAND_LATTICE) {
        VM.sysWrite("Compatible BC2IR.* operands\n");
      }
      return true;
    } else {
      if (IRGenOptions.DBG_OPERAND_LATTICE) {
        VM.sysWrite("Incompatible BC2IR.* operands\n");
      }
      return false;
    }
  }

  /**
   * Meet two operands based on their positions in the operand lattice.
   * For the purposes of doing dataflow analysis, Operands can be
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
   * @param reg  the <code>Register</code> to use to
   *             create a new <code>RegisterOperand</code>
   *             if meeting op1 and op2 requires doing so.
   * @return the Operand that is the meet of op1 and op2.
   *         This function will return <code>null</code> when
   *         the meet evaluates to bottom.  It will return
   *         op1 when conservativelyApproximates(op1, op2)
   *         evaluates to <code>true</code>.
   */
  public static Operand meet(Operand op1, Operand op2, Register reg) {
    // Step 1: Handler pointer equality and bottom
    if (op1 == op2) {
      if (IRGenOptions.DBG_OPERAND_LATTICE) {
        if (op1 == null) {
          VM.sysWrite("Both operands are bottom\n");
        } else {
          VM.sysWrite("Operands are identical\n");
        }
      }
      return op1;
    }
    if (op1 == null) {
      if (IRGenOptions.DBG_OPERAND_LATTICE) {
        VM.sysWrite("Op1 was already bottom\n");
      }
      return op1;
    }
    if (op2 == null) {
      if (IRGenOptions.DBG_OPERAND_LATTICE) {
        VM.sysWrite("Op2 is bottom (but op1 was not)\n");
      }
      return op2;
    }

    // Now handle the nontrivial cases...

    // Step 2: op1 is <null> (the null constant)
    if (op1 instanceof NullConstantOperand) {
      if (op2 instanceof NullConstantOperand) {
        if (IRGenOptions.DBG_OPERAND_LATTICE) {
          VM.sysWrite("Both operands are <null>\n");
        }
        return op1;
      } else {
        /*
         * XXX Opt compiler guru please check :)
         *
         * Protect this code from crashing if op2 is DUMMY.  As I understand
         * the calling code this shouldn't happen, but the case for RegisterOperand
         * handles it so I guess it's not too bad for a NullConstantOperand
         * to do so.
         *
         * -- Robin Garner 1 Feb 7
         */
        if (op2 instanceof BC2IR.ReturnAddressOperand || op2 == BC2IR.DUMMY) {
          if (IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Incompatabily typed operands");
          }
          return null; // bottom
        }
        TypeReference type2 = op2.getType();
        if (type2.isReferenceType()) {
          if (IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("op1 is <null>, but op2 is other ref type\n");
          }
          return new RegisterOperand(reg, type2);
        } else {
          if (IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("op1 is <null>, but op2 is not a ref type\n");
          }
          return null; // bottom
        }
      }
    }

    // Step 3: op1 is some other constant
    if (op1.isConstant()) {
      if (op1.similar(op2)) {
        if (IRGenOptions.DBG_OPERAND_LATTICE) {
          VM.sysWrite("op1 and op2 are similar constants\n");
        }
        return op1;
      } else {
        TypeReference superType = ClassLoaderProxy.findCommonSuperclass(op1.getType(), op2.getType());
        if (superType == null) {
          if (IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("op1 and op2 have incompatible types\n");
          }
          return null; // bottom
        } else {
          return new RegisterOperand(reg, superType);
        }
      }
    }

    // Step 4: op1 is a register operand
    // This case is complicated by the need to ensure that
    // the various Flag bits are considered as well....
    if (op1.isRegister()) {
      RegisterOperand rop1 = op1.asRegister();
      TypeReference type1 = rop1.getType();
      if (op2.isRegister()) {
        RegisterOperand rop2 = op2.asRegister();
        TypeReference type2 = rop2.getType();
        if (type1 == type2) {
          if (IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Identically typed register operands, checking flags...");
          }
          if (rop1.hasLessConservativeFlags(rop2)) {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("mismatch\n");
            }
            RegisterOperand res = new RegisterOperand(reg, type1, rop1.getFlags(), rop1.isPreciseType(), rop1.isDeclaredType());
            if (rop1.scratchObject instanceof Operand &&
                rop2.scratchObject instanceof Operand &&
                (((Operand) rop1.scratchObject).similar(((Operand) rop2.scratchObject)))) {
              res.scratchObject = rop1.scratchObject; // compatible, so preserve onto res
            }
            res.meetInheritableFlags(rop2);
            return res;
          } else if (BC2IR.hasLessConservativeGuard(rop1, rop2)) {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite(
                  "Operands are registers of identical type with compatible flags but with incompatible non-null guards\n");
            }
            // by not setting scratchObject we mark as possible null
            return new RegisterOperand(reg, type1, rop1.getFlags(), rop1.isPreciseType(), rop1.isDeclaredType());
          } else {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("match\n");
            }
            return op1;
          }
        } else if (compatiblePrimitives(type1, type2) ||
                   ClassLoaderProxy.includesType(type1, type2) == OptConstants.YES) {
          if (IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Compatibly typed register operands, checking flags...");
          }
          if (rop1.isPreciseType() || rop1.hasLessConservativeFlags(rop2)) {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("mismatch\n");
            }
            RegisterOperand res = new RegisterOperand(reg, type1, rop1.getFlags(), rop1.isPreciseType(), rop1.isDeclaredType());
            res.meetInheritableFlags(rop2);
            // even if both op1 & op2 are precise,
            // op1.type != op2.type, so clear it on res
            res.clearPreciseType();
            if (rop1.scratchObject instanceof Operand &&
                rop2.scratchObject instanceof Operand &&
                (((Operand) rop1.scratchObject).similar(((Operand) rop2.scratchObject)))) {
              // it matched, so preserve onto res.
              res.scratchObject = rop1.scratchObject;
            }
            return res;
          } else if (BC2IR.hasLessConservativeGuard(rop1, rop2)) {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("Operands are registers of compatible type and flags but with incompatible non-null guards\n");
            }
            return new RegisterOperand(reg, type1, rop1.getFlags(), rop1.isPreciseType(), rop1.isDeclaredType());
          } else {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("match\n");
            }
            return op1;
          }
        } else {
          if (IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Incompatibly typed register operands...(" + type1 + ", " + type2 + ")...");
          }
          TypeReference resType = ClassLoaderProxy.findCommonSuperclass(type1, type2);
          if (resType == null) {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("no common supertype, returning bottom\n");
            }
            return null; // bottom
          } else {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("found common supertype\n");
            }
            RegisterOperand res = new RegisterOperand(reg, resType, rop1.getFlags(), false, false);
            res.meetInheritableFlags(rop2);
            res.clearPreciseType();     // invalid on res
            res.clearDeclaredType();    // invalid on res
            if (rop1.scratchObject instanceof Operand &&
                rop2.scratchObject instanceof Operand &&
                (((Operand) rop1.scratchObject).similar(((Operand) rop2.scratchObject)))) {
              // it matched, so preserve onto res.
              res.scratchObject = rop1.scratchObject;
            }
            return res;
          }
        }
      } else {
        if (op2 instanceof BC2IR.ReturnAddressOperand || op2 == BC2IR.DUMMY) {
          if (IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Incompatibly typed operands");
          }
          return null; // bottom
        }
        TypeReference type2 = op2.getType();
        if (type1 == type2 ||
            compatiblePrimitives(type1, type2) ||
            (ClassLoaderProxy.includesType(type1, type2) == OptConstants.YES)) {
          if (IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Compatibly typed register & other operand, checking flags...");
          }
          RegisterOperand res = rop1;
          if (res.isPreciseType() && type1 != type2) {
            res = res.copyU2U();
            res.clearPreciseType();
          }
          if ((rop1.scratchObject instanceof Operand) &&
              ((type2 == TypeReference.NULL_TYPE) ||
               (type2.isIntLikeType() && op2.asIntConstant().value == 0) ||
               (type2.isWordType() && op2.asAddressConstant().value.isZero()) ||
               (type2.isLongType() && op2.asLongConstant().value == 0L))) {
            res = res.copyU2U();
            res.scratchObject = null;
          }
          if (IRGenOptions.DBG_OPERAND_LATTICE) {
            if (res == rop1) {
              VM.sysWrite("match\n");
            } else {
              VM.sysWrite("mismatch\n");
            }
          }
          return res;
        } else {
          if (IRGenOptions.DBG_OPERAND_LATTICE) {
            VM.sysWrite("Incompatibly typed register & other operand...(" + type1 + ", " + type2 + ")...");
          }
          TypeReference resType = ClassLoaderProxy.findCommonSuperclass(type1, type2);
          if (resType == null) {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("no common supertype, returning bottom\n");
            }
            return null; // bottom
          } else {
            if (IRGenOptions.DBG_OPERAND_LATTICE) {
              VM.sysWrite("found common supertype\n");
            }
            return new RegisterOperand(reg, resType);
          }
        }
      }
    }

    // Step 5: op1 is some IRGEN operand
    if (op1.similar(op2)) {
      if (IRGenOptions.DBG_OPERAND_LATTICE) {
        VM.sysWrite("Compatible BC2IR.* operands\n");
      }
      return op1;
    } else {
      if (IRGenOptions.DBG_OPERAND_LATTICE) {
        VM.sysWrite("Incompatible BC2IR.* operands, returning bottom\n");
      }
      return null; // bottom
    }
  }

  private static boolean compatiblePrimitives(TypeReference type1, TypeReference type2) {
    if (type1.isIntLikeType() && type2.isIntLikeType()) {
      if (type1.isIntType()) {
        return type2.isBooleanType() || type2.isByteType() || type2.isShortType() || type2.isIntType();
      }
      if (type1.isShortType()) {
        return type2.isBooleanType() || type2.isByteType() || type2.isShortType();
      }
      if (type1.isByteType()) {
        return type2.isBooleanType() || type2.isByteType();
      }
      if (type1.isBooleanType()) {
        return type2.isBooleanType();
      }
    }
    return false;
  }

}
