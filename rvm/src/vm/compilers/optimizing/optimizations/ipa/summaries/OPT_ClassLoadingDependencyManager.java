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
   * put your documentation comment here
   * @param c
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
        JDK2_Iterator invalidatedMethods = 
            db.invalidatedByOverriddenMethod(overridden);
        if (invalidatedMethods != null) {
          while (invalidatedMethods.hasNext()) {
            int cmid = ((Integer)invalidatedMethods.next()).intValue();
            VM_CompiledMethod im = VM_ClassLoader.getCompiledMethod(cmid);
            invalidate(im);
          }
          db.removeNotOverriddenDependency(overridden);
        }
      }
    }
  }

  /**
   * put your documentation comment here
   * @param c
   */
  private void handleSubclassing (VM_Class c) {
    VM_Class sc = c.getSuperClass();
    if (sc == null)
      return;                   // c == java.lang.Object.
    JDK2_Iterator invalidatedMethods = db.invalidatedBySubclass(sc);
    if (invalidatedMethods != null) {
      while (invalidatedMethods.hasNext()) {
        int cmid = ((Integer)invalidatedMethods.next()).intValue();
        VM_CompiledMethod im = VM_ClassLoader.getCompiledMethod(cmid);
        invalidate(im);
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
      report("CLDM: Invalidating compiled method " + cm.getId() + "(" + 
          m + ")\n");
    // (1) Blow away information about this now invalid compiled 
    // method being held on the VM_Method
    m.clearMostRecentCompilation();
    // (2) Reset all jtoc or TIB entries that point to the 
    // now invalid compiled method.
    // TODO: replace this with a call to resetMethod(cm)
    if (m.isStatic() || m.isObjectInitializer() || m.isClassInitializer()) {
      // invalidate jtoc slot.
      m.getDeclaringClass().resetStaticMethod(cm);
      if (DEBUG)
        report("\tReset jtoc slot\n");
    } else {
      // invalidate TIB entry
      Stack s = new Stack();
      s.push(m.getDeclaringClass());
      while (!s.isEmpty()) {
        VM_Class c = (VM_Class)s.pop();
        if (DEBUG)
          report("\tConsidering " + c + "\n");
        if (c.isResolved()) {
          c.resetTIBEntry(cm);
          if (DEBUG)
            report("\tReset tib entry for " + c + "\n");
          VM_Class[] subClasses = c.getSubClasses();
          for (int i = 0; i < subClasses.length; i++) {
            s.push(subClasses[i]);
          }
        }
      }
    }
  }
  static final boolean DEBUG = false;
  static final boolean TRACE = false;

  /**
   * put your documentation comment here
   * @param s
   */
  void report (String s) {
    log.print(s);
  }
  private OPT_InvalidationDatabase db = new OPT_InvalidationDatabase();
  private static PrintStream log;
}



