/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$


package com.ibm.JikesRVM.librarySupport;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Array;
import com.ibm.JikesRVM.VM_Callbacks;
import com.ibm.JikesRVM.VM_Lock;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Process;
import com.ibm.JikesRVM.VM_Runtime;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.FinalizerThread;

/**
 * This class provides a set of static method entrypoints used in the
 * implementation of standard library system operations.
 *
 * @author Stephen Fink
 */
public class SystemSupport {

  /**
   * Exit virtual machine.
   * @param value  value to pass to host o/s
   */
  public static void sysExit(int value) {
    VM.sysExit(value);
  }

  /**
   * An entrypoint for debugging messages at runtime
   */
  public static void debugPrint(String s) {
    if (VM.runningVM) {
      VM.sysWriteln(s);
    }
  }

  /**
   * Answers the unique instance of java.lang.Class which
   * represents the class of an object. 
   *
   * @param o the object in quiestion.
   * @return		Class	the receiver's Class
   */
  public final static Class getClass(Object o) {
    return VM_Magic.getObjectType(o).getClassForType();
  }

  /**
   * Get an object's default "hashcode" value.
   * @return object's hashcode
   * Side effect: hash value is generated and stored into object's 
   * status word
   * @see java.lang.Object#hashCode
   */ 
  public static int getDefaultHashCode(Object object) {
    if (object == null) return 0;
    return VM_ObjectModel.getObjectHashCode(object);
  }

  /**
   * Answers the current time expressed as milliseconds since
   * the time 00:00:00 UTC on January 1, 1970.
   *
   * @return		long		the time in milliseconds.
   */
  public static long currentTimeMillis () {
    return VM_Time.currentTimeMillis();
  }

  /**
   * Answers the amount of free memory resources which
   * are available to the running program.
   */
  public static long freeMemory() {
    return VM_Runtime.freeMemory();
  }

  /**
   * Indicates to the virtual machine that it would be a
   * good time to collect available memory. Note that, this
   * is a hint only.
   */
  public static void gc() {
    VM_Runtime.gc();
  }

  /**
   * Answers the total amount of memory resources which
   * is available to (or in use by) the running program.
   */
  public static long totalMemory() {
    return VM_Runtime.totalMemory();
  }

  /**
   * Provides a hint to the virtual machine that it would
   * be useful to attempt to perform any outstanding
   * object finalizations.
   */
  public static void runFinalization() {
    synchronized (FinalizerThread.marker) {}
  }

  /**
   * clone a Scalar or Array Object
   * 
   * @param obj the object to clone
   * @return the cloned object
   */ 
  public static Object clone(Object obj) throws OutOfMemoryError, CloneNotSupportedException {
    return VM_Runtime.clone(obj);
  }

  /**
   * Support for Java synchronization primitive.
   *
   * @param o the object synchronized on
   * @see java.lang.Object#notify
   */
  public static void notify(Object o) {
    VM_Lock.notify(o);
  }

  /**
   * Support for Java synchronization primitive.
   *
   * @param o the object synchronized on
   * @see java.lang.Object#notify
   */
  public static void notifyAll(Object o) {
    VM_Lock.notifyAll(o);
  }

  /**
   * Support for Java synchronization primitive.
   *
   * @param o the object synchronized on
   * @see java.lang.Object#notify
   */
  public static void wait(Object o) {
    VM_Lock.wait(o);
  }

  /**
   * Support for Java synchronization primitive.
   *
   * @param o the object synchronized on
   * @param millis the number of milliseconds to wait for notification
   * @see java.lang.Object#wait(long time)
   */
  public static void wait(Object o, long millis) {
    if (millis == 0) {
      VM_Lock.wait(o); // wait forever
    } else {
      VM_Lock.wait(o, millis);
    }
  }

  /**
   * Copies the contents of <code>array1</code> starting at offset <code>start1</code>
   * into <code>array2</code> starting at offset <code>start2</code> for
   * <code>length</code> bytes.
   *
   * @param		src 		the array to copy out of
   * @param		srcPos		the starting index in array1
   * @param		dst		the array to copy into
   * @param		dstPos		the starting index in array2
   * @param		len		the number of bytes to copy
   */
  public static void arraycopy(Object src, int srcPos,
                               Object dst, int dstPos,
                               int len) {
    if (src == null || dst == null)    VM_Runtime.raiseNullPointerException();
    else if (src instanceof char[])    VM_Array.arraycopy((char[])src, srcPos, (char[])dst, dstPos, len);
    else if (src instanceof boolean[]) VM_Array.arraycopy((boolean[])src, srcPos, (boolean[])dst, dstPos, len);
    else if (src instanceof byte[])    VM_Array.arraycopy((byte[])src, srcPos, (byte[])dst, dstPos, len);
    else if (src instanceof short[])   VM_Array.arraycopy((short[])src, srcPos, (short[])dst, dstPos, len);
    else if (src instanceof int[])     VM_Array.arraycopy((int[])src, srcPos, (int[])dst, dstPos, len);
    else if (src instanceof long[])    VM_Array.arraycopy((long[])src, srcPos, (long[])dst, dstPos, len);
    else if (src instanceof float[])   VM_Array.arraycopy((float[])src, srcPos, (float[])dst, dstPos, len);
    else if (src instanceof double[])  VM_Array.arraycopy((double[])src, srcPos, (double[])dst, dstPos, len);
    else                               VM_Array.arraycopy((Object[])src, srcPos, (Object[])dst, dstPos, len);
  }
    
  public static Process createProcess(String program, String[] args, String[] env, java.io.File dir) {
    String dirPath = (dir != null) ? dir.getPath() : null;
    return new VM_Process(program, args, env, dirPath);
  }

  public static void addShutdownHook(final Thread hook) {
      VM_Callbacks.addExitMonitor(
	  new VM_Callbacks.ExitMonitor() {
	      public void notifyExit(int value) {
		  hook.start();
	      }
	  });
  }
	  
}
	
