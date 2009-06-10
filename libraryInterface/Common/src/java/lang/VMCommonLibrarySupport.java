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
package java.lang;

import static org.jikesrvm.runtime.SysCall.sysCall;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMember;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.scheduler.Synchronization;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.unboxed.Offset;

/**
 * Common utilities for Jikes RVM implementations of the java.lang API
 */
final class VMCommonLibrarySupport {
  /* ---- Non-inlined Exception Throwing Methods --- */
  /**
   * Method just to throw an illegal access exception without being inlined
   */
  @NoInline
  private static void throwNewIllegalAccessException(RVMMember member, RVMClass accessingClass) throws IllegalAccessException{
    throw new IllegalAccessException("Access to " + member + " is denied to " + accessingClass);
  }
  /* ---- General Reflection Support ---- */
  /**
   * Check to see if a method declared by the accessingClass
   * should be allowed to access the argument RVMMember.
   * Assumption: member is not public.  This trivial case should
   * be approved by the caller without needing to call this method.
   */
  static void checkAccess(RVMMember member, RVMClass accessingClass) throws IllegalAccessException {
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
  static void gc() {
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
  static void arraycopy(Object src, int srcPos, Object dst, int dstPos, int len) {
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

  /**
   * Set the value of a static final stream field of the System class
   * @param fieldName name of field to set
   * @param stream value
   */
  static void setSystemStreamField(String fieldName, Object stream) {
    try {
      RVMField field = ((RVMClass)JikesRVMSupport.getTypeForClass(System.class))
        .findDeclaredField(Atom.findOrCreateUnicodeAtom(fieldName));
      field.setObjectValueUnchecked(null, stream);
    } catch (Exception e) {
      throw new Error(e.toString());
    }
  }
  /**
   * Apply library prefixes and suffixes as necessary to libname to produce a
   * full file name. For example, on linux "rvm" would become "librvm.so".
   *
   * @param libname name of library without any prefix or suffix
   * @return complete name of library
   */
  static String mapLibraryName(String libname) {
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
  static String getenv(String envarName) {
    byte[] buf = new byte[128]; // Modest amount of space for starters.

    byte[] nameBytes = envarName.getBytes();

    // sysCall is uninterruptible so passing buf is safe
    int len = sysCall.sysGetenv(nameBytes, buf, buf.length);

    if (len < 0)                // not set.
      return null;

    if (len > buf.length) {
      buf = new byte[len];
      sysCall.sysGetenv(nameBytes, buf, len);
    }
    return new String(buf, 0, len);
  }
}
