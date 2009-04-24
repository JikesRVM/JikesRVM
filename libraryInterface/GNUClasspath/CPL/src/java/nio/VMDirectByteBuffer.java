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
package java.nio;

import gnu.classpath.Pointer;
import org.jikesrvm.runtime.SysCall;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;
import static gnu.classpath.JikesRVMSupport.getAddressFromPointer;
import static gnu.classpath.JikesRVMSupport.getPointerFromAddress;

final class VMDirectByteBuffer {
  /** Malloc capacity bytes and ensure they're zeroed */
  static Pointer allocate(int capacity) {
    return getPointerFromAddress(SysCall.sysCall.sysCalloc(capacity));
  }
  /** Free memory previously allocated */
  static void free(Pointer address) {
    SysCall.sysCall.sysFree(getAddressFromPointer(address));
  }
  /** Read byte at index */
  static byte get(Pointer address, int index) {
    return getAddressFromPointer(address).loadByte(Offset.fromIntSignExtend(index));
  }
  /** Read bytes at index into dst */
  static void get(Pointer address, int index, byte[] dst, int offset, int length) {
    Address startAddress = getAddressFromPointer(address);
    for (int i=0; i<length; i++) {
      dst[offset+i] = startAddress.loadByte(Offset.fromIntSignExtend(index+i));
    }
  }
  /** Write byte at index */
  static void put(Pointer address, int index, byte value) {
    getAddressFromPointer(address).store(value, Offset.fromIntSignExtend(index));
  }
  /** Write bytes at offset in src into buffer */
  static void put(Pointer address, int index, byte[] src, int offset, int length) {
    Address startAddress = getAddressFromPointer(address);
    for (int i=0; i<length; i++) {
      startAddress.store(src[offset+i], Offset.fromIntSignExtend(index+i));
    }
  }
  /** Adjust pointer by offset */
  static Pointer adjustAddress(Pointer address, int offset) {
    return getPointerFromAddress(getAddressFromPointer(address).toWord().plus(Word.fromIntSignExtend(offset)).toAddress());
  }
  /** Copy region in buffer to another region */
  static void shiftDown(Pointer address, int dst_offset, int src_offset, int count) {
    Address startAddress = getAddressFromPointer(address);
    for (int i=0; i < count; i++) {
      startAddress.store(startAddress.loadByte(Offset.fromIntSignExtend(src_offset+i)), Offset.fromIntSignExtend(dst_offset+i));
    }
  }
}
