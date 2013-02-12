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
package org.mmtk.policy.immix;

import static org.mmtk.policy.immix.ImmixConstants.*;

import org.mmtk.utility.Constants;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;

@Uninterruptible
public class Line implements Constants {

  public static Address align(Address ptr) {
    return ptr.toWord().and(LINE_MASK.not()).toAddress();
  }

  public static boolean isAligned(Address address) {
    return address.EQ(align(address));
  }

  static int getChunkIndex(Address line) {
    return line.toWord().and(CHUNK_MASK).rshl(LOG_BYTES_IN_LINE).toInt();
  }

 /***************************************************************************
  * Line marking
  */

  /**
   *
   */
  static void mark(Address address, final byte markValue) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!Block.isUnused(Block.align(address)));
    getMarkAddress(address).store(markValue);
  }

  static void markMultiLine(Address start, ObjectReference object, final byte markValue) {
    /* endLine is the address of the last (highest) line touched by this object */
    Address endLine = Line.align(VM.objectModel.getObjectEndAddress(object).minus(1));
    Address line = Line.align(start.plus(BYTES_IN_LINE));
    while (line.LT(endLine)) {
      if (VM.VERIFY_ASSERTIONS)
        VM.assertions._assert(Block.align(start).EQ(Block.align(line)));
      mark(line, markValue);
      line = line.plus(BYTES_IN_LINE);
    }
  }

  /***************************************************************************
   * Scanning through avail lines
   */

  /**
   *
   */
  public static Address getChunkMarkTable(Address chunk) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Chunk.isAligned(chunk));
    return getMarkAddress(chunk);
  }

  public static Address getBlockMarkTable(Address block) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Block.isAligned(block));
    return getMarkAddress(block);
  }

  @Inline
  public static int getNextUnavailable(Address baseLineAvailAddress, int line, final byte unavailableState) {
    while (line < LINES_IN_BLOCK &&
        baseLineAvailAddress.loadByte(Offset.fromIntZeroExtend(line<<Line.LOG_BYTES_IN_LINE_STATUS)) < unavailableState)
      line++;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(line >= 0 && line <= LINES_IN_BLOCK);
    return line;
  }

  @Inline
  public static int getNextAvailable(Address baseLineAvailAddress, int line, final byte unavailableState) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(line >= 0 && line < LINES_IN_BLOCK);
    byte last = baseLineAvailAddress.loadByte(Offset.fromIntZeroExtend(line<<Line.LOG_BYTES_IN_LINE_STATUS));
    byte thisline;
    line++;
    while (line < LINES_IN_BLOCK) {
      thisline = baseLineAvailAddress.loadByte(Offset.fromIntZeroExtend(line<<Line.LOG_BYTES_IN_LINE_STATUS));
      if (thisline < unavailableState && last < unavailableState)
        break;
      last = thisline;
      line++;
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(line >= 0 && line <= LINES_IN_BLOCK);
    return line;
  }

  private static Address getMetaAddress(Address address, final int tableOffset) {
    Address chunk = Chunk.align(address);
    int index = getChunkIndex(address);
    Address rtn = chunk.plus(tableOffset + (index<<LOG_BYTES_IN_LINE_STATUS));
    if (VM.VERIFY_ASSERTIONS) {
      Address line = chunk.plus(index<<LOG_BYTES_IN_LINE);
      VM.assertions._assert(isAligned(line));
      VM.assertions._assert(align(address).EQ(line));
      boolean valid = rtn.GE(chunk.plus(tableOffset)) && rtn.LT(chunk.plus(tableOffset + LINE_MARK_TABLE_BYTES));
      VM.assertions._assert(valid);
    }
    return rtn;
  }

  private static Address getMarkAddress(Address address) {
    return getMetaAddress(address, Chunk.LINE_MARK_TABLE_OFFSET);
  }

  /* per-line mark bytes */

  static final int LOG_BYTES_IN_LINE_STATUS = 0;
  static final int BYTES_IN_LINE_STATUS = 1<<LOG_BYTES_IN_LINE_STATUS;

  static final int LINE_MARK_TABLE_BYTES = LINES_IN_CHUNK<<LOG_BYTES_IN_LINE_STATUS;
  static final int LOG_LINE_MARK_BYTES_PER_BLOCK = LOG_LINES_IN_BLOCK+LOG_BYTES_IN_LINE_STATUS;
  static final int LINE_MARK_BYTES_PER_BLOCK = (1<<LOG_LINE_MARK_BYTES_PER_BLOCK);
}
