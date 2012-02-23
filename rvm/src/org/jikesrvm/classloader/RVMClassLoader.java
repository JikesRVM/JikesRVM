/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.Properties;

/**
 * Manufacture type descriptions as needed by the running virtual machine. <p>
 */
public class RVMClassLoader implements Constants, ClassLoaderConstants {

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
      VM.sysWriteln("RVMClassLoader.stashApplicationRepositories: " + "applicationRepositories = ", classpath);
    }
    /* If mis-initialized, trash it. */
    if (appCL != null && !classpath.equals(applicationRepositories)) {
      appCL = null;
      if (DBG_APP_CL) {
        VM.sysWriteln("RVMClassLoader.stashApplicationRepositories: Wiping out my remembered appCL.");
      }
    }
    applicationRepositories = classpath;
  }

  /**
   * Are Java 1.4 style assertions enabled?
   */
  private static boolean assertionsEnabled = false;
  /**
   * String describing packages and classes to enable assertions on (of the form ":<packagename>...|:<classname>")
   */
  private static String[] enabledAssertionStrings;
  /**
   * String describing packages and classes to disable assertions on (of the form ":<packagename>...|:<classname>")
   */
  private static String[] disabledAssertionStrings;

  /**
   * Remember the given enable assertions string
   * @param arg String of the form ":<packagename>...|:<classname>"
   */
  public static void stashEnableAssertionArg(String arg) {
    assertionsEnabled = true;
    enabledAssertionStrings = arg.split(":");
    if (enabledAssertionStrings != null) {
      if ((enabledAssertionStrings.length == 0) ||
          (enabledAssertionStrings.length == 1 && enabledAssertionStrings[0].equals(""))) {
        // force enabled assertion strings to null when no arguments are passed with -ea
        enabledAssertionStrings = null;
      }
    }
  }

  /**
   * Remember the given disable assertions string
   * @param arg String of the form ":<packagename>...|:<classname>"
   */
  public static void stashDisableAssertionArg(String arg) {
    if (arg == null || arg.equals("")) {
      assertionsEnabled = false;
    } else {
      disabledAssertionStrings = arg.split(":");
    }
  }

  /**
   * Calculate the desired assertion status for a freshly loaded class
   * @param klass to check against command line argument
   * @return whether assertions should be enabled on class
   */
  static boolean getDesiredAssertionStatus(RVMClass klass) {
    if (!assertionsEnabled) {
      // trivial - no assertions are enabled
      return false;
    } else {
      if (enabledAssertionStrings == null && disabledAssertionStrings == null) {
        // assertions enabled unconditionally
        return true;
      } else {
        // search command line arguments to see if assertions are enabled
        boolean result = false;
        if(enabledAssertionStrings != null) {
          for (String s : enabledAssertionStrings) {
            if (s.equals(klass.getTypeRef().getName().classNameFromDescriptor()) ||
                klass.getPackageName().startsWith(s)) {
              result = true;
              break;
            }
          }
        }
        if (disabledAssertionStrings != null) {
          for (String s : disabledAssertionStrings) {
            if (s.equals(klass.getTypeRef().getName().classNameFromDescriptor()) ||
                klass.getPackageName().startsWith(s)) {
              result = false;
              break;
            }
          }
        }
        return result;
      }
    }
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
      VM.sysWriteln("RVMClassLoader.setApplicationRepositories: applicationRepositories = ", applicationRepositories);
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
    if (!VM.runningVM) {
      return null;
    }              /* trick the boot image writer with null,
                                   which it will use when initializing
                                   java.lang.ClassLoader$StaticData */

    /* Lie, until we are really ready for someone to actually try
     * to use this class loader to load classes and resources.
     */
    if (!appCLReady) {
      return BootstrapClassLoader.getBootstrapClassLoader();
    }

    if (appCL != null) {
      return appCL;
    }

    // Sanity Checks:
    //    synchronized (this) {
    if (gettingAppCL > 0 || DBG_APP_CL) {
      VM.sysWriteln("JikesRVM: RVMClassLoader.getApplicationClassLoader(): ",
                    gettingAppCL > 0 ? "Recursively " : "",
                    "invoked with ",
                    gettingAppCL,
                    " previous instances pending");
    }
    if (gettingAppCL > 0) {
      VM.sysFail(
          "JikesRVM: While we are setting up the application class loader, some class required that selfsame application class loader.  This is a chicken-and-egg problem; see a Jikes RVM Guru.");
    }
    ++gettingAppCL;

    String r = getApplicationRepositories();

    if (Properties.verboseBoot >= 1 || DBG_APP_CL) {
      VM.sysWriteln("RVMClassLoader.getApplicationClassLoader(): " +
                    "Initializing Application ClassLoader, with" +
                    " repositories: `", r, "'...");
    }

    appCL = new ApplicationClassLoader(r);

    if (Properties.verboseBoot >= 1 || DBG_APP_CL) {
      VM.sysWriteln("RVMClassLoader.getApplicationClassLoader(): ...initialized Application classloader, to ",
                    appCL.toString());
    }
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
  public static final Atom StandardClassInitializerMethodName = Atom.findOrCreateAsciiAtom("<clinit>");
  /** "()V" */
  public static final Atom StandardClassInitializerMethodDescriptor = Atom.findOrCreateAsciiAtom("()V");

  /** "<init>" */
  public static final Atom StandardObjectInitializerMethodName = Atom.findOrCreateAsciiAtom("<init>");
  /** "()V" */
  public static final Atom StandardObjectInitializerMethodDescriptor = Atom.findOrCreateAsciiAtom("()V");
  /** "this" */
  public static final Atom StandardObjectInitializerHelperMethodName = Atom.findOrCreateAsciiAtom("this");

  /** "finalize" */
  public static final Atom StandardObjectFinalizerMethodName = Atom.findOrCreateAsciiAtom("finalize");
  /** "()V" */
  public static final Atom StandardObjectFinalizerMethodDescriptor = Atom.findOrCreateAsciiAtom("()V");

  // Names of .class file attributes.
  //
  /** "Code" */
  static final Atom codeAttributeName = Atom.findOrCreateAsciiAtom("Code");
  /** "ConstantValue" */
  static final Atom constantValueAttributeName = Atom.findOrCreateAsciiAtom("ConstantValue");
  /** "LineNumberTable" */
  static final Atom lineNumberTableAttributeName = Atom.findOrCreateAsciiAtom("LineNumberTable");
  /** "Exceptions" */
  static final Atom exceptionsAttributeName = Atom.findOrCreateAsciiAtom("Exceptions");
  /** "SourceFile" */
  static final Atom sourceFileAttributeName = Atom.findOrCreateAsciiAtom("SourceFile");
  /** "LocalVariableTable" */
  static final Atom localVariableTableAttributeName = Atom.findOrCreateAsciiAtom("LocalVariableTable");
  /** "Deprecated" */
  static final Atom deprecatedAttributeName = Atom.findOrCreateAsciiAtom("Deprecated");
  /** "InnerClasses" */
  static final Atom innerClassesAttributeName = Atom.findOrCreateAsciiAtom("InnerClasses");
  /** "Synthetic" */
  static final Atom syntheticAttributeName = Atom.findOrCreateAsciiAtom("Synthetic");
  /** "EnclosingMethod" */
  static final Atom enclosingMethodAttributeName = Atom.findOrCreateAsciiAtom("EnclosingMethod");
  /** "Signature" */
  static final Atom signatureAttributeName = Atom.findOrCreateAsciiAtom("Signature");
  /** "RuntimeVisibleAnnotations" */
  static final Atom runtimeVisibleAnnotationsAttributeName =
      Atom.findOrCreateAsciiAtom("RuntimeVisibleAnnotations");
  /** "RuntimeInvisibleAnnotations" */
  static final Atom runtimeInvisibleAnnotationsAttributeName =
      Atom.findOrCreateAsciiAtom("RuntimeInvisibleAnnotations");
  /** "RuntimeVisibleParameterAnnotations" */
  static final Atom runtimeVisibleParameterAnnotationsAttributeName =
      Atom.findOrCreateAsciiAtom("RuntimeVisibleParameterAnnotations");
  /** "RuntimeInvisibleParameterAnnotations" */
  static final Atom runtimeInvisibleParameterAnnotationsAttributeName =
      Atom.findOrCreateAsciiAtom("RuntimeInvisibleParameterAnnotations");
  /** "AnnotationDefault" */
  static final Atom annotationDefaultAttributeName = Atom.findOrCreateAsciiAtom("AnnotationDefault");

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
    BootstrapClassLoader.boot(bootstrapClasspath);
  }

  public static RVMType defineClassInternal(String className, byte[] classRep, int offset, int length,
                                            ClassLoader classloader) throws ClassFormatError {
    return defineClassInternal(className, new ByteArrayInputStream(classRep, offset, length), classloader);
  }

  public static RVMType defineClassInternal(String className, InputStream is, ClassLoader classloader)
      throws ClassFormatError {
    TypeReference tRef;
    if (className == null) {
      // NUTS: Our caller hasn't bothered to tell us what this class is supposed
      //       to be called, so we must read the input stream and discover it ourselves
      //       before we actually can create the RVMClass instance.
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
      Atom classDescriptor = Atom.findOrCreateAsciiAtom(className.replace('.', '/')).descriptorFromClassName();
      tRef = TypeReference.findOrCreate(classloader, classDescriptor);
    }

    try {
      if (VM.VerifyAssertions) VM._assert(tRef.isClassType());
      if (VM.TraceClassLoading && VM.runningVM) {
        VM.sysWriteln("loading \"" + tRef.getName() + "\" with " + classloader);
      }
      RVMClass ans = ClassFileReader.readClass(tRef, new DataInputStream(is));
      tRef.setType(ans);
      return ans;
    } catch (IOException e) {
      ClassFormatError cfe = new ClassFormatError(e.getMessage());
      cfe.initCause(e);
      throw cfe;
    }
  }

  // Shamelessly cloned & owned from ClassFileReader.readClass constructor....
  private static TypeReference getClassTypeRef(DataInputStream input, ClassLoader cl)
      throws IOException, ClassFormatError {
    int magic = input.readInt();
    if (magic != 0xCAFEBABE) {
      throw new ClassFormatError("bad magic number " + Integer.toHexString(magic));
    }

    // Drop class file version number on floor. readClass will do the check later.
    input.readUnsignedShort(); // minor ID
    input.readUnsignedShort(); // major ID

    //
    // pass 1: read constant pool
    //
    int[] constantPool = new int[input.readUnsignedShort()];
    byte[] tmpTags = new byte[constantPool.length];

    // note: slot 0 is unused
    for (int i = 1; i < constantPool.length; i++) {
      tmpTags[i] = input.readByte();
      switch (tmpTags[i]) {
        case TAG_UTF: {
          byte[] utf = new byte[input.readUnsignedShort()];
          input.readFully(utf);
          constantPool[i] = Atom.findOrCreateUtf8Atom(utf).getId();
          break;
        }

        case TAG_UNUSED:
          break;

        case TAG_INT:
        case TAG_FLOAT:
        case TAG_FIELDREF:
        case TAG_METHODREF:
        case TAG_INTERFACE_METHODREF:
        case TAG_MEMBERNAME_AND_DESCRIPTOR:
          input.readInt(); // drop on floor
          break;

        case TAG_LONG:
        case TAG_DOUBLE:
          i++;
          input.readLong(); // drop on floor
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
    for (int i = 1; i < constantPool.length; i++) {
      switch (tmpTags[i]) {
        case TAG_LONG:
        case TAG_DOUBLE:
          ++i;
          break;

        case TAG_TYPEREF: { // in: utf index
          Atom typeName = Atom.getAtom(constantPool[constantPool[i]]);
          constantPool[i] = TypeReference.findOrCreate(cl, typeName.descriptorFromClassName()).getId();
          break;
        } // out: type reference id
      }
    }

    // drop modifiers on floor.
    input.readUnsignedShort();

    int myTypeIndex = input.readUnsignedShort();
    return TypeReference.getTypeRef(constantPool[myTypeIndex]);
  }


  /**
   * An unpleasant hack to deal with the problem of replaying work
   * when using dynamic classloaders (whose identity will vary from
   * run to run).  When we can't find the classloader that was specified,
   * see if there are any other (non-matching) classloaders that
   * have the relevant class loaded.
   *
   * @param clazz The class we're after
   * @return A usable classloader or null
   */
  public static ClassLoader findWorkableClassloader(Atom clazz) {
    for (ClassLoader clx: TypeReference.getCLDict()) {
      TypeReference tRef = TypeReference.findOrCreate(clx, clazz);
      RVMClass cls = (RVMClass) tRef.peekType();

      if (cls != null)
        return clx;
      else {
        try {
          cls = tRef.resolve().asClass();
          cls.resolve();
          cls.instantiate();
          cls.initialize();
          return clx;
        } catch (NoClassDefFoundError cnf) {
          /* silently catch this exception and try another class loader */
        }
      }
    }
    return null;
  }
}
