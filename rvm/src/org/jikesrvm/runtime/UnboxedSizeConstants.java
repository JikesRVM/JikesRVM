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
package org.jikesrvm.runtime;

import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BITS_IN_BYTE;

import org.jikesrvm.VM;

/**
 * Constants defining the basic sizes of unboxed quantities.
 */
public final class UnboxedSizeConstants {

  public static final int LOG_BYTES_IN_ADDRESS = VM.BuildFor64Addr ? 3 : 2;
  public static final int BYTES_IN_ADDRESS = 1 << LOG_BYTES_IN_ADDRESS;
  public static final int LOG_BITS_IN_ADDRESS = LOG_BITS_IN_BYTE + LOG_BYTES_IN_ADDRESS;
  public static final int BITS_IN_ADDRESS = 1 << LOG_BITS_IN_ADDRESS;

  public static final int LOG_BYTES_IN_WORD = VM.BuildFor64Addr ? 3 : 2;
  public static final int BYTES_IN_WORD = 1 << LOG_BYTES_IN_WORD;
  public static final int LOG_BITS_IN_WORD = LOG_BITS_IN_BYTE + LOG_BYTES_IN_WORD;
  public static final int BITS_IN_WORD = 1 << LOG_BITS_IN_WORD;

  public static final int LOG_BYTES_IN_EXTENT = VM.BuildFor64Addr ? 3 : 2;
  public static final int BYTES_IN_EXTENT = 1 << LOG_BYTES_IN_EXTENT;
  public static final int LOG_BITS_IN_EXTENT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_EXTENT;
  public static final int BITS_IN_EXTENT = 1 << LOG_BITS_IN_EXTENT;

  public static final int LOG_BYTES_IN_OFFSET = VM.BuildFor64Addr ? 3 : 2;
  public static final int BYTES_IN_OFFSET = 1 << LOG_BYTES_IN_OFFSET;
  public static final int LOG_BITS_IN_OFFSET = LOG_BITS_IN_BYTE + LOG_BYTES_IN_OFFSET;
  public static final int BITS_IN_OFFSET = 1 << LOG_BITS_IN_OFFSET;

  private UnboxedSizeConstants() {
    // prevent instantiation
  }

}
