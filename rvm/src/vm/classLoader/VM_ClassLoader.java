/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;

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

  private static ClassLoader appCL;

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
    appCL = null;
  }

  /**
   * Get list of places currently being searched for application 
   * classes and resources.
   * @return names of directories, .zip files, and .jar files
   */ 
  public static String getApplicationRepositories() {
    return applicationRepositories;
  }

  public static ClassLoader getApplicationClassLoader() {
    if (appCL == null) {
      appCL = new ApplicationClassLoader(getApplicationRepositories());
    }
    return appCL;
  }

  /**
   * Load a dynamic library
   * @param libname the name of the library to load.
   */
  public static void load(String libname) {
    currentDynamicLibraryId++;

    if (currentDynamicLibraryId>=(dynamicLibraries.length-1)) {
      dynamicLibraries = 
        growArray(dynamicLibraries, currentDynamicLibraryId << 1); 
    }
    
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
    if (currentDynamicLibraryId>=(dynamicLibraries.length-1)) {
      dynamicLibraries = 
        growArray(dynamicLibraries, currentDynamicLibraryId << 1); 
    }

    if (VM.VerifyAssertions)
      VM._assert(dynamicLibraries[currentDynamicLibraryId] == null);

    String platformLibName = System.mapLibraryName(libname);
    StringTokenizer javaLibDirs =
      new StringTokenizer(javaLibPath, File.pathSeparator, false);

    while (javaLibDirs.hasMoreElements()) {
      String javaLibDir = javaLibDirs.nextToken();
      File javaLib = new File(javaLibDir, platformLibName);
        
      if (javaLib.exists()) {
        dynamicLibraries[currentDynamicLibraryId] = new VM_DynamicLibrary(javaLib.getPath());
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
  public static VM_Atom StandardClassInitializerMethodName;        // "<clinit>"
  public static VM_Atom StandardClassInitializerMethodDescriptor;  // "()V"

  public static VM_Atom StandardObjectInitializerMethodName;       // "<init>"
  public static VM_Atom StandardObjectInitializerMethodDescriptor; // "()V"

  public static VM_Atom StandardObjectInitializerHelperMethodName;       // "this"

  public static VM_Atom StandardObjectFinalizerMethodName;         // "finalize"
  public static VM_Atom StandardObjectFinalizerMethodDescriptor;   // "()V"

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
   * Initialize for boot image.
   */
  public static void init(String vmClassPath) {
    // specify place where vm classes and resources live
    //
    if (vmClassPath != null)
      setVmRepositories(vmClassPath);
    applicationRepositories = null;
    VM_SystemClassLoader.boot();

    // create special method- and attribute- names
    //
    StandardClassInitializerMethodName        = VM_Atom.findOrCreateAsciiAtom("<clinit>");
    StandardClassInitializerMethodDescriptor  = VM_Atom.findOrCreateAsciiAtom("()V");

    StandardObjectInitializerMethodName       = VM_Atom.findOrCreateAsciiAtom("<init>");
    StandardObjectInitializerMethodDescriptor = VM_Atom.findOrCreateAsciiAtom("()V");

    StandardObjectInitializerHelperMethodName = VM_Atom.findOrCreateAsciiAtom("this");

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
    arrayNullCheckAttributeName         = VM_Atom.findOrCreateAsciiAtom("ArrayNullCheckAttribute");

    dynamicLibraries = new VM_DynamicLibrary[0];

    VM_Type.init();
  }

  private static String javaLibPath;

  private static void setJavaLibPath() {
    javaLibPath = VM_CommandLineArgs.getEnvironmentArg("java.library.path");
    if (javaLibPath == null) javaLibPath="";
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
   * files.  This may contain several names separated with colons (':'), just
   * as a classpath may.   (null -> use values specified by
   * setVmRepositories() when boot image was created)
   * @return nothing
   */
  public static void boot(String vmClasses) {      
    if (vmClasses != null)
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
    VM_DynamicLibrary[] newarray = MM_Interface.newContiguousDynamicLibraryArray(newLength);
    for (int i = 0, n = array.length; i < n; ++i) {
      newarray[i] = array[i];
    }

    VM_Magic.sync();
    return newarray;
  }

  public static final VM_Type defineClassInternal(String className, 
                                                  byte[] classRep, 
                                                  int offset, 
                                                  int length, 
                                                  ClassLoader classloader) throws ClassFormatError {
    return defineClassInternal(className, new ByteArrayInputStream(classRep, offset, length), classloader);
  }

  public static final VM_Type defineClassInternal(String className, 
                                                  InputStream is, 
                                                  ClassLoader classloader) throws ClassFormatError {
    VM_TypeReference tRef;
    if (className == null) {
      // NUTS: Our caller hasn't bothered to tell us what this class is supposed
      //       to be called, so we must read the input stream and discover it overselves
      //       before we actually can create the VM_Class instance.
      try {
        is.mark(is.available());
        tRef = getClassTypeRef(new DataInputStream(is), classloader);
        is.reset();
      } catch (IOException e) {
        ClassFormatError cfe = new ClassFormatError(e.getMessage());
        cfe.initCause(e);
        throw cfe;
      }
    } else {
      VM_Atom classDescriptor = VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();
      tRef = VM_TypeReference.findOrCreate(classloader, classDescriptor);
    }

    try {
      if (VM.VerifyAssertions) VM._assert(tRef.isClassType());
      if (VM.TraceClassLoading  && VM.runningVM)
        VM.sysWriteln("loading \"" + tRef.getName() + "\" with " + classloader);
      VM_Class ans = new VM_Class(tRef, new DataInputStream(is));
      tRef.setResolvedType(ans);
      return ans;
    } catch (IOException e) {
      ClassFormatError cfe = new ClassFormatError(e.getMessage());
      cfe.initCause(e);
      throw cfe;
    }
  }

  // Shamelessly cloned & owned from VM_Class constructor....
  private static VM_TypeReference getClassTypeRef(DataInputStream input, ClassLoader cl) throws IOException, ClassFormatError {
    int magic = input.readInt();
    if (magic != 0xCAFEBABE) {
      throw new ClassFormatError("bad magic number " + Integer.toHexString(magic));
    }

    // Drop class file version number on floor. VM_Class constructor will do the check later.
    int minor = input.readUnsignedShort();
    int major = input.readUnsignedShort();
    
    //
    // pass 1: read constant pool
    //
    int[] constantPool = new int[input.readUnsignedShort()];
    byte tmpTags[] = new byte[constantPool.length];

    // note: slot 0 is unused
    for (int i = 1; i <constantPool.length; i++) {
      tmpTags[i] = input.readByte();
      switch (tmpTags[i]) {
      case TAG_UTF:  {
        byte utf[] = new byte[input.readUnsignedShort()];
        input.readFully(utf);
        constantPool[i] = VM_Atom.findOrCreateUtf8Atom(utf).getId();
        break;  
      }

      case TAG_UNUSED: break;
      
      case TAG_INT: case TAG_FLOAT:
      case TAG_FIELDREF:
      case TAG_METHODREF:
      case TAG_INTERFACE_METHODREF: 
      case TAG_MEMBERNAME_AND_DESCRIPTOR: 
        input.readInt(); // drop on floor
        break;

      case TAG_LONG: case TAG_DOUBLE:
        i++; input.readLong(); // drop on floor
        break;

      case TAG_TYPEREF:
        constantPool[i] = input.readUnsignedShort();
        break;

      case TAG_STRING:
        input.readUnsignedShort(); // drop on floor
        break;

      default:
        throw new ClassFormatError("bad constant pool entry: " + tmpTags[i]);
      }
    }
    
    //
    // pass 2: post-process type constant pool entries 
    // (we must do this in a second pass because of forward references)
    //
    for (int i = 1; i<constantPool.length; i++) {
      switch (tmpTags[i]) {
      case TAG_LONG:
      case TAG_DOUBLE: 
        ++i;
        break; 

      case TAG_TYPEREF: { // in: utf index
        VM_Atom typeName = VM_Atom.getAtom(constantPool[constantPool[i]]);
        constantPool[i] = VM_TypeReference.findOrCreate(cl, typeName.descriptorFromClassName()).getId();
        break; 
      } // out: type reference id
      }
    }

    // drop modifiers on floor.
    input.readUnsignedShort();

    int myTypeIndex = input.readUnsignedShort();
    return VM_TypeReference.getTypeRef(constantPool[myTypeIndex]);
  }    
}
