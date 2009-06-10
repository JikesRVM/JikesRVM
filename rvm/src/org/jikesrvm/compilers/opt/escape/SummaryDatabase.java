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
package org.jikesrvm.compilers.opt.escape;

import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.util.ImmutableEntryHashMapRVM;

/**
 * Class that holds method summary information.
 * This class is a Singleton.
 *
 * <p> This database holds summaries:
 *  <ul>
 *   <li>MethodSummary, indexed by RVMMethod
 *  </ul>
 */
class SummaryDatabase {
  /**
   * Lookup a given method in the database
   *
   * @return MethodSummary instance representing method
   */
  public static synchronized MethodSummary findMethodSummary(RVMMethod m) {
    return hash.get(m);
  }

  public static synchronized MethodSummary findOrCreateMethodSummary(RVMMethod m) {
    MethodSummary result = findMethodSummary(m);
    if (result == null) {
      result = new MethodSummary(m);
      hash.put(m, result);
    }
    return result;
  }

  /** Implementation */
  private static final ImmutableEntryHashMapRVM<RVMMethod, MethodSummary> hash =
    new ImmutableEntryHashMapRVM<RVMMethod, MethodSummary>();
}
