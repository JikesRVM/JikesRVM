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
 * Description of a java "primitive" type (int, float, etc.)
 *
 * <p> This description is not read from a ".class" file, but rather
 * is manufactured by the vm before execution begins.
 *
 * <p> Note that instances of primitives are not objects:
 * <ul>
 * <li> they are never heap allocated in the virtual machine
 * <li> they have no virtual methods
 * <li> they appear only in the virtual machine's stack, in its registers,
 *   or in fields/elements of class/array instances.
 * </ul>
 *
 * @see RVMType
 * @see RVMClass
 * @see RVMArray
 */
@NonMoving
public final class Primitive extends RVMType implements Constants, ClassLoaderConstants {
  /**
   * The pretty (external) name for this primitive.
   * For example, for a long the name is 'long'
   * and the descriptor is 'J'
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
  private Primitive(TypeReference tr, Class<?> classForType, Atom name, int stackWords, int memoryBytes) {
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
   * Create an instance of a {@link Primitive}
   * @param tr   The canonical type reference for this primitive
   */
  static Primitive createPrimitive(TypeReference tr) {
    Atom name;
    int stackWords;
    int memoryBytes;
    Class<?> classForType;
    switch (tr.getName().parseForTypeCode()) {
      case VoidTypeCode:
        stackWords = 0;
        memoryBytes = 0;
        name = Atom.findOrCreateAsciiAtom("void");
        classForType = Void.TYPE;
        break;
      case BooleanTypeCode:
        stackWords = 1;
        memoryBytes = BYTES_IN_BOOLEAN;
        name = Atom.findOrCreateAsciiAtom("boolean");
        classForType = Boolean.TYPE;
        break;
      case ByteTypeCode:
        stackWords = 1;
        memoryBytes = BYTES_IN_BYTE;
        name = Atom.findOrCreateAsciiAtom("byte");
        classForType = Byte.TYPE;
        break;
      case CharTypeCode:
        stackWords = 1;
        memoryBytes = BYTES_IN_CHAR;
        name = Atom.findOrCreateAsciiAtom("char");
        classForType = Character.TYPE;
        break;
      case ShortTypeCode:
        stackWords = 1;
        memoryBytes = BYTES_IN_SHORT;
        name = Atom.findOrCreateAsciiAtom("short");
        classForType = Short.TYPE;
        break;
      case IntTypeCode:
        stackWords = 1;
        memoryBytes = BYTES_IN_INT;
        name = Atom.findOrCreateAsciiAtom("int");
        classForType = Integer.TYPE;
        break;
      case LongTypeCode:
        stackWords = 2;
        memoryBytes = BYTES_IN_LONG;
        name = Atom.findOrCreateAsciiAtom("long");
        classForType = Long.TYPE;
        break;
      case FloatTypeCode:
        stackWords = 1;
        memoryBytes = BYTES_IN_FLOAT;
        name = Atom.findOrCreateAsciiAtom("float");
        classForType = Float.TYPE;
        break;
      case DoubleTypeCode:
        stackWords = 2;
        memoryBytes = BYTES_IN_DOUBLE;
        name = Atom.findOrCreateAsciiAtom("double");
        classForType = Double.TYPE;
        break;
      default:
        stackWords = 1;
        name = tr.getName();
        if (tr == TypeReference.Address ||
            tr == TypeReference.Word ||
            tr == TypeReference.Offset ||
            tr == TypeReference.Extent) {
          memoryBytes = BYTES_IN_ADDRESS;
        } else if (tr == TypeReference.Code) {
          memoryBytes = VM.BuildForIA32 ? BYTES_IN_BYTE : BYTES_IN_INT;
        } else {
          throw new Error("Unknown primitive type " + tr.getName());
        }
        try {
          classForType = Class.forName(name.classNameFromDescriptor());
        } catch (Exception e) {
          throw new Error("Error getting java.lang.Class wrapper for type " + name.classNameFromDescriptor());
        }
    }
    return new Primitive(tr, classForType, name, stackWords, memoryBytes);
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
    return true;
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
