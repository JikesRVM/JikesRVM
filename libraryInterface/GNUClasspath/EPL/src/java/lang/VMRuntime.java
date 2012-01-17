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

import java.io.File;
import java.io.IOException;

import org.jikesrvm.*;
import org.jikesrvm.runtime.DynamicLibrary;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.mm.mminterface.*;

/**
 * Jikes RVM implementation of GNU Classpath's java.lang.VMRuntime.
 * See reference implementation for javadoc.
 */
public final class VMRuntime {

  private static boolean runFinalizersOnExit = false;

  private VMRuntime() { }

  static int availableProcessors() {
    return RVMThread.availableProcessors;
  }

  static long freeMemory() {
    return MemoryManager.freeMemory().toLong();
  }

  static long totalMemory() {
    return MemoryManager.totalMemory().toLong();
  }

  static long maxMemory() {
    return MemoryManager.maxMemory().toLong();
  }

  static void gc() {
    VMCommonLibrarySupport.gc();
  }

  static void runFinalization() {
    // TODO: talk to Steve B & Perry and figure out what to do.
    // as this is a hint, we can correctly ignore it.
    // However, there might be something else we should do.
  }

  static void runFinalizationForExit() {
    if (runFinalizersOnExit) {
      // TODO: talk to Steve B & Perry and figure out what to do.
      throw new UnimplementedError();
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
    return DynamicLibrary.load(libName);
  }


  /**
   * Mangle a short-name to the file name (not the full pathname) for a
   * dynamically loadable library.
   */
  static String mapLibraryName(String libname) {
    return VMCommonLibrarySupport.mapLibraryName(libname);
  }

  static Process exec(String[] cmd, String[] env, File dir)
    throws IOException {
    return VMProcess.exec(cmd,env,dir);
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
