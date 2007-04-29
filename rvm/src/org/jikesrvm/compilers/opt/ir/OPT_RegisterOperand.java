/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.classloader.VM_TypeReference;

/**
 * A symbolic or physical register.
 * A wrapper around an OPT_Register that may contain program-point specific
 * information about the value denoted by the OPT_Register.
 * 
 * TODO: This class is due for a refactor into subclasses
 * to split out the symbolic &amp; physical registers and to create
 * special behavior for symbolic registers used as phi operands and
 * as validation (guard) operands.
 * 
 * @see OPT_Operand
 * @author Mauricio Serrano
 * @author John Whaley
 * @modified Stephen Fink
 * @author Ian Rogers
 */
public final class OPT_RegisterOperand extends OPT_Operand {

  /** 
   * Register object that this operand uses.
   */
  public OPT_Register register;

  /**
   * Inferred data type of the contents of the register.
   */
  public VM_TypeReference type;

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
  private static final int DECLARED_TYPE  = 0x01;
  /** We know precisely the type as it was create by a NEW */
  private static final int PRECISE_TYPE   = 0x02;
  /** Is the contents of the int-like register always positive? */
  private static final int POSITIVE       = 0x04;
  
  /** the register operand is for a parameter */
  private static final byte PARAMETER    = 0x10;
  /** is this a non-volatile physical register? */
  private static final byte NON_VOLATILE = 0x20;
  /** is this an extant object? */
  private static final byte EXTANT       = 0x40;


  /**
   * Some bits used to characterize guards.  TODO: Maybe declare a
   * new type OPT_GuardOperand extends OPT_RegisterOperand, and save
   * this state there?
   */
  private byte flags2;

  /** guard operand that represents a taken branch */
  private static final byte TAKEN        = 0x01;
  /** guard operand that represents a not taken branch */
  private static final byte NOT_TAKEN    = 0x02;
  /** Guard operand that originates from a bounds-check */
  private static final byte BOUNDS_CHECK = 0x04;
  /** Guard operand that originates from a null-check */
  private static final byte NULL_CHECK   = 0x08;
  
  /**
   * Constructs a new register operand with the given register and data type.
   * 
   * @param reg register object
   * @param typ data type
   */
  public OPT_RegisterOperand(OPT_Register reg, VM_TypeReference typ) {
    register = reg; 
    type = typ;
  }
  /**
   * Constructs a new register operand with the given register, data type and flags.
   * 
   * @param reg register object
   * @param typ data type
   * @param inFlags to set for this register
   */
  public OPT_RegisterOperand(OPT_Register reg, VM_TypeReference typ, byte inFlags) {
    register = reg; 
    type = typ;
    flags = inFlags;
  }

  /**
   * Returns a copy of this register operand as an operand
   */
  public OPT_Operand copy() {
    return copyRO();
  }

  /**
   * Returns a copy of this register operand as a register operand
   * NOTE: preserves the flags, info and scratchObject.  Preserving is
   * required in all cases as several phases also depend on scratch
   * and/or scratchObject being copied
   */
  public OPT_RegisterOperand copyRO() {
    OPT_RegisterOperand temp = new OPT_RegisterOperand(register, type);
    temp.info = info;
    temp.flags = flags; 
    temp.flags2 = flags2; 
    temp.scratchObject = scratchObject;
    return temp;
  }

  /**
   * Returns a copy of this use register operand as another use reg operand.
   */
  public OPT_RegisterOperand copyU2U() {
    return copyRO();
  }

  /**
   * Returns a copy of this def register operand as a use.
   */
  public OPT_RegisterOperand copyD2U() {
    return copyRO();
  }

  /**
   * Returns a copy of this use register operand as a def.
   */
  public OPT_RegisterOperand copyU2D() {
    return copyRO();
  }

  /**
   * Returns a copy of this def register operand as a def.
   */
  public OPT_RegisterOperand copyD2D() {
    return copyRO();
  }

  /**
   * Returns whether the given operand is a register operand and has the same
   * register object.
   * 
   * @param op operand to compare against
   */
  public boolean similar(OPT_Operand op) {
    return (op instanceof OPT_RegisterOperand) &&
      (register == ((OPT_RegisterOperand)op).register);
  }

  /**
   * Modify the register
   */
  public void setRegister(OPT_Register replacement) {
    register = replacement;
  }

  /**
   * Return the {@link VM_TypeReference} of the value represented by the operand.
   * 
   * @return the inferred data type of the contents of the register
   */
  public VM_TypeReference getType() {
    return type;
  }

  /**
   * Copy type information from the given operand into this one
   * including flag information on whether this is a precise type or
   * not
   * @param rhs the type to copy information from
   */
  public void copyType(OPT_RegisterOperand rhs) {
    this.type = rhs.type;
    this.flags = rhs.flags;
  }

  /**
   * Does the operand represent a value of an int-like data type?
   * 
   * @return <code>true</code> if the data type of <code>this</code> 
   *         is int-like as defined by {@link VM_TypeReference#isIntLikeType}
   *         or <code>false</code> if it is not.
   */
  public boolean isIntLike() {
    return type.isIntLikeType();
  }

  /**
   * Does the operand represent a value of an int data type?
   * 
   * @return <code>true</code> if the data type of <code>this</code> 
   *         is int-like as defined by {@link VM_TypeReference#isIntLikeType}
   *         or <code>false</code> if it is not.
   */
  public boolean isInt() {
    return type.isIntType();
  }

  /**
   * Does the operand represent a value of the long data type?
   * 
   * @return <code>true</code> if the data type of <code>this</code> 
   *         is a long as defined by {@link VM_TypeReference#isLongType}
   *         or <code>false</code> if it is not.
   */
  public boolean isLong() {
    return type.isLongType();
  }

  /**
   * Does the operand represent a value of the float data type?
   * 
   * @return <code>true</code> if the data type of <code>this</code> 
   *         is a float as defined by {@link VM_TypeReference#isFloatType}
   *         or <code>false</code> if it is not.
   */
  public boolean isFloat() {
    return type.isFloatType();
  }

  /**
   * Does the operand represent a value of the double data type?
   * 
   * @return <code>true</code> if the data type of <code>this</code> 
   *         is a double as defined by {@link VM_TypeReference#isDoubleType}
   *         or <code>false</code> if it is not.
   */
  public boolean isDouble() {
    return type.isDoubleType();
  }

  /**
   * Does the operand represent a value of the reference data type?
   * 
   * @return <code>true</code> if the data type of <code>this</code> 
   *         is a reference as defined by {@link VM_TypeReference#isReferenceType}
   *         or <code>false</code> if it is not.
   */
  public boolean isRef() {
    return type.isReferenceType();
  }

  /**
   * Does the operand represent a value of the address data type?
   * 
   * @return <code>true</code> if the data type of <code>this</code> 
   *         is an address as defined by {@link VM_TypeReference#isWordType}
   *         or <code>false</code> if it is not.
   */
  public boolean isAddress() {
    return type.isWordType();
  }

  /**
   * Does the operand definitely represent <code>null</code>?
   * 
   * @return <code>true</code> if the operand definitely represents
   *         <code>null</code> or <code>false</code> if it does not.
   */
  public boolean isDefinitelyNull() {
    return type == VM_TypeReference.NULL_TYPE;
  }

  /** Does this register hold a parameter */
  public boolean isParameter()       {   return (flags & PARAMETER    ) != 0; }
  /** Set this register as being used to hold parameters */
  public void    setParameter()      {   flags |=  PARAMETER;                 }
  /** Clear this register from being used to hold parameters */
  public void    clearParameter()    {   flags &= ~PARAMETER;                 }

  /** Is this a volatile register? */
  public boolean isNonVolatile()     {   return (flags & NON_VOLATILE ) != 0; }
  /** Set this register as being non-volatile */
  public void    setNonVolatile()    {   flags |=  NON_VOLATILE;              }
  /** Set this register as being volatile */
  public void    clearNonVolatile()  {   flags &= ~NON_VOLATILE;              }

  /** Is this register known to contain an object? */
  public boolean isExtant()          {   return (flags & EXTANT       ) != 0; }
  /** Set this register as holding a known to exist object */
  public void    setExtant()         {   flags |=  EXTANT;                    }
  /** Clear this register from holding a known to exist object */
  public void    clearExtant()       {   flags &= ~EXTANT;                    }

  /** Does this register have a declared type? */
  public boolean isDeclaredType()    {   return (flags & DECLARED_TYPE) != 0; }
  /** Set this register as having a declared type */
  public void    setDeclaredType()   {   flags |=  DECLARED_TYPE;             }
  /** Clear this register from having a declared type */
  public void    clearDeclaredType() {   flags &= ~DECLARED_TYPE;             }

  /** Do we know the precise type of this register? */
  public boolean isPreciseType()     {   return (flags & PRECISE_TYPE ) != 0; }
  /** Set this register as having a precise type */
  public void    setPreciseType()    {   flags |=  PRECISE_TYPE;              }
  /** Clear this register from having a precise type */
  public void    clearPreciseType()  {   flags &= ~PRECISE_TYPE;              }
  
  /** Is this register a declared or a precise type? */
  public boolean isDeclaredOrPreciseType() {  return (flags & (DECLARED_TYPE|PRECISE_TYPE)) != 0; }
  
  /** Is this register a positive int? */
  public boolean isPositiveInt()     {   return (flags & POSITIVE     ) != 0; }
  /** Set this register as being a positive int */
  public void    setPositiveInt()    {   flags |=  POSITIVE;                  }
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
  }

  /** Currently all flags are inheritable, so copy all flags from src */
  public void setInheritableFlags(OPT_RegisterOperand src) {
    flags = src.getFlags();
  }

  /** Currently all flags are "meetable", so mask flags together */
  public void meetInheritableFlags(OPT_RegisterOperand other) {
    flags &=  other.flags;
  }

  /**
   * Return true if we have any bits set (flag true) that other
   * doesn't. It's ok for other to have bits set true that we have set
   * to false.
   */
  public boolean hasLessConservativeFlags(OPT_RegisterOperand other) {
    return other.getFlags() != (getFlags() | other.getFlags());
  }

  /** Is this a guard operand from a taken branch? */
  public boolean isTaken()              {   return (flags2 & TAKEN) != 0;     }
  /** Set this a guard operand from a taken branch */
  public void    setTaken()             {   flags2 |=  TAKEN;                 }
  /** Clear this from being a guard operand from a taken branch */
  public void    clearTaken()           {   flags2 &= ~TAKEN;                 }
  
  /** Is this a guard operand from a not taken branch? */
  public boolean isNotTaken()           {   return (flags2 & NOT_TAKEN) != 0; }
  /** Set this a guard operand from a not taken branch */
  public void    setNotTaken()          {   flags2 |=  NOT_TAKEN;             }
  /** Clear this from being a guard operand from a not taken branch */
  public void    clearNotTaken()        {   flags2 &= ~NOT_TAKEN;             }
  
  /** Is this a guard operand from a bounds check? */
  public boolean isBoundsCheck()        {   return (flags2 & BOUNDS_CHECK) != 0; }
  /** Set this as a guard operand from a bounds check */
  public void    setBoundsCheck()       {   flags2 |=  BOUNDS_CHECK;          }
  /** Clear this from being a guard operand from a bounds check */
  public void    clearBoundsCheck()     {   flags2 &= ~BOUNDS_CHECK;          }
  
  /** Is this a guard operand from a null check? */
  public boolean isNullCheck()          {   return (flags2 & NULL_CHECK) != 0; }
  /** Set this as being a guard operand from a null check */
  public void    setNullCheck()         {   flags2 |=  NULL_CHECK;            }
  /** Clear this from being a guard operand from a null check */
  public void    clearNullCheck()       {   flags2 &= ~NULL_CHECK;            }
  
  /** Get info scratch short */
  public final short getInfo() {
    return info;
  }

  /** Set info scratch short */
  public final void setInfo(short value) {
    info = value;
  }

  /**
   * Sets scratch object of the register operand to parameter. (sic)
   * Since there is not multiple inheritance in Java, I am copying the
   * accessor functions &amp; fields of LinkedListElement.  This field
   * is used to maintain lists of USEs and DEFs
   */
  public final void setNext(OPT_RegisterOperand Next) {
    scratchObject = Next;
  }

  /**
   * Sets scratch object of the register operand to parameter.
   */
  public final void append(OPT_RegisterOperand next) {
    scratchObject = next;
  }

  /**
   * Returns the scratch object of the register operand
   */
  public final OPT_RegisterOperand getNext() { 
    return (OPT_RegisterOperand)scratchObject;
  }

  /**
   * Returns the string representation of this operand.
   */
  public String toString() {
    String s = register.toString();
    if (type != null) {
      if (type != VM_TypeReference.VALIDATION_TYPE) {
        s  = s + "("+type.getName();
        if (isExtant())       s += ",x";
        if (isDeclaredType()) s += ",d";
        if (isPreciseType())  s += ",p";
        if (isPositiveInt())  s += ",+";
        s += ")";
      } else {
        s += "(GUARD)";
      }
    }
    return s;
  }

}
