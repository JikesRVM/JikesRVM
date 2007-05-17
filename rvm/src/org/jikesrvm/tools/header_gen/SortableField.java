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
package org.jikesrvm.tools.header_gen;

import org.jikesrvm.classloader.VM_Field;
import org.vmmagic.unboxed.Offset;

/**
 * Utility class to help sot fields.
 */
class SortableField implements Comparable {
  final VM_Field f;
  final Offset offset;

  SortableField(VM_Field ff) {
    f = ff;
    offset = f.getOffset();
  }

  public int compareTo(Object y) {
    if (y instanceof SortableField) {
      Offset offset2 = ((SortableField) y).offset;
      if (offset.sGT(offset2)) return 1;
      if (offset.sLT(offset2)) return -1;
      return 0;
    }
    return 1;
  }
}
