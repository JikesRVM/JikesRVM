/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;

import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

//-#if RVM_WITH_OPT_COMPILER
import com.ibm.JikesRVM.opt.*;
//-#endif

/**
 *  Description of a java "class" type.
 * 
 * <p> This description is read from a ".class" file as classes/field/methods
 * referenced by the running program need to be bound in to the running image.
 * 
 * @see VM_Type
 * @see VM_Array
 * @see VM_Primitive
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public final class VM_Class extends VM_Type implements VM_Constants, 
                                                       VM_ClassLoaderConstants {

  // for closed world testing
  public static boolean classLoadingDisabled = false;

  // Cannonical empty arrays
  private static final VM_Class[] emptyVMClass = new VM_Class[0];
  private static final VM_Field[] emptyVMField = new VM_Field[0];
  private static final VM_Method[] emptyVMMethod = new VM_Method[0];

  private int[]        constantPool;
  private int          modifiers;
  private VM_Class     superClass;
  private VM_Class[]   subClasses;
  private VM_Class[]   declaredInterfaces;
  private VM_Field[]   declaredFields;
  private VM_Method[]  declaredMethods;
  private VM_TypeReference[] declaredClasses; // declared inner classes, may be null
  private VM_TypeReference declaringClass; // the outerclass, or null if this is not a inner/nested class
  private VM_Atom      sourceName;
  private VM_Method    classInitializerMethod;
  //
  // The following are valid only when "state >= CLASS_RESOLVED".
  //

  // --- Field size and offset information --- //
  //
  /**
   * fields shared by all instances of class
   */
  private VM_Field[]   staticFields;           
  /**
   * fields distinct for each instance of class
   */
  private VM_Field[]   instanceFields;         
  /**
   * total size of per-instance data, in bytes
   */
  private int          instanceSize;  
  /**
   * offsets of reference-containing instance fields
   */
  private int[]        referenceOffsets;       
  /**
   * offset of hole due to alignment, zero if none
   */
  private int emptySlot = 0;
  /**
   * The desired alignment for instances of this type.
   */
  private int alignment;
  

  //
  // --- Method-dispatching information ---    //
  //
  /**
   * static methods of class
   */
  private VM_Method[]  staticMethods;          
  /**
   * static methods of class
   */
  private VM_Method[]  constructorMethods;          
  /**
   * virtual methods of class
   */
  private VM_Method[]  virtualMethods;         

  /**
   * method that overrides java.lang.Object.finalize()
   * null => class does not have a finalizer
   */
  private VM_Method finalizeMethod;         

  /**
   * type and virtual method dispatch table for class
   */
  private Object[] typeInformationBlock;   


  /**
   * Name - something like "java.lang.String".
   */ 
  public final String toString() {
    return getDescriptor().classNameFromDescriptor();
  }

  /**
   * Package name - something like "java.lang".
   * Returns the empty string if the class is a member of the unnamed package.
   */
  public final String getPackageName() {
    String className = toString();
    int lastDot = className.lastIndexOf(".");
    return (lastDot >= 0) ? className.substring(0, lastDot) : "";
  }

  /**
   * Stack space requirement.
  */
  public final int getStackWords() throws VM_PragmaUninterruptible {
    return 1;
  }

  /**
   * An "interface" description rather than a "class" description?
   */ 
  public final boolean isInterface() throws VM_PragmaUninterruptible { 
    return (modifiers & ACC_INTERFACE) != 0; 
  } 

  /**
   * Usable from other packages?
   */ 
  public final boolean isPublic() throws VM_PragmaUninterruptible { 
    return (modifiers & ACC_PUBLIC) != 0; 
  }

  /**
   * Non-subclassable?
   */ 
  public final boolean isFinal() throws VM_PragmaUninterruptible { 
    return (modifiers & ACC_FINAL) != 0; 
  }

  /**
   * Non-instantiable?
   */ 
  public final boolean isAbstract() throws VM_PragmaUninterruptible { 
    return (modifiers & ACC_ABSTRACT) != 0; 
  }

  /**
   * Use new-style "invokespecial" semantics for method calls in this class?
   */ 
  public final boolean isSpecial() throws VM_PragmaUninterruptible { 
    return (modifiers & ACC_SUPER) != 0; 
  }

  public int getModifiers() {
    return modifiers & APPLICABLE_TO_CLASSES;
  }

  /**
   * Name of source file from which class was compiled - 
   * something like "c:\java\src\java\lang\Object.java".
   * (null --> "unknown - wasn't recorded by compiler").
   */
  public final VM_Atom getSourceName() { 
    return sourceName;
  }

  /**
   * Superclass of this class (null means "no superclass", 
   * ie. class is "java/lang/Object").
   */
  public final VM_Class getSuperClass() throws VM_PragmaUninterruptible { 
    return superClass;
  }

  /**
   * Currently loaded classes that "extend" this class.
   */ 
  public final VM_Class[] getSubClasses() throws VM_PragmaUninterruptible {
    return subClasses;
  }

  /**
   * Interfaces implemented directly by this class 
   * (ie. not including superclasses).
   */
  public final VM_Class[] getDeclaredInterfaces() throws VM_PragmaUninterruptible { 
    return declaredInterfaces;
  }

  /**
   * Fields defined directly by this class (ie. not including superclasses).
   */ 
  public final VM_Field[] getDeclaredFields() throws VM_PragmaUninterruptible { 
    return declaredFields;
  }

  /**
   * Methods defined directly by this class (ie. not including superclasses).
   */
  public final VM_Method[] getDeclaredMethods() throws VM_PragmaUninterruptible { 
    return declaredMethods;
  }

  /**
   * Declared inner and static member classes.
   */
  public final VM_TypeReference[] getDeclaredClasses() {
    return declaredClasses;
  }

  /**
   * Outer class of this class, or null if this is not an inner/nested class
   */
  public final VM_TypeReference getDeclaringClass() {
    return declaringClass;
  }

  /**
   * Static initializer method for this class (null -> no static initializer
   *  or initializer already been run).
   */ 
  public final VM_Method getClassInitializerMethod() throws VM_PragmaUninterruptible {
    return classInitializerMethod;
  }

  /** 
   * Find description of a field of this class.
   * @param fieldName field name - something like "foo"
   * @param fieldDescriptor field descriptor - something like "I"
   * @return description (null --> not found)
   */ 
  public final VM_Field findDeclaredField(VM_Atom fieldName, VM_Atom fieldDescriptor) {
    for (int i = 0, n = declaredFields.length; i < n; ++i) {
      VM_Field field = declaredFields[i];
      if (field.getName() == fieldName && 
          field.getDescriptor() == fieldDescriptor)
        return field;
    }
    return null;
  }

  /** 
   * Find description of a method of this class.
   * @param methodName method name - something like "foo"
   * @param methodDescriptor method descriptor - something like "()I"
   * @return description (null --> not found)
   */ 
  public final VM_Method findDeclaredMethod(VM_Atom methodName, VM_Atom methodDescriptor) {
    for (int i = 0, n = declaredMethods.length; i < n; ++i) {
      VM_Method method = declaredMethods[i];
      if (method.getName() == methodName && 
          method.getDescriptor() == methodDescriptor)
        return method;
    }
    return null;
  }

  /**
   * Find description of "public static void main(String[])" 
   * method of this class.
   * @return description (null --> not found)
   */ 
  public final VM_Method findMainMethod() {
    VM_Atom   mainName       = VM_Atom.findOrCreateAsciiAtom(("main"));
    VM_Atom   mainDescriptor = VM_Atom.findOrCreateAsciiAtom
      (("([Ljava/lang/String;)V"));
    VM_Method mainMethod     = this.findDeclaredMethod(mainName, 
                                                       mainDescriptor);

    if (mainMethod == null   ||
        !mainMethod.isPublic() ||
        !mainMethod.isStatic() ) { 
      // no such method
      return null;
    }
    return mainMethod;
  }

  //
  // Constant pool accessors.
  //
  // The constant pool holds literals and external references used by 
  // the bytecodes of this class's methods.
  // Items are fetched by specifying their "constant pool index".
  //

  /**
   * Get offset of a literal constant, in bytes.
   * Offset is with respect to virtual machine's "table of contents" (jtoc).
   */ 
  public final int getLiteralOffset(int constantPoolIndex) {
    // jtoc slot number --> jtoc offset
    return constantPool[constantPoolIndex] << LOG_BYTES_IN_INT; 
  }

  /**
   * Get description of a literal constant.
   */ 
  public final byte getLiteralDescription(int constantPoolIndex) {
    // jtoc slot number --> description
    return VM_Statics.getSlotDescription(constantPool[constantPoolIndex]); 
  }

  /**
   * Get contents of a "typeRef" constant pool entry.
   * @return type that was referenced
   */
  public final VM_TypeReference getTypeRef(int constantPoolIndex) throws VM_PragmaUninterruptible {
    return VM_TypeReference.getTypeRef(constantPool[constantPoolIndex]);
  }

  /**
   * Get contents of a "methodRef" constant pool entry.
   */
  public final VM_MethodReference getMethodRef(int constantPoolIndex) throws VM_PragmaUninterruptible {
    return (VM_MethodReference)VM_MemberReference.getMemberRef(constantPool[constantPoolIndex]);
  }

  /**
   * Get contents of a "fieldRef" constant pool entry.
   */
  public final VM_FieldReference getFieldRef(int constantPoolIndex) throws VM_PragmaUninterruptible {
    return (VM_FieldReference)VM_MemberReference.getMemberRef(constantPool[constantPoolIndex]);
  }

  /**
   * Get contents of a "utf" constant pool entry.
   */
  final VM_Atom getUtf(int constantPoolIndex) throws VM_PragmaUninterruptible {
    return VM_Atom.getAtom(constantPool[constantPoolIndex]);
  }

  /**
   * Does this object implement the VM_SynchronizedObject interface?
   * @see VM_SynchronizedObject
   */ 
  final boolean isSynchronizedObject() throws VM_PragmaUninterruptible {
    VM_Class[] interfaces = getDeclaredInterfaces();
    for (int i = 0, n = interfaces.length; i < n; ++i)
      if (interfaces[i].isSynchronizedObjectType()) return true;
    return false;
  }

  /**
   * Should the methods of this class be compiled with special 
   * register save/restore logic?
   * @see VM_DynamicBridge
   */
  public final boolean isDynamicBridge () throws VM_PragmaUninterruptible {
    VM_Class[] interfaces = getDeclaredInterfaces();
    for (int i = 0, n = interfaces.length; i < n; ++i)
      if (interfaces[i].isDynamicBridgeType()) return true;
    return false;
  }

  /**
   * The methods of this class are only called from native code, 
   * they are compiled with
   * a special prolog to interface with the native stack frame.
   */
  public final boolean isBridgeFromNative() throws VM_PragmaUninterruptible {
    // The only class that returns true is the VM_JNIFunctions
    // which must have been loaded by the first call to System.loadLibrary
    // If this class is not loaded yet, we can assume that it
    // is not the VM_JNIFunctions
    VM_Class[] interfaces = getDeclaredInterfaces();
    for (int i = 0, n = interfaces.length; i < n; ++i)
      if (interfaces[i].isNativeBridgeType()) return true;
    return false;
  }

  /**
   * Should the methods of this class save incoming registers ?
   * @see VM_SaveVolatile
   */
  public final boolean isSaveVolatile() throws VM_PragmaUninterruptible {
    VM_Class[] interfaces = getDeclaredInterfaces();
    for (int i = 0, n = interfaces.length; i < n; ++i)
      if (interfaces[i].isSaveVolatileType()) return true;
    return false; 
  }

  //--------------------------------------------------------------------//
  // The following are available after the class has been "resolved".   //
  //--------------------------------------------------------------------//

  /**
   * Does this class override java.lang.Object.finalize()?
   */
  public final boolean hasFinalizer() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return (finalizeMethod != null);
  }

  /**
   * Get finalize method that overrides java.lang.Object.finalize(), 
   * if one exists
   */
  public final VM_Method getFinalizer() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return finalizeMethod;
  }

  /**
   * Static fields of this class.
   * Values in these fields are shared by all class instances.
   */
  public final VM_Field[] getStaticFields() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return staticFields;
  }

  /**
   * Non-static fields of this class (composed with supertypes, if any).
   * Values in these fields are distinct for each class instance.
   */
  public final VM_Field[] getInstanceFields() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return instanceFields;
  }

  /**
   * Statically dispatched methods of this class.
   */
  public final VM_Method[] getStaticMethods() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return staticMethods;
  }

  /**
   * Constructors (<init>) methods of this class.
   */
  public final VM_Method[] getConstructorMethods() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return constructorMethods;
  }

  /**
   * Virtually dispatched methods of this class 
   * (composed with supertypes, if any).
   */
  public final VM_Method[] getVirtualMethods() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return virtualMethods;
  }

  /**
   * @return All of the interfaces implemented by this class either
   * directly or by inheritance from superclass and superinterfaces
   * recursively.
   */
  public final VM_Class[] getAllImplementedInterfaces() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    int count = 0;
    int [] doesImplement = getDoesImplement();
    for (int i=0; i<doesImplement.length; i++) {
      int mask = doesImplement[i];
      while (mask != 0) {
        count++;
        mask &= (mask-1); // clear lsb 1 bit
      }
    }
    if (count == 0) return emptyVMClass;
    VM_Class[] ans = new VM_Class[count];
    for (int i =0, idx = 0; i<doesImplement.length; i++) {
      int mask = doesImplement[i];
      if (mask != 0) {
        for (int j=0; j<32; j++) {
          if ((mask & (1<<j)) != 0) {
            int id = 32 * i + j;
            ans[idx++] = VM_Class.getInterface(id);
          }
        }
      }
    }
    return ans;
  }

  /**
   * Total size, in bytes, of an instance of this class 
   * (including object header).
   */
  public final int getInstanceSize() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return instanceSize;
  }

  public final int getInstanceSizeInternal() throws VM_PragmaUninterruptible {
    return instanceSize;
  }

  /**
   * Add a field to the object; only meant to be called from VM_ObjectModel et al.
   * must be called when lock on class object is already held (ie from resolve).
   */
  public final void increaseInstanceSize(int numBytes) throws VM_PragmaUninterruptible {
    instanceSize += numBytes;
  }

  /**
   * Offsets of reference-containing instance fields of this class type.
   * Offsets are with respect to object pointer -- see VM_Field.getOffset().
   */
  public final int[] getReferenceOffsets() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return referenceOffsets;
  }

  /**
   * Offset of hole, due to alignment; 
   * returns zero if none.
   */
  public final int getEmptySlot() {
    return emptySlot;
  }

  /**
   * Set the offset of a hole.
   */
  public final void setEmptySlot(int off) {
    if (VM.VerifyAssertions) {
      VM._assert(off == 0 || emptySlot == 0);
    }
    emptySlot = off;
  }

  /**
   * @return alignment for instances of this class type
   */
  public final int getAlignment() throws VM_PragmaUninterruptible {
    return alignment;
  }

  /**
   * Set the alignment for instances of this class type
   */
  public final void setAlignment(int align) {
    if (VM.VerifyAssertions) VM._assert(align >= alignment);
    alignment = align;
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
   * Find specified static method description.
   * @param memberName method name - something like "foo"
   * @param memberDescriptor method descriptor - something like "I" or "()I"
   * @return method description (null --> not found)
   */
  public final VM_Method findStaticMethod(VM_Atom memberName, 
                                          VM_Atom memberDescriptor) {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    VM_Method methods[] = getStaticMethods();
    for (int i = 0, n = methods.length; i < n; ++i) {
      VM_Method method = methods[i];
      if (method.getName() == memberName && 
          method.getDescriptor() == memberDescriptor)
        return method;
    }
    return null;
  }

  /**
   * Find specified initializer method description.
   * @param    init method descriptor - something like "(I)V"
   * @return method description (null --> not found)
   */
  public final VM_Method findInitializerMethod(VM_Atom memberDescriptor) {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    VM_Method methods[] = getConstructorMethods();
    for (int i = 0, n = methods.length; i < n; ++i) {
      VM_Method method = methods[i];
      if (method.getDescriptor() == memberDescriptor)
        return method;
    }
    return null;
  }

  /**
   * Runtime type information for this class type.
   */
  public final Object[] getTypeInformationBlock() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return typeInformationBlock;
  }

  //--------------------------------------------------------------------//
  //                     Miscellaneous queries.                         //
  //---------------------------------------------------------------------//


  /**
   * Support for user-written class loaders:
   * It's required to find the classloader of the class
   * whose method requires another class to be loaded;
   * the initiating loader of the required class is the
   * defining loader of the requiring class.
   *
   * @author Julian Dolby
   * 
   * @param frames specifies the number of frames back from the 
   * caller to the method whose class's loader is required
   */
  public static ClassLoader getClassLoaderFromStackFrame(int skip) {
    skip++; // account for stack frame of this function
    VM_StackBrowser browser = new VM_StackBrowser();
    VM.disableGC();
    browser.init();
    while (skip-- > 0) browser.up();
    VM.enableGC();
    return browser.getClassLoader();
  }
  
  /**
   * Used for accessibility checks in reflection code.
   * Find the class of the method that corresponds to the requested frame.
   * 
   * @param frames specifies the number of frames back from the 
   * caller to the method whose class is required
   */
  public static VM_Class getClassFromStackFrame(int skip) {
    skip++; // account for stack frame of this function
    VM_StackBrowser browser = new VM_StackBrowser();
    VM.disableGC();
    browser.init();
    while (skip-- > 0) browser.up();
    VM.enableGC();
    return browser.getCurrentClass();
  }
  
  //--------------------------------------------------------------------//
  //      Load, Resolve, Instantiate, and Initialize                    //
  //--------------------------------------------------------------------//

  /**
   * Create an instance of a VM_Class.
   * @param typeRef the cannonical type reference for this type.
   * @param input the data stream from which to read the class's description.
   */
  VM_Class(VM_TypeReference typeRef, DataInputStream input) 
    throws ClassFormatError, 
           IOException {
    super(typeRef);
    subClasses = emptyVMClass;

    if (classLoadingDisabled) {
      throw new RuntimeException("ClassLoading Disabled : "+this);
    }

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("VM_Class: (begin) load file " + this.getDescriptor() + "\n");
    
    int magic = input.readInt();
    if (magic != 0xCAFEBABE) {
      throw new ClassFormatError("bad magic number " + Integer.toHexString(magic));
    }

    // Get the class file version number and check to see if it is a version
    // that we support.
    int minor = input.readUnsignedShort();
    int major = input.readUnsignedShort();
    switch (major) {
    case 45: case 46: case 47:  // we support all variants of these major versions so the minor number doesn't matter.
      break;
    case 48: // we only support up to 48.0
      if (minor == 0) break;
    default:
      throw new java.lang.UnsupportedClassVersionError("unsupported class file version " + major + "." + minor);
    }
    
    //
    // pass 1: read constant pool
    //
    constantPool = new int[input.readUnsignedShort()];
    byte tmpTags[] = new byte[constantPool.length];

    // note: slot 0 is unused
    for (int i = 1; i <constantPool.length; i++) {
      switch (tmpTags[i] = input.readByte()) {
      case TAG_UTF:  {
        byte utf[] = new byte[input.readUnsignedShort()];
        input.readFully(utf);
        constantPool[i] = VM_Atom.findOrCreateUtf8Atom(utf).getId();
        break;  
      }
      
      case TAG_UNUSED:
        if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
        break;

      case TAG_INT:
        constantPool[i] = VM_Statics.findOrCreateIntLiteral(input.readInt());
        break;

      case TAG_FLOAT:
        constantPool[i] = VM_Statics.findOrCreateFloatLiteral(input.readInt());
        break;

      case TAG_LONG:
        constantPool[i++] = VM_Statics.findOrCreateLongLiteral(input.readLong());
        break;

      case TAG_DOUBLE:
        constantPool[i++] = VM_Statics.findOrCreateDoubleLiteral(input.readLong());
        break;

      case TAG_TYPEREF:
        constantPool[i] = input.readUnsignedShort();
        break;

      case TAG_STRING:
        constantPool[i] = input.readUnsignedShort();
        break;

      case TAG_FIELDREF:
      case TAG_METHODREF:
      case TAG_INTERFACE_METHODREF: {
        int classDescriptorIndex         = input.readUnsignedShort();
        int memberNameAndDescriptorIndex = input.readUnsignedShort();
        constantPool[i] = (classDescriptorIndex << BITS_IN_SHORT) | memberNameAndDescriptorIndex;
        break; 
      }

      case TAG_MEMBERNAME_AND_DESCRIPTOR: {
        int memberNameIndex = input.readUnsignedShort();
        int descriptorIndex = input.readUnsignedShort();
        constantPool[i] = (memberNameIndex << BITS_IN_SHORT) | descriptorIndex;
        break;  
      }
        
      default:
        throw new ClassFormatError("bad constant pool");
      }
    }

    //
    // pass 2: post-process type and string constant pool entries 
    // (we must do this in a second pass because of forward references)
    //
    try {
      for (int i = 1; i<constantPool.length; i++) {
        switch (tmpTags[i]) {
        case TAG_LONG:
        case TAG_DOUBLE: 
          ++i;
          break; 

        case TAG_TYPEREF: { // in: utf index
          VM_Atom typeName = getUtf(constantPool[i]);
          constantPool[i] = VM_TypeReference.findOrCreate(getClassLoader(), typeName.descriptorFromClassName()).getId();
          break; 
        } // out: type reference id

        case TAG_STRING: { // in: utf index
          constantPool[i] = VM_Statics.findOrCreateStringLiteral(getUtf(constantPool[i]));
          break; 
        } // out: jtoc slot number
        }
      }
    } catch (java.io.UTFDataFormatException x) {
      throw new ClassFormatError(x.toString());
    }
    
    //
    // pass 3: post-process type field and method constant pool entries 
    //
    for (int i = 1; i<constantPool.length; i++) {
      switch (tmpTags[i]) {
      case TAG_LONG:
      case TAG_DOUBLE: 
        ++i;
        break; 

      case TAG_FIELDREF:
      case TAG_METHODREF:
      case TAG_INTERFACE_METHODREF: { // in: classname+membername+memberdescriptor indices
        int bits                         = constantPool[i];
        int classNameIndex               = (bits >> BITS_IN_SHORT) & 0xffff;
        int memberNameAndDescriptorIndex = bits & 0xffff;
        int memberNameAndDescriptorBits  = constantPool[memberNameAndDescriptorIndex];
        int memberNameIndex              = (memberNameAndDescriptorBits >> BITS_IN_SHORT) & 0xffff;
        int memberDescriptorIndex        = (memberNameAndDescriptorBits       ) & 0xffff;
        
        VM_TypeReference  tref   = getTypeRef(classNameIndex);
        VM_Atom memberName       = getUtf(memberNameIndex);
        VM_Atom memberDescriptor = getUtf(memberDescriptorIndex);
        VM_MemberReference mr    = VM_MemberReference.findOrCreate(tref, memberName, memberDescriptor);
        constantPool[i] = mr.getId();
        break; 
      } // out: VM_MemberReference id
      }
    }

    modifiers     = input.readUnsignedShort();
    int myTypeIndex = input.readUnsignedShort();
    VM_TypeReference myTypeRef = getTypeRef(myTypeIndex);
    if (myTypeRef != typeRef) {
      // eg. file contains a different class than would be 
      // expected from its .class file name
      throw new ClassFormatError("expected class \"" + getDescriptor()
                                 + "\" but found \"" + myTypeRef.getName() + "\"");
    }
    
    VM_TypeReference superType = getTypeRef(input.readUnsignedShort()); // possibly null
    if (!(isInterface() || superType == null)) {
      superClass = superType.resolve().asClass();
    }

    int numInterfaces = input.readUnsignedShort();
    if (numInterfaces == 0) {
      declaredInterfaces = emptyVMClass;
    } else {
      declaredInterfaces = new VM_Class[numInterfaces];
      for (int i = 0; i < numInterfaces; ++i) {
        VM_TypeReference inTR = getTypeRef(input.readUnsignedShort());
        declaredInterfaces[i] = inTR.resolve().asClass();
      }
    }

    int numFields = input.readUnsignedShort();
    if (numFields == 0) {
      declaredFields = emptyVMField;
    } else {
      declaredFields = new VM_Field[numFields];
      for (int i = 0; i<numFields; i++) {
        int      fmodifiers      = input.readUnsignedShort();
        VM_Atom  fieldName       = getUtf(input.readUnsignedShort());
        VM_Atom  fieldDescriptor = getUtf(input.readUnsignedShort());
        VM_MemberReference memRef= VM_MemberReference.findOrCreate(typeRef, fieldName, fieldDescriptor);
        declaredFields[i] = new VM_Field(this, memRef, fmodifiers, input);
      }
    }

    int numMethods = input.readUnsignedShort();
    if (numMethods == 0) {
      declaredMethods = emptyVMMethod;
    } else {
      declaredMethods = new VM_Method[numMethods];
      for (int i = 0; i<numMethods; i++) {
        int       mmodifiers       = input.readUnsignedShort();
        VM_Atom   methodName       = getUtf(input.readUnsignedShort());
        VM_Atom   methodDescriptor = getUtf(input.readUnsignedShort());
        VM_MemberReference memRef  = VM_MemberReference.findOrCreate(typeRef, methodName, methodDescriptor);
        VM_Method method           = VM_Method.readMethod(this, memRef, mmodifiers, input);
        declaredMethods[i] = method;
        if (method.isClassInitializer())
          classInitializerMethod = method;
      }
    }

    // Read attributes.
    for (int i = 0, n = input.readUnsignedShort(); i < n; ++i) {
      VM_Atom attName   = getUtf(input.readUnsignedShort());
      int     attLength = input.readInt();

      // Class attributes
      if (attName == VM_ClassLoader.sourceFileAttributeName && attLength == 2) {
        sourceName = getUtf(input.readUnsignedShort());
        continue;
      }

      if (attName == VM_ClassLoader.innerClassesAttributeName) {
        // Parse InnerClasses attribute, and use the information to populate
        // the list of declared member classes.  We do this so we can 
        // support the java.lang.Class.getDeclaredClasses() 
        // and java.lang.Class.getDeclaredClass methods.

        int numberOfClasses = input.readUnsignedShort();
        declaredClasses = new VM_TypeReference[numberOfClasses];

        for (int j = 0; j < numberOfClasses; ++j) {
          int innerClassInfoIndex = input.readUnsignedShort();
          int outerClassInfoIndex = input.readUnsignedShort();
          int innerNameIndex = input.readUnsignedShort();
          int innerClassAccessFlags = input.readUnsignedShort();

          if (innerClassInfoIndex != 0 &&
              outerClassInfoIndex == myTypeIndex &&
              innerNameIndex != 0) {
            // This looks like a declared inner class.
            declaredClasses[j] = getTypeRef(innerClassInfoIndex);
          }

          if (innerClassInfoIndex == myTypeIndex) {
            if (outerClassInfoIndex != 0) {
              declaringClass = getTypeRef(outerClassInfoIndex);
            }
            if ((innerClassAccessFlags & (ACC_PRIVATE | ACC_PROTECTED)) != 0) {
              modifiers &= ~(ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED);
            }
            modifiers |= innerClassAccessFlags;
          }
        }
        continue;
      }

      input.skipBytes(attLength);
    }

    state = CLASS_LOADED;

    VM_Magic.sync(); // we're about to leak a reference to 'this' force memory to be consistent

    if (superClass != null) {
      // MUST wait until end of constructor to 'publish' the subclass link.
      // If we do this earlier, then people can see an incomplete VM_Class object
      // by traversing the subclasses of our superclass!
      superClass.addSubClass(this);
    }

    VM_Callbacks.notifyClassLoaded(this);

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("VM_Class: (end)   load file " + 
                                                          getDescriptor() + "\n");
  }

  /**
   * Generate size and offset information for members of this class and
   * allocate space in jtoc for static fields, static methods, and virtual 
   * method table. 
   * Side effects: superclasses and superinterfaces are resolved.
   */ 
  public final synchronized void resolve() {
    if (isResolved()) return;

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWriteln("VM_Class: (begin) resolve "+this);
    if (VM.VerifyAssertions) VM._assert(state == CLASS_LOADED);

    // Resolve superclass and super interfaces
    //
    if (superClass != null) {
      superClass.resolve();
    }
    for (int i=0; i<declaredInterfaces.length; i++) {
      declaredInterfaces[i].resolve();
    }

    if (isInterface()) {
      depth = 1; 
    } else if (isJavaLangObjectType()) {
      instanceSize = VM_ObjectModel.computeScalarHeaderSize(this);
      alignment = BYTES_IN_ADDRESS;
    } else {
      depth = superClass.depth + 1;
      thinLockOffset = superClass.thinLockOffset;
      instanceSize = superClass.instanceSize;
      emptySlot = superClass.emptySlot;
      alignment= superClass.alignment;
    }

    if (isSynchronizedObject() || this == VM_Type.JavaLangClassType)
      VM_ObjectModel.allocateThinLock(this);

    if (VM.verboseClassLoading) VM.sysWrite("[Preparing "+this+"]\n");

    // build field and method lists for this class
    //
    {
      VM_FieldVector  staticFields   = new VM_FieldVector();
      VM_FieldVector  instanceFields = new VM_FieldVector();
      VM_MethodVector staticMethods  = new VM_MethodVector();
      VM_MethodVector constructorMethods  = new VM_MethodVector();
      VM_MethodVector virtualMethods = new VM_MethodVector();

      // start with fields and methods of superclass
      //
      if (superClass != null) {
        VM_Field fields[] = superClass.getInstanceFields();
        for (int i = 0, n = fields.length; i < n; ++i)
          instanceFields.addElement(fields[i]);

        VM_Method methods[] = superClass.getVirtualMethods();
        for (int i = 0, n = methods.length; i < n; ++i)
          virtualMethods.addElement(methods[i]);
      }

      // append fields defined by this class
      //
      VM_Field fields[] = getDeclaredFields();
      for (int i = 0, n = fields.length; i < n; ++i) {
        VM_Field field = fields[i];
        if (field.isStatic())
          staticFields.addElement(field);
        else
          instanceFields.addElement(field);
      }

      // append/overlay methods defined by this class
      //
      VM_Method methods[] = getDeclaredMethods();
      for (int i = 0, n = methods.length; i < n; ++i) {
        VM_Method method = methods[i];

        if (VM.VerifyUnint) {
          if (!method.isInterruptible() && method.isSynchronized()) {
            if (VM.ParanoidVerifyUnint || !VM_PragmaLogicallyUninterruptible.declaredBy(method)) {
              VM.sysWriteln("WARNING: "+method+" cannot be both uninterruptible and synchronized");
            }
          }
        }

        if (method.isObjectInitializer()) {
          VM_Callbacks.notifyMethodOverride(method, null);
          constructorMethods.addElement(method);
        } else if (method.isStatic()) {
          if (!method.isClassInitializer()) {
            VM_Callbacks.notifyMethodOverride(method, null);
            staticMethods.addElement(method);
          }
        } else { // Virtual method

          if (method.isSynchronized()) {
            VM_ObjectModel.allocateThinLock(this);
          }

          // method could override something in superclass - check for it
          //
          int superclassMethodIndex = -1;
          for (int j = 0, m = virtualMethods.size(); j < m; ++j) {
            VM_Method alreadyDefinedMethod = virtualMethods.elementAt(j);
            if (alreadyDefinedMethod.getName() == method.getName() &&
                alreadyDefinedMethod.getDescriptor() == method.getDescriptor()) {
              // method already defined in superclass
              superclassMethodIndex = j;
              break;
            }
          }

          if (superclassMethodIndex == -1) {
            VM_Callbacks.notifyMethodOverride(method, null);
            virtualMethods.addElement(method);                          // append
          } else {
            VM_Method superc = (VM_Method)virtualMethods.elementAt(superclassMethodIndex);
            if (VM.VerifyUnint) {
              if (!superc.isInterruptible() && method.isInterruptible()) {
                VM.sysWriteln("WARNING: interruptible "+method+" overrides uninterruptible "+superc);
              }
            }
            VM_Callbacks.notifyMethodOverride(method, superc);
            virtualMethods.setElementAt(method, superclassMethodIndex); // override
          }
        }
      }

      // Deal with Miranda methods.
      // If this is an abstract class, then for each
      // interface that this class implements, ensure that a corresponding virtual
      // method is declared.  If one is not, then create an abstract method to fill the void.
      if (!isInterface() && isAbstract()) {
        for (int i=0; i<declaredInterfaces.length; i++) {
          VM_Class I = declaredInterfaces[i];
          VM_Method[] iMeths = I.getVirtualMethods();
          outer: 
          for (int j=0; j<iMeths.length; j++) {
            VM_Method iMeth = iMeths[j];
            VM_Atom iName = iMeth.getName();
            VM_Atom iDesc = iMeth.getDescriptor();
            for (int k=0; k<virtualMethods.size(); k++) {
              VM_Method vMeth = virtualMethods.elementAt(k);
              if (vMeth.getName() == iName && vMeth.getDescriptor() == iDesc) continue outer;
            }
            VM_MemberReference mRef = VM_MemberReference.findOrCreate(typeRef, iName, iDesc);
            virtualMethods.addElement(new VM_AbstractMethod(this, mRef, ACC_ABSTRACT | ACC_PUBLIC, 
                                                            iMeth.getExceptionTypes()));
          }
        }
      }

      // If this is an interface, inherit methods from its superinterfaces
      if (isInterface()) {
        for (int i=0; i<declaredInterfaces.length; i++) {
          VM_Method[] meths = declaredInterfaces[i].getVirtualMethods();
          for (int j=0; j<meths.length; j++) {
            virtualMethods.addUniqueElement(meths[j]);
          }
        }
      }
      
      this.staticFields   = staticFields.finish();
      this.instanceFields = instanceFields.finish();
      this.staticMethods  = staticMethods.finish();
      this.constructorMethods = constructorMethods.finish();
      this.virtualMethods = virtualMethods.finish();
    }

    // allocate space for class fields
    //
    for (int i = 0, n = staticFields.length; i < n; ++i) {
      VM_Field field = staticFields[i];
      VM_TypeReference fieldType = field.getType();
      byte slotType;
      if (fieldType.isReferenceType())
        slotType = VM_Statics.REFERENCE_FIELD;
      else if (fieldType.getSize() == BYTES_IN_LONG)
        slotType = VM_Statics.WIDE_NUMERIC_FIELD;
      else
        slotType = VM_Statics.NUMERIC_FIELD;
      field.offset = (VM_Statics.allocateSlot(slotType) << LOG_BYTES_IN_INT);

      // (SJF): Serialization nastily accesses even final private static
      //           fields via pseudo-reflection! So, we must shove the
      //           values of final static fields into the JTOC.  Now
      //           seems to be a good time.
      if (field.isFinal()) {
        setFinalStaticJTOCEntry(field,field.offset);
      }
    }

    // lay out instance fields
    //
    VM_ObjectModel.layoutInstanceFields(this);

    // count reference fields
    int referenceFieldCount = 0;
    for (int i = 0, n = instanceFields.length; i < n; ++i) {
      VM_Field field     = instanceFields[i];
      if (field.getType().isReferenceType())
        referenceFieldCount += 1;
    }

    // record offsets of those instance fields that contain references
    //
    referenceOffsets = new int[referenceFieldCount];
    for (int i = 0, j = 0, n = instanceFields.length; i < n; ++i) {
      VM_Field field = instanceFields[i];
      if (field.getType().isReferenceType())
        referenceOffsets[j++] = field.offset;
    }

    // Allocate space for <init> method pointers
    //
    for (int i = 0, n = constructorMethods.length; i < n; ++i) {
      VM_Method method = constructorMethods[i];
      method.offset = VM_Statics.allocateSlot(VM_Statics.METHOD) << LOG_BYTES_IN_INT;
    }

    // Allocate space for static method pointers
    //
    for (int i = 0, n = staticMethods.length; i < n; ++i) {
      VM_Method method = staticMethods[i];
      if (method.isClassInitializer()) {
        method.offset = 0xdeadbeef; // should never be used.
      } else {
        method.offset = VM_Statics.allocateSlot(VM_Statics.METHOD) << LOG_BYTES_IN_INT;
      }
    }

    // create "type information block" and initialize its first four words
    //
    typeInformationBlock = MM_Interface.newTIB(TIB_FIRST_VIRTUAL_METHOD_INDEX + virtualMethods.length);
    VM_Statics.setSlotContents(tibSlot, typeInformationBlock);
    // Initialize dynamic type checking data structures
    typeInformationBlock[TIB_TYPE_INDEX] = this;
    typeInformationBlock[TIB_SUPERCLASS_IDS_INDEX] = VM_DynamicTypeCheck.buildSuperclassIds(this);
    typeInformationBlock[TIB_DOES_IMPLEMENT_INDEX] = VM_DynamicTypeCheck.buildDoesImplement(this);
    // (element type for arrays not used classes)

    // lay out virtual method section of type information block 
    // (to be filled in by instantiate)
    for (int i = 0, n = virtualMethods.length; i < n; ++i) {
      VM_Method method = virtualMethods[i];
      method.offset = (TIB_FIRST_VIRTUAL_METHOD_INDEX + i) << LOG_BYTES_IN_ADDRESS;
    }

    // RCGC: Determine if class is inherently acyclic
    acyclic = false;    // must initially be false for recursive types
    boolean foundCyclic = false;
    for (int i = 0; i < instanceFields.length; i++) {
      VM_TypeReference ft = instanceFields[i].getType();
      if (!(ft.isResolved() && ft.peekResolvedType().isAcyclicReference())) {
        foundCyclic = true; 
        break;
      }
    }
    if (!foundCyclic) {
      acyclic = true;
    }

    state = CLASS_RESOLVED; // can't move this beyond "finalize" code block
    
    VM_Callbacks.notifyClassResolved(this);
    MM_Interface.notifyClassResolved(this);

    // check for a "finalize" method that overrides the one in java.lang.Object
    //
    finalizeMethod = null;
    if (!isInterface()) {
      VM_Method finalize = findVirtualMethod(VM_ClassLoader.StandardObjectFinalizerMethodName, 
                                             VM_ClassLoader.StandardObjectFinalizerMethodDescriptor);
      if (!finalize.getDeclaringClass().isJavaLangObjectType()) {
        finalizeMethod = finalize;
      }
    }

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWriteln("VM_Class: (end)   resolve " + this);
  }

  // RCGC: A reference to class is acyclic if the class is acyclic and
  // final (otherwise the reference could be to a subsequently loaded
  // cyclic subclass).
  //
  public final boolean isAcyclicReference() throws VM_PragmaUninterruptible {
    return acyclic && isFinal();
  }

  /** 
   * Insert the value of a final static field into the JTOC 
   */
  private void setFinalStaticJTOCEntry(VM_Field field, int fieldOffset) {
    if (!field.isFinal()) return;
    // value Index: index into the classes constant pool.
    int valueIndex = field.getConstantValueIndex();

    // index for field value in JTOC
    int fieldIndex = fieldOffset >> LOG_BYTES_IN_INT;

    // if there's no value in the constant pool, bail out
    if (valueIndex <= 0) return;

    int literalOffset= field.getDeclaringClass().getLiteralOffset(valueIndex);

    // if field is object, should use reference form of setSlotContents.
    // But getSlotContentsAsObject() uses Magic to recast as Object, and
    // Magic is not allowed when BootImageWriter is executing under JDK,
    // so we only do the "proper" thing when the vm is running.  This is OK
    // for now, because the bootImage is not collected (all object get BIG
    // reference counts
    //
    if (VM.runningVM && VM_Statics.isReference(fieldIndex)) {
      Object obj = VM_Statics.getSlotContentsAsObject(literalOffset>>LOG_BYTES_IN_INT);
      VM_Statics.setSlotContents(fieldIndex,obj);
    } else if (field.getType().getSize() == BYTES_IN_INT) {
      // copy one word from constant pool to JTOC
      int value = VM_Statics.getSlotContentsAsInt(literalOffset>>LOG_BYTES_IN_INT);
      VM_Statics.setSlotContents(fieldIndex,value);
    } else {
      // copy two words from constant pool to JTOC
      long value = VM_Statics.getSlotContentsAsLong(literalOffset>>LOG_BYTES_IN_INT);
      VM_Statics.setSlotContents(fieldIndex,value);
    }
  }

  /**
   * Compile this class's methods, build type information block, populate jtoc.
   * Side effects: superclasses are instantiated.
   */
  public final synchronized void instantiate() {
    if (isInstantiated())
      return;

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWriteln("VM_Class: (begin) instantiate "+this);
    if (VM.VerifyAssertions) VM._assert(state == CLASS_RESOLVED);

    // instantiate superclass
    //
    if (superClass != null) {
      superClass.instantiate();
    }
    if (VM.runningVM) {
      // can't instantiate if building bootimage, since this can cause
      // class initializer to be lost (when interface is not included in bootimage).
      // since we don't need to instantiate/initialize for the purposes of 
      // dynamic type checking and interface invocation, defer it until runtime
      // and the class actually refers to a static field of the interface.
      for (int i=0; i<declaredInterfaces.length; i++) {
        declaredInterfaces[i].instantiate();
      }
    }

    // Initialize slots in the TIB for virtual methods
    for (int slot = TIB_FIRST_VIRTUAL_METHOD_INDEX, i = 0, 
           n = virtualMethods.length; i < n; ++i, ++slot) {
      VM_Method method = virtualMethods[i];
      if (method.isPrivate() && method.getDeclaringClass() != this) {
        typeInformationBlock[slot] = null; // an inherited private method....will never be invoked via this TIB
      } else {
        typeInformationBlock[slot] = method.getCurrentInstructions();
      }
    }

    // compile <init> methods and put their addresses into jtoc
    for (int i = 0, n = constructorMethods.length; i < n; ++i) {
      VM_Method method = constructorMethods[i];
      VM_Statics.setSlotContents(method.getOffset() >> LOG_BYTES_IN_INT, method.getCurrentInstructions());
    }

    // compile static methods and put their addresses into jtoc
    for (int i = 0, n = staticMethods.length; i < n; ++i) {
      // don't bother compiling <clinit> here;
      // compile it right before we invoke it in initialize.
      // This also avoids putting <clinit>s in the bootimage.
      VM_Method method = staticMethods[i];
      if (!method.isClassInitializer()) {
        VM_Statics.setSlotContents(method.getOffset() >> LOG_BYTES_IN_INT, method.getCurrentInstructions());
      }
    }

    VM_InterfaceInvocation.initializeDispatchStructures(this);

    if (VM.writingBootImage) { 
      state = CLASS_INITIALIZED; 
    } else {
      state = CLASS_INSTANTIATED;
    }

    VM_Callbacks.notifyClassInstantiated(this);
    if (VM.writingBootImage)
      VM_Callbacks.notifyClassInitialized(this);

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWriteln("VM_Class: (end)   instantiate " +this);
  }

  /**
   * Execute this class's static initializer, <clinit>.
   * Side effects: superclasses are initialized, static fields receive 
   * initial values.
   */ 
  public final synchronized void initialize() 
    // Doesn't really need declaring.
    throws ExceptionInInitializerError
  {
    if (isInitialized())
      return;

    if (state == CLASS_INITIALIZING) {
      return;
    }

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWriteln("VM_Class: (begin) initialize " + this);
    if (VM.VerifyAssertions) VM._assert(state == CLASS_INSTANTIATED);
    state = CLASS_INITIALIZING;
    if (VM.verboseClassLoading) VM.sysWrite("[Initializing "+this+"]\n");

    // run super <clinit>
    //
    if (superClass != null) {
      superClass.initialize();
    }

    // run <clinit>
    //
    if (classInitializerMethod != null) {
      VM_CompiledMethod cm = classInitializerMethod.getCurrentCompiledMethod();
      while (cm == null) {
        classInitializerMethod.compile();
        cm = classInitializerMethod.getCurrentCompiledMethod();
      }

      if (VM.verboseClassLoading) VM.sysWrite("[Running static initializer for "+this+"]\n");

      try {
        VM_Magic.invokeClassInitializer(cm.getInstructions());
      } catch (Error e) {
        throw e;
      } catch (Throwable t) {
        ExceptionInInitializerError eieio 
          = new ExceptionInInitializerError("While initializing " + this);
        eieio.initCause(t);
        throw eieio;
      }

      // <clinit> is no longer needed: reclaim space by removing references to it
      classInitializerMethod.invalidateCompiledMethod(cm);
      classInitializerMethod               = null;
    }

    // report that a class is about to be marked initialized to 
    // the opt compiler so it can invalidate speculative CHA optimizations
    // before an instance of this class could actually be created.
    //-#if RVM_WITH_OPT_COMPILER
    OptCLDepManager.classInitialized(this);
    //-#endif

    state = CLASS_INITIALIZED;

    VM_Callbacks.notifyClassInitialized(this);

    if (VM.verboseClassLoading) VM.sysWrite("[Initialized "+this+"]\n");
    if (VM.TraceClassLoading && VM.runningVM) VM.sysWriteln("VM_Class: (end)   initialize " +this);
  }

  /**
   * Copy the values of all static final fields into 
   * the JTOC.  Note: This method should only be run AFTER
   * the class initializer has run.
   */
  public void setAllFinalStaticJTOCEntries() {
    if (VM.VerifyAssertions) VM._assert (isInitialized());
    VM_Field[] fields = getStaticFields();
    for (int i=0; i<fields.length; i++) {
      VM_Field f = fields[i];
      if (f.isFinal()) {
        setFinalStaticJTOCEntry(f,f.getOffset());
      }
    }
  }

  void resolveNativeMethods() {
    if (VM.VerifyAssertions) VM._assert (isInitialized());
    resolveNativeMethodsInternal(getStaticMethods());
    resolveNativeMethodsInternal(getVirtualMethods());
  }

  private void resolveNativeMethodsInternal(VM_Method[] methods) {
    for (int i=0; i<methods.length; i++) {
      VM_Method m = methods[i];
      if (m.isNative()) {
        m.replaceCompiledMethod(null);
      }
    }
  }

  /**
   * Add to list of classes that derive from this one.
   */
  private void addSubClass(VM_Class sub) {
    int        n    = subClasses.length;
    VM_Class[] tmp  = new VM_Class[n + 1];

    for (int i = 0; i < n; ++i)
      tmp[i] = subClasses[i];
    tmp[n] = sub;

    subClasses = tmp;
  }

  //------------------------------------------------------------//
  // Support for speculative optimizations that may need to 
  // invalidate compiled code when new classes are loaded.
  //------------------------------------------------------------//
  //-#if RVM_WITH_OPT_COMPILER
  public static final OPT_ClassLoadingDependencyManager OptCLDepManager = new OPT_ClassLoadingDependencyManager();
  //-#endif

  /**
   * Given a method declared by this class, update all
   * dispatching tables to refer to the current compiled
   * code for the method.
   */
  public void updateMethod(VM_Method m) {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    if (VM.VerifyAssertions) VM._assert(m.getDeclaringClass() == this);
    if (m.isClassInitializer()) return; // we never put this method in the jtoc anyways!

    if (m.isStatic() || m.isObjectInitializer()) {
      updateJTOCEntry(m);
    } else {
      updateVirtualMethod(m);
    }
  }

  /**
   * Update the JTOC slot for the given static method to point to
   * the current compiled code for the given method.
   * NOTE: This method is intentionally not synchronized to avoid deadlocks.
   *       We instead rely on the fact that we are always updating the JTOC with
   *       the most recent instructions for the method.
   */
  public void updateJTOCEntry(VM_Method m) {
    if (VM.VerifyAssertions) VM._assert(m.getDeclaringClass() == this);
    if (VM.VerifyAssertions) VM._assert(isResolved());
    if (VM.VerifyAssertions) VM._assert(m.isStatic() || m.isObjectInitializer());
    int slot = m.getOffset() >>> LOG_BYTES_IN_INT;
    VM_Statics.setSlotContents(slot, m.getCurrentInstructions());
  }


  /**
   * Update this class's TIB entry for the given method to point to
   * the current compiled code for the given method.
   * NOTE: This method is intentionally not synchronized to avoid deadlocks.
   *       We instead rely on the fact that we are always updating the JTOC with
   *       the most recent instructions for the method.
   */
  public void updateTIBEntry(VM_Method m) {
    if (VM.VerifyAssertions) {
      VM_Method vm = findVirtualMethod(m.getName(), m.getDescriptor());
      VM._assert(vm == m);
    }
    int offset = m.getOffset() >>> LOG_BYTES_IN_ADDRESS;
    typeInformationBlock[offset] = m.getCurrentInstructions();
    VM_InterfaceInvocation.updateTIBEntry(this, m);
  }


  /**
   * Update the TIB entry's for all classes that inherit the given method 
   * to point to the current compiled code for the given method.
   * NOTE: This method is intentionally not synchronized to avoid deadlocks.
   *       We instead rely on the fact that we are always updating the JTOC with
   *       the most recent instructions for the method.
   */
  public void updateVirtualMethod(VM_Method m) {
    VM_Method dm = findDeclaredMethod(m.getName(), m.getDescriptor());
    if (dm != null && dm != m) return;  // this method got overridden
    updateTIBEntry(m);
    if (m.isPrivate()) return; // can't override
    VM_Class[] subClasses = getSubClasses(); 
    for (int i = 0; i < subClasses.length; i++) {
      VM_Class sc = subClasses[i];
      if (sc.isResolved()) {
        sc.updateVirtualMethod(m);
      }
    }
  }

  //------------------------------------------------------------//
  // Additional fields and methods for Interfaces               //
  //------------------------------------------------------------//

  private static final VM_Synchronizer interfaceCountLock = new VM_Synchronizer();
  private static int          interfaceCount     = 0;
  private static VM_Class[]   interfaces         = new VM_Class[100];
  private int                 interfaceId        = -1; 
  VM_Method[]                 noIMTConflictMap; // used by VM_InterfaceInvocation to support resetTIB

  /**
   * VM_Classes used as Interfaces get assigned an interface id.
   *   If the class is not an interface, attempting to use this
   *   id will cause an IncompatibleClassChangeError to be thrown
   */ 
  public int getInterfaceId () {
    if (interfaceId == -1) {
      assignInterfaceId();
    }
    return interfaceId;
  }

  public int getDoesImplementIndex() {
    return getInterfaceId() >>> 5;
  }

  public int getDoesImplementBitMask() {
    return 1 << (getInterfaceId() & 31);
  }

  public static VM_Class getInterface(int id) {
    return interfaces[id];
  }

  private synchronized void assignInterfaceId() {
    if (interfaceId == -1) {
      synchronized(interfaceCountLock) {
        interfaceId = interfaceCount++;
        if (interfaceId == interfaces.length) {
          VM_Class[] tmp = new VM_Class[interfaces.length*2];
          System.arraycopy(interfaces, 0, tmp, 0, interfaces.length);
          interfaces = tmp;
        }
        interfaces[interfaceId] = this;
      }
    }
  }
}
