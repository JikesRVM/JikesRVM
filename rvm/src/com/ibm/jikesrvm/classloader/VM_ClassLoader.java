/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002, 2005
 */
//$Id$
package com.ibm.jikesrvm.classloader;

import com.ibm.jikesrvm.*;
import com.ibm.jikesrvm.VM;
import com.ibm.jikesrvm.VM_Properties;

import java.io.*;

/**
 * Manufacture type descriptions as needed by the running virtual machine. <p>
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public class VM_ClassLoader implements VM_Constants, 
                                       VM_ClassLoaderConstants {

  private static final boolean DBG_APP_CL = false;
  
  private static ClassLoader appCL;

  /**
   * Set list of places to be searched for application classes and resources.
   * Do NOT set the java.class.path property; it is probably too early in
   * the VM's booting cycle to set properties.
   *
   * @param classpath path specification in standard "classpath" format
   */
  public static void stashApplicationRepositories(String classpath) {
    if (DBG_APP_CL) {
      VM.sysWriteln("VM_ClassLoader.stashApplicationRepositories: "
                    + "applicationRepositories = ", classpath);
    }
    /* If mis-initialized, trash it. */
    if (appCL != null && !classpath.equals(applicationRepositories)) {
      appCL = null;
      if (DBG_APP_CL)
        VM.sysWriteln("VM_ClassLoader.stashApplicationRepositories: Wiping out my remembered appCL.");
    }
    applicationRepositories = classpath;
  }

  
  /**
   * Set list of places to be searched for application classes and resources.
   * @param classpath path specification in standard "classpath" format
   *
   * Our Jikes RVM classloader can not handle having the class path reset
   * after it's been set up.   Unfortunately, it is stashed by classes in
   * GNU Classpath.
   */
  public static void setApplicationRepositories(String classpath) {
    System.setProperty("java.class.path", classpath);
    stashApplicationRepositories(classpath);
    if (DBG_APP_CL) {
      VM.sysWriteln("VM_ClassLoader.setApplicationRepositories: applicationRepositories = ", applicationRepositories);
    }
  }

  /**
   * Get list of places currently being searched for application 
   * classes and resources.
   * @return names of directories, .zip files, and .jar files
   */ 
  public static String getApplicationRepositories() {
    return applicationRepositories;
  }

  /** Are we getting the application CL?  Access is synchronized via the
   *  Class object.  Probably not necessary, but doesn't hurt, or shouldn't.
   *  Used for sanity checks. */
      
  private static int gettingAppCL = 0;
  
  /** Is the application class loader ready for use?  Don't leak it out until
   * it is! */
  private static boolean appCLReady;
  public static void declareApplicationClassLoaderIsReady() {
    appCLReady = true;
  }

  public static ClassLoader getApplicationClassLoader() {
    if (! VM.runningVM)
      return null;              /* trick the boot image writer with null,
                                   which it will use when initializing
                                   java.lang.ClassLoader$StaticData */

    /* Lie, until we are really ready for someone to actually try
     * to use this class loader to load classes and resources.
     */
    if (!appCLReady)
      return VM_BootstrapClassLoader.getBootstrapClassLoader(); 
    
    if (appCL != null)
      return appCL;

    // Sanity Checks:
    //    synchronized (this) {
      if (gettingAppCL > 0 || DBG_APP_CL)
        VM.sysWriteln("JikesRVM: VM_ClassLoader.getApplicationClassLoader(): ", gettingAppCL > 0 ? "Recursively " : "", "invoked with ", gettingAppCL, " previous instances pending");
      if (gettingAppCL > 0) {
        VM.sysFail("JikesRVM: While we are setting up the application class loader, some class required that selfsame application class loader.  This is a chicken-and-egg problem; see a Jikes RVM Guru.");
      }
      ++gettingAppCL;

      String r = getApplicationRepositories();

      if (VM_Properties.verboseBoot >= 1 || DBG_APP_CL)
        VM.sysWriteln("VM_ClassLoader.getApplicationClassLoader(): " +
                      "Initializing Application ClassLoader, with" +
                      " repositories: `", r, "'...");

      appCL = new ApplicationClassLoader(r);

      if (VM_Properties.verboseBoot >= 1 || DBG_APP_CL)
        VM.sysWriteln("VM_ClassLoader.getApplicationClassLoader(): ...initialized Application classloader, to ", appCL.toString());
      --gettingAppCL;
      //    }
    return appCL;
  }

  //----------------//
  // implementation //
  //----------------//

  //
  private static String applicationRepositories;

  // Names of special methods.
  //
  /** "<clinit>" */
  public static final VM_Atom StandardClassInitializerMethodName        = VM_Atom.findOrCreateAsciiAtom("<clinit>");
  /** "()V" */
  public static final VM_Atom StandardClassInitializerMethodDescriptor  = VM_Atom.findOrCreateAsciiAtom("()V");

  /** "<init>" */
  public static final VM_Atom StandardObjectInitializerMethodName       = VM_Atom.findOrCreateAsciiAtom("<init>"); 
  /** "()V" */
  public static final VM_Atom StandardObjectInitializerMethodDescriptor = VM_Atom.findOrCreateAsciiAtom("()V");
  /** "this" */
  public static final VM_Atom StandardObjectInitializerHelperMethodName = VM_Atom.findOrCreateAsciiAtom("this");

  /** "finalize" */
  public static final VM_Atom StandardObjectFinalizerMethodName         = VM_Atom.findOrCreateAsciiAtom("finalize");
  /** "()V" */
  public static final VM_Atom StandardObjectFinalizerMethodDescriptor   = VM_Atom.findOrCreateAsciiAtom("()V");

  // Names of .class file attributes.
  //
  /** "Code" */
  static final VM_Atom codeAttributeName = VM_Atom.findOrCreateAsciiAtom("Code");
  /** "ConstantValue" */
  static final VM_Atom constantValueAttributeName = VM_Atom.findOrCreateAsciiAtom("ConstantValue");
  /** "LineNumberTable" */
  static final VM_Atom lineNumberTableAttributeName = VM_Atom.findOrCreateAsciiAtom("LineNumberTable");
  /** "Exceptions" */
  static final VM_Atom exceptionsAttributeName = VM_Atom.findOrCreateAsciiAtom("Exceptions");
  /** "SourceFile" */
  static final VM_Atom sourceFileAttributeName = VM_Atom.findOrCreateAsciiAtom("SourceFile");
  /** "LocalVariableTable" */
  static final VM_Atom localVariableTableAttributeName = VM_Atom.findOrCreateAsciiAtom("LocalVariableTable");
  /** "Deprecated" */
  static final VM_Atom deprecatedAttributeName = VM_Atom.findOrCreateAsciiAtom("Deprecated");
  /** "InnerClasses" */
  static final VM_Atom innerClassesAttributeName = VM_Atom.findOrCreateAsciiAtom("InnerClasses");
  /** "Synthetic" */
  static final VM_Atom syntheticAttributeName = VM_Atom.findOrCreateAsciiAtom("Synthetic");
  /** "EnclosingMethod" */
  static final VM_Atom enclosingMethodAttributeName = VM_Atom.findOrCreateAsciiAtom("EnclosingMethod");
  /** "Signature" */
  static final VM_Atom signatureAttributeName = VM_Atom.findOrCreateAsciiAtom("Signature");
  /** "RuntimeVisibleAnnotations" */
  static final VM_Atom runtimeVisibleAnnotationsAttributeName = VM_Atom.findOrCreateAsciiAtom("RuntimeVisibleAnnotations");
  /** "RuntimeInvisibleAnnotations" */
  static final VM_Atom runtimeInvisibleAnnotationsAttributeName = VM_Atom.findOrCreateAsciiAtom("RuntimeInvisibleAnnotations");
  /** "RuntimeVisibleParameterAnnotations" */
  static final VM_Atom runtimeVisibleParameterAnnotationsAttributeName = VM_Atom.findOrCreateAsciiAtom("RuntimeVisibleParameterAnnotations");
  /** "RuntimeInvisibleParameterAnnotations" */
  static final VM_Atom runtimeInvisibleParameterAnnotationsAttributeName = VM_Atom.findOrCreateAsciiAtom("RuntimeInvisibleParameterAnnotations");
  /** "AnnotationDefault" */
  static final VM_Atom annotationDefaultAttributeName = VM_Atom.findOrCreateAsciiAtom("AnnotationDefault");

  /** Initialize at boot time.
   */
  public static void boot() {
    appCL = null;
  }

  /**
   * Initialize for boot image writing
   */
  public static void init(String bootstrapClasspath) {
    // specify where the VM's core classes and resources live
    //
    applicationRepositories = "."; // Carried over.
    VM_BootstrapClassLoader.boot(bootstrapClasspath);
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
      VM_Class ans = VM_Class.readClass(tRef, new DataInputStream(is));
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
    input.readUnsignedShort(); // minor ID
    input.readUnsignedShort(); // major ID
    
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
