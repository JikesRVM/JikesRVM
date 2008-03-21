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
package java.lang;

import static org.jikesrvm.runtime.VM_SysCall.sysCall;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;

import org.jikesrvm.VM;
import org.jikesrvm.VM_UnimplementedError;
import org.jikesrvm.classloader.VM_Array;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.runtime.VM_Runtime;
import org.jikesrvm.runtime.VM_Time;

/**
 * Library support interface of Jikes RVM
 */
public final class VMSystem {


  static void arraycopy(Object src, int srcPos, Object dst, int dstPos, int len) {
    if (src == null || dst == null) {
      VM_Runtime.raiseNullPointerException();
    } else if ((src instanceof char[]) && (dst instanceof char[])) {
      VM_Array.arraycopy((char[])src, srcPos, (char[])dst, dstPos, len);
    } else if ((src instanceof Object[]) && (dst instanceof Object[])) {
      VM_Array.arraycopy((Object[])src, srcPos, (Object[])dst, dstPos, len);
    } else if ((src instanceof byte[]) && (dst instanceof byte[])) {
      VM_Array.arraycopy((byte[])src, srcPos, (byte[])dst, dstPos, len);
    } else if ((src instanceof boolean[]) && (dst instanceof boolean[])) {
      VM_Array.arraycopy((boolean[])src, srcPos, (boolean[])dst, dstPos, len);
    } else if ((src instanceof short[]) && (dst instanceof short[])) {
      VM_Array.arraycopy((short[])src, srcPos, (short[])dst, dstPos, len);
    } else if ((src instanceof int[]) && (dst instanceof int[])) {
      VM_Array.arraycopy((int[])src, srcPos, (int[])dst, dstPos, len);
    } else if ((src instanceof long[]) && (dst instanceof long[])) {
      VM_Array.arraycopy((long[])src, srcPos, (long[])dst, dstPos, len);
    } else if ((src instanceof float[]) && (dst instanceof float[])) {
      VM_Array.arraycopy((float[])src, srcPos, (float[])dst, dstPos, len);
    } else if ((src instanceof double[]) && (dst instanceof double[])) {
      VM_Array.arraycopy((double[])src, srcPos, (double[])dst, dstPos, len);
    } else {
      VM_Runtime.raiseArrayStoreException();
    }
  }

  static int identityHashCode(Object o) {
    return o == null ? 0 : VM_ObjectModel.getObjectHashCode(o);
  }

  static boolean isWordsBigEndian() {
    return !VM.LittleEndian;
  }

  public static long currentTimeMillis() {
    return VM_Time.currentTimeMillis();
  }

  public static long nanoTime() {
    return VM_Time.nanoTime();
  }

  static void setIn(InputStream in) {
    try {
      VM_Field inField =
        ((VM_Class)JikesRVMSupport.getTypeForClass(System.class))
        .findDeclaredField(
                           VM_Atom.findOrCreateUnicodeAtom("in"),
                           VM_Atom.findOrCreateUnicodeAtom("Ljava/io/InputStream;"));

      inField.setObjectValueUnchecked(null, in);
    } catch (Exception e) {
      throw new Error(e.toString());
    }
  }

  static void setOut(PrintStream out) {
    try {
      VM_Field outField =
        ((VM_Class)JikesRVMSupport.getTypeForClass(System.class))
        .findDeclaredField(
                           VM_Atom.findOrCreateUnicodeAtom("out"),
                           VM_Atom.findOrCreateUnicodeAtom("Ljava/io/PrintStream;"));

      outField.setObjectValueUnchecked(null, out);
    } catch (Exception e) {
      throw new Error(e.toString());
    }
  }

  static void setErr(PrintStream err) {
    try {
      VM_Field errField =
        ((VM_Class)JikesRVMSupport.getTypeForClass(System.class))
        .findDeclaredField(
                           VM_Atom.findOrCreateUnicodeAtom("err"),
                           VM_Atom.findOrCreateUnicodeAtom("Ljava/io/PrintStream;"));

      errField.setObjectValueUnchecked(null, err);
    } catch (Exception e) {
      throw new Error(e.toString());
    }
  }

  static InputStream makeStandardInputStream() { return null; }

  static PrintStream makeStandardOutputStream() { return null; }

  static PrintStream makeStandardErrorStream() { return null; }

  /** Get the value of an environment variable.
   */
  static String getenv(String envarName) {

    byte[] buf = new byte[128]; // Modest amount of space for starters.

    byte[] nameBytes = envarName.getBytes();

    int len = sysCall.sysGetenv(nameBytes, buf, buf.length);

    if (len < 0)                // not set.
      return null;

    if (len > buf.length) {
      buf = new byte[len];
      sysCall.sysGetenv(nameBytes, buf, len);
    }

    return new String(buf, 0, len);
  }

  static List<?> environ() {
    throw new VM_UnimplementedError();
  }
}
