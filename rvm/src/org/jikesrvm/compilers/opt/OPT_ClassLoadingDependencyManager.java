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
package org.jikesrvm.compilers.opt;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Iterator;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_ClassLoadingListener;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;

/**
 * This class acts as an intermediary between VM_ClassLoader and the
 * optimizing compiler's dependency database.  Just before a class
 * is marked as INITIALIZED, VM_Class.initialize() invokes
 * OPT_ClassLoadingDependencyManager.classInitialized(), which is responsible
 * for identifying and performing all necessary invalidations of
 * opt compiler code.
 */
public final class OPT_ClassLoadingDependencyManager implements VM_ClassLoadingListener {

  ////////////////////////
  // Entrypoints from VM_Class
  ////////////////////////
  public synchronized void classInitialized(VM_Class c, boolean writingBootImage) {
    // Process any dependencies on methods not being overridden.
    if (!writingBootImage) {
      if (DEBUG) {
        report("CLDM: " + c + " is about to be marked as initialized.\n");
      }
      handleOverriddenMethods(c);
      handleSubclassing(c);
    }
    OPT_InterfaceHierarchy.notifyClassInitialized(c);
  }

  /////////////////////////
  // Entrypoints for the opt compiler to record dependencies
  /////////////////////////

  /**
   * Record that the code currently being compiled (cm) must be
   * invalidated if source is overridden.
   */
  public synchronized void addNotOverriddenDependency(VM_Method source, VM_CompiledMethod cm) {
    int cmid = cm.getId();
    if (TRACE || DEBUG) {
      report("CLDM: " + cmid + "(" + cm.getMethod() + ") is dependent on " + source + " not being overridden\n");
    }
    db.addNotOverriddenDependency(source, cmid);
  }

  /**
   * Record that the code currently being compiled (cm) must be
   * invalidated if source ever has a subclass.
   */
  public synchronized void addNoSubclassDependency(VM_Class source, VM_CompiledMethod cm) {
    int cmid = cm.getId();
    if (TRACE || DEBUG) {
      report("CLDM: " + cmid + "(" + cm.getMethod() + ") is dependent on " + source + " not having a subclass\n");
    }
    db.addNoSubclassDependency(source, cmid);
  }

  ////////////////////////
  // Implementation
  ////////////////////////

  /**
   * Take action when a method is overridden.
   * @param c a class that has just been loaded.
   */
  private void handleOverriddenMethods(VM_Class c) {
    if (c.isJavaLangObjectType() || c.isInterface()) return; // nothing to do.
    VM_Class sc = c.getSuperClass();
    // for each virtual method of sc, if it is overriden by
    // a virtual method declared by c, then handle any required invalidations.
    VM_Method[] sc_methods = sc.getVirtualMethods();
    VM_Method[] c_methods = c.getVirtualMethods();
    for (int i = 0; i < sc_methods.length; i++) {
      if (sc_methods[i] != c_methods[i]) {
        processOverride(sc_methods[i]);
      }
    }
    // for each interface implmented by c, note that c provides an overridding
    // implementation
    for (VM_Class intf : c.getAllImplementedInterfaces()) {
      for (VM_Method m : intf.getVirtualMethods()) {
        processOverride(m);
      }
    }
  }

  private void processOverride(VM_Method overridden) {
    Iterator<Integer> invalidatedMethods = db.invalidatedByOverriddenMethod(overridden);
    if (invalidatedMethods != null) {
      while (invalidatedMethods.hasNext()) {
        int cmid = invalidatedMethods.next();
        VM_CompiledMethod im = VM_CompiledMethods.getCompiledMethod(cmid);
        if (im != null) { // im == null implies that the code has been GCed already
          invalidate(im);
        }
      }
      db.removeNotOverriddenDependency(overridden);
    }
  }

  private void handleSubclassing(VM_Class c) {
    if (c.isJavaLangObjectType() || c.isInterface()) return; // nothing to do
    VM_Class sc = c.getSuperClass();
    Iterator<Integer> invalidatedMethods = db.invalidatedBySubclass(sc);
    if (invalidatedMethods != null) {
      while (invalidatedMethods.hasNext()) {
        int cmid = invalidatedMethods.next();
        VM_CompiledMethod im = VM_CompiledMethods.getCompiledMethod(cmid);
        if (im != null) { // im == null implies that the code has been GCed already
          invalidate(im);
        }
      }
      db.removeNoSubclassDependency(sc);
    }
  }

  /**
   * helper method to invalidate a particular compiled method
   */
  private void invalidate(VM_CompiledMethod cm) {
    VM_Method m = cm.getMethod();
    if (TRACE || DEBUG) {
      report("CLDM: Invalidating compiled method " + cm.getId() + "(" + m + ")\n");
    }

    // (1) Mark the compiled method as invalid.
    synchronized (cm) {
      if (cm.isInvalid()) {
        if (TRACE || DEBUG) report("\tcmid was alrady invalid; nothing more to do\n");
        return;
      }

      // (2) Apply any code patches to protect invocations already executing
      //     in the soon to be invalid code.
      ((VM_OptCompiledMethod) cm).applyCodePatches(cm);

      cm.setInvalid();
    }

    // (3) Inform its VM_Method that cm is invalid;
    //     This will update all the dispatching entries (TIB, JTOC, IMTs)
    //     so that no new invocations will reach the invalid compiled code.
    //     It also marks cm as obsolete so it can eventually be reclaimed by GC.
    m.invalidateCompiledMethod(cm);
  }

  static final boolean DEBUG = false;
  static final boolean TRACE = false;

  void report(String s) {
    if (VM.runningVM) {
      if (log == null) {
        if (true || !VM.fullyBooted) {
          VM.sysWriteln("CLDM: VM not fully booted ", s);
          return;
        }
        try {
          log = new PrintStream(new FileOutputStream("PREEX_OPTS.TRACE"));
        } catch (IOException e) {
          VM.sysWrite("\n\nCLDM: Error opening logging file!!\n\n");
        }
      }
    } else {
      System.out.print(s);
    }
  }

  private OPT_InvalidationDatabase db = new OPT_InvalidationDatabase();
  private static PrintStream log;
}
