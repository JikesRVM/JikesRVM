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
package org.jikesrvm.classloader;

import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.ReflectionBase;
import org.jikesrvm.util.ImmutableEntryHashSetRVM;

import org.vmmagic.pragma.Uninterruptible;

/**
 * A class to represent the reference in a class file to some
 * type (class, primitive or array).
 * A type reference is uniquely defined by
 * <ul>
 * <li> an initiating class loader
 * <li> a type name
 * </ul>
 * Resolving a TypeReference to a RVMType can
 * be an expensive operation.  Therefore we canonicalize
 * TypeReference instances and cache the result of resolution.
 */
public final class TypeReference {
  /**
   * The initiating class loader
   */
  final ClassLoader classloader;

  /**
   * The type name. For example, the primitive type int is "I", the
   * class java.lang.String is "Ljava/lang/String;"
   */
  final Atom name;

  /**
   * The id of this type reference.
   */
  final int id;

  /**
   * The RVMType instance that this type reference resolves to.
   * Null if the reference has not yet been resolved.
   */
  private RVMType type;

  /**
   * Used to canonicalize TypeReferences
   */
  private static final ImmutableEntryHashSetRVM<TypeReference> dictionary =
    new ImmutableEntryHashSetRVM<TypeReference>();

  private static final ImmutableEntryHashSetRVM<ClassLoader> clDict = new ImmutableEntryHashSetRVM<ClassLoader>();
  /**
   * 2^LOG_ROW_SIZE is the number of elements per row
   */
  private static final int LOG_ROW_SIZE = 10;
  /**
   * Mask to ascertain row from id number
   */
  private static final int ROW_MASK = (1 << LOG_ROW_SIZE) - 1;
  /**
   * Dictionary of all TypeReference instances.
   */
  private static TypeReference[][] types = new TypeReference[3][1 << LOG_ROW_SIZE];

  /**
   * Used to assign Ids.  Id 0 is not used. Ids are compressed and
   * stored in the constant pool (See {@link RVMClass}).
   */
  private static int nextId = 1;

  public static final TypeReference Void = findOrCreate("V");
  public static final TypeReference Boolean = findOrCreate("Z");
  public static final TypeReference Byte = findOrCreate("B");
  public static final TypeReference Char = findOrCreate("C");
  public static final TypeReference Short = findOrCreate("S");
  public static final TypeReference Int = findOrCreate("I");
  public static final TypeReference Long = findOrCreate("J");
  public static final TypeReference Float = findOrCreate("F");
  public static final TypeReference Double = findOrCreate("D");

  public static final TypeReference BooleanArray = findOrCreate("[Z");
  public static final TypeReference ByteArray = findOrCreate("[B");
  public static final TypeReference CharArray = findOrCreate("[C");
  public static final TypeReference ShortArray = findOrCreate("[S");
  public static final TypeReference IntArray = findOrCreate("[I");
  public static final TypeReference LongArray = findOrCreate("[J");
  public static final TypeReference FloatArray = findOrCreate("[F");
  public static final TypeReference DoubleArray = findOrCreate("[D");

  public static final TypeReference Word = findOrCreate(org.vmmagic.unboxed.Word.class);
  public static final TypeReference Address = findOrCreate(org.vmmagic.unboxed.Address.class);
  public static final TypeReference ObjectReference = findOrCreate(org.vmmagic.unboxed.ObjectReference.class);
  public static final TypeReference Offset = findOrCreate(org.vmmagic.unboxed.Offset.class);
  public static final TypeReference Extent = findOrCreate(org.vmmagic.unboxed.Extent.class);
  public static final TypeReference Code = findOrCreate(org.jikesrvm.compilers.common.Code.class);
  public static final TypeReference WordArray = findOrCreate(org.vmmagic.unboxed.WordArray.class);
  public static final TypeReference AddressArray = findOrCreate(org.vmmagic.unboxed.AddressArray.class);
  public static final TypeReference ObjectReferenceArray =
      findOrCreate(org.vmmagic.unboxed.ObjectReferenceArray.class);
  public static final TypeReference OffsetArray = findOrCreate(org.vmmagic.unboxed.OffsetArray.class);
  public static final TypeReference ExtentArray = findOrCreate(org.vmmagic.unboxed.ExtentArray.class);
  public static final TypeReference CodeArray = findOrCreate(org.jikesrvm.compilers.common.CodeArray.class);
  public static final TypeReference Magic = findOrCreate(org.jikesrvm.runtime.Magic.class);
  public static final TypeReference SysCall = findOrCreate(org.vmmagic.pragma.SysCallNative.class);
  public static final TypeReference TIB = findOrCreate(org.jikesrvm.objectmodel.TIB.class);
  public static final TypeReference ITableArray = findOrCreate(org.jikesrvm.objectmodel.ITableArray.class);
  public static final TypeReference ITable = findOrCreate(org.jikesrvm.objectmodel.ITable.class);
  public static final TypeReference IMT = findOrCreate(org.jikesrvm.objectmodel.IMT.class);
  public static final TypeReference Thread = findOrCreate(org.jikesrvm.scheduler.RVMThread.class);
  public static final TypeReference FunctionTable = findOrCreate(org.jikesrvm.jni.FunctionTable.class);
  public static final TypeReference LinkageTripletTable = findOrCreate(org.jikesrvm.jni.LinkageTripletTable.class);

  public static final TypeReference JavaLangObject = findOrCreate(java.lang.Object.class);
  public static final TypeReference JavaLangClass = findOrCreate(java.lang.Class.class);
  public static final TypeReference JavaLangString = findOrCreate(java.lang.String.class);
  public static final TypeReference JavaLangCloneable = findOrCreate(java.lang.Cloneable.class);
  public static final TypeReference JavaIoSerializable = findOrCreate(java.io.Serializable.class);
  public static final TypeReference JavaLangRefReference = findOrCreate(java.lang.ref.Reference.class);
  public static final TypeReference JavaLangSystem = findOrCreate(java.lang.System.class);

  public static final TypeReference JavaLangObjectArray = findOrCreate(java.lang.Object[].class);

  public static final TypeReference JavaLangThrowable = findOrCreate(java.lang.Throwable.class);
  public static final TypeReference JavaLangError = findOrCreate(java.lang.Error.class);
  public static final TypeReference JavaLangNullPointerException =
      findOrCreate(java.lang.NullPointerException.class);
  public static final TypeReference JavaLangArrayIndexOutOfBoundsException =
      findOrCreate(java.lang.ArrayIndexOutOfBoundsException.class);
  public static final TypeReference JavaLangArithmeticException = findOrCreate(java.lang.ArithmeticException.class);
  public static final TypeReference JavaLangArrayStoreException = findOrCreate(java.lang.ArrayStoreException.class);
  public static final TypeReference JavaLangClassCastException = findOrCreate(java.lang.ClassCastException.class);
  public static final TypeReference JavaLangNegativeArraySizeException =
      findOrCreate(java.lang.NegativeArraySizeException.class);
  public static final TypeReference JavaLangIllegalMonitorStateException =
      findOrCreate(java.lang.IllegalMonitorStateException.class);

  public static final TypeReference Type = findOrCreate(org.jikesrvm.classloader.RVMType.class);
  public static final TypeReference Class = findOrCreate(org.jikesrvm.classloader.RVMClass.class);

  public static final TypeReference NativeBridge = findOrCreate(org.vmmagic.pragma.NativeBridge.class);
  public static final TypeReference DynamicBridge = findOrCreate(org.vmmagic.pragma.DynamicBridge.class);
  public static final TypeReference SaveVolatile = findOrCreate(org.vmmagic.pragma.SaveVolatile.class);
  public static final TypeReference Interruptible = findOrCreate(org.vmmagic.pragma.Interruptible.class);
  public static final TypeReference LogicallyUninterruptible =
      findOrCreate(org.vmmagic.pragma.LogicallyUninterruptible.class);
  public static final TypeReference Preemptible = findOrCreate(org.vmmagic.pragma.Preemptible.class);
  public static final TypeReference UninterruptibleNoWarn =
      findOrCreate(org.vmmagic.pragma.UninterruptibleNoWarn.class);
  public static final TypeReference UnpreemptibleNoWarn =
      findOrCreate(org.vmmagic.pragma.UnpreemptibleNoWarn.class);
  public static final TypeReference Uninterruptible = findOrCreate(org.vmmagic.pragma.Uninterruptible.class);
  public static final TypeReference NoCheckStore = findOrCreate(org.vmmagic.pragma.NoCheckStore.class);
  public static final TypeReference Unpreemptible = findOrCreate(org.vmmagic.pragma.Unpreemptible.class);
  public static final TypeReference SpecializedMethodInvoke = findOrCreate(org.vmmagic.pragma.SpecializedMethodInvoke.class);
  public static final TypeReference Untraced = findOrCreate(org.vmmagic.pragma.Untraced.class);
  public static final TypeReference NonMoving = findOrCreate(org.vmmagic.pragma.NonMoving.class);
  public static final TypeReference NonMovingAllocation = findOrCreate(org.vmmagic.pragma.NonMovingAllocation.class);
  public static final TypeReference BaselineNoRegisters = findOrCreate(org.vmmagic.pragma.BaselineNoRegisters.class);
  public static final TypeReference BaselineSaveLSRegisters = findOrCreate(org.vmmagic.pragma.BaselineSaveLSRegisters.class);
  public static final TypeReference ReferenceFieldsVary = findOrCreate(org.vmmagic.pragma.ReferenceFieldsVary.class);


  public static final TypeReference ReferenceMaps =
      findOrCreate(org.jikesrvm.compilers.baseline.ReferenceMaps.class);
  public static final TypeReference JNIFunctions = findOrCreate(org.jikesrvm.jni.JNIFunctions.class);

  public static final TypeReference RVMArray = findOrCreate(org.jikesrvm.classloader.RVMArray.class);
  /** Abstract base of reflective method invoker classes */
  static final TypeReference baseReflectionClass = TypeReference.findOrCreate(ReflectionBase.class);

  // Synthetic types used by the opt compiler
  public static final TypeReference NULL_TYPE =
      (VM.BuildForOptCompiler) ? findOrCreate("Lorg/jikesrvm/classloader/TypeReference$NULL;") : null;
  public static final TypeReference VALIDATION_TYPE =
      (VM.BuildForOptCompiler) ? findOrCreate("Lorg/jikesrvm/classloader/TypeReference$VALIDATION;") : null;

  public static final TypeReference ExceptionTable =
      (VM.BuildForOptCompiler) ? findOrCreate(org.jikesrvm.compilers.common.ExceptionTable.class) : null;

  public static final TypeReference OptimizationPlanner =
      (VM.BuildForAdaptiveSystem) ? findOrCreate(org.jikesrvm.compilers.opt.driver.OptimizationPlanner.class) : null;

  /**
   * Hash value based on name, used for canonical type dictionary
   */
  @Override
  public int hashCode() {
    return name.hashCode();
  }

  /**
   * Are two keys equivalent? Used for canonical type dictionary.
   * NB ignores id value
   */
  @Override
  public boolean equals(Object other) {
    if (other instanceof TypeReference) {
      TypeReference that = (TypeReference) other;
      return name == that.name && classloader.equals(that.classloader);
    } else {
      return false;
    }
  }

  /**
   * Find or create the canonical TypeReference instance for
   * the given pair.
   *
   * @param cl the classloader (defining/initiating depending on usage)
   * @param tn the name of the type
   * @return the canonical type reference
   * @throws IllegalArgumentException Needs to throw some kind of error in
   *  the case of a Atom that does not represent a type name.
   */
  public static synchronized TypeReference findOrCreate(ClassLoader cl, Atom tn) throws IllegalArgumentException {
    TypeDescriptorParsing.validateAsTypeDescriptor(tn);
    // Primitives, arrays of primitives, system classes and arrays of system
    // classes must use the bootstrap classloader.  Force that here so we don't
    // have to worry about it anywhere else in the VM.
    ClassLoader bootstrapCL = BootstrapClassLoader.getBootstrapClassLoader();
    if (cl == null) {
      cl = bootstrapCL;
    } else if (cl != bootstrapCL) {
      if (tn.isClassDescriptor()) {
        if (tn.isBootstrapClassDescriptor()) {
          cl = bootstrapCL;
        }
      } else if (tn.isArrayDescriptor()) {
        Atom innermostElementType = tn.parseForInnermostArrayElementDescriptor();
        if (innermostElementType.isClassDescriptor()) {
          if (innermostElementType.isBootstrapClassDescriptor()) {
            cl = bootstrapCL;
          }
        } else {
          cl = bootstrapCL;
        }
      } else {
        cl = bootstrapCL;
      }
    }
    return findOrCreateInternal(cl, tn);
  }

  /**
   * Shorthand for doing a find or create for a type reference that should
   * be created using the bootstrap classloader.
   * @return the canonical type reference
   * @param tn type descriptor
   */
  public static TypeReference findOrCreate(String tn) {
    return findOrCreate(BootstrapClassLoader.getBootstrapClassLoader(), Atom.findOrCreateAsciiAtom(tn));
  }

  /**
   * Convert a java.lang.Class into a type reference the slow way. For
   * use in boot image writing
   * @param klass java.lang.Class to convert to type reference
   * @return the canonical type reference
   */
  public static TypeReference findOrCreate(Class<?> klass) {
    if (VM.runningVM) {
      return java.lang.JikesRVMSupport.getTypeForClass(klass).getTypeRef();
    } else {
      String className = klass.getName();
      if (className.startsWith("[")) {
        // an array
        Atom classAtom = Atom.findOrCreateAsciiAtom(className.replace('.', '/'));
        return findOrCreate(BootstrapClassLoader.getBootstrapClassLoader(), classAtom);
      } else {
        // a class
        Atom classAtom;
        if (className.equals("int")) {
          return TypeReference.Int;
        } else if (className.equals("boolean")) {
          return TypeReference.Boolean;
        } else if (className.equals("byte")) {
          return TypeReference.Byte;
        } else if (className.equals("char")) {
          return TypeReference.Char;
        } else if (className.equals("double")) {
          return TypeReference.Double;
        } else if (className.equals("float")) {
          return TypeReference.Float;
        } else if (className.equals("long")) {
          return TypeReference.Long;
        } else if (className.equals("short")) {
          return TypeReference.Short;
        } else if (className.equals("void")) {
          return TypeReference.Void;
        } else {
          classAtom = Atom.findOrCreateAsciiAtom(className.replace('.', '/'));
        }
        Atom classDescriptor = classAtom.descriptorFromClassName();
        return findOrCreate(BootstrapClassLoader.getBootstrapClassLoader(), classDescriptor);
      }
    }
  }

  /**
   * Find or create the canonical TypeReference instance for
   * the given pair without type descriptor parsing.
   *
   * @param cl the classloader (defining/initiating depending on usage)
   * @param tn the name of the type
   * @return the canonical type reference
   */
  public static synchronized TypeReference findOrCreateInternal(ClassLoader cl, Atom tn) {
    // Next actually findOrCreate the type reference using the proper classloader.
    TypeReference key = new TypeReference(cl, tn, nextId);
    TypeReference val = dictionary.get(key);
    if (val == null) {
      // Create type reference
      val = key;
      nextId++; // id of val is the nextId, move it along
      int column = val.id >> LOG_ROW_SIZE;
      if (column == types.length) {
        // Grow the array of types if necessary
        TypeReference[][] tmp = new TypeReference[column + 1][];
        for (int i = 0; i < column; i++) {
          tmp[i] = types[i];
        }
        types = tmp;
        types[column] = new TypeReference[1 << LOG_ROW_SIZE];
      }
      types[column][val.id & ROW_MASK] = val;
      dictionary.add(val);
    }
    return val;
  }
  private static void canonicalizeCL(ClassLoader cl) {
    clDict.add(cl);
  }
  public static ImmutableEntryHashSetRVM<ClassLoader> getCLDict() {
    return clDict;
  }

  /**
   * @param cl the classloader
   * @param tn the type name
   * @param id the numeric identifier
   */
  private TypeReference(ClassLoader cl, Atom tn, int id) {
    canonicalizeCL(cl);
    classloader = cl;
    name = tn;
    this.id = id;
  }

  /**
   * Get the canonical type reference given its id. The unused id of 0 will return null.
   * @param id the type references id
   * @return the type reference
   */
  @Uninterruptible
  public static TypeReference getTypeRef(int id) {
    return types[id >> LOG_ROW_SIZE][id & ROW_MASK];
  }

  /**
   * @return the classloader component of this type reference
   */
  @Uninterruptible
  public ClassLoader getClassLoader() {
    return classloader;
  }

  /**
   * @return the type name component of this type reference
   */
  @Uninterruptible
  public Atom getName() {
    return name;
  }

  /**
   * @return the element type of for this array type
   */
  public TypeReference getArrayElementType() {
    if (VM.VerifyAssertions) VM._assert(isArrayType());

    if (isUnboxedArrayType()) {
      if (this == AddressArray) {
        return Address;
      } else if (this == ObjectReferenceArray) {
        return ObjectReference;
      } else if (this == WordArray) {
        return Word;
      } else if (this == OffsetArray) {
        return Offset;
      } else if (this == ExtentArray) {
        return Extent;
      } else if (this == CodeArray) {
        return Code;
      } else {
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED, "Unexpected case of Magic arrays!");
        return null;
      }
    } else {
      return findOrCreate(classloader, name.parseForArrayElementDescriptor());
    }
  }

  /**
   * @return array type corresponding to "this" array element type
   */
  public TypeReference getArrayTypeForElementType() {
    Atom arrayDescriptor = name.arrayDescriptorFromElementDescriptor();
    return findOrCreate(classloader, arrayDescriptor);
  }

  /**
   * @return the dimensionality of the type.
   * By convention, class types have dimensionality 0,
   * primitives -1, and arrays the number of [ in their descriptor.
   */
  public int getDimensionality() {
    if (isArrayType()) {
      TypeReference elem = getArrayElementType();
      if (elem.isArrayType()) {
        // NOTE: we must recur instead of attempting to parse
        //       the array descriptor for ['s so we correctly handle
        //       [AddressArray etc. which actually has dimensionality 2!
        return 1 + elem.getDimensionality();
      } else {
        return 1;
      }
    } else if (isUnboxedType()) {
      return -1;
    } else if (isClassType()) {
      return 0;
    } else {
      return -1;
    }
  }

  /**
   * @return the innermost element type reference for an array
   */
  public TypeReference getInnermostElementType() {
    TypeReference elem = getArrayElementType();
    if (elem.isArrayType()) {
      // NOTE: we must recur instead of attempting to parse
      //       the array descriptor for ['s so we correctly handle
      //       [AddressArray and similar evil VMMagic
      return elem.getInnermostElementType();
    } else {
      return elem;
    }
  }

  /**
   * @return {@code true} if 'this' refers to a class
   */
  @Uninterruptible
  public boolean isClassType() {
    return name.isClassDescriptor() && !(isUnboxedArrayType() || isUnboxedType());
  }

  /**
   * @return {@code true} if 'this' refers to an array
   */
  @Uninterruptible
  public boolean isArrayType() {
    return name.isArrayDescriptor() || isUnboxedArrayType();
  }

  /**
   * @return {@code true} if 'this' refers to a primitive type
   */
  @Uninterruptible
  public boolean isPrimitiveType() {
    return !(isArrayType() || isClassType());
  }

  /**
   * @return {@code true} if 'this' refers to a reference type
   */
  @Uninterruptible
  public boolean isReferenceType() {
    return !isPrimitiveType();
  }

  /**
   * @return {@code true} if 'this' refers to Word, Address, Offset or Extent
   */
  @Uninterruptible
  public boolean isWordLikeType() {
    return this == Word || this == Offset || this == Address || this == Extent;
  }

  /**
   * @return {@code true} if 'this' refers to Word
   */
  @Uninterruptible
  public boolean isWordType() {
    return this == Word;
  }

  /**
   * @return {@code true} if 'this' refers to Address
   */
  @Uninterruptible
  public boolean isAddressType() {
    return this == Address;
  }

  /**
   * @return {@code true} if 'this' refers to Offset
   */
  @Uninterruptible
  public boolean isOffsetType() {
    return this == Offset;
  }

  /**
   * @return {@code true} if 'this' refers to Extent
   */
  @Uninterruptible
  public boolean isExtentType() {
    return this == Extent;
  }

  /**
   * @return {@code true} if 'this' refers to an unboxed type.
   */
  @Uninterruptible
  public boolean isUnboxedType() {
    return isWordLikeType() || isCodeType();
  }

  /**
   * @return {@code true} if 'this' refers to Code
   */
  @Uninterruptible
  public boolean isCodeType() {
    return this == Code;
  }

  /**
   * @return {@code true} if  'this' refers to WordArray, AddressArray, OffsetArray or ExtentArray
   */
  @Uninterruptible
  public boolean isWordArrayType() {
    return this == WordArray ||
           this == OffsetArray ||
           this == AddressArray ||
           this == ExtentArray;
  }

  /**
   * @return {@code true} if 'this' refers to WordArray, AddressArray, OffsetArray or ExtentArray
   */
  @Uninterruptible
  public boolean isUnboxedArrayType() {
    return isWordArrayType() || isCodeArrayType() || this == ObjectReferenceArray;
  }

  /**
   * @return {@code true} if 'this' refers to a runtime table type
   */
  @Uninterruptible
  public boolean isRuntimeTable() {
    return this == IMT || this == TIB || this == ITable || this == ITableArray ||
           this == FunctionTable || this == LinkageTripletTable;
  }

  /**
   * @return {@code true} if 'this' refers to CodeArray
   */
  @Uninterruptible
  public boolean isCodeArrayType() {
    return this == CodeArray;
  }

  /**
   * @return {@code true} if 'this' refers to Magic
   */
  @Uninterruptible
  public boolean isMagicType() {
    return this == Magic || isUnboxedType() || isUnboxedArrayType() || this == ObjectReference || isRuntimeTable();
  }

  /**
   * @return number of java stack/local words that values of this type take
   */
  @Uninterruptible
  public int getStackWords() {
    if (isLoaded()) {
      // all primitive and magic types are resolved immediately
      return type.getStackWords();
    } else {
      // anything remaining must be a reference
      return 1;
    }
  }

  /**
   * @return number of bytes that values of this type take
   */
  @Uninterruptible
  public int getMemoryBytes() {
    if (isLoaded()) {
      // all primitive and magic types are resolved immediately
      return type.getMemoryBytes();
    } else {
      // anything remaining must be a reference
      return BYTES_IN_ADDRESS;
    }
  }

  /**
   * @return the id to use for this type
   */
  @Uninterruptible
  public int getId() {
    return id;
  }

  /**
   * @return {@code true} if this is the type reference for the void primitive type
   */
  @Uninterruptible
  public boolean isVoidType() {
    return this == Void;
  }

  /**
   * @return {@code true} if this is the type reference for the boolean primitive type
   */
  @Uninterruptible
  public boolean isBooleanType() {
    return this == Boolean;
  }

  /**
   * @return {@code true} if this is the type reference for the byte primitive type
   */
  @Uninterruptible
  public boolean isByteType() {
    return this == Byte;
  }

  /**
   * @return {@code true} if this is the type reference for the short primitive type
   */
  @Uninterruptible
  public boolean isShortType() {
    return this == Short;
  }

  /**
   * @return {@code true} if this is the type reference for the char primitive type
   */
  @Uninterruptible
  public boolean isCharType() {
    return this == Char;
  }

  /**
   * @return {@code true} if this is the type reference for the int primitive type
   */
  @Uninterruptible
  public boolean isIntType() {
    return this == Int;
  }

  /**
   * @return {@code true} if this is the type reference for the long primitive type
   */
  @Uninterruptible
  public boolean isLongType() {
    return this == Long;
  }

  /**
   * @return {@code true} if this is the type reference for the float primitive type
   */
  @Uninterruptible
  public boolean isFloatType() {
    return this == Float;
  }

  /**
   * @return {@code true} if this is the type reference for the double primitive type
   */
  @Uninterruptible
  public boolean isDoubleType() {
    return this == Double;
  }

  /**
   * @return {@code true} if this is the type for either the float primitive
   *  type or the double primitive type
   */
  @Uninterruptible
  public boolean isFloatingPointType() {
    return isFloatType() || isDoubleType();
  }

  /**
   * @return {@code true} if {@code this} is the type reference for an
   * int-like (1, 8, 16, or 32 bit integral) primitive type
   */
  @Uninterruptible
  public boolean isIntLikeType() {
    return isBooleanType() || isByteType() || isCharType() || isShortType() || isIntType();
  }

  /**
   * Do this and that definitely refer to the different types?
   *
   * @param that other type
   * @return {@code true} if this and the other type are definitely
   *  different, {@code false} if it's not known (e.g. because a
   *  type reference is not yet resolved)
   */
  public boolean definitelyDifferent(TypeReference that) {
    if (this == that) return false;
    if (name != that.name) return true;
    RVMType mine = peekType();
    RVMType theirs = that.peekType();
    if (mine == null || theirs == null) return false;
    return mine != theirs;
  }

  /**
   * Do {@code this} and that definitely refer to the same type?
   *
   * @param that other type
   * @return {@code true} if this and the other type are definitely
   *  the same, {@code false} if it's not known (e.g. because a
   *  type reference is not yet resolved)
   */
  public boolean definitelySame(TypeReference that) {
    if (VM.VerifyAssertions) VM._assert(that != null);
    if (this == that) return true;
    if (name != that.name) return false;
    RVMType mine = peekType();
    RVMType theirs = that.peekType();
    if (mine == null || theirs == null) return false;
    return mine == theirs;
  }

  /**
   * @return {@code true} if the type for type reference has been loaded.
   */
  @Uninterruptible
  public boolean isLoaded() {
    return type != null;
  }

  /**
   * @return {@code true} if the type for type reference has been loaded and
   *  it is resolved.
   */
  @Uninterruptible
  public boolean isResolved() {
    return isLoaded() && type.isResolved();
  }

  /**
   * @return the current value of resolvedType -- null if not yet resolved.
   */
  @Uninterruptible
  public RVMType peekType() {
    return type;
  }

  void setType(RVMType rt) {
    type = rt;
    if (type.isClassType()) {
      type.asClass().setResolvedMembers();
    }
  }

  /**
   * Force the resolution of the type reference. May cause class loading
   * if a required class file hasn't been loaded before.
   *
   * @return the RVMType instance that this references resolves to.
   *
   * @throws NoClassDefFoundError When it cannot resolve a class.
   *        we go to the trouble of converting the class loader's
   *        <code>ClassNotFoundException</code> into this error,
   *        since we need to be able to throw
   *        <code>NoClassDefFoundError</code> for classes
   *        that we're loading whose existence was compile-time checked.
   *
   * @throws IllegalArgumentException In case of a malformed class name
   *        (should never happen, since the right thing to do is probably to
   *        validate them as soon as we insert them into a TypeReference.
   *        This stinks. XXX)
   */
  public RVMType resolve() throws NoClassDefFoundError, IllegalArgumentException {
    /*
    * Lock the classloader instead of this to avoid conflicting locking order.
    * Suppose we locked this, then one thread could call resolve(), locking this,
    * call classloader.loadClass(), trying to lock the classloader. Meanwhile,
    * another thread could call loadClass(), locking the classloader, then
    * try to resolve() the TypeReference, resulting in a deadlock
    */
    synchronized (classloader) {
      return resolveInternal();
    }
  }

  private RVMType resolveInternal() throws NoClassDefFoundError, IllegalArgumentException {
    if (type != null) return type;
    if (isClassType()) {
      RVMType ans;
      if (VM.runningVM) {
        Class<?> klass;
        String myName = name.classNameFromDescriptor();
        try {
          klass = classloader.loadClass(myName);
        } catch (ClassNotFoundException cnf) {
          NoClassDefFoundError ncdfe =
              new NoClassDefFoundError("Could not find the class " + myName + ":\n\t" + cnf.getMessage());
          ncdfe.initCause(cnf); // in dubious taste, but helps us debug Jikes RVM
          throw ncdfe;
        }

        ans = java.lang.JikesRVMSupport.getTypeForClass(klass);
      } else {
        // Use a special purpose backdoor to avoid creating java.lang.Class
        // objects when not running the VM (we get host JDK Class objects
        // and that just doesn't work).
        ans = ((BootstrapClassLoader) classloader).loadVMClass(name.classNameFromDescriptor());
      }
      if (VM.VerifyAssertions) {
        VM._assert(type == null || type == ans);
      }
      setType(ans);
    } else if (isArrayType()) {
      if (isUnboxedArrayType()) {
        // Ensure that we only create one RVMArray object for each pair of
        // names for this type.
        // Do this by resolving AddressArray to [Address
        setType(getArrayElementType().getArrayTypeForElementType().resolve());
      } else {
        RVMType elementType = getArrayElementType().resolve();
        if (elementType.getClassLoader() != classloader) {
          // We aren't the canonical type reference because the element type
          // was loaded using a different classloader.
          // Find the canonical type reference and ask it to resolve itself.
          TypeReference canonical = TypeReference.findOrCreate(elementType.getClassLoader(), name);
          setType(canonical.resolve());
        } else {
          setType(new RVMArray(this, elementType));
        }
      }
    } else {
      if (isUnboxedType()) {
        setType(UnboxedType.createUnboxedType(this));
      } else {
        setType(Primitive.createPrimitive(this));
      }
    }
    return type;
  }

  @Override
  public String toString() {
    return "< " + classloader + ", " + name + " >";
  }
}
