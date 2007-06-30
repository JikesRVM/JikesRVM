/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2007
 */
//$Id: $
package sun.misc;

import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.scheduler.VM_Thread;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Synchronization;
import org.vmmagic.unboxed.Offset;
import java.lang.reflect.Field;

public class Unsafe {
  private static final Unsafe unsafe = new Unsafe();

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
    VM_Field vmfield = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    return vmfield.getOffset().toLong();
  }

  public boolean compareAndSwapInt(Object obj,long offset,int expect,int update) {
    Offset off = longToOffset(offset);
    return VM_Synchronization.tryCompareAndSwap(obj, off, expect, update);
  }

  public boolean compareAndSwapLong(Object obj,long offset,long expect,long update) {
    Offset off = Offset.fromIntSignExtend((int)offset);
    return VM_Synchronization.tryCompareAndSwap(obj, off, expect, update);
  }

  public boolean compareAndSwapObject(Object obj,long offset,Object expect,Object update) {
    Offset off = Offset.fromIntSignExtend((int)offset);
    return VM_Synchronization.tryCompareAndSwap(obj, off, expect, update);
  }

  public void putOrderedInt(Object obj,long offset,int value) {
    Offset off = longToOffset(offset);
    VM_Magic.setIntAtOffset(obj,off,value);
  }

  public void putOrderedLong(Object obj,long offset,long value) {
    Offset off = longToOffset(offset);
    VM_Magic.setLongAtOffset(obj,off,value);
  }

  public void putOrderedObject(Object obj,long offset,Object value) {
    Offset off = longToOffset(offset);
    VM_Magic.setObjectAtOffset(obj,off,value);
   }

  public void putIntVolatile(Object obj,long offset,int value) {
    Offset off = longToOffset(offset);
    VM_Magic.setIntAtOffset(obj,off,value);
  }

  public int getIntVolatile(Object obj,long offset) {
    Offset off = longToOffset(offset);
    return VM_Magic.getIntAtOffset(obj,off);
  }

  public void putLongVolatile(Object obj,long offset,long value) {
    Offset off = longToOffset(offset);
    VM_Magic.setLongAtOffset(obj,off,value);
   }

  public void putLong(Object obj,long offset,long value) {
    Offset off = longToOffset(offset);
    VM_Magic.setLongAtOffset(obj,off,value);
  }

  public long getLongVolatile(Object obj,long offset) {
    Offset off = longToOffset(offset);
    return VM_Magic.getLongAtOffset(obj,off);
  }

  public long getLong(Object obj,long offset) {
    Offset off = longToOffset(offset);
    return VM_Magic.getLongAtOffset(obj,off);
  }

  public void putObjectVolatile(Object obj,long offset,Object value) {
    Offset off = longToOffset(offset);
    VM_Magic.setObjectAtOffset(obj,off,value);
  }

  public void putObject(Object obj,long offset,Object value) {
    Offset off = longToOffset(offset);
    VM_Magic.setObjectAtOffset(obj,off,value);
  }

  public Object getObjectVolatile(Object obj,long offset) {
    Offset off = longToOffset(offset);
    return VM_Magic.getObjectAtOffset(obj,off);
  }

  public int arrayBaseOffset(Class arrayClass) {
    return 0;
  }

  public int arrayIndexScale(Class arrayClass) {
    VM_Type arrayType = java.lang.JikesRVMSupport.getTypeForClass(arrayClass);
    if (!arrayType.isArrayType()) {
      return 0;
    } else {
      return 1 << arrayType.asArray().getLogElementSize();
    }
  }

  public void unpark(Thread thread) {
    VM_Thread vmthread = java.lang.JikesRVMSupport.getThread(thread);
    vmthread.unpark();
  }

  public void park(boolean isAbsolute,long time) {
    VM_Thread vmthread = java.lang.JikesRVMSupport.getThread(Thread.currentThread());
    vmthread.park(isAbsolute, time);
  }
}
