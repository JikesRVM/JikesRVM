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
package org.jikesrvm.util;

import java.io.InputStream;
import java.io.IOException;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Access raw memory region as an input stream.
 */
public final class AddressInputStream extends InputStream {
  /** Address of memory region to be read */
  private final Address location;
  /** Length of the memory region */
  private final Offset length;
  /** Offset to be read */
  private Offset offset;
  /** Mark offset */
  private Offset markOffset;

  /**
   *
   * @param location an address
   * @param length a positive Offset. Negative offsets will be silently
   *  changed to zero.
   */
  public AddressInputStream(Address location, Offset length) {
    if (length.sLT(Offset.zero())) {
      length = Offset.zero();
    }

    this.location = location;
    this.length = length;

    offset = Offset.zero();
    markOffset = Offset.zero();
  }

  /** @return number of bytes that can be read */
  @Override
  public int available() {
    int available = length.minus(offset).toInt();
    available = (available > 0) ? available : 0;
    return available;
  }

  /**
   * Closing an AddressInputStream has no effect.
   */
  @Override
  public void close() throws IOException {
  }

  /** Marks location. Read limit has no effect. */
  @Override
  public void mark(int readLimit) {
    markOffset = offset;
  }

  /** Is mark/reset supported */
  @Override
  public boolean markSupported() {
    return true;
  }

  /** Reads a byte */
  @Override
  public int read() {
    Word offsetAsWord = offset.toWord();
    Word lengthAsWord = length.toWord();
    if (offsetAsWord.GE(lengthAsWord)) {
      return -1;
    }

    byte result = location.loadByte(offset);
    offset = offset.plus(1);
    return result & 0xFF;
  }

  /** Resets to mark */
  @Override
  public void reset() {
    offset = markOffset;
  }

  /** Skips bytes (at most @code{Integer.MAX_VALUE} bytes) */
  @Override
  public long skip(long n) {
    if (n < 0) {
      return 0;
    }

    long maxInt = Integer.MAX_VALUE;
    int skipAmount = (n > maxInt) ? Integer.MAX_VALUE : (int) n;
    int available = available();
    skipAmount = (skipAmount > available) ? available : skipAmount;

    offset = offset.plus(skipAmount);
    return skipAmount;
  }
}
