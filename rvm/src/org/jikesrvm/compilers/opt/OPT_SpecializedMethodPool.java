/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.ArchitectureSpecific.VM_CodeArray;
import org.jikesrvm.compilers.common.VM_CompiledMethod;

/**
 * This class holds the static array of pointers to instructions
 * of specialized methods
 */
public class OPT_SpecializedMethodPool {
  private static final int SPECIALIZED_METHOD_COUNT = 1024;
  static int specializedMethodCount = 0;
  static VM_CodeArray[] specializedMethods = 
    new VM_CodeArray[SPECIALIZED_METHOD_COUNT];

  /**
   * Return the number of specialized methods
   */
  public int getSpecializedMethodCount () {
    return  specializedMethodCount;
  }

  /**
   * Register the specialized instructions for a method.
   */
  static void registerCompiledMethod (OPT_SpecializedMethod m) {
    int smid = m.getSpecializedMethodIndex();
    VM_CompiledMethod cm = m.getCompiledMethod();
    storeSpecializedMethod(cm, smid);
  }

  /**
   * Associate a particular compiled method with a specialized method id.
   */
  public static void storeSpecializedMethod (VM_CompiledMethod cm, int smid) {
    specializedMethods[smid] = cm.getEntryCodeArray();
  }

  /**
   * Is there a compiled version of a particular specialized method?
   * @param smid
   */
  public static boolean hasCompiledVersion (int smid) {
    return specializedMethods[smid] != null;
  }

  /**
   * @return a new unique integer identifier for a specialized method
   */
  public static int createSpecializedMethodID () {
    specializedMethodCount++;
    if (specializedMethodCount >= specializedMethods.length) {
      growSpecializedMethods();
    }
    return  specializedMethodCount;
  }

  /**
   * Increase the capacity of the internal data structures to track
   * specialized methods.
   */
  public static void growSpecializedMethods () {
    int org_length = specializedMethods.length;
    int new_length = 2*org_length;
    VM_CodeArray[] temp = new VM_CodeArray[new_length];
    for (int i = 0; i < org_length; i++) {
      temp[i] = specializedMethods[i];
    }
    specializedMethods = temp;
  }
}



