/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package java.lang;

import java.io.File;

import org.jikesrvm.*;
import org.jikesrvm.runtime.VM_DynamicLibrary;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.scheduler.greenthreads.VM_Process;
import org.jikesrvm.scheduler.VM_Synchronization;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.memorymanagers.mminterface.*;

import org.vmmagic.unboxed.Offset;
import org.vmmagic.pragma.Entrypoint;

/**
 * Jikes RVM implementation of GNU Classpath's java.lang.VMRuntime.
 * See reference implementation for javadoc.
 */
public final class VMRuntime {

  private static boolean runFinalizersOnExit = false;

  static {
    instance = new VMRuntime();
    gcLockOffset = VM_Entrypoints.gcLockField.getOffset();
  }
  private static final VMRuntime instance;
  @SuppressWarnings("unused") // Accessed from VM_EntryPoints
  @Entrypoint
  private int gcLock;
  private static final Offset gcLockOffset;

  private VMRuntime() { }

  static int availableProcessors() {
    return VM_Scheduler.availableProcessors();
  }

  static long freeMemory() {
    return MM_Interface.freeMemory().toLong();
  }

  static long totalMemory() {
    return MM_Interface.totalMemory().toLong();
  }

  static long maxMemory() {
    return MM_Interface.maxMemory().toLong();
  }

  static void gc() {
    if (VM_Synchronization.testAndSet(instance, gcLockOffset, 1)) {
      MM_Interface.gc();
      VM_Synchronization.fetchAndStore(instance, gcLockOffset, 0);
    }
  }

  static void runFinalization() {
    // TODO: talk to Steve B & Perry and figure out what to do.
    // as this is a hint, we can correctly ignore it.
    // However, there might be something else we should do.
  }

  static void runFinalizationForExit() {
    if (runFinalizersOnExit) {
      // TODO: talk to Steve B & Perry and figure out what to do.
      throw new VM_UnimplementedError();
    }
  }

  static void traceInstructions(boolean on) {
    // VMs are free to ignore this...
  }

  static void traceMethodCalls(boolean on) {
    // VMs are free to ignore this...
  }

  static void runFinalizersOnExit(boolean value) {
    runFinalizersOnExit = value;
  }

  static void exit(int status) {
    VM.sysExit(status);
  }

  /** <b>XXX TODO</b> We currently ignore the
   * <code>loader</code> parameter.
   * @param loader Ignored.  null means the bootstrap class loader.
   * @return nonzero on success, zero on failure. */
  static int nativeLoad(String libName, ClassLoader loader) {
    return VM_DynamicLibrary.load(libName);
  }


  /** Mangle a short-name to the file name (not the full pathname) for a
   *  dynamically loadable library.
   */
  static String mapLibraryName(String libname) {
    String libSuffix;
    if (VM.BuildForLinux) {
      libSuffix = ".so";
    } else if (VM.BuildForOsx) {
      libSuffix = ".jnilib";
    } else {
      libSuffix = ".a";
    }
    return "lib" + libname + libSuffix;
  }

  static Process exec(String[] cmd, String[] env, File dir) {
    String dirPath = (dir != null) ? dir.getPath() : null;
    return new VM_Process(cmd[0], cmd, env, dirPath);
  }

  /**
   * This is used by Runtime.addshutdownHook().
   *
   * TODO: I don't THINK there's anything we need to do for this, but we should
   * look it over more carefully.  Perhaps we want to add something so that we
   * will try to run the hooks in case of an abnormal  exit (such a
   * control-C)?  */
  static void enableShutdownHooks() {
  }
}
