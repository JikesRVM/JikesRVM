/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package com.ibm.jikesrvm;

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

  /** The address of the start of the data section of the boot image. */
  public static final Address BOOT_IMAGE_DATA_START = 
    //-#if RVM_FOR_32_ADDR
    Address.fromIntZeroExtend
    //-#elif RVM_FOR_64_ADDR
    Address.fromLong
    //-#endif
    (
     //-#value BOOTIMAGE_DATA_ADDRESS
     );

  /** The address of the start of the code section of the boot image. */
  public static final Address BOOT_IMAGE_CODE_START = 
    //-#if RVM_FOR_32_ADDR
    Address.fromIntZeroExtend
    //-#elif RVM_FOR_64_ADDR
    Address.fromLong
    //-#endif
    (
     //-#value BOOTIMAGE_CODE_ADDRESS
     );

  /** The address of the start of the ref map section of the boot image. */
  public static final Address BOOT_IMAGE_RMAP_START = 
    //-#if RVM_FOR_32_ADDR
    Address.fromIntZeroExtend
    //-#elif RVM_FOR_64_ADDR
    Address.fromLong
    //-#endif
    (
     //-#value BOOTIMAGE_RMAP_ADDRESS
     );

  /** The maximum boot image data size */
  public static final int BOOT_IMAGE_DATA_SIZE = 48<<20;

  /** The maximum boot image code size */
  public static final int BOOT_IMAGE_CODE_SIZE = 24<<20;

  /* Typical compression ratio is about 1/20 */
  static final int BAD_MAP_COMPRESSION = 5;  // conservative heuristic
  static final int MAX_BOOT_IMAGE_RMAP_SIZE = BOOT_IMAGE_DATA_SIZE/BAD_MAP_COMPRESSION;

  /** The address of the end of the data section of the boot image. */
  public static final Address BOOT_IMAGE_DATA_END = BOOT_IMAGE_DATA_START.plus(BOOT_IMAGE_DATA_SIZE);
  /** The address of the end of the code section of the boot image. */
  public static final Address BOOT_IMAGE_CODE_END = BOOT_IMAGE_CODE_START.plus(BOOT_IMAGE_CODE_SIZE);
  /** The address of the end of the ref map section of the boot image. */
  public static final Address BOOT_IMAGE_RMAP_END = BOOT_IMAGE_RMAP_START.plus(MAX_BOOT_IMAGE_RMAP_SIZE);
  /** The address of the end of the boot image. */
  public static final Address BOOT_IMAGE_END = BOOT_IMAGE_RMAP_END;

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
