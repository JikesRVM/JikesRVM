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
package org.mmtk.utility;

import org.mmtk.utility.alloc.EmbeddedMetaData;
import org.mmtk.vm.VM;

/**
 * MMTk follows the pattern set by Jikes RVM for defining sizes of
 * primitive types thus:
 *
 * <pre>
 * static final int LOG_BYTES_IN_INT = 2;
 * static final int BYTES_IN_INT = 1&lt;&lt;LOG_BYTES_IN_INT;
 * static final int LOG_BITS_IN_INT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_INT;
 * static final int BITS_IN_INT = 1&lt;&lt;LOG_BITS_IN_INT;
 * </pre>
 *
 */
public final class Constants {

  /****************************************************************************
   *
   * MMTk constants
   */

  /**
   * Modes.
   */
  public static final int INSTANCE_FIELD = 0;
  public static final int ARRAY_ELEMENT = 1;


  /****************************************************************************
   *
   * Generic sizes
   */

  /**
   *
   */
  public static final byte LOG_BYTES_IN_BYTE = 0;
  public static final int BYTES_IN_BYTE = 1;
  public static final byte LOG_BITS_IN_BYTE = 3;
  public static final int BITS_IN_BYTE = 1 << LOG_BITS_IN_BYTE;

  public static final byte LOG_BYTES_IN_MBYTE = 20;
  public static final int BYTES_IN_MBYTE = 1 << LOG_BYTES_IN_MBYTE;

  public static final byte LOG_BYTES_IN_KBYTE = 10;
  public static final int BYTES_IN_KBYTE = 1 << LOG_BYTES_IN_KBYTE;

  /****************************************************************************
   *
   * Card scanning
   */

  /**
   *
   */
  public static final boolean SUPPORT_CARD_SCANNING = false;
  public static final int LOG_CARD_META_SIZE = 2;// each card consumes four bytes of metadata
  public static final int LOG_CARD_UNITS = 10;  // number of units tracked per card
  public static final int LOG_CARD_GRAIN = 0;   // track at byte grain, save shifting
  public static final int LOG_CARD_BYTES = LOG_CARD_UNITS + LOG_CARD_GRAIN;
  public static final int LOG_CARD_META_BYTES = EmbeddedMetaData.LOG_BYTES_IN_REGION - LOG_CARD_BYTES + LOG_CARD_META_SIZE;
  public static final int LOG_CARD_META_PAGES = LOG_CARD_META_BYTES - VM.LOG_BYTES_IN_PAGE;
  public static final int CARD_META_PAGES_PER_REGION = SUPPORT_CARD_SCANNING ? (1 << LOG_CARD_META_PAGES) : 0;
  public static final int CARD_MASK = (1 << LOG_CARD_BYTES) - 1;

  /**
   * Lazy sweeping - controlled from here because PlanConstraints needs to
   * tell the VM that we need to support linear scan.
   */
  public static final boolean LAZY_SWEEP = true;

  /****************************************************************************
   *
   * Java-specific sizes currently required by MMTk
   *
   * TODO MMTk should really become independent of these Java types
   */

  /**
   *
   */
  public static final byte LOG_BYTES_IN_CHAR = 1;
  public static final int BYTES_IN_CHAR = 1 << LOG_BYTES_IN_CHAR;
  public static final byte LOG_BITS_IN_CHAR = LOG_BITS_IN_BYTE + LOG_BYTES_IN_CHAR;
  public static final int BITS_IN_CHAR = 1 << LOG_BITS_IN_CHAR;

  public static final byte LOG_BYTES_IN_SHORT = 1;
  public static final int BYTES_IN_SHORT = 1 << LOG_BYTES_IN_SHORT;
  public static final byte LOG_BITS_IN_SHORT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_SHORT;
  public static final int BITS_IN_SHORT = 1 << LOG_BITS_IN_SHORT;

  public static final byte LOG_BYTES_IN_INT = 2;
  public static final int BYTES_IN_INT = 1 << LOG_BYTES_IN_INT;
  public static final byte LOG_BITS_IN_INT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_INT;
  public static final int BITS_IN_INT = 1 << LOG_BITS_IN_INT;

  public static final byte LOG_BYTES_IN_LONG = 3;
  public static final int BYTES_IN_LONG = 1 << LOG_BYTES_IN_LONG;
  public static final byte LOG_BITS_IN_LONG = LOG_BITS_IN_BYTE + LOG_BYTES_IN_LONG;
  public static final int BITS_IN_LONG = 1 << LOG_BITS_IN_LONG;

  public static final int MAX_INT = 0x7fffffff;
  public static final int MIN_INT = 0x80000000;

  /****************************************************************************
   *
   * VM-Specific sizes
   */

  /**
   *
   */
  public static final byte LOG_BYTES_IN_ADDRESS = VM.LOG_BYTES_IN_ADDRESS;
  public static final int BYTES_IN_ADDRESS = 1 << LOG_BYTES_IN_ADDRESS;
  public static final int LOG_BITS_IN_ADDRESS = LOG_BITS_IN_BYTE + LOG_BYTES_IN_ADDRESS;
  public static final int BITS_IN_ADDRESS = 1 << LOG_BITS_IN_ADDRESS;

  // Note that in MMTk we currently define WORD & ADDRESS to be the same size
  public static final byte LOG_BYTES_IN_WORD = LOG_BYTES_IN_ADDRESS;
  public static final int BYTES_IN_WORD = 1 << LOG_BYTES_IN_WORD;
  public static final int LOG_BITS_IN_WORD = LOG_BITS_IN_BYTE + LOG_BYTES_IN_WORD;
  public static final int BITS_IN_WORD = 1 << LOG_BITS_IN_WORD;

  public static final byte LOG_BYTES_IN_PAGE = VM.LOG_BYTES_IN_PAGE;
  public static final int BYTES_IN_PAGE = 1 << LOG_BYTES_IN_PAGE;
  public static final int LOG_BITS_IN_PAGE = LOG_BITS_IN_BYTE + LOG_BYTES_IN_PAGE;
  public static final int BITS_IN_PAGE = 1 << LOG_BITS_IN_PAGE;

  /* Assume byte-addressability */
  public static final byte LOG_BYTES_IN_ADDRESS_SPACE = (byte) BITS_IN_ADDRESS;

  /**
   * This value specifies the <i>minimum</i> allocation alignment
   * requirement of the VM.  When making allocation requests, both
   * <code>align</code> and <code>offset</code> must be multiples of
   * <code>MIN_ALIGNMENT</code>.
   *
   * This value is required to be a power of 2.
   */
  public static final byte LOG_MIN_ALIGNMENT = VM.LOG_MIN_ALIGNMENT;
  public static final int MIN_ALIGNMENT = 1 << LOG_MIN_ALIGNMENT;

  /**
   * The maximum alignment request the vm will make. This must be a
   * power of two multiple of the minimum alignment.
   */
  public static final int MAX_ALIGNMENT = MIN_ALIGNMENT << VM.MAX_ALIGNMENT_SHIFT;

  /**
   * The VM will add at most this value minus BYTES_IN_INT bytes of
   * padding to the front of an object that it places in a region of
   * memory. This value must be a power of 2.
   */
  public static final int MAX_BYTES_PADDING = VM.MAX_BYTES_PADDING;

  /**
   * A bit-pattern used to fill alignment gaps.
   */
  public static final int ALIGNMENT_VALUE = VM.ALIGNMENT_VALUE;
}
