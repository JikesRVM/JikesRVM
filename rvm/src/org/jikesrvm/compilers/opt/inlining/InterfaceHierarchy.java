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

import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.util.ImmutableEntryHashMapRVM;
import org.jikesrvm.util.ImmutableEntryHashSetRVM;

/**
 * This class holds, for each interface, the set of initialized classes
 * that implement the interface.
 */
public class InterfaceHierarchy {

  /**
   * a mapping from RVMClass (an interface) to a set of classes that
   * claim to implement this interface.
   */
  private static final ImmutableEntryHashMapRVM<RVMClass, ImmutableEntryHashSetRVM<RVMClass>> interfaceMapping =
      new ImmutableEntryHashMapRVM<RVMClass, ImmutableEntryHashSetRVM<RVMClass>>();

  /**
   * Notify this dictionary that a new class has been initialized.
   * This method updates the dictionary to record the interface
   * implementors.
   */
  public static synchronized void notifyClassInitialized(RVMClass c) {
    if (!c.isInterface()) {
      for (RVMClass intf : c.getAllImplementedInterfaces()) {
        noteImplements(c, intf);
      }
    }
  }

  /**
   * Note that class c implements interface I;
   */
  private static void noteImplements(RVMClass c, RVMClass I) {
    ImmutableEntryHashSetRVM<RVMClass> implementsSet = findOrCreateSet(I);
    implementsSet.add(c);
  }

  /**
   * Return the set of classes that implement a given interface. Create a
   * set if none found.
   */
  private static synchronized ImmutableEntryHashSetRVM<RVMClass> findOrCreateSet(RVMClass I) {
    ImmutableEntryHashSetRVM<RVMClass> set = interfaceMapping.get(I);
    if (set == null) {
      set = new ImmutableEntryHashSetRVM<RVMClass>(3);
      interfaceMapping.put(I, set);
    }
    return set;
  }

  /**
   * Return the set of all classes known to implement interface I.
   */
  private static ImmutableEntryHashSetRVM<RVMClass> allImplementors(RVMClass I) {
    // get the set of classes registered as implementing I
    ImmutableEntryHashSetRVM<RVMClass> result = findOrCreateSet(I);

    // also add any classes that implement a sub-interface of I.
    // need to do this kludge to avoid recursive concurrent modification
    for (RVMClass subClass : I.getSubClasses()) {
      result.addAll(allImplementors(subClass));
    }

    // also add any sub-classes of these classes.
    // need to cache additions to avoid modifying the set while iterating
    ImmutableEntryHashSetRVM<RVMClass> toAdd = new ImmutableEntryHashSetRVM<RVMClass>(5);
    for (RVMClass c : result) {
      toAdd.addAll(allSubClasses(c));
    }
    result.addAll(toAdd);

    return result;
  }

  /**
   * Return the set of all classes known to extend C
   */
  private static ImmutableEntryHashSetRVM<RVMClass> allSubClasses(RVMClass C) {
    ImmutableEntryHashSetRVM<RVMClass> result = new ImmutableEntryHashSetRVM<RVMClass>(5);

    // also add any classes that implement a sub-interface of I.
    for (RVMClass subClass : C.getSubClasses()) {
      result.add(subClass);
      result.addAll(allSubClasses(subClass));
    }

    return result;
  }

  /**
   * If, in the current class hierarchy, there is exactly one method that
   * defines the interface method foo, then return the unique
   * implementation.  If there is not a unique implementation, return
   * null.
   */
  public static synchronized RVMMethod getUniqueImplementation(RVMMethod foo) {
    RVMClass I = foo.getDeclaringClass();

    ImmutableEntryHashSetRVM<RVMClass> classes = allImplementors(I);
    RVMMethod firstMethod = null;
    Atom name = foo.getName();
    Atom desc = foo.getDescriptor();

    for (RVMClass klass : classes) {
      RVMMethod m = klass.findDeclaredMethod(name, desc);
      if (firstMethod == null) {
        firstMethod = m;
      }

      if (m != firstMethod) {
        return null;
      }
    }
    return firstMethod;
  }
}
