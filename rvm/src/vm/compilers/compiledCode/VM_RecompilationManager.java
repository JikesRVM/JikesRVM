/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
//-#if RVM_WITH_ADAPTIVE_SYSTEM
import com.ibm.JikesRVM.adaptive.VM_RuntimeMeasurements;
//-#endif

/**
 * This class enables an external driver to block recompile all
 * methods that have been dynamically compiled since the VM began
 * execution.  This support can be used to eliminate the effects
 * of early vs. late compilation by removing all dynamic linking
 * and "bad" class hierarchy based optimizations.
 * <p>
 * @author Dave Grove
 */
public final class VM_RecompilationManager {

  private static final boolean DEBUG = false;

  /**
   * Use the runtime compiler to forcibly recompile all dynamically
   * loaded methods
   */ 
  public static void recompileAllDynamicallyLoadedMethods(boolean report) {
    int numMethods = VM_CompiledMethods.numCompiledMethods();
    // To avoid the assertion for unused cmids
    VM_CompiledMethod[] compiledMethods = VM_CompiledMethods.getCompiledMethods();
    for (int cmid=1; cmid<numMethods; cmid++) {
      VM_CompiledMethod cpMeth = compiledMethods[cmid];
      if (cpMeth == null) {
        if (DEBUG) VM.sysWrite("Not recompiling method ID "+cmid+
                               " because it has no compiledMethod\n");
      } else {
        VM_Method meth = cpMeth.getMethod();
        if (meth.getDeclaringClass().isResolved()) {
          if (meth.getDeclaringClass().isInBootImage()) {
            if (DEBUG) VM.sysWrite("Not recompiling bootimage method "+meth+
                                   "("+cmid+")\n");
          } else {
            if (meth.isAbstract()) {
              if (DEBUG) VM.sysWrite("Not recompiling abstract method "+meth+"("+cmid+")\n");
            } else if (meth.isNative()) {
              if (DEBUG) VM.sysWrite("Not recompiling native method "+meth+"("+cmid+")\n");
            } else {
              if (DEBUG||report) VM.sysWrite("Recompiling "+meth+"("+cmid+") ");
              recompile((VM_NormalMethod)meth);
              if (DEBUG||report) VM.sysWrite("...done\n");
            }
          }
        } else {
          if (DEBUG) VM.sysWrite("Class not resolved"+meth+"("+cmid+")\n");
        }
      }
    }

    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    // clear profiling counter
    if (DEBUG||report) { VM.sysWrite("Reseting profiling information\n"); }
    VM_RuntimeMeasurements.resetReportableObjects();
    //-#endif

  }

  /**
   * recompile and replace the argument method by invoking the runtime compiler
   */
  public static void recompile(VM_NormalMethod meth) {
    try {
      VM_CompiledMethod cm = VM_RuntimeCompiler.compile(meth);
      meth.replaceCompiledMethod(cm);
    } catch (Throwable e) {
      VM.sysWrite("Failure while recompiling \""+meth+"\" : "+e+"\n");
    }
  }
}
 
