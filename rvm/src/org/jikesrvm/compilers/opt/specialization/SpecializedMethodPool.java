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

import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.compilers.common.CompiledMethod;

/**
 * This class holds the static array of pointers to instructions
 * of specialized methods
 */
public final class SpecializedMethodPool {
  private static final int SPECIALIZED_METHOD_COUNT = 1024;
  static int specializedMethodCount = 0;
  static CodeArray[] specializedMethods = new CodeArray[SPECIALIZED_METHOD_COUNT];

  /**
   * Return the number of specialized methods
   */
  public int getSpecializedMethodCount() {
    return specializedMethodCount;
  }

  /**
   * Register the specialized instructions for a method.
   */
  static void registerCompiledMethod(SpecializedMethod m) {
    int smid = m.getSpecializedMethodIndex();
    CompiledMethod cm = m.getCompiledMethod();
    storeSpecializedMethod(cm, smid);
  }

  /**
   * Associate a particular compiled method with a specialized method id.
   */
  public static void storeSpecializedMethod(CompiledMethod cm, int smid) {
    specializedMethods[smid] = cm.getEntryCodeArray();
  }

  /**
   * Is there a compiled version of a particular specialized method?
   * @param smid
   */
  public static boolean hasCompiledVersion(int smid) {
    return specializedMethods[smid] != null;
  }

  /**
   * @return a new unique integer identifier for a specialized method
   */
  public static int createSpecializedMethodID() {
    specializedMethodCount++;
    if (specializedMethodCount >= specializedMethods.length) {
      growSpecializedMethods();
    }
    return specializedMethodCount;
  }

  /**
   * Increase the capacity of the internal data structures to track
   * specialized methods.
   */
  public static void growSpecializedMethods() {
    int org_length = specializedMethods.length;
    int new_length = 2 * org_length;
    CodeArray[] temp = new CodeArray[new_length];
    for (int i = 0; i < org_length; i++) {
      temp[i] = specializedMethods[i];
    }
    specializedMethods = temp;
  }
}



