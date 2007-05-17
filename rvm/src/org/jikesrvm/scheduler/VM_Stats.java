/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.scheduler;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public final class VM_Stats {

  static void println() { VM.sysWrite("\n"); }

  static void print(String s) { VM.sysWrite(s); }

  static void println(String s) {
    print(s);
    println();
  }

  static void print(int i) { VM.sysWrite(i); }

  static void println(int i) {
    print(i);
    println();
  }

  static void print(String s, int i) {
    print(s);
    print(i);
  }

  static void println(String s, int i) {
    print(s, i);
    println();
  }

  public static void percentage(int numerator, int denominator, String quantity) {
    print("\t");
    if (denominator > 0) {
      print((int) ((((double) numerator) * 100.0) / ((double) denominator)));
    } else {
      print("0");
    }
    print("% of ");
    println(quantity);
  }

  static void percentage(long numerator, long denominator, String quantity) {
    print("\t");
    if (denominator > 0L) {
      print((int) ((((double) numerator) * 100.0) / ((double) denominator)));
    } else {
      print("0");
    }
    print("% of ");
    println(quantity);
  }

}
