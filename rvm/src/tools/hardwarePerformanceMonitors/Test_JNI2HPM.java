/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * This class provides a jni interface to access the PowerPC
 * hardware performance monitors.
 * Trampoline code to hpm.c methods.
 * No dependency on "C" include files.
 *
 * @author Peter Sweeney
 * @date 6/27/2001
 */

import JNI2HPM;
import java.lang.Math;

public class Test_JNI2HPM 
{
  private static final boolean debug = false;
  /**
   * Test driver.
   */
  public static void main (String argv[])
  {
    final int sequentialAccess        =  1;	//         4 bytes
    final int smallerCacheLineAccess  =  2;	//         8 bytes
    final int smallCacheLineAccess    =  4;	//        16 bytes
    final int mediumCacheLineAccess   =  8;	//        32 bytes
    final int largeCacheLineAccess    = 16;	//        64 bytes
    final int largerCacheLineAccess   = 32;	//       128 bytes
    final int smallPageAccess      =   256;	//     1 Kbytes
    final int mediumPageAccess     =  1024;	//     4 Kbytes
    final int largePageAccess      =  4096;	//    16 Kbytes
    final int largerPageAccess     = 16384;	//    64 Kbytes
    final int largestPageAccess    = 65536;	//   256 Kbytes
    double base = 2;
    double exp  = 18;
    final int limit = (int)java.lang.Math.pow(base,exp);// 1,000 Kbytes
    int[] a = new int[limit];

    if(debug)System.out.println("Test_JNI2HPM.main()");

    // set system properties.
    System.setProperty("HPM_EVENT_1", "5");	// icache misses
    System.setProperty("HPM_EVENT_2", "6");	// data cached misses
    System.setProperty("HPM_EVENT_3", "1");	// processor cycles
    //    System.setProperty("HPM_EVENT_4", "2");	// instructions completed
    // initialize HPM
    JNI2HPM.init();

    // warm up
    accessByRectangles(sequentialAccess, limit, a);
    // access array sequentially
    accessByRectangles(sequentialAccess, limit, a);
    accessByRectangles(sequentialAccess, limit, a);
    // access array across cache lines
    accessByRectangles(smallerCacheLineAccess, limit, a);
    accessByRectangles(smallCacheLineAccess, limit, a);
    accessByRectangles(mediumCacheLineAccess, limit, a);
    accessByRectangles(largeCacheLineAccess, limit, a);
    accessByRectangles(largerCacheLineAccess, limit, a);
    // access array across pages
    accessByRectangles(smallPageAccess, limit, a);
    accessByRectangles(mediumPageAccess, limit, a);
    //	accessByRectangles(largePageAccess);
    //	accessByRectangles(largerPageAccess);
    accessByRectangles(largestPageAccess, limit, a);

  }
  /*
   * Access array in chunksize strides.
   * The parameter chunkSize determines how many times a chunk
   * is accessed after all the other chunks are accessed.
   * The number of chunks determines the number of active cache lines.
   *
   * @param chunkSize  how big is a chunk
   * @param limit      number of elements in array
   * @param a          array
   */
  static void accessByRectangles(int chunkSize, int limit, int []a) {
    
    long startTime = System.currentTimeMillis();
    int n_chunks = limit/chunkSize;
    System.out.println("accessByRectangles("+chunkSize+", "+limit+
		       ") # chunks "+n_chunks+" startTime "+startTime);

    // stop, reset, and start HPM counters
    JNI2HPM.hpmStopCounting();
    JNI2HPM.hpmResetCounters();
    JNI2HPM.hpmStartCounting();

    int accesses = 0;
    for (int i = 0; i < chunkSize; i++) {	// within a chunk
      for (int j = i; j < limit; j+=chunkSize) { // across chunks
	for (int k = j; k < j+chunkSize && k < limit; k+=chunkSize) {
	  //	    System.out.println("  i: "+i+", j: "+j+", k: "+k);
	  a[k]=chunkSize;
	  accesses++;
	}
      }
    }
    // write HPM counters.
    JNI2HPM.dump();

    long endTime = System.currentTimeMillis();
    long time = endTime - startTime;
    System.out.println("accessByRectangles("+chunkSize+") accesses "+accesses+
		       " endTime "+endTime+
		       " totalTime "+time+" ms");
  }
}
