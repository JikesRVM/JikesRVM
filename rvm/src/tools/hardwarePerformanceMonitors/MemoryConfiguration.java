/*
 * (C) Copyright IBM Corp. 2001
 */

//$Id$

/**
 * This class determines the data cache and tlb sizes of the machine 
 * that it is run on by determine 
 * This class provides a jni interface to access the PowerPC
 * hardware performance monitors.
 *
 * @author Peter Sweeney
 * creation date 3/22/2002
 */
import JNI2HPM;
import java.lang.Math;

public class MemoryConfiguration 
{
  private static final boolean debug = false;

  // where to find native method implementations
  static {
    System.loadLibrary("jni2hpm");
  }

  /**
   * Test driver.
   * Memory configuration details interested in:
   *   cache size
   *   cache line size
   *   cache set associativity
   *   cache miss cost 
   *   tlb size
   *   tlb set associativity 
   *   tlb miss cost
   *   page size
   *   page miss cost
   *
   * Determine cache size by picking largest array size
   * that is a power of two such that next largest array size
   * causes significant additional number of cache misses
   * on second access of data.
   * Determine line size when increase stride results in
   * significant increase in cache misses.
   * Determine set associativity.
   * Determine TLB size when second run over data
   * results in same number of TLB misses.
   */
  public static void main (String argv[])
  {
    if(debug)System.out.println("MemoryConfigurations.main()");

    int sizes[] = {       1,        2,        4,        8,       16, //  0- 4
			 32,       64,      128,      256,      512, //  5- 9
		       1024,     2048,     4096,     8192,    16348, // 10-14
		      32768,    65536,   131072,   262144,   524288, // 15-19
		    1048576,  2097152,  4194304,  8388508, 16777216  // 20-24
    };

    // Determine d cache size
    System.setProperty("HPM_EVENT_1", "5");	// icache misses
    System.setProperty("HPM_EVENT_2", "6");	// data cached misses
    System.setProperty("HPM_EVENT_3", "1");	// processor cycles
    System.setProperty("HPM_EVENT_4", "2");	// instructions completed
    // initialize HPM
    JNI2HPM.init();

    for (int i=10; i<sizes.length; i++) {
      int size = sizes[i];
      int a[] = new int[size];
      Result result = accesses(1, size, 1, a);
      System.out.println("accesses(1:"+size+":1) accesses "+result.accesses+
			 ", time "+result.time+
			 ", i miss "+result.i_miss+"("+result.i_miss_pc+
			 ") d miss "+result.d_miss+"("+result.d_miss_pc+")");
      
    }

  }
  /*
   * Access stride array elements across rows.
   * By accessing the array twice can determine if
   * underlying resource constraints are invalidated.
   *
   * @param n_rows     number of rows
   * @param n_columns  number of columns
   * @param stride     how many elements are access concurrently
   * @param a          array
   */
  static Result accesses(int n_rows, int n_columns, int stride, int[] a) 
  {
    Result result1 = access(n_rows, n_columns, stride, a);
    Result result2 = access(n_rows, n_columns, stride, a);
    if (result1.accesses != result2.accesses) {
      System.err.println("***accessess("+n_rows+":"+n_columns+":"+stride+
			 ") accesses "+result1.accesses+" != "+result2.accesses+
			 "!***");
    }
    int i_miss_pc = 0;
    int d_miss_pc = 0;
    if (result1.i_miss == 0) {
      System.err.println("***accessess("+n_rows+":"+n_columns+":"+stride+
			 ") result1.i_miss == 0!***");
    } else {
      i_miss_pc = (int)((result1.i_miss+result2.i_miss)/result1.i_miss)/100;
    }
    if (result1.d_miss == 0) {
      System.err.println("***accessess("+n_rows+":"+n_columns+":"+stride+
			 ") result1.d_miss == 0!***");
    } else {
      d_miss_pc = (int)((result1.d_miss+result2.d_miss)/result1.d_miss)/100;
    }
    Result result = new Result(result1.time, result1.accesses, result1.i_miss, i_miss_pc,
			       result1.d_miss, d_miss_pc);
    return result;
  }

  /*
   * Access array elements and read performance.
   */
  static Result access(int n_rows, int n_columns, int stride, int[] a) 
  {
    // stop, reset, and start HPM counters
    JNI2HPM.hpmStopCounting(); 
    JNI2HPM.hpmResetCounters(); 
    JNI2HPM.hpmStartCounting();

    long startTime = System.currentTimeMillis();
    int accesses = 0;
    for (int column = 0; column < n_columns; column+=stride) {	// column access
      
      for (int row  = 0; row    < n_rows;    row+=n_columns) { 	// row access

	int index = (row*n_columns)+column;
	for (int i = index; i < index+stride; i++) {		// element access
	  //	  System.out.println(i+" ("+row+":"+column+":"+stride+")  index "+index);
	  a[i] = i;
	  accesses++;
	}
      }
    }

    // write HPM counters.
    JNI2HPM.dump();

    // read HPM counters.
    if (JNI2HPM.hpmStopCounting() != JNI2HPM.OK_CODE) {
      System.err.print("JNI2HPM.report(): hpmStopCounting() failed!\n");
      System.exit(-1);
    }
    long i_misses = JNI2HPM.hpmGetCounter(1);
    long d_misses = JNI2HPM.hpmGetCounter(2);

    long endTime = System.currentTimeMillis();
    long time = endTime - startTime;
    Result result = new Result(time, accesses, i_misses, 0, d_misses, 0);
    return result;
  }
}

/*
 * multi-valued return result.
 */
class Result {
  long time = 0;
  int  accesses = 0;
  long i_miss = 0;
  long d_miss = 0;
  int  i_miss_pc = 0;
  int  d_miss_pc = 0;
  public Result(long time, int accesses, 
		long i_miss, int i_miss_pc,
		long d_miss, int d_miss_pc)
  {
    this.time      = time;
    this.accesses  = accesses;
    this.i_miss    = i_miss;
    this.i_miss_pc = i_miss_pc;
    this.d_miss    = d_miss;
    this.d_miss_pc = d_miss_pc;
  }
}
