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

import static org.vmmagic.unboxed.harness.Architecture.BITS32;
import static org.vmmagic.unboxed.harness.Architecture.BITS64;

/**
 * A word of whatever length the architecture requires.
 *
 */
public abstract class ArchitecturalWord {

  /**
   * Factory method
   * @param value The <code>long</code> to convert to a word
   * @return The corresponding word
   */
  public static ArchitecturalWord fromLong(long value) {
    if (model == null) {
      throw new RuntimeException("ArchitecturalWord used before initialization");
    }
    return model.fromLong(value);
  }

  /**
   * Factory method
   * @param value The int to convert
   * @return The converted int
   */
  public static ArchitecturalWord fromIntZeroExtend(int value) {
    if (model == null) {
      throw new RuntimeException("ArchitecturalWord used before initialization");
    }
    return model.fromIntZeroExtend(value);
  }

  /**
   * Factory method
   * @param value The int to sign-extend to a Word
   * @return The sign-extended word
   */
  public static ArchitecturalWord fromIntSignExtend(int value) {
    if (model == null) {
      throw new RuntimeException("ArchitecturalWord used before initialization");
    }
    return model.fromLong(value);  // Java sign-extends as it casts int -> long
  }

  private static Architecture model = null;

  /**
   * Static initialization
   * @param bits TODO
   */
  public static void init(int bits) {
    assert bits == 32 || bits == 64 : "Unsupported bit width, "+bits;
    if (model == null) {
      model = bits == 32 ? BITS32 : BITS64;
    }
  }

  /**
   * @return The architectural model (BITS32 or BITS64)
   */
  public static Architecture getModel() {
    return model;
  }

  public abstract boolean isZero();
  public abstract boolean isMax();
  public abstract int toInt();
  public abstract long toLongSignExtend();
  public abstract long toLongZeroExtend();

  public abstract ArchitecturalWord plus(long offset);
  public abstract ArchitecturalWord minus(long offset);

  public abstract boolean LT(ArchitecturalWord word);
  public abstract boolean EQ(ArchitecturalWord word);

  public final boolean LE(ArchitecturalWord word) {
    return LT(word) || EQ(word);
  }

  public final boolean GT(ArchitecturalWord word) {
    return !LE(word);
  }

  public final boolean GE(ArchitecturalWord word) {
    return !LT(word);
  }

  public final boolean NE(ArchitecturalWord word) {
    return !EQ(word);
  }

  public abstract boolean sLT(ArchitecturalWord word);

  public final boolean sLE(ArchitecturalWord word) {
    return sLT(word) || EQ(word);
  }

  public final boolean sGT(ArchitecturalWord word) {
    return !sLE(word);
  }

  public final boolean sGE(ArchitecturalWord word) {
    return !sLT(word);
  }

  public abstract ArchitecturalWord diff(ArchitecturalWord w);

  public abstract ArchitecturalWord and(ArchitecturalWord w);
  public abstract ArchitecturalWord or(ArchitecturalWord w);
  public abstract ArchitecturalWord not();
  public abstract ArchitecturalWord xor(ArchitecturalWord w);
  public abstract ArchitecturalWord lsh(int amt);
  public abstract ArchitecturalWord rshl(int amt);
  public abstract ArchitecturalWord rsha(int amt);

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ArchitecturalWord)) {
      return false;
    }
    return EQ((ArchitecturalWord)obj);
  }
}
