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

/**
 * Constants to describe the current architecture, and basic conversion/
 * arithmetic functions.
 */
public enum Architecture {
  BITS32 {
    public final int logBitsInWord() { return 5; }
    final ArchitecturalWord fromLong(long value) {
      return new ArchitecturalWord32((int)value);
    }
    final ArchitecturalWord fromIntZeroExtend(int value) {
      return fromLong(value);
    }
  },
  BITS64 {
    public final int logBitsInWord() { return 6; }
    final ArchitecturalWord fromLong(long value) {
      return new ArchitecturalWord64(value);
    }
    final ArchitecturalWord fromIntZeroExtend(int value) {
      return fromLong(value & 0xFFFFFFFFL);
    }
  };

  private static final int LOG_BITS_IN_BYTE = 3;
  private static final int BITS_IN_BYTE = 1<<LOG_BITS_IN_BYTE;

  abstract ArchitecturalWord fromLong(long value);
  abstract ArchitecturalWord fromIntZeroExtend(int value);

  public abstract int logBitsInWord();

  public final int bitsInWord() {
    return 1<<logBitsInWord();
  }

  public final int bytesInWord() {
    return bitsInWord()/BITS_IN_BYTE;
  }

  public final int logBytesInWord() {
    return logBitsInWord() - LOG_BITS_IN_BYTE;
  }
}
