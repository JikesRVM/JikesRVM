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
import org.vmmagic.unboxed.Offset;

import static org.jikesrvm.mm.mminterface.Barriers.*;

public final class Unsafe {
  private static final Unsafe unsafe = new Unsafe();
  private static final Unsafe theUnsafe = unsafe; // alias to match name that DL's FJ framework appears to expect from class libs.

  private Unsafe() {}

  public static Unsafe getUnsafe() {
    SecurityManager sm = System.getSecurityManager();
    if (sm != null)
      sm.checkPropertiesAccess();
    return unsafe;
  }

  private Offset longToOffset(long offset) {
    return Offset.fromIntSignExtend((int)offset);
  }

  public long objectFieldOffset(Field field) {
    RVMField vmfield = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    return vmfield.getOffset().toLong();
  }

  public boolean compareAndSwapInt(Object obj,long offset,int expect,int update) {
    Offset off = longToOffset(offset);
    return Synchronization.tryCompareAndSwap(obj, off, expect, update);
  }

  public boolean compareAndSwapLong(Object obj,long offset,long expect,long update) {
    Offset off = Offset.fromIntSignExtend((int)offset);
    return Synchronization.tryCompareAndSwap(obj, off, expect, update);
  }

  public boolean compareAndSwapObject(Object obj,long offset,Object expect,Object update) {
    Offset off = Offset.fromIntSignExtend((int)offset);
    return Synchronization.tryCompareAndSwap(obj, off, expect, update);
  }

  public void putOrderedInt(Object obj,long offset,int value) {
    Offset off = longToOffset(offset);
    Magic.writeFloor();
    if (NEEDS_INT_PUTFIELD_BARRIER) {
      intFieldWrite(obj, value, off, 0);
    } else {
      Magic.setIntAtOffset(obj, off, value);
    }
  }

  public void putOrderedLong(Object obj,long offset,long value) {
    Offset off = longToOffset(offset);
    Magic.writeFloor();
    if (NEEDS_LONG_PUTFIELD_BARRIER) {
      longFieldWrite(obj, value, off, 0);
    } else {
      Magic.setLongAtOffset(obj, off, value);
    }
  }

  public void putOrderedObject(Object obj,long offset,Object value) {
    Offset off = longToOffset(offset);
    Magic.writeFloor();
    if (NEEDS_OBJECT_PUTFIELD_BARRIER) {
      objectFieldWrite(obj, value, off, 0);
    } else {
      Magic.setObjectAtOffset(obj, off, value);
    }
   }

  public void putIntVolatile(Object obj,long offset,int value) {
    Magic.writeFloor();
    Offset off = longToOffset(offset);
    if (NEEDS_INT_PUTFIELD_BARRIER) {
      intFieldWrite(obj, value, off, 0);
    } else {
      Magic.setIntAtOffset(obj,off,value);
    }
  }

  public void putInt(Object obj,long offset,int value) {
    Offset off = longToOffset(offset);
    if (NEEDS_INT_PUTFIELD_BARRIER) {
      intFieldWrite(obj, value, off, 0);
    } else {
      Magic.setIntAtOffset(obj,off,value);
    }
  }

  public int getIntVolatile(Object obj,long offset) {
    Offset off = longToOffset(offset);
    int result = Magic.getIntAtOffset(obj,off);
    Magic.readCeiling();
    return result;
  }

  public int getInt(Object obj,long offset) {
    Offset off = longToOffset(offset);
    return Magic.getIntAtOffset(obj,off);
  }

  public void putLongVolatile(Object obj,long offset,long value) {
    Magic.writeFloor();
    Offset off = longToOffset(offset);
    if (NEEDS_LONG_PUTFIELD_BARRIER) {
      longFieldWrite(obj, value, off, 0);
    } else {
      Magic.setLongAtOffset(obj,off,value);
    }
   }

  public void putLong(Object obj,long offset,long value) {
    Offset off = longToOffset(offset);
    if (NEEDS_LONG_PUTFIELD_BARRIER) {
      longFieldWrite(obj, value, off, 0);
    } else {
      Magic.setLongAtOffset(obj,off,value);
    }
  }

  public long getLongVolatile(Object obj,long offset) {
    Offset off = longToOffset(offset);
    long result = Magic.getLongAtOffset(obj,off);
    Magic.readCeiling();
    return result;
  }

  public long getLong(Object obj,long offset) {
    Offset off = longToOffset(offset);
    return Magic.getLongAtOffset(obj,off);
  }

  public void putObjectVolatile(Object obj,long offset,Object value) {
    Offset off = longToOffset(offset);
    Magic.writeFloor();
    if (NEEDS_OBJECT_PUTFIELD_BARRIER) {
      objectFieldWrite(obj, value, off, 0);
    } else {
      Magic.setObjectAtOffset(obj,off,value);
    }
  }

  public void putObject(Object obj,long offset,Object value) {
    Offset off = longToOffset(offset);
    if (NEEDS_OBJECT_PUTFIELD_BARRIER) {
      objectFieldWrite(obj, value, off, 0);
    } else {
      Magic.setObjectAtOffset(obj,off,value);
    }
  }

  public Object getObjectVolatile(Object obj,long offset) {
    Offset off = longToOffset(offset);
    Object result = Magic.getObjectAtOffset(obj,off);
    Magic.readCeiling();
    return result;
  }

  public int arrayBaseOffset(Class<?> arrayClass) {
    return 0;
  }

  public int arrayIndexScale(Class<?> arrayClass) {
    RVMType arrayType = java.lang.JikesRVMSupport.getTypeForClass(arrayClass);
    if (!arrayType.isArrayType()) {
      return 0;
    } else {
      return 1 << arrayType.asArray().getLogElementSize();
    }
  }

  public void unpark(Object thread) {
    RVMThread vmthread = java.lang.JikesRVMSupport.getThread((Thread)thread);
    if (vmthread != null) {
      vmthread.unpark();
    }
  }

  public void park(boolean isAbsolute,long time) throws Throwable  {
    RVMThread vmthread = java.lang.JikesRVMSupport.getThread(Thread.currentThread());
    vmthread.park(isAbsolute, time);
  }

  public void throwException(Throwable ex) {
    RuntimeEntrypoints.athrow(ex);
  }
}
