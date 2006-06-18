/*
 * (C) Copyright IBM Corp 2001,2002, 2004,2005
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import java.security.ProtectionDomain;

import com.ibm.JikesRVM.*;
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
 */
public abstract class VM_Type extends VM_AnnotatedElement implements VM_ClassLoaderConstants, VM_SizeConstants {

  /*
   * We hold on to a number of special types here for easy access.
   */
  public static VM_Primitive VoidType;
  public static VM_Primitive BooleanType;
  public static VM_Primitive ByteType;
  public static VM_Primitive ShortType;
  public static VM_Primitive IntType;
  public static VM_Primitive LongType;
  public static VM_Primitive FloatType;
  public static VM_Primitive DoubleType;
  public static VM_Primitive CharType;
  public static VM_Class JavaLangObjectType;
  public static VM_Array JavaLangObjectArrayType;
  public static VM_Class JavaLangClassType;
  public static VM_Class JavaLangThrowableType; 
  public static VM_Class NativeBridgeType;
  public static VM_Class JavaLangStringType;    
  public static VM_Class JavaLangCloneableType; 
  public static VM_Class JavaIoSerializableType; 
  public static VM_Class MagicType;             
  public static VM_Primitive WordType;             
  public static VM_Array WordArrayType;             
  public static VM_Primitive AddressType;             
  public static VM_Array AddressArrayType;             
  public static VM_Class ObjectReferenceType;             
  public static VM_Array ObjectReferenceArrayType;             
  public static VM_Primitive OffsetType;             
  public static VM_Array OffsetArrayType;             
  public static VM_Primitive ExtentType;             
  public static VM_Array ExtentArrayType;             
  public static VM_Primitive CodeType;
  public static VM_Array CodeArrayType;
  public static VM_Class UninterruptibleType;   
  public static VM_Class UnpreemptibleType;   
  public static VM_Class SynchronizedObjectType;   
  public static VM_Class DynamicBridgeType;     
  public static VM_Class SaveVolatileType;      

  private static int nextId = 1;
  private static VM_Type[] types = new VM_Type[1000];

  /**
   * Canonical type reference for this VM_Type instance
   */
  protected final VM_TypeReference typeRef;

  /**
   * Type id -- used to index into typechecking datastructures
   */
  protected final int id;

  /**
   * current class-loading stage of this type
   */
  protected int state;        

  /**
   * Is this type in the bootimage?
   */
  private boolean inBootImage;

  /**
   * index of jtoc slot that has type information block for this VM_Type
   */
  protected final int tibOffset;      

  /**
   * instance of java.lang.Class corresponding to this type 
   * (null --> not created yet)
   */
  private Class classForType; 

  /**
   * cached VM_Array that corresponds to arrays of this type.
   * (null ->> not created yet).
   */
  private VM_Array cachedElementType;

  /**
   * -1 => primitive, 0 => Class/Interface, positive => array (number of [)
   */
  protected final int dimension;    

  /**
   * number of superclasses to Object
   */
  protected int depth;        

  /**
   * At what offset is the thin lock word to be found in instances of
   * objects of this type?  A value of -1 indicates that the instances of
   * this type do not have inline thin locks.
   */
  protected Offset thinLockOffset = VM_ObjectModel.defaultThinLockOffset();

  /** The memory manager's notion of this type */
  private Object mmType;

  /** Counters for boot image per-type statistics */
  public int bootCount;
  public int bootBytes; 

  /**
   * RCGC: is this type acyclic? 
   */
  protected boolean acyclic;       

  /**
   * Create an instance of a {@link VM_Type}
   * @param typeRef The canonical type reference for this type.
   * @param runtimeVisibleAnnotations array of runtime visible
   * annotations
   * @param runtimeInvisibleAnnotations optional array of runtime
   * invisible annotations
   */
  protected VM_Type(VM_TypeReference typeRef,
                    VM_Annotation runtimeVisibleAnnotations[],
                    VM_Annotation runtimeInvisibleAnnotations[])
  {
    super(runtimeVisibleAnnotations, runtimeInvisibleAnnotations);
    this.typeRef = typeRef;
    this.state = CLASS_VACANT;
    this.dimension = typeRef.getDimensionality();
    this.tibOffset = VM_Statics.allocateSlot(VM_Statics.TIB);
    this.id = nextId(this);

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

  public final int getId() throws UninterruptiblePragma { return id; }

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
   * Resolution status.
   * If the class/array has been "resolved", then size and offset information is
   * available by which the compiler can generate code to access this 
   * class/array's 
   * fields/methods via direct loads/stores/calls (rather than generating
   * code to access fields/methods symbolically, via dynamic linking stubs).
   * Primitives are always treated as "resolved".
   */ 
  public final boolean isResolved() throws UninterruptiblePragma { 
    return state >= CLASS_RESOLVED; 
  }
   
  /**
   * Instantiation status.
   * If the class/array has been "instantiated", 
   * then all its methods have been compiled
   * and its type information block has been placed in the jtoc.
   * Primitives are always treated as "instantiated".
   */ 
  public final boolean isInstantiated() throws UninterruptiblePragma { 
    return state >= CLASS_INSTANTIATED; 
  }
   
  /**
   * Initialization status.
   * If the class has been "initialized", 
   * then its <clinit> method has been executed.
   * Arrays have no <clinit> methods so they become 
   * "initialized" immediately upon "instantiation".
   * Primitives are always treated as "initialized".
   */ 
  public final boolean isInitialized() throws UninterruptiblePragma { 
    return state == CLASS_INITIALIZED; 
  }

  /**
   * Only intended to be used by the BootImageWriter
   */
  public void markAsBootImageClass() {
    inBootImage = true;
  }

  /**
   * Is this class part of the virtual machine's boot image?
   */ 
  public final boolean isInBootImage() throws UninterruptiblePragma {
    return inBootImage;
  }

  /**
   * Get offset of tib slot from start of jtoc, in bytes.
   */ 
  public final Offset getTibOffset() throws UninterruptiblePragma { 
    return Offset.fromIntSignExtend(tibOffset); 
  }

  /**
   * Number of [ in descriptor for arrays; -1 for primitives; 0 for classes
   */ 
  public final int getDimensionality() throws UninterruptiblePragma { 
    return dimension; 
  }

  /**
   * get number of superclasses to Object 
   *   0 java.lang.Object, VM_Primitive, and VM_Classes that are interfaces
   *   1 for VM_Arrays and classes that extend Object directly
   */ 
  public final int getTypeDepth () throws UninterruptiblePragma { 
    return depth; 
  }

  /**
   * Get the offset in instances of this type assigned to the thin lock word.
   * -1 if instances of this type do not have thin lock words.
   */
  public final Offset getThinLockOffset() throws UninterruptiblePragma { 
    return thinLockOffset; 
  }

  public final void setThinLockOffset(Offset offset) {
    if (VM.VerifyAssertions) VM._assert (thinLockOffset.isMax());
    thinLockOffset = offset;
  }
  
  /**
   * RCGC: Is a reference of this type contained 
   * in another object inherently acyclic?
   */ 
  public boolean isAcyclicReference() throws UninterruptiblePragma {
    return acyclic;
  }

  /**
   * @return whether or not this is an instance of VM_Class?
   */
  public final boolean isClassType() throws UninterruptiblePragma { 
    return dimension == 0; 
  } 
  /**
   * @return whether or not this is an instance of VM_Array?
   */
  public final boolean isArrayType() throws UninterruptiblePragma { 
    return dimension > 0; 
  }
  /**
   * @return whether or not this is a primitive type
   */
  public final boolean isPrimitiveType() throws UninterruptiblePragma { 
    return dimension < 0;
  }
  /**
   * @return whether or not this is a reference (ie non-primitive) type.
   */
  public final boolean isReferenceType() throws UninterruptiblePragma { 
    return !isPrimitiveType(); 
  }
   
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
   * Define hashCode(), to allow use of consistent hash codes during
   * bootImage writing and run-time
   */
  public int hashCode() { return typeRef.hashCode(); }

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
   * Instance of java.lang.Class corresponding to this type.
   */   
  public final Class getClassForType() {
    // ensure that create() is not called during boot image writing
    // since the jdk loads its version of java.lang.Class instead of ours.
    // This only happens for static synchronized methods and the Class 
    // object must be explictly loaded at start up of the runtime.  
    // See VM.boot(). This test for runtime can be removed 
    // once the bootImageWriter has been rewritten to properly load classes.
    if (classForType == null) {
      if (!VM.runningVM) {
        throw new Error("It is not possible to get the class associated with a type " +
                        "before booting (ie in the boot image writer) "+
                        "as the JDK version of java.lang.Class will be used instead " +
                        "of our own");
      }
      // ensure that we resolve the VM_Class before creating a 
      // java.lang.Class object for it.  Doing it here frees us from having
      // to check it all over the reflection code. 
      if (!isResolved()) {
        resolve();
      }
      synchronized(this) {
        if (classForType == null) {
          classForType = java.lang.JikesRVMSupport.createClass(this);
        }
      }
    }
    return classForType;
  }

  /* This code is cribbed from VM_Type.getClassForType.  I didn't fully
   * understand it when I was cribbing from it.   This had to be done so that
   * we can set the ProtectionDomain for the class when we create it.  */
  public final Class createClassForType(ProtectionDomain pd) {
    if (!VM.runningVM)
      return null;
    // ensure that we resolve the VM_Class before creating a 
    // java.lang.Class object for it.  Doing it here frees us from having
    // to check it all over the reflection code. 
    if (!isResolved()) {
      resolve();
    }
    synchronized(this) {
      // Must not already be created.
      if (VM.VerifyAssertions) VM._assert(classForType == null);
      return classForType = java.lang.JikesRVMSupport.createClass(this, pd);
    }
  }

  public final void setMMType(Object mmt) {
    mmType = mmt;
  }

  public final Object getMMType() throws UninterruptiblePragma {
    return mmType;
  }
  
  // Convenience methods.
  //
  public final boolean isVoidType() throws UninterruptiblePragma              { return this == VoidType;           }
  public final boolean isBooleanType() throws UninterruptiblePragma           { return this == BooleanType;        }
  public final boolean isByteType() throws UninterruptiblePragma              { return this == ByteType;           }
  public final boolean isShortType() throws UninterruptiblePragma             { return this == ShortType;          }
  public final boolean isIntType() throws UninterruptiblePragma               { return this == IntType;            }
  public final boolean isLongType() throws UninterruptiblePragma              { return this == LongType;           }
  public final boolean isFloatType() throws UninterruptiblePragma             { return this == FloatType;          }
  public final boolean isDoubleType() throws UninterruptiblePragma            { return this == DoubleType;         }
  public final boolean isCharType() throws UninterruptiblePragma              { return this == CharType;           }
  public final boolean isIntLikeType() throws UninterruptiblePragma           { return isBooleanType() || isByteType() || isShortType() || isIntType() || isCharType(); }

  public final boolean isJavaLangObjectType() throws UninterruptiblePragma    { return this == JavaLangObjectType;    }
  public final boolean isJavaLangThrowableType() throws UninterruptiblePragma { return this == JavaLangThrowableType; }
  public final boolean isJavaLangStringType() throws UninterruptiblePragma    { return this == JavaLangStringType;    }

  public final boolean isWordType() throws UninterruptiblePragma              { return (this == WordType) ||
                                                                                          (this == AddressType) ||
                                                                                          (this == ObjectReferenceType) ||
                                                                                          (this == ExtentType) || 
                                                                                          (this == OffsetType); }
  final boolean isWordArrayType() throws UninterruptiblePragma         { return (this == WordArrayType) ||
                                                                                          (this == AddressArrayType) ||
                                                                                          (this == ObjectReferenceArrayType) ||
                                                                                          (this == ExtentArrayType) || 
                                                                                          (this == OffsetArrayType); }
  final boolean isCodeType() throws UninterruptiblePragma { return this == CodeType; }
  final boolean isCodeArrayType() throws UninterruptiblePragma { return this == CodeArrayType; }
  public final boolean isMagicType() throws UninterruptiblePragma             { return isWordType() || isWordArrayType() ||
                                                                                     this == MagicType || this == CodeArrayType; }
  public final boolean isUninterruptibleType() throws UninterruptiblePragma   { return this == UninterruptibleType;   }
  public final boolean isUnpreemptibleType() throws UninterruptiblePragma     { return this == UnpreemptibleType;   }
  public final boolean isSynchronizedObjectType() throws UninterruptiblePragma{ return this == SynchronizedObjectType;   }
  public final boolean isDynamicBridgeType() throws UninterruptiblePragma     { return this == DynamicBridgeType;     }
  public final boolean isSaveVolatileType() throws UninterruptiblePragma      { return this == SaveVolatileType;      }
  public final boolean isNativeBridgeType() throws UninterruptiblePragma      { return this == NativeBridgeType;      }

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
  final short[] getSuperclassIds () throws UninterruptiblePragma {
    return VM_Magic.objectAsShortArray(getTypeInformationBlock()[VM.TIB_SUPERCLASS_IDS_INDEX]);
  }

  /**
   * get doesImplement vector (@see VM_DynamicTypeCheck)
   */ 
  public final int[] getDoesImplement () throws UninterruptiblePragma {
    return VM_Magic.objectAsIntArray(getTypeInformationBlock()[VM.TIB_DOES_IMPLEMENT_INDEX]);
  }
         
  static void init() {
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
                                    VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_SynchronizedObject;")
                                    ).resolve().asClass();
    DynamicBridgeType =
      VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                    VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_DynamicBridge;")
                                    ).resolve().asClass();
    SaveVolatileType =
      VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                    VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_SaveVolatile;")
                                    ).resolve().asClass();
    NativeBridgeType      =
      VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                    VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/jni/VM_NativeBridge;")
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
    
    VM_Array.init();
  }

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
}
