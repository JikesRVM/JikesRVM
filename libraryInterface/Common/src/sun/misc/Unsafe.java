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
import java.security.ProtectionDomain;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.objectmodel.ObjectModel;
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

  public static final int INVALID_FIELD_OFFSET = -1;

  private Unsafe() {}

  @Inline
  public static Unsafe getUnsafe() {
    SecurityManager sm = System.getSecurityManager();
    if (sm != null)
      sm.checkPropertiesAccess();
    return theUnsafe;
  }

  // Manual memory management

  @Inline
  public long allocateMemory(long bytes) {
    // TODO sysMalloc needs to be changed to Extent because the C
    // prototype uses size_t
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

  // Access to underlying platform

  @Inline
  public int pageSize() {
    return Memory.getPagesize();
  }

  // Reflection

  /**
   * Ensure the given class has been initialized. This is often
   * needed in conjunction with obtaining the static field base of a
   * class.
   */
  @Inline
  public void ensureClassInitialized(Class<?> c){
    RVMType type = JikesRVMSupport.getTypeForClass(c);
    if (!type.isInitialized()) {
      if (type.isClassType()) {
        RuntimeEntrypoints.initializeClassForDynamicLink(type.asClass());
      } else {
        // TODO these 3 methods appear a few times in the code base together.
        // Consider refactoring this
        type.resolve();
        type.instantiate();
        type.initialize();
      }
    }
  }

  @Inline
  public Class<?> defineClass(String name, byte[] bytes, int off, int len, final ClassLoader parentClassLoader, ProtectionDomain protectionDomain) {
    if (parentClassLoader != null) {
      return RVMClassLoader.defineClassInternal(name, bytes, off,len, parentClassLoader).getClassForType();
    } else{
      ClassLoader callingClassloader = null;
//      ClassLoader callingClassloader = VMStackWalker.getCallingClassLoader();
      VM.sysFail("Implement me with org.jikesrvm.runtime.StackBrowser or move code "+
          "from VMStackWalker.getCallingClassLoader() to a place that's not in the GNU " +
          "Classpath namespace");
      return RVMClassLoader.defineClassInternal(name, bytes, off,len, callingClassloader).getClassForType();
    }
  }

  @Inline
  public Class<?> defineClass(String name, byte[] bytes, int off, int len) {
    ClassLoader callingClassloader = null;
//  ClassLoader callingClassloader = VMStackWalker.getCallingClassLoader();
    VM.sysFail("Implement me with org.jikesrvm.runtime.StackBrowser or move code "+
      "from VMStackWalker.getCallingClassLoader() to a place that's not in the GNU " +
      "Classpath namespace");
    return RVMClassLoader.defineClassInternal(name, bytes, off,len, callingClassloader).getClassForType();
  }

  @Inline
  public long objectFieldOffset(Field field) {
    RVMField vmfield = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    return vmfield.getOffset().toLong();
  }

  @Inline
  public int arrayBaseOffset(Class<?> arrayClass) {
    return ObjectModel.getArrayBaseOffset().toInt();
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
  public void throwException(Throwable ex) {
    RuntimeEntrypoints.athrow(ex);
  }

  // Direct memory access

  @Inline
  public void setMemory(long address, long bytes, byte value) {
    for (long i = 0; i < bytes; i++) {
      Address.fromLong(address + i).store(value);
    }
  }

  @Inline
  public void copyMemory(long srcAddress, long destAddress, long bytes) {
    Memory.memcopy(Address.fromLong(destAddress), Address.fromLong(srcAddress), Offset.fromLong(bytes).toWord().toExtent());
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

  // Synchronization primitives

  @Inline
  public boolean compareAndSwapInt(Object obj, long offset, int expect, int update) {
    Offset off = Offset.fromLong(offset);
    return Synchronization.tryCompareAndSwap(obj, off, expect, update);
  }

  @Inline
  public boolean compareAndSwapLong(Object obj, long offset, long expect, long update) {
    Offset off = Offset.fromLong(offset);
    return Synchronization.tryCompareAndSwap(obj, off, expect, update);
  }

  // Reading and writing of Java data types

  @Inline
  public boolean compareAndSwapObject(Object obj, long offset, Object expect, Object update) {
    Offset off = Offset.fromLong(offset);
    return Synchronization.tryCompareAndSwap(obj, off, expect, update);
  }

  @Inline
  public void putOrderedInt(Object obj, long offset, int value) {
    Offset off = Offset.fromLong(offset);
    Magic.storeStoreBarrier();
    if (NEEDS_INT_PUTFIELD_BARRIER) {
      intFieldWrite(obj, value, off, 0);
    } else {
      Magic.setIntAtOffset(obj, off, value);
    }
  }

  @Inline
  public void putOrderedLong(Object obj, long offset, long value) {
    Offset off = Offset.fromLong(offset);
    Magic.storeStoreBarrier();
    if (NEEDS_LONG_PUTFIELD_BARRIER) {
      longFieldWrite(obj, value, off, 0);
    } else {
      Magic.setLongAtOffset(obj, off, value);
    }
  }

  @Inline
  public void putOrderedObject(Object obj, long offset, Object value) {
    Offset off = Offset.fromLong(offset);
    Magic.storeStoreBarrier();
    if (NEEDS_OBJECT_PUTFIELD_BARRIER) {
      objectFieldWrite(obj, value, off, 0);
    } else {
      Magic.setObjectAtOffset(obj, off, value);
    }
  }

  @Inline
  public boolean getBoolean(long address) {
    return Address.fromLong(address).loadByte() == 0;
  }

  @Inline
  public boolean getBoolean(Object obj, int offset) {
    return Magic.getByteAtOffset(obj, Offset.fromIntSignExtend(offset)) == 0;
  }

  @Inline
  public boolean getBoolean(Object obj, long offset) {
    return Magic.getByteAtOffset(obj, Offset.fromLong(offset)) == 0;
  }

  @Inline
  public void putBoolean(Object obj, long offset, boolean value) {
    Magic.setBooleanAtOffset(obj, Offset.fromLong(offset), value);
  }

  @Inline
  public void putBoolean(long address, boolean x) {
    Address.fromLong(address).store(x) ;
  }

  @Inline
  public byte getByte(Object obj, int offset) {
    Offset off = Offset.fromIntSignExtend(offset);
    return Magic.getByteAtOffset(obj,off);
  }

  @Inline
  public byte getByte(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    return Magic.getByteAtOffset(obj,off);
  }

  @Inline
  public byte getByte(long address) {
    return Address.fromLong(address).loadByte();
  }

  @Inline
  public void putByte(Object obj,long offset, byte value) {
    Magic.setByteAtOffset(obj, Offset.fromLong(offset), value);
  }

  @Inline
  public void putByte(long address, byte x) {
    Address.fromLong(address).store(x);
  }

  @Inline
  public char getChar(Object obj, long offset) {
    return Magic.getCharAtOffset(obj, Offset.fromLong(offset));
  }

  @Inline
  public char getChar(long address) {
    return Address.fromLong(address).loadChar();
  }

  @Inline
  public void putChar(Object obj,long offset,char value) {
    Magic.setCharAtOffset(obj, Offset.fromLong(offset), value);
  }

  @Inline
  public void putChar(long address, char x) {
    Address.fromLong(address).store(x);
  }

  @Inline
  public double getDouble(long address) {
    return Address.fromLong(address).loadDouble();
  }

  @Inline
  public double getDouble(Object obj, int offset) {
    return Magic.getDoubleAtOffset(obj, Offset.fromIntSignExtend(offset));
  }

  @Inline
  public double getDouble(Object obj, long offset) {
    return Magic.getDoubleAtOffset(obj, Offset.fromLong(offset));
  }

  @Inline
  public void putDouble(Object obj,long offset,double value) {
    Magic.setDoubleAtOffset(obj, Offset.fromLong(offset), value);
  }

  @Inline
  public void putDouble(long address, double x) {
    Address.fromLong(address).store(x) ;
  }

  @Inline
  public float getFloat(long address) {
    return Address.fromLong(address).loadFloat();
  }

  @Inline
  public float getFloat(Object obj, int offset) {
    return Magic.getFloatAtOffset(obj, Offset.fromIntSignExtend(offset));
  }

  @Inline
  public float getFloat(Object obj, long offset) {
    return Magic.getFloatAtOffset(obj, Offset.fromLong(offset));
  }

  @Inline
  public void putFloat(Object obj, long offset, float value) {
    Magic.setFloatAtOffset(obj, Offset.fromLong(offset), value);
  }

  @Inline
  public void putFloat(long address, float x){
    Address.fromLong(address).store(x) ;
  }

  @Inline
  public int getInt(long address) {
    return Address.fromLong(address).loadInt();
  }

  @Inline
  public int getInt(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    return Magic.getIntAtOffset(obj,off);
  }

  @Inline
  public int getIntVolatile(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    int result = Magic.getIntAtOffset(obj,off);
    Magic.combinedLoadBarrier();
    return result;
  }

  @Inline
  public void putInt(long address, int x) {
    Address.fromLong(address).store(x);
  }

  @Inline
  public void putInt(Object obj, long offset, int value) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_INT_PUTFIELD_BARRIER) {
      intFieldWrite(obj, value, off, 0);
    } else {
      Magic.setIntAtOffset(obj,off,value);
    }
  }

  @Inline
  public void putIntVolatile(Object obj, long offset, int value) {
    Magic.storeStoreBarrier();
    Offset off = Offset.fromLong(offset);
    if (NEEDS_INT_PUTFIELD_BARRIER) {
      intFieldWrite(obj, value, off, 0);
    } else {
      Magic.setIntAtOffset(obj,off,value);
    }
    Magic.fence();
  }

  @Inline
  public short getShort(long address) {
    return Address.fromLong(address).loadShort();
  }

  @Inline
  public void putShort(Object obj, long offset, short value) {
    Magic.setShortAtOffset(obj, Offset.fromLong(offset), value);
  }

  @Inline
  public void putShort(long address, short x) {
    Address.fromLong(address).store(x);
  }

  @Inline
  public void putShort(long address, Long x) {
    Address.fromLong(address).store(x);
  }

  @Inline
  public long getLong(long address) {
    return Address.fromLong(address).loadLong();
  }

  @Inline
  public long getLong(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    return Magic.getLongAtOffset(obj,off);
  }

  @Inline
  public long getLongVolatile(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    long result = Magic.getLongAtOffset(obj,off);
    Magic.combinedLoadBarrier();
    return result;
  }

  @Inline
  public void putLong(long address, long x) {
    Address.fromLong(address).store(x);
  }

  @Inline
  public void putLong(Object obj, long offset, long value) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_LONG_PUTFIELD_BARRIER) {
      longFieldWrite(obj, value, off, 0);
    } else {
      Magic.setLongAtOffset(obj,off,value);
    }
  }

  @Inline
  public void putLongVolatile(Object obj, long offset, long value) {
    Magic.storeStoreBarrier();
    Offset off = Offset.fromLong(offset);
    if (NEEDS_LONG_PUTFIELD_BARRIER) {
      longFieldWrite(obj, value, off, 0);
    } else {
      Magic.setLongAtOffset(obj,off,value);
    }
    Magic.fence();
  }

  @Inline
  public Object getObject(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    Object result = Magic.getObjectAtOffset(obj,off);
    return result;
  }

  @Inline
  public Object getObjectVolatile(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    Object result = Magic.getObjectAtOffset(obj,off);
    Magic.combinedLoadBarrier();
    return result;
  }

  @Inline
  public void putObject(Object obj, long offset, Object value) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_OBJECT_PUTFIELD_BARRIER) {
      objectFieldWrite(obj, value, off, 0);
    } else {
      Magic.setObjectAtOffset(obj,off,value);
    }
  }

  @Inline
  public void putObjectVolatile(Object obj, long offset, Object value) {
    Offset off = Offset.fromLong(offset);
    Magic.storeStoreBarrier();
    if (NEEDS_OBJECT_PUTFIELD_BARRIER) {
      objectFieldWrite(obj, value, off, 0);
    } else {
      Magic.setObjectAtOffset(obj,off,value);
    }
    Magic.fence();
  }

  // Memory Barriers

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
