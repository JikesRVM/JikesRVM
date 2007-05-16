/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Australian National University 2007
 */
package org.jikesrvm.objectmodel;

import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.classloader.VM_Class;

public class VM_FieldLayoutUnpacked extends VM_FieldLayout implements VM_SizeConstants {

  private static class LayoutContext extends VM_FieldLayoutContext {
    private static final int NO_HOLE = -1;
    int intHole = NO_HOLE;

    LayoutContext(byte alignment) {
      super(alignment);
    }

    LayoutContext(byte alignment, LayoutContext superLayout) {
      super(alignment, superLayout);
      if (superLayout != null) {
        intHole = superLayout.intHole;
      }
    }

    /** Return the next available offset for a given size */
    @Override
    int nextOffset(int size, boolean isReference) {
      int objectSize = getObjectSize();
      if (size == VM_FieldLayoutUnpacked.BYTES_IN_DOUBLE) {
        adjustAlignment(VM_FieldLayoutUnpacked.BYTES_IN_DOUBLE);
        if ((objectSize & 0x7) == 0) {
          ensureObjectSize(objectSize + VM_FieldLayoutUnpacked.BYTES_IN_DOUBLE);
          return objectSize;
        } else {
          ensureObjectSize(objectSize + VM_FieldLayoutUnpacked.BYTES_IN_DOUBLE + VM_FieldLayoutUnpacked.BYTES_IN_INT);
          intHole = objectSize;
          return objectSize + VM_FieldLayoutUnpacked.BYTES_IN_INT;
        }
      } else if (intHole >= 0) {
        int result = intHole;
        intHole = NO_HOLE;
        return result;
      } else {
        ensureObjectSize(objectSize + VM_FieldLayoutUnpacked.BYTES_IN_INT);
        return objectSize;
      }
    }
  }

  public VM_FieldLayoutUnpacked(boolean largeFieldsFirst, boolean clusterReferenceFields) {
    super(largeFieldsFirst, clusterReferenceFields);
  }

  /**
   * @param klass
   * @return
   */
  @Override
  protected VM_FieldLayoutContext getLayoutContext(VM_Class klass) {
    return new LayoutContext((byte) klass.getAlignment(), (LayoutContext) klass.getFieldLayoutContext());
  }
}
