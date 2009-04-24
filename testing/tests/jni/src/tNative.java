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
class tNative {
  public static native int nativeFoo(int count);

  public static void main(String[] args) {
    System.out.println("Attempting to load dynamic library ...");
    System.out.println("(the LIBPATH env variable must be set for this directory)");

    System.loadLibrary("tNative");

    int returnValue = nativeFoo(17);
    System.out.println("First nativeFoo return " + returnValue);

    returnValue = nativeFoo(30);
    System.out.println("Second nativeFoo return " + returnValue);

  }
}
