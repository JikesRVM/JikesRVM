/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.classloader;

import org.jikesrvm.VM;
import org.vmmagic.pragma.*;
import org.jikesrvm.util.VM_HashSet;
import static org.jikesrvm.VM_SizeConstants.*;
/**
 * A class to represent the reference in a class file to some 
 * type (class, primitive or array).
 * A type reference is uniquely defined by
 * <ul>
 * <li> an initiating class loader
 * <li> a type name
 * </ul>
 * Resolving a VM_TypeReference to a VM_Type can
 * be an expensive operation.  Therefore we canonicalize
 * VM_TypeReference instances and cache the result of resolution.
 * <p>
 * It is officially illegal (as of July 31, 2003) 
 * to create a VM_TypeReference for a string that would not be syntactically
 * valid in a class file.   --Steven Augart
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 * @author Ian Rogers
 * @modified Steven Augart
 */
public final class VM_TypeReference {
  /**
   * The initiating class loader
   */
  final ClassLoader classloader;

  /**
   * The type name. For example, the primitive type int is "I", the
   * class java.lang.String is "Ljava/lang/String;"
   */
  final VM_Atom name;

  /**
   * The id of this type reference.
   */
  final int id;

  /**
   * The VM_Type instance that this type reference resolves to.
   * Null if the reference has not yet been resolved.
   */
  private VM_Type resolvedType;

  /**
   * Used to canonicalize TypeReferences
   */
  private static final VM_HashSet<VM_TypeReference> dictionary = new VM_HashSet<VM_TypeReference>();

  /**
   * Dictionary of all VM_TypeReference instances.
   */
  private static VM_TypeReference[] types = new VM_TypeReference[2500];

  /**
   * Used to assign Ids.  Id 0 is not used. Ids are compressed and
   * stored in the constant pool (See {@link VM_Class}).
   */
  private static int nextId = 1;
  
  public static final VM_TypeReference Void    = findOrCreate("V");
  public static final VM_TypeReference Boolean = findOrCreate("Z");
  public static final VM_TypeReference Byte    = findOrCreate("B");
  public static final VM_TypeReference Char    = findOrCreate("C");
  public static final VM_TypeReference Short   = findOrCreate("S");
  public static final VM_TypeReference Int     = findOrCreate("I");
  public static final VM_TypeReference Long    = findOrCreate("J");
  public static final VM_TypeReference Float   = findOrCreate("F");
  public static final VM_TypeReference Double  = findOrCreate("D");
  
  public static final VM_TypeReference BooleanArray = findOrCreate("[Z");
  public static final VM_TypeReference ByteArray    = findOrCreate("[B");
  public static final VM_TypeReference CharArray    = findOrCreate("[C");
  public static final VM_TypeReference ShortArray   = findOrCreate("[S");
  public static final VM_TypeReference IntArray     = findOrCreate("[I");
  public static final VM_TypeReference LongArray    = findOrCreate("[J");
  public static final VM_TypeReference FloatArray   = findOrCreate("[F");
  public static final VM_TypeReference DoubleArray  = findOrCreate("[D");
  
  public static final VM_TypeReference Word    = findOrCreate(org.vmmagic.unboxed.Word.class);
  public static final VM_TypeReference Address = findOrCreate(org.vmmagic.unboxed.Address.class);
  public static final VM_TypeReference ObjectReference = findOrCreate(org.vmmagic.unboxed.ObjectReference.class);
  public static final VM_TypeReference Offset  = findOrCreate(org.vmmagic.unboxed.Offset.class);
  public static final VM_TypeReference Extent  = findOrCreate(org.vmmagic.unboxed.Extent.class);
  public static final VM_TypeReference Code    = findOrCreate(VM.BuildForIA32 ? "Lorg/jikesrvm/ia32/VM_Code;":"Lorg/jikesrvm/ppc/VM_Code;");
  public static final VM_TypeReference WordArray = findOrCreate(org.vmmagic.unboxed.WordArray.class);
  public static final VM_TypeReference AddressArray = findOrCreate(org.vmmagic.unboxed.AddressArray.class);
  public static final VM_TypeReference ObjectReferenceArray = findOrCreate(org.vmmagic.unboxed.ObjectReferenceArray.class);
  public static final VM_TypeReference OffsetArray = findOrCreate(org.vmmagic.unboxed.OffsetArray.class);
  public static final VM_TypeReference ExtentArray = findOrCreate(org.vmmagic.unboxed.ExtentArray.class);
  public static final VM_TypeReference CodeArray = findOrCreate(org.jikesrvm.ArchitectureSpecific.VM_CodeArray.class);
  public static final VM_TypeReference Magic   = findOrCreate(org.jikesrvm.runtime.VM_Magic.class);
  public static final VM_TypeReference SysCall = findOrCreate(org.vmmagic.pragma.SysCall.class);

  public static final VM_TypeReference JavaLangObject = findOrCreate(java.lang.Object.class);
  public static final VM_TypeReference JavaLangClass = findOrCreate(java.lang.Class.class);
  public static final VM_TypeReference JavaLangString = findOrCreate(java.lang.String.class);
  public static final VM_TypeReference JavaLangCloneable = findOrCreate(java.lang.Cloneable.class);
  public static final VM_TypeReference JavaIoSerializable = findOrCreate(java.io.Serializable.class);

  public static final VM_TypeReference JavaLangObjectArray = findOrCreate(java.lang.Object[].class);

  public static final VM_TypeReference JavaLangThrowable = findOrCreate(java.lang.Throwable.class);
  public static final VM_TypeReference JavaLangError = findOrCreate(java.lang.Error.class);
  public static final VM_TypeReference JavaLangNullPointerException = findOrCreate(java.lang.NullPointerException.class);
  public static final VM_TypeReference JavaLangArrayIndexOutOfBoundsException = findOrCreate(java.lang.ArrayIndexOutOfBoundsException.class);
  public static final VM_TypeReference JavaLangArithmeticException = findOrCreate(java.lang.ArithmeticException.class);
  public static final VM_TypeReference JavaLangArrayStoreException = findOrCreate(java.lang.ArrayStoreException.class);
  public static final VM_TypeReference JavaLangClassCastException = findOrCreate(java.lang.ClassCastException.class);
  public static final VM_TypeReference JavaLangNegativeArraySizeException = findOrCreate(java.lang.NegativeArraySizeException.class);
  public static final VM_TypeReference JavaLangIllegalMonitorStateException = findOrCreate(java.lang.IllegalMonitorStateException.class);

  
  public static final VM_TypeReference VM_Processor = findOrCreate(org.jikesrvm.scheduler.VM_Processor.class);
  public static final VM_TypeReference VM_Type = findOrCreate(org.jikesrvm.classloader.VM_Type.class);
  public static final VM_TypeReference VM_Class = findOrCreate(org.jikesrvm.classloader.VM_Class.class);

  public static final VM_TypeReference NativeBridge = findOrCreate(org.vmmagic.pragma.NativeBridge.class);
  public static final VM_TypeReference DynamicBridge = findOrCreate(org.vmmagic.pragma.DynamicBridge.class);
  public static final VM_TypeReference SynchronizedObject = findOrCreate(org.vmmagic.pragma.SynchronizedObject.class);
  public static final VM_TypeReference SaveVolatile = findOrCreate(org.vmmagic.pragma.SaveVolatile.class);
  public static final VM_TypeReference Interruptible = findOrCreate(org.vmmagic.pragma.Interruptible.class);
  public static final VM_TypeReference LogicallyUninterruptible = findOrCreate(org.vmmagic.pragma.LogicallyUninterruptible.class);
  public static final VM_TypeReference NoOptCompile = findOrCreate(org.vmmagic.pragma.NoOptCompile.class);
  public static final VM_TypeReference Preemptible = findOrCreate(org.vmmagic.pragma.Preemptible.class);
  public static final VM_TypeReference UninterruptibleNoWarn = findOrCreate(org.vmmagic.pragma.UninterruptibleNoWarn.class);
  public static final VM_TypeReference Uninterruptible = findOrCreate(org.vmmagic.pragma.Uninterruptible.class);
  public static final VM_TypeReference Unpreemptible = findOrCreate(org.vmmagic.pragma.Unpreemptible.class);
  public static final VM_TypeReference Inline = findOrCreate(org.vmmagic.pragma.Inline.class);
  public static final VM_TypeReference NoInline = findOrCreate(org.vmmagic.pragma.NoInline.class);
  public static final VM_TypeReference BaselineNoRegisters = 
      VM.BuildForIA32 ? null : findOrCreate(org.vmmagic.pragma.BaselineNoRegisters.class);
  public static final VM_TypeReference BaselineSaveLSRegisters = 
      VM.BuildForIA32 ? null : findOrCreate(org.vmmagic.pragma.BaselineSaveLSRegisters.class);

  public static final VM_TypeReference VM_BaseAnnotation = findOrCreate(org.jikesrvm.classloader.VM_Annotation.BaseAnnotation.class);
  public static final VM_TypeReference VM_ReferenceMaps = findOrCreate(org.jikesrvm.compilers.baseline.VM_ReferenceMaps.class);
  public static final VM_TypeReference VM_JNIFunctions = findOrCreate(org.jikesrvm.jni.VM_JNIFunctions.class);
  
  public static final VM_TypeReference VM_CollectorThread = findOrCreate(org.jikesrvm.memorymanagers.mminterface.VM_CollectorThread.class);

  public static final VM_TypeReference VM_Array = findOrCreate(org.jikesrvm.classloader.VM_Array.class);

  // Synthetic types used by the opt compiler 
  public static final VM_TypeReference NULL_TYPE = (VM.BuildForOptCompiler) ? findOrCreate("Lorg/jikesrvm/classloader/VM_TypeReference$NULL;") : null;
  public static final VM_TypeReference VALIDATION_TYPE = (VM.BuildForOptCompiler) ? findOrCreate("Lorg/jikesrvm/classloader/VM_TypeReference$VALIDATION;") : null;
  
  public static final VM_TypeReference VM_ExceptionTable = (VM.BuildForOptCompiler) ? findOrCreate(org.jikesrvm.compilers.common.VM_ExceptionTable.class) : null; 

  public static final VM_TypeReference OPT_OptimizationPlanner = (VM.BuildForAdaptiveSystem) ? findOrCreate(org.jikesrvm.compilers.opt.OPT_OptimizationPlanner.class) : null; 

  /**
   * Hash value based on name, used for canonical type dictionary
   */
  public int hashCode() {
    return name.hashCode();
  }
  /**
   * Are two keys equivalent? Used for canonical type dictionary.
   * NB ignores id value
   */
  public boolean equals(Object other) {
    if (other instanceof VM_TypeReference) {
      VM_TypeReference that = (VM_TypeReference)other;
      return name == that.name && classloader.equals(that.classloader);
    } else {
      return false;
    }
  }

  /**
   * Find or create the canonical VM_TypeReference instance for
   * the given pair.
   *
   * @param cl the classloader (defining/initiating depending on usage)
   * @param tn the name of the type
   *
   * @throws IllegalArgumentException Needs to throw some kind of error in
   *  the case of a VM_Atom that does not represent a type name.
   */
  public static synchronized VM_TypeReference findOrCreate(ClassLoader cl, VM_Atom tn) 
    throws IllegalArgumentException // does not need to be declared
  {
    VM_TypeDescriptorParsing.validateAsTypeDescriptor(tn);
    // Primitives, arrays of primitives, system classes and arrays of system
    // classes must use the bootstrap classloader.  Force that here so we don't
    // have to worry about it anywhere else in the VM.
    ClassLoader bootstrapCL = VM_BootstrapClassLoader.getBootstrapClassLoader();
    if (cl == null) {
      cl = bootstrapCL;
    } else if (cl != bootstrapCL) {
      if (tn.isClassDescriptor()) {
        if (tn.isBootstrapClassDescriptor()) {
          cl = bootstrapCL;
        }
      } else if (tn.isArrayDescriptor()) {
        VM_Atom innermostElementType = tn.parseForInnermostArrayElementDescriptor();
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
   * @param tn type name
   */
  public static VM_TypeReference findOrCreate(String tn) {
    return findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                        VM_Atom.findOrCreateAsciiAtom(tn));
  }
  
  /**
   * Convert a java.lang.Class into a type reference the slow way. For
   * use in boot image writing
   * @param klass java.lang.Class to convert to typereference
   */
  public static VM_TypeReference findOrCreate(Class<?> klass) {
    if (VM.runningVM) {
      return java.lang.JikesRVMSupport.getTypeForClass(klass).getTypeRef();
    } else {
      String className = klass.getName();
      if (className.startsWith("[")) {
        // an array
        VM_Atom classAtom = VM_Atom.findOrCreateAsciiAtom(className.replace('.','/'));
        return findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(), classAtom);
      } else {
        // a class
        VM_Atom classAtom;
        if     (className.equals("int"))     return VM_TypeReference.Int;
        else if(className.equals("boolean")) return VM_TypeReference.Boolean;
        else if(className.equals("byte"))    return VM_TypeReference.Byte;
        else if(className.equals("char"))    return VM_TypeReference.Char;
        else if(className.equals("double"))  return VM_TypeReference.Double;
        else if(className.equals("float"))   return VM_TypeReference.Float;
        else if(className.equals("long"))    return VM_TypeReference.Long;
        else if(className.equals("short"))   return VM_TypeReference.Short;
        else if(className.equals("void"))    return VM_TypeReference.Void;
        else {
          classAtom = VM_Atom.findOrCreateAsciiAtom(className.replace('.','/'));
        }
        VM_Atom classDescriptor = classAtom.descriptorFromClassName();
        return findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(), classDescriptor);
      }
    }
  }

  /**
   * Find or create the canonical VM_TypeReference instance for
   * the given pair without type descriptor parsing.
   *
   * @param cl the classloader (defining/initiating depending on usage)
   * @param tn the name of the type
   */
  public static synchronized VM_TypeReference findOrCreateInternal (ClassLoader cl, VM_Atom tn) 
  {
    // Next actually findOrCreate the type reference using the proper classloader.
    VM_TypeReference key = new VM_TypeReference(cl, tn, nextId);
    VM_TypeReference val = dictionary.get(key);
    if (val == null) {
      // Create type reference
      val = key;
      nextId ++; // id of val is the nextId, move it along
      if (val.id >= types.length) {
        // Grow the array of types if necessary
        VM_TypeReference[] tmp = new VM_TypeReference[types.length + 500];
        System.arraycopy(types, 0, tmp, 0, types.length);
        types = tmp;
      }
      types[val.id] = val;
      dictionary.add(val);
    }
    return val;
  }

  /**
   * Constructor
   * @param cl the classloader
   * @param tn the type name
   * @param id the numeric identifier
   */
  private VM_TypeReference(ClassLoader cl, VM_Atom tn, int id) {
    classloader = cl;
    name = tn;
    this.id = id;
  }

  /**
   * Get the cannonical type reference given its id. The unused id of 0 will return null.
   * @param id the type references id
   * @return the type reference
   */
  @Uninterruptible
  public static VM_TypeReference getTypeRef(int id) { 
    return types[id];
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
  public VM_Atom getName() { 
    return name;
  }

  /**
   * Get the element type of for this array type
   */
  public VM_TypeReference getArrayElementType() {
    if (VM.VerifyAssertions) VM._assert(isArrayType());
    
    if (isWordArrayType()) {
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
      } else {
        if (VM.VerifyAssertions) VM._assert(false, "Unexpected case of Magic arrays!");
        return null;
      }
    } else if (isCodeArrayType()) {
      return Code;
    } else {
      return findOrCreate(classloader, name.parseForArrayElementDescriptor());
    }
  }

  /**
   * Get array type corresponding to "this" array element type.
   */ 
  public VM_TypeReference getArrayTypeForElementType() {
    VM_Atom arrayDescriptor = name.arrayDescriptorFromElementDescriptor();
    return findOrCreate(classloader, arrayDescriptor);
  }

  /**
   * Return the dimensionality of the type.
   * By convention, class types have dimensionality 0,
   * primitves -1, and arrays the number of [ in their descriptor.
   */
  public int getDimensionality() {
    if (isArrayType()) {
      VM_TypeReference elem = getArrayElementType();
      if (elem.isArrayType()) {
        // NOTE: we must recur instead of attempting to parse
        //       the array descriptor for ['s so we correctly handle
        //       [AddressArray etc. which actually has dimensionality 2!
        return 1 + elem.getDimensionality();
      } else {
        return 1;
      }
    } else if (isWordType() || isCodeType()) {
      return -1;
    } else if (isClassType()) {
      return 0;
    } else {
      return -1;
    }
  }

  /**
   * Return the innermost element type reference for an array
   */
  public VM_TypeReference getInnermostElementType() {
    VM_TypeReference elem = getArrayElementType();
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
   * Does 'this' refer to a class?
   */ 
  @Uninterruptible
  public boolean isClassType() { 
    return name.isClassDescriptor() &&
      !(isWordArrayType() || isWordType() || isCodeArrayType() || isCodeType());
  }
      
  /**
   * Does 'this' refer to an array?
   */ 
  @Uninterruptible
  public boolean isArrayType() { 
    return name.isArrayDescriptor() || isWordArrayType() || isCodeArrayType();
  }

  /**
   * Does 'this' refer to a primitive type
   */
  @Uninterruptible
  public boolean isPrimitiveType() { 
    return !(isArrayType() || isClassType());
  }

  /**
   * Does 'this' refer to a reference type
   */
  @Uninterruptible
  public boolean isReferenceType() { 
    return !isPrimitiveType();
  }

  /**
   * Does 'this' refer to Word, Address, Offset or Extent
   */
  @Uninterruptible
  public boolean isWordType() { 
    return this == Word || this == Offset || this == Address || this == Extent;
  }

  /**
   * Does 'this' refer to VM_Code
   */
  @Uninterruptible
  public boolean isCodeType() { 
    return this == Code;
  }

  /**
   * Does 'this' refer to WordArray, AddressArray, OffsetArray or ExtentArray
   */
  @Uninterruptible
  boolean isWordArrayType() { 
    return this == WordArray || this == OffsetArray || this == AddressArray || this == ObjectReferenceArray || this == ExtentArray;
  }

  /**
   * Does 'this' refer to VM_CodeArray
   */
  @Uninterruptible
  public boolean isCodeArrayType() { 
    return this == CodeArray;
  }

  /**
   * Does 'this' refer to VM_Magic?
   */
  public boolean isMagicType() {
    return this == Magic
      || this == ObjectReference || this == ObjectReferenceArray 
      || isWordType() || isWordArrayType() 
      || isCodeType() || isCodeArrayType();
  }

  /**
   * How many java stack/local words do value of this type take?
   */
  @Uninterruptible
  public int getStackWords() { 
    if(isResolved()) {
      // all primitive and magic types are resolved immediately
      return resolvedType.getStackWords();
    }
    else {
      // anything remaining must be a reference
      return 1;
    }
  }
    
  /**
   * How many bytes do values of this type take?
   */
  @Uninterruptible
  public int getMemoryBytes() {
    if(isResolved()) {
      // all primitive and magic types are resolved immediately
      return resolvedType.getMemoryBytes();
    }
    else {
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
   * Is this the type reference for the void primitive type?
   */
  @Uninterruptible
  public boolean isVoidType() { 
    return this == Void;
  }
  /**
   * Is this the type reference for the boolean primitive type?
   */
  @Uninterruptible
  public boolean isBooleanType() { 
    return this == Boolean;
  }
  /**
   * Is this the type reference for the byte primitive type?
   */
  @Uninterruptible
  public boolean isByteType() { 
    return this == Byte;
  }
  /**
   * Is this the type reference for the short primitive type?
   */
  @Uninterruptible
  public boolean isShortType() { 
    return this == Short;
  }
  /**
   * Is this the type reference for the char primitive type?
   */
  @Uninterruptible
  public boolean isCharType() { 
    return this == Char;
  }
  /**
   * Is this the type reference for the int primitive type?
   */
  @Uninterruptible
  public boolean isIntType() { 
    return this == Int;
  }
  /**
   * Is this the type reference for the long primitive type?
   */
  @Uninterruptible
  public boolean isLongType() { 
    return this == Long;
  }
  /**
   * Is this the type reference for the float primitive type?
   */
  @Uninterruptible
  public boolean isFloatType() { 
    return this == Float;
  }
  /**
   * Is this the type reference for the double primitive type?
   */
  @Uninterruptible
  public boolean isDoubleType() { 
    return this == Double;
  }
  /**
   * Is <code>this</code> the type reference for an 
   * int-like (1, 8, 16, or 32 bit integral) primitive type? 
   */
  @Uninterruptible
  public boolean isIntLikeType() { 
    return isBooleanType() || isByteType() || isCharType() 
      || isShortType() || isIntType();
  } 

  /**
   * Do this and that definitely refer to the different types?
   */
  public boolean definitelyDifferent(VM_TypeReference that) {
    if (this == that) return false;
    if (name != that.name) return true;
    VM_Type mine = peekResolvedType();
    VM_Type theirs = that.peekResolvedType();
    if (mine == null || theirs == null) return false;
    return mine != theirs;
  }

    
  /**
   * Do this and that definitely refer to the same type?
   */
  public boolean definitelySame(VM_TypeReference that) {
    if (VM.VerifyAssertions) VM._assert(that != null);
    if (this == that) return true;
    if (name != that.name) return false;
    VM_Type mine = peekResolvedType();
    VM_Type theirs = that.peekResolvedType();
    if (mine == null || theirs == null) return false;
    return mine == theirs;
  }

  /**
   * Has the type reference already been resolved into a type?
   */
  @Uninterruptible
  public boolean isResolved() { 
    return resolvedType != null;
  }

  /**
   * @return the current value of resolvedType -- null if not yet resolved.
   */
  @Uninterruptible
  public VM_Type peekResolvedType() { 
    return resolvedType;
  }

  /*
   * for use by VM_ClassLoader.defineClassInternal
   */
  void setResolvedType(VM_Type rt) {
    resolvedType = rt;
  }

  /** 
   * Force the resolution of the type reference. May cause class loading
   * if a required class file hasn't been loaded before.
   *
   * @return the VM_Type instance that this references resolves to.
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
   *        validate them as soon as we insert them into a VM_TypeReference.
   *        This stinks. XXX)
   */
  public VM_Type resolve() throws NoClassDefFoundError, 
                                        IllegalArgumentException {
   /*
    * Lock the classloader instead of this to avoid conflicting locking order.
    * Suppose we locked this, then one thread could call resolve(), locking this,
    * call classloader.loadClass(), trying to lock the classloader. Meanwhile,
    * another thread could call loadClass(), locking the classloader, then
    * try to resolve() the VM_TypeReference, resulting in a deadlock
    */
    synchronized (classloader) {
      return resolveInternal();
    }
  }

  private VM_Type resolveInternal() throws NoClassDefFoundError,
                                                     IllegalArgumentException {
    if (resolvedType != null) return resolvedType;
    if (isClassType()) {
      VM_Type ans; 
      if (VM.runningVM) {
        Class<?> klass;
        String myName = name.classNameFromDescriptor();
        try {
          klass = classloader.loadClass(myName);
        } catch (ClassNotFoundException cnf) {
          NoClassDefFoundError ncdfe 
            = new NoClassDefFoundError("Could not find the class " + myName + ":\n\t" + cnf.getMessage());
          ncdfe.initCause(cnf); // in dubious taste, but helps us debug Jikes
                                // RVM 
          throw ncdfe;
        }

        ans = java.lang.JikesRVMSupport.getTypeForClass(klass);
      } else {
        // Use a special purpose backdoor to avoid creating java.lang.Class
        // objects when not running the VM (we get host JDK Class objects
        // and that just doesn't work).
        ans = ((VM_BootstrapClassLoader)classloader).loadVMClass(name.classNameFromDescriptor());
      }
      if (VM.VerifyAssertions) 
        VM._assert(resolvedType == null || resolvedType == ans);
      setResolvedType(ans);
    } else if (isArrayType()) {
      if (isWordArrayType() || isCodeArrayType()) {
        // Ensure that we only create one VM_Array object for each pair of
        // names for this type. 
        // Do this by resolving AddressArray to [Address
        setResolvedType(getArrayElementType().getArrayTypeForElementType().resolve());
      } else {
        VM_Type elementType = getArrayElementType().resolve();
        if (elementType.getClassLoader() != classloader) {
          // We aren't the canonical type reference because the element type
          // was loaded using a different classloader. 
          // Find the canonical type reference and ask it to resolve itself.
          VM_TypeReference canonical = VM_TypeReference.findOrCreate(elementType.getClassLoader(), name);
          setResolvedType(canonical.resolve());
        } else {
          setResolvedType(new VM_Array(this, elementType));
        }
      }
    } else {
      setResolvedType(VM_Primitive.createPrimitive(this));
    }
    return resolvedType;
  }

  public String toString() {
    return "< " + classloader + ", "+ name + " >";
  }
}
