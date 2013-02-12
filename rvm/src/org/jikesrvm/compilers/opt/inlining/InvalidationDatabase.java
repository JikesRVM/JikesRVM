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
package org.jikesrvm.compilers.opt.inlining;

import java.util.Iterator;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.util.HashMapRVM;
import org.jikesrvm.util.HashSetRVM;

/**
 * This class holds the dependencies that define invalidation
 * requirements for the opt compiled methods.
 *
 * <p> Currently we only support 2 kinds of dependencies:
 *   The set of compiled method id's that depend on a RVMMethod
 *   not being overridden.
 *   The set of compiled method id's that depend on a RVMClass
 *   having no subclasses
 *
 * <p> Note we track by compiled method ids instead of pointers to
 *     compiled methods because we don't have weak pointers.
 *     We don't want the invalidaton database to keep code alive!
 *     This would be an ideal use of weak references if we had them.
 *
 * <p> TODO: In the future, we should think about implementing a general
 *       dependency mechanism.
 *   See Chambers, Dean, Grove in ICSE-17 (1995) for one possible design
 *   and pointers to related work.
 */
public final class InvalidationDatabase {

  /**
   * A mapping from RVMMethod to MethodSet: holds the set of methods which
   * depend on a particular method being "final"
   */
  private final HashMapRVM<RVMMethod, MethodSet> nonOverriddenHash =
    new HashMapRVM<RVMMethod, MethodSet>();

  /**
   * A mapping from RVMClass to MethodSet: holds the set of methods which
   * depend on a particular class being "final"
   */
  private final HashMapRVM<RVMClass, MethodSet> noSubclassHash =
    new HashMapRVM<RVMClass, MethodSet>();

  /////////////////////
  // (1) Dependency on a particular RVMMethod not being overridden.
  /////////////////////

  /**
   * Returns an iteration of CMID's (compiled method ids) that are dependent
   * on the argument RVMMethod not being overridden. If there are no dependent
   * methods, {@code null} will be returned.<p>
   *
   * NOTE: {@code null} is used instead of {@code EmptyIterator.getInstance}
   * as part of delicate dance to avoid recursive classloading.
   *
   * @param m a method that can be overridden
   * @return an iterator of CMIDs or {@code null}
   */
  public Iterator<Integer> invalidatedByOverriddenMethod(RVMMethod m) {
    MethodSet s = nonOverriddenHash.get(m);
    return (s == null) ? null : s.iterator();
  }

  /**
   * Record that if a particular RVMMethod method is ever overridden, then
   * the CompiledMethod encoded by the cmid must be invalidated.
   */
  public void addNotOverriddenDependency(RVMMethod source, int dependent_cmid) {
    MethodSet s = findOrCreateMethodSet(nonOverriddenHash, source);
    s.add(dependent_cmid);
  }

  /**
   * Delete a NotOverriddenDependency.
   * No effect if the dependency doesn't exist..
   */
  public void removeNotOverriddenDependency(RVMMethod source, int dependent_cmid) {
    MethodSet s = nonOverriddenHash.get(source);
    if (s != null) {
      s.remove(dependent_cmid);
    }
  }

  /**
   * Delete all NotOverridden dependencies on the argument RVMMethod
   */
  public void removeNotOverriddenDependency(RVMMethod source) {
    nonOverriddenHash.remove(source);
  }

  /////////////////////
  // (2) Dependency on a particular RVMClass not having any subclasses.
  /////////////////////

  /**
   * Returns an iteration of CMID's (compiled method ids) that are dependent
   * on the argument RVMMethod not having any subclasses. If there are no
   * dependent methods, {@code null} will be returned.<p>
   *
   * NOTE: {@code null} is used instead of {@code EmptyIterator.getInstance}
   * as part of delicate dance to avoid recursive classloading.
   *
   * @param m a method that can be overridden
   * @return an iterator of CMIDs or {@code null}
   */
  public Iterator<Integer> invalidatedBySubclass(RVMClass m) {
    MethodSet s = noSubclassHash.get(m);
    return (s == null) ? null : s.iterator();
  }

  /**
   * Record that if a particular RVMClass ever has a subclass, then
   * the CompiledMethod encoded by the cmid must be invalidated.
   */
  public void addNoSubclassDependency(RVMClass source, int dependent_cmid) {
    MethodSet s = findOrCreateMethodSet(noSubclassHash, source);
    s.add(dependent_cmid);
  }

  /**
   * Delete a NoSubclassDependency. No effect if the dependency doesn't exist..
   */
  public void removeNoSubclassDependency(RVMClass source, int dependent_cmid) {
    MethodSet s = noSubclassHash.get(source);
    if (s != null) {
      s.remove(dependent_cmid);
    }
  }

  /**
   * Delete all NoSubclass dependencies on the argument RVMClass
   */
  public void removeNoSubclassDependency(RVMClass source) {
    noSubclassHash.remove(source);
  }

  /**
   * Look up the MethodSet corresponding to a given key in the database.
   * If none found, create one.
   */
  private <T> MethodSet findOrCreateMethodSet(HashMapRVM<T, MethodSet> hash, T key) {
    MethodSet result = hash.get(key);
    if (result == null) {
      result = new MethodSet(key);
      hash.put(key, result);
    }
    return result;
  }

  /**
   * The following defines a set of methods that share a common "key"
   */
  static final class MethodSet {
    final Object key;
    /**
     * a set of cmids (Integers)
     */
    final HashSetRVM<Integer> methods = new HashSetRVM<Integer>();

    MethodSet(Object key) {
      this.key = key;
    }

    void add(int cmid) {
      methods.add(cmid);
    }

    void remove(int cmid) {
      methods.remove(cmid);
    }

    public Iterator<Integer> iterator() {
      return methods.iterator();
    }
  }
}
