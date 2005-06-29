/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import  java.util.Vector;
import  java.util.Enumeration;

/**
 * This class holds the static array of pointers to instructions
 * of specialized methods
 *
 * @author Rajesh Bordawekar
 * @modified Stephen Fink
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
    if (specializedMethods[smid] != null) {
      return  true;
    }
    return  false;
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



