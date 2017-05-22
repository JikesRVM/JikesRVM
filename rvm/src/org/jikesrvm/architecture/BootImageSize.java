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
package org.jikesrvm.architecture;

import org.jikesrvm.VM;

/**
 * Provides a way to adjust size limits for the boot image.
 * <p>
 * It would probably a good idea to have a systematic way to
 * set the expected size of the boot image segments, depending
 * on the architecture. We currently don't have that.
 * <p>
 * The approach for the initial version of this class
 * was to adjust the limits that have been in place for a while
 * for the architectures supported at that time:
 * <ul>
 *   <li>64 bit boot images are expected to have a larger data
 *       segment than 32 bit images due to 64 bit pointers.</li>
 *   <li>x64 code is significantly larger than x86 code</li>
 * </ul>
 */
public final class BootImageSize {

  private BootImageSize() {
    // no instantiation desired
  }

  /**
   * @return adjustment factor for boot image data size limits
   */
  public static float dataSizeAdjustment() {
    // Data from Nov 2016 shows that data size grows by
    // about a third for development builds for
    // x86 -> x64 and PPC32 -> PPC64
    return VM.BuildFor32Addr ? 1.0f : 1.35f;
  }

  /**
   * @return adjustment factor for boot image code size limits
   */
  public static float codeSizeAdjustment() {
    // x64 code is a lot bigger than ia32 code.
    // For PPC, code size growth from 32 bit to 64 bit is
    // not nearly as big. The current limits are fine for PPC
    // so no adjustment is needed.
    return (VM.BuildForIA32 && VM.BuildFor64Addr) ? 1.5f : 1.0f;
  }

}
