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
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.scheduler.Synchronization;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.Offset;

import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;

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
 * <p>
 * All put* and get* methods that have a parameter with {@code java.lang.Object}
 * perform barriers as if the value was written or read to a Java field of the
 * appropriate type. Methods without such a parameter are assumed to access native
 * memory and thus don't have barriers. Note that HotSpot's {@code sun.misc.Unsafe}
 * doesn't make any distinction between barriers for field accesses and those for
 * array accesses. The put* and get* methods in this class use barriers for field
 * accesses.
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
  public void ensureClassInitialized(Class<?> c) {
    RVMType type = JikesRVMSupport.getTypeForClass(c);
    if (!type.isInitialized()) {
      if (type.isClassType()) {
        RuntimeEntrypoints.initializeClassForDynamicLink(type.asClass());
      } else {
        type.prepareForFirstUse();
      }
    }
  }

  @Inline
  public Object allocateInstance(Class c) throws InstantiationException {
    ensureClassInitialized(c);
    RVMType t = JikesRVMSupport.getTypeForClass(c);
    if (t.isClassType()) {
      RVMClass rvmClass = t.asClass();
      return RuntimeEntrypoints.resolvedNewScalar(rvmClass);
    }
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  @Inline
  public Class<?> defineClass(String name, byte[] bytes, int off, int len, final ClassLoader parentClassLoader, ProtectionDomain protectionDomain) {
    if (parentClassLoader != null) {
      return RVMClassLoader.defineClassInternal(name, bytes, off,len, parentClassLoader).getClassForType();
    } else {
      ClassLoader callingClassloader = null;
//      ClassLoader callingClassloader = VMStackWalker.getCallingClassLoader();
      VM.sysFail("Implement me with org.jikesrvm.runtime.StackBrowser or move code " +
          "from VMStackWalker.getCallingClassLoader() to a place that's not in the GNU " +
          "Classpath namespace");
      return RVMClassLoader.defineClassInternal(name, bytes, off,len, callingClassloader).getClassForType();
    }
  }

  @Inline
  public Class<?> defineClass(String name, byte[] bytes, int off, int len) {
    ClassLoader callingClassloader = null;
//  ClassLoader callingClassloader = VMStackWalker.getCallingClassLoader();
    VM.sysFail("Implement me with org.jikesrvm.runtime.StackBrowser or move code " +
      "from VMStackWalker.getCallingClassLoader() to a place that's not in the GNU " +
      "Classpath namespace");
    return RVMClassLoader.defineClassInternal(name, bytes, off,len, callingClassloader).getClassForType();
  }

  @Inline
  public long objectFieldOffset(Field field) {
    RVMField vmfield = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    if (VM.VerifyAssertions) VM._assert(vmfield != null);
    return vmfield.getOffset().toLong();
  }

  @Inline
  public Object staticFieldBase(Field f) {
    return Statics.getSlots().toObjectReference().toObject();
  }

  @Inline
  public long staticFieldOffset(Field field) {
    RVMField vmfield = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    if (VM.VerifyAssertions) VM._assert(vmfield != null);
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

  // FIXME OPENJDK/ICEDTEA add atomic copying if size is aligned properly (i.e. 8 / 4 / 2 bytes).
  @Inline
  public void copyMemory(long srcAddress, long destAddress, long bytes) {
    Memory.memcopy(Address.fromLong(destAddress), Address.fromLong(srcAddress), Offset.fromLong(bytes).toWord().toExtent());
  }

  // FIXME OPENJDK/ICEDTEA add atomic copying if size is aligned properly (i.e. 8 / 4 / 2 bytes).
  @Inline
  public void copyMemory(Object srcBase, long srcOffset, Object dstBase, long dstOffset, long bytes) {
    Address effectiveSrcAddr = Magic.objectAsAddress(srcBase).plus(Offset.fromLong(srcOffset));
    Address effectiveDstAddr = Magic.objectAsAddress(dstBase).plus(Offset.fromLong(dstOffset));
    Extent length = Offset.fromLong(bytes).toWord().toExtent();
    Memory.memcopy(effectiveDstAddr, effectiveSrcAddr, length);
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
    return Address.fromLong(address).loadByte() == 1;
  }

  @Inline
  public boolean getBoolean(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_BOOLEAN_GETFIELD_BARRIER) {
      return booleanFieldRead(obj, off, 0);
    } else {
      return Magic.getByteAtOffset(obj, off) == 1;
    }
  }

  @Inline
  public void putBoolean(Object obj, long offset, boolean value) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_BOOLEAN_PUTFIELD_BARRIER) {
      booleanFieldWrite(obj, value, off, 0);
    } else {
      Magic.setBooleanAtOffset(obj, off, value);
    }
  }

  @Inline
  public void putBoolean(long address, boolean x) {
    Address.fromLong(address).store(x) ;
  }

  @Inline
  public byte getByte(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_BYTE_GETFIELD_BARRIER) {
      return byteFieldRead(obj, off, 0);
    } else {
      return Magic.getByteAtOffset(obj, off);
    }
  }

  @Inline
  public byte getByte(long address) {
    return Address.fromLong(address).loadByte();
  }

  @Inline
  public void putByte(Object obj,long offset, byte value) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_BYTE_PUTFIELD_BARRIER) {
      byteFieldWrite(obj, value, off, 0);
    } else {
      Magic.setByteAtOffset(obj, off, value);
    }
  }

  @Inline
  public void putByte(long address, byte x) {
    Address.fromLong(address).store(x);
  }

  @Inline
  public char getChar(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_CHAR_PUTFIELD_BARRIER) {
      return charFieldRead(obj, off, 0);
    } else {
      return Magic.getCharAtOffset(obj, off);
    }
  }

  @Inline
  public char getChar(long address) {
    return Address.fromLong(address).loadChar();
  }

  @Inline
  public void putChar(Object obj, long offset, char value) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_CHAR_PUTFIELD_BARRIER) {
      charFieldWrite(obj, value, off, 0);
    } else {
      Magic.setCharAtOffset(obj, off, value);
    }
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
  public double getDouble(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_DOUBLE_GETFIELD_BARRIER) {
      return doubleFieldRead(obj, off, 0);
    } else {
      return Magic.getDoubleAtOffset(obj, off);
    }
  }

  @Inline
  public void putDouble(Object obj,long offset,double value) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_DOUBLE_PUTFIELD_BARRIER) {
      doubleFieldWrite(obj, value, off, 0);
    } else {
      Magic.setDoubleAtOffset(obj, off, value);
    }
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
  public float getFloat(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_FLOAT_GETFIELD_BARRIER) {
      return floatFieldRead(obj, off, 0);
    } else {
      return Magic.getFloatAtOffset(obj, off);
    }
  }

  @Inline
  public void putFloat(Object obj, long offset, float value) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_FLOAT_PUTFIELD_BARRIER) {
      floatFieldWrite(obj, value, off, 0);
    } else {
      Magic.setFloatAtOffset(obj, off, value);
    }
  }

  @Inline
  public void putFloat(long address, float x) {
    Address.fromLong(address).store(x) ;
  }

  @Inline
  public int getInt(long address) {
    return Address.fromLong(address).loadInt();
  }

  @Inline
  public int getInt(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_INT_GETFIELD_BARRIER) {
      return intFieldRead(obj, off, 0);
    } else {
      return Magic.getIntAtOffset(obj, off);
    }
  }

  @Inline
  public int getIntVolatile(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    int result;
    if (NEEDS_INT_GETFIELD_BARRIER) {
      result = intFieldRead(obj, off, 0);
    } else {
      result = Magic.getIntAtOffset(obj, off);
    }
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
  public short getShort(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_SHORT_GETFIELD_BARRIER) {
      return shortFieldRead(obj, off, 0);
    } else {
      return Magic.getShortAtOffset(obj, off);
    }
  }

  @Inline
  public void putShort(Object obj, long offset, short value) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_SHORT_PUTFIELD_BARRIER) {
      shortFieldWrite(obj, value, off, 0);
    } else {
      Magic.setShortAtOffset(obj, off, value);
    }
  }

  @Inline
  public void putShort(long address, short x) {
    Address.fromLong(address).store(x);
  }

  @Inline
  public long getLong(long address) {
    return Address.fromLong(address).loadLong();
  }

  @Inline
  public long getLong(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_LONG_GETFIELD_BARRIER) {
      return longFieldRead(obj, off, 0);
    } else {
      return Magic.getLongAtOffset(obj, off);
    }
  }

  @Inline
  public long getLongVolatile(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    long result;
    if (NEEDS_LONG_GETFIELD_BARRIER) {
      result = longFieldRead(obj, off, 0);
    } else {
      result = Magic.getLongAtOffset(obj, off);
    }
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
    Object result;
    if (NEEDS_OBJECT_GETFIELD_BARRIER) {
      result = objectFieldRead(obj, off, 0);
    } else {
      result = Magic.getObjectAtOffset(obj, off);
    }
    return result;
  }

  @Inline
  public Object getObjectVolatile(Object obj, long offset) {
    Offset off = Offset.fromLong(offset);
    Object result;
    if (NEEDS_OBJECT_GETFIELD_BARRIER) {
      result = objectFieldRead(obj, off, 0);
    } else {
      result = Magic.getObjectAtOffset(obj, off);
    }
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
      Magic.setObjectAtOffset(obj, off, value);
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

  // Deprecated methods that we're carrying around because of IcedTea 6

  @Inline
  @Deprecated // deprecated in OpenJDK because it returns an int as "offset" and OpenJDK wants to use longs
  public int fieldOffset(Field f) {
    if (Modifier.isStatic(f.getModifiers())) {
      return (int) staticFieldOffset(f);
    } else {
      return (int) objectFieldOffset(f);
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public boolean getBoolean(Object obj, int offset) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_BOOLEAN_GETFIELD_BARRIER) {
      return booleanFieldRead(obj, off, 0);
    } else {
      return Magic.getByteAtOffset(obj, off) == 1;
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public void putBoolean(Object obj, int offset, boolean value) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_BOOLEAN_PUTFIELD_BARRIER) {
      booleanFieldWrite(obj, value, off, 0);
    } else {
      Magic.setBooleanAtOffset(obj, off, value);
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public byte getByte(Object obj, int offset) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_BYTE_GETFIELD_BARRIER) {
      return byteFieldRead(obj, off, 0);
    } else {
      return Magic.getByteAtOffset(obj, off);
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public void putByte(Object obj, int offset, byte value) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_BYTE_PUTFIELD_BARRIER) {
      byteFieldWrite(obj, value, off, 0);
    } else {
      Magic.setByteAtOffset(obj, off, value);
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public char getChar(Object obj, int offset) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_CHAR_PUTFIELD_BARRIER) {
      return charFieldRead(obj, off, 0);
    } else {
      return Magic.getCharAtOffset(obj, off);
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public void putChar(Object obj, int offset, char value) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_CHAR_PUTFIELD_BARRIER) {
      charFieldWrite(obj, value, off, 0);
    } else {
      Magic.setCharAtOffset(obj, off, value);
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public double getDouble(Object obj, int offset) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_DOUBLE_GETFIELD_BARRIER) {
      return doubleFieldRead(obj, off, 0);
    } else {
      return Magic.getDoubleAtOffset(obj, off);
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public void putDouble(Object obj, int offset, double value) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_DOUBLE_PUTFIELD_BARRIER) {
      doubleFieldWrite(obj, value, off, 0);
    } else {
      Magic.setDoubleAtOffset(obj, off, value);
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public float getFloat(Object obj, int offset) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_FLOAT_GETFIELD_BARRIER) {
      return floatFieldRead(obj, off, 0);
    } else {
      return Magic.getFloatAtOffset(obj, off);
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public void putFloat(Object obj, int offset, float value) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_FLOAT_PUTFIELD_BARRIER) {
      floatFieldWrite(obj, value, off, 0);
    } else {
      Magic.setFloatAtOffset(obj, off, value);
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public int getInt(Object obj, int offset) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_INT_GETFIELD_BARRIER) {
      return intFieldRead(obj, off, 0);
    } else {
      return Magic.getIntAtOffset(obj, off);
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public void putInt(Object obj, int offset, int value) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_INT_PUTFIELD_BARRIER) {
      intFieldWrite(obj, value, off, 0);
    } else {
      Magic.setIntAtOffset(obj,off,value);
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public short getShort(Object obj, int offset) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_SHORT_GETFIELD_BARRIER) {
      return shortFieldRead(obj, off, 0);
    } else {
      return Magic.getShortAtOffset(obj, off);
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public void putShort(Object obj, int offset, short value) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_SHORT_PUTFIELD_BARRIER) {
      shortFieldWrite(obj, value, off, 0);
    } else {
      Magic.setShortAtOffset(obj, off, value);
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public long getLong(Object obj, int offset) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_LONG_GETFIELD_BARRIER) {
      return longFieldRead(obj, off, 0);
    } else {
      return Magic.getLongAtOffset(obj, off);
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public void putLong(Object obj, int offset, long value) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_LONG_PUTFIELD_BARRIER) {
      longFieldWrite(obj, value, off, 0);
    } else {
      Magic.setLongAtOffset(obj,off,value);
    }
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public Object getObject(Object obj, int offset) {
    Offset off = Offset.fromIntSignExtend(offset);
    Object result;
    if (NEEDS_OBJECT_GETFIELD_BARRIER) {
      result = objectFieldRead(obj, off, 0);
    } else {
      result = Magic.getObjectAtOffset(obj, off);
    }
    return result;
  }

  @Inline
  @Deprecated // deprecated in OpenJDK because it uses an int as "offset" and OpenJDK wants to use longs
  public void putObject(Object obj, int offset, Object value) {
    Offset off = Offset.fromIntSignExtend(offset);
    if (NEEDS_OBJECT_PUTFIELD_BARRIER) {
      objectFieldWrite(obj, value, off, 0);
    } else {
      Magic.setObjectAtOffset(obj,off,value);
    }
  }

}
