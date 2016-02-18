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
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.Offset;

import static org.jikesrvm.mm.mminterface.Barriers.*;

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
  private static final Unsafe unsafe = new Unsafe();

  /** alias to match name that DL's FJ framework appears to expect from class libs */
  @SuppressWarnings("unused")
  private static final Unsafe theUnsafe = unsafe;

  private Unsafe() {}

  @Inline
  public static Unsafe getUnsafe() {
    SecurityManager sm = System.getSecurityManager();
    if (sm != null)
      sm.checkPropertiesAccess();
    return unsafe;
  }

  @Inline
  public long objectFieldOffset(Field field) {
    RVMField vmfield = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    return vmfield.getOffset().toLong();
  }

  @Inline
  public boolean compareAndSwapInt(Object obj,long offset,int expect,int update) {
    Offset off = Offset.fromLong(offset);
    return Synchronization.tryCompareAndSwap(obj, off, expect, update);
  }

  @Inline
  public boolean compareAndSwapLong(Object obj,long offset,long expect,long update) {
    Offset off = Offset.fromLong(offset);
    return Synchronization.tryCompareAndSwap(obj, off, expect, update);
  }

  @Inline
  public boolean compareAndSwapObject(Object obj,long offset,Object expect,Object update) {
    Offset off = Offset.fromLong(offset);
    return Synchronization.tryCompareAndSwap(obj, off, expect, update);
  }

  @Inline
  public void putOrderedInt(Object obj,long offset,int value) {
    Offset off = Offset.fromLong(offset);
    Magic.storeStoreBarrier();
    if (NEEDS_INT_PUTFIELD_BARRIER) {
      intFieldWrite(obj, value, off, 0);
    } else {
      Magic.setIntAtOffset(obj, off, value);
    }
  }

  @Inline
  public void putOrderedLong(Object obj,long offset,long value) {
    Offset off = Offset.fromLong(offset);
    Magic.storeStoreBarrier();
    if (NEEDS_LONG_PUTFIELD_BARRIER) {
      longFieldWrite(obj, value, off, 0);
    } else {
      Magic.setLongAtOffset(obj, off, value);
    }
  }

  @Inline
  public void putOrderedObject(Object obj,long offset,Object value) {
    Offset off = Offset.fromLong(offset);
    Magic.storeStoreBarrier();
    if (NEEDS_OBJECT_PUTFIELD_BARRIER) {
      objectFieldWrite(obj, value, off, 0);
    } else {
      Magic.setObjectAtOffset(obj, off, value);
    }
   }

  @Inline
  public void putIntVolatile(Object obj,long offset,int value) {
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
  public void putInt(Object obj,long offset,int value) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_INT_PUTFIELD_BARRIER) {
      intFieldWrite(obj, value, off, 0);
    } else {
      Magic.setIntAtOffset(obj,off,value);
    }
  }

  @Inline
  public int getIntVolatile(Object obj,long offset) {
    Offset off = Offset.fromLong(offset);
    int result = Magic.getIntAtOffset(obj,off);
    Magic.combinedLoadBarrier();
    return result;
  }

  @Inline
  public int getInt(Object obj,long offset) {
    Offset off = Offset.fromLong(offset);
    return Magic.getIntAtOffset(obj,off);
  }

  @Inline
  public void putLongVolatile(Object obj,long offset,long value) {
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
  public void putLong(Object obj,long offset,long value) {
    Offset off = Offset.fromLong(offset);
    if (NEEDS_LONG_PUTFIELD_BARRIER) {
      longFieldWrite(obj, value, off, 0);
    } else {
      Magic.setLongAtOffset(obj,off,value);
    }
  }

  @Inline
  public long getLongVolatile(Object obj,long offset) {
    Offset off = Offset.fromLong(offset);
    long result = Magic.getLongAtOffset(obj,off);
    Magic.combinedLoadBarrier();
    return result;
  }

  @Inline
  public long getLong(Object obj,long offset) {
    Offset off = Offset.fromLong(offset);
    return Magic.getLongAtOffset(obj,off);
  }

  @Inline
  public void putObjectVolatile(Object obj,long offset,Object value) {
    Offset off = Offset.fromLong(offset);
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
    Offset off = Offset.fromLong(offset);
    if (NEEDS_OBJECT_PUTFIELD_BARRIER) {
      objectFieldWrite(obj, value, off, 0);
    } else {
      Magic.setObjectAtOffset(obj,off,value);
    }
  }

  @Inline
  public Object getObjectVolatile(Object obj,long offset) {
    Offset off = Offset.fromLong(offset);
    Object result = Magic.getObjectAtOffset(obj,off);
    Magic.combinedLoadBarrier();
    return result;
  }

  @Inline
  public int arrayBaseOffset(Class<?> arrayClass) {
    return 0;
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
