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

  @Override
  public final Address malloc(int size) {
    if (org.jikesrvm.VM.BuildWithGCSpy) {
      Address rtn  = sysCall.sysMalloc(size);
      if (rtn.isZero()) VM.assertions.fail("GCspy malloc failure");
      return rtn;
    } else
      return Address.zero();
  }

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

  @Override
  public final void formatSize(Address buffer, int size) {
    if (org.jikesrvm.VM.BuildWithGCSpy)
      sysCall.gcspyFormatSize(buffer, size);
  }


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

  @Override
  public final int sprintf(Address str, Address format, Address value) {
    if (org.jikesrvm.VM.BuildWithGCSpy)
      return sysCall.gcspySprintf(str, format, value);
    else
      return 0;
  }
}
