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
 * Basic constants for describing memory
 */
public class MemoryConstants {

  /** Log_2 of page size */
  public static final int LOG_BYTES_IN_PAGE = 12;
  /** Log_2 of size(long) */
  public static final int LOG_BYTES_IN_LONG = 3;
  /** Log_2 of size(address) */
  public static final int LOG_BYTES_IN_WORD = ArchitecturalWord.getModel().logBytesInWord();
  /** Log_2 of size(int) */
  public static final int LOG_BYTES_IN_INT = 2;
  /** Log_2 of size(short) */
  public static final int LOG_BYTES_IN_SHORT = 1;
  /** Log_2 of size(byte) */
  public static final int LOG_BITS_IN_BYTE = 3;
  /** size in bytes of a memory page */
  public static final int BYTES_IN_PAGE = 1 << LOG_BYTES_IN_PAGE;
  /** size(long) */
  public static final int BYTES_IN_LONG = 1 << LOG_BYTES_IN_LONG;
  /** size(address), size(word) etc */
  public static final int BYTES_IN_WORD = 1 << LOG_BYTES_IN_WORD;
  /** size(int) */
  public static final int BYTES_IN_INT = 1 << LOG_BYTES_IN_INT;
  /** size(short) */
  public static final int BYTES_IN_SHORT = 1 << LOG_BYTES_IN_SHORT;
  /** size(byte) */
  public static final int BITS_IN_BYTE = 1 << LOG_BITS_IN_BYTE;
  /** Mask an offset within an int */
  public static final int INT_MASK = ~(BYTES_IN_INT - 1);
  /** Mask an offset within a word */
  public static final int WORD_MASK = ~(BYTES_IN_WORD - 1);
  /** Mask an offset within a page */
  public static final int PAGE_MASK = ~(BYTES_IN_PAGE - 1);

}
