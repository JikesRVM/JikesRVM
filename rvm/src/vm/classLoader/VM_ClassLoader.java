/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import java.util.StringTokenizer;
import java.io.*;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Hashtable;
import java.security.ProtectionDomain;

/**
 * Manufacture type descriptions as needed by the running virtual machine. <p>
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public class VM_ClassLoader implements VM_Constants, 
				       VM_ClassLoaderConstants {

  /**
   * Set list of places to be searched for vm classes and resources.
   * @param classPath path specification in standard "classpath" format
   */
  public static void setVmRepositories(String classPath) {
    vmRepositories = classPath;
  }

  public static String getVmRepositories() {
    return vmRepositories;
  }

  /**
   * Set list of places to be searched for application classes and resources.
   * @param classPath path specification in standard "classpath" format
   */
  public static void setApplicationRepositories(String classPath) {
    System.setProperty("java.class.path", classPath);
    applicationRepositories = classPath;
  }

  /**
   * Get list of places currently being searched for application 
   * classes and resources.
   * @return names of directories, .zip files, and .jar files
   */ 
  public static String getApplicationRepositories() {
    return applicationRepositories;
  }

  /**
   * Find a type description, or create one if this is a type we haven't 
   * seen before.
   * @param descriptor descriptor for desired type - 
   * something like "Ljava/lang/String;" or "[I" or "I"
   * @return type description
   */ 
  public static VM_Type findOrCreateType(VM_Atom descriptor, ClassLoader cl) {
    switch (descriptor.parseForTypeCode()) {
    case ClassTypeCode: 
    case ArrayTypeCode: 
      return VM_TypeDictionary.getValue(findOrCreateTypeId(descriptor, cl));
    case BooleanTypeCode:
      return VM_Type.BooleanType;
    case ByteTypeCode:
      return VM_Type.ByteType;
    case ShortTypeCode:
      return VM_Type.ShortType;
    case IntTypeCode:
      return VM_Type.IntType;
    case LongTypeCode:
      return VM_Type.LongType;
    case FloatTypeCode:
      return VM_Type.FloatType;
    case DoubleTypeCode:
      return VM_Type.DoubleType;
    case CharTypeCode:
      return VM_Type.CharType;
    case VoidTypeCode:
      return VM_Type.VoidType;
    default:
      VM._assert(NOT_REACHED);
      return null;
    }
  }

  /**
   * Find a type dictionary id, or create one if this is a type we haven't 
   * seen before.
   * @param descriptor descriptor for desired type - 
   * something like "Ljava/lang/String;" or "[I" or "I"
   * @return type dictionary id
   */ 
  static int findOrCreateTypeId(VM_Atom descriptor, ClassLoader classloader) {
    int     typeId = VM_TypeDictionary.findOrCreateId(descriptor, null);
    VM_Type type   = VM_TypeDictionary.getValue(typeId);
    if (type != null) {
	if (VM.runningVM && ! type.isLoaded()) type.setClassLoader(classloader);
	return typeId;
    } else if (descriptor.isArrayDescriptor()) { // new array type
      VM_Array ary = new VM_Array(descriptor, typeId, classloader);
      VM_TypeDictionary.setValue(typeId, ary);
      return typeId;
    } else { 
      // new class type
      VM_Class cls = new VM_Class(descriptor, typeId, classloader);
      VM_TypeDictionary.setValue(typeId, cls);
      return typeId;
    }
  }

  /**
   * Find a primitive type description, 
   * or create one if this is a type we haven't seen before.
   * @param name name for desired type       - something like "void"
   * @param descriptor descriptor for desired type - something like "V"
   * @return type description
   */
  public static VM_Type findOrCreatePrimitiveType(VM_Atom name, VM_Atom descriptor) {
    int typeId = VM_TypeDictionary.findOrCreateId(descriptor, null);
    VM_Type type = VM_TypeDictionary.getValue(typeId);
    if (type == null)
      VM_TypeDictionary.setValue(typeId, type = new VM_Primitive(name, descriptor, typeId));
    return type;
  }

  /**
   * Short term migration aid as we phase out the global name space
   * @param dictId the dictionaryId of a type
   * @return the corresponding VM_Type object
   */
  public static VM_Type getTypeFromId(int id) {
    return VM_TypeDictionary.getValue(id);
  }
  public static int numTypes() {
    return VM_TypeDictionary.getNumValues();
  }
  public static VM_Type[] getTypes() {
    return VM_TypeDictionary.getValues();
  }

  public static void spaceReport() {
    int atomBytes = 0;
    for (int i=1; i<VM_AtomDictionary.getNumValues(); i++) {
      VM_Atom val = VM_AtomDictionary.getValue(i);
      if (val != null) atomBytes += val.length();
    }
    VM.sysWriteln("\t[B in VM_Atoms ",atomBytes);
  }

  /**
   * Find an interface signature id, or create one if this is an 
   * interface signature we haven't seen before.
   * @param methRef method reference of the target interface method
   * @return interface signature id
   */ 
  public static int findOrCreateInterfaceMethodSignatureId(VM_MemberReference methRef) {
    VM_InterfaceMethodSignature key = 
      new VM_InterfaceMethodSignature(methRef.getMemberName(), methRef.getDescriptor());
    int id = VM_InterfaceMethodSignatureDictionary.findOrCreateId(key, UNRESOLVED_INTERFACE_METHOD_OFFSET);
    return id;
  }

  /**
   * Load a dynamic library
   * @param libname the name of the library to load.
   */
  public static void load(String libname) {
    currentDynamicLibraryId++;

    if (currentDynamicLibraryId>=(dynamicLibraries.length-1))
	dynamicLibraries = 
	    growArray(dynamicLibraries, currentDynamicLibraryId << 1); 
    
    if (VM.VerifyAssertions)
	VM._assert(dynamicLibraries[currentDynamicLibraryId] == null);
    
    dynamicLibraries[currentDynamicLibraryId] = new VM_DynamicLibrary(libname);
  }

  /**
   * Load a dynamic library
   * @param libname the name of the library to load.
   */
  public static void loadLibrary(String libname) {
    currentDynamicLibraryId++;
    if (currentDynamicLibraryId>=(dynamicLibraries.length-1))
      dynamicLibraries = 
	  growArray(dynamicLibraries, currentDynamicLibraryId << 1); 

    if (VM.VerifyAssertions)
	VM._assert(dynamicLibraries[currentDynamicLibraryId] == null);

    //-#if RVM_WITH_GNU_CLASSPATH    
    String platformLibName = System.mapLibraryName( libname );
    //-#else
    // this is ugly, but will go away soon anyway
    String platformLibName;
    if (VM.BuildForLinux)
	platformLibName = "lib" + libname + ".so";
    else if (VM.BuildForAix)
	platformLibName = "lib" + libname + ".a";
    else {
	platformLibName = null;
	VM._assert(NOT_REACHED);
    }
    //-#endif

    StringTokenizer javaLibDirs =
	new StringTokenizer(javaLibPath, File.pathSeparator, false);

    while (javaLibDirs.hasMoreElements()) {
	String javaLibDir = javaLibDirs.nextToken();
	File javaLib = new File(javaLibDir, platformLibName);
	
	if (javaLib.exists()) {
	    dynamicLibraries[currentDynamicLibraryId] = 
		new VM_DynamicLibrary(javaLib.getPath());

	    return;
	}
    }

    throw new UnsatisfiedLinkError("Cannot find library " + libname);
  }
    
  static VM_DynamicLibrary[] getDynamicLibraries() {
    return dynamicLibraries;
  }

  //----------------//
  // implementation //
  //----------------//

  // Places from which to load .class files.
  //
  private static String applicationRepositories;
  private static String vmRepositories;

  // Names of special methods.
  //
  static VM_Atom StandardClassInitializerMethodName;        // "<clinit>"
  static VM_Atom StandardClassInitializerMethodDescriptor;  // "()V"

  public static VM_Atom StandardObjectInitializerMethodName;       // "<init>"
  public static VM_Atom StandardObjectInitializerMethodDescriptor; // "()V"

  static VM_Atom StandardObjectFinalizerMethodName;         // "finalize"
  static VM_Atom StandardObjectFinalizerMethodDescriptor;   // "()V"

  // Names of .class file attributes.
  //
  static VM_Atom codeAttributeName;                   // "Code"
  static VM_Atom constantValueAttributeName;          // "ConstantValue"
  static VM_Atom lineNumberTableAttributeName;        // "LineNumberTable"
  static VM_Atom exceptionsAttributeName;             // "Exceptions"
  static VM_Atom sourceFileAttributeName;             // "SourceFile"
  static VM_Atom localVariableTableAttributeName;     // "LocalVariableTable"
  static VM_Atom deprecatedAttributeName;             // "Deprecated"
  static VM_Atom innerClassesAttributeName;           // "InnerClasses"
  static VM_Atom syntheticAttributeName;              // "Synthetic"
  static VM_Atom arrayNullCheckAttributeName;         // "ArrayNullCheckAttribute"

  /**
   * Dynamic libraries for native code
   * Note: this is static for now, but it needs to be a list per class loader
   */
  private static VM_DynamicLibrary[] dynamicLibraries;

  /**
   * Index of most recently allocated slot in dynamicLibraries.
   */
  private static int currentDynamicLibraryId = 0;

  /**
   * Initialize for bootimage.
   */
  public static void init(String vmClassPath) {
    // specify place where vm classes and resources live
    //
    setVmRepositories(vmClassPath);
    applicationRepositories = null;
    VM_SystemClassLoader.boot();

    // create special method- and attribute- names
    //
    StandardClassInitializerMethodName        = VM_Atom.findOrCreateAsciiAtom("<clinit>");
    StandardClassInitializerMethodDescriptor  = VM_Atom.findOrCreateAsciiAtom("()V");

    StandardObjectInitializerMethodName       = VM_Atom.findOrCreateAsciiAtom("<init>");
    StandardObjectInitializerMethodDescriptor = VM_Atom.findOrCreateAsciiAtom("()V");

    StandardObjectFinalizerMethodName         = VM_Atom.findOrCreateAsciiAtom("finalize");
    StandardObjectFinalizerMethodDescriptor   = VM_Atom.findOrCreateAsciiAtom("()V");

    codeAttributeName                   = VM_Atom.findOrCreateAsciiAtom("Code");
    constantValueAttributeName          = VM_Atom.findOrCreateAsciiAtom("ConstantValue");
    lineNumberTableAttributeName        = VM_Atom.findOrCreateAsciiAtom("LineNumberTable");
    exceptionsAttributeName             = VM_Atom.findOrCreateAsciiAtom("Exceptions");
    sourceFileAttributeName             = VM_Atom.findOrCreateAsciiAtom("SourceFile");
    localVariableTableAttributeName     = VM_Atom.findOrCreateAsciiAtom("LocalVariableTable");
    deprecatedAttributeName             = VM_Atom.findOrCreateAsciiAtom("Deprecated");
    innerClassesAttributeName           = VM_Atom.findOrCreateAsciiAtom("InnerClasses");
    syntheticAttributeName              = VM_Atom.findOrCreateAsciiAtom("Synthetic");
    arrayNullCheckAttributeName		= VM_Atom.findOrCreateAsciiAtom("ArrayNullCheckAttribute");

    dynamicLibraries = new VM_DynamicLibrary[0];

    VM_Type.init();

    //-#if !RVM_WITH_GNU_CLASSPATH
    com.ibm.oti.vm.AbstractClassLoader.setBootstrapClassLoader(VM_SystemClassLoader.getVMClassLoader());
    //-#endif
  }

    private static String javaLibPath;

    private static void setJavaLibPath() {
	javaLibPath = VM_CommandLineArgs.getEnvironmentArg("java.library.path");
	if (javaLibPath == null) javaLibPath="";

	//-#if !RVM_WITH_GNU_CLASSPATH
	// an ugly hack that will go away soon
	javaLibPath = javaLibPath + File.pathSeparator + VM_CommandLineArgs.getEnvironmentArg("rvm.build");
	//-#endif
    }

    public static String getJavaLibPath() {
	return javaLibPath;
    } 

    private static String systemNativePath;

    private static void setSystemNativePath() {
	systemNativePath = VM_CommandLineArgs.getEnvironmentArg("rvm.build");
    }

    public static String getSystemNativePath() {
	return systemNativePath;
    } 

  /**
   * Initialize for execution.
   * @param vmClasses name of directory containing vm .class and .zip/.jar 
   * files (null -> use values specified by setVmRepositories() when 
   * bootimage was created)
   * @return nothing
   */
  public static void boot(String vmClasses) {      
    setVmRepositories(vmClasses);
    setSystemNativePath();
    setJavaLibPath();
    currentDynamicLibraryId = 0;
    dynamicLibraries = new VM_DynamicLibrary[0];
  }

  /**
   * Expand an array.
   */ 
  private static VM_DynamicLibrary[] growArray(VM_DynamicLibrary[] array, 
                                               int newLength) {
    VM_DynamicLibrary[] newarray = VM_Interface.newContiguousDynamicLibraryArray(newLength);
    for (int i = 0, n = array.length; i < n; ++i)
      newarray[i] = array[i];

    VM_Magic.sync();
    return newarray;
  }

  public static final void resolveClassInternal(Class clazz) {
    VM_Type cls = java.lang.JikesRVMSupport.getTypeForClass( clazz );
    try {
      cls.resolve();
    } catch (VM_ResolutionException e) { 
      VM.sysWrite("ERROR: DROPPING EXCEPTION: "+e+" ON THE FLOOR\n");
    }
    cls.instantiate();
    cls.initialize();
  }

  public static final Class defineClassInternal(String className, 
                                                byte[] classRep, 
                                                int offset, 
                                                int length, 
                                                ClassLoader classloader, 
                                                ProtectionDomain pd) throws ClassFormatError {
    Class c = defineClassInternal(className, new ByteArrayInputStream(classRep, offset, length), classloader);
    java.lang.JikesRVMSupport.setClassProtectionDomain(c, pd);
    return c;
  }

  public static final Class defineClassInternal(String className, 
                                                byte[] classRep, 
                                                int offset, 
                                                int length, 
                                                ClassLoader classloader) throws ClassFormatError {
    return defineClassInternal(className, new ByteArrayInputStream(classRep, offset, length), classloader);
  }

  public static final Class defineClassInternal(String className, 
                                                InputStream is, 
                                                ClassLoader classloader, 
                                                ProtectionDomain pd) throws ClassFormatError {
    Class c = defineClassInternal(className, is, classloader);
    java.lang.JikesRVMSupport.setClassProtectionDomain(c, pd);
    return c;
  }

  public static final Class defineClassInternal(String className, 
                                                InputStream is, 
                                                ClassLoader classloader) throws ClassFormatError {

    VM_Atom classDescriptor = null;
    if (className != null)
	classDescriptor = VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();

    if (VM.TraceClassLoading  && VM.runningVM)
	VM.sysWrite("loading " + classDescriptor + " with " + classloader);
      
    try {
	return VM_Class.load(new DataInputStream(is), classloader, classDescriptor).getClassForType();
    } catch (IOException e) {
        throw new ClassFormatError(e.getMessage());
    }
  }
}
