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

/**
 * Represent a value that is a parameter
 */
final class ValueGraphParamLabel {
  private final int paramNum;

  ValueGraphParamLabel(int pn) {
    paramNum = pn;
  }

  public String toString() {
    return "formal" + paramNum;
  }
}
