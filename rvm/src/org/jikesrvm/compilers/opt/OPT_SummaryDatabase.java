/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.util.VM_HashMap;

/**
 * Class that holds method summary information.
 * This class is a Singleton.
 *
 * <p> This database holds summaries:
 *  <ul>
 *   <li>OPT_MethodSummary, indexed by VM_Method
 *  </ul>
 */
public class OPT_SummaryDatabase {
  /**
   * Lookup a given method in the database
   *
   * @return OPT_MethodSummary instance representing method
   */
  public static synchronized OPT_MethodSummary findMethodSummary(VM_Method m) {
    return hash.get(m);
  }

  public static synchronized OPT_MethodSummary findOrCreateMethodSummary(VM_Method m) {
    OPT_MethodSummary result = findMethodSummary(m);
    if (result == null) {
      result = new OPT_MethodSummary(m);
      hash.put(m, result);
    }
    return result;
  }

  /** Implementation */
  private static final VM_HashMap<VM_Method, OPT_MethodSummary> hash = new VM_HashMap<VM_Method, OPT_MethodSummary>();
}
