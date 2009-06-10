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

import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.runtime.Statics;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * A description of a java type.
 *
 * This class is the base of the java type system.
 * To the three kinds of java objects
 * (class-instances, array-instances, primitive-instances)
 * there are three corresponding
 * subclasses of RVMType: RVMClass, RVMArray, Primitive.
 * <p>
 * A RVMClass is constructed in four phases:
 * <ul>
 * <li> A "load" phase reads the ".class" file but does not attempt to
 *      examine any of the symbolic references present there. This is done
 *      by the RVMClass constructor as a result of a TypeReference being
 *      resolved.
 *
 * <li> A "resolve" phase follows symbolic references as needed to discover
 *   ancestry, to measure field sizes, and to allocate space in the jtoc
 *   for the class's static fields and methods.
 *
 * <li>  An "instantiate" phase initializes and
 * installs the type information block and static methods.
 *
 * <li> An "initialize" phase runs the class's static initializer.
 * </ul>
 *
 * RVMArray's are constructed in a similar fashion.
 *
 * Primitive's are constructed ab initio.
 * Their "resolution", "instantiation", and "initialization" phases
 * are no-ops.
 */
@NonMoving
public abstract class RVMType extends AnnotatedElement
    implements ClassLoaderConstants, SizeConstants, Constants {

  /** Next space in the the type array */
  private static int nextId = 1;

  /**
   * 2^LOG_ROW_SIZE is the number of elements per row
   */
  private static final int LOG_ROW_SIZE = 10;
  /**
   * Mask to ascertain row from id number
   */
  private static final int ROW_MASK = (1 << LOG_ROW_SIZE)-1;
  /** All types */
  private static RVMType[][] types = new RVMType[1][1 << LOG_ROW_SIZE];

  /** Canonical representation of no fields */
  protected static final RVMField[] emptyVMField = new RVMField[0];
  /** Canonical representation of no methods */
  protected static final RVMMethod[] emptyVMMethod = new RVMMethod[0];
  /** Canonical representation of no VM classes */
  protected static final RVMClass[] emptyVMClass = new RVMClass[0];

  /*
   * We hold on to a number of special types here for easy access.
   */
  public static final Primitive VoidType;
  public static final Primitive BooleanType;
  public static final Primitive ByteType;
  public static final Primitive ShortType;
  public static final Primitive IntType;
  public static final Primitive LongType;
  public static final Primitive FloatType;
  public static final Primitive DoubleType;
  public static final Primitive CharType;
  public static final RVMClass JavaLangObjectType;
  public static final RVMArray JavaLangObjectArrayType;
  public static final RVMClass JavaLangClassType;
  public static final RVMClass JavaLangThrowableType;
  public static final RVMClass JavaLangStringType;
  public static final RVMClass JavaLangCloneableType;
  public static final RVMClass JavaIoSerializableType;
  public static final RVMClass JavaLangRefReferenceType;
  public static final RVMField JavaLangRefReferenceReferenceField;
  public static final RVMClass MagicType;
  public static final UnboxedType WordType;
  public static final RVMArray WordArrayType;
  public static final UnboxedType AddressType;
  public static final RVMArray AddressArrayType;
  public static final RVMClass ObjectReferenceType;
  public static final RVMArray ObjectReferenceArrayType;
  public static final UnboxedType OffsetType;
  public static final RVMArray OffsetArrayType;
  public static final UnboxedType ExtentType;
  public static final RVMArray ExtentArrayType;
  public static final UnboxedType CodeType;
  public static final RVMArray CodeArrayType;
  public static final RVMClass TIBType;
  public static final RVMClass ITableType;
  public static final RVMClass ITableArrayType;
  public static final RVMClass IMTType;
  public static final RVMClass FunctionTableType;
  public static final RVMClass LinkageTripletTableType;

  static {
    // Primitive types
    VoidType = TypeReference.Void.resolve().asPrimitive();
    BooleanType = TypeReference.Boolean.resolve().asPrimitive();
    ByteType = TypeReference.Byte.resolve().asPrimitive();
    ShortType = TypeReference.Short.resolve().asPrimitive();
    IntType = TypeReference.Int.resolve().asPrimitive();
    LongType = TypeReference.Long.resolve().asPrimitive();
    FloatType = TypeReference.Float.resolve().asPrimitive();
    DoubleType = TypeReference.Double.resolve().asPrimitive();
    CharType = TypeReference.Char.resolve().asPrimitive();
    // Jikes RVM primitives
    AddressType = TypeReference.Address.resolve().asUnboxedType();
    WordType = TypeReference.Word.resolve().asUnboxedType();
    OffsetType = TypeReference.Offset.resolve().asUnboxedType();
    ExtentType = TypeReference.Extent.resolve().asUnboxedType();
    CodeType = TypeReference.Code.resolve().asUnboxedType();
    ObjectReferenceType = TypeReference.ObjectReference.resolve().asClass();
    // Jikes RVM classes
    MagicType = TypeReference.Magic.resolve().asClass();
    // Array types
    CodeArrayType = TypeReference.CodeArray.resolve().asArray();
    WordArrayType = TypeReference.WordArray.resolve().asArray();
    AddressArrayType = TypeReference.AddressArray.resolve().asArray();
    ObjectReferenceArrayType = TypeReference.ObjectReferenceArray.resolve().asArray();
    OffsetArrayType = TypeReference.OffsetArray.resolve().asArray();
    ExtentArrayType = TypeReference.ExtentArray.resolve().asArray();
    // Runtime Tables
    TIBType = TypeReference.TIB.resolve().asClass();
    ITableType = TypeReference.ITable.resolve().asClass();
    ITableArrayType = TypeReference.ITableArray.resolve().asClass();
    IMTType = TypeReference.IMT.resolve().asClass();
    FunctionTableType = TypeReference.FunctionTable.resolve().asClass();
    LinkageTripletTableType = TypeReference.LinkageTripletTable.resolve().asClass();
    // Java clases
    JavaLangObjectType = TypeReference.JavaLangObject.resolve().asClass();
    JavaLangObjectArrayType = TypeReference.JavaLangObjectArray.resolve().asArray();
    JavaLangClassType = TypeReference.JavaLangClass.resolve().asClass();
    JavaLangThrowableType = TypeReference.JavaLangThrowable.resolve().asClass();
    JavaLangStringType = TypeReference.JavaLangString.resolve().asClass();
    JavaLangCloneableType = TypeReference.JavaLangCloneable.resolve().asClass();
    JavaIoSerializableType = TypeReference.JavaIoSerializable.resolve().asClass();
    JavaLangRefReferenceType = TypeReference.JavaLangRefReference.resolve().asClass();
    JavaLangRefReferenceReferenceField = JavaLangRefReferenceType.findDeclaredField(Atom.findAsciiAtom("_referent"));
  }

  /**
   * Canonical type reference for this RVMType instance
   */
  protected final TypeReference typeRef;

  /**
   * Type id -- used to index into typechecking datastructures
   */
  @Entrypoint
  protected final int id;

  /**
   * index of jtoc slot that has type information block for this RVMType
   */
  protected final int tibOffset;

  /**
   * instance of java.lang.Class corresponding to this type
   */
  private final Class<?> classForType;

  /**
   * Number of [ in descriptor for arrays; -1 for primitives; 0 for
   * classes. NB this field must appear in all Types for fast type
   * checks (See {@link org.jikesrvm.compilers.opt.hir2lir.DynamicTypeCheckExpansion}).
   */
  @Entrypoint
  protected final int dimension;
  /**
   * Number of superclasses to Object. Known immediately for
   * primitives and arrays, but only after resolving for classes. NB
   * this field must appear in all Types for fast object array
   * store checks (See {@link org.jikesrvm.compilers.opt.hir2lir.DynamicTypeCheckExpansion}).
   */
  @Entrypoint
  protected int depth;
  /**
   * cached RVMArray that corresponds to arrays of this type.
   * (null ->> not created yet).
   */
  private RVMArray cachedElementType;

  /**
   * The superclass ids for this type.
   */
  protected short[] superclassIds;

  /**
   * The interface implementation array for this type.
   */
  protected int[] doesImplement;

  /**
   * Create an instance of a {@link RVMType}
   * @param typeRef The canonical type reference for this type.
   * @param classForType The java.lang.Class representation
   * @param dimension The dimensionality
   * @param annotations array of runtime visible annotations
   */
  protected RVMType(TypeReference typeRef, Class<?> classForType, int dimension, RVMAnnotation[] annotations) {
    super(annotations);
    this.typeRef = typeRef;
    this.tibOffset = Statics.allocateReferenceSlot(false).toInt();
    this.id = nextId(this);
    this.classForType = classForType;
    this.dimension = dimension;

    /* install partial type information block (no method dispatch table) for use in type checking. */
    TIB tib = MemoryManager.newTIB(0);
    tib.setType(this);
    Statics.setSlotContents(getTibOffset(), tib);
  }

  /**
   * Create an instance of a {@link RVMType}
   * @param typeRef The canonical type reference for this type.
   * @param dimension The dimensionality
   * @param annotations array of runtime visible annotations
   */
  protected RVMType(TypeReference typeRef, int dimension, RVMAnnotation[] annotations) {
    super(annotations);
    this.typeRef = typeRef;
    this.tibOffset = Statics.allocateReferenceSlot(false).toInt();
    this.id = nextId(this);
    this.classForType = createClassForType(this, typeRef);
    this.dimension = dimension;

    /* install partial type information block (no method dispatch table) for use in type checking. */
    TIB tib = MemoryManager.newTIB(0);
    tib.setType(this);
    Statics.setSlotContents(getTibOffset(), tib);
  }

  /**
   * Canonical type reference for this type.
   */
  @Uninterruptible
  public final TypeReference getTypeRef() {
    return typeRef;
  }

  /**
   * Get the numeric identifier for this type
   */
  @Uninterruptible
  public final int getId() {
    return id;
  }

  /**
   * Instance of java.lang.Class corresponding to this type.
   * This is commonly used for reflection.
   */
  public final Class<?> getClassForType() {
    if (VM.runningVM) {
      // Resolve the class so that we don't need to resolve it
      // in reflection code
      if (!isResolved()) {
        resolve();
      }
      return classForType;
    } else {
      return createClassForType(this, getTypeRef());
    }
  }

  /**
   * Instance of java.lang.Class corresponding to this type.
   * This is commonly used for reflection.
   */
  @Uninterruptible
  public final Class<?> getResolvedClassForType() {
    // Resolve the class so that we don't need to resolve it
    // in reflection code
    if (VM.VerifyAssertions) VM._assert(VM.runningVM && isResolved());
    return classForType;
  }

  /**
   * Get offset of tib slot from start of jtoc, in bytes.
   */
  @Uninterruptible
  public final Offset getTibOffset() {
    return Offset.fromIntSignExtend(tibOffset);
  }

  /**
   * Get the class loader for this type
   */
  @Uninterruptible
  public final ClassLoader getClassLoader() {
    return typeRef.getClassLoader();
  }

  /**
   * Should assertions be enabled on this type?
   * @return false
   */
  public boolean getDesiredAssertionStatus() {
    return false;
  }

  /**
   * Descriptor for this type.
   * For a class, something like "Ljava/lang/String;".
   * For an array, something like "[I" or "[Ljava/lang/String;".
   * For a primitive, something like "I".
   */
  @Uninterruptible
  public final Atom getDescriptor() {
    return typeRef.getName();
  }

  /**
   * Define hashCode(), to allow use of consistent hash codes during
   * bootImage writing and run-time
   */
  @Override
  public final int hashCode() {
    return typeRef.hashCode();
  }

  /**
   * get number of superclasses to Object
   *   0 java.lang.Object, Primitive, and Classes that are interfaces
   *   1 for RVMArrays and classes that extend Object directly
   */
  @Uninterruptible
  public abstract int getTypeDepth();

  /**
   * Reference Count GC: Is a reference of this type contained in
   * another object inherently acyclic (without cycles) ?
   */
  @Uninterruptible
  public abstract boolean isAcyclicReference();

  /**
   * Number of [ in descriptor for arrays; -1 for primitives; 0 for classes
   */
  @Uninterruptible
  public abstract int getDimensionality();

  /**
   * @return this cast to a RVMClass
   */
  @Uninterruptible
  public final RVMClass asClass() {
    return (RVMClass) this;
  }

  /**
   * @return this cast to a RVMArray
   */
  @Uninterruptible
  public final RVMArray asArray() {
    return (RVMArray) this;
  }

  /**
   * @return this cast to a Primitive
   */
  @Uninterruptible
  public final Primitive asPrimitive() {
    return (Primitive) this;
  }

  /**
   * @return this cast to a UnboxedType
   */
  @Uninterruptible
  public final UnboxedType asUnboxedType() {
    return (UnboxedType) this;
  }
  // Convenience methods.
  //
  /** @return is this type void? */
  @Uninterruptible
  public final boolean isVoidType() {
    return this == VoidType;
  }

  /** @return is this type the primitive boolean? */
  @Uninterruptible
  public final boolean isBooleanType() {
    return this == BooleanType;
  }

  /** @return is this type the primitive byte? */
  @Uninterruptible
  public final boolean isByteType() {
    return this == ByteType;
  }

  /** @return is this type the primitive short? */
  @Uninterruptible
  public final boolean isShortType() {
    return this == ShortType;
  }

  /** @return is this type the primitive int? */
  @Uninterruptible
  public final boolean isIntType() {
    return this == IntType;
  }

  /** @return is this type the primitive long? */
  @Uninterruptible
  public final boolean isLongType() {
    return this == LongType;
  }

  /** @return is this type the primitive float? */
  @Uninterruptible
  public final boolean isFloatType() {
    return this == FloatType;
  }

  /** @return is this type the primitive double? */
  @Uninterruptible
  public final boolean isDoubleType() {
    return this == DoubleType;
  }

  /** @return is this type the primitive char? */
  @Uninterruptible
  public final boolean isCharType() {
    return this == CharType;
  }

  /**
   * @return is this type the primitive int like? ie is it held as an
   * int on the JVM stack
   */
  @Uninterruptible
  public final boolean isIntLikeType() {
    return isBooleanType() || isByteType() || isShortType() || isIntType() || isCharType();
  }

  /** @return is this type the class Object? */
  @Uninterruptible
  public final boolean isJavaLangObjectType() {
    return this == JavaLangObjectType;
  }

  /** @return is this type the class Throwable? */
  @Uninterruptible
  public final boolean isJavaLangThrowableType() {
    return this == JavaLangThrowableType;
  }

  /** @return is this type the class String? */
  @Uninterruptible
  public final boolean isJavaLangStringType() {
    return this == JavaLangStringType;
  }

  /**
   * Get array type corresponding to "this" array element type.
   */
  public final RVMArray getArrayTypeForElementType() {
    if (cachedElementType == null) {
      TypeReference tr = typeRef.getArrayTypeForElementType();
      cachedElementType = tr.resolve().asArray();
      /*  Can't fail to resolve the type, because the element type already
          exists (it is 'this') and the VM creates array types itself without
          any possibility of error if the element type is already loaded. */
    }
    return cachedElementType;
  }

  /**
   * get superclass id vector (@see DynamicTypeCheck)
   */
  @Uninterruptible
  public final short[] getSuperclassIds() {
    return superclassIds;
  }

  /**
   * get doesImplement vector (@see DynamicTypeCheck)
   */
  @Uninterruptible
  public final int[] getDoesImplement() {
    return doesImplement;
  }

  /**
   * Allocate entry in types array and add it (NB resize array if it's
   * not long enough)
   */
  private static synchronized int nextId(RVMType it) {
    int ans = nextId++;
    int column = ans >> LOG_ROW_SIZE;
    if (column >= types.length) {
      RVMType[][] newTypes = new RVMType[column+1][];
      for (int i = 0; i < types.length; i++) {
        newTypes[i] = types[i];
      }
      newTypes[column] = new RVMType[1<<LOG_ROW_SIZE];
      types = newTypes;
    }
    types[ans >> LOG_ROW_SIZE][ans & ROW_MASK] = it;
    return ans;
  }

  /**
   * How many types have been created?
   * Only intended to be used by the bootimage writer!
   */
  @Uninterruptible
  public static int numTypes() {
    return nextId - 1;
  }

  /**
   * Get the type for the given id
   */
  @Uninterruptible
  public static RVMType getType(int id) {
    return types[id >> LOG_ROW_SIZE][id & ROW_MASK];
  }

  /**
   * Utility to create a java.lang.Class for the given type using the
   * given type reference
   */
  protected static Class<?> createClassForType(RVMType type, TypeReference typeRef) {
    if (VM.runningVM) {
      return java.lang.JikesRVMSupport.createClass(type);
    } else {
      Exception x;
      try {
        Atom className = typeRef.getName();
        if (className.isAnnotationClass()) {
          return Class.forName(className.annotationClassToAnnotationInterface(), false, RVMType.class.getClassLoader());
        } else if (className.isClassDescriptor()) {
          return Class.forName(className.classNameFromDescriptor(), false, RVMType.class.getClassLoader());
        } else {
          String classNameString = className.toString();
          if (classNameString.equals("V")) {
            return void.class;
          } else if(classNameString.equals("I")){
            return int.class;
          } else if(classNameString.equals("J")){
            return long.class;
          } else if(classNameString.equals("F")){
            return float.class;
          } else if(classNameString.equals("D")){
            return double.class;
          } else if(classNameString.equals("C")){
            return char.class;
          } else if(classNameString.equals("S")){
            return short.class;
          } else if(classNameString.equals("Z")){
            return boolean.class;
          } else if(classNameString.equals("B")){
            return byte.class;
          } else {
            return Class.forName(classNameString.replace('/', '.'), false, RVMType.class.getClassLoader());
          }
        }
      } catch (ClassNotFoundException e) { x = e; } catch (SecurityException e) { x = e; }
      if (typeRef.isArrayType() && typeRef.getArrayElementType().isCodeType()) {
        // fix up class for code array
        return CodeArray.class;
      } else if (!VM.runningVM) {
        // Give a warning as this is probably a protection issue for
        // the tool and JVM
        VM.sysWriteln("Warning unable to find Java class for RVM type");
        x.printStackTrace();
        return null;
      } else {
        throw new Error("Unable to find Java class for RVM type", x);
      }
    }
  }

  /**
   * Find specified virtual method description.
   * @param memberName   method name - something like "foo"
   * @param memberDescriptor method descriptor - something like "I" or "()I"
   * @return method description (null --> not found)
   */
  public final RVMMethod findVirtualMethod(Atom memberName, Atom memberDescriptor) {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    RVMMethod[] methods = getVirtualMethods();
    for (int i = 0, n = methods.length; i < n; ++i) {
      RVMMethod method = methods[i];
      if (method.getName() == memberName && method.getDescriptor() == memberDescriptor) {
        return method;
      }
    }
    return null;
  }

  /**
   * Return the method at the given TIB slot
   * @param slot the slot that contains the method
   * @return the method at that slot
   */
  public final RVMMethod getTIBMethodAtSlot(int slot) {
    int index = TIB.getVirtualMethodIndex(slot);
    RVMMethod[] methods = getVirtualMethods();
    if (VM.VerifyAssertions) VM._assert(methods[index].getOffset().toInt() == slot << LOG_BYTES_IN_ADDRESS);
    return methods[index];
  }
  // Methods implemented in Primitive, RVMArray or RVMClass

  /**
   * Resolution status.
   * If the class/array has been "resolved", then size and offset information is
   * available by which the compiler can generate code to access this
   * class/array's
   * fields/methods via direct loads/stores/calls (rather than generating
   * code to access fields/methods symbolically, via dynamic linking stubs).
   * Primitives are always treated as "resolved".
   */
  @Uninterruptible
  public abstract boolean isResolved();

  /**
   * Instantiation status.
   * If the class/array has been "instantiated",
   * then all its methods have been compiled
   * and its type information block has been placed in the jtoc.
   * Primitives are always treated as "instantiated".
   */
  @Uninterruptible
  public abstract boolean isInstantiated();

  /**
   * Initialization status.
   * If the class has been "initialized",
   * then its <clinit> method has been executed.
   * Arrays have no <clinit> methods so they become
   * "initialized" immediately upon "instantiation".
   * Primitives are always treated as "initialized".
   */
  @Uninterruptible
  public abstract boolean isInitialized();

  /**
   * Only intended to be used by the BootImageWriter
   */
  public abstract void markAsBootImageClass();

  /**
   * Is this class part of the virtual machine's boot image?
   */
  @Uninterruptible
  public abstract boolean isInBootImage();

  /**
   * Get the offset in instances of this type assigned to the thin lock word.
   * Offset.max() if instances of this type do not have thin lock words.
   */
  @Uninterruptible
  public abstract Offset getThinLockOffset();

  /**
   * @return whether or not this is an instance of RVMClass?
   */
  @Uninterruptible
  public abstract boolean isClassType();

  /**
   * @return whether or not this is an instance of RVMArray?
   */
  @Uninterruptible
  public abstract boolean isArrayType();

  /**
   * @return whether or not this is a primitive type
   */
  @Uninterruptible
  public abstract boolean isPrimitiveType();

  /**
   * @return whether or not this is an unboxed type
   */
  @Uninterruptible
  public abstract boolean isUnboxedType();

  /**
   * @return whether or not this is a reference (ie non-primitive) type.
   */
  @Uninterruptible
  public abstract boolean isReferenceType();

  /**
   * @return whether type can be assigned to things of this RVMType
   */
  public boolean isAssignableFrom(RVMType type) {
    return this == type || RuntimeEntrypoints.isAssignableWith(this, type);
  }

  /**
   * Space required when this type is stored on the stack
   * (or as a field), in words.
   * Ie. 0, 1, or 2 words:
   * <ul>
   * <li> reference types (classes and arrays) require 1 word
   * <li> void types require 0 words
   * <li> long and double types require 2 words
   * <li> all other primitive types require 1 word
   * </ul>
   */
  @Uninterruptible
  public abstract int getStackWords();

  /**
   * Number of bytes in memory required to represent the type
   */
  @Uninterruptible
  public abstract int getMemoryBytes();

  /**
   * Cause resolution to take place.
   * This will cause slots to be allocated in the jtoc.
   */
  public abstract void resolve();

  /**
   * This method is only called by the bootimage writer.
   * It is called after {@link #resolve()} has been called on all
   * bootimaage types but before {@link #instantiate()} has been called
   * on any bootimaage type.
   * This provides a hook to compute various summaries that cannot be computed before types
   * are resolved.
   */
  public abstract void allBootImageTypesResolved();

  /**
   * Cause instantiation to take place.
   * This will cause the class's methods to be compiled and slots in the
   * jtoc to be filled-in.
   */
  public abstract void instantiate();

  /**
   * Cause initialization to take place.
   * This will cause the class's <clinit> method to be executed.
   */
  public abstract void initialize();

  /**
   * Does this type override java.lang.Object.finalize()?
   */
  @Uninterruptible
  public abstract boolean hasFinalizer();

  /**
   * Static fields of this class/array type.
   */
  public abstract RVMField[] getStaticFields();

  /**
   * Non-static fields of this class/array type
   * (composed with supertypes, if any).
   */
  public abstract RVMField[] getInstanceFields();

  /**
   * Statically dispatched methods of this class/array type.
   */
  public abstract RVMMethod[] getStaticMethods();

  /**
   * Virtually dispatched methods of this class/array type
   * (composed with supertypes, if any).
   */
  public abstract RVMMethod[] getVirtualMethods();

  /**
   * Runtime type information for this class/array type.
   */
  @Uninterruptible
  public abstract TIB getTypeInformationBlock();

  /**
   * Set the specialized method for a class or array.
   */
  public final void setSpecializedMethod(int id, CodeArray code) {
    getTypeInformationBlock().setSpecializedMethod(id, code);
  }

  /**
   * The memory manager's allocator id for this type.
   */
  private int mmAllocator;

  /**
   * Record the allocator information the memory manager holds about this type.
   *
   * @param allocator the allocator to record
   */
  public final void setMMAllocator(int allocator) {
    this.mmAllocator = allocator;
  }

  /**
   * This returns the allocator id as supplied by the memory manager.
   * The method is located here as this is the only common superclass of RVMArray
   * and RVMClass, and due to performance reasons this needs to be a non-abstract
   * method. For Primitive this field is unused.
   *
   * @return the allocator id previously recorded.
   */
  @Uninterruptible
  @Inline
  public final int getMMAllocator() {
    return mmAllocator;
  }

  /**
   * Is this field a type that must never move?
   */
  public boolean isNonMoving() {
    return hasNonMovingAnnotation();
  }
}
