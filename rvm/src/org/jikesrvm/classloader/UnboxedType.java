/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

import org.jikesrvm.Constants;
import org.jikesrvm.VM;
import org.jikesrvm.objectmodel.TIB;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * Description of an Unboxed Magic type.
 *
 * Currently, unboxed types are restricted to be values that can fit in a single machines word.
 *
 * @see RVMType
 * @see RVMClass
 * @see RVMArray
 * @see Primitive
 */
@NonMoving
public final class UnboxedType extends RVMType implements Constants, ClassLoaderConstants {
  /**
   * The pretty (external) name for this Unboxed type.
   */
  private final Atom name;

  /**
   * How many slots in the Java Expression Stack does it take
   * to hold a value of this primitive type?
   */
  private final int stackWords;

  /**
   * How many bytes in memory does it take to hold a value of this
   * primitive type?
   */
  private final int memoryBytes;

  /**
   * Name - something like "int".
   */
  @Override
  @Pure
  public String toString() {
    return name.toString();
  }

  /**
   * Constructor
   * @param tr   The canonical type reference for this primitive
   * @param classForType The java.lang.Class representation
   * @param name The name for this primitive
   * @param stackWords The stack slots used by this primitive
   * @param memoryBytes The bytes in memory used by this primitive
   */
  private UnboxedType(TypeReference tr, Class<?> classForType, Atom name, int stackWords, int memoryBytes) {
    super(tr,    // type reference
          classForType, // j.l.Class representation
          -1,    // dimensionality
          null  // runtime visible annotations
    );
    this.name = name;
    this.stackWords = stackWords;
    this.memoryBytes = memoryBytes;
    this.depth = 0;
  }

  /**
   * Create an instance of a {@link UnboxedType}
   * @param tr   The canonical type reference for this primitive
   */
  static UnboxedType createUnboxedType(TypeReference tr) {
    Atom name;
    int stackWords = 1;
    int memoryBytes;
    Class<?> classForType;

    name = tr.getName();
    if (tr == TypeReference.Address ||
        tr == TypeReference.Word ||
        tr == TypeReference.Offset ||
        tr == TypeReference.Extent) {
      memoryBytes = BYTES_IN_ADDRESS;
    } else if (tr == TypeReference.Code) {
      memoryBytes = VM.BuildForIA32 ? BYTES_IN_BYTE : BYTES_IN_INT;
    } else {
      throw new Error("Unknown unboxed type " + tr.getName());
    }
    try {
      classForType = Class.forName(name.classNameFromDescriptor());
    } catch (Exception e) {
      throw new Error("Error getting java.lang.Class wrapper for type " + name.classNameFromDescriptor());
    }

    return new UnboxedType(tr, classForType, name, stackWords, memoryBytes);
  }

  /**
   * get number of superclasses to Object
   * @return 0
   */
  @Override
  @Pure
  @Uninterruptible
  public int getTypeDepth() {
    return 0;
  }

  /**
   * Reference Count GC: Is a reference of this type contained in
   * another object inherently acyclic (without cycles) ?
   * @return true
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isAcyclicReference() {
    return true;
  }

  /**
   * Number of [ in descriptor for arrays; -1 for primitives; 0 for
   * classes
   * @return -1;
   */
  @Override
  @Pure
  @Uninterruptible
  public int getDimensionality() {
    return -1;
  }

  /**
   * Resolution status.
   * @return true
   */
  @Override
  @Uninterruptible
  public boolean isResolved() {
    return true;
  }

  /**
   * Instantiation status.
   * @return true
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isInstantiated() {
    return true;
  }

  /**
   * Initialization status.
   * @return true
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isInitialized() {
    return true;
  }

  /**
   * Only intended to be used by the BootImageWriter
   */
  @Override
  public void markAsBootImageClass() {}

  /**
   * Is this class part of the virtual machine's boot image?
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isInBootImage() {
    return true;
  }

  /**
   * Get the offset in instances of this type assigned to the thin
   * lock word.  Offset.max() if instances of this type do not have thin lock
   * words.
   * @return Offset.max();
   */
  @Override
  @Pure
  @Uninterruptible
  public Offset getThinLockOffset() {
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return Offset.max();
  }

  /**
   * Whether or not this is an instance of RVMClass?
   * @return false
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isClassType() {
    return false;
  }

  /**
   * Whether or not this is an instance of RVMArray?
   * @return false
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isArrayType() {
    return false;
  }

  /**
   * Whether or not this is a primitive type
   * @return true
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isPrimitiveType() {
    return false;
  }

  /**
   * @return whether or not this is a reference (ie non-primitive) type.
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isReferenceType() {
    return false;
  }

  /**
   * @return whether or not this is an unboxed type
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isUnboxedType() {
    return true;
  }

  /**
   * Stack space requirement in words.
   */
  @Override
  @Pure
  @Uninterruptible
  public int getStackWords() {
    return stackWords;
  }

  /**
   * Space required in memory in bytes.
   */
  @Override
  @Pure
  @Uninterruptible
  public int getMemoryBytes() {
    return memoryBytes;
  }

  /**
   * Cause resolution to take place.
   */
  @Override
  @Pure
  public void resolve() {}

  @Override
  public void allBootImageTypesResolved() { }

  /**
   * Cause instantiation to take place.
   */
  @Override
  @Pure
  public void instantiate() {}

  /**
   * Cause initialization to take place.
   */
  @Override
  @Pure
  public void initialize() {}

  /**
   * Does this type override java.lang.Object.finalize()?
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean hasFinalizer() {
    return false;
  }

  /*
   * Primitives are not first class objects -
   * but the implementation of reflection is cleaner if
   * we pretend that they are and provide dummy implementations of
   * the following methods
   */

  /**
   * Static fields of this class/array type.
   * @return zero length array
   */
  @Override
  @Pure
  public RVMField[] getStaticFields() {
    return emptyVMField;
  }

  /**
   * Non-static fields of this class/array type
   * (composed with supertypes, if any).
   * @return zero length array
   */
  @Override
  @Pure
  public RVMField[] getInstanceFields() {
    return emptyVMField;
  }

  /**
   * Statically dispatched methods of this class/array type.
   * @return zero length array
   */
  @Override
  @Pure
  public RVMMethod[] getStaticMethods() {
    return emptyVMMethod;
  }

  /**
   * Virtually dispatched methods of this class/array type
   * (composed with supertypes, if any).
   * @return zero length array
   */
  @Override
  @Pure
  public RVMMethod[] getVirtualMethods() {
    return emptyVMMethod;
  }

  /**
   * Runtime type information for this class/array type.
   */
  @Override
  @Uninterruptible
  public TIB getTypeInformationBlock() {
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return null;
  }
}
