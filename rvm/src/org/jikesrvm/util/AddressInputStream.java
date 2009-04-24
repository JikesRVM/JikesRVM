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

/**
 * Access raw memory region as an input stream
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

  /** Constructor */
  public AddressInputStream(Address location, Offset length) {
    this.location = location;
    this.length = length;
    offset = Offset.zero();
    markOffset = Offset.zero();
  }

  /** @return number of bytes that can be read */
  public int available() {
    return length.minus(offset).toInt();
  }
  /** Mark location */
  public void mark(int readLimit) {
    markOffset = offset;
  }
  /** Is mark/reset supported */
  public boolean markSupported() {
    return true;
  }
  /** Read a byte */
  public int read() throws IOException {
    if (offset.sGE(length)) {
      throw new IOException("Read beyond end of memory region");
    }
    byte result = location.loadByte(offset);
    offset = offset.plus(1);
    return result;
  }
  /** Reset to mark */
  public void reset() {
    offset = markOffset;
  }
  /** Skip bytes */
  public long skip(long n) {
    offset = offset.plus((int)n);
    return (long)((int)n);
  }
}
