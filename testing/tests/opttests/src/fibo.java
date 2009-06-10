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
public class fibo {

  public static void main(String[] args) {
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
