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
package org.jikesrvm.compilers.common;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.measurements.RuntimeMeasurements;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;

/**
 * This class enables an external driver to block recompile all
 * methods that have been dynamically compiled since the VM began
 * execution.  This support can be used to eliminate the effects
 * of early vs. late compilation by removing all dynamic linking
 * and "bad" class hierarchy based optimizations.
 */
public final class RecompilationManager {

  private static final boolean DEBUG = false;

  /**
   * Use the runtime compiler to forcibly recompile all dynamically
   * loaded methods
   */
  public static void recompileAllDynamicallyLoadedMethods(boolean report) {
    int numMethods = CompiledMethods.numCompiledMethods();
    for (int cmid = 1; cmid < numMethods; cmid++) {
      // To avoid the assertion for unused cmids
      CompiledMethod cpMeth = CompiledMethods.getCompiledMethodUnchecked(cmid);
      if (cpMeth == null) {
        if (DEBUG) {
          VM.sysWrite("Not recompiling method ID ", cmid, " because it has no compiledMethod\n");
        }
      } else {
        RVMMethod meth = cpMeth.getMethod();
        if (DEBUG) {
          VM.sysWrite("numMethods: " +
                      numMethods +
                      ", Inspecting cpMethod " +
                      cpMeth +
                      ", method: " +
                      cpMeth.getMethod() +
                      "(" +
                      cmid +
                      ")\n");
        }
        if (cpMeth.getCompilerType() == CompiledMethod.TRAP) {
          if (DEBUG) {
            VM.sysWrite("Not recompiling compiled method " +
                        cpMeth +
                        "(" +
                        cmid +
                        ") because it a TRAP, i.e. has no source code\n");
          }
        } else {
          if (meth.getDeclaringClass().isResolved()) {
            if (meth.getDeclaringClass().isInBootImage()) {
              if (DEBUG) {
                VM.sysWrite("Not recompiling bootimage method " + meth + "(" + cmid + ")\n");
              }
            } else {
              if (meth.isAbstract()) {
                if (DEBUG) VM.sysWrite("Not recompiling abstract method " + meth + "(" + cmid + ")\n");
              } else if (meth.isNative()) {
                if (DEBUG) VM.sysWrite("Not recompiling native method " + meth + "(" + cmid + ")\n");
              } else {
                if (DEBUG || report) VM.sysWrite("Recompiling " + meth + "(" + cmid + ") ");
                recompile((NormalMethod) meth);
                if (DEBUG || report) VM.sysWrite("...done\n");
              }
            }
          } else {
            if (DEBUG) VM.sysWrite("Class not resolved" + meth + "(" + cmid + ")\n");
          }
        }
      }
    }

    if (VM.BuildForAdaptiveSystem) {
      // clear profiling counter
      if (DEBUG || report) { VM.sysWrite("Reseting profiling information\n"); }
      RuntimeMeasurements.resetReportableObjects();
    }
  }

  /**
   * recompile and replace the argument method by invoking the runtime compiler
   */
  public static void recompile(NormalMethod meth) {
    try {
      CompiledMethod cm = RuntimeCompiler.compile(meth);
      meth.replaceCompiledMethod(cm);
    } catch (Throwable e) {
      VM.sysWrite("Failure while recompiling \"" + meth + "\" : " + e + "\n");
    }
  }
}

