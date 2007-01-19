/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt;

import com.ibm.jikesrvm.classloader.*;
import com.ibm.jikesrvm.util.*;

import java.util.Iterator;

/**
 * Database to store multiple specialized versions for a given method.
 *
 * <p> The overall design is very similar to that of the
 * InvalidationDatabase (see OPT_InvalidationDatabase.java)
 * In this database, the key is the VM_Method object of the source method 
 * and the value is a method set. The method set is a list of
 * specialized versions of the method pointed by the key. Specialized
 * versions are represented by using the OPT_SpecializedMethod class.
 * There is no provision for removing/deleting method versions as classes 
 * are never unloaded and the ClassLoader.compiledMethods[] is never cleaned.
 *
 * @author Rajesh Bordawekar
 * @author Stephen Fink
 */
public final class OPT_SpecializationDatabase {

  /**
   * Drain the queue of methods waiting for specialized code
   * generation.
   */
  static synchronized void doDeferredSpecializations() {
    // prevent recursive entry to this method
    if (specializationInProgress)
      return;
    specializationInProgress = true;
    Iterator<OPT_SpecializedMethod> methods = deferredMethods.iterator();
    while (methods.hasNext()) {
      OPT_SpecializedMethod m = methods.next();
      if (m.getCompiledMethod() == null) {
        m.compile();
        registerCompiledMethod(m);
      }
      deferredMethods.remove(m);
      // since we modified the set, reset the iterator.  
      // TODO: use a better abstraction 
      // (ModifiableSetIterator of some kind?)
      methods = deferredMethods.iterator();
    }
    specializationInProgress = false;
  }
  private static boolean specializationInProgress;
  private static VM_HashSet<OPT_SpecializedMethod> deferredMethods = 
    new VM_HashSet<OPT_SpecializedMethod>();

  // write the new compiled method in the specialized method pool
  private static void registerCompiledMethod(OPT_SpecializedMethod m) {
    OPT_SpecializedMethodPool.registerCompiledMethod(m);
  }

  /**
   * Return an iteration of OPT_SpecializedMethods that represents
   * specialied compiled versions of the method pointed by VM_Method
   * @return null if no specialized versions
   */
  static synchronized Iterator<OPT_SpecializedMethod> getSpecialVersions(VM_Method m) {
    MethodSet s = (MethodSet)specialVersionsHash.get(m);
    if (s == null) {
      return  null;
    } 
    else {
      return  s.iterator();
    }
  }

  static int getSpecialVersionCount(VM_Method m) {
    Iterator<OPT_SpecializedMethod> versions = getSpecialVersions(m);
    int count = 0;
    if (versions != null) {
      while (versions.hasNext() && (versions.next() != null)) {
        count++;
      }
    }
    return  count;
  }

  /**
   * Record a new specialized method in this database.
   * Also remember that this method will need to be compiled later,
   * at the next call to <code> doDeferredSpecializations() </code>
   */
  static synchronized  void registerSpecialVersion(OPT_SpecializedMethod spMethod) {
    VM_Method source = spMethod.getMethod();
    MethodSet s = findOrCreateMethodSet(specialVersionsHash, source);
    s.add(spMethod);
    deferredMethods.add(spMethod);
  }
  private static VM_HashMap<VM_Method,MethodSet> specialVersionsHash = 
    new VM_HashMap<VM_Method,MethodSet>();

  /**
   * Look up the MethodSet corresponding to a given key in the database
   * If none found, create one.
   */
  private static <T> MethodSet findOrCreateMethodSet(VM_HashMap<T,MethodSet> hash, T key) {
    MethodSet result = hash.get(key);
    if (result == null) {
      result = new MethodSet(key);
      hash.put(key, result);
    }
    return  result;
  }

  /**
   * The following defines a set of methods that share a common "key"
   */
  static class MethodSet {
    Object key;

    /**
     * a set of OPT_SpecializedMethod
     */
    VM_HashSet<OPT_SpecializedMethod> methods = new VM_HashSet<OPT_SpecializedMethod>();

    MethodSet(Object key) {
      this.key = key;
    }

    void add(OPT_SpecializedMethod spMethod) {
      methods.add(spMethod);
    }

    public Iterator<OPT_SpecializedMethod> iterator() {
      return  methods.iterator();
    }
  }
}



