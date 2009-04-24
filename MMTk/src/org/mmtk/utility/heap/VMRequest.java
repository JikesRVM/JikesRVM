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
package org.mmtk.utility.heap;

import org.mmtk.utility.Constants;

import org.vmmagic.unboxed.*;

/**
 * This class manages the encoding and decoding of virtual memory requests.
 *
 * By encapsulating this aspect of the construction of a space, we greatly
 * reduce the number of constructors required.
 */
public final class VMRequest implements Constants {

  public static final int REQUEST_DISCONTIGUOUS = 0;
  public static final int REQUEST_FIXED = 1;
  public static final int REQUEST_EXTENT = 3;
  public static final int REQUEST_FRACTION = 4;

  public final int type;
  public final Address start;
  public final Extent extent;
  public final float frac;
  public final boolean top;

  private VMRequest(int type, Address start, Extent bytes, float frac, boolean top) {
    this.type = type;
    this.start = start;
    this.extent = bytes;
    this.frac = frac;
    this.top = top;
  }

  /**
   * Is this a discontiguous space request?
   * @return true if this is a discontiguous space request, false otherwise
   */
  public boolean isDiscontiguous() {
    return type == REQUEST_DISCONTIGUOUS;
  }

  /**
   * A request for a discontiguous region of memory
   *
   * @return The request object
   */
  public static VMRequest create() {
    return new VMRequest(REQUEST_DISCONTIGUOUS, Address.zero(), Extent.zero(), 0f, false);
  }

  /**
   * A request for an explicit region of memory
   *
   * @param start The start of the region
   * @param extent The size of the region
   * @return The request object
   */
  public static VMRequest create(Address start, Extent extent) {
    return new VMRequest(REQUEST_FIXED, start, extent, 0f, false);
  }

  /**
   * A request for a number of megabytes of memory
   *
   * @param mb The number of megabytes
   * @return The request object
   */
  public static VMRequest create(int mb) {
    return create(mb, false);
  }

  /**
   * A request for a fraction of available memory
   *
   * @param frac The fraction
   * @return The request object
   */
  public static VMRequest create(float frac) {
    return create(frac, false);
  }

  /**
   * A request for a number of megabytes of memory, optionally requesting the highest available.
   *
   * @param mb The number of megabytes
   * @param top True to request high memory
   * @return The request object
   */
  public static VMRequest create(int mb, boolean top) {
    return new VMRequest(REQUEST_EXTENT, Address.zero(), Word.fromIntSignExtend(mb).lsh(LOG_BYTES_IN_MBYTE).toExtent(), 0f, top);
  }

  /**
   * A request for a fraction of available memory, optionally requesting the highest available.
   *
   * @param frac The fraction
   * @param top True to request high memory
   * @return The request object
   */
  public static VMRequest create(float frac, boolean top) {
    return new VMRequest(REQUEST_FRACTION, Address.zero(), Extent.zero(), frac, top);
  }

  /**
   * A request for a number of bytes of memory, optionally requesting the highest available.
   *
   * @param extent The number of bytes
   * @param top True to request high memory
   * @return The request object
   */
  public static VMRequest create(Extent extent, boolean top) {
    return new VMRequest(REQUEST_EXTENT, Address.zero(), extent, 0f, top);
  }
}
