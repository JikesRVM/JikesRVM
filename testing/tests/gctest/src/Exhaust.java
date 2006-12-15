/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.io.PrintStream;
import com.ibm.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.vmmagic.pragma.*;

/**
 * @author Perry Cheng
 */
class Exhaust {

  final private static PrintStream o = System.out;

  static Object [] first;
  static Object [] last;
  final static int itemSize = 64;
  static double growthFactor = 10.0;
  static int rounds = 5;
  static long tot = 0;          // total # allocated
  static long wasAllocating;            // How much were we allocating?
  

  public static void main(String args[]) {
    long mhs = MM_Interface.getMaxHeapSize().toLong();
    
    o.println("Max heap size: " + mhs + " bytes");
    if (mhs > 1024 * 1024)
      o.println("  that's " + mhs / ( 1024.0 * 1024.0 ) + " megabytes");
    if (mhs > 1024 * 1024 * 1024)
      o.println("  that's " + mhs / (1024.0 * 1024 * 1024) + " gigabytes");
    runTest();

    System.exit(0);
  }

  @NoInline
  public static int doInner (int size) { 
    while (true) {
      wasAllocating = size;
      Object [] next = new Object[((int) size) / 4];
      last[0] = next;
      last = next;
      tot += size;
      wasAllocating = 0;
    }
  }

  @NoInline
  public static void runTest() { 
    int size = itemSize;  
    for (int i=1; i<=rounds; i++) {
      o.println("Starting round " + i + " with size = " + size);
        
      first = new Object[1];
      last = first;
      tot = 0;
        
      o.println("  Allocating until exception thrown");
      try {
        doInner(size);
      }
      catch (OutOfMemoryError e) {
        first = last = null;  // kills everything
        o.println("  Caught OutOfMemory - freeing now");  // this allocates; must follow nulling

        //        o.println("  Maximum size reached is " + size);

        o.println("  Had " + tot + " bytes allocated; failed trying to allocate " + wasAllocating + " bytes");
      }

      size *= growthFactor;
    }
    System.out.println("Overall: SUCCESS");
  }
}

