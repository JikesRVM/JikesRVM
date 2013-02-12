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
package org.vmmagic.unboxed.harness;

import java.util.Comparator;

import org.vmmagic.unboxed.Word;

/**
 * A comparator so that we can use a Word in java.util sets etc.
 */
public class WordComparator implements Comparator<Word> {

  @Override
  public int compare(Word arg0, Word arg1) {
    return arg0.LT(arg1) ? -1 : (arg0.EQ(arg1) ? 0 : 1);
  }

}
