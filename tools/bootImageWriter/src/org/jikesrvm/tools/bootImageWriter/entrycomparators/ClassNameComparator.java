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

import java.util.Comparator;

import org.jikesrvm.tools.bootImageWriter.BootImageMap;

/**
 * Comparator of boot image entries that sorts according to the name of the
 * classes.
 */
public final class ClassNameComparator implements Comparator<BootImageMap.Entry> {
  @Override
  public int compare(BootImageMap.Entry a, BootImageMap.Entry b) {
    return b.getJdkObject().getClass().toString().compareTo(a.getJdkObject().getClass().toString());
  }
}
