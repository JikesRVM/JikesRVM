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
package sun.misc;

import java.lang.reflect.Field;

import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.scheduler.Synchronization;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Address;

import static org.jikesrvm.mm.mminterface.Barriers.*;
import org.jikesrvm.runtime.SysCall;

/**
 * Our implementation of sun.misc.Unsafe maps the operations to
 * the Jikes RVM compiler intrinsics and its runtime service methods.
 * <p>
 * The OpenJDK class libraries (and normal programs using sun.misc.Unsafe)
 * may expect that the methods in this class are compiler intrinsics (because
 * that's the case for HotSpot). Therefore, force inlining of all methods
 * where performance might matter.
 */
public final class Unsafe {

  /** use name that Doug Lea's Fork-Join framework appears to expect from class libs */
  private static final Unsafe theUnsafe = new Unsafe();

  private Unsafe() {}

  @Inline
  public static Unsafe getUnsafe() {
    SecurityManager sm = System.getSecurityManager();
    if (sm != null)
      sm.checkPropertiesAccess();
    return theUnsafe;
  }

  @Inline
  private Offset longToOffset(long offset) {
    return Offset.fromIntSignExtend((int)offset);
  }

  @Inline
  public long objectFieldOffset(Field field) {
    RVMField vmfield = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    return vmfield.getOffset().toLong();
  }

  @Inline
  public boolean compareAndSwapInt(Object obj,long offset,int expect,int update) {
    Offset off = longToOffset(offset);
    return Synchronization.tryCompareAndSwap(obj, off, expect, update);
  }

  @Inline
  public boolean compareAndSwapLong(Object obj,long offset,long expect,long update) {
    Offset off = Offset.fromIntSignExtend((int)offset);
    return Synchronization.tryCompareAndSwap(obj, off, expect, update);
  }

  @Inline
  public boolean compareAndSwapObject(Object obj,long offset,Object expect,Object update) {
    Offset off = Offset.fromIntSignExtend((int)offset);
    return Synchronization.tryCompareAndSwap(obj, off, expect, update);
  }

  @Inline
  public void putOrderedInt(Object obj,long offset,int value) {
    Offset off = longToOffset(offset);
    Magic.storeStoreBarrier();
    if (NEEDS_INT_PUTFIELD_BARRIER) {
      intFieldWrite(obj, value, off, 0);
    } else {
      Magic.setIntAtOffset(obj, off, value);
    }
  }

  @Inline
  public void putOrderedLong(Object obj,long offset,long value) {
    Offset off = longToOffset(offset);
    Magic.storeStoreBarrier();
    if (NEEDS_LONG_PUTFIELD_BARRIER) {
      longFieldWrite(obj, value, off, 0);
    } else {
      Magic.setLongAtOffset(obj, off, value);
    }
  }

  @Inline
  public void putOrderedObject(Object obj,long offset,Object value) {
    Offset off = longToOffset(offset);
    Magic.storeStoreBarrier();
    if (NEEDS_OBJECT_PUTFIELD_BARRIER) {
      objectFieldWrite(obj, value, off, 0);
    } else {
      Magic.setObjectAtOffset(obj, off, value);
    }
   }

  @Inline
  public byte getByte(Object obj, int offset) {
    Offset off = longToOffset(offset);
    return Magic.getByteAtOffset(obj,off);
  }

  @Inline
  public byte getByte(Object obj, long offset) {
    Offset off = longToOffset(offset);
    return Magic.getByteAtOffset(obj,off);
  }

  @Inline
  public byte getByte(long address) {
    return Address.fromLong(address).loadByte();
  }

  @Inline
  public void putByte(long address, byte x) {
    Address.fromLong(address).store(x);
  }

  @Inline
  public char getChar(long address) {
    return Address.fromLong(address).loadChar();
  }

  @Inline
  public void putChar(long address, char x) {
    Address.fromLong(address).store(x);
  }

  @Inline
  public short getShort(long address) {
    return Address.fromLong(address).loadShort();
  }

  @Inline
  public void putShort(long address, short x) {
    Address.fromLong(address).store(x);
  }

  @Inline
  public int getInt(Object obj,long offset) {
    Offset off = longToOffset(offset);
    return Magic.getIntAtOffset(obj,off);
  }

  @Inline
  public int getIntVolatile(Object obj,long offset) {
    Offset off = longToOffset(offset);
    int result = Magic.getIntAtOffset(obj,off);
    Magic.combinedLoadBarrier();
    return result;
  }

  @Inline
  public void putIntVolatile(Object obj,long offset,int value) {
    Magic.storeStoreBarrier();
    Offset off = longToOffset(offset);
    if (NEEDS_INT_PUTFIELD_BARRIER) {
      intFieldWrite(obj, value, off, 0);
    } else {
      Magic.setIntAtOffset(obj,off,value);
    }
    Magic.fence();
  }

  @Inline
  public void putInt(Object obj,long offset,int value) {
    Offset off = longToOffset(offset);
    if (NEEDS_INT_PUTFIELD_BARRIER) {
      intFieldWrite(obj, value, off, 0);
    } else {
      Magic.setIntAtOffset(obj,off,value);
    }
  }

  @Inline
  public  int getInt(long address) {
    return Address.fromLong(address).loadInt();
  }

  @Inline
  public void putInt(long address, int x) {
    Address.fromLong(address).store(x);
  }

  @Inline
  public long getLong(long address) {
    return Address.fromLong(address).loadLong();
  }

  @Inline
  public void putShort(long address, Long x) {
    Address.fromLong(address).store(x);
  }

  @Inline
  public int pageSize() {
    return Memory.getPagesize();
  }

  @Inline
  public void putLongVolatile(Object obj,long offset,long value) {
    Magic.storeStoreBarrier();
    Offset off = longToOffset(offset);
    if (NEEDS_LONG_PUTFIELD_BARRIER) {
      longFieldWrite(obj, value, off, 0);
    } else {
      Magic.setLongAtOffset(obj,off,value);
    }
    Magic.fence();
  }

  @Inline
  public void putLong(Object obj,long offset,long value) {
    Offset off = longToOffset(offset);
    if (NEEDS_LONG_PUTFIELD_BARRIER) {
      longFieldWrite(obj, value, off, 0);
    } else {
      Magic.setLongAtOffset(obj,off,value);
    }
  }

  @Inline
  public void putLong(long address, long x) {
    Address.fromLong(address).store(x);
  }

  @Inline
  public long getLongVolatile(Object obj,long offset) {
    Offset off = longToOffset(offset);
    long result = Magic.getLongAtOffset(obj,off);
    Magic.combinedLoadBarrier();
    return result;
  }

  @Inline
  public long getLong(Object obj,long offset) {
    Offset off = longToOffset(offset);
    return Magic.getLongAtOffset(obj,off);
  }

  @Inline
  public long allocateMemory(long bytes) {
    Address result = SysCall.sysCall.sysMalloc((int)bytes);
    if (result.isZero()) {
      throw new OutOfMemoryError("Unable to satisfy malloc of " + bytes);
    }
    return result.toLong();
  }

  @Inline
  public void freeMemory(long address) {
    SysCall.sysCall.sysFree(Address.fromLong(address));
  }

  @Inline
  public void putObjectVolatile(Object obj,long offset,Object value) {
    Offset off = longToOffset(offset);
    Magic.storeStoreBarrier();
    if (NEEDS_OBJECT_PUTFIELD_BARRIER) {
      objectFieldWrite(obj, value, off, 0);
    } else {
      Magic.setObjectAtOffset(obj,off,value);
    }
    Magic.fence();
  }

  @Inline
  public void putObject(Object obj,long offset,Object value) {
    Offset off = longToOffset(offset);
    if (NEEDS_OBJECT_PUTFIELD_BARRIER) {
      objectFieldWrite(obj, value, off, 0);
    } else {
      Magic.setObjectAtOffset(obj,off,value);
    }
  }

  @Inline
  public Object getObject(Object obj, long offset) {
    Offset off = longToOffset(offset);
    Object result = Magic.getObjectAtOffset(obj,off);
    return result;
  }

  @Inline
  public Object getObjectVolatile(Object obj,long offset) {
    Offset off = longToOffset(offset);
    Object result = Magic.getObjectAtOffset(obj,off);
    Magic.combinedLoadBarrier();
    return result;
  }

  @Inline
  public int arrayBaseOffset(Class<?> arrayClass) {
    // TODO should we get this via ObjectModel?
    return 0;
  }

  /**
   * Ensure the given class has been initialized. This is often
   * needed in conjunction with obtaining the static field base of a
   * class.
   */
  public void ensureClassInitialized(Class<?> c){
    // TODO implement
  }

  @Inline
  public int arrayIndexScale(Class<?> arrayClass) {
    RVMType arrayType = java.lang.JikesRVMSupport.getTypeForClass(arrayClass);
    if (!arrayType.isArrayType()) {
      return 0;
    } else {
      return 1 << arrayType.asArray().getLogElementSize();
    }
  }

  @Inline
  public void unpark(Object thread) {
    RVMThread vmthread = java.lang.JikesRVMSupport.getThread((Thread)thread);
    if (vmthread != null) {
      vmthread.unpark();
    }
  }

  @Inline
  public void park(boolean isAbsolute,long time) throws Throwable  {
    RVMThread vmthread = java.lang.JikesRVMSupport.getThread(Thread.currentThread());
    if (vmthread != null) {
      vmthread.park(isAbsolute, time);
    }
  }

  public void throwException(Throwable ex) {
    RuntimeEntrypoints.athrow(ex);
  }

  @Inline
  public void setMemory(long address, long bytes, byte value) {
    for (long i = 0; i < bytes; i++) {
      Address.fromLong(address + i).store(value);
    }
  }

  @Inline
  public void copyMemory(long srcAddress, long destAddress, long bytes) {
    Memory.memcopy(Address.fromLong(destAddress), Address.fromLong(srcAddress), (int)bytes);
  }

  @Inline
  public void loadFence() {
    Magic.combinedLoadBarrier();
  }

  @Inline
  public void storeFence() {
    Magic.fence();
  }

  @Inline
  public void fullFence() {
    Magic.fence();
  }

}
