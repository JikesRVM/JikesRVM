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

  public static void main(String args[])  //throws Throwable 
  {

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
      junk[cursor++] = new byte[(int) size];
      size *= growthFactor;
    }
    return size;
  }

  public static void runTest() 
    throws // Throwable, 
	   VM_PragmaNoInline 
  {

    double growthFactor = 1.0;
    for (int i=1; i<=10; i++) {
	growthFactor *= metaGrowthFactor;
	growthFactor = ((int) (100 * growthFactor)) / 100.0;
	o.println("Starting round " + i + " with growthFactor " + growthFactor);
	junk = new Object[metaSize];
	o.println("  Allocating until exception thrown");
      int size = itemSize;
      try {
	  size = doInner(size);
      }
      catch (OutOfMemoryError e) {
	  junk = null;  // kills everything
	  cursor = 0;
	  System.out.println("  Caught OutOfMemory - freeing now");  // this allocates; must follow nulling
	  System.out.println("  Maximum size reached is " + size);
      }
    }
    System.out.println("Overall: SUCCESS");
  }


}

