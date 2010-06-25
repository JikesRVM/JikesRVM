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
package org.mmtk.utility.statistics;

import org.mmtk.utility.Log;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Uninterruptible;

/**
 * This class represents a perf event, such as cache misses, etc.
 */
@Uninterruptible
public final class PerfEvent extends LongCounter {
  /** True if the counter did not run due to contention for a physical counter */
  private boolean contended;

  /** True if the counter did not run all of the time and has been scaled appropriately */
  private boolean scaled;

  /** True if the counter overflowed */
  private boolean overflowed;

  /** The index of the counter in the native array */
  private int index;

  /** The previously read value of the counter (used to detect overflow) */
  private long previousValue;

  /** A buffer passed to the native code when reading values, returns the tuple RAW_COUNT, TIME_ENABLED, TIME_RUNNING */
  private final long[] readBuffer = new long[3];
  private static final int RAW_COUNT = 0;
  private static final int TIME_ENABLED = 1;
  private static final int TIME_RUNNING = 2;

  /** Three 64 bit values is 24 bytes */
  private static final int BYTES_TO_READ = 24;

  /** True if any data was scaled */
  public static boolean dataWasScaled = false;

  public PerfEvent(int index, String name) {
    super(name, true, false);
    this.index = index;
  }

  /**
   * Counters are 64 bit unsigned in the kernel but only 63 bits are available in Java
   */
  @Override
  protected long getCurrentValue() {
    VM.statistics.perfEventRead(index, readBuffer);
    if (readBuffer[RAW_COUNT] < 0 || readBuffer[TIME_ENABLED] < 0 || readBuffer[TIME_RUNNING] < 0) {
      // Negative implies they have exceeded 63 bits.
      overflowed = true;
    }
    if (readBuffer[TIME_ENABLED] == 0) {
      // Counter never run (assume contention)
      contended = true;
    }
    // Was the counter scaled?
    if (readBuffer[TIME_ENABLED] != readBuffer[TIME_RUNNING]) {
      scaled = true;
      dataWasScaled = true;
      double scaleFactor;
      if (readBuffer[TIME_RUNNING] == 0) {
        scaleFactor = 0;
      } else {
        scaleFactor = readBuffer[TIME_ENABLED] / readBuffer[TIME_RUNNING];
      }
      readBuffer[RAW_COUNT] = (long) (readBuffer[RAW_COUNT] * scaleFactor);
    }
    if (readBuffer[RAW_COUNT] < previousValue) {
      // value should monotonically increase
      overflowed = true;
    }
    previousValue = readBuffer[RAW_COUNT];
    return readBuffer[RAW_COUNT];
  }

  /**
   * Print the given value
   * @param value The value to be printed
   */
  @Override
  void printValue(long value) {
    if (overflowed) {
      Log.write("OVERFLOWED");
    } else if (contended) {
      Log.write("CONTENDED");
    } else {
      Log.write(value);
      if (scaled) {
        Log.write(" (SCALED)");
      }
    }
  }

  public String getColumnSuffix() {
    return
      overflowed ? "overflowed" :
      contended ? "contended" :
      scaled ? ".scaled" :
      "";
  }
}

