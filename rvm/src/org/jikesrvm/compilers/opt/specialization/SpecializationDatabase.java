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
package org.jikesrvm.compilers.opt.specialization;

import java.util.Iterator;

import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.util.ImmutableEntryHashMapRVM;
import org.jikesrvm.util.HashSetRVM;

/**
 * Database to store multiple specialized versions for a given method.
 *
 * <p> The overall design is very similar to that of the
 * InvalidationDatabase (see InvalidationDatabase.java)
 * In this database, the key is the RVMMethod object of the source method
 * and the value is a method set. The method set is a list of
 * specialized versions of the method pointed by the key. Specialized
 * versions are represented by using the SpecializedMethod class.
 * There is no provision for removing/deleting method versions as classes
 * are never unloaded and the ClassLoader.compiledMethods[] is never cleaned.
 */
public final class SpecializationDatabase {

  private static boolean specializationInProgress;

  private static final HashSetRVM<SpecializedMethod> deferredMethods =
    new HashSetRVM<SpecializedMethod>();

  private static final ImmutableEntryHashMapRVM<RVMMethod, MethodSet<RVMMethod>> specialVersionsHash =
      new ImmutableEntryHashMapRVM<RVMMethod, MethodSet<RVMMethod>>();

  /**
   * Drain the queue of methods waiting for specialized code
   * generation.
   */
  public static synchronized void doDeferredSpecializations() {
    // prevent recursive entry to this method
    if (specializationInProgress) {
      return;
    }
    specializationInProgress = true;
    Iterator<SpecializedMethod> methods = deferredMethods.iterator();
    while (methods.hasNext()) {
      SpecializedMethod m = methods.next();
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

  // write the new compiled method in the specialized method pool
  private static void registerCompiledMethod(SpecializedMethod m) {
    SpecializedMethodPool.registerCompiledMethod(m);
  }

  /**
   * Return an iteration of SpecializedMethods that represents
   * specialized compiled versions of the method pointed by RVMMethod
   * @return null if no specialized versions
   */
  static synchronized Iterator<SpecializedMethod> getSpecialVersions(RVMMethod m) {
    MethodSet<RVMMethod> s = specialVersionsHash.get(m);
    if (s == null) {
      return null;
    } else {
      return s.iterator();
    }
  }

  static int getSpecialVersionCount(RVMMethod m) {
    Iterator<SpecializedMethod> versions = getSpecialVersions(m);
    int count = 0;
    if (versions != null) {
      while (versions.hasNext() && (versions.next() != null)) {
        count++;
      }
    }
    return count;
  }

  /**
   * Record a new specialized method in this database.
   * Also remember that this method will need to be compiled later,
   * at the next call to <code> doDeferredSpecializations() </code>
   */
  static synchronized void registerSpecialVersion(SpecializedMethod spMethod) {
    RVMMethod source = spMethod.getMethod();
    MethodSet<RVMMethod> s = findOrCreateMethodSet(specialVersionsHash, source);
    s.add(spMethod);
    deferredMethods.add(spMethod);
  }

  /**
   * Look up the MethodSet corresponding to a given key in the database
   * If none found, create one.
   */
  private static <T> MethodSet<T> findOrCreateMethodSet(ImmutableEntryHashMapRVM<T, MethodSet<T>> hash, T key) {
    MethodSet<T> result = hash.get(key);
    if (result == null) {
      result = new MethodSet<T>(key);
      hash.put(key, result);
    }
    return result;
  }

  /**
   * The following defines a set of methods that share a common "key"
   */
  static class MethodSet<T> {
    final T key;

    /**
     * a set of SpecializedMethod
     */
    final HashSetRVM<SpecializedMethod> methods = new HashSetRVM<SpecializedMethod>();

    MethodSet(T key) {
      this.key = key;
    }

    void add(SpecializedMethod spMethod) {
      methods.add(spMethod);
    }

    public Iterator<SpecializedMethod> iterator() {
      return methods.iterator();
    }
  }
}
