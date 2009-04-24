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
public class stack {

  public static void main(String[] arg) {
    int disk = 3;
    if (arg.length > 0) disk = Integer.parseInt(arg[0]);
    overflow(disk);
  }

  public static void overflow(int n) {
    if (n > 0)
       overflow(n-1);
  }
}
