/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/*
 * @author Perry Cheng
 */

import com.ibm.JikesRVM.VM_PragmaNoInline;
import java.lang.*;

class Exhaust {

  static int itemSize = 32;
  static int metaSize = 4 * 1024 * 1024;
  static Object [] junk;
  static int cursor = 0;
  static double growthFactor = 1.0;
  static double metaGrowthFactor = 1.1;
  static int rounds = 10;

  public static void main(String args[])  throws Throwable {

    runTest();

    System.exit(0);
  }

  public static void runTest() throws Throwable {

    double growthFactor = 1.0;
    for (int i=1; i<=10; i++) {
	growthFactor *= metaGrowthFactor;
	growthFactor = ((int) (100 * growthFactor)) / 100.0;
	System.out.println("Starting round " + i + " with growthFactor " + growthFactor);
	junk = new Object[metaSize];
	System.out.println("  Allocating until exception thrown");
	int size = itemSize;
	try {
	  for (int j=0; j<metaSize; j++) {
	    junk[cursor++] = new byte[(int) size];
	    size *= growthFactor;
	  }
	}
	catch (OutOfMemoryError e) {
	    System.out.println("  Caught OutOfMemory - freeing now");
	    System.out.println("  Maximum size reached is " + size);
	    junk = null;  // kills everything
	    cursor = 0;
	}
    }
    System.out.println("Overall: SUCCESS");
  }

}

