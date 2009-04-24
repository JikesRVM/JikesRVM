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
package org.vmmagic.unboxed;

import static org.vmmagic.unboxed.Architecture.BITS32;
import static org.vmmagic.unboxed.Architecture.BITS64;

import org.mmtk.harness.Harness;

public abstract class ArchitecturalWord {

  /**
   * Factory method
   * @param value
   * @return
   */
  static ArchitecturalWord fromLong(long value) {
    if (model == null) {
      throw new RuntimeException("ArchitecturalWord used before initialization");
    }
    return model.fromLong(value);
  }

  /**
   * Factory method
   * @param value
   * @return
   */
  static ArchitecturalWord fromIntZeroExtend(int value) {
    if (model == null) {
      throw new RuntimeException("ArchitecturalWord used before initialization");
    }
    return model.fromIntZeroExtend(value);
  }

  /**
   * Factory method
   * @param value
   * @return
   */
  static ArchitecturalWord fromIntSignExtend(int value) {
    if (model == null) {
      throw new RuntimeException("ArchitecturalWord used before initialization");
    }
    return model.fromLong(value);  // Java sign-extends as it casts int -> long
  }

  private static Architecture model = null;

  public static void setBits(int bits) {
    model = bits == 32 ? BITS32 : BITS64;
  }

  /**
   * Static initialization
   */
  public static void init() {
    if (model == null) {
      setBits(Harness.bits.getValue());
    }
  }

  public static Architecture getModel() {
    return model;
  }

  abstract boolean isZero();
  abstract boolean isMax();
  abstract int toInt();
  abstract long toLongSignExtend();
  abstract long toLongZeroExtend();

  abstract ArchitecturalWord plus(long offset);
  abstract ArchitecturalWord minus(long offset);

  abstract boolean LT(ArchitecturalWord word);
  abstract boolean EQ(ArchitecturalWord word);

  final boolean LE(ArchitecturalWord word) {
    return LT(word) || EQ(word);
  }

  final boolean GT(ArchitecturalWord word) {
    return !LE(word);
  }

  final boolean GE(ArchitecturalWord word) {
    return !LT(word);
  }

  final boolean NE(ArchitecturalWord word) {
    return !EQ(word);
  }

  abstract boolean sLT(ArchitecturalWord word);

  final boolean sLE(ArchitecturalWord word) {
    return sLT(word) || EQ(word);
  }

  final boolean sGT(ArchitecturalWord word) {
    return !sLE(word);
  }

  final boolean sGE(ArchitecturalWord word) {
    return !sLT(word);
  }

  abstract ArchitecturalWord diff(ArchitecturalWord w);

  abstract ArchitecturalWord and(ArchitecturalWord w);
  abstract ArchitecturalWord or(ArchitecturalWord w);
  abstract ArchitecturalWord not();
  abstract ArchitecturalWord xor(ArchitecturalWord w);
  abstract ArchitecturalWord lsh(int amt);
  abstract ArchitecturalWord rshl(int amt);
  abstract ArchitecturalWord rsha(int amt);

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ArchitecturalWord)) {
      return false;
    }
    return EQ((ArchitecturalWord)obj);
  }
}
