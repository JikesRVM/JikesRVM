/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id:

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
   public final int getTibSlot() { return tibSlot; }

   /**
    * Get offset of tib slot from start of jtoc, in bytes.
    */ 
   public final int getTibOffset() { return tibSlot << 2; }

   /**
    * Deprecated.
    *!!TODO: remove this when cleanup complete. [--DL]
    */
   public final int getOffset() { return getTibOffset(); }

   // Classification.
   //
   public final boolean isClassType()     { return dimension == 0; } 
   public final boolean isArrayType()     { return dimension > 0;  }
   public final boolean isPrimitiveType() { return (dimension < 0) || isAddressType();  }
   public final boolean isReferenceType() { return !isPrimitiveType(); }
   
  // Downcasting.
  public final VM_Class asClass() { 
    if (VM.VerifyAssertions) VM.assert(dimension == 0);
    return (VM_Class)this;
  }

  public final VM_Array asArray() { 
    if (VM.VerifyAssertions) VM.assert(dimension > 0);
    return (VM_Array)this;
  }

  public final VM_Primitive asPrimitive() { 
    if (VM.VerifyAssertions) VM.assert(dimension < 0);
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
  public final VM_Atom getDescriptor() { return descriptor; }
   
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
  public abstract int getStackWords();

  /**
   * Number of [ in descriptor. 
   */ 
  public final int getDimensionality() { return dimension; }

  /**
   * Index of this type in the type dictionary
   */ 
  public final int getDictionaryId() { return dictionaryId; }
   
  /**
   * Redefine hashCode(), to allow use of consistent hash codes during
   * bootImage writing and run-time
   */
  public int hashCode() { return dictionaryId; }

  public int liveCount; 		// field for statistics: instance cnt
  public int liveSpace;                // field for statistics: space
 
  /**
   * Load status.
   * If the class has been "loaded", 
   * then its declared member and field names are available.
   * Arrays and primitives are always treated as "loaded".
   */ 
  public final boolean isLoaded() { return state >= CLASS_LOADED; }
   
  /**
   * Resolution status.
   * If the class/array has been "resolved", then size and offset information is
   * available by which the compiler can generate code to access this 
   * class/array's 
   * fields/methods via direct loads/stores/calls (rather than generating
   * code to access fields/methods symbolically, via dynamic linking stubs).
   * Primitives are always treated as "resolved".
   */ 
  public final boolean isResolved() { return state >= CLASS_RESOLVED; }
   
  /**
   * Instantiation status.
   * If the class/array has been "instantiated", 
   * then all its methods have been compiled
   * and its type information block has been placed in the jtoc.
   * Primitives are always treated as "instantiated".
   */ 
  public final boolean isInstantiated() { return state >= CLASS_INSTANTIATED; }
   
  /**
   * Initialization status.
   * If the class has been "initialized", 
   * then its <clinit> method has been executed.
   * Arrays have no <clinit> methods so they become 
   * "initialized" immediately upon "instantiation".
   * Primitives are always treated as "initialized".
   */ 
  public final boolean isInitialized() { return state == CLASS_INITIALIZED; }

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
  public abstract boolean hasFinalizer();

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
  public abstract Object[] getTypeInformationBlock();

  /**
   * get number of superclasses to Object 
   *   0 java.lang.Object, VM_Primitive, and VM_Classes that are interfaces
   *   1 for VM_Arrays and classes that extend Object directly
   */ 
  public final int getTypeDepth () { return depth; };

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
	 synchronized(VM_ClassLoader.lock) {
	   load();
	   resolve();
	 }
       } catch (VM_ResolutionException e) {
	 throw new NoClassDefFoundError(e.getException().toString());
       }
     }
     synchronized(this) {
       if (classForType == null) {
	 classForType = Class.create(this);
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
  static VM_Type JavaLangThrowableType; 
  /**
   * "final" type that gets special language treatment
   */
  static VM_Type JavaLangStringType;    
  /**
   * all arrays are Cloneable, needed for type checking
   */
  static VM_Class JavaLangCloneableType; 
  /**
   * all arrays are Serializable, needed for type checking
   */
  static VM_Class JavaIoSerializableType; 
  /**
   * type used to extend java semantics for vm implementation
   */
  static VM_Type MagicType;             
  /**
   * type used to represent machine addresses
   */
  static VM_Type AddressType;             
  /**
   * type used to represent code - array of INSTRUCTION
   */
  static VM_Type CodeType;             
  /**
   * interface implemented to prevent compiler-inserted threadswitching
   */
  static VM_Type UninterruptibleType;   
  /**
   * interface implemented to hint to the runtime system that an object
   * may be locked
   */
  static VM_Type SynchronizedObjectType;   
  /**
   * interface implemented to save/restore appropriate registers 
   * during dynamic linking, etc.
   */
  static VM_Type DynamicBridgeType;     
  /**
   * interface implemented to save various registers 
   * !!TODO: phase out in favor of preceeding line?
   */
  static VM_Type SaveVolatileType;      

  // Convenience methods.
  //
  public final boolean isVoidType()              { return this == VoidType;           }
  public final boolean isBooleanType()           { return this == BooleanType;        }
  public final boolean isByteType()              { return this == ByteType;           }
  public final boolean isShortType()             { return this == ShortType;          }
  public final boolean isIntType()               { return this == IntType;            }
  public final boolean isLongType()              { return this == LongType;           }
  public final boolean isFloatType()             { return this == FloatType;          }
  public final boolean isDoubleType()            { return this == DoubleType;         }
  public final boolean isCharType()              { return this == CharType;           }
  public final boolean isIntLikeType()           { return isBooleanType() || isByteType() || isShortType() || isIntType() || isCharType(); }

  public final boolean isJavaLangObjectType()    { return this == JavaLangObjectType;    }
  public final boolean isJavaLangThrowableType() { return this == JavaLangThrowableType; }
  public final boolean isJavaLangStringType()    { return this == JavaLangStringType;    }
  public final boolean isMagicType()             { return this == MagicType;             }
  public final boolean isAddressType()           { return this == AddressType;           }
  public final boolean isUninterruptibleType()   { return this == UninterruptibleType;   }
  public final boolean isSynchronizedObjectType(){ return this == SynchronizedObjectType;   }
  public final boolean isDynamicBridgeType()     { return this == DynamicBridgeType;     }
  public final boolean isSaveVolatileType()      { return this == SaveVolatileType;      }
  public final boolean isNativeBridgeType()      { return this == NativeBridgeType;      }

  /**
   * Get array type corresponding to "this" array element type.
   */ 
  public final VM_Array getArrayTypeForElementType() {
    VM_Atom arrayDescriptor = getDescriptor().
      arrayDescriptorFromElementDescriptor();
    return VM_ClassLoader.findOrCreateType(arrayDescriptor).asArray();
  }

  /**
   * get superclass id vector (see VM_DynamicTypeCheck)
   */ 
  final short[] getSuperclassIds () {
    if (VM.VerifyAssertions) VM.assert(VM.BuildForFastDynamicTypeCheck);
    return VM_Magic.objectAsShortArray(getTypeInformationBlock()
                                       [VM.TIB_SUPERCLASS_IDS_INDEX]);
  }

  /**
   * get doesImplement vector (@see VM_DynamicTypeCheck)
   */ 
  final int[] getDoesImplement () {
    if (VM.VerifyAssertions) VM.assert(VM.BuildForFastDynamicTypeCheck);
    return VM_Magic.objectAsIntArray(getTypeInformationBlock()[VM.TIB_DOES_IMPLEMENT_INDEX]);
  }

  /**
   * May a variable of "this" type be assigned a value of "that" type?
   * @param that type of object to be assigned to "this"
   * @return   true  --> assignment is legal
   *           false --> assignment is illegal
   */ 
  public final boolean isAssignableWith(VM_Type that) 
    throws VM_ResolutionException {
    return isAssignableWith(this, that);
  }
   
  /**
   * Simple graph-based type checker.
   * Replaced by methods of VM_DynamicTypeChecking.
   */ 
  static boolean isAssignableWith(VM_Type lhs, 
				  VM_Type rhs) throws VM_ResolutionException {
    // check trivial case first
    //
    if (lhs == rhs)
      return true;
         
    // check that array element types (if any) match
    //
    while (lhs.isArrayType() && rhs.isArrayType()) {
      lhs.load();
      lhs.resolve();

      rhs.load();
      rhs.resolve();

      lhs = lhs.asArray().getElementType();
      rhs = rhs.asArray().getElementType();
    }

    // if one element is itself an array, then the only legal
    // possibilities are <Object> := <array> and <Cloneable> := <array>
    // and <java.io.Serializable> := array
    //
    if (rhs.isArrayType()) {
      return (lhs == VM_Type.JavaLangObjectType) 
        || (lhs == VM_Type.JavaLangCloneableType) 
        || (lhs == VM_Type.JavaIoSerializableType);
    }

    if (lhs.isArrayType())
      return false;

    // if one element is a primitive, then 
    // the other must be a primitive of the same type
    //
    if (lhs.isPrimitiveType() || rhs.isPrimitiveType())
      return false;

    // at this point, we know that rhs and lhs are both classes
    //
    if (VM.VerifyAssertions) VM.assert(lhs.isClassType() && rhs.isClassType());

    lhs.load();
    lhs.resolve();

    rhs.load();
    rhs.resolve();

    if (lhs.asClass().isInterface()) { 
      // rhs (or one of its superclasses/superinterfaces) must 
      // implement interface of lhs
      if (lhs == rhs) return true;
      VM_Class Y = rhs.asClass();
      VM_Class I = lhs.asClass();
      while (Y != null && !explicitImplementsTest(I, Y)) {
	Y = Y.getSuperClass();
      }
      return (Y != null);
    } else { 
      // rhs must be same class as lhs, or a subclass of it
      while (rhs != null) {
	if (lhs == rhs) return true;
	rhs = rhs.asClass().getSuperClass();
      }
      return false;
    }
  }

   private static boolean explicitImplementsTest (VM_Class I, VM_Class J) throws VM_ResolutionException {
     VM_Class [] superInterfaces = J.getDeclaredInterfaces();
     if (superInterfaces == null) return false;
     for (int i=0; i<superInterfaces.length; i++) {
       VM_Class superInterface = superInterfaces[i];
       if (!superInterface.isInterface()) throw new VM_ResolutionException(superInterface.getDescriptor(), new IncompatibleClassChangeError());
       if (I==superInterface || explicitImplementsTest(I, superInterface)) return true;
     }
     return false;
   }
       
  //----------------//
  // implementation //
  //----------------//

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

    //-#if RVM_FOR_POWERPC
    CodeType    =  VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[I")).asArray();
    //-#endif
    //-#if RVM_FOR_IA32
    CodeType    =  VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[B")).asArray();
    //-#endif

    // create additional, frequently used, type descriptions
    //
    JavaLangObjectType    = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("Ljava/lang/Object;"));
    JavaLangClassType     = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("Ljava/lang/Class;"));
    JavaLangObjectArrayType = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("[Ljava/lang/Object;")).asArray();
    JavaLangThrowableType = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("Ljava/lang/Throwable;"));
    JavaLangStringType    = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("Ljava/lang/String;"));
    JavaLangCloneableType = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("Ljava/lang/Cloneable;")).asClass();
    JavaIoSerializableType = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("Ljava/io/Serializable;")).asClass();
    MagicType             = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("LVM_Magic;"));
    UninterruptibleType   = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("LVM_Uninterruptible;"));
    SynchronizedObjectType = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("LVM_SynchronizedObject;"));
    DynamicBridgeType     = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("LVM_DynamicBridge;"));
    SaveVolatileType      = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("LVM_SaveVolatile;"));
    NativeBridgeType      = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("LVM_NativeBridge;"));
    AddressType           = VM_ClassLoader.findOrCreateType (VM_Atom.findOrCreateAsciiAtom("LVM_Address;"));
  }

  public String toString() {
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
