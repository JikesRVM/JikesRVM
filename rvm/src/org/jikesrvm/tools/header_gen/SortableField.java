/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002, 2003
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
  SortableField(VM_Field ff) { f = ff; offset = f.getOffset(); }
  public int compareTo (Object y) {
    if (y instanceof SortableField) {
      Offset offset2 = ((SortableField) y).offset;
      if (offset.sGT(offset2)) return 1;
      if (offset.sLT(offset2)) return -1;
      return 0;
    }
    return 1;
  }
}
