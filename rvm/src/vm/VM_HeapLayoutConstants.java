/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package com.ibm.JikesRVM;

import org.vmmagic.unboxed.*;

/**
 * Constants defining heap layout constants
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 *
 * @version $Revision$
 * @date $Date$
 */
public interface VM_HeapLayoutConstants {

  /** The address of the start of the boot image */
  public static final Address BOOT_IMAGE_START = 
    //-#if RVM_FOR_32_ADDR
    Address.fromIntZeroExtend
    //-#elif RVM_FOR_64_ADDR
    Address.fromLong
    //-#endif
    (
     //-#value BOOTIMAGE_LOAD_ADDRESS
     );

  /** The maximum boot image size */
  public static final Extent BOOT_IMAGE_SIZE = Extent.fromInt(60<<20);

  /** The address of the end of the boot image. */
  public static final Address BOOT_IMAGE_END = BOOT_IMAGE_START.add(BOOT_IMAGE_SIZE);

  /** The address in virtual memory that is the highest that can be mapped. */
  public static Address MAXIMUM_MAPPABLE = 
    //-#if RVM_FOR_32_ADDR
    Address.fromIntZeroExtend
    //-#elif RVM_FOR_64_ADDR
    Address.fromLong
    //-#endif
    (
     //-#value MAXIMUM_MAPPABLE_ADDRESS
     );
}
