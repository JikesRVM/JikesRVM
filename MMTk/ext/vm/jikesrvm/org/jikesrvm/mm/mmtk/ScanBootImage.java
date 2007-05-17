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
package org.jikesrvm.mm.mmtk;

import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;
import org.jikesrvm.VM;
import org.jikesrvm.runtime.VM_BootRecord;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Thread;
import org.jikesrvm.memorymanagers.mminterface.VM_CollectorThread;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Scan the boot image for references using the boot image reference map
 */
public class ScanBootImage implements Constants {
  
  private static final boolean DEBUG = false;
  private static final boolean FILTER = true;
  
  private static final int LOG_CHUNK_BYTES = 10;
  private static final int CHUNK_BYTES = 1<<LOG_CHUNK_BYTES;
  private static final int LONG_MASK = 0x1;
  private static final int RUN_MASK = 0x2;
  private static final int MAX_RUN = (1<<BITS_IN_BYTE)-1;
  private static final int LONG_OFFSET_BYTES = 4;
  private static final int GUARD_REGION = LONG_OFFSET_BYTES + 1; /* long offset + run encoding */
  
  /* statistics */
  static int roots = 0;
  static int refs = 0;
  
  /****************************************************************************
   *
   * GC-time decoding (multi-threaded)
   */
  
  /**
   * Scan the boot image for object references.  Executed by
   * all GC threads in parallel, with each doing a portion of the
   * boot image.
   * 
   * @param trace The trace object to which the roots should be added
   */
  @Inline
  @Uninterruptible
  public static void scanBootImage(TraceLocal trace) { 
    /* establish sentinals in map & image */
    Address mapStart = VM_BootRecord.the_boot_record.bootImageRMapStart;
    Address mapEnd = VM_BootRecord.the_boot_record.bootImageRMapEnd;
    Address imageStart = VM_BootRecord.the_boot_record.bootImageDataStart;
    
    /* figure out striding */
    int stride = VM_CollectorThread.numCollectors()<<LOG_CHUNK_BYTES;
    VM_CollectorThread collector = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    int start = (collector.getGCOrdinal() - 1)<<LOG_CHUNK_BYTES;
    Address cursor = mapStart.plus(start);
    
    /* statistics */
    roots = 0;
    refs = 0;
    
    /* process chunks in parallel till done */
    while (cursor.LT(mapEnd)) {
      processChunk(cursor, imageStart, mapStart, mapEnd, trace);
      cursor = cursor.plus(stride);
    }
    
    /* print some debugging stats */
    if (DEBUG) {
      Log.write("<boot image");
      Log.write(" roots: "); Log.write(roots);
      Log.write(" refs: "); Log.write(refs);
      Log.write(">");
    }
    
    /* sync up all collection threads */
    VM_CollectorThread.gcBarrier.rendezvous(4300);
  }
  
  /**
   * Process a chunk of encoded reference data, enqueuing each
   * reference (optionally filtering them on whether they point
   * outside the boot image).
   *
   * @param chunkStart The address of the first byte of encoded data
   * @param imageStart The address of the start of the boot image
   * @param mapStart The address of the start of the encoded reference map
   * @param mapEnd The address of the end of the encoded reference map
   * @param trace The <code>TraceLocal</code> into which roots should
   * be enqueued.
   */
  @Inline
  @Uninterruptible
  private static void processChunk(Address chunkStart, Address imageStart,
      Address mapStart, Address mapEnd, TraceLocal trace) { 
    int value;
    Offset offset = Offset.zero();
    Address cursor = chunkStart;
    while ((value = (cursor.loadByte() & 0xff)) != 0) {
      /* establish the offset */
      if ((value & LONG_MASK) != 0) {
        offset = decodeWord(cursor);
        cursor = cursor.plus(LONG_OFFSET_BYTES);
      } else {
        offset = offset.plus(value & 0xfc);
        cursor = cursor.plus(1);
      }
      /* figure out the length of the run, if any */
      int runlength = 0;
      if ((value & RUN_MASK) != 0) {
        runlength = cursor.loadByte() & 0xff;
        cursor = cursor.plus(1);
      }
      /* enqueue the specified slot or slots */
      Address slot = imageStart.plus(offset);
      if (DEBUG) refs++;
      if (!FILTER || slot.loadAddress().GT(mapEnd)) {
        if (DEBUG) roots++;
        trace.addRootLocation(slot);
      }
      if (runlength != 0) {
        for (int i = 0; i < runlength; i++) {
          offset = offset.plus(BYTES_IN_ADDRESS);
          slot = imageStart.plus(offset);
          if (DEBUG) refs++;
          if (!FILTER || slot.loadAddress().GT(mapEnd)) {
            if (DEBUG) roots++;
            trace.addRootLocation(slot);
          }
        }
      }
    }
  }
  
  /****************************************************************************
   *
   * Build-time encoding (assumed to be single-threaded)
   */
  private static int lastOffset = 0;
  private static int oldIndex = 0;
  private static int codeIndex = 0;
  
  /* statistics */
  private static int shortRefs = 0;
  private static int runRefs = 0;
  private static int longRefs = 0;
  private static int startRefs = 0;
  
  /**
   * Take a bytemap encoding of all references in the boot image, and
   * produce an encoded byte array.  Return the total length of the
   * encoding.
   */
  public static int encodeRMap(byte[] bootImageRMap, byte[] referenceMap, 
      int referenceMapLimit) {
    for (int index = 0; index <= referenceMapLimit; index++) {
      if (referenceMap[index] == 1) {
        addOffset(bootImageRMap, index<<LOG_BYTES_IN_ADDRESS);
      }
    }
    return codeIndex + 1;
  }
  
  /**
   * Print some basic statistics about the encoded references, for
   * debugging purposes.
   */
  public static void encodingStats() {
    if (DEBUG) {
      Log.write("refs: "); Log.writeln(startRefs + shortRefs + longRefs + runRefs);
      Log.write("start: "); Log.writeln(startRefs);
      Log.write("short: "); Log.writeln(shortRefs);
      Log.write("long: "); Log.writeln(longRefs);
      Log.write("run: "); Log.writeln(runRefs);
      Log.write("size: "); Log.writeln(codeIndex);
    }
  }
  
  /**
   * Encode a given offset (distance from the start of the boot image)
   * into the code array.
   *
   * @param code A byte array into which the value should be encoded
   * @param offset The offset value to be encoded
   */
  private static void addOffset(byte[] code, int offset) {
    if ((codeIndex ^ (codeIndex + GUARD_REGION)) >= CHUNK_BYTES) {
      codeIndex = (codeIndex + GUARD_REGION) & ~(CHUNK_BYTES - 1);
      oldIndex = codeIndex;
      codeIndex = encodeWord(code, codeIndex, offset);
      if (DEBUG) {
        startRefs++;
        Log.write("[chunk: "); Log.write(codeIndex);
        Log.write(" offset: "); Log.write(offset);
        Log.write(" last offset: "); Log.write(lastOffset);
        Log.writeln("]");
      }
    } else {
      int delta = offset - lastOffset;
      if (VM.VerifyAssertions) VM._assert((delta & 0x3) == 0);
      if (VM.VerifyAssertions) VM._assert(delta > 0);
      
      int currentrun = ((int) code[codeIndex]) & 0xff;
      if ((delta == BYTES_IN_ADDRESS) &&
          (currentrun < MAX_RUN)) {
        currentrun++;
        code[codeIndex] = (byte) (currentrun & 0xff);
        code[oldIndex] |= RUN_MASK;
        if (DEBUG) runRefs++;
      } else {
        if (currentrun != 0) codeIndex++;
        oldIndex = codeIndex;
        if (delta < 1<<BITS_IN_BYTE) {
          /* common case: single byte encoding */
          code[codeIndex++] = (byte) (delta & 0xff);
          if (DEBUG) shortRefs++;
        } else {
          /* else four byte encoding */
          codeIndex = encodeWord(code, codeIndex, offset);
          if (DEBUG) longRefs++;
        }
      }
    }
    if (offset != getOffset(code, oldIndex, lastOffset)) {
      Log.write("offset: "); Log.writeln(offset);
      Log.write("last offset: "); Log.writeln(lastOffset);
      Log.write("offset: "); Log.writeln(getOffset(code, oldIndex, lastOffset));
      Log.write("index: "); Log.writeln(oldIndex);
      Log.write("index: "); Log.writeln(oldIndex & (CHUNK_BYTES - 1));
      Log.writeln();
      Log.write("1: "); Log.writeln(code[oldIndex]);
      Log.write("2: "); Log.writeln(code[oldIndex+1]);
      Log.write("3: "); Log.writeln(code[oldIndex+2]);
      Log.write("4: "); Log.writeln(code[oldIndex+3]);
      Log.write("5: "); Log.writeln(code[oldIndex+4]);
      if (VM.VerifyAssertions)
        VM._assert(offset == getOffset(code, oldIndex, lastOffset));
    }
    lastOffset = offset;
  }
  
  /****************************************************************************
   *
   * Utility encoding and decoding methods
   */
  
  /**
   * Decode an encoded offset given the coded byte array, and index
   * into it, and the current (last) offset.
   *
   * @param code A byte array containing the encoded value
   * @param index The offset into the code array from which to
   * commence decoding
   * @param lastOffset The current (last) encoded offset
   * @return The next offset, which is either explicitly encoded in
   * the byte array or inferred from an encoded delta and the last
   * offset.
 */
  private static int getOffset(byte[] code, int index, int lastOffset) {
    if (((int) code[index] & RUN_MASK) == RUN_MASK) {
      return lastOffset + BYTES_IN_WORD;
    } else {
      if (((index & (CHUNK_BYTES - 1)) == 0) ||
          (((int) code[index] &LONG_MASK) == LONG_MASK)) 
      {
        return decodeWord(code, index);
      } else {
        return lastOffset + (((int) code[index]) & 0xff);
      } 
    }
  }
  
  /**
   * Decode a 4-byte encoding, taking a pointer to the first byte of
   * the encoding, and returning the encoded value as an <code>Offset</code>
   *
   * @param cursor A pointer to the first byte of encoded data
   * @return The encoded value as an <code>Offset</code>
   */
  @Inline
  @Uninterruptible
  private static Offset decodeWord(Address cursor) { 
    int value;
    value  = ((int) cursor.loadByte())                                    & 0x000000fc;
    value |= ((int) cursor.loadByte(Offset.fromIntSignExtend(1))<<BITS_IN_BYTE)     & 0x0000ff00;
    value |= ((int) cursor.loadByte(Offset.fromIntSignExtend(2))<<(2*BITS_IN_BYTE)) & 0x00ff0000;
    value |= ((int) cursor.loadByte(Offset.fromIntSignExtend(3))<<(3*BITS_IN_BYTE)) & 0xff000000;
    return Offset.fromIntSignExtend(value);
  }
  
  /**
   * Decode a 4-byte encoding, taking a byte array and an index into
   * it and returning the encoded value as an integer
   *
   * @param code A byte array containing the encoded value
   * @param index The offset into the code array from which to
   * commence decoding
   * @return The encoded value as an integer
   */
  @Inline
  @Uninterruptible
  private static int decodeWord(byte[] code, int index) { 
    int value;
    value  = ((int) code[index])                     & 0x000000fc;
    value |= ((int) code[index+1]<<BITS_IN_BYTE)     & 0x0000ff00;
    value |= ((int) code[index+2]<<(2*BITS_IN_BYTE)) & 0x00ff0000;
    value |= ((int) code[index+3]<<(3*BITS_IN_BYTE)) & 0xff000000;
    return value;
  }
  
  /**
   * Encode a 4-byte encoding, taking a byte array, the current index into
   * it, and the value to be encoded.
   *
   * @param code A byte array to containthe encoded value
   * @param index The current offset into the code array
   * @param value The value to be encoded
   * @return The updated index into the code array
   */
  private static int encodeWord(byte[] code, int index, int value) {
    code[index++] = (byte) ((value & 0xff) | LONG_MASK);
    value = value >>> BITS_IN_BYTE;
    code[index++] = (byte) (value & 0xff);
    value = value >>> BITS_IN_BYTE;
    code[index++] = (byte) (value & 0xff);
    value = value >>> BITS_IN_BYTE;
    code[index++] = (byte) (value & 0xff);
    return index;
  }
  
}
