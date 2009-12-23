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
package org.jikesrvm;

/**
 * Constants defining the basic sizes of primitive quantities
 */
public interface SizeConstants {

  int LOG_BYTES_IN_BYTE = 0;
  int BYTES_IN_BYTE = 1;
  int LOG_BITS_IN_BYTE = 3;
  int BITS_IN_BYTE = 1 << LOG_BITS_IN_BYTE;

  int LOG_BYTES_IN_BOOLEAN = 0;
  int BYTES_IN_BOOLEAN = 1 << LOG_BYTES_IN_BOOLEAN;
  int LOG_BITS_IN_BOOLEAN = LOG_BITS_IN_BYTE + LOG_BYTES_IN_BOOLEAN;
  int BITS_IN_BOOLEAN = 1 << LOG_BITS_IN_BOOLEAN;

  int LOG_BYTES_IN_CHAR = 1;
  int BYTES_IN_CHAR = 1 << LOG_BYTES_IN_CHAR;
  int LOG_BITS_IN_CHAR = LOG_BITS_IN_BYTE + LOG_BYTES_IN_CHAR;
  int BITS_IN_CHAR = 1 << LOG_BITS_IN_CHAR;

  int LOG_BYTES_IN_SHORT = 1;
  int BYTES_IN_SHORT = 1 << LOG_BYTES_IN_SHORT;
  int LOG_BITS_IN_SHORT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_SHORT;
  int BITS_IN_SHORT = 1 << LOG_BITS_IN_SHORT;

  int LOG_BYTES_IN_INT = 2;
  int BYTES_IN_INT = 1 << LOG_BYTES_IN_INT;
  int LOG_BITS_IN_INT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_INT;
  int BITS_IN_INT = 1 << LOG_BITS_IN_INT;

  int LOG_BYTES_IN_FLOAT = 2;
  int BYTES_IN_FLOAT = 1 << LOG_BYTES_IN_FLOAT;
  int LOG_BITS_IN_FLOAT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_FLOAT;
  int BITS_IN_FLOAT = 1 << LOG_BITS_IN_FLOAT;

  int LOG_BYTES_IN_LONG = 3;
  int BYTES_IN_LONG = 1 << LOG_BYTES_IN_LONG;
  int LOG_BITS_IN_LONG = LOG_BITS_IN_BYTE + LOG_BYTES_IN_LONG;
  int BITS_IN_LONG = 1 << LOG_BITS_IN_LONG;

  int LOG_BYTES_IN_DOUBLE = 3;
  int BYTES_IN_DOUBLE = 1 << LOG_BYTES_IN_DOUBLE;
  int LOG_BITS_IN_DOUBLE = LOG_BITS_IN_BYTE + LOG_BYTES_IN_DOUBLE;
  int BITS_IN_DOUBLE = 1 << LOG_BITS_IN_DOUBLE;

  int LOG_BYTES_IN_ADDRESS = VM.BuildFor64Addr ? 3 : 2;
  int BYTES_IN_ADDRESS = 1 << LOG_BYTES_IN_ADDRESS;
  int LOG_BITS_IN_ADDRESS = LOG_BITS_IN_BYTE + LOG_BYTES_IN_ADDRESS;
  int BITS_IN_ADDRESS = 1 << LOG_BITS_IN_ADDRESS;

  int LOG_BYTES_IN_WORD = VM.BuildFor64Addr ? 3 : 2;
  int BYTES_IN_WORD = 1 << LOG_BYTES_IN_WORD;
  int LOG_BITS_IN_WORD = LOG_BITS_IN_BYTE + LOG_BYTES_IN_WORD;
  int BITS_IN_WORD = 1 << LOG_BITS_IN_WORD;

  int LOG_BYTES_IN_EXTENT = VM.BuildFor64Addr ? 3 : 2;
  int BYTES_IN_EXTENT = 1 << LOG_BYTES_IN_EXTENT;
  int LOG_BITS_IN_EXTENT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_EXTENT;
  int BITS_IN_EXTENT = 1 << LOG_BITS_IN_EXTENT;

  int LOG_BYTES_IN_OFFSET = VM.BuildFor64Addr ? 3 : 2;
  int BYTES_IN_OFFSET = 1 << LOG_BYTES_IN_OFFSET;
  int LOG_BITS_IN_OFFSET = LOG_BITS_IN_BYTE + LOG_BYTES_IN_OFFSET;
  int BITS_IN_OFFSET = 1 << LOG_BITS_IN_OFFSET;

  int LOG_BYTES_IN_PAGE = 12;
  int BYTES_IN_PAGE = 1 << LOG_BYTES_IN_PAGE;
  int LOG_BITS_IN_PAGE = LOG_BITS_IN_BYTE + LOG_BYTES_IN_PAGE;
  int BITS_IN_PAGE = 1 << LOG_BITS_IN_PAGE;
}
