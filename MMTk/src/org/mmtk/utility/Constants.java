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
 * static final int BYTES_IN_INT = 1<<LOG_BYTES_IN_INT;
 * static final int LOG_BITS_IN_INT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_INT;
 * static final int BITS_IN_INT = 1<<LOG_BITS_IN_INT;
 * </pre>
 */
public interface Constants {

  /****************************************************************************
   *
   * MMTk constants
   */
  int PUTFIELD_WRITE_BARRIER = 0;
  int GETFIELD_READ_BARRIER = 0;
  int PUTSTATIC_WRITE_BARRIER = 1;
  int GETSTATIC_READ_BARRIER = 1;
  int AASTORE_WRITE_BARRIER = 2;
  int AALOAD_READ_BARRIER = 2;


  /****************************************************************************
   *
   * Generic sizes
   */
  byte LOG_BYTES_IN_BYTE = 0;
  int BYTES_IN_BYTE = 1;
  byte LOG_BITS_IN_BYTE = 3;
  int BITS_IN_BYTE = 1 << LOG_BITS_IN_BYTE;

  byte LOG_BYTES_IN_MBYTE = 20;
  int BYTES_IN_MBYTE = 1 << LOG_BYTES_IN_MBYTE;

  byte LOG_BYTES_IN_KBYTE = 10;
  int BYTES_IN_KBYTE = 1 << LOG_BYTES_IN_KBYTE;

  /****************************************************************************
   *
   * Card scanning
   */
  boolean SUPPORT_CARD_SCANNING = false;
  int LOG_CARD_META_SIZE = 2;// each card consumes four bytes of metadata
  int LOG_CARD_UNITS = 10;  // number of units tracked per card
  int LOG_CARD_GRAIN = 0;   // track at byte grain, save shifting
  int LOG_CARD_BYTES = LOG_CARD_UNITS + LOG_CARD_GRAIN;
  int LOG_CARD_META_BYTES = EmbeddedMetaData.LOG_BYTES_IN_REGION - LOG_CARD_BYTES + LOG_CARD_META_SIZE;
  int LOG_CARD_META_PAGES = LOG_CARD_META_BYTES - VM.LOG_BYTES_IN_PAGE;
  int CARD_META_PAGES_PER_REGION = SUPPORT_CARD_SCANNING ? (1<<LOG_CARD_META_PAGES) : 0;
  int CARD_MASK = (1<<LOG_CARD_BYTES) - 1;


  /****************************************************************************
   *
   * Java-specific sizes currently required by MMTk
   *
   * TODO MMTk should really become independent of these Java types
   */
  byte LOG_BYTES_IN_SHORT = 1;
  int BYTES_IN_SHORT = 1 << LOG_BYTES_IN_SHORT;
  byte LOG_BITS_IN_SHORT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_SHORT;
  int BITS_IN_SHORT = 1 << LOG_BITS_IN_SHORT;

  byte LOG_BYTES_IN_INT = 2;
  int BYTES_IN_INT = 1 << LOG_BYTES_IN_INT;
  byte LOG_BITS_IN_INT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_INT;
  int BITS_IN_INT = 1 << LOG_BITS_IN_INT;

  int MAX_INT = 0x7fffffff;
  int MIN_INT = 0x80000000;

  /****************************************************************************
   *
   * VM-Specific sizes
   */
  byte LOG_BYTES_IN_ADDRESS = VM.LOG_BYTES_IN_ADDRESS;
  int BYTES_IN_ADDRESS = 1 << LOG_BYTES_IN_ADDRESS;
  int LOG_BITS_IN_ADDRESS = LOG_BITS_IN_BYTE + LOG_BYTES_IN_ADDRESS;
  int BITS_IN_ADDRESS = 1 << LOG_BITS_IN_ADDRESS;

  // Note that in MMTk we currently define WORD & ADDRESS to be the same size
  byte LOG_BYTES_IN_WORD = LOG_BYTES_IN_ADDRESS;
  int BYTES_IN_WORD = 1 << LOG_BYTES_IN_WORD;
  int LOG_BITS_IN_WORD = LOG_BITS_IN_BYTE + LOG_BYTES_IN_WORD;
  int BITS_IN_WORD = 1 << LOG_BITS_IN_WORD;

  byte LOG_BYTES_IN_PAGE = VM.LOG_BYTES_IN_PAGE;
  int BYTES_IN_PAGE = 1 << LOG_BYTES_IN_PAGE;
  int LOG_BITS_IN_PAGE = LOG_BITS_IN_BYTE + LOG_BYTES_IN_PAGE;
  int BITS_IN_PAGE = 1 << LOG_BITS_IN_PAGE;

  /* Assume byte-addressability */
  byte LOG_BYTES_IN_ADDRESS_SPACE = (byte) BITS_IN_ADDRESS;

  /**
   * This value specifies the <i>minimum</i> allocation alignment
   * requirement of the VM.  When making allocation requests, both
   * <code>align</code> and <code>offset</code> must be multiples of
   * <code>MIN_ALIGNMENT</code>.
   *
   * This value is required to be a power of 2.
   */
  byte LOG_MIN_ALIGNMENT = VM.LOG_MIN_ALIGNMENT;
  int MIN_ALIGNMENT = 1 << LOG_MIN_ALIGNMENT;

  /**
   * The maximum alignment request the vm will make. This must be a
   * power of two multiple of the minimum alignment.
   */
  int MAX_ALIGNMENT = MIN_ALIGNMENT<<VM.MAX_ALIGNMENT_SHIFT;

  /**
   * The VM will add at most this value minus BYTES_IN_INT bytes of
   * padding to the front of an object that it places in a region of
   * memory. This value must be a power of 2.
   */
  int MAX_BYTES_PADDING = VM.MAX_BYTES_PADDING;

  /**
   * The VM will add at most this value minus BYTES_IN_INT bytes of
   * padding to the front of an object that it places in a region of
   * memory. This value must be a power of 2.
   */
  int ALIGNMENT_VALUE = VM.ALIGNMENT_VALUE;
}
