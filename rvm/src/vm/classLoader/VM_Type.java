/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002, 2004,2005
 */
//$Id$
package com.ibm.jikesrvm.classloader;

import java.security.ProtectionDomain;

import com.ibm.jikesrvm.*;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * A description of a java type.
 * 
 * This class is the base of the java type system. 
 * To the three kinds of java objects
 * (class-instances, array-instances, primitive-instances) 
 * there are three corresponding
 * subclasses of VM_Type: VM_Class, VM_Array, VM_Primitive.
 * <p>
 * A VM_Class is constructed in four phases:
 * <ul>
 * <li> A "load" phase reads the ".class" file but does not attempt to 
 *      examine any of the symbolic references present there. This is done
 *      by the VM_Class constructor as a result of a VM_TypeReference being
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
 * VM_Array's are constructed in a similar fashion.
 * 
 * VM_Primitive's are constructed ab initio. 
 * Their "resolution", "instantiation", and "initialization" phases
 * are no-ops.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 * @author Ian Rogers
 */
public abstract class VM_Type extends VM_AnnotatedElement implements VM_ClassLoaderConstants, VM_SizeConstants, VM_Constants {

  /** Next space in the the type array */
  private static int nextId = 1;
  /** All types */
  private static VM_Type[] types = new VM_Type[1000];

  /** Canonical representation of no fields */
  protected static final VM_Field[] emptyVMField = new VM_Field[0];
  /** Canonical representation of no methods */
  protected static final VM_Method[] emptyVMMethod = new VM_Method[0];
  /** Canonical representation of no VM classes */
  protected static final VM_Class[] emptyVMClass = new VM_Class[0];

  /*
   * We hold on to a number of special types here for easy access.
   */
  public static final VM_Primitive VoidType;
  public static final VM_Primitive BooleanType;
  public static final VM_Primitive ByteType;
  public static final VM_Primitive ShortType;
  public static final VM_Primitive IntType;
  public static final VM_Primitive LongType;
  public static final VM_Primitive FloatType;
  public static final VM_Primitive DoubleType;
  public static final VM_Primitive CharType;
  public static final VM_Class JavaLangObjectType;
  public static final VM_Array JavaLangObjectArrayType;
  public static final VM_Class JavaLangClassType;
  public static final VM_Class JavaLangThrowableType; 
  public static final VM_Class NativeBridgeType;
  public static final VM_Class JavaLangStringType;    
  public static final VM_Class JavaLangCloneableType; 
  public static final VM_Class JavaIoSerializableType; 
  public static final VM_Class MagicType;             
  public static final VM_Primitive WordType;             
  public static final VM_Array WordArrayType;             
  public static final VM_Primitive AddressType;             
  public static final VM_Array AddressArrayType;             
  public static final VM_Class ObjectReferenceType;             
  public static final VM_Array ObjectReferenceArrayType;             
  public static final VM_Primitive OffsetType;             
  public static final VM_Array OffsetArrayType;             
  public static final VM_Primitive ExtentType;             
  public static final VM_Array ExtentArrayType;             
  public static final VM_Primitive CodeType;
  public static final VM_Array CodeArrayType;
  public static final VM_Class UninterruptibleType;   
  public static final VM_Class UnpreemptibleType;   
  public static final VM_Class SynchronizedObjectType;   
  public static final VM_Class DynamicBridgeType;     
  public static final VM_Class SaveVolatileType;      

  static {
    // Primitive types
    VoidType    = VM_TypeReference.Void.resolve().asPrimitive();
    BooleanType = VM_TypeReference.Boolean.resolve().asPrimitive();
    ByteType    = VM_TypeReference.Byte.resolve().asPrimitive();
    ShortType   = VM_TypeReference.Short.resolve().asPrimitive();
    IntType     = VM_TypeReference.Int.resolve().asPrimitive();
    LongType    = VM_TypeReference.Long.resolve().asPrimitive();
    FloatType   = VM_TypeReference.Float.resolve().asPrimitive();
    DoubleType  = VM_TypeReference.Double.resolve().asPrimitive();
    CharType    = VM_TypeReference.Char.resolve().asPrimitive();
    // Jikes RVM primitives
    AddressType = VM_TypeReference.Address.resolve().asPrimitive();
    WordType    = VM_TypeReference.Word.resolve().asPrimitive();
    OffsetType  = VM_TypeReference.Offset.resolve().asPrimitive();
    ExtentType  = VM_TypeReference.Extent.resolve().asPrimitive();
    CodeType    = VM_TypeReference.Code.resolve().asPrimitive();
    // Jikes RVM classes
    ObjectReferenceType = VM_TypeReference.ObjectReference.resolve().asClass();
    MagicType           = VM_TypeReference.Magic.resolve().asClass();
    UninterruptibleType =
      VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                    VM_Atom.findOrCreateAsciiAtom("Lorg/vmmagic/pragma/Uninterruptible;")
                                    ).resolve().asClass();
    UnpreemptibleType =
      VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                    VM_Atom.findOrCreateAsciiAtom("Lorg/vmmagic/pragma/Unpreemptible;")
                                    ).resolve().asClass();
    SynchronizedObjectType =
      VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                    VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/jikesrvm/VM_SynchronizedObject;")
                                    ).resolve().asClass();
    DynamicBridgeType =
      VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                    VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/jikesrvm/VM_DynamicBridge;")
                                    ).resolve().asClass();
    SaveVolatileType =
      VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                    VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/jikesrvm/VM_SaveVolatile;")
                                    ).resolve().asClass();
    NativeBridgeType      =
      VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                    VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/jikesrvm/jni/VM_NativeBridge;")
                                    ).resolve().asClass();
    // Array types
    CodeArrayType = VM_TypeReference.CodeArray.resolve().asArray();
    WordArrayType = VM_TypeReference.WordArray.resolve().asArray();
    AddressArrayType = VM_TypeReference.AddressArray.resolve().asArray();
    ObjectReferenceArrayType = VM_TypeReference.ObjectReferenceArray.resolve().asArray();
    OffsetArrayType = VM_TypeReference.OffsetArray.resolve().asArray();
    ExtentArrayType = VM_TypeReference.ExtentArray.resolve().asArray();
    // Java clases
    JavaLangObjectType      = VM_TypeReference.JavaLangObject.resolve().asClass();
    JavaLangObjectArrayType = VM_TypeReference.JavaLangObjectArray.resolve().asArray();
    JavaLangClassType       = VM_TypeReference.JavaLangClass.resolve().asClass();
    JavaLangThrowableType   = VM_TypeReference.JavaLangThrowable.resolve().asClass();
    JavaLangStringType      = VM_TypeReference.JavaLangString.resolve().asClass();
    JavaLangCloneableType   = VM_TypeReference.JavaLangCloneable.resolve().asClass();
    JavaIoSerializableType  = VM_TypeReference.JavaIoSerializable.resolve().asClass();
  }

  /**
   * Canonical type reference for this VM_Type instance
   */
  protected final VM_TypeReference typeRef;

  /**
   * Type id -- used to index into typechecking datastructures
   */
  protected final int id;

  /**
   * index of jtoc slot that has type information block for this VM_Type
   */
  protected final int tibOffset;

  /**
   * instance of java.lang.Class corresponding to this type 
   */
  private final Class classForType; 

  /**
   * Number of [ in descriptor for arrays; -1 for primitives; 0 for
   * classes. NB this field must appear in all VM_Types for fast type
   * checks {@see OPT_DynamicTypeCheckExpansion}.
   */
  protected final int dimension;
  /**
   * Number of superclasses to Object. Known immediately for
   * primitives and arrays, but only after resolving for classes. NB
   * this field must appear in all VM_Types for fast object array
   * store checks {@see OPT_DynamicTypeCheckExpansion}.
   */
  protected int depth;
  /**
   * cached VM_Array that corresponds to arrays of this type.
   * (null ->> not created yet).
   */
  private VM_Array cachedElementType;

  /**
   * Create an instance of a {@link VM_Type}
   * @param typeRef The canonical type reference for this type.
   * @param classForType The java.lang.Class representation
   * @param dimension The dimensionality
   * @param runtimeVisibleAnnotations array of runtime visible
   * annotations
   * @param runtimeInvisibleAnnotations optional array of runtime
   * invisible annotations
   */
  protected VM_Type(VM_TypeReference typeRef,
                    Class classForType,
                    int dimension,
                    VM_Annotation runtimeVisibleAnnotations[],
                    VM_Annotation runtimeInvisibleAnnotations[])
  {
    super(runtimeVisibleAnnotations, runtimeInvisibleAnnotations);
    this.typeRef = typeRef;
    this.tibOffset = VM_Statics.allocateReferenceSlot().toInt();
    this.id = nextId(this);
    this.classForType = classForType;
    this.dimension = dimension;

    // install partial type information block (no method dispatch table) 
    // for use in type checking.
    //
    if (VM.VerifyAssertions) VM._assert(VM_TIBLayoutConstants.TIB_TYPE_INDEX == 0);
    Object[] tib = new Object[1];
    tib[0] = this;
    VM_Statics.setSlotContents(getTibOffset(), tib);
  }

  /**
   * Create an instance of a {@link VM_Type}
   * @param typeRef The canonical type reference for this type.
   * @param dimension The dimensionality
   * @param runtimeVisibleAnnotations array of runtime visible
   * annotations
   * @param runtimeInvisibleAnnotations optional array of runtime
   * invisible annotations
   */
  protected VM_Type(VM_TypeReference typeRef,
                    int dimension,
                    VM_Annotation runtimeVisibleAnnotations[],
                    VM_Annotation runtimeInvisibleAnnotations[])
  {
    super(runtimeVisibleAnnotations, runtimeInvisibleAnnotations);
    this.typeRef = typeRef;
    this.tibOffset = VM_Statics.allocateReferenceSlot().toInt();
    this.id = nextId(this);
    this.classForType = createClassForType(this, typeRef);
    this.dimension = dimension;

    // install partial type information block (no method dispatch table) 
    // for use in type checking.
    //
    if (VM.VerifyAssertions) VM._assert(VM_TIBLayoutConstants.TIB_TYPE_INDEX == 0);
    Object[] tib = new Object[1];
    tib[0] = this;
    VM_Statics.setSlotContents(getTibOffset(), tib);
  }

  /**
   * Canonical type reference for this type.
   */
  public final VM_TypeReference getTypeRef() throws UninterruptiblePragma {
    return typeRef;
  }

  /**
   * Get the numeric identifier for this type
   */
  public final int getId() throws UninterruptiblePragma {
    return id;
  }

  /**
   * Instance of java.lang.Class corresponding to this type.
   * This is commonly used for reflection.
   */   
  public final Class getClassForType() {
    // Resolve the class so that we don't need to resolve it
    // in reflection code
    if (!isResolved()) {
       resolve();
    }
    return classForType;
  }

  /**
   * Get offset of tib slot from start of jtoc, in bytes.
   */ 
  public final Offset getTibOffset() throws UninterruptiblePragma { 
    return Offset.fromIntSignExtend(tibOffset);
  }

  /**
   * Get the class loader for this type
   */
  public final ClassLoader getClassLoader() throws UninterruptiblePragma {
    return typeRef.getClassLoader();
  }

  /** 
   * Descriptor for this type.
   * For a class, something like "Ljava/lang/String;".
   * For an array, something like "[I" or "[Ljava/lang/String;".
   * For a primitive, something like "I".
   */ 
  public final VM_Atom getDescriptor() throws UninterruptiblePragma {
    return typeRef.getName();
  }

  /**
   * Define hashCode(), to allow use of consistent hash codes during
   * bootImage writing and run-time
   */
  public final int hashCode() {
    return typeRef.hashCode();
  }

  /**
   * get number of superclasses to Object 
   *   0 java.lang.Object, VM_Primitive, and VM_Classes that are interfaces
   *   1 for VM_Arrays and classes that extend Object directly
   */ 
  public abstract int getTypeDepth () throws UninterruptiblePragma;

  /**
   * Reference Count GC: Is a reference of this type contained in
   * another object inherently acyclic (without cycles) ?
   */ 
  public abstract boolean isAcyclicReference() throws UninterruptiblePragma;

  /**
   * Number of [ in descriptor for arrays; -1 for primitives; 0 for classes
   */ 
  public abstract int getDimensionality() throws UninterruptiblePragma;

  /**
   * @return this cast to a VM_Class
   */
  public final VM_Class asClass() throws UninterruptiblePragma {
    return (VM_Class)this;
  }
  /**
   * @return this cast to a VM_Array
   */
  public final VM_Array asArray() throws UninterruptiblePragma {
    return (VM_Array)this;
  }
  /**
   * @return this cast to a VM_Primitive
   */
  public final VM_Primitive asPrimitive() throws UninterruptiblePragma { 
    return (VM_Primitive)this;
  }

  // Convenience methods.
  //
  /** @return is this type void? */
  public final boolean isVoidType() throws UninterruptiblePragma {
    return this == VoidType;
  }
  /** @return is this type the primitive boolean? */
  public final boolean isBooleanType() throws UninterruptiblePragma {
    return this == BooleanType;
  }
  /** @return is this type the primitive byte? */
  public final boolean isByteType() throws UninterruptiblePragma {
    return this == ByteType;
  }
  /** @return is this type the primitive short? */
  public final boolean isShortType() throws UninterruptiblePragma {
    return this == ShortType;
  }
  /** @return is this type the primitive int? */
  public final boolean isIntType() throws UninterruptiblePragma {
    return this == IntType;
  }
  /** @return is this type the primitive long? */
  public final boolean isLongType() throws UninterruptiblePragma {
    return this == LongType;
  }
  /** @return is this type the primitive float? */
  public final boolean isFloatType() throws UninterruptiblePragma {
    return this == FloatType;
  }
  /** @return is this type the primitive double? */
  public final boolean isDoubleType() throws UninterruptiblePragma {
    return this == DoubleType;
  }
  /** @return is this type the primitive char? */
  public final boolean isCharType() throws UninterruptiblePragma {
    return this == CharType;
  }
  /**
   * @return is this type the primitive int like? ie is it held as an
   * int on the JVM stack
   */
  public final boolean isIntLikeType() throws UninterruptiblePragma {
    return isBooleanType() || isByteType() ||
      isShortType() || isIntType() || isCharType();
  }
  /** @return is this type the class Object? */
  public final boolean isJavaLangObjectType() throws UninterruptiblePragma {
    return this == JavaLangObjectType;
  }
  /** @return is this type the class Throwable? */
  public final boolean isJavaLangThrowableType() throws UninterruptiblePragma {
    return this == JavaLangThrowableType;
  }
  /** @return is this type the class String? */
  public final boolean isJavaLangStringType() throws UninterruptiblePragma {
    return this == JavaLangStringType;
  }
  /**
   * @return is this type an internal Jikes RVM type the size of a
   * word?
   */
  public final boolean isWordType() throws UninterruptiblePragma {
    return (this == WordType) || (this == AddressType) ||
      (this == ObjectReferenceType) || (this == ExtentType) ||
      (this == OffsetType);
  }
  /**
   * @return is this type an internal Jikes RVM type representing a
   * word sized array?
   */
  final boolean isWordArrayType() throws UninterruptiblePragma {
    return (this == WordArrayType) || (this == AddressArrayType) ||
      (this == ObjectReferenceArrayType) || (this == ExtentArrayType) || 
      (this == OffsetArrayType);
  }
  /** @return is this type the Jikes RVM internal code type? */
  final boolean isCodeType() throws UninterruptiblePragma {
    return this == CodeType;
  }
  /** @return is this type the Jikes RVM internal code array type? */
  final boolean isCodeArrayType() throws UninterruptiblePragma {
    return this == CodeArrayType;
  }
  /**
   * @return is this type a Jikes RVM magic type that the compilers
   * will compile differently?
   */
  public final boolean isMagicType() throws UninterruptiblePragma {
    return isWordType() || isWordArrayType() ||
      this == MagicType || this == CodeArrayType;
  }
  /**
   * @return is this type the Jikes RVM internal uninterruptible
   * pragma type?
   */
  public final boolean isUninterruptibleType() throws UninterruptiblePragma {
    return this == UninterruptibleType;
  }
  /**
   * @return is this type the Jikes RVM internal unpreemptible pragma
   * type?
   */
  public final boolean isUnpreemptibleType() throws UninterruptiblePragma {
    return this == UnpreemptibleType;
  }
  /**
   * @return is this type the Jikes RVM internal synchronized object
   * pragma type?
   */
  public final boolean isSynchronizedObjectType() throws UninterruptiblePragma {
    return this == SynchronizedObjectType;
  }
  /**
   * @return is this type the Jikes RVM internal dynamic bridge pragma
   * type?
   */
  public final boolean isDynamicBridgeType() throws UninterruptiblePragma {
    return this == DynamicBridgeType;
  }
  /**
   * @return is this type the Jikes RVM internal save volatile pragma
   * type?
   */
  public final boolean isSaveVolatileType() throws UninterruptiblePragma {
    return this == SaveVolatileType;
  }
  /**
   * @return is this type the Jikes RVM internal native bridge pragma
   * type?
   */
  public final boolean isNativeBridgeType() throws UninterruptiblePragma {
    return this == NativeBridgeType;
  }

  /**
   * Get array type corresponding to "this" array element type.
   */ 
  public final VM_Array getArrayTypeForElementType() {
    if (cachedElementType == null) {
      VM_TypeReference tr = typeRef.getArrayTypeForElementType();
      cachedElementType = tr.resolve().asArray();
      /*  Can't fail to resolve the type, because the element type already
          exists (it is 'this') and the VM creates array types itself without
          any possibility of error if the element type is already loaded. */
    }
    return cachedElementType;
  }

  /**
   * get superclass id vector (@see VM_DynamicTypeCheck)
   */ 
  public final short[] getSuperclassIds () throws UninterruptiblePragma {
    return VM_Magic.objectAsShortArray(getTypeInformationBlock()[VM.TIB_SUPERCLASS_IDS_INDEX]);
  }

  /**
   * get doesImplement vector (@see VM_DynamicTypeCheck)
   */ 
  public final int[] getDoesImplement () throws UninterruptiblePragma {
    return VM_Magic.objectAsIntArray(getTypeInformationBlock()[VM.TIB_DOES_IMPLEMENT_INDEX]);
  }

  /**
   * Allocate entry in types array and add it (NB resize array if it's
   * not long enough)
   */
  private static synchronized int nextId(VM_Type it) {
    int ans = nextId++;
    if (ans == types.length) {
      VM_Type[] newTypes = new VM_Type[types.length+500];
      for (int i=0; i<types.length; i++) {
        newTypes[i] = types[i];
      }
      types = newTypes;
    }
    types[ans] = it;
    return ans;
  }

  /**
   * How many types have been created?
   * Only intended to be used by the bootimage writer!
   */
  public static final int numTypes() throws UninterruptiblePragma { 
    return nextId-1; 
  }
  /**
   * Get all the created types.
   * Only intended to be used by the bootimage writer!
   */
  public static final VM_Type[] getTypes() throws UninterruptiblePragma { 
    return types; 
  }
  /**
   * Get the type for the given id
   */
  public static final VM_Type getType(int id) throws UninterruptiblePragma {
    return types[id];
  }

  /**
   * Utility to create a java.lang.Class for the given type using the
   * given type reference
   */
  protected static Class createClassForType(VM_Type type,
                                            VM_TypeReference typeRef) {
    if (VM.runningVM) {
      return java.lang.JikesRVMSupport.createClass(type);
    }
    else if (VM.writingBootImage) {
      Exception x;
      try {
        VM_Atom className = typeRef.getName();
        if(className.isClassDescriptor()) {
          return Class.forName(className.classNameFromDescriptor(), false, VM_Type.class.getClassLoader());
        }
        else {
          return Class.forName(className.toString().replace('/','.'), false, VM_Type.class.getClassLoader());
        }
      }
      catch (ClassNotFoundException e) { x = e; }
      catch (SecurityException e) { x = e; }
      if (typeRef.isArrayType() && typeRef.getArrayElementType().isCodeType()) {
        // ignore - we expect not to have a concrete version of the code class
        return null;
      } else {
        throw new Error("Unable to find Java class for RVM type", x);
      }
    } else {
      return null;
    }
  }

  /**
   * Find specified virtual method description.
   * @param memberName   method name - something like "foo"
   * @param memberDescriptor method descriptor - something like "I" or "()I"
   * @return method description (null --> not found)
   */
  public final VM_Method findVirtualMethod(VM_Atom memberName, 
                                           VM_Atom memberDescriptor) {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    VM_Method methods[] = getVirtualMethods();
    for (int i = 0, n = methods.length; i < n; ++i) {
      VM_Method method = methods[i];
      if (method.getName() == memberName && 
          method.getDescriptor() == memberDescriptor)
        return method;
    }
    return null;
  }
  
  /**
   * Return the method at the given TIB slot
   * @param slot the slot that contains the method
   * @return the method at that slot
   */
  public final VM_Method getTIBMethodAtSlot(int slot) {
    if (slot >= VM_TIBLayoutConstants.TIB_FIRST_VIRTUAL_METHOD_INDEX) {
      VM_Method methods[] = getVirtualMethods();
      int offset = slot << LOG_BYTES_IN_ADDRESS;
      for(int i=0, n = methods.length; i < n; i++) {
        if (methods[i].getOffset().toInt() == offset) {
          return methods[i];
        }
      }
    }
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return null;
  }

  protected void  checkTIBSlotIsAccessible(int slot) {
    if (!isInstantiated()) {
      VM._assert(false, toString() + "'s TIB is inaccessible as its not instantiated");
    }
    if ((slot < 0) || (slot >= getTypeInformationBlock().length)) {
      VM._assert(false, toString() + " doesn't have a slot at " + slot);
    }
  }
  // Methods implemented in VM_Primitive, VM_Array or VM_Class

  /**
   * Resolution status.
   * If the class/array has been "resolved", then size and offset information is
   * available by which the compiler can generate code to access this 
   * class/array's 
   * fields/methods via direct loads/stores/calls (rather than generating
   * code to access fields/methods symbolically, via dynamic linking stubs).
   * Primitives are always treated as "resolved".
   */ 
  public abstract boolean isResolved() throws UninterruptiblePragma;

  /**
   * Instantiation status.
   * If the class/array has been "instantiated", 
   * then all its methods have been compiled
   * and its type information block has been placed in the jtoc.
   * Primitives are always treated as "instantiated".
   */ 
  public abstract boolean isInstantiated() throws UninterruptiblePragma;
   
  /**
   * Initialization status.
   * If the class has been "initialized", 
   * then its <clinit> method has been executed.
   * Arrays have no <clinit> methods so they become 
   * "initialized" immediately upon "instantiation".
   * Primitives are always treated as "initialized".
   */ 
  public abstract boolean isInitialized() throws UninterruptiblePragma;

  /**
   * Only intended to be used by the BootImageWriter
   */
  public abstract void markAsBootImageClass();

  /**
   * Is this class part of the virtual machine's boot image?
   */ 
  public abstract boolean isInBootImage() throws UninterruptiblePragma;

  /**
   * Get the offset in instances of this type assigned to the thin lock word.
   * -1 if instances of this type do not have thin lock words.
   */
  public abstract Offset getThinLockOffset() throws UninterruptiblePragma;

  public abstract void setThinLockOffset(Offset offset);
  
  /**
   * @return whether or not this is an instance of VM_Class?
   */
  public abstract boolean isClassType() throws UninterruptiblePragma;

  /**
   * @return whether or not this is an instance of VM_Array?
   */
  public abstract boolean isArrayType() throws UninterruptiblePragma;
  /**
   * @return whether or not this is a primitive type
   */
  public abstract boolean isPrimitiveType() throws UninterruptiblePragma;
  /**
   * @return whether or not this is a reference (ie non-primitive) type.
   */
  public abstract boolean isReferenceType() throws UninterruptiblePragma;
   
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
  public abstract int getStackWords() throws UninterruptiblePragma;

  /**
   * Cause resolution to take place.
   * This will cause slots to be allocated in the jtoc.
   */ 
  public abstract void resolve();

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
  public abstract boolean hasFinalizer() throws UninterruptiblePragma;

  /**
   * Static fields of this class/array type.
   */ 
  public abstract VM_Field[] getStaticFields();

  /**
   * Non-static fields of this class/array type 
   * (composed with supertypes, if any).
   */ 
  public abstract VM_Field[] getInstanceFields();

  /**
   * Statically dispatched methods of this class/array type.
   */ 
  public abstract VM_Method[] getStaticMethods();

  /**
   * Virtually dispatched methods of this class/array type 
   * (composed with supertypes, if any).
   */ 
  public abstract VM_Method[] getVirtualMethods();

  /**
   * Runtime type information for this class/array type.
   */ 
  public abstract Object[] getTypeInformationBlock() throws UninterruptiblePragma;  
  /**
   * Does this slot in the TIB hold a TIB entry?
   */
  public abstract boolean isTIBSlotTIB(int slot);
  /**
   * Does this slot in the TIB hold code?
   */
  public abstract boolean isTIBSlotCode(int slot);
  /**
   * Record the type information the memory manager holds about this
   * type
   * @param mmt the type to record
   */
  public abstract void setMMType(Object mmt);

  /**
   * @return the type information the memory manager previously
   * recorded about this type
   */
  public abstract Object getMMType() throws UninterruptiblePragma;
}
