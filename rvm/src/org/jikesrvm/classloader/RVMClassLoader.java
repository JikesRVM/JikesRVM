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
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.security.ProtectionDomain;

import org.jikesrvm.Properties;
import org.jikesrvm.VM;
import org.jikesrvm.classlibrary.JavaLangInstrument;

/**
 * Manufacture type descriptions as needed by the running virtual machine.
 */
public class RVMClassLoader {

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
   * String describing packages and classes to enable assertions on (of the form ":&lt;packagename&gt;...|:&lt;classname&gt;")
   */
  private static String[] enabledAssertionStrings;
  /**
   * String describing packages and classes to disable assertions on (of the form ":&lt;packagename&gt;...|:&lt;classname&gt;")
   */
  private static String[] disabledAssertionStrings;

  /**
   * Remember the given enable assertions string
   * @param arg String of the form ":&lt;packagename&gt;...|:&lt;classname&gt;"
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
   * @param arg String of the form ":&lt;packagename&gt;...|:&lt;classname&gt;"
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
        if (enabledAssertionStrings != null) {
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
   * Set list of places to be searched for application classes and resources.<p>
   *
   * Our Jikes RVM classloader can not handle having the class path reset
   * after it's been set up.   Unfortunately, it is stashed by classes in
   * GNU Classpath.
   *
   * @param classpath path specification in standard "classpath" format
   */
  public static void setApplicationRepositories(String classpath) {
    System.setProperty("java.class.path", classpath);
    stashApplicationRepositories(classpath);
    if (DBG_APP_CL) {
      VM.sysWriteln("RVMClassLoader.setApplicationRepositories: applicationRepositories = ", applicationRepositories);
    }
  }

  /**
   * Overwrites the application repositories with the given classpath.
   * <p>
   * Only for tests.
   *
   * @param classpath the new classpath
   */
  static void overwriteApplicationRepositoriesForUnitTest(String classpath) {
    applicationRepositories = classpath;
  }

  /**
   * Overwrites the agent repositories with the given classpath.
   * <p>
   * Only for tests.
   *
   * @param classpath the new classpath
   */
  static void overwriteAgentPathForUnitTest(String classpath) {
    agentRepositories = classpath;
  }

  private static String buildRealClasspath(String classpath) {
    if (agentRepositories == null) {
      return classpath;
    }
    return classpath + File.pathSeparator + agentRepositories;
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
   * The classpath entries that are added implicitly by Java Agents for
   * the jars that contain the agents.
   */
  private static String agentRepositories;

  /**
   * Adds repositories for a Java Agent.
   *
   * @param agentClasspath the classpath entry to add
   */
  public static void addAgentRepositories(String agentClasspath) {
    if (agentRepositories == null) {
      agentRepositories = agentClasspath;
    } else {
      agentRepositories = agentRepositories + File.pathSeparator + agentClasspath;
    }
  }

  /**
   * Rebuilds the application repositories to include jars for Java agents.
   * Called after command line arg parsing is done.
   */
  public static void rebuildApplicationRepositoriesWithAgents() {
    if (agentRepositories == null) {
      return;
    }
    String newApplicationRepositories = applicationRepositories + File.pathSeparator + agentRepositories;
    setApplicationRepositories(newApplicationRepositories);
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
  /** {@code <clinit>} */
  public static final Atom StandardClassInitializerMethodName = Atom.findOrCreateAsciiAtom("<clinit>");
  /** "()V" */
  public static final Atom StandardClassInitializerMethodDescriptor = Atom.findOrCreateAsciiAtom("()V");

  /** {@code <init>} */
  public static final Atom StandardObjectInitializerMethodName = Atom.findOrCreateAsciiAtom("<init>");
  /** {@code ()V} */
  public static final Atom StandardObjectInitializerMethodDescriptor = Atom.findOrCreateAsciiAtom("()V");
  /** {@code this} */
  public static final Atom StandardObjectInitializerHelperMethodName = Atom.findOrCreateAsciiAtom("this");

  /** {@code finalize} */
  public static final Atom StandardObjectFinalizerMethodName = Atom.findOrCreateAsciiAtom("finalize");
  /** {@code ()V} */
  public static final Atom StandardObjectFinalizerMethodDescriptor = Atom.findOrCreateAsciiAtom("()V");

  // Names of .class file attributes.
  //
  /** {@code Code} */
  static final Atom codeAttributeName = Atom.findOrCreateAsciiAtom("Code");
  /** {@code ConstantValue} */
  static final Atom constantValueAttributeName = Atom.findOrCreateAsciiAtom("ConstantValue");
  /** {@code LineNumberTable} */
  static final Atom lineNumberTableAttributeName = Atom.findOrCreateAsciiAtom("LineNumberTable");
  /** {@code Exceptions} */
  static final Atom exceptionsAttributeName = Atom.findOrCreateAsciiAtom("Exceptions");
  /** {@code SourceFile} */
  static final Atom sourceFileAttributeName = Atom.findOrCreateAsciiAtom("SourceFile");
  /** {@code LocalVariableTable} */
  static final Atom localVariableTableAttributeName = Atom.findOrCreateAsciiAtom("LocalVariableTable");
  /** {@code Deprecated}  */
  static final Atom deprecatedAttributeName = Atom.findOrCreateAsciiAtom("Deprecated");
  /** {@code InnerClasses} */
  static final Atom innerClassesAttributeName = Atom.findOrCreateAsciiAtom("InnerClasses");
  /** {@code Synthetic} */
  static final Atom syntheticAttributeName = Atom.findOrCreateAsciiAtom("Synthetic");
  /** {@code EnclosingMethod} */
  static final Atom enclosingMethodAttributeName = Atom.findOrCreateAsciiAtom("EnclosingMethod");
  /** {@code Signature} */
  static final Atom signatureAttributeName = Atom.findOrCreateAsciiAtom("Signature");
  /** {@code RuntimeVisibleAnnotations} */
  static final Atom runtimeVisibleAnnotationsAttributeName =
      Atom.findOrCreateAsciiAtom("RuntimeVisibleAnnotations");
  /** {@code RuntimeInvisibleAnnotations} */
  static final Atom runtimeInvisibleAnnotationsAttributeName =
      Atom.findOrCreateAsciiAtom("RuntimeInvisibleAnnotations");
  /** {@code RuntimeVisibleParameterAnnotations} */
  static final Atom runtimeVisibleParameterAnnotationsAttributeName =
      Atom.findOrCreateAsciiAtom("RuntimeVisibleParameterAnnotations");
  /** {@code RuntimeInvisibleParameterAnnotations} */
  static final Atom runtimeInvisibleParameterAnnotationsAttributeName =
      Atom.findOrCreateAsciiAtom("RuntimeInvisibleParameterAnnotations");
  /** {@code AnnotationDefault} */
  static final Atom annotationDefaultAttributeName = Atom.findOrCreateAsciiAtom("AnnotationDefault");

  /** Initialize at boot time.
   */
  public static void boot() {
    appCL = null;
  }

  /**
   * Initialize for boot image writing.
   *
   * @param bootstrapClasspath the bootstrap classpath to load the classes from
   */
  public static void init(String bootstrapClasspath) {
    // specify where the VM's core classes and resources live
    //
    applicationRepositories = "."; // Carried over.
    BootstrapClassLoader.boot(bootstrapClasspath);
  }

  public static RVMType defineClassInternal(String className, byte[] classRep, int offset, int length,
      ClassLoader classloader, ProtectionDomain pd) throws ClassFormatError {
    if (VM.BuildForOpenJDK && VM.runningVM && JavaLangInstrument.instrumentationReady()) {
      try {
        String classNameInternal = ClassNameHelpers.convertClassnameToInternalName(className);
        byte[] res = (byte[]) JavaLangInstrument.getTransformMethod().invoke(JavaLangInstrument.getInstrumenter(), classloader, classNameInternal, null, pd, classRep, false);
        if (res != null) {
          classRep = res;
        }
      } catch (SecurityException e) {
        ignoreExceptionOnNormalBuildsAndFailOnAssserionEnabledBuilds(e);
      } catch (IllegalArgumentException e) {
        ignoreExceptionOnNormalBuildsAndFailOnAssserionEnabledBuilds(e);
      } catch (IllegalAccessException e) {
        ignoreExceptionOnNormalBuildsAndFailOnAssserionEnabledBuilds(e);
      } catch (InvocationTargetException e) {
        ignoreExceptionOnNormalBuildsAndFailOnAssserionEnabledBuilds(e);
      }
    }
    return defineClassInternal(className, classRep, offset, length, classloader);
  }

  private static void ignoreExceptionOnNormalBuildsAndFailOnAssserionEnabledBuilds(Exception e) {
    if (VM.VerifyAssertions) {
      e.printStackTrace();
      VM._assert(VM.NOT_REACHED, "Transformation of class failed with exception");
    } else {
      // continue with untransformed class
    }
  }

  public static RVMType defineClassInternal(String className, InputStream is, ClassLoader classloader,
      ProtectionDomain pd) throws ClassFormatError {
    return defineClassInternal(className, is, classloader);
  }

  static RVMType defineClassInternal(String className, byte[] classRep, int offset, int length,
                                            ClassLoader classloader) throws ClassFormatError {
    return defineClassInternal(className, new ByteArrayInputStream(classRep, offset, length), classloader);
  }

  static RVMType defineClassInternal(String className, InputStream is, ClassLoader classloader)
      throws ClassFormatError {
    ClassFileReader reader = new ClassFileReader(is);
    return reader.readClass(className, classloader);
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
          cls.prepareForFirstUse();
          return clx;
        } catch (NoClassDefFoundError cnf) {
          /* silently catch this exception and try another class loader */
        }
      }
    }
    return null;
  }
}
