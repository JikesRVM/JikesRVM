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

import org.jikesrvm.classloader.RVMField;
import org.vmmagic.unboxed.Offset;

/**
 * Utility class to help sort fields.
 */
class SortableField implements Comparable<SortableField> {
  final RVMField f;
  final Offset offset;

  SortableField(RVMField ff) {
    f = ff;
    offset = f.getOffset();
  }

  @Override
  public int compareTo(SortableField y) {
    if (offset.sGT(y.offset)) return 1;
    if (offset.sLT(y.offset)) return -1;
    return 0;
  }
}
