/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import  java.util.*;

/**
 * Class that holds class and method summary information
 * This class is a Singleton
 *
 * <p> This database holds several types of summaries:
 *  <ul>
 *   <li>OPT_ClassSummary, indexed by VM_Class
 *   <li>OPT_MethodSummary, indexed by VM_Method
 *  </ul>
 *
 * @author Stephen Fink
 */
public class OPT_SummaryDatabase {

  /** 
   * Make sure the database is initialized.
   * Calling this more than once is harmless.
   */
  public static void init () {}

  /** 
   * Lookup a given class in the database.
   * 
   * @return OPT_ClassSummary instance representing class. 
   *  null if not found
   */
  public static OPT_ClassSummary findClassSummary (VM_Class c) {
    return  (OPT_ClassSummary)hash.get(c);
  }

  /** 
   * Lookup a given method in the database
   * 
   * @return OPT_MethodSummary instance representing method
   */
  public static OPT_MethodSummary findMethodSummary (VM_Method m) {
    return  (OPT_MethodSummary)hash.get(m);
  }

  public static OPT_MethodSummary findOrCreateMethodSummary (VM_Method m) {
    OPT_MethodSummary result = findMethodSummary(m);
    if (result == null) {
      result = new OPT_MethodSummary(m);
      hash.put(m, result);
    }
    return  result;
  }

  /** Implementation */
  private static final boolean DEBUG = false;
  private static java.util.HashMap hash = new java.util.HashMap();

}



