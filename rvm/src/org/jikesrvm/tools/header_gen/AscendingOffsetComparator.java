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
package org.jikesrvm.tools.header_gen;

import java.util.Comparator;

import org.jikesrvm.classloader.RVMField;
import org.vmmagic.unboxed.Offset;

/**
 * Utility class to help sort fields.
 */
class AscendingOffsetComparator implements Comparator<RVMField> {

  @Override
  public int compare(RVMField firstField, RVMField secondField) {
    Offset firstOff = firstField.getOffset();
    Offset secondOff = secondField.getOffset();
    if (firstOff.sGT(secondOff)) return 1;
    if (firstOff.sLT(secondOff)) return -1;
    return 0;
  }
}
