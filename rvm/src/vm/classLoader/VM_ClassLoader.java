/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

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
 * @author Derek Lieber
 */
public class VM_ClassLoader
implements VM_Constants, VM_ClassLoaderConstants {

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
    switch ( descriptor.parseForTypeCode() ) {
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
    int     typeId = VM_TypeDictionary.findOrCreateId(descriptor, null);
    VM_Type type   = VM_TypeDictionary.getValue(typeId);
    if (type == null)
      VM_TypeDictionary.setValue(typeId, type = new VM_Primitive(name, descriptor, typeId));
    return type;
  }

  /**
   * Find a field description, or create one if this is a field we 
   * haven't seen before.
   * @param classDescriptor class descriptor - 
   * something like "Ljava/lang/String;"
   * @param fieldName field name - something like "value"
   * @param fieldDescriptor field descriptor - something like "[I"
   * @return field description
   */ 
  static VM_Field findOrCreateField(VM_Atom classDescriptor, 
                                    VM_Atom fieldName, 
                                    VM_Atom fieldDescriptor,
                                    ClassLoader classloader) {
    return VM_FieldDictionary.getValue(findOrCreateFieldId(classDescriptor, 
                                                           fieldName, 
                                                           fieldDescriptor,
                                                           classloader));
  }

  /**
   * Find a field dictionary id, or create one if this is a field we 
   * haven't seen before.
   * @param classDescriptor class descriptor - 
   * something like "Ljava/lang/String;"
   * @param fieldName field name - something like "value"
   * @param fieldDescriptor field descriptor - something like "[I"
   * @return field dictionary id
   */ 
  static int findOrCreateFieldId(VM_Atom classDescriptor, 
                                 VM_Atom fieldName, 
                                 VM_Atom fieldDescriptor,
                                 ClassLoader classloader) {
    VM_Triplet fieldKey = new VM_Triplet(classDescriptor, fieldName, 
                                         fieldDescriptor);
    int        fieldId  = VM_FieldDictionary.findOrCreateId(fieldKey, null);

    if (VM_FieldDictionary.getValue(fieldId) == null) {
      VM_Class cls = VM_ClassLoader.findOrCreateType(classDescriptor, classloader).asClass();
      VM_FieldDictionary.setValue(fieldId, new VM_Field(cls, 
                                                        fieldName, 
                                                        fieldDescriptor, 
                                                        fieldId));
    }

    // keep size of co-indexed array in pace with dictionary
    //
    VM_TableBasedDynamicLinker.ensureFieldCapacity(fieldId);

    return fieldId;
  }

  /**
   * Find an interface signature id, or create one if this is an 
   * interface signature we haven't seen before.
   * @param interfaceMethodName interface method name - something like "getNext"
   * @param interfaceMethodDescriptor interface method descriptor - 
   * something like "(I)I"
   * @return interface signature id
   */ 
  public static int findOrCreateInterfaceMethodSignatureId(VM_Atom interfaceMethodName, 
                                                    VM_Atom interfaceMethodDescriptor) {
    VM_InterfaceMethodSignature key = new VM_InterfaceMethodSignature(interfaceMethodName, interfaceMethodDescriptor);
    int id = VM_InterfaceMethodSignatureDictionary.findOrCreateId(key, UNRESOLVED_INTERFACE_METHOD_OFFSET);
    return id;
  }

  /**
   * Find a method description, or create one if this is a method we 
   * haven't seen before.
   * @param classDescriptor class descriptor - something like 
   * "Ljava/lang/String;"
   * @param methodName method name - something like "charAt"
   * @param methodDescriptor  method descriptor - something like "(I)C"
   * @return method description
   */
  public static VM_Method findOrCreateMethod(VM_Atom classDescriptor, 
                                      VM_Atom methodName, 
                                      VM_Atom methodDescriptor,
                                      ClassLoader classloader) {
    return VM_MethodDictionary.getValue(findOrCreateMethodId(classDescriptor, methodName, methodDescriptor, classloader));
  }

  /**
   * Find a method dictionary id, or create one if this is a method we 
   * haven't seen before.
   * @param classDescriptor class descriptor - something like 
   * "Ljava/lang/String;"
   * @param methodName method name - something like "charAt"
   * @param methodDescriptor  method descriptor - something like "(I)C"
   * @return method dictionary id
   */
  static int findOrCreateMethodId(VM_Atom classDescriptor, 
                                  VM_Atom methodName, 
                                  VM_Atom methodDescriptor,
                                  ClassLoader classloader) {
    VM_Triplet methodKey = new VM_Triplet(classDescriptor, methodName, 
                                          methodDescriptor);
    int        methodId  = VM_MethodDictionary.findOrCreateId(methodKey, null);
    if (VM_MethodDictionary.getValue(methodId) == null) {
      VM_Class cls = VM_ClassLoader.findOrCreateType(classDescriptor, classloader).asClass();
      VM_MethodDictionary.setValue(methodId, 
                                   new VM_Method(cls, methodName, 
                                                 methodDescriptor,
                                                 methodId,
                                                 classloader));
    }
    // keep size of co-indexed array in pace with dictionary
    //
    VM_TableBasedDynamicLinker.ensureMethodCapacity(methodId);
    return methodId;
  }

  public static void loadLibrary(String libname) {
    currentDynamicLibraryId++;
    if (currentDynamicLibraryId>=(dynamicLibraries.length-1))
      dynamicLibraries = growArray(dynamicLibraries, currentDynamicLibraryId << 1); // grow array by 2x
    if (VM.VerifyAssertions) VM._assert(dynamicLibraries[currentDynamicLibraryId] == null);

    // prepend "lib" if there is no path in the name
    // attach the suffix .a to the library name for AIX, .so for Linux
    //-#if RVM_FOR_LINUX  
    String suf = ".so";
    //-#else
    String suf = ".a";
    //-#endif
    if (libname.indexOf('/')==-1)
      dynamicLibraries[currentDynamicLibraryId] = new VM_DynamicLibrary("lib" + libname + suf);
    else
      dynamicLibraries[currentDynamicLibraryId] = new VM_DynamicLibrary(libname + suf);
  }

  public static void load(String libname) {
    currentDynamicLibraryId++;
    if (currentDynamicLibraryId>=(dynamicLibraries.length-1))
      dynamicLibraries = growArray(dynamicLibraries, currentDynamicLibraryId << 1); // grow array by 2x
    if (VM.VerifyAssertions) VM._assert(dynamicLibraries[currentDynamicLibraryId] == null);

    dynamicLibraries[currentDynamicLibraryId] = new VM_DynamicLibrary(libname);
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

  static VM_Atom StandardObjectInitializerMethodName;       // "<init>"
  static VM_Atom StandardObjectInitializerMethodDescriptor; // "()V"

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
  static void init(String vmClassPath) {
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

    //-#if RVM_WITH_GNU_CLASSPATH
    //-#else
    com.ibm.oti.vm.AbstractClassLoader.setBootstrapClassLoader( VM_SystemClassLoader.getVMClassLoader() );
    //-#endif
  }

  /**
   * Initialize for execution.
   * @param vmClasses name of directory containing vm .class and .zip/.jar 
   * files (null -> use values specified by setVmRepositories() when 
   * bootimage was created)
   * @return nothing
   */
  static void boot(String vmClasses) {
    setVmRepositories( vmClasses );
    currentDynamicLibraryId = 0;
    dynamicLibraries = new VM_DynamicLibrary[0];
  }

  /**
   * Expand an array.
   */ 
  private static int[] growArray(int[] array, int newLength) {
    // assertion: no special array initialization needed (default 0 is ok)
    if (VM.VerifyAssertions) VM._assert(NEEDS_DYNAMIC_LINK == 0); 
    int[] newarray = VM_RuntimeStructures.newContiguousIntArray(newLength);
    for (int i = 0, n = array.length; i < n; ++i)
      newarray[i] = array[i];

    VM_Magic.sync();
    return newarray;
  }

  // Expand an array.
  //

  /**
   * Create id for use by C signal handler as placeholder to mark stackframe
   * introduced when a hardware trap is encountered. This method is completely
   * artifical: it has no code, class description, etc. 
   * Its only purpose is to mark the place
   * on the stack where a trap was encountered, 
   * for identification when walking the stack
   * during gc.
   */ 
  static int createHardwareTrapCompiledMethodId() {
    VM_Method method = VM_ClassLoader.findOrCreateMethod(VM_Atom.findOrCreateAsciiAtom("L<hardware>;"),
                                                         VM_Atom.findOrCreateAsciiAtom("<trap>"),
                                                         VM_Atom.findOrCreateAsciiAtom("()V"),
                                                         VM_SystemClassLoader.getVMClassLoader());
    VM_CompiledMethod compiledMethod   = VM_CompiledMethods.createCompiledMethod(method, VM_CompiledMethod.TRAP);
    INSTRUCTION[]     instructions     = VM_RuntimeStructures.newInstructions(0);
    compiledMethod.compileComplete(instructions);
    return compiledMethod.getId();
  }


  /**
   * Expand an array.
   */ 
  private static VM_DynamicLibrary[] growArray(VM_DynamicLibrary[] array, 
                                               int newLength) {
    VM_DynamicLibrary[] newarray = VM_RuntimeStructures.newContiguousDynamicLibraryArray(newLength);
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

  /**
   * In some bizarre circumstances a key can be created for a member that
   * does not exist.
   *
   * <pre>
   * e.g. |               |                      |                    |
   *      |imports p;     |package p;            |package p;          |
   *      |class C {      |class A {             |class B extends A { |
   *      |               |                      |                    |
   *      | void bar () { | public void foo () { |                    |
   *      |   ... B.foo() |   ...                |                    |
   *      | }             | }                    |                    |
   *      |               |                      |                    |
   *      |}              |}                     |}                   |
   * </pre>
   *
   * <p> Here, a key to the method dictionary is created for the triple
   * <p.B, "foo", "()V">, even though no such method exists.
   *
   * <p> This honors package protection levels.  There was a bug in the
   * older versions of both javac and jikes that resulted in complete
   * resolution of method calls and field references.  We can no longer
   * rely on this bug.
   *
   * <p> In such cases, an empty VM_Member object gets created.  This object
   * has bogus information stored in it, and will have the loaded state
   * inconsistend with the loaded state of its declaring class.  When such
   * an object is detected, the following methods repair the appropriate
   * dictionaries and return the correct member.
   */ 
  static VM_Member repairMember(VM_Member m) {
    if (m instanceof VM_Method) {
      return repairMethod((VM_Method) m);
    } else { // m instanceof VM_Field
      return repairField((VM_Field) m);
    }
  }

  static VM_Method repairMethod(VM_Method m) {
    VM_Method newm = VM_MethodDictionary.getValue(m.getDictionaryId());
    if (newm != m) return newm;  // already done!
    VM_Class c = m.getDeclaringClass();
    VM_Atom name = m.getName();
    VM_Atom desc = m.getDescriptor();
    while ((c = c.getSuperClass()) != null) {
      VM_Method [] dm = c.getDeclaredMethods();
      for (int i=0; i<dm.length; i++ ) {
        VM_Method n = dm[i];
        if (name == n.getName() && desc == n.getDescriptor() && n.isLoaded()) {
          VM_MethodDictionary.setValue(m.getDictionaryId(), n);
          return n;
        }
      }
    }
    VM.sysWrite(m.getDeclaringClass()+": "+name+" " +desc+" no such method found");
    throw new NoSuchMethodError(m.getDeclaringClass()+": "+name+" " +desc+" no such method found");
  }

  static VM_Field repairField(VM_Field f) {
    VM_Field newf = VM_FieldDictionary.getValue(f.getDictionaryId());
    if (newf != f) return newf;  // already done!
    VM_Class c = f.getDeclaringClass();
    VM_Atom name = f.getName();
    VM_Atom desc = f.getDescriptor();
    while ((c = c.getSuperClass()) != null) {
      VM_Field [] df = c.getDeclaredFields();
      for (int i=0; i<df.length; i++ ) {
        VM_Field n = df[i];
        if (name == n.getName() && desc == n.getDescriptor() && n.isLoaded()) {
          VM_FieldDictionary.setValue(f.getDictionaryId(), n);
          return n;
        }
      }
    }
    throw new NoSuchFieldError(f.getDeclaringClass()+": "+name+" "+desc+" no such field found");
  }

  /**
   * like repairMethod, except a) we crawl the interface hierarchy
   * and b) some of the VM_Classes we look at may not be loaded yet,
   * so we have to know whether or not we are allowed to perform classloading
   * to resolve the ghost reference. 
   * we'll return null if it might be a ghost reference and we couldn't 
   * resolve it.
   */
  static VM_Method repairInterfaceMethod(VM_Method m, boolean canLoad) 
    throws VM_ResolutionException {
      VM_Method newm = VM_MethodDictionary.getValue(m.getDictionaryId());
      if (newm != m) return newm;  // already done!
      newm = repairInterfaceMethodHelper(m, canLoad, m.getDeclaringClass(), 
                                         m.getName(), m.getDescriptor());
      if (canLoad && newm == null) {
        throw new VM_ResolutionException(m.getDeclaringClass().getDescriptor(), 
                                         new IncompatibleClassChangeError());
      }
      return newm;
    }

  private static VM_Method repairInterfaceMethodHelper(VM_Method m, 
                                                       boolean canLoad,
                                                       VM_Class I, 
                                                       VM_Atom name, 
                                                       VM_Atom desc) 
    throws VM_ResolutionException {
      if (!I.isLoaded()) {
        if (canLoad) {
          VM_Runtime.initializeClassForDynamicLink(I);
          if (!I.isInterface()) 
            throw new VM_ResolutionException(I.getDescriptor(), 
                                             new IncompatibleClassChangeError());
        } else {
          return null;
        }
      }

      VM_Method [] dm = I.getDeclaredMethods();
      for (int i=0; i<dm.length; i++ ) {
        VM_Method n = dm[i];
        if (name == n.getName() && desc == n.getDescriptor() && n.isLoaded()) {
          VM_MethodDictionary.setValue(m.getDictionaryId(), n);
          return n;
        }
      }

      VM_Class [] superInterfaces = I.getDeclaredInterfaces();
      for (int i=0; i<superInterfaces.length; i++) {
        VM_Class superInterface = superInterfaces[i];
        VM_Method n = repairInterfaceMethodHelper(m, canLoad, 
                                                  superInterface, name, desc);
        if (n != null) return n;
      }

      return null;
    }

  /**
   *  Is dynamic linking code required to access one member when 
   * referenced from another?
   *
   * @param referent the member being referenced
   * @param referrer the declaring class of the method containing the reference
   */
  static public boolean needsDynamicLink(VM_Member referent, VM_Class referrer)
  {
    VM_Class referentClass = referent.getDeclaringClass();

    if (referentClass.isInitialized()) {
      // No dynamic linking code is required to access this field or call this method
      // because its size and offset are known and its class's static initializer
      // has already run, thereby compiling this method or initializing this field.
      //
      return false;
    }

    if (referent instanceof VM_Field && referentClass.isResolved() && 
        referentClass.getClassInitializerMethod() == null) {
      // No dynamic linking code is required to access this field
      // because its size and offset is known and its class has no static
      // initializer, therefore its value need not be specially initialized
      // (its default value of zero or null is sufficient).
      //
      return false;
    }

    if (VM.writingBootImage && referentClass.isInBootImage()) {
      // Loads, stores, and calls within boot image are compiled without dynamic
      // linking code because all boot image classes are explicitly loaded/resolved/compiled
      // and have had their static initializers run by the boot image writer.
      //
      if (!referentClass.isResolved()) VM.sysWrite("unresolved: \"" + referent + "\" referenced from \"" + referrer + "\"\n");
      if (VM.VerifyAssertions) VM._assert(referentClass.isResolved());
      return false;
    }

    if (referentClass == referrer) {
      // Intra-class references don't need to be compiled with dynamic linking
      // because they execute *after* class has been loaded/resolved/compiled.
      //
      return false;
    }

    // This member needs size and offset to be computed, or its class's static
    // initializer needs to be run when the member is first "touched", so
    // dynamic linking code is required to access the member.
    //
    return true;
  }


}
