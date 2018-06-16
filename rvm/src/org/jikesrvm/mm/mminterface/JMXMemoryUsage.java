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
package org.jikesrvm.mm.mminterface;

import java.lang.management.MemoryUsage;

import org.mmtk.policy.Space;
import org.mmtk.utility.Conversions;
import org.vmmagic.unboxed.Extent;

public class JMXMemoryUsage {

  private long usedBytes;
  private long reservedBytes;
  private long maxBytes;

  private JMXMemoryUsage() {
    // nothing to, everything initialised with 0
  }

  public JMXMemoryUsage(Space space) {
    this();
    add(space);
  }

  /**
   * @return the number of bytes that are available at maximum, as defined by
   *         the {@code MemoryUsage} class from JMX
   */
  public long getMax() {
    return maxBytes;
  }

  /**
   * @return the number of reserved bytes, as defined by the {@code MemoryUsage}
   *         class from JMX
   */
  public long getReserved() {
    return reservedBytes;
  }

  /**
   * @return the number of used bytes, as defined by the {@code MemoryUsage}
   *         class from JMX
   */
  public long getUsed() {
    return usedBytes;
  }

  public void add(Space space) {
    int committedPages = space.committedPages();
    long committedBytes = Conversions.pagesToBytes(committedPages).toLong();
    this.usedBytes += committedBytes;

    int reservedPages = space.reservedPages();
    long reservedBytes = Conversions.pagesToBytes(reservedPages).toLong();
    // According to JMX, reserved >= used which we can't guarantee
    // because we don't synchronize or make a snapshot when getting
    // the values. Therefore, modify the values to be valid, if necessary.
    reservedBytes = Math.max(committedBytes, reservedBytes);
    this.reservedBytes += reservedBytes;

    Extent extent = space.getExtent();
    if (extent.EQ(Extent.zero())) {
      // discontiguous space. The maximum is unknown because all discontiguous
      // spaces consider non-allocated space as theirs.
      maxBytes = -1;
    } else {
      maxBytes += extent.toLong();
    }
  }

  public MemoryUsage toMemoryUsage() {
    return new MemoryUsage(-1L, usedBytes, reservedBytes, maxBytes);
  }

  public static JMXMemoryUsage empty() {
    return new JMXMemoryUsage();
  }

}
