/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.*;
import org.vmmagic.pragma.*;
import java.util.HashMap;

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
 * @modified Steven Augart
 */
public class VM_TypeReference implements VM_SizeConstants {
  /**
   * Used to canonicalize TypeReferences
   */
  private static HashMap dictionary = new HashMap();

  /**
   * Dictionary of all VM_TypeReference instances.
   */
  private static VM_TypeReference[] types = new VM_TypeReference[2000];

  /**
   * Used to assign ids.  Id 0 is not used.
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
  
  public static final VM_TypeReference Word    = findOrCreate("Lorg/vmmagic/unboxed/Word;");
  public static final VM_TypeReference Address = findOrCreate("Lorg/vmmagic/unboxed/Address;");
  public static final VM_TypeReference ObjectReference = findOrCreate("Lorg/vmmagic/unboxed/ObjectReference;");
  public static final VM_TypeReference Offset  = findOrCreate("Lorg/vmmagic/unboxed/Offset;");
  public static final VM_TypeReference Extent  = findOrCreate("Lorg/vmmagic/unboxed/Extent;");
  public static final VM_TypeReference Code    = findOrCreate("Lcom/ibm/JikesRVM/VM_Code;");
  public static final VM_TypeReference WordArray = findOrCreate("Lorg/vmmagic/unboxed/WordArray;");
  public static final VM_TypeReference AddressArray = findOrCreate("Lorg/vmmagic/unboxed/AddressArray;");
  public static final VM_TypeReference ObjectReferenceArray = findOrCreate("Lorg/vmmagic/unboxed/ObjectReferenceArray;");
  public static final VM_TypeReference OffsetArray = findOrCreate("Lorg/vmmagic/unboxed/OffsetArray;");
  public static final VM_TypeReference ExtentArray = findOrCreate("Lorg/vmmagic/unboxed/ExtentArray;");
  public static final VM_TypeReference CodeArray = findOrCreate("Lcom/ibm/JikesRVM/VM_CodeArray;");
  public static final VM_TypeReference Magic   = findOrCreate("Lcom/ibm/JikesRVM/VM_Magic;");
  public static final VM_TypeReference SysCall = findOrCreate("Lcom/ibm/JikesRVM/VM_SysCall;");

  public static final VM_TypeReference JavaLangObject = findOrCreate("Ljava/lang/Object;");
  public static final VM_TypeReference JavaLangClass = findOrCreate("Ljava/lang/Class;");
  public static final VM_TypeReference JavaLangString = findOrCreate("Ljava/lang/String;");
  public static final VM_TypeReference JavaLangCloneable = findOrCreate("Ljava/lang/Cloneable;");
  public static final VM_TypeReference JavaIoSerializable = findOrCreate("Ljava/io/Serializable;");

  public static final VM_TypeReference JavaLangObjectArray = findOrCreate("[Ljava/lang/Object;");

  public static final VM_TypeReference JavaLangThrowable = findOrCreate("Ljava/lang/Throwable;");
  public static final VM_TypeReference JavaLangError = findOrCreate("Ljava/lang/Error;");
  public static final VM_TypeReference JavaLangNullPointerException = findOrCreate("Ljava/lang/NullPointerException;");
  public static final VM_TypeReference JavaLangArrayIndexOutOfBoundsException = findOrCreate("Ljava/lang/ArrayIndexOutOfBoundsException;");
  public static final VM_TypeReference JavaLangArithmeticException = findOrCreate("Ljava/lang/ArithmeticException;");
  public static final VM_TypeReference JavaLangArrayStoreException = findOrCreate("Ljava/lang/ArrayStoreException;");
  public static final VM_TypeReference JavaLangClassCastException = findOrCreate("Ljava/lang/ClassCastException;");
  public static final VM_TypeReference JavaLangNegativeArraySizeException = findOrCreate("Ljava/lang/NegativeArraySizeException;");
  public static final VM_TypeReference JavaLangIllegalMonitorStateException = findOrCreate("Ljava/lang/IllegalMonitorStateException;");

  
  public static final VM_TypeReference VM_Processor = findOrCreate("Lcom/ibm/JikesRVM/VM_Processor;");
  public static final VM_TypeReference VM_Type = findOrCreate("Lcom/ibm/JikesRVM/classloader/VM_Type;");
  public static final VM_TypeReference VM_Class = findOrCreate("Lcom/ibm/JikesRVM/classloader/VM_Class;");
  public static final VM_TypeReference VM_Array = findOrCreate("Lcom/ibm/JikesRVM/classloader/VM_Array;");

  //-#if RVM_WITH_OPT_COMPILER
  // Synthetic types used by the opt compiler 
  public static final VM_TypeReference NULL_TYPE = findOrCreate("Lcom/ibm/JikesRVM/VM_TypeReference$NULL;");
  public static final VM_TypeReference VALIDATION_TYPE = findOrCreate("Lcom/ibm/JikesRVM/VM_TypeReference$VALIDATION;");
  //-#endif

  /**
   * The initiating class loader
   */
  protected final ClassLoader classloader;

  /**
   * The type name
   */
  protected final VM_Atom name;

  /**
   * The id of thie type reference.
   */
  protected int id;

  /**
   * The VM_Type instance that this type reference resolves to.
   * Null if the reference has not yet been resolved.
   */
  protected VM_Type resolvedType;

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
    if (cl != bootstrapCL) {
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
    // Next actually findOrCreate the type reference using the proper classloader.
    VM_TypeReference key = new VM_TypeReference(cl, tn);
    VM_TypeReference val = (VM_TypeReference)dictionary.get(key);
    if (val != null)  return val;
    key.id = nextId++;
    if (key.id == types.length) {
      VM_TypeReference[] tmp = new VM_TypeReference[types.length + 500];
      System.arraycopy(types, 0, tmp, 0, types.length);
      types = tmp;
    }
    types[key.id] = key;
    dictionary.put(key, key);
    return key;
  }

  /**
   * Shorthand for doing a find or create for a type reference that should
   * be created using the bootstrap classloader.
   */
  public static VM_TypeReference findOrCreate(String tn) {
    return findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                        VM_Atom.findOrCreateAsciiAtom(tn));
  }

  public static VM_TypeReference getTypeRef(int id) throws UninterruptiblePragma {
    return types[id];
  }

  /**
   * @param cl the classloader
   * @param tn the type name
   */
  protected VM_TypeReference(ClassLoader cl, VM_Atom tn) {
    classloader = cl;
    name = tn;
  }

  /**
   * @return the classloader component of this type reference
   */
  public final ClassLoader getClassLoader() throws UninterruptiblePragma {
    return classloader;
  }
      
  /**
   * @return the type name component of this type reference
   */
  public final VM_Atom getName() throws UninterruptiblePragma {
    return name;
  }

  /**
   * Get the element type of for this array type
   */
  public final VM_TypeReference getArrayElementType() {
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
  public final VM_TypeReference getArrayTypeForElementType() {
    VM_Atom arrayDescriptor = name.arrayDescriptorFromElementDescriptor();
    return findOrCreate(classloader, arrayDescriptor);
  }

  /**
   * Return the dimensionality of the type.
   * By convention, class types have dimensionality 0,
   * primitves -1, and arrays the number of [ in their descriptor.
   */
  public final int getDimensionality() {
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
  public final VM_TypeReference getInnermostElementType() {
    if (isWordArrayType() || isCodeArrayType()) {
      return getArrayElementType();
    } else {
      return findOrCreate(classloader, name.parseForInnermostArrayElementDescriptor());
    }
  }

  /**
   * Does 'this' refer to a class?
   */ 
  public final boolean isClassType() throws UninterruptiblePragma {
    return name.isClassDescriptor() &&
      !(isWordArrayType() || isWordType() || isCodeArrayType() || isCodeType());
  }
      
  /**
   * Does 'this' refer to an array?
   */ 
  public final boolean isArrayType() throws UninterruptiblePragma {
    return name.isArrayDescriptor() || isWordArrayType() || isCodeArrayType();
  }

  /**
   * Does 'this' refer to a primitive type
   */
  public final boolean isPrimitiveType() throws UninterruptiblePragma {
    return !(isArrayType() || isClassType());
  }

  /**
   * Does 'this' refer to a reference type
   */
  public final boolean isReferenceType() throws UninterruptiblePragma {
    return !isPrimitiveType();
  }

  /**
   * Does 'this' refer to Word, Address, Offset or Extent
   */
  public final boolean isWordType() throws UninterruptiblePragma {
    return this == Word || this == Offset || this == Address || this == Extent;
  }

  /**
   * Does 'this' refer to VM_Code
   */
  public final boolean isCodeType() throws UninterruptiblePragma {
    return this == Code;
  }

  /**
   * Does 'this' refer to WordArray, AddressArray, OffsetArray or ExtentArray
   */
  final boolean isWordArrayType() throws UninterruptiblePragma {
    return this == WordArray || this == OffsetArray || this == AddressArray || this == ObjectReferenceArray || this == ExtentArray;
  }

  /**
   * Does 'this' refer to VM_CodeArray
   */
  public final boolean isCodeArrayType() throws UninterruptiblePragma {
    return this == CodeArray;
  }

  /**
   * Does 'this' refer to VM_Magic?
   */
  public final boolean isMagicType() {
    return this == Magic || this == SysCall 
      || this == ObjectReference || this == ObjectReferenceArray 
      || isWordType() || isWordArrayType() 
      || isCodeType() || isCodeArrayType();
  }

  /**
   * How many java stack/local words do value of this type take?
   */
  public final int getStackWords() throws UninterruptiblePragma {
    if (this == Long || this == Double) return 2;
    if (this == Void) return 0;
    return 1;
  }
    
  /**
   * How many bytes of memory words do value of this type take?
   */
  public final int getSize() throws UninterruptiblePragma {
    if (isReferenceType() || isWordType()) return BYTES_IN_ADDRESS; 
    if (this == Long || this == Double) return BYTES_IN_LONG;
    if (this == Void) return 0;
    if (this == Code) return VM.BuildForIA32 ? BYTES_IN_BYTE : BYTES_IN_INT;
    return BYTES_IN_INT; //all int like types 
  }
    
  /**
   * @return the id to use for this type
   */
  public final int getId() throws UninterruptiblePragma {
    return id;
  }

  /**
   * Is this the type reference for the void primitive type?
   */
  public final boolean isVoidType() throws UninterruptiblePragma { 
    return this == Void;
  }
  /**
   * Is this the type reference for the boolean primitive type?
   */
  public final boolean isBooleanType() throws UninterruptiblePragma { 
    return this == Boolean;
  }
  /**
   * Is this the type reference for the byte primitive type?
   */
  public final boolean isByteType() throws UninterruptiblePragma { 
    return this == Byte;
  }
  /**
   * Is this the type reference for the short primitive type?
   */
  public final boolean isShortType() throws UninterruptiblePragma { 
    return this == Short;
  }
  /**
   * Is this the type reference for the char primitive type?
   */
  public final boolean isCharType() throws UninterruptiblePragma {
    return this == Char;
  }
  /**
   * Is this the type reference for the int primitive type?
   */
  public final boolean isIntType() throws UninterruptiblePragma {
    return this == Int;
  }
  /**
   * Is this the type reference for the long primitive type?
   */
  public final boolean isLongType() throws UninterruptiblePragma { 
    return this == Long;
  }
  /**
   * Is this the type reference for the float primitive type?
   */
  public final boolean isFloatType() throws UninterruptiblePragma { 
    return this == Float;
  }
  /**
   * Is this the type reference for the double primitive type?
   */
  public final boolean isDoubleType() throws UninterruptiblePragma { 
    return this == Double;
  }
  /**
   * Is <code>this</code> the type reference for an 
   * int-like (1, 8, 16, or 32 bit integral) primitive type? 
   */
  public final boolean isIntLikeType() throws UninterruptiblePragma { 
    return isBooleanType() || isByteType() || isCharType() 
      || isShortType() || isIntType();
  } 

  /**
   * Do this and that definitely refer to the different types?
   */
  public final boolean definitelyDifferent(VM_TypeReference that) {
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
  public final boolean definitelySame(VM_TypeReference that) {
    if (that == null) System.out.println("that is null :)");
    if (this == null) System.out.println("this is null :)");
    if (this == that) return true;
    if (name != that.name) return false;
    VM_Type mine = peekResolvedType();
    VM_Type theirs = that.peekResolvedType();
    if (mine == null || theirs == null) return false;
    return mine == theirs;
  }

  /**
   * Has the field reference already been resolved into a target method?
   */
  public final boolean isResolved() {
    return resolvedType != null;
  }

  /**
   * @return the current value of resolvedType -- null if not yet resolved.
   */
  public final VM_Type peekResolvedType() throws UninterruptiblePragma {
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
  public final synchronized VM_Type resolve() throws NoClassDefFoundError, 
                                                     IllegalArgumentException {
    if (resolvedType != null) return resolvedType;
    if (isClassType()) {
      VM_Type ans; 
      if (VM.runningVM) {
        Class klass;
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
      resolvedType = ans;
    } else if (isArrayType()) {
      if (isWordArrayType() || isCodeArrayType()) {
        // Ensure that we only create one VM_Array object for each pair of
        // names for this type. 
        // Do this by resolving AddressArray to [Address
        resolvedType = getArrayElementType().getArrayTypeForElementType().resolve();
      } else {
        VM_Type elementType = getArrayElementType().resolve();
        if (elementType.getClassLoader() != classloader) {
          // We aren't the canonical type reference because the element type
          // was loaded using a different classloader. 
          // Find the canonical type reference and ask it to resolve itself.
          VM_TypeReference canonical = VM_TypeReference.findOrCreate(elementType.getClassLoader(), name);
          resolvedType = canonical.resolve();
        } else {
          resolvedType = new VM_Array(this, elementType);
        }
      }
    } else {
      resolvedType = new VM_Primitive(this);
    }
    return resolvedType;
  }

  public final int hashCode() {
    return name.hashCode();
  }

  public final boolean equals(Object other) {
    if (other instanceof VM_TypeReference) {
      VM_TypeReference that = (VM_TypeReference)other;
      return name == that.name && classloader.equals(that.classloader);
    } else {
      return false;
    }
  }

  public final String toString() {
    return "< " + classloader + ", "+ name + " >";
  }
}
