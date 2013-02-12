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

/**
 * Hold semantic information about a method that is not defined in
 * RVMMethod.
 */
class MethodSummary {

  /**
   * Is this method currently being analyzed?  Used for recursive
   * invocations of the optimizing compiler.
   */
  private static boolean inProgress = false;

  /**
   * Default escape result, that the result escapes but that no parameter is
   * escaping.
   */
  private static final long RES_ESCAPE = 0x80000000;

  /**
   * Escape result, top bit is result of the method bits 0..63 are for
   * parameters 0..63 respectively
   */
  private long escapeInfo = RES_ESCAPE;

  /**
   * @param m RVMMethod representing this method.
   */
  MethodSummary(RVMMethod m) { }

  /**
   * Record that a parameter may or may not escape from a thread.
   *
   * @param p the number of the parameter
   * @param b may it escape?
   */
  public void setParameterMayEscapeThread(int p, boolean b) {
    if (p > 62) return; // all params past 62 escape!
    long mask = 1L << p;
    if (b) {
      escapeInfo |= mask;
    } else {
      escapeInfo &= (~mask);
    }
  }

  /**
   * Query whether a parameter may escape from a thread.
   * @param p the number of the parameter
   * @return false iff the parameter <em> must not </em> escape from the
   * thread. true otherwise.
   */
  public boolean parameterMayEscapeThread(int p) {
    if (p > 62) return true; // all params past 62 escape!
    long mask = 1L << p;
    return (escapeInfo & mask) != 0;
  }

  /**
   * Record that a result of this method may or may not escape from a thread.
   *
   * @param b may it escape?
   */
  public void setResultMayEscapeThread(boolean b) {
    if (b) {
      escapeInfo |= RES_ESCAPE;
    } else {
      escapeInfo &= ~RES_ESCAPE;
    }
  }

  /**
   * Query whether the result of this method may escape from a thread.
   * @return {@code false} iff the parameter <em> must not </em> escape from the
   * thread. true otherwise.
   */
  public boolean resultMayEscapeThread() {
    return (escapeInfo & RES_ESCAPE) != 0L;
  }

  /**
   * Is analysis of this method in progress?
   */
  public boolean inProgress() {
    return inProgress;
  }

  /**
   * Mark that analysis of this method is or is not in progress.
   * @param b
   */
  public void setInProgress(boolean b) {
    inProgress = b;
  }
}
