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

  static int itemSize = 4 * 1024; 
  static int metaSize = 1024 * 1024;
  static Object [] junk;
  static int cursor = 0;

  public static void main(String args[])  throws Throwable {

    runTest();

    System.exit(0);
  }

  public static void runTest() throws Throwable {

    for (int i=1; i<=10; i++) {
	System.out.println("Starting round " + i);
	junk = new Object[metaSize];
	System.out.println("  Allocating until exception thrown");
	try {
	    while (true)
		junk[cursor++] = new byte[itemSize];
	}
	catch (OutOfMemoryError e) {
	    System.out.println("  Caught OutOfMemory - freeing now");
	    junk = null;  // kills everything
	    cursor = 0;
	}
    }
    System.out.println("Overall: SUCCESS");
  }

}
