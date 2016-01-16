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

/**
 * Constants defining the basic sizes of primitive quantities
 */
public final class JavaSizeConstants {

  public static final int LOG_BYTES_IN_BYTE = 0;
  public static final int BYTES_IN_BYTE = 1;
  public static final int LOG_BITS_IN_BYTE = 3;
  public static final int BITS_IN_BYTE = 1 << LOG_BITS_IN_BYTE;

  public static final int LOG_BYTES_IN_BOOLEAN = 0;
  public static final int BYTES_IN_BOOLEAN = 1 << LOG_BYTES_IN_BOOLEAN;
  public static final int LOG_BITS_IN_BOOLEAN = LOG_BITS_IN_BYTE + LOG_BYTES_IN_BOOLEAN;
  public static final int BITS_IN_BOOLEAN = 1 << LOG_BITS_IN_BOOLEAN;

  public static final int LOG_BYTES_IN_CHAR = 1;
  public static final int BYTES_IN_CHAR = 1 << LOG_BYTES_IN_CHAR;
  public static final int LOG_BITS_IN_CHAR = LOG_BITS_IN_BYTE + LOG_BYTES_IN_CHAR;
  public static final int BITS_IN_CHAR = 1 << LOG_BITS_IN_CHAR;

  public static final int LOG_BYTES_IN_SHORT = 1;
  public static final int BYTES_IN_SHORT = 1 << LOG_BYTES_IN_SHORT;
  public static final int LOG_BITS_IN_SHORT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_SHORT;
  public static final int BITS_IN_SHORT = 1 << LOG_BITS_IN_SHORT;

  public static final int LOG_BYTES_IN_INT = 2;
  public static final int BYTES_IN_INT = 1 << LOG_BYTES_IN_INT;
  public static final int LOG_BITS_IN_INT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_INT;
  public static final int BITS_IN_INT = 1 << LOG_BITS_IN_INT;

  public static final int LOG_BYTES_IN_FLOAT = 2;
  public static final int BYTES_IN_FLOAT = 1 << LOG_BYTES_IN_FLOAT;
  public static final int LOG_BITS_IN_FLOAT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_FLOAT;
  public static final int BITS_IN_FLOAT = 1 << LOG_BITS_IN_FLOAT;

  public static final int LOG_BYTES_IN_LONG = 3;
  public static final int BYTES_IN_LONG = 1 << LOG_BYTES_IN_LONG;
  public static final int LOG_BITS_IN_LONG = LOG_BITS_IN_BYTE + LOG_BYTES_IN_LONG;
  public static final int BITS_IN_LONG = 1 << LOG_BITS_IN_LONG;

  public static final int LOG_BYTES_IN_DOUBLE = 3;
  public static final int BYTES_IN_DOUBLE = 1 << LOG_BYTES_IN_DOUBLE;
  public static final int LOG_BITS_IN_DOUBLE = LOG_BITS_IN_BYTE + LOG_BYTES_IN_DOUBLE;
  public static final int BITS_IN_DOUBLE = 1 << LOG_BITS_IN_DOUBLE;

  private JavaSizeConstants() {
    // prevent instantiation
  }

}
