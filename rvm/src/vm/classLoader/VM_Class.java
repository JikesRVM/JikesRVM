/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.io.IOException;
import java.io.FileNotFoundException;
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
public class VM_Class extends VM_Type
  implements VM_Constants, VM_ClassLoaderConstants {
  //-----------//
  // interface //
  //-----------//

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
  public final int getStackWords() {
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
  public final boolean isInterface() { 
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return (modifiers & ACC_INTERFACE) != 0; 
  } 

  /**
   * Usable from other packages?
   */ 
  final boolean isPublic() { 
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return (modifiers & ACC_PUBLIC) != 0; 
  }

  /**
   * Non-subclassable?
   */ 
  final boolean isFinal()     { 
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return (modifiers & ACC_FINAL) != 0; 
  }

  /**
   * Non-instantiable?
   */ 
  final boolean isAbstract()  { 
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return (modifiers & ACC_ABSTRACT) != 0; 
  }

  /**
   * Use new-style "invokespecial" semantics for method calls in this class?
   */ 
  final boolean isSpecial() { 
    if (VM.VerifyAssertions) VM.assert(isLoaded());
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
  final VM_Atom getSourceName() { 
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return sourceName;
  }

  /**
   * Superclass of this class (null means "no superclass", 
   * ie. class is "java/lang/Object").
   */
  public final VM_Class getSuperClass() { 
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return superClass;
  }

  /**
   * Currently loaded classes that "extend" this class.
   */ 
  final VM_Class[] getSubClasses() {
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return subClasses;
  }

  /**
   * Interfaces implemented directly by this class 
   * (ie. not including superclasses).
   */
  public final VM_Class[] getDeclaredInterfaces() { 
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return declaredInterfaces;
  }

  /**
   * Fields defined directly by this class (ie. not including superclasses).
   */ 
  public final VM_Field[] getDeclaredFields() { 
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return declaredFields;
  }

  /**
   * Methods defined directly by this class (ie. not including superclasses).
   * TODO: must prevent user access.
   */
  public final VM_Method[] getDeclaredMethods() { 
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return declaredMethods;
  }

  /**
   * Static initializer method for this class (null -> no static initializer
   *  or initializer already been run).
   */ 
  final VM_Method getClassInitializerMethod() {
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return classInitializerMethod;
  }

  /** 
   * Find description of a field of this class.
   * @param fieldName field name - something like "foo"
   * @param fieldDescriptor field descriptor - something like "I"
   * @return description (null --> not found)
   */ 
  final VM_Field findDeclaredField(VM_Atom fieldName, VM_Atom fieldDescriptor) {
    if (VM.VerifyAssertions) VM.assert(isLoaded());
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
  final VM_Method findDeclaredMethod(VM_Atom methodName, 
                                     VM_Atom methodDescriptor) {
    if (VM.VerifyAssertions) VM.assert(isLoaded());
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
  final VM_Method findMainMethod() {
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
  final int getLiteralOffset(int constantPoolIndex) {
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    // jtoc slot number --> jtoc offset
    return constantPool[constantPoolIndex] << 2; 
  }

  /**
   * Get description of a literal constant.
   */ 
  final byte getLiteralDescription(int constantPoolIndex) {
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    // jtoc slot number --> description
    return VM_Statics.getSlotDescription(constantPool[constantPoolIndex]); 
  }

  /**
   * Get contents of a "typeRef" constant pool entry.
   * @return id of type that was referenced, for use 
   * by "VM_TypeDictionary.getValue()"
   */
  final int getTypeRefId(int constantPoolIndex) {
    return constantPool[constantPoolIndex];
  }

  /**
   * Get contents of a "typeRef" constant pool entry.
   * @return type that was referenced
   */
  final VM_Type getTypeRef(int constantPoolIndex) {
    return VM_TypeDictionary.getValue(getTypeRefId(constantPoolIndex));
  }

  /**
   * Get contents of a "fieldRef" constant pool entry.
   * @return id of field that was referenced, for use by 
   * "VM_FieldDictionary.getValue()"
   */
  final int getFieldRefId(int constantPoolIndex) {
    return constantPool[constantPoolIndex];
  }

  /**
   * Get contents of a "fieldRef" constant pool entry.
   * @return field that was referenced
   */
  final VM_Field getFieldRef(int constantPoolIndex) {
    return VM_FieldDictionary.getValue(constantPool[constantPoolIndex]);
  }

  /**
   * Get contents of a "methodRef" constant pool entry.
   * @return id of method that was referenced, for use by 
   * "VM_MethodDictionary.getValue()"
   */ 
  final int getMethodRefId(int constantPoolIndex) {
    return constantPool[constantPoolIndex];
  }

  /**
   * Get contents of a "methodRef" constant pool entry.
   * @return method that was referenced
   */
  final VM_Method getMethodRef(int constantPoolIndex) {
    return VM_MethodDictionary.getValue(constantPool[constantPoolIndex]);
  }

  /**
   * Get contents of a "utf" constant pool entry.
   */
  final VM_Atom getUtf(int constantPoolIndex) {
    return VM_AtomDictionary.getValue(constantPool[constantPoolIndex]);
  }

  /**
   * Should the methods of this class be compiled with 
   * thread switching prologue?
   * @see VM_Uninterruptible
   */ 
  final boolean isInterruptible () {
    VM_Class[] interfaces = getDeclaredInterfaces();
    for (int i = 0, n = interfaces.length; i < n; ++i)
      if (interfaces[i].isUninterruptibleType()) return false;
    return true; // does not (directly) implement VM_Uninterruptible
  }

  /**
   * Should the methods of this class be compiled with 
   */ 
  final boolean isBootImageInitialized () {
    VM_Class[] interfaces = getDeclaredInterfaces();
    for (int i = 0, n = interfaces.length; i < n; ++i)
	if (interfaces[i].getName().equals("VM_BootImageInitialization"))
	    return true;
    return false; // does not (directly) implement VM_BootImageInitialization
  }

  /**
   * Does this object implement the VM_SynchronizedObject interface?
   * @see VM_SynchronizedObject
   */ 
  final boolean isSynchronizedObject() {
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
  final boolean isDynamicBridge () {
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
  final boolean isBridgeFromNative() {
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
  final boolean isSaveVolatile() {
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
  public final boolean hasFinalizer() {
    if (VM.VerifyAssertions) VM.assert(isResolved());
    return (finalizeMethod != null);
  }

  /**
   * Get finalize method that overrides java.lang.Object.finalize(), 
   * if one exists
   */
  public final VM_Method getFinalizer() {
    if (VM.VerifyAssertions) VM.assert(isResolved());
    return finalizeMethod;
  }

  /**
   * Static fields of this class.
   * Values in these fields are shared by all class instances.
   */
  public final VM_Field[] getStaticFields() {
    if (VM.VerifyAssertions) VM.assert(isResolved());
    return staticFields;
  }

  /**
   * Non-static fields of this class (composed with supertypes, if any).
   * Values in these fields are distinct for each class instance.
   */
  public final VM_Field[] getInstanceFields() {
    if (VM.VerifyAssertions) VM.assert(isResolved());
    return instanceFields;
  }

  /**
   * Statically dispatched methods of this class.
   */
  public final VM_Method[] getStaticMethods() {
    if (VM.VerifyAssertions) VM.assert(isResolved());
    return staticMethods;
  }

  /**
   * Virtually dispatched methods of this class 
   * (composed with supertypes, if any).
   */
  public final VM_Method[] getVirtualMethods() {
    if (VM.VerifyAssertions) VM.assert(isResolved());
    return virtualMethods;
  }

  /**
   * Total size, in bytes, of an instance of this class 
   * (including object header).
   */
  public final int getInstanceSize() {
    if (VM.VerifyAssertions) VM.assert(isResolved());
    return instanceSize;
  }

  final int getInstanceSizeInternal() {
    return instanceSize;
  }

  /**
   * Add a field to the object; only meant to be called from VM_ObjectModel et al.
   * must be called when lock on class object is already held (ie from resolve).
   */
  final void increaseInstanceSize(int numBytes) {
    instanceSize += numBytes;
  }

  /**
   * Offsets of reference-containing instance fields of this class type.
   * Offsets are with respect to object pointer -- see VM_Field.getOffset().
   */
  public final int[] getReferenceOffsets() {
    if (VM.VerifyAssertions) VM.assert(isResolved());
    return referenceOffsets;
  }

  /**
   * Find specified virtual method description.
   * @param memberName   method name - something like "foo"
   * @param memberDescriptor method descriptor - something like "I" or "()I"
   * @return method description (null --> not found)
   */
  final VM_Method findVirtualMethod(VM_Atom memberName, 
                                    VM_Atom memberDescriptor) {
    if (VM.VerifyAssertions) VM.assert(isResolved());
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
  final VM_Method findStaticMethod(VM_Atom memberName, 
                                   VM_Atom memberDescriptor) {
    if (VM.VerifyAssertions) VM.assert(isResolved());
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
    if (VM.VerifyAssertions) VM.assert(isResolved());
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
  public final Object[] getTypeInformationBlock() {
    if (VM.VerifyAssertions) VM.assert(isResolved());
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
  static VM_Method findSpecialMethod(VM_Method sought) {
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

  // debug flag: CRA
  private static boolean DEBUG = false;
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
    this.subClasses   = new VM_Class[0];
    this.classloader  = classloader;

    // install partial type information block 
    // (type-slot but no method-slots) for use in type checking.
    // later, during instantiate(), we'll replace it with full type 
    // information block (including method-slots).
    //
    if (VM.VerifyAssertions) VM.assert(TIB_TYPE_INDEX == 0);
    Object[] tib = VM_RuntimeStructures.newTIB(1);
    tib[TIB_TYPE_INDEX] = this;
    VM_Statics.setSlotContents(tibSlot, tib);
  }

  /**
   * Read this class's description from its .class file.
   */ 
  public final synchronized void load() throws VM_ResolutionException {
    if (isLoaded())
      return;

    if (VM.TraceClassLoading && VM.runningVM) 
      VM.sysWrite("VM_Class: (begin) load " + descriptor + "\n");

    VM_Thread myThread;
    
    try {
      classloader.loadClass( getName().toString() );
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
    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("VM_Class: (begin) load file " 
                                          + descriptor + "\n");
    if (VM.VerifyAssertions) VM.assert(state == CLASS_VACANT);

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
	  if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
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
	    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
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
	    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
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

    declaredInterfaces = new VM_Class[input.readUnsignedShort()];
    for (int i = 0, n = declaredInterfaces.length; i < n; ++i)
      declaredInterfaces[i] = getTypeRef(input.readUnsignedShort()).asClass();

    declaredFields = new VM_Field[input.readUnsignedShort()];
    for (int i = 0, n = declaredFields.length; i < n; ++i) {
      int      modifiers       = input.readUnsignedShort();
      VM_Atom  fieldName       = VM_AtomDictionary.getValue(constantPool[input.readUnsignedShort()]);
      VM_Atom  fieldDescriptor = VM_AtomDictionary.getValue(constantPool[input.readUnsignedShort()]);
      VM_Field field           = VM_ClassLoader.findOrCreateField(getDescriptor(), fieldName, fieldDescriptor, classloader);
      
      field.load(input, modifiers);
      declaredFields[i] = field;
    }

    declaredMethods = new VM_Method[input.readUnsignedShort()];
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
    if (VM.VerifyAssertions) VM.assert(state == CLASS_LOADED);

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
	  continue;
	}

	// Now deal with virtual methods

	if (method.isSynchronized())
	  VM_ObjectModel.allocateThinLock(this);

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
	  VM_Callbacks.notifyMethodOverride(method,
					    (VM_Method)virtualMethods.
					    elementAt(superclassMethodIndex));
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
      method.offset = VM_Statics.allocateSlot(VM_Statics.METHOD) << 2;
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
  protected final boolean isAcyclicReference() {
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
    if (VM.VerifyAssertions) VM.assert (isInitialized());
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
    if (VM.VerifyAssertions) VM.assert(state == CLASS_RESOLVED);

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
      if (method.isCompiled()) {
	if (method.isPrivate())
	  // This must be the parents private method which we won't be
	  // invoking directly
	  typeInformationBlock[slot] = null;
	else
	  // inherited method already compiled, no point in going through 
	  // lazy invoker
	  typeInformationBlock[slot] = method.getMostRecentlyGeneratedInstructions();
      }
      else if (method.isNative())
	typeInformationBlock[slot] = VM_Method.getNativeMethodInvokerInstructions(); 
      else
	// new method
	typeInformationBlock[slot] = getInitialInstructions(method);  
    }

    // compile static methods and put their addresses into jtoc
    for (int i = 0, n = staticMethods.length; i < n; ++i) {
      // !!TODO: no need to put <clinit>'s instructions into the jtoc, 
      // because we never call the code from anywhere other than 
      // "VM_Class.initialize()", below.
      // this would save us a precious jtoc slot.

      VM_Method method = staticMethods[i];
      if (method.isNative())
        VM_Statics.setSlotContents(method.getOffset() >> 2, 
                                   VM_Method.getNativeMethodInvokerInstructions());
      else
        VM_Statics.setSlotContents(method.getOffset() >> 2, 
                                   getInitialInstructions(method));
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
    if (VM.VerifyAssertions) VM.assert(state == CLASS_INSTANTIATED);
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
      INSTRUCTION[] instructions;
      instructions = classInitializerMethod.compile();
      if (VM.verboseClassLoading) VM.sysWrite("[Running static initializer for "
					      +descriptor.
					      classNameFromDescriptor()+"]\n");
      VM_Magic.invokeClassInitializer(instructions);

      // <clinit> is no longer needed: reclaim space by removing references to it
      classInitializerMethod.clearMostRecentCompilation();
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
    // anyone who has registered interest
    reportInitialize();

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
  static OPT_ClassLoadingDependencyManager OptCLDepManager;
  //-#endif

  /**
   * This method is invoked from VM_Class.initialize() immediately before 
   * 'this' is marked as INITIALIZED.  
   */
  private void reportInitialize() {
    //-#if RVM_WITH_OPT_COMPILER
    if (OptCLDepManager != null)
      OptCLDepManager.classInitialized(this);
    //-#endif
  }

  /**
   * return a newly-compiled version of the method (if lazy compilation
   * is off) or the lazy compilation stub.
   */
  private INSTRUCTION[] getInitialInstructions(VM_Method m) {
    if (VM.BuildForLazyCompilation && !VM.writingBootImage)
      return VM_Method.getLazyMethodInvokerInstructions();
    else
      return m.compile(); // must compile now
  }

  /**
   * return a newly-compiled version of the method corresponding to the
   * given compiled method (if lazy compilation is off) or the lazy
   * compilation stub.
   */
  private INSTRUCTION[] getInitialInstructions(VM_CompiledMethod cm) {
    return getInitialInstructions(cm.getMethod());
  }

  /**
   * If cm is the current compiled version of a static method, then 
   * reset its jtoc slot to point to a newly-compiled version of the
   * method (if lazy compilation is off) or the lazy compilation stub.
   */
  void resetStaticMethod(VM_CompiledMethod cm, INSTRUCTION[] code) {
    if (VM.VerifyAssertions) VM.assert(isResolved());
    VM_Method m = cm.getMethod();
    if (VM.VerifyAssertions)
      VM.assert(m.isStatic() || m.isObjectInitializer() ||
                m.isClassInitializer());
    int slot = m.getOffset() >>> 2;
    if (VM_Statics.getSlotContentsAsObject(slot) == cm.getInstructions())
      VM_Statics.setSlotContents(slot, code);
  }

  /**
   * Given a static method, reset its jtoc slot to point to the given
   * instruction array
   */
  synchronized void resetStaticMethod(VM_Method m) {
    if (VM.VerifyAssertions) VM.assert(isResolved());
    if (VM.VerifyAssertions)
      VM.assert(m.isStatic() || m.isObjectInitializer() ||
                m.isClassInitializer());
    INSTRUCTION[] code = m.getMostRecentlyGeneratedInstructions();
    int slot = m.getOffset() >>> 2;
    VM_Statics.setSlotContents(slot, code);
  }

  /**
   * If cm is the current compiled version of a virtual method for this 
   * class tib then reset its tib slot to point to a newly-compiled
   * version of the method (if lazy compilation is off) or the lazy
   * compilation stub.
   */
  void resetTIBEntry(VM_CompiledMethod cm) {
    resetTIBEntry(cm, getInitialInstructions(cm));
  }
  synchronized void resetTIBEntry(VM_CompiledMethod cm, INSTRUCTION[] code) {
    if (VM.VerifyAssertions) VM.assert(isResolved());
    VM_Method m = cm.getMethod();
    if (VM.VerifyAssertions)
      VM.assert(!m.isStatic() && !m.isObjectInitializer() && 
                !m.isClassInitializer());
    int offset = m.getOffset() >>> 2;
    if (typeInformationBlock[offset] == cm.getInstructions()) {
      typeInformationBlock[offset] = code;
    }
    VM_InterfaceInvocation.resetTIBEntry(this, cm, code);
  }

  /**
   * Given a virtual method, reset its TIB entry to point to the given
   * instruction array
   */
  synchronized void resetTIBEntry(VM_Method m) {
    resetTIBEntry(m, m.getMostRecentlyGeneratedInstructions());
  }

  synchronized void resetTIBEntry(VM_Method m, INSTRUCTION[] code) {
    if (VM.VerifyAssertions) VM.assert(isResolved());
    if (VM.VerifyAssertions)
      VM.assert(!m.isStatic() && !m.isObjectInitializer() &&
                !m.isClassInitializer());
    int offset = m.getOffset() >>> 2;
    typeInformationBlock[offset] = code;
    VM_InterfaceInvocation.resetTIBEntry(this, m, code);
  }

  /**
   * If cm is the current compiled version of a virtual method, then 
   * reset its TIB slot in this class and all subclasses that don't
   * override the method to point to a newly-compiled version of the
   * method (if lazy compilation is off) or the lazy compilation stub.
   */
  private void resetVirtualMethod(VM_CompiledMethod cm) {
    resetVirtualMethod(cm, getInitialInstructions(cm));
  }

  private void resetVirtualMethod(VM_CompiledMethod cm, INSTRUCTION[] code) {
    if (!isResolved()) return;
    VM_Method m = cm.getMethod();
    VM_Method dm = findDeclaredMethod(m.getName(), m.getDescriptor());
    if (dm != null && dm != m) return;  // this method got overridden
    resetTIBEntry(cm, code);
    if (m.isPrivate()) return; // can't override
    VM_Class[] subClasses = getSubClasses(); 
    for (int i = 0; i < subClasses.length; i++)
      subClasses[i].resetVirtualMethod(cm, code);
  }

  synchronized void resetVirtualMethod(VM_Method m) {
    resetVirtualMethod( m, m.getMostRecentlyGeneratedInstructions() );
  }

  void resetVirtualMethod(VM_Method m, INSTRUCTION[] code) {
    if (!isResolved()) return;
    VM_Method dm = findDeclaredMethod(m.getName(), m.getDescriptor());
    if (dm != null && dm != m) return;  // this method got overridden
    resetTIBEntry(m, code);
    if (m.isPrivate()) return; // can't override
    VM_Class[] subClasses = getSubClasses(); 
    for (int i = 0; i < subClasses.length; i++)
      subClasses[i].resetVirtualMethod(m, code);
  }

  /**
   * If cm is the current compiled version of a method, then 
   * reset its TIB slot in this class (and all subclasses that don't
   * override the method) if it's virtual or its jtoc slot if it's
   * static to point to a newly-compiled version of the method (if lazy
   * compilation is off) or the lazy compilation stub.
   */
  void resetMethod(VM_CompiledMethod cm) {
    resetMethod(cm, getInitialInstructions(cm), true);
  }

  void resetMethod(VM_CompiledMethod cm, boolean recurse) {
    resetMethod(cm, getInitialInstructions(cm), recurse);
  }

  void resetMethod(VM_CompiledMethod cm, INSTRUCTION[] code, boolean recurse) {
    if (VM.VerifyAssertions) VM.assert(isResolved());
    VM_Method m = cm.getMethod();
    if (m.isStatic() || m.isObjectInitializer() || m.isClassInitializer()) {
      // invalidate jtoc slot
      resetStaticMethod(cm, code);
    } else {
      // invalidate TIB entry
      if (recurse)
        resetVirtualMethod(cm, code);
      else
        resetTIBEntry(cm, code);
    }
  }

  /**
   * Given a method, reset its TIB slot in this class (and all
   * subclasses that don't override the method) if it's virtual or its
   * jtoc slot if it's static to point to the given instruction array.
   */
  void resetMethod(VM_Method m, boolean recurse) {
    if (VM.VerifyAssertions) VM.assert(isResolved());
    if (m.isStatic() || m.isObjectInitializer() || m.isClassInitializer()) {
      // invalidate jtoc slot
      resetStaticMethod(m);
    } else {
      // invalidate TIB entry
      if (recurse)
        resetVirtualMethod(m);
      else
        resetTIBEntry(m);
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
  int getInterfaceId () {
    if (interfaceId == -1) {
      assignInterfaceId();
    }
    return interfaceId;
  }

  int getDoesImplementIndex() {
    return getInterfaceId() >>> 5;
  }

  int getDoesImplementBitMask() {
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
