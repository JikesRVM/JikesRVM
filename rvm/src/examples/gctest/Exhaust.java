/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/*
 * @author Perry Cheng
 */

import com.ibm.JikesRVM.VM_PragmaNoInline;
import java.lang.System;	// unneeded
import java.io.PrintStream;
import com.ibm.JikesRVM.memoryManagers.JMTk.Options;

class Exhaust {

  final private static PrintStream o = System.out;
  final static int itemSize = 32;
  /** We really want this to be something like
   *  (max-heap-size / itemSize) * 2,.
   *  But we don't know what the actual max heap size is.  And we don't want
   *  the allocation of this array to be the cause of the exception.  This is
   *  frustrating.  */ 
  //  static int metaSize = 4 * 1024 * 1024;
  //  final static int metaSize = (Options.getMaxHeapSize() / itemSize) * 2;
  final static int metaSize = (Options.getMaxHeapSize() / itemSize) / 2;
  static Object [] junk;
  static int cursor = 0;
  static double growthFactor = 1.0;
  static double metaGrowthFactor = 1.1;
  static int rounds = 10;
  static long tot = 0;		// total # allocated
  static long wasAllocating;		// How much were we allocating?
  

  public static void main(String args[])  //throws Throwable 
  {
    long mhs = Options.getMaxHeapSize();
    
    o.println("Max heap size: " + mhs + " bytes");
    if (mhs > 1024 * 1024)
      o.println("  that's " + mhs / ( 1024.0 * 1024.0 ) + " megabytes");
    if (mhs > 1024 * 1024 * 1024)
      o.println("  that's " + mhs / (1024.0 * 1024 * 1024) + " gigabytes");
    runTest();

    System.exit(0);
  }

  public static int doInner (int size) 
    throws // Throwable, 
      VM_PragmaNoInline 
  {
    for (int j=0; j<metaSize; j++) {
      if (cursor >= metaSize) {
	o.println("cursor >= metaSize; we won't allocate more memory.  This should never happen.");
      }
      wasAllocating = size;
      junk[cursor++] = new byte[(int) size];
      tot += size;
      wasAllocating = 0;
      o.println("growthFactor = " + growthFactor);
      size *= growthFactor;
      o.println("Size = " + size);
    }
    return size;
  }

  public static void runTest() 
    throws // Throwable, 
      VM_PragmaNoInline 
  {
    // Whoops!   This does not do what we thought it would; hides the global.
    // double growthFactor = 1.0;
    for (int i=1; i<=10; i++) {
      growthFactor *= metaGrowthFactor;
      growthFactor = ((int) (100 * growthFactor)) / 100.0;
      o.println("Starting round " + i + " with growthFactor " + growthFactor);
      o.println("Allocating an array with room for " + metaSize + " objects");
	
      junk = new Object[metaSize];
      tot = 0;
	
      o.println("  Allocating until exception thrown");
      int size = itemSize;
      try {
	size = doInner(size);
      }
      catch (OutOfMemoryError e) {
	junk = null;  // kills everything
	cursor = 0;
	o.println("  Caught OutOfMemory - freeing now");  // this allocates; must follow nulling

	//	  o.println("  Maximum size reached is " + size);

	o.println("  Had " + tot + " bytes allocated; failed trying to allocate " + wasAllocating + " bytes");
      }
      if (junk != null) {
	junk = null;
	o.println("  Had " + tot + " bytes allocated; kept on allocating until we couldn't go any further; this should never happen.");
      }
    }
    System.out.println("Overall: SUCCESS");
  }
}

