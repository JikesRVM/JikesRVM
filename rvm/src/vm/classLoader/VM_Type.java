/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
//-#if RVM_WITH_JMTK
import com.ibm.JikesRVM.memoryManagers.vmInterface.Type;
//-#endif

/**
 * A description of a java object.
 *
 * This class is the base of the java type system. 
 * To the three kinds of java objects
 * (class-instances, array-instances, primitive-instances) 
 * there are three corresponding
 * subclasses of VM_Type: VM_Class, VM_Array, VM_Primitive.
 *
 * VM_Class's are constructed in four phases:
 *
 * <ul>
 * <li> A "forward reference" phase records the type descriptor but 
 * does not attempt to read
 *   the ".class" file.
 *
 * <li> A "load" phase reads the ".class" file but does not attempt to 
 * examine any of the symbolic references present there.
 *
 * <li> A "resolve" phase follows symbolic references as needed to discover
 *   ancestry, to measure field sizes, and to allocate space in the jtoc
 *   for the class's static fields and methods.
 *
 * <li>  An "instantiate" phase compiles the class's methods, 
 * installs the type information block,
 *   static fields, and static methods into the jtoc.
 *
 * <li> An "initialize" phase runs the class's static initializer.
 * </ul>
 *
 * VM_Array's are constructed in similar fashion to VM_Class's, 
 * except there is no 
 * "forward reference" or "load" phase, because the descriptions are 
 * completely self contained.
 *
 * VM_Primitive's are constructed ab initio. 
 * They have no "forward reference", "load", 
 * "resolution", "instantiation", or "initialization" phases.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
 public abstract class VM_Type implements VM_ClassLoaderConstants {
   //-----------//
   // interface //
   //-----------//

   // Identity: if two types have the same "tib" (type information block) 
   // then they are identical types.

   /**
    * Get jtoc slot that contains tib for this VM_Type.
    * Note that tib is incomplete (contains a type-slot but no method-slots) 
    * until the class/array has been "instantiated".
    */ 
   public final int getTibSlot() throws VM_PragmaUninterruptible { return tibSlot; }

   /**
    * Get offset of tib slot from start of jtoc, in bytes.
    */ 
   public final int getTibOffset() throws VM_PragmaUninterruptible { return tibSlot << 2; }

   /**
    * Deprecated.
    *!!TODO: remove this when cleanup complete. [--DL]
    */
   public final int getOffset() { return getTibOffset(); }

   // Classification.
   //
   public final boolean isClassType() throws VM_PragmaUninterruptible { return dimension == 0; } 
   public final boolean isArrayType() throws VM_PragmaUninterruptible { return dimension > 0;  }
   public final boolean isPrimitiveType() throws VM_PragmaUninterruptible { return (dimension < 0) || isWordType();  }
   public final boolean isReferenceType() throws VM_PragmaUninterruptible { return !isPrimitiveType(); }
   
  // Downcasting.
  public final VM_Class asClass() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(dimension == 0);
    return (VM_Class)this;
  }

  public final VM_Array asArray() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(dimension > 0);
    return (VM_Array)this;
  }

  public final VM_Primitive asPrimitive() throws VM_PragmaUninterruptible { 
    if (VM.VerifyAssertions) VM._assert(dimension < 0);
    return (VM_Primitive)this;
  }


  /**
   * Name of this type.
   * For a class, something like "java.lang.String".
   * For an array, something like "[I" or "[Ljava.lang.String;".
   * For a primitive, something like "int".
   */ 
  public abstract String getName();
   
  /** 
   * Descriptor for this type.
   * For a class, something like "Ljava/lang/String;".
   * For an array, something like "[I" or "[Ljava/lang/String;".
   * For a primitive, something like "I".
   */ 
  public final VM_Atom getDescriptor() throws VM_PragmaUninterruptible { return descriptor; }
   
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
  public abstract int getStackWords() throws VM_PragmaUninterruptible;

  /**
   * Number of [ in descriptor. 
   */ 
  public final int getDimensionality() throws VM_PragmaUninterruptible { return dimension; }

  /**
   * Index of this type in the type dictionary
   */ 
  public final int getDictionaryId() throws VM_PragmaUninterruptible { return dictionaryId; }
   
  /**
   * Redefine hashCode(), to allow use of consistent hash codes during
   * bootImage writing and run-time
   */
  public int hashCode() { return dictionaryId; }

  // Statistics by type
  public int allocCount; 		// number of objects of this type allocated
  public int allocBytes;                // total bytes of objs of this type allocated
  public int copyCount; 		
  public int copyBytes;                 
  public int scanCount; 		
  public int scanBytes;
  public int bootCount;
  public int bootBytes; 

   //-#if RVM_WITH_JMTK
   public Type JMTKtype = new Type();
   //-#endif

  /**
   * Load status.
   * If the class has been "loaded", 
   * then its declared member and field names are available.
   * Arrays and primitives are always treated as "loaded".
   */ 
  public final boolean isLoaded() throws VM_PragmaUninterruptible { return state >= CLASS_LOADED; }
   
  /**
   * Resolution status.
   * If the class/array has been "resolved", then size and offset information is
   * available by which the compiler can generate code to access this 
   * class/array's 
   * fields/methods via direct loads/stores/calls (rather than generating
   * code to access fields/methods symbolically, via dynamic linking stubs).
   * Primitives are always treated as "resolved".
   */ 
  public final boolean isResolved() throws VM_PragmaUninterruptible { return state >= CLASS_RESOLVED; }
   
  /**
   * Instantiation status.
   * If the class/array has been "instantiated", 
   * then all its methods have been compiled
   * and its type information block has been placed in the jtoc.
   * Primitives are always treated as "instantiated".
   */ 
  public final boolean isInstantiated() throws VM_PragmaUninterruptible { return state >= CLASS_INSTANTIATED; }
   
  /**
   * Initialization status.
   * If the class has been "initialized", 
   * then its <clinit> method has been executed.
   * Arrays have no <clinit> methods so they become 
   * "initialized" immediately upon "instantiation".
   * Primitives are always treated as "initialized".
   */ 
  public final boolean isInitialized() throws VM_PragmaUninterruptible { return state == CLASS_INITIALIZED; }

  /**
   * Cause loading to take place.
   * This will cause the .class file to be read.
   */
  public abstract void load() throws VM_ResolutionException;

  /**
   * Cause resolution to take place.
   * This will cause slots to be allocated in the jtoc.
   */ 
  public abstract void resolve() throws VM_ResolutionException;

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
  public abstract boolean hasFinalizer() throws VM_PragmaUninterruptible;

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
  public abstract Object[] getTypeInformationBlock() throws VM_PragmaUninterruptible;

  /**
   * Get the class loader for this type
   */
  public abstract ClassLoader getClassLoader();

  /**
   * Set the class loader for this type
   */
  public abstract void setClassLoader(ClassLoader classLoader);

  /**
   * get number of superclasses to Object 
   *   0 java.lang.Object, VM_Primitive, and VM_Classes that are interfaces
   *   1 for VM_Arrays and classes that extend Object directly
   */ 
  public final int getTypeDepth ()  throws VM_PragmaUninterruptible { return depth; };

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
   if (classForType == null && VM.runningVM) {

     // ensure that we load and resolve VM_Class before creating a 
     // java.lang.Class object for it.  Doing it here frees us from having
     // to check it all over the reflection code. 
     if (!isResolved()) {
       try {
	 load();
	 resolve();
       } catch (VM_ResolutionException e) {
	   e.printStackTrace( System.err );
	 throw new NoClassDefFoundError(e.getException().toString());
       }
     }
     synchronized(this) {
       if (classForType == null) {
	 classForType = java.lang.JikesRVMSupport.createClass(this);
       }
     }
   }
   return classForType;
  }

  // Frequently used types.
  //
  public static VM_Type VoidType;
  public static VM_Type BooleanType;
  public static VM_Type ByteType;
  public static VM_Type ShortType;
  public static VM_Type IntType;
  public static VM_Type LongType;
  public static VM_Type FloatType;
  public static VM_Type DoubleType;
  public static VM_Type CharType;

  /**
   * supertype of all types
   */
  public static VM_Type JavaLangObjectType;

  public static VM_Type JavaLangClassType;

  public static VM_Array JavaLangObjectArrayType;
  
  public static VM_Type NativeBridgeType;
  /**
   * supertype of all exception types
   */
  public static VM_Type JavaLangThrowableType; 
  /**
   * "final" type that gets special language treatment
   */
  public static VM_Type JavaLangStringType;    
  /**
   * all arrays are Cloneable, needed for type checking
   */
  public static VM_Class JavaLangCloneableType; 
  /**
   * all arrays are Serializable, needed for type checking
   */
  public static VM_Class JavaIoSerializableType; 
  /**
   * type used to extend java semantics for vm implementation
   */
  public static VM_Type MagicType;             
  /**
   * type used to represent machine words, addresses, differences of addresses
   */
  public static VM_Type WordType;             
  public static VM_Type AddressType;             
  public static VM_Type OffsetType;             
  /**
   * type used to represent code - array of INSTRUCTION
   */
  public static VM_Type CodeType;             
  /**
   * interface implemented to prevent compiler-inserted threadswitching
   */
  public static VM_Type UninterruptibleType;   
  /**
   * interface implemented to hint to the runtime system that an object
   * may be locked
   */
  public static VM_Type SynchronizedObjectType;   
  /**
   * interface implemented to save/restore appropriate registers 
   * during dynamic linking, etc.
   */
  public static VM_Type DynamicBridgeType;     
  /**
   * interface implemented to save various registers 
   * !!TODO: phase out in favor of preceeding line?
   */
  public static VM_Type SaveVolatileType;      

  // Convenience methods.
  //
  public final boolean isVoidType() throws VM_PragmaUninterruptible               { return this == VoidType;           }
  public final boolean isBooleanType() throws VM_PragmaUninterruptible           { return this == BooleanType;        }
  public final boolean isByteType() throws VM_PragmaUninterruptible              { return this == ByteType;           }
  public final boolean isShortType() throws VM_PragmaUninterruptible             { return this == ShortType;          }
  public final boolean isIntType() throws VM_PragmaUninterruptible               { return this == IntType;            }
  public final boolean isLongType() throws VM_PragmaUninterruptible              { return this == LongType;           }
  public final boolean isFloatType() throws VM_PragmaUninterruptible             { return this == FloatType;          }
  public final boolean isDoubleType() throws VM_PragmaUninterruptible            { return this == DoubleType;         }
  public final boolean isCharType() throws VM_PragmaUninterruptible              { return this == CharType;           }
  public final boolean isIntLikeType() throws VM_PragmaUninterruptible           { return isBooleanType() || isByteType() || isShortType() || isIntType() || isCharType() || isWordType(); }

  public final boolean isJavaLangObjectType() throws VM_PragmaUninterruptible    { return this == JavaLangObjectType;    }
  public final boolean isJavaLangThrowableType() throws VM_PragmaUninterruptible { return this == JavaLangThrowableType; }
  public final boolean isJavaLangStringType() throws VM_PragmaUninterruptible    { return this == JavaLangStringType;    }
  public final boolean isMagicType() throws VM_PragmaUninterruptible             { return this == MagicType;             }
  public final boolean isWordType() throws VM_PragmaUninterruptible              { return (this == WordType) ||
                                                                                          (this == AddressType) ||
										          (this == OffsetType);          }
  public final boolean isUninterruptibleType() throws VM_PragmaUninterruptible   { return this == UninterruptibleType;   }
  public final boolean isSynchronizedObjectType() throws VM_PragmaUninterruptible{ return this == SynchronizedObjectType;   }
  public final boolean isDynamicBridgeType() throws VM_PragmaUninterruptible     { return this == DynamicBridgeType;     }
  public final boolean isSaveVolatileType() throws VM_PragmaUninterruptible      { return this == SaveVolatileType;      }
  public final boolean isNativeBridgeType() throws VM_PragmaUninterruptible      { return this == NativeBridgeType;      }

  /**
   * Get array type corresponding to "this" array element type.
   */ 
  public final VM_Array getArrayTypeForElementType() {
    VM_Atom arrayDescriptor = getDescriptor().
      arrayDescriptorFromElementDescriptor();
    ClassLoader cl;
    return VM_ClassLoader.findOrCreateType(arrayDescriptor, getClassLoader()).asArray();
  }

  /**
   * get superclass id vector (@see VM_DynamicTypeCheck)
   */ 
  final short[] getSuperclassIds () throws VM_PragmaUninterruptible {
    return VM_Magic.objectAsShortArray(getTypeInformationBlock()
                                       [VM.TIB_SUPERCLASS_IDS_INDEX]);
  }

  /**
   * get doesImplement vector (@see VM_DynamicTypeCheck)
   */ 
   public final int[] getDoesImplement () throws VM_PragmaUninterruptible {
    return VM_Magic.objectAsIntArray(getTypeInformationBlock()[VM.TIB_DOES_IMPLEMENT_INDEX]);
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
  public final boolean isInBootImage() throws VM_PragmaUninterruptible {
    return inBootImage;
  }

  //----------------//
  // implementation //
  //----------------//

  private boolean inBootImage;

  /**
   * current class-loading stage of this type
   */
  int     state;        
  /**
   * descriptor for this type, 
   * something like "I" or "[I" or "Ljava/lang/String;"
   */
  protected VM_Atom descriptor;   
  /**
   * index into VM_TypeDictionary for this VM_Type
   */
  protected int     dictionaryId; 
  /**
   * index of jtoc slot that has type information block for this VM_Type
   */
  protected int     tibSlot;      
  /**
   * instance of java.lang.Class corresponding to this type 
   * (null --> not created yet
   */
  private   Class   classForType; 
  /**
   * RCGC: is this type acyclic? (public because VM_Type not Uninterruptable)
   */
  public    boolean acyclic;	   
  /**
   * -1 => primitive, 0 => Class/Interface, positive => array (number of [)
   */
  int     dimension;    
  /**
   * number of superclasses to Object
   */
  protected int     depth;        

  /**
   * At what offset is the thin lock word to be found in instances of
   * objects of this type?  A value of -1 indicates that the instances of
   * this type do not have inline thin locks. <p>
   * Accessed directly instead of via accessor function because this class is not Uninterruptible.
   * TODO: once we have method-level uninterruptibility make this protected and
   *       add appropriate accessor methods.
   */
  public int thinLockOffset = VM_ObjectModel.defaultThinLockOffset();

  static void init() {
    // create primitive type descriptions
    //
    VoidType    = VM_ClassLoader.findOrCreatePrimitiveType
      (VM_Atom.findOrCreateAsciiAtom("void"),    
       VM_Atom.findOrCreateAsciiAtom("V"));
    BooleanType = VM_ClassLoader.findOrCreatePrimitiveType
      (VM_Atom.findOrCreateAsciiAtom("boolean"), 
       VM_Atom.findOrCreateAsciiAtom("Z"));
    ByteType    = VM_ClassLoader.findOrCreatePrimitiveType
      (VM_Atom.findOrCreateAsciiAtom("byte"),    
       VM_Atom.findOrCreateAsciiAtom("B"));
    ShortType   = VM_ClassLoader.findOrCreatePrimitiveType
      (VM_Atom.findOrCreateAsciiAtom("short"),   
       VM_Atom.findOrCreateAsciiAtom("S"));
    IntType     = VM_ClassLoader.findOrCreatePrimitiveType
      (VM_Atom.findOrCreateAsciiAtom("int"),     
       VM_Atom.findOrCreateAsciiAtom("I"));
    LongType    = VM_ClassLoader.findOrCreatePrimitiveType
      (VM_Atom.findOrCreateAsciiAtom("long"),    
       VM_Atom.findOrCreateAsciiAtom("J"));
    FloatType   = VM_ClassLoader.findOrCreatePrimitiveType
      (VM_Atom.findOrCreateAsciiAtom("float"),   
       VM_Atom.findOrCreateAsciiAtom("F"));
    DoubleType  = VM_ClassLoader.findOrCreatePrimitiveType
      (VM_Atom.findOrCreateAsciiAtom("double"),  
       VM_Atom.findOrCreateAsciiAtom("D"));
    CharType    = VM_ClassLoader.findOrCreatePrimitiveType
      (VM_Atom.findOrCreateAsciiAtom("char"),    
       VM_Atom.findOrCreateAsciiAtom("C"));

    CodeType    =  VM_ClassLoader.findOrCreateType
      (VM_Atom.findOrCreateAsciiAtom(VM.INSTRUCTION_ARRAY_SIGNATURE), VM_SystemClassLoader.getVMClassLoader()).asArray();

    // create additional, frequently used, type descriptions
    //
    JavaLangObjectType    = VM_ClassLoader.findOrCreateType
      (VM_Atom.findOrCreateAsciiAtom("Ljava/lang/Object;"), VM_SystemClassLoader.getVMClassLoader());
    JavaLangClassType    = VM_ClassLoader.findOrCreateType
      (VM_Atom.findOrCreateAsciiAtom("Ljava/lang/Class;"), VM_SystemClassLoader.getVMClassLoader());
    JavaLangObjectArrayType = VM_ClassLoader.findOrCreateType
      (VM_Atom.findOrCreateAsciiAtom("[Ljava/lang/Object;"), VM_SystemClassLoader.getVMClassLoader()).asArray();
    JavaLangThrowableType = VM_ClassLoader.findOrCreateType
      (VM_Atom.findOrCreateAsciiAtom("Ljava/lang/Throwable;"), VM_SystemClassLoader.getVMClassLoader());
    JavaLangStringType    = VM_ClassLoader.findOrCreateType
      (VM_Atom.findOrCreateAsciiAtom("Ljava/lang/String;"), VM_SystemClassLoader.getVMClassLoader());
    JavaLangCloneableType = (VM_Class) VM_ClassLoader.findOrCreateType
      (VM_Atom.findOrCreateAsciiAtom("Ljava/lang/Cloneable;"), VM_SystemClassLoader.getVMClassLoader());
    JavaIoSerializableType = (VM_Class) VM_ClassLoader.findOrCreateType
      (VM_Atom.findOrCreateAsciiAtom("Ljava/io/Serializable;"), VM_SystemClassLoader.getVMClassLoader());
    MagicType             = VM_ClassLoader.findOrCreateType
      (VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_Magic;"), VM_SystemClassLoader.getVMClassLoader());
    UninterruptibleType   = VM_ClassLoader.findOrCreateType
      (VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_Uninterruptible;"), VM_SystemClassLoader.getVMClassLoader());
    SynchronizedObjectType = VM_ClassLoader.findOrCreateType 
      (VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_SynchronizedObject;"), VM_SystemClassLoader.getVMClassLoader());
    DynamicBridgeType     = VM_ClassLoader.findOrCreateType
      (VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_DynamicBridge;"), VM_SystemClassLoader.getVMClassLoader());
    SaveVolatileType      = VM_ClassLoader.findOrCreateType
      (VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_SaveVolatile;"), VM_SystemClassLoader.getVMClassLoader());
    NativeBridgeType      = VM_ClassLoader.findOrCreateType
      (VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_NativeBridge;"), VM_SystemClassLoader.getVMClassLoader());
    WordType           = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_Word;"), VM_SystemClassLoader.getVMClassLoader());
    AddressType           = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_Address;"), VM_SystemClassLoader.getVMClassLoader());
    OffsetType           = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_Offset;"), VM_SystemClassLoader.getVMClassLoader());

    VM_Array.init();

  }

  public final String toString() {
    return getName().toString();
  }

  /**
   * RCGC: Is a reference of this type contained 
   * in another object inherently acyclic?
   */ 
  protected boolean isAcyclicReference() {
    return acyclic;
  }
}
