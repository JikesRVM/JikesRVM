/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
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
final class OPT_ClassLoadingDependencyManager {

  ////////////////////////
  // Entrypoints from VM_Class
  ////////////////////////
  public void classInitialized (VM_Class c) {
    // Process any dependencies on methods not being overridden.
    if (DEBUG)
      report("CLDM: " + c + " is about to be marked as initialized.\n");
    handleOverriddenMethods(c);
    handleSubclassing(c);
    OPT_InterfaceHierarchy.notifyClassInitialized(c);
    VM_OptStaticProgramStats.newClass();
  }

  /////////////////////////
  // Entrypoints for the opt compiler to record dependencies
  /////////////////////////
  /** 
   * Record that the code currently being compiled (cmid) must be 
   * invalidated if source is overridden.
   */
  public void addNotOverriddenDependency (VM_Method source, int cmid) {
    if (TRACE || DEBUG)
      report("CLDM: " + cmid + " is dependent on " + source + 
             " not being overridden\n");
    db.addNotOverriddenDependency(source, cmid);
  }

  /**
   * Record that the code currently being compiled (cmid) must be 
   * invalidated if source ever has a subclass.
   */
  public void addNoSubclassDependency (VM_Class source, int cmid) {
    if (TRACE || DEBUG)
      report("CLDM: " + cmid + " is dependent on " + source + 
          " not having a subclass\n");
    db.addNoSubclassDependency(source, cmid);
  }

  ////////////////////////
  // Implementation
  ////////////////////////
  OPT_ClassLoadingDependencyManager () {
    if (TRACE || DEBUG) {
      try {
        log = new PrintStream(new FileOutputStream("PREEX_OPTS.TRACE"));
      } catch (IOException e) {
        VM.sysWrite("\n\nCLDM: Error opening logging file!!\n\n");
      }
    }
  }

  /**
   * Take action when a method is overridden.
   * @param c a class that has just been loaded.
   */
  private void handleOverriddenMethods (VM_Class c) {
    VM_Class sc = c.getSuperClass();
    if (sc == null)
      return;                   // c == java.lang.Object.
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

  private void handleSubclassing (VM_Class c) {
    VM_Class sc = c.getSuperClass();
    if (sc == null)
      return;                   // c == java.lang.Object.
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
  private void invalidate (VM_CompiledMethod cm) {
    VM_Method m = cm.getMethod();
    if (TRACE || DEBUG)
      report("CLDM: Invalidating compiled method " + cm.getId() + "(" + m + ")\n");
    // (1) Blow away information about this now invalid compiled 
    //     method being held on the VM_Method
    m.clearMostRecentCompilation();

    // (2) Reset all jtoc or TIB entries that point to the 
    //     now invalid compiled method.
    m.getDeclaringClass().resetMethod(cm);

    //-#if RVM_FOR_IA32
    // (3) Apply any code patches
    VM_OptCompilerInfo info = (VM_OptCompilerInfo)cm.getCompilerInfo();
    info.applyCodePatches(cm);
    //-#endif
  }

  static final boolean DEBUG = false;
  static final boolean TRACE = false;

  void report (String s) {
    log.print(s);
  }
  private OPT_InvalidationDatabase db = new OPT_InvalidationDatabase();
  private static PrintStream log;
}



