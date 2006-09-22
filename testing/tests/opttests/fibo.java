/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * Use the following command to compile the program with full debug output:
 *
 *     jsh java optCompilerDriver +depgraph +ir +low +burs +regalloc fibo 
 *
 * @author unascribed
 */

public class fibo {

  public static void main(String args[]) {
     run();
  }

  static boolean run() {
    int i = fibo.fib(22);
    System.out.println("Fibo returned: " + i);
    return true;
  }

static int fib(int x) {  /* compute Fibonacci number recursively */
    if (x > 2)
       return (fib(x-1) + fib(x-2));
    else
       return (1);
}

}
