/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import  java.util.*;
import  java.io.*;

/**
 * This class acts as an intermediary between VM_ClassLoader and the 
 * optimizing compiler's dependency database.  Just before a class 
 * is marked as INITIALIZED, VM_Class.initialize() invokes
 * OPT_ClassLoadingDependencyManager.classInitialized(), which is responsible
 * for identifying and performing all necessary invalidations of 
 * opt compiler code.
 *
 * @author Steve Fink
 * @author Dave Grove
 */
public final class OPT_ClassLoadingDependencyManager {

  ////////////////////////
  // Entrypoints from VM_Class
  ////////////////////////
  public synchronized void classInitialized(VM_Class c) {
    // Process any dependencies on methods not being overridden.
    if (DEBUG)
      report("CLDM: " + c + " is about to be marked as initialized.\n");
    handleOverriddenMethods(c);
    handleSubclassing(c);
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
    if (TRACE || DEBUG)
      report("CLDM: " + cmid + "("+cm.getMethod()+") is dependent on " + source + " not being overridden\n");
    db.addNotOverriddenDependency(source, cmid);
  }

  /**
   * Record that the code currently being compiled (cm) must be 
   * invalidated if source ever has a subclass.
   */
  public synchronized void addNoSubclassDependency(VM_Class source, VM_CompiledMethod cm) {
    int cmid = cm.getId();
    if (TRACE || DEBUG)
      report("CLDM: " + cmid + "("+cm.getMethod()+") is dependent on " + source + " not having a subclass\n");
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
        VM_Method overridden = sc_methods[i];
        java.util.Iterator invalidatedMethods = 
            db.invalidatedByOverriddenMethod(overridden);
        if (invalidatedMethods != null) {
          while (invalidatedMethods.hasNext()) {
            int cmid = ((Integer)invalidatedMethods.next()).intValue();
            VM_CompiledMethod im = VM_CompiledMethods.getCompiledMethod(cmid);
	    if (im != null) { // im == null implies that the code has been GCed already
	      invalidate(im);
	    }
          }
          db.removeNotOverriddenDependency(overridden);
	}
      }
    }
  }

  private void handleSubclassing(VM_Class c) {
    if (c.isJavaLangObjectType() || c.isInterface()) return; // nothing to do
    VM_Class sc = c.getSuperClass();
    java.util.Iterator invalidatedMethods = db.invalidatedBySubclass(sc);
    if (invalidatedMethods != null) {
      while (invalidatedMethods.hasNext()) {
        int cmid = ((Integer)invalidatedMethods.next()).intValue();
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
    if (TRACE || DEBUG)
      report("CLDM: Invalidating compiled method " + cm.getId() + "(" + m + ")\n");

    // (1) Mark the compiled method as invalid.
    synchronized(cm) {
      if (cm.isInvalid()) {
	if (TRACE || DEBUG) report("\tcmid was alrady invalid; nothing more to do\n");
	return;
      }

      // (2) Apply any code patches to protect invocations already executing
      //     in the soon to be invalid code.
      ((VM_OptCompiledMethod)cm).applyCodePatches(cm);

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
	if (!VM.fullyBooted) {
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
