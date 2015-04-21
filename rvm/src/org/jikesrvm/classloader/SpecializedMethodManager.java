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
package org.jikesrvm.classloader;

import org.jikesrvm.mm.mminterface.MemoryManager;

import org.jikesrvm.VM;

/**
 * The manager of specialized methods.
 */
public final class SpecializedMethodManager {
  /** The number of specialized methods. Currently the MM is the only consumer. */
  private static final int numSpecializedMethods = MemoryManager.numSpecializedMethods();

  /** All the specialized methods */
  private static final SpecializedMethod[] methods = new SpecializedMethod[numSpecializedMethods];

  /** @return the number of specialized methods */
  public static int numSpecializedMethods() {
    return numSpecializedMethods;
  }

  /**
   * Sets up the specialized methods for the given type.
   * @param type the type that was instantiated
   */
  public static void notifyTypeInstantiated(RVMType type) {
    for (int i = 0; i < numSpecializedMethods; i++) {
      if (methods[i] == null) {
        initializeSpecializedMethod(i);
      }
      type.setSpecializedMethod(i, methods[i].specializeMethod(type));
    }
  }

  /**
   * Refreshes the specialized methods for the given type.
   * @param type the type whose methods need to be refreshed
   */
  public static void refreshSpecializedMethods(RVMType type) {
    for (int i = 0; i < numSpecializedMethods; i++) {
      if (VM.VerifyAssertions) VM._assert(methods[i] != null, "Specialized method missing!");
      type.setSpecializedMethod(i, methods[i].specializeMethod(type));
    }
  }

  /**
   * Initializes a specialized method with a given id. No specialized
   * method with this id may exist at this point.
   * @param id id of the specialized
   */
  private static void initializeSpecializedMethod(int id) {
    if (VM.VerifyAssertions) VM._assert(id >= 0);
    if (VM.VerifyAssertions) VM._assert(id < numSpecializedMethods);
    if (VM.VerifyAssertions) VM._assert(methods[id] == null);
    methods[id] = MemoryManager.createSpecializedMethod(id);
  }

  /** Can not create an instance of the manager */
  private SpecializedMethodManager() {}
}
