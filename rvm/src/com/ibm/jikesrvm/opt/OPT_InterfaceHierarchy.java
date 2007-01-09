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

import com.ibm.jikesrvm.util.*;
import com.ibm.jikesrvm.classloader.*;

/**
 * This class holds, for each interface, the set of initialized classes
 * that implement the interface.
 *
 * @author Stephen Fink
 */
public class OPT_InterfaceHierarchy {

  /**
   * a mapping from VM_Class (an interface) to a set of classes that
   * claim to implement this interface.
   */
  private static VM_HashMap<VM_Class,VM_HashSet<VM_Class>> interfaceMapping = 
    new VM_HashMap<VM_Class,VM_HashSet<VM_Class>>();

  /**
   * Notify this dictionary that a new class has been initialized. 
   * This method updates the dictionary to record the interface
   * implementors.
   */
  public static synchronized void notifyClassInitialized(VM_Class c) {
    if (!c.isInterface()) {
      VM_Class[] interfaces = c.getAllImplementedInterfaces();
      for (int i=0; i<interfaces.length; i++) {
        noteImplements(c, interfaces[i]);
      }
    }
  }

  /**
   * Note that class c implements interface I;
   */
  private static void noteImplements(VM_Class c, VM_Class I) {
    VM_HashSet<VM_Class> implementsSet = findOrCreateSet(I);
    implementsSet.add(c);
  }

  /**
   * Return the set of classes that implement a given interface. Create a
   * set if none found.
   */
  private static synchronized VM_HashSet<VM_Class> findOrCreateSet(VM_Class I) {
    VM_HashSet<VM_Class> set = interfaceMapping.get(I);
    if (set == null) {
      set = new VM_HashSet<VM_Class>(3);
      interfaceMapping.put(I,set);
    }
    return set;
  }

  /**
   * Return the set of all classes known to implement interface I.
   */
  private static VM_HashSet<VM_Class> allImplementors(VM_Class I) {
    // get the set of classes registered as implementing I
    VM_HashSet<VM_Class> result = findOrCreateSet(I);
    
    // also add any classes that implement a sub-interface of I.
    VM_Class[] subI = I.getSubClasses();
    // need to do this kludge to avoid recursive concurrent modification
    for (int i=0; i<subI.length; i++) {
      result.addAll(allImplementors(subI[i]));
    }

    // also add any sub-classes of these classes.
    // need to cache additions to avoid modifying the set while iterating
    VM_HashSet<VM_Class> toAdd = new VM_HashSet<VM_Class>(5);
    for (VM_Class c : result) {
      toAdd.addAll(allSubClasses(c));
    }
    result.addAll(toAdd);

    return result;
  }

  /**
   * Return the set of all classes known to extend C
   */
  private static VM_HashSet<VM_Class> allSubClasses(VM_Class C) {
    VM_HashSet<VM_Class> result = new VM_HashSet<VM_Class>(5);
    
    // also add any classes that implement a sub-interface of I.
    VM_Class[] subC = C.getSubClasses();
    for (int i=0; i<subC.length; i++) {
      result.add(subC[i]);
      result.addAll(allSubClasses(subC[i]));
    }

    return result;
  }

  /**
   * If, in the current class hierarchy, there is exactly one method that
   * defines the interface method foo, then return the unique
   * implementation.  If there is not a unique implementation, return
   * null.
   */
  public static synchronized VM_Method getUniqueImplementation(VM_Method foo) {
    VM_Class I = foo.getDeclaringClass();

    VM_HashSet<VM_Class> classes = allImplementors(I);
    VM_Method firstMethod = null;
    VM_Atom name = foo.getName();
    VM_Atom desc = foo.getDescriptor();

    for (VM_Class klass : classes) {
      VM_Method m = klass.findDeclaredMethod(name,desc);
      if (firstMethod == null) 
        firstMethod = m;

      if (m != firstMethod)
        return null;
    }
    return firstMethod;
  }
}
