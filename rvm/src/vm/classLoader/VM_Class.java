/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import java.io.IOException;
import java.io.FileNotFoundException;
//-#if RVM_WITH_OPT_COMPILER
import com.ibm.JikesRVM.opt.*;
//-#endif
// Following for beginning of classloader support
import java.lang.ClassLoader;
import java.io.InputStream;
import java.io.DataInputStream;

/**
 *  Description of a java "class" type.
 * 
 * <p> This description is read from a ".class" file as classes/field/methods
 * referenced by the running program need to be bound in to the running image.
 * 
 * @see VM_Array
 * @see VM_Primitive
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public final class VM_Class extends VM_Type
  implements VM_Constants, VM_ClassLoaderConstants {

  //------------------------------------------------------------------//
  //                              Section 0.                          //
  //                   The following are always available.            //
  //------------------------------------------------------------------//

  /**
   * Name - something like "java.lang.String".
   */ 
  public final String getName() {
    return descriptor.classNameFromDescriptor();
  }

  /**
   * Stack space requirement.
   */ 
  public final int getStackWords() throws VM_PragmaUninterruptible {
    return 1;
  }

  //--------------------------------------------------------------------//
  //                           Section 1.                               //
  //  The following are available after the class has been "loaded".    //
  //--------------------------------------------------------------------//


  //
  // Attributes.
  //

  /**
   * An "interface" description rather than a "class" description?
   */ 
  public final boolean isInterface() throws VM_PragmaUninterruptible { 
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return (modifiers & ACC_INTERFACE) != 0; 
  } 

  /**
   * Usable from other packages?
   */ 
  final boolean isPublic() throws VM_PragmaUninterruptible { 
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return (modifiers & ACC_PUBLIC) != 0; 
  }

  /**
   * Non-subclassable?
   */ 
  public final boolean isFinal() throws VM_PragmaUninterruptible { 
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return (modifiers & ACC_FINAL) != 0; 
  }

  /**
   * Non-instantiable?
   */ 
  final boolean isAbstract() throws VM_PragmaUninterruptible { 
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return (modifiers & ACC_ABSTRACT) != 0; 
  }

  /**
   * Use new-style "invokespecial" semantics for method calls in this class?
   */ 
  final boolean isSpecial() throws VM_PragmaUninterruptible { 
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return (modifiers & ACC_SPECIAL) != 0; 
  }

  public int getModifiers() {
    return modifiers;
  }

  /**
   * Name of source file from which class was compiled - 
   * something like "c:\java\src\java\lang\Object.java".
   * (null --> "unknown - wasn't recorded by compiler").
   */
  public final VM_Atom getSourceName() { 
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return sourceName;
  }

  /**
   * Superclass of this class (null means "no superclass", 
   * ie. class is "java/lang/Object").
   */
  public final VM_Class getSuperClass() throws VM_PragmaUninterruptible { 
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return superClass;
  }

  /**
   * Currently loaded classes that "extend" this class.
   */ 
  public final VM_Class[] getSubClasses() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return subClasses;
  }

  /**
   * Interfaces implemented directly by this class 
   * (ie. not including superclasses).
   */
  public final VM_Class[] getDeclaredInterfaces() throws VM_PragmaUninterruptible { 
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return declaredInterfaces;
  }

  /**
   * Fields defined directly by this class (ie. not including superclasses).
   */ 
  public final VM_Field[] getDeclaredFields() throws VM_PragmaUninterruptible { 
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return declaredFields;
  }

  /**
   * Methods defined directly by this class (ie. not including superclasses).
   */
  public final VM_Method[] getDeclaredMethods() throws VM_PragmaUninterruptible { 
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return declaredMethods;
  }

  /**
   * Static initializer method for this class (null -> no static initializer
   *  or initializer already been run).
   */ 
  final VM_Method getClassInitializerMethod() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return classInitializerMethod;
  }

  /** 
   * Find description of a field of this class.
   * @param fieldName field name - something like "foo"
   * @param fieldDescriptor field descriptor - something like "I"
   * @return description (null --> not found)
   */ 
  final VM_Field findDeclaredField(VM_Atom fieldName, VM_Atom fieldDescriptor) {
    if (VM.VerifyAssertions) VM._assert(isLoaded());
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
  public final VM_Method findDeclaredMethod(VM_Atom methodName, 
                                     VM_Atom methodDescriptor) {
    if (VM.VerifyAssertions) VM._assert(isLoaded());
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
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    // jtoc slot number --> jtoc offset
    return constantPool[constantPoolIndex] << 2; 
  }

  /**
   * Get description of a literal constant.
   */ 
  public final byte getLiteralDescription(int constantPoolIndex) {
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    // jtoc slot number --> description
    return VM_Statics.getSlotDescription(constantPool[constantPoolIndex]); 
  }

  /**
   * Get contents of a "typeRef" constant pool entry.
   * @return id of type that was referenced, for use 
   * by "VM_TypeDictionary.getValue()"
   */
  public final int getTypeRefId(int constantPoolIndex) throws VM_PragmaUninterruptible {
    return constantPool[constantPoolIndex];
  }

  /**
   * Get contents of a "typeRef" constant pool entry.
   * @return type that was referenced
   */
  public final VM_Type getTypeRef(int constantPoolIndex) throws VM_PragmaUninterruptible {
    return VM_TypeDictionary.getValue(getTypeRefId(constantPoolIndex));
  }

  /**
   * Get contents of a "fieldRef" constant pool entry.
   * @return id of field that was referenced, for use by 
   * "VM_FieldDictionary.getValue()"
   */
  public final int getFieldRefId(int constantPoolIndex) throws VM_PragmaUninterruptible {
    return constantPool[constantPoolIndex];
  }

  /**
   * Get contents of a "fieldRef" constant pool entry.
   * @return field that was referenced
   */
  public final VM_Field getFieldRef(int constantPoolIndex) throws VM_PragmaUninterruptible {
    return VM_FieldDictionary.getValue(constantPool[constantPoolIndex]);
  }

  /**
   * Get contents of a "methodRef" constant pool entry.
   * @return id of method that was referenced, for use by 
   * "VM_MethodDictionary.getValue()"
   */ 
  public final int getMethodRefId(int constantPoolIndex) throws VM_PragmaUninterruptible {
    return constantPool[constantPoolIndex];
  }

  /**
   * Get contents of a "methodRef" constant pool entry.
   * @return method that was referenced
   */
  public final VM_Method getMethodRef(int constantPoolIndex) throws VM_PragmaUninterruptible {
    return VM_MethodDictionary.getValue(constantPool[constantPoolIndex]);
  }

  /**
   * Get contents of a "utf" constant pool entry.
   */
  final VM_Atom getUtf(int constantPoolIndex) throws VM_PragmaUninterruptible {
    return VM_AtomDictionary.getValue(constantPool[constantPoolIndex]);
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
    if (!isLoaded())
      return false;
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
  //                         Section 2.                                 //
  // The following are available after the class has been "resolved".   //
  //--------------------------------------------------------------------//

  /**
   * Which class loader loaded this class?  (null => system class loader):CRA 
   * final ClassLoader 
   */
  public ClassLoader getClassLoader () { 
    return classloader; 
  } 

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
   * Virtually dispatched methods of this class 
   * (composed with supertypes, if any).
   */
  public final VM_Method[] getVirtualMethods() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return virtualMethods;
  }

  /**
   * Total size, in bytes, of an instance of this class 
   * (including object header).
   */
  public final int getInstanceSize() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return instanceSize;
  }

  final int getInstanceSizeInternal() throws VM_PragmaUninterruptible {
    return instanceSize;
  }

  /**
   * Add a field to the object; only meant to be called from VM_ObjectModel et al.
   * must be called when lock on class object is already held (ie from resolve).
   */
  final void increaseInstanceSize(int numBytes) throws VM_PragmaUninterruptible {
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
  final VM_Method findInitializerMethod(VM_Atom memberDescriptor) {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    VM_Method methods[] = getStaticMethods();
    for (int i = 0, n = methods.length; i < n; ++i) {
      VM_Method method = methods[i];
      if (method.isObjectInitializer() && 
          method.getDescriptor() == memberDescriptor)
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
  //                          Section 3.                                //
  // The following are available after the class has been "instantiated"//
  //--------------------------------------------------------------------//

  //--------------------------------------------------------------------//
  //                          Section 4.                                //
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
   * Load, resolve, instantiate, and initialize specified class.
   * @param className class name - something like "java.lang.String"
   * @return class description
   */
  public static VM_Class forName(String className) 
    throws VM_ResolutionException {
    VM_Atom classDescriptor = VM_Atom.findOrCreateAsciiAtom
      (className.replace('.','/')).descriptorFromClassName();

    ClassLoader cl = VM_SystemClassLoader.getVMClassLoader();
    VM_Class cls = 
	VM_ClassLoader.findOrCreateType(classDescriptor, cl).asClass();

    cls.load();
    cls.resolve();
    cls.instantiate();
    cls.initialize();

    return cls;
  }

  /**
   * Find specified method using "invokespecial" lookup semantics.
   * <p> There are three kinds of "special" method invocation:
   * <ul>
   *   <li> an instance initializer method, eg. <init>
   *   <li> a non-static but private and/or final method in the current class
   *   <li> a non-static method for which the non-overridden (superclass) 
   *   version is desired
   * </ul>
   *
   * @param sought method sought
   * @return method found (null --> not found)
   */ 
  public static VM_Method findSpecialMethod(VM_Method sought) {
    if (sought.isObjectInitializer())
      return sought;   // <init>

    VM_Class cls = sought.getDeclaringClass();
    if (!cls.isSpecial())
      return sought;   // old-style invokespecial semantics

    for (; cls != null; cls = cls.getSuperClass()) {
      VM_Method found = cls.findDeclaredMethod(sought.getName(), 
                                               sought.getDescriptor());
      if (found != null)
	return found; // new-style invokespecial semantics
    }
    return null;        // not found
  }


  // getter and setter for constant pool
  final int[] getConstantPool () {
    return constantPool;
  }

  final void setConstantPool(int[] pool) {
    constantPool = pool;
  }

  //----------------//
  // implementation //
  //----------------//

  //
  // The following are always valid.
  //

  // add field to identify the class Loader for this class
  // 06/19/00 CRA:
  //
  ClassLoader  classloader; 

  //
  // The following are valid only when "state >= CLASS_LOADED".
  //
  private int[]        constantPool;
  private int          modifiers;
  private VM_Class     superClass;
  private VM_Class[]   subClasses;
  private VM_Class[]   declaredInterfaces;
  private VM_Field[]   declaredFields;
  private VM_Method[]  declaredMethods;
  private VM_Atom      sourceName;
  private VM_Method    classInitializerMethod;

  // Cannonical empty arrays
  private static final VM_Class[] emptyVMClass = new VM_Class[0];
  private static final VM_Field[] emptyVMField = new VM_Field[0];
  private static final VM_Method[] emptyVMMethod = new VM_Method[0];

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
  //
  // --- Method-dispatching information ---    //
  //
  /**
   * static methods of class
   */
  private VM_Method[]  staticMethods;          
  /**
   * virtual methods of class
   */
  private VM_Method[]  virtualMethods;         

  /**
   * method that overrides java.lang.Object.finalize()
   * null => class does not have a finalizer
   */
  private VM_Method    finalizeMethod;         

  //
  // The following are valid only when "state >= IS_INSTANTIATED".
  //

  /**
   * type and virtual method dispatch table for class
   */
  private Object[]     typeInformationBlock;   


  /**
   * To guarantee uniqueness, only the VM_ClassLoader class may 
   * construct VM_Class instances.
   * All VM_Class creation should be performed by calling 
   * "VM_ClassLoader.findOrCreate" methods.
   */ 
  private VM_Class() { }

  VM_Class(VM_Atom descriptor, int dictionaryId, ClassLoader classloader) {
    this.descriptor   = descriptor;
    this.dictionaryId = dictionaryId;
    this.tibSlot      = VM_Statics.allocateSlot(VM_Statics.TIB);
    this.subClasses   = emptyVMClass;
    this.classloader  = classloader;

    // install partial type information block 
    // (type-slot but no method-slots) for use in type checking.
    // later, during instantiate(), we'll replace it with full type 
    // information block (including method-slots).
    //
    if (VM.VerifyAssertions) VM._assert(TIB_TYPE_INDEX == 0);
    Object[] tib = VM_RuntimeStructures.newTIB(1);
    tib[TIB_TYPE_INDEX] = this;
    VM_Statics.setSlotContents(tibSlot, tib);
  }

  // for close world testing
  public static boolean classLoadingDisabled = false;

  /**
   * Read this class's description from its .class file.
   */ 
  public final synchronized void load() throws VM_ResolutionException {
    if (isLoaded()) return;

    if (classLoadingDisabled) {
      throw new RuntimeException("ClassLoading Disabled : "
				 +this.getDescriptor());
    }

    if (VM.TraceClassLoading && VM.runningVM) 
      VM.sysWrite("VM_Class: (begin) load " + descriptor + "\n");

    VM_Thread myThread;
    
    try {
      classloader.loadClass(getName().toString());
    } catch (ClassNotFoundException e) { 
      // no .class file
      throw new VM_ResolutionException(descriptor, 
				       new NoClassDefFoundError
					 (descriptor.classNameFromDescriptor()));
    } catch (ClassFormatError e) { 
      // not really a .class file
      throw new VM_ResolutionException(descriptor, e);
    }

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("VM_Class: (end)   load " + descriptor + "\n");
  }

  /**
   * Read this class's description from specified data stream.
   */ 
  final synchronized void load(DataInputStream input) throws ClassFormatError, IOException {
    if (isLoaded()) return;

    if (classLoadingDisabled) {
      throw new RuntimeException("ClassLoading Disabled : "
				 +this.getDescriptor());
    }

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("VM_Class: (begin) load file " 
                                          + descriptor + "\n");
    if (VM.VerifyAssertions) VM._assert(state == CLASS_VACANT);

    if (input.readInt() != 0xCAFEBABE)
      throw new ClassFormatError("bad magic number");

    if (input.readUnsignedShort() != 3 || input.readUnsignedShort() != 45)
      throw new ClassFormatError("bad version number");

    //
    // pass 1: read constant pool
    //
    int  tmpPool[] = new int[input.readUnsignedShort()];
    byte tmpTags[] = new byte[tmpPool.length];

    // note: slot 0 is unused
    for (int i = 1; i < tmpPool.length; ++i) {
      switch (tmpTags[i] = input.readByte())
	{
	case TAG_UTF: 
	  {
	    byte utf[] = new byte[input.readUnsignedShort()];
	    input.readFully(utf);
	    tmpPool[i] = VM_Atom.findOrCreateAtomId(utf);
	    break;  
	  }

	case TAG_UNUSED:
	  if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
	  break;

	case TAG_INT:
	  tmpPool[i] = VM_Statics.findOrCreateIntLiteral(input.readInt());
	  break;

	case TAG_FLOAT:
	  tmpPool[i] = VM_Statics.findOrCreateFloatLiteral(input.readInt());
	  break;

	case TAG_LONG:
	  tmpPool[i++] = VM_Statics.findOrCreateLongLiteral(input.readLong());
	  break;

	case TAG_DOUBLE:
	  tmpPool[i++] = VM_Statics.findOrCreateDoubleLiteral(input.readLong());
	  break;

	case TAG_TYPEREF:
	  tmpPool[i] = input.readUnsignedShort();
	  break;

	case TAG_STRING:
	  tmpPool[i] = input.readUnsignedShort();
	  break;

	case TAG_FIELDREF:
	case TAG_METHODREF:
	case TAG_INTERFACE_METHODREF: 
	  {
	    int classDescriptorIndex         = input.readUnsignedShort();
	    int memberNameAndDescriptorIndex = input.readUnsignedShort();
	    tmpPool[i] = (classDescriptorIndex << 16) | 
              memberNameAndDescriptorIndex;
	    break; 
	  }

	case TAG_MEMBERNAME_AND_DESCRIPTOR:
	  {
	    int memberNameIndex = input.readUnsignedShort();
	    int descriptorIndex = input.readUnsignedShort();
	    tmpPool[i] = (memberNameIndex << 16) | descriptorIndex;
	    break;  
	  }
	
	default:
	  throw new ClassFormatError("bad constant pool");
	}
    }

    //
    // pass 2: post-process type, method, field, and string constant 
    // pool entries
    //         (we must do this in a second pass because of forward references)
    //

    try {
      constantPool = new int[tmpPool.length];
      for (int i = 1, n = tmpPool.length; i < n; ++i) {
	switch (tmpTags[i])
	  {
	  case TAG_UTF: // in: atom dictionary id
	    constantPool[i] = tmpPool[i];
	    break; // out: atom dictionary id

	  case TAG_UNUSED:
	    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
	    break;

	  case TAG_INT: 
	  case TAG_FLOAT: // in: jtoc slot number
	    constantPool[i] = tmpPool[i];
	    break; // out: jtoc slot number
	    
	  case TAG_LONG:
	  case TAG_DOUBLE: // in: jtoc slot number
	    constantPool[i] = tmpPool[i];
	    ++i;
	    break; // out: jtoc slot number

	  case TAG_TYPEREF: { // in: utf index
	    VM_Atom typeName = VM_AtomDictionary.getValue(tmpPool[tmpPool[i]]);
	    if (typeName.isArrayDescriptor())
	      constantPool[i] = VM_ClassLoader.findOrCreateTypeId(typeName, classloader);
	    else
	      constantPool[i] = VM_ClassLoader.findOrCreateTypeId
		(typeName.descriptorFromClassName(), classloader);
	    break; } // out: type dictionary id

	  case TAG_STRING: 
	    { // in: utf index
	      int utfIndex = tmpPool[i];
	      constantPool[i] = VM_Statics.findOrCreateStringLiteral
		(VM_AtomDictionary.getValue(tmpPool[utfIndex]));
	      break; 
	    } // out: jtoc slot number

	  case TAG_FIELDREF:
	  case TAG_METHODREF:
	  case TAG_INTERFACE_METHODREF: 
	    { // in: classname+membername+memberdescriptor indices
	      int bits                         = tmpPool[i];
	      int classNameIndex               = (bits >> 16) & 0xffff;
	      int memberNameAndDescriptorIndex = bits & 0xffff;
	      int memberNameAndDescriptorBits  = tmpPool[memberNameAndDescriptorIndex];
	      int memberNameIndex              = (memberNameAndDescriptorBits >> 16) & 0xffff;
	      int memberDescriptorIndex        = (memberNameAndDescriptorBits       ) & 0xffff;

	      VM_Atom className        = VM_AtomDictionary.getValue
		(tmpPool[tmpPool[classNameIndex]]);
	      VM_Atom classDescriptor  = className.descriptorFromClassName();
	      VM_Atom memberName       = VM_AtomDictionary.getValue
		(tmpPool[memberNameIndex]);
	      VM_Atom memberDescriptor = VM_AtomDictionary.getValue
		(tmpPool[memberDescriptorIndex]);

	      constantPool[i] = (tmpTags[i] == TAG_FIELDREF)
		? VM_ClassLoader.findOrCreateFieldId(classDescriptor, memberName, memberDescriptor, classloader)
		: VM_ClassLoader.findOrCreateMethodId(classDescriptor, memberName, memberDescriptor, classloader);
	      break; } // out: field or method dictionary id
	    
	  case TAG_MEMBERNAME_AND_DESCRIPTOR: // in: member+descriptor indices
	    constantPool[i] = -1;
	    break; // out: nothing 
	    // (this constant pool entry is no longer needed)

	  default:
	    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
	  }
      }
    } catch (java.io.UTFDataFormatException x) {
      throw new ClassFormatError(x.toString());
    }
    
    modifiers         = input.readUnsignedShort();
    VM_Type myType    = getTypeRef(input.readUnsignedShort()); 
    if (myType != this) { 
      // eg. file contains a different class than would be 
      // expected from its .class file name
      throw new ClassFormatError("expected class \"" + this 
                                 + "\" but found \"" + myType + "\"");
    }
    VM_Type superType = getTypeRef(input.readUnsignedShort()); // possibly null
    if (superType != null) {
      superClass = superType.asClass();
      superClass.addSubClass(this);
    }

    int numInterfaces = input.readUnsignedShort();
    if (numInterfaces == 0) {
      declaredInterfaces = emptyVMClass;
    } else {
      declaredInterfaces = new VM_Class[numInterfaces];
      for (int i = 0, n = declaredInterfaces.length; i < n; ++i)
	declaredInterfaces[i] = getTypeRef(input.readUnsignedShort()).asClass();
    }

    int numFields = input.readUnsignedShort();
    if (numFields == 0) {
      declaredFields = emptyVMField;
    } else {
      declaredFields = new VM_Field[numFields];
      for (int i = 0, n = declaredFields.length; i < n; ++i) {
	int      modifiers       = input.readUnsignedShort();
	VM_Atom  fieldName       = VM_AtomDictionary.getValue(constantPool[input.readUnsignedShort()]);
	VM_Atom  fieldDescriptor = VM_AtomDictionary.getValue(constantPool[input.readUnsignedShort()]);
	VM_Field field           = VM_ClassLoader.findOrCreateField(getDescriptor(), fieldName, fieldDescriptor, classloader);
	
	field.load(input, modifiers);
	declaredFields[i] = field;
      }
    }

    int numMethods = input.readUnsignedShort();
    if (numMethods == 0) {
      declaredMethods = emptyVMMethod;
    } else {
      declaredMethods = new VM_Method[numMethods];
      for (int i = 0, n = declaredMethods.length; i < n; ++i) {
	int       modifiers        = input.readUnsignedShort();
	VM_Atom   methodName       = VM_AtomDictionary.getValue(constantPool[input.readUnsignedShort()]);
	VM_Atom   methodDescriptor = VM_AtomDictionary.getValue(constantPool[input.readUnsignedShort()]);
	VM_Method method           = VM_ClassLoader.findOrCreateMethod(getDescriptor(), methodName, methodDescriptor, classloader);
	
	method.load(input, modifiers);
	declaredMethods[i] = method;
	if (method.isClassInitializer())
	  classInitializerMethod = method;
      }
    }

    for (int i = 0, n = input.readUnsignedShort(); i < n; ++i) {
      VM_Atom attName   = getUtf(input.readUnsignedShort());
      int     attLength = input.readInt();

      // Class attributes
      if (attName == VM_ClassLoader.sourceFileAttributeName && attLength == 2) {
	sourceName = getUtf(input.readUnsignedShort());
	continue;
      }

      if (attName == VM_ClassLoader.innerClassesAttributeName) {
	input.skipBytes(attLength);
	continue;
      }

      if (attName == VM_ClassLoader.deprecatedAttributeName) {
	input.skipBytes(attLength);
	continue;
      }

      input.skipBytes(attLength);
    }

    state = CLASS_LOADED;

    VM_Callbacks.notifyClassLoaded(this);

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("VM_Class: (end)   load file " + 
                                          descriptor + "\n");
  }

  /**
   * Generate size and offset information for members of this class and
   * allocate space in jtoc for static fields, static methods, and virtual 
   * method table. 
   * Side effects: superclasses and superinterfaces are resolved.
   */ 
  public final synchronized void resolve() throws VM_ResolutionException {
    if (isResolved())
      return;

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("VM_Class: (begin) resolve " 
                                          + descriptor + "\n");
    if (VM.VerifyAssertions) VM._assert(state == CLASS_LOADED);

    // load/resolve superclass
    //
    if (VM.verboseClassLoading) VM.sysWrite("[Loading superclasses of "+
                                            descriptor.classNameFromDescriptor()
                                            +"]\n");
    if (superClass != null) {
      superClass.load();
      superClass.resolve();
      depth = 1 + superClass.depth;
      thinLockOffset = superClass.thinLockOffset;
      instanceSize = superClass.instanceSize;
    } else {
      instanceSize = VM_ObjectModel.computeScalarHeaderSize(this);
    }
    for (int i=0; i<declaredInterfaces.length; i++) {
      declaredInterfaces[i].load();
      declaredInterfaces[i].resolve();
    }

    if (isSynchronizedObject() || this == VM_Type.JavaLangClassType)
      VM_ObjectModel.allocateThinLock(this);

    if (VM.verboseClassLoading) VM.sysWrite("[Preparing "+
                                            descriptor.classNameFromDescriptor()
                                            +"]\n");

    // build field and method lists for this class
    //
    {
      VM_FieldVector  staticFields   = new VM_FieldVector();
      VM_FieldVector  instanceFields = new VM_FieldVector();
      VM_MethodVector staticMethods  = new VM_MethodVector();
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

	if (method.isObjectInitializer() || method.isStatic())  {
	  VM_Callbacks.notifyMethodOverride(method, null);
	  staticMethods.addElement(method);
	  if (VM.VerifyUnint) {
	    if (!method.isInterruptible() && method.isSynchronized()) {
	      if (VM.ParanoidVerifyUnint || !VM_PragmaLogicallyUninterruptible.declaredBy(method)) {
		VM.sysWriteln("WARNING: "+method+" cannot be both uninterruptible and synchronized");
	      }
	    }
	  }
	  continue;
	}

	// Now deal with virtual methods
	if (method.isSynchronized()) {
	  VM_ObjectModel.allocateThinLock(this);
	  if (VM.VerifyUnint) {
	    if (!method.isInterruptible()) {
	      if (VM.ParanoidVerifyUnint || !VM_PragmaLogicallyUninterruptible.declaredBy(method)) {
		VM.sysWriteln("WARNING: "+method+" cannot be both uninterruptible and synchronized");
	      }
	    }
	  }
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

      this.staticFields   = staticFields.finish();
      this.instanceFields = instanceFields.finish();
      this.staticMethods  = staticMethods.finish();
      this.virtualMethods = virtualMethods.finish();
    }

    // allocate space for class fields
    //
    for (int i = 0, n = staticFields.length; i < n; ++i) {
      VM_Field field     = staticFields[i];
      VM_Type  fieldType = field.getType();
      byte     slotType;
      if (fieldType.isReferenceType())
	slotType = VM_Statics.REFERENCE_FIELD;
      ///	(SJF:: Note: we DO need to have a JTOC entry even 
      //               for final static
      ///	       private fields. (for serialization) )
      else if (fieldType.getStackWords() == 2)
	slotType = VM_Statics.WIDE_NUMERIC_FIELD;
      else
	slotType = VM_Statics.NUMERIC_FIELD;
      field.offset = (VM_Statics.allocateSlot(slotType) << 2);

      // (SJF): Serialization nastily accesses even final private static
      //	   fields via pseudo-reflection! So, we must shove the
      //	   values of final static fields into the JTOC.  Now
      //	   seems to be a good time.
      if (field.isFinal()) {
	setFinalStaticJTOCEntry(field,field.offset);
      }
    }

    // lay out instance fields
    //
    VM_ObjectModel.layoutInstanceFields(this);

    // count reference fields and update dynamic linking data structures
    int referenceFieldCount = 0;
    for (int i = 0, n = instanceFields.length; i < n; ++i) {
      VM_Field field     = instanceFields[i];
      if (field.getType().isReferenceType())
	referenceFieldCount += 1;
      // Should be ok to do here instead of in initialize, because
      // "new" will ensure that the class is instantiated before it 
      // creates an instance.
      VM_TableBasedDynamicLinker.setFieldOffset(field, field.offset);
    }

    // record offsets of those instance fields that contain references
    //
    referenceOffsets = new int[referenceFieldCount];
    for (int i = 0, j = 0, n = instanceFields.length; i < n; ++i) {
      VM_Field field = instanceFields[i];
      if (field.getType().isReferenceType())
	referenceOffsets[j++] = field.offset;
    }

    // Allocate space for static method pointers
    //
    for (int i = 0, n = staticMethods.length; i < n; ++i) {
      VM_Method method = staticMethods[i];
      if (method.isClassInitializer()) {
	method.offset = 0xdeadbeef; // should never be used.
      } else {
	method.offset = VM_Statics.allocateSlot(VM_Statics.METHOD) << 2;
      }
    }

    // create "type information block" and initialize its first four words
    //
    typeInformationBlock = VM_RuntimeStructures.newTIB(TIB_FIRST_VIRTUAL_METHOD_INDEX + virtualMethods.length);
    VM_Statics.setSlotContents(tibSlot, typeInformationBlock);
    typeInformationBlock[0] = this;
    if (VM.BuildForFastDynamicTypeCheck) {
      typeInformationBlock[TIB_SUPERCLASS_IDS_INDEX] = VM_DynamicTypeCheck.buildSuperclassIds(this);
      typeInformationBlock[TIB_DOES_IMPLEMENT_INDEX] = VM_DynamicTypeCheck.buildDoesImplement(this);
      // element type for arrays (empty for classes)
    }

    // lay out virtual method section of type information block 
    // (to be filled in by instantiate)
    for (int i = 0, n = virtualMethods.length; i < n; ++i) {
      VM_Method method = virtualMethods[i];
      method.offset = (TIB_FIRST_VIRTUAL_METHOD_INDEX + i) << 2;
      // Should be OK to do here instead of in initialize because 
      // "new" will ensure that the class is instantiated before 
      // it creates an instance
      VM_TableBasedDynamicLinker.setMethodOffset(method, method.offset);
    }

    // RCGC: Determine if class is inherently acyclic
    if (VM.BuildForConcurrentGC) {	
      acyclic = false;	// must initially be false for recursive types
      boolean foundCyclic = false;
      for (int i = 0; i < instanceFields.length; i++) {
        if (!instanceFields[i].getType().isAcyclicReference()) {
          foundCyclic = true; 
          break;
        }
      }
      if (!foundCyclic)
        acyclic = true;
    }

    state = CLASS_RESOLVED; // can't move this beyond "finalize" code block

    VM_Callbacks.notifyClassResolved(this);

    // check for a "finalize" method that overrides the one in java.lang.Object
    //
    VM_Method finalize = findVirtualMethod(VM_ClassLoader.StandardObjectFinalizerMethodName, 
                                           VM_ClassLoader.StandardObjectFinalizerMethodDescriptor);
    if (finalize.getDeclaringClass().getSuperClass() != null)
      finalizeMethod = finalize;
    else
      finalizeMethod = null;

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("VM_Class: (end)   resolve " + descriptor + "\n");
  }


  // RCGC: A reference to class is acyclic if the class is acyclic and final
  //    (otherwise the reference could be to a subsequently loaded cyclic subclass).
  //
  protected final boolean isAcyclicReference() throws VM_PragmaUninterruptible {
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
    int fieldIndex = fieldOffset >> 2;

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
      Object obj = VM_Statics.getSlotContentsAsObject(literalOffset>>2);
      VM_Statics.setSlotContents(fieldIndex,obj);
    } else if (field.getSize() == 4) {
      // copy one word from constant pool to JTOC
      int value = VM_Statics.getSlotContentsAsInt(literalOffset>>2);
      VM_Statics.setSlotContents(fieldIndex,value);
    } else {
      // copy two words from constant pool to JTOC
      long value = VM_Statics.getSlotContentsAsLong(literalOffset>>2);
      VM_Statics.setSlotContents(fieldIndex,value);
    }
  }

  /**
   * Copy the values of all static final fields into 
   * the JTOC.  Note: This method should only be run AFTER
   * the class initializer has run.
   */
  void setAllFinalStaticJTOCEntries() {
    if (VM.VerifyAssertions) VM._assert (isInitialized());
    VM_Field[] fields = getStaticFields();
    for (int i=0; i<fields.length; i++) {
      VM_Field f = fields[i];
      if (f.isFinal()) {
        setFinalStaticJTOCEntry(f,f.getOffset());
      }
    }
  }

  /**
   * Compile this class's methods, build type information block, populate jtoc.
   * Side effects: superclasses are instantiated.
   */
  public final synchronized void instantiate() {
    if (isInstantiated())
      return;

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("VM_Class: (begin) instantiate " 
                                          + descriptor + "\n");
    if (VM.VerifyAssertions) VM._assert(state == CLASS_RESOLVED);

    // instantiate superclass
    //
    if (superClass != null)
      superClass.instantiate();
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

    // compile static methods and put their addresses into jtoc
    for (int i = 0, n = staticMethods.length; i < n; ++i) {
      // don't bother compiling <clinit> here;
      // compile it right before we invoke it in initialize.
      // This also avoids putting clinit's in the bootimage.
      VM_Method method = staticMethods[i];
      if (!method.isClassInitializer()) {
	VM_Statics.setSlotContents(method.getOffset() >> 2, method.getCurrentInstructions());
      }
    }

    VM_InterfaceInvocation.initializeDispatchStructures(this);

    if (VM.writingBootImage) { 
      // host jvm will initialize this class as side effect of building
      // boot image, so we must set state as if initialize() had been called
      //
      for (int i = 0, n = staticFields.length; i < n; ++i) {
	VM_Field field = staticFields[i];
	VM_TableBasedDynamicLinker.setFieldOffset(field, field.getOffset());
      }
      for (int i = 0, n = staticMethods.length; i < n; ++i) {
	VM_Method method = staticMethods[i];
	VM_TableBasedDynamicLinker.setMethodOffset(method, method.getOffset());
      }
      state = CLASS_INITIALIZED; 
    } else {
      state = CLASS_INSTANTIATED;
    }

    VM_Callbacks.notifyClassInstantiated(this);
    if (VM.writingBootImage)
      VM_Callbacks.notifyClassInitialized(this);

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("VM_Class: (end)   instantiate " 
							  + descriptor + "\n");
  }

  /**
   * Execute this class's static initializer, <clinit>.
   * Side effects: superclasses are initialized, static fields receive 
   * initial values.
   */ 
  public final synchronized void initialize() {
    if (isInitialized())
      return;

    if (state == CLASS_INITIALIZING) {
      // recursion: <clinit> called something that called <clinit>
      // java language specification says results are undefined: user gets
      // static field values of either zero (default initial value) 
      // or whatever values happen to be initialized so far.
      // TODO: Thus by the logic of the above statement, we can set
      // the offsetTables _for_the_current_thread_ to non-zero values.
      // Unfortunately, we have global offsetTables and thus are backed into
      // a corner and have to make the non-zero values visible to all
      // threads. Then again, it isn't obvious that doing it on a 
      // per-thread basis would work either, since that could get us
      // into a deadlock if the program has cyclic clinits.
      for (int i = 0, n = staticFields.length; i < n; ++i) {
	VM_Field field = staticFields[i];
	VM_TableBasedDynamicLinker.setFieldOffset(field, field.getOffset());
      }
      for (int i = 0, n = staticMethods.length; i < n; ++i) {
	VM_Method method = staticMethods[i];
	VM_TableBasedDynamicLinker.setMethodOffset(method, method.getOffset());
      }
      return;
    }

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("VM_Class: (begin) initialize " + 
                                          descriptor + "\n");
    if (VM.VerifyAssertions) VM._assert(state == CLASS_INSTANTIATED);
    state = CLASS_INITIALIZING;
    if (VM.verboseClassLoading) VM.sysWrite("[Initializing "+
                                            descriptor.classNameFromDescriptor()
                                            +"]\n");

    // run super <clinit>
    //
    if (superClass != null)
      superClass.initialize();
    if (VM.runningVM) {
      for (int i=0; i<declaredInterfaces.length; i++) {
	declaredInterfaces[i].initialize();
      }
    }

    // run <clinit>
    //
    if (classInitializerMethod != null) {
      VM_CompiledMethod cm = classInitializerMethod.getCurrentCompiledMethod();
      while (cm == null) {
	classInitializerMethod.compile();
	cm = classInitializerMethod.getCurrentCompiledMethod();
      }

      if (VM.verboseClassLoading) VM.sysWrite("[Running static initializer for "
					      +descriptor.
					      classNameFromDescriptor()+"]\n");

      VM_Magic.invokeClassInitializer(cm.getInstructions());

      // <clinit> is no longer needed: reclaim space by removing references to it
      classInitializerMethod.invalidateCompiledMethod(cm);
      classInitializerMethod               = null;
    }

    // now that <clinit> has run, it's safe to fill in offset tables
    //
    for (int i = 0, n = staticFields.length; i < n; ++i) {
      VM_Field field = staticFields[i];
      VM_TableBasedDynamicLinker.setFieldOffset(field, field.getOffset());
    }
    for (int i = 0, n = staticMethods.length; i < n; ++i) {
      VM_Method method = staticMethods[i];
      VM_TableBasedDynamicLinker.setMethodOffset(method, method.getOffset());
    }

    // report that a class is about to be marked initialized to 
    // the opt compiler so it can invalidate speculative CHA optimizations
    // before an instance of this class could actually be created.
    //-#if RVM_WITH_OPT_COMPILER
    if (OptCLDepManager != null) OptCLDepManager.classInitialized(this);
    //-#endif

    state = CLASS_INITIALIZED;

    VM_Callbacks.notifyClassInitialized(this);

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("VM_Class: (end)   initialize " 
                                          + descriptor + "\n");
  }

  //-#if RVM_WITH_PREMATURE_CLASS_RESOLUTION
  // Initialize a preresolved class immediately prior to its first use.
  //
  static final void initializeClassIfNecessary (int classId) {
    VM_Class c = (VM_Class) VM_TypeDictionary.getValue(classId);
    if (c.isInitialized()) return;
    c.instantiate();
    c.initialize();
  }
  //-#endif


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
  public static OPT_ClassLoadingDependencyManager OptCLDepManager;
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
    int slot = m.getOffset() >>> 2;
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
    int offset = m.getOffset() >>> 2;
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

  static VM_Class getInterface(int id) {
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
