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
package org.jikesrvm.mm.mmtk.gcspy;

import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;
import org.mmtk.plan.Plan;
import org.mmtk.vm.VM;

import org.jikesrvm.runtime.Magic;
import static org.jikesrvm.runtime.SysCall.sysCall;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.RuntimeEntrypoints;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class provides generally useful methods.
 */
@Uninterruptible public class Util extends org.mmtk.vm.gcspy.Util implements Constants {
  private static final boolean DEBUG_ = false;
  public static final int KILOBYTE = 1024;
  public static final int MEGABYTE = 1024 * 1024;

  /**
   * Allocate an array of bytes with malloc
   *
   * @param size The size to allocate
   * @return The start address of the memory allocated in C space
   * @see #free
   */
  @Override
  public final Address malloc(int size) {
    if (org.jikesrvm.VM.BuildWithGCSpy) {
      Address rtn  = sysCall.sysMalloc(size);
      if (rtn.isZero()) VM.assertions.fail("GCspy malloc failure");
      return rtn;
    } else
      return Address.zero();
  }

  /**
   * Free an array of bytes previously allocated with malloc
   *
   * @param addr The address of some memory previously allocated with malloc
   * @see #malloc
   */
  @Override
  public final void free(Address addr) {
    if (org.jikesrvm.VM.BuildWithGCSpy)
      if (!addr.isZero())
        sysCall.sysFree(addr);
  }

  /**
   * Convert a String to a 0-terminated array of bytes
   *
   * @param str The string to convert
   * @return The address of a null-terminated array in C-space
   *
   * WARNING: we call out to String.length and String.charAt, both of
   * which are interruptible. We protect these calls with a
   * swLock/swUnlock mechanism, as per VM.sysWrite on String
   */
  @Override
  public final Address getBytes(String str) {
    if (org.jikesrvm.VM.BuildWithGCSpy) {
      if (str == null)
        return Address.zero();

      if (DEBUG_) {
        Log.write("getBytes: "); Log.write(str); Log.write("->");
      }

      // Grab some memory sufficient to hold the null terminated string,
      // rounded up to an integral number of ints.
      char[] str_backing = java.lang.JikesRVMSupport.getBackingCharArray(str);
      int str_length = java.lang.JikesRVMSupport.getStringLength(str);
      int str_offset = java.lang.JikesRVMSupport.getStringOffset(str);
      int size = (str_length + 4) & -4;
      Address rtn = malloc(size);

      // Write the string into it, one byte at a time (dodgy conversion)
      for (int i=0; i < str_length; i++)  {
        rtn.store((byte)str_backing[str_offset+i], Offset.fromIntSignExtend(i));
      }
      // Zero rest of byte[]
      for (int i=str_length; i < size; i++)  {
        rtn.store((byte)0, Offset.fromIntSignExtend(i-str_offset));
      }
      if (DEBUG_) {
        sysCall.sysWriteBytes(2/*SysTraceFd*/, rtn, size); Log.write("\n");
      }
      return rtn;
    } else {
      return Address.zero();
    }
  }

  /**
   * Pretty print a size, converting from bytes to kilo- or mega-bytes as appropriate
   *
   * @param buffer The buffer (in C space) in which to place the formatted size
   * @param size The size in bytes
   */
  @Override
  public final void formatSize(Address buffer, int size) {
    if (org.jikesrvm.VM.BuildWithGCSpy)
      sysCall.gcspyFormatSize(buffer, size);
  }


  /**
   * Pretty print a size, converting from bytes to kilo- or mega-bytes as appropriate
   *
   * @param format A format string
   * @param bufsize The size of a buffer large enough to hold the formatted result
   * @param size The size in bytes
   */
  @Override
  public final Address formatSize(String format, int bufsize, int size) {
    if (org.jikesrvm.VM.BuildWithGCSpy) {
      // - sprintf(tmp, "Current Size: %s\n", gcspy_formatSize(size));
      Address tmp = malloc(bufsize);
      Address formattedSize = malloc(bufsize);
      Address currentSize = getBytes(format);
      formatSize(formattedSize, size);
      sprintf(tmp, currentSize, formattedSize);
      return tmp;
    } else {
      return Address.zero();
    }
  }

  /**
   * Create an array of a particular type.
   * The easiest way to use this is:
   *     Foo[] x = (Foo [])Stream.createDataArray(new Foo[0], numElements);
   * @param templ a data array to use as a template
   * @param numElements number of elements in new array
   * @return the new array
   */
  @Override
  @Interruptible
  public Object createDataArray(Object templ, int numElements) {
    if (org.jikesrvm.VM.BuildWithGCSpy) {
      RVMArray array = Magic.getObjectType(templ).asArray();
      return RuntimeEntrypoints.resolvedNewArray(numElements,
                              array.getLogElementSize(),
                              ObjectModel.computeArrayHeaderSize(array),
                              array.getTypeInformationBlock(),
                              Plan.ALLOC_GCSPY,
                              ObjectModel.getAlignment(array),
                              ObjectModel.getOffsetForAlignment(array, false),
                              0);
    } else {
      return null;
    }
  }

  //----------- Various methods modelled on string.c ---------------------//

  /**
   * sprintf(char *str, char *format, char* value)
   *
   * @param str The destination 'string' (memory in C space)
   * @param format The format 'string' (memory in C space)
   * @param value The value 'string' (memory in C space)
   * @return The number of characters printed (as returned by C's sprintf
   */
  @Override
  public final int sprintf(Address str, Address format, Address value) {
    if (org.jikesrvm.VM.BuildWithGCSpy)
      return sysCall.gcspySprintf(str, format, value);
    else
      return 0;
  }
}
