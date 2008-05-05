/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.policy.immix;

import static org.mmtk.policy.immix.ImmixConstants.BYTES_IN_LINE;
import static org.mmtk.policy.immix.ImmixConstants.CHUNK_MASK;
import static org.mmtk.policy.immix.ImmixConstants.LINES_IN_BLOCK;
import static org.mmtk.policy.immix.ImmixConstants.LINES_IN_CHUNK;
import static org.mmtk.policy.immix.ImmixConstants.LINE_MASK;
import static org.mmtk.policy.immix.ImmixConstants.LOG_BYTES_IN_LINE;
import static org.mmtk.policy.immix.ImmixConstants.LOG_LINES_IN_BLOCK;
import static org.mmtk.policy.immix.ImmixConstants.TMP_EXACT_LINE_MARKS;
import static org.mmtk.policy.immix.ImmixConstants.TMP_USE_LINE_MARKS;

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
  static void mark(Address address) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!Block.isUnused(Block.align(address)));
    getMarkAddress(address).store(LINE_MARK_VALUE);
  }

  static void markMultiLine(Address start, ObjectReference object) {
    /* endLine is the address of the last (highest) line touched by this object */
    Address endLine = Line.align(VM.objectModel.getObjectEndAddress(object).minus(1));
    Address line = Line.align(start.plus(BYTES_IN_LINE));
    /* we only record spill into the last line if we're exact (otherwise we're conservative) */
    if (TMP_EXACT_LINE_MARKS)
      endLine = endLine.plus(BYTES_IN_LINE);
    while (line.LT(endLine)) {
      if (VM.VERIFY_ASSERTIONS)
        VM.assertions._assert(Block.align(start) == Block.align(line));
      mark(line);
      line = line.plus(BYTES_IN_LINE);
    }
  }

  /***************************************************************************
   * Scanning through line marks
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
  public static int getNextUsed(Address baseLineMarkAddress, int line) {
    return getNext(baseLineMarkAddress, line, LINE_MARK_VALUE);
  }

  @Inline
  public static int getNextUnused(Address baseLineMarkAddress, int line) {
    if (TMP_EXACT_LINE_MARKS)
      return getNext(baseLineMarkAddress, line, LINE_UNMARKED_VALUE);
    else
      return getNextDoubleLine(baseLineMarkAddress, line, LINE_UNMARKED_VALUE);
  }

  @Inline
  private static int getNext(Address baseLineMarkAddress, int line, final byte test) {
    while (line < LINES_IN_BLOCK &&
          baseLineMarkAddress.loadByte(Offset.fromIntZeroExtend(line<<Line.LOG_BYTES_IN_LINE_MARK)) != test)
      line++;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(line >= 0 && line <= LINES_IN_BLOCK);
    return line;
  }

  @Inline
  private static int getNextDoubleLine(Address baseLineMarkAddress, int line, final byte test) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(line >= 0 && line < LINES_IN_BLOCK);
    byte last = baseLineMarkAddress.loadByte(Offset.fromIntZeroExtend(line<<Line.LOG_BYTES_IN_LINE_MARK));
    byte thisline;
    line++;
    while (line < LINES_IN_BLOCK) {
      thisline = baseLineMarkAddress.loadByte(Offset.fromIntZeroExtend(line<<Line.LOG_BYTES_IN_LINE_MARK));
      if (thisline == test && last == test)
        break;
      last = thisline;
      line++;
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(line >= 0 && line <= LINES_IN_BLOCK);
    return line;
  }

  private static Address getMarkAddress(Address address) {
    Address chunk = Chunk.align(address);
    int index = getChunkIndex(address);
    Address rtn = chunk.plus(Chunk.LINE_MARK_TABLE_OFFSET).plus(index<<LOG_BYTES_IN_LINE_MARK);
    if (VM.VERIFY_ASSERTIONS) {
      Address line = chunk.plus(index<<LOG_BYTES_IN_LINE);
      VM.assertions._assert(isAligned(line));
      VM.assertions._assert(align(address).EQ(line));
      boolean valid = rtn.GE(chunk.plus(Chunk.LINE_MARK_TABLE_OFFSET)) && rtn.LT(chunk.plus(Chunk.LINE_MARK_TABLE_OFFSET+Line.LINE_MARK_TABLE_BYTES));
      VM.assertions._assert(valid);
    }
    return rtn;
  }

  /* per-line mark bytes */
  private static final byte LINE_MARK_VALUE = 1;
          static final byte LINE_UNMARKED_VALUE = 0;

  static final int LOG_BYTES_IN_LINE_MARK = 0;
  static final int LINE_MARK_TABLE_BYTES = TMP_USE_LINE_MARKS ? LINES_IN_CHUNK<<LOG_BYTES_IN_LINE_MARK : 0;
  static final int LOG_LINE_MARK_BYTES_PER_BLOCK = TMP_USE_LINE_MARKS ? LOG_LINES_IN_BLOCK+LOG_BYTES_IN_LINE_MARK : 0;
  static final int LINE_MARK_BYTES_PER_BLOCK = TMP_USE_LINE_MARKS ? (1<<LOG_LINE_MARK_BYTES_PER_BLOCK) : 0;
}
