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
   * Long is 64 bits, but we need to reserve a bit for the result
   * and we start counting at zero.
   */
  private static final int MAXIMUM_PARAMETER_INDEX = 62;

  /**
   * Is this method currently being analyzed?  Used for recursive
   * invocations of the optimizing compiler.
   */
  private boolean inProgress = false;

  private static final long RESULT_ESCAPES = 0x8000000000000000L;

  private static final long EVERYTHING_ESCAPES = 0xFFFFFFFFFFFFFFFFL;

  /**
   * Escape result. Top bit is for the result of the method, i.e. the
   * return value. The this parameter counts as a parameter.
   */
  private long escapeInfo;

  /**
   * @param m RVMMethod representing this method.
   */
  MethodSummary(RVMMethod m) {
    escapeInfo = EVERYTHING_ESCAPES;
  }

  /**
   * Record that a parameter may or may not escape from a thread.
   *
   * @param p the number of the parameter
   * @param b may it escape?
   */
  public void setParameterMayEscapeThread(int p, boolean b) {
    if (p > MAXIMUM_PARAMETER_INDEX) return;
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
   * @return {@code false} iff the parameter <em>cannot</em> escape from the
   * thread, {@code true} otherwise.
   */
  public boolean parameterMayEscapeThread(int p) {
    if (p > MAXIMUM_PARAMETER_INDEX) return true;
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
      escapeInfo |= RESULT_ESCAPES;
    } else {
      escapeInfo &= ~RESULT_ESCAPES;
    }
  }

  /**
   * Query whether the result of this method may escape from a thread.
   * @return {@code false} iff the parameter <em> cannot </em> escape from the
   * thread, {@code true} otherwise.
   */
  public boolean resultMayEscapeThread() {
    return (escapeInfo & RESULT_ESCAPES) != 0L;
  }

  /**
   * @return whether the analysis of this method is in progress
   */
  public boolean inProgress() {
    return inProgress;
  }

  public void setInProgress(boolean b) {
    inProgress = b;
  }
}
