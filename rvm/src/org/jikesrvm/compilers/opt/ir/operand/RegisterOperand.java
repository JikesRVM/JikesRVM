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
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;

/**
 * A symbolic or physical register.
 * A wrapper around an Register that may contain program-point specific
 * information about the value denoted by the Register.
 * <p>
 * TODO: This class is due for a refactor into subclasses
 * to split out the symbolic &amp; physical registers and to create
 * special behavior for symbolic registers used as phi operands and
 * as validation (guard) operands.
 *
 * @see Operand
 */
public final class RegisterOperand extends Operand {

  /**
   * Register object that this operand uses.
   */
  private Register register;

  /**
   * Inferred data type of the contents of the register.
   */
  private TypeReference type;

  /**
   * Optimizations can use it for different purposes, as long as they
   * are not used simultaneously
   */
  public Object scratchObject;

  /**
   * 16bit scratch word that can be used for different optimizations.
   */
  private short info;

  /**
   * Type of a RegisterOperand can be in one of three states:
   *
   * <ol>
   * <li>a- declared: the type obtained from a
   * getfield,getstatic,putfield,putstatic,array load</li>
   * <li>b- precise:  obtained from a NEW.</li>
   * <li>c- computed: (default) computed from propagating types.</li>
   * </ol>
   *
   * If the register is holding an int-like type it can be holding
   * just positive values.
   *
   * For calling conventions registers can be for parameters or
   * volatile/non-volatile. The value in the register can also be
   * extant - that is a definite non-null value (e.g. this pointer).
   */
  private byte flags;

  /**
   * The type has been declared as obtained from a getfield,
   * getstatic, putfield, putstatic, array load
   */
  private static final int DECLARED_TYPE = 0x01;
  /** We know precisely the type as it was create by a NEW */
  private static final int PRECISE_TYPE = 0x02;
  /** Is the contents of the int-like register always positive? */
  private static final int POSITIVE = 0x04;

  /** the register operand is for a parameter */
  private static final byte PARAMETER = 0x10;
  /** is this a non-volatile physical register? */
  private static final byte NON_VOLATILE = 0x20;
  /** is this an extant object? */
  private static final byte EXTANT = 0x40;

  /**
   * Some bits used to characterize guards.  TODO: Maybe declare a
   * new type GuardOperand extends RegisterOperand, and save
   * this state there?
   */
  private byte flags2;

  /** guard operand that represents a taken branch */
  private static final byte TAKEN = 0x01;
  /** guard operand that represents a not taken branch */
  private static final byte NOT_TAKEN = 0x02;
  /** Guard operand that originates from a bounds-check */
  private static final byte BOUNDS_CHECK = 0x04;
  /** Guard operand that originates from a null-check */
  private static final byte NULL_CHECK = 0x08;

  /**
   * Constructs a new register operand with the given register and data type.
   *
   * @param reg register object
   * @param typ data type
   */
  public RegisterOperand(Register reg, TypeReference typ) {
    setRegister(reg);
    setType(typ);
  }

  /**
   * Constructs a new register operand with the given register, data type and flags.
   *
   * @param reg register object
   * @param typ data type
   * @param inFlags to set for this register
   * @param isPrecise is this a precise type
   * @param isDeclared is this a declared type
   */
  public RegisterOperand(Register reg, TypeReference typ, byte inFlags,
      boolean isPrecise, boolean isDeclared) {
    setRegister(reg);
    flags = inFlags;
    if (isPrecise) {
      setPreciseType(typ);
    } else {
      clearPreciseType();
      setType(typ);
    }
    if (isDeclared) {
      setDeclaredType();
    } else {
      clearDeclaredType();
    }
  }

  /**
   * Returns a copy of this register operand as an operand
   */
  @Override
  public Operand copy() {
    return copyRO();
  }

  /**
   * Returns a copy of this register operand as a register operand.<p>
   *
   * NOTE: preserves the flags, info and scratchObject.  Preserving is
   * required in all cases as several phases also depend on scratch
   * and/or scratchObject being copied
   */
  public RegisterOperand copyRO() {
    RegisterOperand temp = new RegisterOperand(register, type);
    temp.info = info;
    temp.flags = flags;
    temp.flags2 = flags2;
    temp.scratchObject = scratchObject;
    if (VM.VerifyAssertions) verifyPreciseType();
    return temp;
  }

  /**
   * Returns a copy of this use register operand as another use reg operand.
   */
  public RegisterOperand copyU2U() {
    return copyRO();
  }

  /**
   * Returns a copy of this def register operand as a use.
   */
  public RegisterOperand copyD2U() {
    return copyRO();
  }

  /**
   * Returns a copy of this use register operand as a def.
   */
  public RegisterOperand copyU2D() {
    return copyRO();
  }

  /**
   * Returns a copy of this def register operand as a def.
   */
  public RegisterOperand copyD2D() {
    return copyRO();
  }

  /**
   * Returns whether the given operand is a register operand and has the same
   * register object.
   *
   * @param op operand to compare against
   */
  @Override
  public boolean similar(Operand op) {
    return (op instanceof RegisterOperand) && (register == ((RegisterOperand) op).getRegister());
  }

  /**
   * Copy type information from the given operand into this one
   * including flag information on whether this is a precise type or
   * not
   * @param rhs the type to copy information from
   */
  public void copyType(RegisterOperand rhs) {
    this.flags = rhs.flags;
    this.setType(rhs.getType()); // setting type this way will force checking of precision
  }

  @Override
  public boolean isIntLike() {
    return type.isIntLikeType();
  }

  @Override
  public boolean isInt() {
    return type.isIntType();
  }

  @Override
  public boolean isLong() {
    return type.isLongType();
  }

  @Override
  public boolean isFloat() {
    return type.isFloatType();
  }

  @Override
  public boolean isDouble() {
    return type.isDoubleType();
  }

  @Override
  public boolean isRef() {
    return type.isReferenceType();
  }

  @Override
  public boolean isAddress() {
    return type.isWordLikeType();
  }

  @Override
  public boolean isDefinitelyNull() {
    return type == TypeReference.NULL_TYPE;
  }

  /** Does this register hold a parameter */
  public boolean isParameter() { return (flags & PARAMETER) != 0; }

  /** Set this register as being used to hold parameters */
  public void setParameter() { flags |= PARAMETER; }

  /** Clear this register from being used to hold parameters */
  public void clearParameter() { flags &= ~PARAMETER; }

  /** Is this a volatile register? */
  public boolean isNonVolatile() { return (flags & NON_VOLATILE) != 0; }

  /** Set this register as being non-volatile */
  public void setNonVolatile() { flags |= NON_VOLATILE; }

  /** Set this register as being volatile */
  public void clearNonVolatile() { flags &= ~NON_VOLATILE; }

  /**
   * Is this register known to contain either NULL or an object whose class was fully loaded
   * before the current method was called?
   * This fact is used to determine whether we can optimize away inline guards
   * based on pre-existence based inlining.*/
  public boolean isExtant() { return (flags & EXTANT) != 0; }

  /**
   * Set this register as holding an extant object (or NULL)
   * (ie, an object whose class was fully loaded before the current method was called).
   * This fact is used to determine whether we can optimize away inline guards based on pre-existence
   * based inlining.
   */
  public void setExtant() { flags |= EXTANT; }

  /** Clear this register from holding an extant value */
  public void clearExtant() { flags &= ~EXTANT; }

  /** Does this register have a declared type? */
  public boolean isDeclaredType() { return (flags & DECLARED_TYPE) != 0; }

  /** Set this register as having a declared type */
  public void setDeclaredType() { flags |= DECLARED_TYPE; }

  /** Clear this register from having a declared type */
  public void clearDeclaredType() { flags &= ~DECLARED_TYPE; }

  private void verifyPreciseType() {
    if (!VM.VerifyAssertions) {
      VM.sysFail("Calls to verifyPreciseType must always be guarded by if \"(VM.VerifyAssertions)\"!");
    } else {
      if (isPreciseType() && type != null &&
          type.isClassType() && type.isResolved()) {
        RVMClass preciseClass = type.resolve().asClass();
        if(preciseClass.isInterface() || preciseClass.isAbstract()) {
          VM.sysWriteln("Error processing instruction: ", this.instruction.toString());
          throw new OptimizingCompilerException("Unable to set type as it would make this interface/abstract class precise " + preciseClass);
        }
      }
    }
  }
  /** Do we know the precise type of this register? */
  public boolean isPreciseType() { return (flags & PRECISE_TYPE) != 0; }

  /** Set this register as having a precise type */
  public void setPreciseType() {
    flags |= PRECISE_TYPE;
    if (VM.VerifyAssertions) verifyPreciseType();
  }

  /** Clear this register from having a precise type */
  public void clearPreciseType() { flags &= ~PRECISE_TYPE; }

  /** Is this register a declared or a precise type? */
  public boolean isDeclaredOrPreciseType() { return (flags & (DECLARED_TYPE | PRECISE_TYPE)) != 0; }

  /** Is this register a positive int? */
  public boolean isPositiveInt() { return (flags & POSITIVE) != 0; }

  /** Set this register as being a positive int */
  public void setPositiveInt() { flags |= POSITIVE; }

  /** Return a byte encoding register flags */
  public byte getFlags() {
    return flags;
  }

  /** Clear the flags of a register */
  public void clearFlags() {
    flags = 0;
  }

  /** Merge two sets of register flags */
  public void addFlags(byte inFlag) {
    flags |= inFlag;
    if (VM.VerifyAssertions) verifyPreciseType();
  }

  /** Currently all flags are inheritable, so copy all flags from src */
  public void setInheritableFlags(RegisterOperand src) {
    flags = src.getFlags();
    if (VM.VerifyAssertions) verifyPreciseType();
  }

  /** Currently all flags are "meetable", so mask flags together */
  public void meetInheritableFlags(RegisterOperand other) {
    flags &= other.flags;
  }

  /**
   * Return true if we have any bits set (flag true) that other
   * doesn't. It's ok for other to have bits set true that we have set
   * to false.
   */
  public boolean hasLessConservativeFlags(RegisterOperand other) {
    return other.getFlags() != (getFlags() | other.getFlags());
  }

  /** Is this a guard operand from a taken branch? */
  public boolean isTaken() { return (flags2 & TAKEN) != 0; }

  /** Set this a guard operand from a taken branch */
  public void setTaken() { flags2 |= TAKEN; }

  /** Clear this from being a guard operand from a taken branch */
  public void clearTaken() { flags2 &= ~TAKEN; }

  /** Is this a guard operand from a not taken branch? */
  public boolean isNotTaken() { return (flags2 & NOT_TAKEN) != 0; }

  /** Set this a guard operand from a not taken branch */
  public void setNotTaken() { flags2 |= NOT_TAKEN; }

  /** Clear this from being a guard operand from a not taken branch */
  public void clearNotTaken() { flags2 &= ~NOT_TAKEN; }

  /** Is this a guard operand from a bounds check? */
  public boolean isBoundsCheck() { return (flags2 & BOUNDS_CHECK) != 0; }

  /** Set this as a guard operand from a bounds check */
  public void setBoundsCheck() { flags2 |= BOUNDS_CHECK; }

  /** Clear this from being a guard operand from a bounds check */
  public void clearBoundsCheck() { flags2 &= ~BOUNDS_CHECK; }

  /** Is this a guard operand from a null check? */
  public boolean isNullCheck() { return (flags2 & NULL_CHECK) != 0; }

  /** Set this as being a guard operand from a null check */
  public void setNullCheck() { flags2 |= NULL_CHECK; }

  /** Clear this from being a guard operand from a null check */
  public void clearNullCheck() { flags2 &= ~NULL_CHECK; }

  /** Get info scratch short */
  public short getInfo() {
    return info;
  }

  /** Set info scratch short */
  public void setInfo(short value) {
    info = value;
  }

  /**
   * Sets scratch object of the register operand to parameter. (sic)
   * Since there is not multiple inheritance in Java, I am copying the
   * accessor functions &amp; fields of LinkedListElement.  This field
   * is used to maintain lists of USEs and DEFs
   */
  public void setNext(RegisterOperand Next) {
    scratchObject = Next;
  }

  /**
   * Sets scratch object of the register operand to parameter.
   */
  public void append(RegisterOperand next) {
    scratchObject = next;
  }

  /**
   * Returns the scratch object of the register operand
   */
  public RegisterOperand getNext() {
    return (RegisterOperand) scratchObject;
  }

  /**
   * Returns the string representation of this operand.
   */
  @Override
  public String toString() {
    String s = register.toString();
    if (type != null) {
      if (type != TypeReference.VALIDATION_TYPE) {
        s = s + "(" + type.getName();
        if (isExtant()) s += ",x";
        if (isDeclaredType()) s += ",d";
        if (isPreciseType()) s += ",p";
        if (isPositiveInt()) s += ",+";
        s += ")";
      } else {
        s += "(GUARD)";
      }
    }
    return s;
  }

  /**
   * Modify the register
   */
  public void setRegister(Register register) {
    this.register = register;
  }

  /**
   * @return the register
   */
  public Register getRegister() {
    return register;
  }

  /**
   * Set the {@link TypeReference} of the value represented by the operand.
   *
   * @param t the inferred data type of the contents of the register
   */
  public void setType(TypeReference t) {
    type = t;
    if (VM.VerifyAssertions) verifyPreciseType();
  }

  /**
   * Set the {@link TypeReference} of the value represented by the operand and
   * make the type precise.
   *
   * @param t the inferred data type of the contents of the register
   */
  public void setPreciseType(TypeReference t) {
    setType(t);
    flags |= PRECISE_TYPE;
    if (VM.VerifyAssertions) verifyPreciseType();
  }

  /**
   * Return the {@link TypeReference} of the value represented by the operand.
   *
   * @return the inferred data type of the contents of the register
   */
  @Override
  public TypeReference getType() {
    return type;
  }

  /**
   * Refine the type of the register to t if t is a more precise type than the
   * register currently holds
   *
   * @param t type to try to refine to
   */
  public void refine(TypeReference t) {
    // TODO: see JIRA RVM-137
    if (!isPreciseType()) {
      setType(t);
    }
  }

  /**
   * Note: This method is currently used only by test cases.<p>
   *
   * Does this operand have the same properties as the given Operand? This method
   * checks only the properties specific to RegisterOperand.
   *
   * @param other
   * @return whether the given RegisterOperand could be seen as a copy of this one
   */
  public boolean sameRegisterPropertiesAs(RegisterOperand other) {
    return this.register == other.register && this.flags == other.flags &&
        this.flags2 == other.flags2 && this.info == other.info &&
        this.scratchObject == other.scratchObject;
  }

  /**
   * Note: This method is currently used only by test cases.<p>
   *
   * Does this operand have the same properties as the given Operand? This method
   * checks only the properties specific to RegisterOperand.
   *
   * @param other
   * @return whether the given RegisterOperand could be seen as a copy of this one
   *  if {@link #scratchObject} can be ignored
   */
  public boolean sameRegisterPropertiesAsExceptForScratchObject(RegisterOperand other) {
    return this.register == other.register && this.flags == other.flags &&
        this.flags2 == other.flags2 && this.info == other.info;
  }
}
