/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002, 2004
 */
package org.jikesrvm.classloader;

import org.jikesrvm.VM;
import org.jikesrvm.VM_Constants;
import org.vmmagic.pragma.SynchronizedObject;
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
 * @see VM_Type
 * @see VM_Class
 * @see VM_Array
 */
@SynchronizedObject
public final class VM_Primitive extends VM_Type implements VM_Constants,
                                                           VM_ClassLoaderConstants {
  /**
   * The pretty (external) name for this primitive.
   * For example, for a long the name is 'long'
   * and the descriptor is 'J'
   */
  private final VM_Atom name;

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
  private VM_Primitive(VM_TypeReference tr, Class<?> classForType,
                       VM_Atom name, int stackWords, int memoryBytes) {
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
   * Create an instance of a {@link VM_Primitive}
   * @param tr   The canonical type reference for this primitive
   */
  static VM_Primitive createPrimitive(VM_TypeReference tr) {
    VM_Atom name;
    int stackWords;
    int memoryBytes;
    Class<?> classForType;
    switch (tr.getName().parseForTypeCode()) {
      case VoidTypeCode:
        stackWords = 0;
        memoryBytes = 0;
        name = VM_Atom.findOrCreateAsciiAtom("void");
        classForType = Void.TYPE;
        break;
      case BooleanTypeCode:
        stackWords = 1;
        memoryBytes = BYTES_IN_BOOLEAN;
        name = VM_Atom.findOrCreateAsciiAtom("boolean");
        classForType = Boolean.TYPE;
        break;
      case ByteTypeCode:
        stackWords = 1;
        memoryBytes = BYTES_IN_BYTE;
        name = VM_Atom.findOrCreateAsciiAtom("byte");
        classForType = Byte.TYPE;
        break;
      case CharTypeCode:
        stackWords = 1;
        memoryBytes = BYTES_IN_CHAR;
        name = VM_Atom.findOrCreateAsciiAtom("char");
        classForType = Character.TYPE;
        break;
      case ShortTypeCode:
        stackWords = 1;
        memoryBytes = BYTES_IN_SHORT;
        name = VM_Atom.findOrCreateAsciiAtom("short");
        classForType = Short.TYPE;
        break;
      case IntTypeCode:
        stackWords = 1;
        memoryBytes = BYTES_IN_INT;
        name = VM_Atom.findOrCreateAsciiAtom("int");
        classForType = Integer.TYPE;
        break;
      case LongTypeCode:
        stackWords = 2;
        memoryBytes = BYTES_IN_LONG;
        name = VM_Atom.findOrCreateAsciiAtom("long");
        classForType = Long.TYPE;
        break;
      case FloatTypeCode:
        stackWords = 1;
        memoryBytes = BYTES_IN_FLOAT;
        name = VM_Atom.findOrCreateAsciiAtom("float");
        classForType = Float.TYPE;
        break;
      case DoubleTypeCode:
        stackWords = 2;
        memoryBytes = BYTES_IN_DOUBLE;
        name = VM_Atom.findOrCreateAsciiAtom("double");
        classForType = Double.TYPE;
        break;
      default:
        if (tr == VM_TypeReference.Address ||
            tr == VM_TypeReference.Word ||
            tr == VM_TypeReference.ObjectReference ||
            tr == VM_TypeReference.Offset ||
            tr == VM_TypeReference.Extent) {
          stackWords = 1;
          memoryBytes = BYTES_IN_ADDRESS;
          name = tr.getName();
          classForType = null;
        } else if (tr == VM_TypeReference.Code) {
          stackWords = 1;
          memoryBytes = VM.BuildForIA32 ? BYTES_IN_BYTE : BYTES_IN_INT;
          name = tr.getName();
          classForType = null;
        } else {
          throw new Error("Unknown primitive type " + tr.getName());
        }
    }
    return new VM_Primitive(tr, classForType, name, stackWords, memoryBytes);
  }

  /**
   * get number of superclasses to Object
   * @return 0
   */
  @Uninterruptible
  public int getTypeDepth() {
    return 0;
  }

  /**
   * Reference Count GC: Is a reference of this type contained in
   * another object inherently acyclic (without cycles) ?
   * @return true
   */
  @Uninterruptible
  public boolean isAcyclicReference() {
    return true;
  }

  /**
   * Number of [ in descriptor for arrays; -1 for primitives; 0 for
   * classes
   * @return -1;
   */
  @Uninterruptible
  public int getDimensionality() {
    return -1;
  }

  /**
   * Resolution status.
   * @return true
   */
  @Uninterruptible
  public boolean isResolved() {
    return true;
  }

  /**
   * Instantiation status.
   * @return true
   */
  @Uninterruptible
  public boolean isInstantiated() {
    return true;
  }

  /**
   * Initialization status.
   * @return true
   */
  @Uninterruptible
  public boolean isInitialized() {
    return true;
  }

  /**
   * Only intended to be used by the BootImageWriter
   */
  public void markAsBootImageClass() {}

  /**
   * Is this class part of the virtual machine's boot image?
   */
  @Uninterruptible
  public boolean isInBootImage() {
    return true;
  }

  /**
   * Get the offset in instances of this type assigned to the thin
   * lock word.  -1 if instances of this type do not have thin lock
   * words.
   * @return -1
   */
  @Uninterruptible
  public Offset getThinLockOffset() {
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return Offset.fromIntSignExtend(-1);
  }

  /**
   * Set the thin lock offset for instances of this type
   */
  public void setThinLockOffset(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
  }

  /**
   * Whether or not this is an instance of VM_Class?
   * @return false
   */
  @Uninterruptible
  public boolean isClassType() {
    return false;
  }

  /**
   * Whether or not this is an instance of VM_Array?
   * @return false
   */
  @Uninterruptible
  public boolean isArrayType() {
    return false;
  }

  /**
   * Whether or not this is a primitive type
   * @return true
   */
  @Uninterruptible
  public boolean isPrimitiveType() {
    return true;
  }

  /**
   * @return whether or not this is a reference (ie non-primitive) type.
   */
  @Uninterruptible
  public boolean isReferenceType() {
    return false;
  }

  /**
   * Stack space requirement in words.
   */
  @Uninterruptible
  public int getStackWords() {
    return stackWords;
  }

  /**
   * Space required in memory in bytes.
   */
  @Uninterruptible
  public int getMemoryBytes() {
    return memoryBytes;
  }

  /**
   * Cause resolution to take place.
   */
  public void resolve() {}

  /**
   * Cause instantiation to take place.
   */
  public void instantiate() {}

  /**
   * Cause initialization to take place.
   */
  public void initialize() {}

  /**
   * Does this type override java.lang.Object.finalize()?
   */
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
  public VM_Field[] getStaticFields() {
    return emptyVMField;
  }

  /**
   * Non-static fields of this class/array type
   * (composed with supertypes, if any).
   * @return zero length array
   */
  public VM_Field[] getInstanceFields() {
    return emptyVMField;
  }

  /**
   * Statically dispatched methods of this class/array type.
   * @return zero length array
   */
  public VM_Method[] getStaticMethods() {
    return emptyVMMethod;
  }

  /**
   * Virtually dispatched methods of this class/array type
   * (composed with supertypes, if any).
   * @return zero length array
   */
  public VM_Method[] getVirtualMethods() {
    return emptyVMMethod;
  }

  /**
   * Runtime type information for this class/array type.
   */
  @Uninterruptible
  public Object[] getTypeInformationBlock() {
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return null;
  }

  /**
   * Does this slot in the TIB hold a TIB entry?
   */
  public boolean isTIBSlotTIB(int slot) {
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return false;
  }

  /**
   * Does this slot in the TIB hold code?
   */
  public boolean isTIBSlotCode(int slot) {
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return false;
  }

  /**
   * Record the type information the memory manager holds about this
   * type. This method should never be called for a primitive type.
   * @param mmt the type to record
   */
  public void setMMType(Object mmt) {
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
  }

  /**
   * Return the type information the memory manager previously
   * recorded about this type. This method should never be called for
   * a primitive type.
   */
  @Uninterruptible
  public Object getMMType() {
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return null;
  }

}
