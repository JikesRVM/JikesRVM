/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002
 */

import org.jikesrvm.classloader.VM_Type;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Worker for parallel compilation during bootimage writing.
 * @author Perry Cheng
 */
public class BootImageWorker implements Runnable {

  public static final boolean verbose = false;
  private static final AtomicLong count = new AtomicLong();
  private final VM_Type type; 

  BootImageWorker(VM_Type type) {
    this.type = type;
  }

  public void run () {
    if (type == null) 
      return;
    long startTime = 0;
    long myCount = 0;
    if (verbose) {
      startTime = System.currentTimeMillis();
      myCount = count.incrementAndGet();
      BootImageWriterMessages.say(startTime + ": "+ myCount +" starting " + type);
    }
    type.instantiate();
    if (verbose) {
      long stopTime = System.currentTimeMillis();
      BootImageWriterMessages.say(stopTime + ": "+ myCount +" finish " + type +
          " duration: " + (stopTime - startTime));
    }
  }
}

