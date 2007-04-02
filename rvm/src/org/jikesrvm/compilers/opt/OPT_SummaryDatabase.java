/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.util.*;
import org.jikesrvm.classloader.*;

/**
 * Class that holds method summary information.
 * This class is a Singleton.
 *
 * <p> This database holds summaries:
 *  <ul>
 *   <li>OPT_MethodSummary, indexed by VM_Method
 *  </ul>
 *
 * @author Stephen Fink
 */
public class OPT_SummaryDatabase {
  /** 
   * Lookup a given method in the database
   * 
   * @return OPT_MethodSummary instance representing method
   */
  public static synchronized OPT_MethodSummary findMethodSummary (VM_Method m) {
    return hash.get(m);
  }

  public static synchronized OPT_MethodSummary findOrCreateMethodSummary (VM_Method m) {
    OPT_MethodSummary result = findMethodSummary(m);
    if (result == null) {
      result = new OPT_MethodSummary(m);
      hash.put(m, result);
    }
    return  result;
  }

  /** Implementation */
  private static final VM_HashMap<VM_Method,OPT_MethodSummary> hash = 
    new VM_HashMap<VM_Method,OPT_MethodSummary>();
}
