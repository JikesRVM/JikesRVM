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
package org.mmtk.utility.alloc;

import org.mmtk.utility.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This plan implements constants and access methods for meta data
 * that is embedded in allocation spaces (rather than kept on the
 * side).  The basic idea is that meta data be embedded at a very
 * coarse power of two granularity for fast access, minimal wastage
 * and by making the regions coarse, the contiguous meta-data will be
 * relatively large and thus the probability of L1 conflict misses
 * will be reduced (as compared with embedding meta-data at the start
 * of each page which will cause those few cache lines corresponding
 * to the start of each page to be heavily conflicted).
 */
@Uninterruptible
public final class EmbeddedMetaData implements Constants {

  /* The (log of the) size of each region of meta data management */
  public static final int LOG_BYTES_IN_REGION = 22;
  public static final int BYTES_IN_REGION = 1 << LOG_BYTES_IN_REGION;
  private static final Word REGION_MASK = Word.fromIntSignExtend(BYTES_IN_REGION - 1);
  public static final int LOG_PAGES_IN_REGION = LOG_BYTES_IN_REGION - LOG_BYTES_IN_PAGE;
  public static final int PAGES_IN_REGION = 1 << LOG_PAGES_IN_REGION;

  /**
   * Given an address, return the beginning of the meta data for the
   * region containing the address.  This is a fast operation because
   * it only involves masking out low order bits.
   *
   * @param address The address whose meta data is sought.
   * @return The address of the start of the meta data for the meta
   * region in which the address is located.
   */
  @Inline
  public static Address getMetaDataBase(Address address) {
    return address.toWord().and(REGION_MASK.not()).toAddress();
  }

  /**
   * Given an address, the density (coverage) of a meta data type, and
   * the granularity (alignment) of the meta data, return the offset
   * into the meta data the address.
   *
   * @param address The address whose meta data offset is sought.
   * @param logCoverage The log base two of the coverage of the meta
   * data in question. For example, a value of 4 would indicate a
   * coverage of 16; one metadata byte for every 16 bytes of data.
   * @param logAlign The log base two of the alignment or granularity
   * of the meta-data (it may be arranged in bytes, words, double
   * words etc).
   * @return The offset into the meta-data for this region, given the
   * specified address and coverage and alignment requirements.
   */
  public static Extent getMetaDataOffset(Address address,
                                                  int logCoverage,
                                                  int logAlign) {
    return address.toWord().and(REGION_MASK).rshl(logCoverage+logAlign).lsh(logAlign).toExtent();
  }
}
