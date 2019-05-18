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
package org.jikesrvm.classlibrary;

import static org.jikesrvm.runtime.SysCall.sysCall;

import java.lang.reflect.Modifier;
import java.util.Properties;

import org.jikesrvm.Configuration;
import org.jikesrvm.VM;
import org.jikesrvm.architecture.StackFrameLayout;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.BootstrapClassLoader;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMember;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.runtime.CommandLineArgs;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.scheduler.Synchronization;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Pure;
import org.vmmagic.unboxed.Offset;

/**
 * Common utilities for Jikes RVM implementations of the java.lang API
 */
public final class JavaLangSupport {
  /* ---- Non-inlined Exception Throwing Methods --- */
  /**
   * Method just to throw an illegal access exception without being inlined
   */
  @NoInline
  private static void throwNewIllegalAccessException(RVMMember member, RVMClass accessingClass) throws IllegalAccessException {
    throw new IllegalAccessException("Access to " + member + " is denied to " + accessingClass);
  }
  /* ---- General Reflection Support ---- */
  /**
   * Check to see if a method declared by the accessingClass
   * should be allowed to access the argument RVMMember.
   * Assumption: member is not public.  This trivial case should
   * be approved by the caller without needing to call this method.
   */
  public static void checkAccess(RVMMember member, RVMClass accessingClass) throws IllegalAccessException {
    RVMClass declaringClass = member.getDeclaringClass();
    if (member.isPrivate()) {
      // access from the declaringClass is allowed
      if (accessingClass == declaringClass) return;
    } else if (member.isProtected()) {
      // access within the package is allowed.
      if (declaringClass.getClassLoader() == accessingClass.getClassLoader() && declaringClass.getPackageName().equals(accessingClass.getPackageName())) return;

      // access by subclasses is allowed.
      for (RVMClass cls = accessingClass; cls != null; cls = cls.getSuperClass()) {
        if (accessingClass == declaringClass) return;
      }
    } else {
      // default: access within package is allowed
      if (declaringClass.getClassLoader() == accessingClass.getClassLoader() && declaringClass.getPackageName().equals(accessingClass.getPackageName())) return;
    }
    throwNewIllegalAccessException(member, accessingClass);
  }
  /* ---- Runtime Support ---- */
  /**
   * Class responsible for acquiring and call the gc method. If a call has
   * taken place the gc method will just return.
   */
  private static final class GCLock {
    @SuppressWarnings("unused") // Accessed from EntryPoints
    @Entrypoint
    private int gcLock;
    private final Offset gcLockOffset = Entrypoints.gcLockField.getOffset();
    GCLock() {}
    void gc() {
      if (Synchronization.testAndSet(this, gcLockOffset, 1)) {
        MemoryManager.gc();
        Synchronization.fetchAndStore(this, gcLockOffset, 0);
      }
    }
  }
  private static final GCLock gcLockSingleton = new GCLock();

  /**
   * Request GC
   */
  public static void gc() {
    gcLockSingleton.gc();
  }

  /**
   * Copy src array to dst array from location srcPos for length len to dstPos
   *
   * @param src array
   * @param srcPos position within source array
   * @param dst array
   * @param dstPos position within destination array
   * @param len amount of elements to copy
   */
  @Entrypoint
  public static void arraycopy(Object src, int srcPos, Object dst, int dstPos, int len) {
    if (src == null || dst == null) {
      RuntimeEntrypoints.raiseNullPointerException();
    } else if ((src instanceof char[]) && (dst instanceof char[])) {
      RVMArray.arraycopy((char[])src, srcPos, (char[])dst, dstPos, len);
    } else if ((src instanceof Object[]) && (dst instanceof Object[])) {
      RVMArray.arraycopy((Object[])src, srcPos, (Object[])dst, dstPos, len);
    } else if ((src instanceof byte[]) && (dst instanceof byte[])) {
      RVMArray.arraycopy((byte[])src, srcPos, (byte[])dst, dstPos, len);
    } else if ((src instanceof boolean[]) && (dst instanceof boolean[])) {
      RVMArray.arraycopy((boolean[])src, srcPos, (boolean[])dst, dstPos, len);
    } else if ((src instanceof short[]) && (dst instanceof short[])) {
      RVMArray.arraycopy((short[])src, srcPos, (short[])dst, dstPos, len);
    } else if ((src instanceof int[]) && (dst instanceof int[])) {
      RVMArray.arraycopy((int[])src, srcPos, (int[])dst, dstPos, len);
    } else if ((src instanceof long[]) && (dst instanceof long[])) {
      RVMArray.arraycopy((long[])src, srcPos, (long[])dst, dstPos, len);
    } else if ((src instanceof float[]) && (dst instanceof float[])) {
      RVMArray.arraycopy((float[])src, srcPos, (float[])dst, dstPos, len);
    } else if ((src instanceof double[]) && (dst instanceof double[])) {
      RVMArray.arraycopy((double[])src, srcPos, (double[])dst, dstPos, len);
    } else {
      RuntimeEntrypoints.raiseArrayStoreException();
    }
  }

  public static int identityHashCode(Object obj) {
    return obj == null ? 0 : ObjectModel.getObjectHashCode(obj);
  }

  /**
   * Set the value of a static final stream field of the System class
   * @param fieldName name of field to set
   * @param stream value
   */
  public static void setSystemStreamField(String fieldName, Object stream) {
    try {
      RVMField field = ((RVMClass)JikesRVMSupport.getTypeForClass(System.class))
        .findDeclaredField(Atom.findOrCreateUnicodeAtom(fieldName));
      field.setObjectValueUnchecked(null, stream);
    } catch (Exception e) {
      throw new Error("Error setting stream field " + fieldName + " of java.lang.System", e);
    }
  }
  /**
   * Apply library prefixes and suffixes as necessary to libname to produce a
   * full file name. For example, on linux "rvm" would become "librvm.so".
   *
   * @param libname name of library without any prefix or suffix
   * @return complete name of library
   */
  public static String mapLibraryName(String libname) {
    String libSuffix;
    if (VM.BuildForLinux || VM.BuildForSolaris) {
      libSuffix = ".so";
    } else if (VM.BuildForOsx) {
      libSuffix = ".jnilib";
    } else {
      libSuffix = ".a";
    }
    return "lib" + libname + libSuffix;
  }
  /**
   * Get the value of an environment variable.
   */
  public static String getenv(String envarName) {
    byte[] buf = new byte[128]; // Modest amount of space for starters.

    byte[] nameBytes = envarName.getBytes();
    byte[] nameBytesWithNullTerminator = new byte[nameBytes.length + 1];
    System.arraycopy(nameBytes, 0, nameBytesWithNullTerminator, 0, nameBytes.length);

    // sysCall is uninterruptible so passing buf is safe
    int len = sysCall.sysGetenv(nameBytesWithNullTerminator, buf, buf.length);

    if (len < 0)                // not set.
      return null;

    if (len > buf.length) {
      buf = new byte[len];
      sysCall.sysGetenv(nameBytesWithNullTerminator, buf, len);
    }
    return new String(buf, 0, len);
  }

  /* --- String support --- */

  public static String intern(String str) {
    if (VM.VerifyAssertions) VM._assert(VM.runningVM);
    return Atom.internString(str);
  }

  /* --- Stack trace support --- */

  /**
   * Converts from {@link java.lang.Thread} stack size to Jikes
   * RVM internal stack size.
   * <p>
   * Note that Jikes RVM currently restricts the stack size to be an integer.
   * This is consistent with the API specification of the java.lang.Thread
   * constructors. The spec says that the VM is free to treat the stack size
   * from java.lang.Thread as a suggestion.
   *
   * @param stacksize the stack size coming from java.lang.Thread.
   *  0 means that the VM should use the values that should use the values
   *  that it would use if stack size weren't specified.
   * @return the actual stack size to use
   */
  public static int stackSizeFromAPIToJikesRVM(long stacksize) {
    if (stacksize > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    } else if (stacksize == 0) {
      // According to the JavaDoc of the java.lang.Thread constructors,
      // a stack size of 0 means that the VM should use the values that
      // it would use if stack size weren't specified.
      return StackFrameLayout.getNormalStackSize();
    } else if (stacksize < 0) {
      if (VM.VerifyAssertions) {
        String failingStackSize = "Received invalid stack size " + stacksize +
            " from java.lang.Thread!";
        VM._assert(VM.NOT_REACHED, failingStackSize);
      }
      return StackFrameLayout.getNormalStackSize();
    }
    return (int) stacksize;
  }

  // Properties support

  /**
   * Sets standard Java properties.
   * <p>
   * Most of these are useful for all class libaries.
   */
  public static void setupStandardJavaProperties(Properties p) {
    p.put("java.version", "1.6.0"); /* This is a lie, of course -- we don't
    really support all 1.6 features, such
    as assertions.  However, it is a
    necessary lie, since Eclipse 3.0
    explicitly tests java.version and
    insists upon at least 1.4.1 to run. */

    p.put("java.vm.name", "Jikes RVM");
    p.put("java.vm.info", org.jikesrvm.JMXSupport.getVmInfo());
    p.put("java.vm.vendor", "Jikes RVM Project");
    p.put("java.vm.version", Configuration.RVM_CONFIGURATION + " " + Configuration.RVM_VERSION_STRING);

    p.put("java.vendor", "Jikes RVM Project");
    p.put("java.vendor.url", "http://www.jikesrvm.org");
    p.put("java.vendor.url.bug", "http://www.jikesrvm.org/ReportingBugs/");

    p.put("java.specification.name", "Java Platform API Specification");
    p.put("java.specification.vendor", "Sun Microsystems Inc.");
    p.put("java.specification.version", "1.6");

    p.put("java.vm.specification.name", "Java Virtual Machine Specification");
    p.put("java.vm.specification.vendor", "Sun Microsystems Inc.");
    p.put("java.vm.specification.version", "1.0");

    /* 50.0 brings us through Java version 1.6. */
    p.put("java.class.version", "50.0");

    p.put("file.separator", "/");
    p.put("path.separator", ":");
    p.put("line.separator", "\n");

    p.put("java.compiler", "JikesRVM");
    p.put("java.vm.version", "1.6.0");
    p.put("java.vm.name", "JikesRVM");

    p.put("file.encoding", "8859_1");
    p.put("java.io.tmpdir", "/tmp");

    /* java.library.path
    Now the library path.  This is the path used for system
    dynamically-loaded libraries, the things that end in ".so" on Linux. */
    insertLibraryPath(p);

    String bootClassPath = BootstrapClassLoader.getBootstrapRepositories();
    p.put("java.boot.class.path", bootClassPath);

    /* What should we do about java.ext.dirs?
    XXX TODO

    java.ext.dirs is allegedly mandatory, according to the API docs shipped
    with the Sun 1.4.2 JDK.

    Ridiculous, since we don't search it for anything, and since if the
    user were to set it it wouldn't do anything anyway.   We keep all of
    the extensions stored with the other bits of the JDK.   So, this would
    really need to be prepended to the list of VM classes, wouldn't it?  Or
    appended, perhaps? */
    String s = CommandLineArgs.getEnvironmentArg("java.ext.dirs");
    if (s == null) {
      s = "";
    } else {
      VM.sysWrite("Jikes RVM: Warning: You have explicitly set java.ext.dirs; that will not do anything under Jikes RVM");
    }
    p.put("java.ext.dirs", s);

    /* We also set java.class.path in setApplicationRepositories().
     *  We'll treat setting the java.class.path property as essentially
     * equivalent to using the -classpath argument. */
    s = CommandLineArgs.getEnvironmentArg("java.class.path");
    if (s != null) {
      p.put("java.class.path", s);
      RVMClassLoader.stashApplicationRepositories(s);
    } else {
      p.put("java.class.path", RVMClassLoader.getApplicationRepositories());
    }

    /* Now the rest of the special ones that we set on the command line.   Do
     * this just in case later revisions of the class libraries start to require
     * some of them in the boot process; otherwise, we could wait for them to
     * be set in CommandLineArgs.lateProcessCommandLineArguments() */
    final String[] clProps = new String[] {"os.name", "os.arch", "os.version", "user.name", "user.home", "user.dir", "java.home"};

    for (final String prop : clProps) {
      s = CommandLineArgs.getEnvironmentArg(prop);
      if (s != null) {
        p.put(prop, s);
      }
    }
  }

  public static void setTimezoneProperties(Properties p) {
    String s = CommandLineArgs.getEnvironmentArg("user.timezone");
    s = (s == null) ? "" : s;   // Maybe it's silly to set it to the empty
                                // string.  Well, this should never succeed
                                // anyway, since we're always called by
                                // runrvm, which explicitly sets the value.
    p.put("user.timezone", s);
  }

  /** Set java.library.path.<p>
   *
   * I wish I knew where to check in the source code to confirm that this
   * is, in fact, the process we actually follow.  I do not understand this
   * code.  I do not understand why we are adding something to
   * java.library.path.  --Steve Augart, 3/23/2004 XXX
   *
   * @param p the properties to modify
   */
  private static void insertLibraryPath(Properties p) {
    String jlp = CommandLineArgs.getEnvironmentArg("java.library.path");
    String snp = CommandLineArgs.getEnvironmentArg("java.home");
    if (jlp == null) jlp = ".";
    p.put("java.library.path", snp + p.get("path.separator") + jlp);
  }

  /* ---- java.lang.Class Support ---- */

  @Pure
  public static int getModifiersFromRvmType(RVMType type) {
    if (type.isClassType()) {
      return type.asClass().getOriginalModifiers();
    } else if (type.isArrayType()) {
      RVMType innermostElementType = type.asArray().getInnermostElementType();
      int result = Modifier.FINAL;
      if (innermostElementType.isClassType()) {
        int component = innermostElementType.asClass().getOriginalModifiers();
        result |= (component & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE));
      } else {
        result |= Modifier.PUBLIC; // primitive
      }
      return result;
    } else {
      return Modifier.PUBLIC | Modifier.FINAL;
    }
  }

  @Pure
  public static boolean validArrayDescriptor(String name) {
    int i;
    int length = name.length();

    for (i = 0; i < length; i++)
      if (name.charAt(i) != '[') break;
    if (i == length) return false;      // string of only ['s

    if (i == length - 1) {
      switch (name.charAt(i)) {
      case 'B': return true;    // byte
      case 'C': return true;    // char
      case 'D': return true;    // double
      case 'F': return true;    // float
      case 'I': return true;    // integer
      case 'J': return true;    // long
      case 'S': return true;    // short
      case 'Z': return true;    // boolean
      default:  return false;
      }
    } else if (name.charAt(i) != 'L') {
      return false;    // not a class descriptor
    } else if (name.charAt(length - 1) != ';') {
      return false;     // ditto
    }
    return true;                        // a valid class descriptor
  }

  // TODO OPENJDK/ICEDTEA use this for GNU Classpath
  @Pure
  public static boolean isInstanceOf(Class<?> c, Object o) {
    if (o == null) return false;
    if (c.isPrimitive())  return false;
    return c.isAssignableFrom(o.getClass());
  }

}
