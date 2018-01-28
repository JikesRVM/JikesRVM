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
package org.jikesrvm.tools.bootImageWriter.entrycomparators;
import static org.jikesrvm.tools.bootImageWriter.entrycomparators.BootImageObjectInformation.getNumberOfReferences;

import java.util.Comparator;

import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.tools.bootImageWriter.BootImageMap;

/**
 * Comparator of boot image entries that sorts according to the number of
 * references within the objects.
 */
public final class NumberOfReferencesComparator implements Comparator<BootImageMap.Entry> {
  private final Comparator<BootImageMap.Entry> identicalSizeComparator;
  public NumberOfReferencesComparator(Comparator<BootImageMap.Entry> identicalSizeComparator) {
    this.identicalSizeComparator = identicalSizeComparator;
  }
  @Override
  public int compare(BootImageMap.Entry a, BootImageMap.Entry b) {
    TypeReference aRef = TypeReference.findOrCreate(a.getJdkObject().getClass());
    TypeReference bRef = TypeReference.findOrCreate(b.getJdkObject().getClass());
    if ((!aRef.isResolved() && !bRef.isResolved()) || (aRef == bRef)) {
      return identicalSizeComparator.compare(a, b);
    } else if (!aRef.isResolved()) {
      return 1;
    } else if (!bRef.isResolved()) {
      return -1;
    } else {
      int aSize = getNumberOfReferences(aRef.peekType(), a.getJdkObject());
      int bSize = getNumberOfReferences(bRef.peekType(), b.getJdkObject());
      if (aSize == bSize) {
        return identicalSizeComparator.compare(a, b);
      } else {
        return bSize - aSize;
      }
    }
  }
}
