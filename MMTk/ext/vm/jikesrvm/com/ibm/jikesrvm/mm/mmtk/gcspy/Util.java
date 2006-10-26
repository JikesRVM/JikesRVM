/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Richard Jones, 2003-6
 * Computing Laboratory, University of Kent at Canterbury
 */
package com.ibm.jikesrvm.mm.mmtk.gcspy;

import org.mmtk.vm.Assert;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;
import org.mmtk.plan.Plan;
import org.mmtk.vm.VM;

import com.ibm.jikesrvm.VM_Magic;
import com.ibm.jikesrvm.VM_SysCall;
import com.ibm.jikesrvm.VM_Synchronization;
import com.ibm.jikesrvm.classloader.VM_Array;
import com.ibm.jikesrvm.VM_ObjectModel;
import com.ibm.jikesrvm.VM_Runtime;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class provides generally useful methods.
 *
 * $Id: Util.java 10806 2006-09-22 12:17:46Z dgrove-oss $
 *
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision: 10806 $
 * @date $Date: 2006-09-22 13:17:46 +0100 (Fri, 22 Sep 2006) $
 */
public class Util extends org.mmtk.vm.gcspy.Util implements Uninterruptible, Constants {
  private static final boolean DEBUG_ = false;
  private static final int LOG_BYTES_IN_WORD = LOG_BYTES_IN_INT;
  private static final int BYTES_IN_WORD = 1 << LOG_BYTES_IN_WORD;
  
  /**
   * Allocate an array of bytes with malloc
   * 
   * @param size The size to allocate
   * @return The start address of the memory allocated in C space
   * @see #free
   */
  public final Address malloc(int size) {
    if (VM_SysCall.WITH_GCSPY) {
      Address rtn  = VM_SysCall.sysMalloc(size);
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
  public final void free(Address addr) {
    if (VM_SysCall.WITH_GCSPY) 
      if (!addr.isZero())
        VM_SysCall.sysFree(addr);
  }
  
  
  // From VM.java
  private static int sysWriteLock = 0;
  private static Offset sysWriteLockOffset = Offset.max();

  private static final void swLock() {
    if (VM_SysCall.WITH_GCSPY) {
      if (sysWriteLockOffset.isMax()) return;
      while (!VM_Synchronization.testAndSet(VM_Magic.getJTOC(), sysWriteLockOffset, 1)) 
        ;
    }
  }

  private static final void swUnlock() {
    if (VM_SysCall.WITH_GCSPY) {
      if (sysWriteLockOffset.isMax()) return;
      VM_Synchronization.fetchAndStore(VM_Magic.getJTOC(), sysWriteLockOffset, 0);
    }
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
  public final Address getBytes (String str) 
    throws LogicallyUninterruptiblePragma {
    if (VM_SysCall.WITH_GCSPY) {
      if (str == null) 
        return Address.zero();
  
      if (DEBUG_) {
        Log.write("getBytes: "); Log.write(str); Log.write("->");
      }
  
      // Grab some memory sufficient to hold the null terminated string,
      // rounded up to an integral number of ints.
      int len;
      swLock(); 
        len = str.length(); 
      swUnlock();
      int size = ((len >>> LOG_BYTES_IN_WORD) + 1) << LOG_BYTES_IN_WORD;
      Address rtn = malloc(size);
     
      // Write the string into it, one word at a time, being carefull about endianism
      for (int w = 0; w <= (len >>> LOG_BYTES_IN_WORD); w++)  {
        int value = 0;
        int offset = w << LOG_BYTES_IN_WORD;
        int shift = 0;
        for (int b = 0; b < BYTES_IN_WORD; b++) {
          byte byteVal = 0;
          if (offset + b < len) {
            swLock(); 
              byteVal = (byte) str.charAt(offset + b);    // dodgy conversion!
            swUnlock();
          }
          // Endianism matters
          //-#if RVM_FOR_IA32
          value = (byteVal << shift) | value;
          //-#else
          value = (value << shift) | byteVal; // not tested
          //-#endif
          shift += BITS_IN_BYTE;
        }
        rtn.store(value, Offset.fromInt(offset));
      }
      if (DEBUG_) {
        VM_SysCall.sysWriteBytes(2/*SysTraceFd*/, rtn, size); Log.write("\n");
      }
      return rtn;
    }
    else 
      return Address.zero();
  }

  public static final int KILOBYTE = 1024;
  public static final int MEGABYTE = 1024 * 1024;

  /**
   * Pretty print a size, converting from bytes to kilo- or mega-bytes as appropriate
   * 
   * @param buffer The buffer (in C space) in which to place the formatted size
   * @param size The size in bytes
   */
  public final void formatSize(Address buffer, int size) {
    if (VM_SysCall.WITH_GCSPY) 
      VM_SysCall.gcspyFormatSize(buffer, size);
  }

  
  /**
   * Pretty print a size, converting from bytes to kilo- or mega-bytes as appropriate
   * 
   * @param format A format string
   * @param bufsize The size of a buffer large enough to hold the formatted result
   * @param size The size in bytes
   */
  public final Address formatSize(String format, int bufsize, int size) {
    if (VM_SysCall.WITH_GCSPY) {
      // - sprintf(tmp, "Current Size: %s\n", gcspy_formatSize(size));
      Address tmp = malloc(bufsize);
      Address formattedSize = malloc(bufsize);
      Address currentSize = getBytes(format); 
      formatSize(formattedSize, size);
      sprintf(tmp, currentSize, formattedSize);
      return tmp;
    }
    else 
      return Address.zero();
  }

  /**
   * Create an array of a particular type. 
   * The easiest way to use this is:
   *     Foo[] x = (Foo [])Stream.createDataArray(new Foo[0], numElements);
   * @param templ a data array to use as a template
   * @param numElements number of elements in new array
   * @return the new array
   */
  public Object createDataArray(Object templ, int numElements) 
    throws InterruptiblePragma {
    if (VM_SysCall.WITH_GCSPY) {
      VM_Array array = VM_Magic.getObjectType(templ).asArray();
      return VM_Runtime.resolvedNewArray(numElements, 
                              array.getLogElementSize(),
                              VM_ObjectModel.computeArrayHeaderSize(array),
                              array.getTypeInformationBlock(),
                              Plan.ALLOC_GCSPY,
                              VM_ObjectModel.getAlignment(array),
                              VM_ObjectModel.getOffsetForAlignment(array),
                              0);
    }
    else 
      return null;
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
  public final int sprintf(Address str, Address format, Address value) {
    if (VM_SysCall.WITH_GCSPY) 
      return VM_SysCall.gcspySprintf(str, format, value);
    else 
      return 0;
  }
}

