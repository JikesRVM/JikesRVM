/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.StringTokenizer;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Hashtable;

/**
 * Manufacture type descriptions as needed by the running virtual machine. <p>
 * 
 * TODO: Access to many of the VM_ClassLoader data structures currently must
 *       be serialized (access is controlled by VM_ClassLoader.lock).
 *       This is perhaps the biggest scalability problem in the Jikes RVM.
 *       We need to rewrite the data structures and move to a looser
 *       model that allows multiple concurrent readers and only serializes
 *       writers (possibly using multiple write locks, one for each data
 *       structure arranged in a lock hierarchy to prevent deadlock). <p>
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_ClassLoader
  implements VM_Constants, VM_ClassLoaderConstants {

  /** 
   * Serialization lock for thread-safe access to VM_ClassLoader
   * services and data structures.
   * A few of the simpler data structures can be read with out acquiring 
   * the lock; writing any of the data structures requires acquiring 
   * the lock. 
   */
  public static Object lock;

  
  // Set list of places to be searched for vm classes and resources.
  // Taken:    path specification in standard "classpath" format
  // Returned: nothing
  //
  public static void setVmRepositories(String classPath) {
    VM_StringVector vec = new VM_StringVector();
    for (StringTokenizer st = new StringTokenizer(classPath, System.getProperty("path.separator"), false); st.hasMoreTokens(); )
      vec.addElement(st.nextToken());
    vmRepositories = vec.finish();
  }

  // Get list of places currently being searched for vm classes and resources.
  // Taken:  nothing
  // Return: names of directories, .zip files, and .jar files
  //
  public static String[] getVmRepositories() {
    return vmRepositories;
  }

  // Set list of places to be searched for application classes and resources.
  // Taken:    path specification in standard "classpath" format
  // Returned: nothing
  //
  public static void setApplicationRepositories(String classPath) {
    VM_StringVector vec = new VM_StringVector();
    for (StringTokenizer st = new StringTokenizer(classPath, System.getProperty("path.separator"), false); st.hasMoreTokens(); )
      vec.addElement(st.nextToken());
    applicationRepositories = vec.finish();
  }

  // Get list of places currently being searched for application classes and resources.
  // Taken:  nothing
  // Return: names of directories, .zip files, and .jar files
  //
  public static String[] getApplicationRepositories() {
    return applicationRepositories;
  }

  // Find a type description, or create one if this is a type we haven't seen before.
  // Taken:    descriptor for desired type - something like "Ljava/lang/String;" or "[I" or "I"
  // Returned: type description
  //
  public static VM_Type findOrCreateType(VM_Atom descriptor) {
    return VM_TypeDictionary.getValue(findOrCreateTypeId(descriptor));
  }

  // As above, but return type dictionary id.
  //
  static int findOrCreateTypeId(VM_Atom descriptor) {
    int     typeId = VM_TypeDictionary.findOrCreateId(descriptor, null);
    VM_Type type   = VM_TypeDictionary.getValue(typeId);

    if (type != null)
      return typeId;

    if (descriptor.isArrayDescriptor()) { // new array type
      VM_Array ary = new VM_Array(descriptor, typeId);
      VM_TypeDictionary.setValue(typeId, ary);
      return typeId;
    } else { 
      // new class type
      VM_Class cls = new VM_Class(descriptor, typeId);
      VM_TypeDictionary.setValue(typeId, cls);
      return typeId;
    }
  }

  // Find a primitive type description, or create one if this is a type we haven't seen before.
  // Taken:    name for desired type       - something like "void"
  //           descriptor for desired type - something like "V"
  // Returned: type description
  //
  static VM_Type findOrCreatePrimitiveType(VM_Atom name, VM_Atom descriptor) {
    int     typeId = VM_TypeDictionary.findOrCreateId(descriptor, null);
    VM_Type type   = VM_TypeDictionary.getValue(typeId);
    if (type == null)
      VM_TypeDictionary.setValue(typeId, type = new VM_Primitive(name, descriptor, typeId));
    return type;
  }

  // Find a field description, or create one if this is a field we haven't seen before.
  // Taken:    class descriptor - something like "Ljava/lang/String;"
  //           field name       - something like "value"
  //           field descriptor - something like "[I"
  // Returned: field description
  //
  static VM_Field findOrCreateField(VM_Atom classDescriptor, 
				    VM_Atom fieldName, 
				    VM_Atom fieldDescriptor) {
    return VM_FieldDictionary.getValue(findOrCreateFieldId(classDescriptor, 
							   fieldName, 
							   fieldDescriptor));
  }

  // As above, but return field dictionary id.
  //
  static int findOrCreateFieldId(VM_Atom classDescriptor, 
				 VM_Atom fieldName, 
				 VM_Atom fieldDescriptor) {
    VM_Triplet fieldKey = new VM_Triplet(classDescriptor, fieldName, fieldDescriptor);
    int        fieldId  = VM_FieldDictionary.findOrCreateId(fieldKey, null);

    if (VM_FieldDictionary.getValue(fieldId) == null) {
      VM_Class cls = VM_ClassLoader.findOrCreateType(classDescriptor).asClass();
      VM_FieldDictionary.setValue(fieldId, new VM_Field(cls, fieldName, fieldDescriptor, fieldId));
    }

    // keep size of co-indexed array in pace with dictionary
    //
    if (fieldId >= fieldOffsets.length)
      fieldOffsets = growArray(fieldOffsets, fieldId << 1); // grow array by 2x in anticipation of more entries being added

    return fieldId;
  }

  // Find an interface signature id, or create one if this is an interface signature we haven't seen before.
  // Taken:    interface method name - something like "getNext"
  //           interface method descriptor - something like "(I)I"
  // Returned: interface signature id
  //
  static int findOrCreateInterfaceMethodSignatureId(VM_Atom interfaceMethodName, 
						    VM_Atom interfaceMethodDescriptor) {
    VM_InterfaceMethodSignature key = new VM_InterfaceMethodSignature(interfaceMethodName, interfaceMethodDescriptor);
    int id = VM_InterfaceMethodSignatureDictionary.findOrCreateId(key, UNRESOLVED_INTERFACE_METHOD_OFFSET);
    return id;
  }

  // Get offset of field within jtoc or instance object, 
  // for use by table-driven dynamic linking.
  // Taken:    field dictionary id
  // Returned: field offset (a value of NEEDS_DYNAMIC_LINK means field hasn't been resolved yet or class initializer hasn't been run)
  // See also: VM_FieldDictionary, VM_Field.getOffset()
  //
  static int getFieldOffset(int fieldId) {
    return fieldOffsets[fieldId];
  }

  // NOTE: This value is used by the table-based dynamic linking.
  // A field's offset should not be set to valid until references to
  // that field no longer require any class loading action to be performed.
  //
  static void setFieldOffset(VM_Field field, int offset) {
    if (VM.VerifyAssertions) VM.assert(offset != NEEDS_DYNAMIC_LINK);
    fieldOffsets[field.getDictionaryId()] = offset;
  }

  // Find a method description, or create one if this is a method we haven't seen before.
  // Taken:    class descriptor  - something like "Ljava/lang/String;"
  //           method name       - something like "charAt"
  //           method descriptor - something like "(I)C"
  // Returned: method description
  //
  static VM_Method findOrCreateMethod(VM_Atom classDescriptor, 
				      VM_Atom methodName, 
				      VM_Atom methodDescriptor) {
    return VM_MethodDictionary.getValue(findOrCreateMethodId(classDescriptor, methodName, methodDescriptor));
  }

  // As above, but return method dictionary id.
  //
  static int findOrCreateMethodId(VM_Atom classDescriptor, 
				  VM_Atom methodName, 
				  VM_Atom methodDescriptor) {
    VM_Triplet methodKey = new VM_Triplet(classDescriptor, methodName, methodDescriptor);
    int        methodId  = VM_MethodDictionary.findOrCreateId(methodKey, null);

    if (VM_MethodDictionary.getValue(methodId) == null) {
      VM_Class cls = VM_ClassLoader.findOrCreateType(classDescriptor).asClass();
      VM_MethodDictionary.setValue(methodId, new VM_Method(cls, methodName, methodDescriptor, methodId));
    }

    // keep size of co-indexed array in pace with dictionary
    //
    if (methodId >= methodOffsets.length)
      methodOffsets = growArray(methodOffsets, methodId << 1); // grow array by 2x in anticipation of more entries being added

    return methodId;
  }

  // Get offset of method within jtoc or tib, for use by opt compiler's dynamic linker.
  // Taken:    method dictionary id
  // Returned: method offset (a value of NEEDS_DYNAMIC_LINK means method hasn't been resolved yet or class initializer hasn't been run)
  // See also: VM_MethodDictionary, VM_Method.getOffset()
  //
  static int getMethodOffset(int methodId) {
    return methodOffsets[methodId];
  }

  // NOTE: This value is used by the table-driven dynamic linker.
  // A method's offset should not be set to valid until we are positive that
  // a compiled method will be available at that offset before the offset
  // could be read/used by any thread.
  //
  static void setMethodOffset(VM_Method method, int offset) {
    if (VM.VerifyAssertions) VM.assert(offset != NEEDS_DYNAMIC_LINK);
    methodOffsets[method.getDictionaryId()] = offset;
  }

  // Create an id that will uniquely identify a version of machine code generated for some method.
  // Taken:    nothing
  // Returned: id
  // See also: setCompiledMethod(), getCompiledMethod()
  //
  static int createCompiledMethodId() {
    return ++currentCompiledMethodId;
  }

  // Install a newly compiled method.
  //
  static void setCompiledMethod(int compiledMethodId, 
				VM_CompiledMethod compiledMethod) {
    if (compiledMethodId >= compiledMethods.length)
      compiledMethods = growArray(compiledMethods, compiledMethodId << 1); // grow array by 2x in anticipation of more entries being added
    if (VM.VerifyAssertions) VM.assert(compiledMethods[compiledMethodId] == null); // !!TODO: someday recycle slots (see VM_Method.replaceCompiledMethod). for now they are never reused.
    compiledMethods[compiledMethodId] = compiledMethod;
    VM_Magic.sync();  // make sure the update is visible on other procs
  }

  // Fetch a previously compiled method.
  //
  static VM_CompiledMethod getCompiledMethod(int compiledMethodId) {
    VM_Magic.isync();  // see potential update from other procs

    if (VM.VerifyAssertions) {
      VM.assert(0 <= compiledMethodId);
      VM.assert(compiledMethodId <= currentCompiledMethodId);
      VM.assert(compiledMethods[compiledMethodId] != null);
    }

    return compiledMethods[compiledMethodId];
  }

  // Get number of methods compiled so far.
  //
  static int numCompiledMethods() {
    return currentCompiledMethodId + 1;
  }

  // Getter method for the debugger, interpreter.
  //
  static VM_CompiledMethod[] getCompiledMethods() {
    return compiledMethods;
  }

  // Getter method for the debugger, interpreter.
  //
  static int numCompiledMethodsLess1() {
    return currentCompiledMethodId;
  }

  //
  public static void loadLibrary(String libname) {
    currentDynamicLibraryId++;
    if (currentDynamicLibraryId>=(dynamicLibraries.length-1))
      dynamicLibraries = growArray(dynamicLibraries, currentDynamicLibraryId << 1); // grow array by 2x
    if (VM.VerifyAssertions) VM.assert(dynamicLibraries[currentDynamicLibraryId] == null);

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
    if (VM.VerifyAssertions) VM.assert(dynamicLibraries[currentDynamicLibraryId] == null);

    dynamicLibraries[currentDynamicLibraryId] = new VM_DynamicLibrary(libname);
  }


  static VM_DynamicLibrary[] getDynamicLibraries() {
    return dynamicLibraries;
  }

   // Find method whose machine code contains specified instruction.
   // Taken:      instruction address
   // Returned:   method (null --> not found)
   // Assumption: caller has disabled gc (otherwise collector could move
   //             objects without fixing up raw "ip" pointer)
   //
   // Usage note: "ip" must point to the instruction *following* the actual instruction
   // whose method is sought. This allows us to properly handle the case where
   // the only address we have to work with is a return address (ie. from a stackframe)
   // or an exception address (ie. from a null pointer dereference, array bounds check,
   // or divide by zero) on a machine architecture with variable length instructions.
   // In such situations we'd have no idea how far to back up the instruction pointer
   // to point to the "call site" or "exception site".
   //
   // Note: this method is highly inefficient. Normally you should use the following instead:
   //   VM_ClassLoader.getCompiledMethod(VM_Magic.getCompiledMethodID(fp))
   //
  static VM_CompiledMethod findMethodForInstruction(int ip) {
    for (int i = 0, n = numCompiledMethods(); i < n; ++i) {
      VM_CompiledMethod compiledMethod = compiledMethods[i];
      if (compiledMethod == null)
	continue; // empty slot

      INSTRUCTION[] instructions = compiledMethod.getInstructions();
      int           beg          = VM_Magic.objectAsAddress(instructions);
      int           end          = beg + (instructions.length << VM.LG_INSTRUCTION_WIDTH);

      // note that "ip" points to a return site (not a call site)
      // so the range check here must be "ip <= beg || ip >  end"
      // and not                         "ip <  beg || ip >= end"
      //
      if (ip <= beg || ip > end)
	continue;

      return compiledMethod;
    }

    return null;
  }

  //----------------//
  // implementation //
  //----------------//

   // Places from which to load .class files.
   //
  private static String[] vmRepositories;
  private static String[] applicationRepositories;

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

  // Offsets of fields and methods of java classes that have been encountered so far.
  // Entries in these arrays are co-indexed with corresponding entries in
  // VM_FieldDictionary and VM_MethodDictionary. Offsets are with respect to
  // jtoc, object instance, or tib, as appropriate. A value of NEEDS_DYNAMIC_LINK
  // means the field or method hasn't been resolved yet or class initializer hasn't been run.
  // This information is maintained for use by table-based dynamic linker.
  //
  private static int[] fieldOffsets;
  private static int[] methodOffsets;

   // Java methods that have been compiled into machine code.
   // Note that there may be more than one compiled versions of the same method
   // (ie. at different levels of optimization).
   //
  private static VM_CompiledMethod[] compiledMethods;

  // Index of most recently allocated slot in compiledMethods[].
  //
  private static int currentCompiledMethodId;

   // Dynamic libraries for native code
   // Note: this is static for now, but it needs to be a list per class loader
  private static VM_DynamicLibrary[] dynamicLibraries;

  // Index of most recently allocated slot in dynamicLibraries.
  //
  private static int currentDynamicLibraryId;

   // Initialize for bootimage.
   //
  static void init(String vmClassPath) {
    // Create classloader serialization lock.
    //
    lock = new Object();
      
    // specify place where vm classes and resources live
    //
    setVmRepositories(vmClassPath);
    setApplicationRepositories("");

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

    VM_TypeDictionary.init();
    VM_FieldDictionary.init();
    VM_MethodDictionary.init();
    if (VM.BuildForIMTInterfaceInvocation)
      VM_InterfaceMethodSignatureDictionary.init();

    fieldOffsets  = new int[0];
    methodOffsets = new int[0];

    compiledMethods = new VM_CompiledMethod[0];
    dynamicLibraries = new VM_DynamicLibrary[0];

    VM_Type.init();

    // Zip file caching
    caches = new ZipFile[10];
    zipCacheCount = 0;

    // Resource caching disabled to avoid writing table to boot image
    resourceCache = null;
  }

  // Initialize for execution.
  // Taken:    name of directory containing vm .class and .zip/.jar files (null -> use values specified by setVmRepositories() when bootimage was created)
  // Returned: nothing
  //
  static void boot(String vmClasses) {
    if (vmClasses != null) {
      vmRepositories = new String[2];
      vmRepositories[0] = vmClasses;
      //-#if RVM_FOR_CYGWIN
      vmRepositories[1] = vmClasses + "\\rvmrt.jar";
      //-#else
      vmRepositories[1] = vmClasses + "/rvmrt.jar";
      //-#endif
    }

    if (VM.TraceRepositoryReading) {
      VM.sysWrite("VM_ClassLoader.boot:\n");
      for (int i = 0, n = vmRepositories.length; i < n; ++i)
	VM.sysWrite("   " + vmRepositories[i] + "\n");
    }

    // Zip file caching
    caches = new ZipFile[10];
    zipCacheCount = 0;

    // Resource caching
    resourceCache = new Hashtable();
    resourceNullKey = new VM_BinaryData( (byte[])null );
  }

  private static Hashtable resourceCache;

  private static VM_BinaryData resourceNullKey;

  public static VM_BinaryData  getClassOrResourceData(String fileName)
    throws FileNotFoundException, IOException {
    VM_BinaryData data;

    if (VM.runningVM) {
      data = (VM_BinaryData) resourceCache.get( fileName );
      if (data == resourceNullKey)
	throw new FileNotFoundException(fileName);
      else if (data == null) {
	data = getClassOrResourceDataInternal( fileName );
	if (data != null)
	  resourceCache.put( fileName, data );
	else
	  resourceCache.put( fileName, resourceNullKey );
      }
    } else {
      data = getClassOrResourceDataInternal( fileName );
    }
	       
    if (data != null)
      return data;
    else
      throw new FileNotFoundException(fileName);
  }

  // Fetch class or resource data from a repository.
  // Taken:    filename - something like "java/lang/String.class" or "sun/tools/javac/resources/javac.properties"
  // Returned: contents of data file
  //
  private static VM_BinaryData getClassOrResourceDataInternal(String fileName)
    throws FileNotFoundException, IOException {
    VM_BinaryData data;

    data = searchRepositories(fileName, vmRepositories);
    if (data != null)
      return data;

    data = searchRepositories(fileName, applicationRepositories);

    return data;
  }

  private static ZipFile caches[];
  private static int zipCacheCount = 0;

  private static ZipFile getCachedZip(String zipFileName) {
    int i;
    for (i = 0; i < zipCacheCount; i++)
      if (caches[i].getName().equals( zipFileName ))
	return caches[i];

    if (i >= caches.length) {
      ZipFile[] newCaches = new ZipFile[ caches.length * 2 ];
      for(int j = 0; j < caches.length; j++) newCaches[j] = caches[j];
      caches = newCaches;
    }

    try {
      ZipFile zip = new ZipFile( zipFileName );
      caches[ zipCacheCount++ ] = zip;
      return zip;
    } catch (java.io.IOException e) {
      return null;
    }
  }
          
  private static VM_BinaryData searchRepositories(String fileName, 
						  String[] repositories) throws IOException {
    for (int i = 0, n = repositories.length; i < n; ++i)      {
      String repositoryName = repositories[i];

      if (VM.TraceRepositoryReading) VM.sysWrite("VM_ClassLoader: trying to read " + fileName + " from " + repositoryName + "\n");
      // Note: the "canRead()" tests in the following code are not required
      // for correctness, but rather to improve performance by avoiding the use of
      // exceptions for "file not found" detection.
      //
      try {
	if (repositoryName.endsWith(".zip") || repositoryName.endsWith(".jar")) {
	  if (new File(repositoryName).canRead()) {
	    ZipFile  archive = getCachedZip(repositoryName);
	    ZipEntry entry   = archive.getEntry(fileName);
	    if (entry != null) {
	      if (VM.TraceRepositoryReading) VM.sysWrite("VM_ClassLoader: (begin) reading " + fileName + " from " + repositoryName + "\n");
	      VM_BinaryData binaryData = new VM_BinaryData(archive.getInputStream(entry), (int)entry.getSize());
	      if (VM.TraceRepositoryReading) VM.sysWrite("VM_ClassLoader: (end)   reading " + fileName + " from " + repositoryName + "\n");
	      if (VM.verboseClassLoading) VM.sysWrite("[Loaded "+fileName+" from "+repositoryName+"]\n");
	      return binaryData;
	    }
	  }
	} else { // filesystem directory
	  //-#if RVM_FOR_CYGWIN
	  String fullName;
	  if (repositoryName.equals(".")) {
	    fullName = fileName;
	  } else {
	    fullName = repositoryName + "\\" + fileName;
	  }
	  //-#else
	  String fullName = repositoryName + "/" + fileName;
	  //-#endif
	  File file = new File(fullName);
	  if (file.canRead()) {
	    if (VM.TraceRepositoryReading) VM.sysWrite("VM_ClassLoader: (begin) reading " + fileName + " from " + repositoryName + "\n");
	    VM_BinaryData binaryData = new VM_BinaryData(fullName);
	    if (VM.TraceRepositoryReading) VM.sysWrite("VM_ClassLoader: (end)   reading " + fileName + " from " + repositoryName + "\n");
	    if (VM.verboseClassLoading) VM.sysWrite("[Loaded "+fileName+" from "+repositoryName+"]\n");
	    return binaryData;
	  }
	}
      }	catch (IOException x) { // repository corrupted - try next one
      }
    }

    // not found
    return null;
  }

  // Expand an array.
  //
  private static int[] growArray(int[] array, int newLength) {
    if (VM.VerifyAssertions) VM.assert(NEEDS_DYNAMIC_LINK == 0); // assertion: no special array initialization needed (default 0 is ok)
    int[] newarray = new int[newLength];
    for (int i = 0, n = array.length; i < n; ++i)
      newarray[i] = array[i];

    VM_Magic.sync();
    return newarray;
  }

  // Expand an array.
  //
  private static VM_CompiledMethod[] growArray(VM_CompiledMethod[] array, 
					       int newLength) {
    VM_CompiledMethod[] newarray = new VM_CompiledMethod[newLength];
    for (int i = 0, n = array.length; i < n; ++i)
      newarray[i] = array[i];

    VM_Magic.sync();
    return newarray;
  }

  // Create id for use by C signal handler as placeholder to mark stackframe
  // introduced when a hardware trap is encountered. This method is completely
  // artifical: it has no code, class description, etc. Its only purpose is to mark the place
  // on the stack where a trap was encountered, for identification when walking the stack
  // during gc.
  //
  static int createHardwareTrapCompiledMethodId() {
    VM_Method method = VM_ClassLoader.findOrCreateMethod(VM_Atom.findOrCreateAsciiAtom("L<hardware>;"),
							 VM_Atom.findOrCreateAsciiAtom("<trap>"),
							 VM_Atom.findOrCreateAsciiAtom("()V"));
    INSTRUCTION[]     instructions     = new INSTRUCTION[0];
    VM_CompilerInfo   compilerInfo     = new VM_HardwareTrapCompilerInfo();
    int               compiledMethodId = VM_ClassLoader.createCompiledMethodId();
    VM_CompiledMethod compiledMethod   = new VM_CompiledMethod(compiledMethodId, method, instructions, compilerInfo);
    VM_ClassLoader.setCompiledMethod(compiledMethod.getId(), compiledMethod);
    return compiledMethodId;
  }


  // Expand an array.
  //
  private static VM_DynamicLibrary[] growArray(VM_DynamicLibrary[] array, 
					       int newLength) {
    VM_DynamicLibrary[] newarray = new VM_DynamicLibrary[newLength];
    for (int i = 0, n = array.length; i < n; ++i)
      newarray[i] = array[i];

    VM_Magic.sync();
    return newarray;
  }



  public static Class findClassInternal (String className) throws ClassNotFoundException {

    // original OTI code just throws an exception, expecting subclasses to override.
    // as specified in java 1.2 spec....but Websphere 3.02 class loaders don't do this,
    // so temporarily provide a default implementation which will find and load a class
    //          throw new ClassNotFoundException();

    VM_Atom classDescriptor = VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();
    VM_Class cls = VM_ClassLoader.findOrCreateType(classDescriptor).asClass();
    try {
      cls.load();
    } catch (VM_ResolutionException e) {
      throw new ClassNotFoundException(className);
    }

    return cls.getClassForType();
  }


  public static Class loadClassInternal(String className, boolean resolveClass)
    throws ClassNotFoundException {
    Class loadedClass = null;

    // Ask the VM to look in its cache.
    loadedClass = VM_SystemClassLoader.getVMClassLoader().findLoadedClassInternal(className);

    /* parent classloaders not yet in RVM
     *  // search in parent if not found
     *  if (loadedClass == null) {
     *    try {
     *      if (parent != null)
     *        loadedClass = parent.loadClass(className, resolveClass);
     *    } catch (ClassNotFoundException e) {
     *        // don't do anything.  Catching this exception is the normal protocol for
     *        // parent classloaders telling use they couldn't find a class.
     *    }
     *  }
     */

    if (loadedClass == null) loadedClass = findClassInternal(className);

    // resolve if required
    if (resolveClass) resolveClassInternal(loadedClass);
    return loadedClass;
  }


  public static final void resolveClassInternal(Class clazz) {
    VM_Class cls = clazz.getVMType().asClass();
    try {
      cls.resolve();
      cls.instantiate();
      cls.initialize();
    } catch (VM_ResolutionException e) { }
  }

  public static final Class defineClassInternal (String className, byte[] classRep, int offset, int length)
    throws ClassFormatError {
    // original code...
    // Class answer = defineClassImpl(className, classRep, offset, length);
    // answer.setPDImpl(getDefaultProtectionDomain());
    // return answer;

    VM_BinaryData classData;

    if (className == null) {
      VM.sysFail("ClassLoader.defineClass class name == null not implemented"); //!!TODO
      return null;
    }

    VM_Atom classDescriptor = VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();
    VM_Class cls = VM_ClassLoader.findOrCreateType(classDescriptor).asClass();

    if (offset > 0) {
      // we have never seen an offset other than zero!
      byte[] bytes = new byte[length];
      VM_Array.arraycopy(classRep,offset,bytes,0,length);
      classData = new VM_BinaryData( bytes );
    }
    else classData = new VM_BinaryData( classRep );

    cls.load( classData );

    return cls.getClassForType();
  }

  // In some bizarre circumstances a key can be created for a member that
  // does not exist.
  //
  // e.g. |               |                      |                    |
  //      |imports p;     |package p;            |package p;          |
  //      |class C {      |class A {             |class B extends A { |
  //      |               |                      |                    |
  //      | void bar () { | public void foo () { |                    |
  //      |   ... B.foo() |   ...                |                    |
  //      | }             | }                    |                    |
  //      |               |                      |                    |
  //      |}              |}                     |}                   |
  //
  // Here, a key to the method dictionary is created for the triple
  // <p.B, "foo", "()V">, even though no such method exists.
  //
  // This honors package protection levels.  There was a bug in the
  // older versions of both javac and jikes that resulted in complete
  // resolution of method calls and field references.  We can no longer
  // rely on this bug.
  //
  // In such cases, an empty VM_Member object gets created.  This object
  // has bogus information stored in it, and will have the loaded state
  // inconsistend with the loaded state of its declaring class.  When such
  // an object is detected, the following methods repair the appropriate
  // dictionaries and return the correct member.
  //
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
    VM.sysWrite("VM_ClassLoader: repairMethod: method " + m + " not found");
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
    return null;
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
    VM.sysWrite("VM_ClassLoader: repairField: field " + f + " not found");
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
    return null;
  }

  // like repairMethod, except a) we crawl the interface hierarchy
  // and b) some of the VM_Classes we look at may not be loaded yet,
  // so we have to know whether or not we are allowed to perform classloading
  // to resolve the ghost reference. 
  // we'll return null if it might be a ghost reference and we couldn't resolve it.
  static VM_Method repairInterfaceMethod(VM_Method m, boolean canLoad) throws VM_ResolutionException {
    VM_Method newm = VM_MethodDictionary.getValue(m.getDictionaryId());
    if (newm != m) return newm;  // already done!
    newm = repairInterfaceMethodHelper(m, canLoad, m.getDeclaringClass(), m.getName(), m.getDescriptor());
    if (canLoad && newm == null) {
      throw new VM_ResolutionException(m.getDeclaringClass().getDescriptor(), new IncompatibleClassChangeError());
    }
    return newm;
  }

  private static VM_Method repairInterfaceMethodHelper(VM_Method m, boolean canLoad,
						       VM_Class I, VM_Atom name, VM_Atom desc) throws VM_ResolutionException {
    if (!I.isLoaded()) {
      if (canLoad) {
	synchronized (VM_ClassLoader.lock) {
	  I.load();
	}
	if (!I.isInterface()) throw new VM_ResolutionException(I.getDescriptor(), new IncompatibleClassChangeError());
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
      VM_Method n = repairInterfaceMethodHelper(m, canLoad, superInterface, name, desc);
      if (n != null) return n;
    }

    return null;
  }

}
