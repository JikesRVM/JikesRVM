/*
 * (C) Copyright IBM Corp. 2001
 */

// $Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;

/**
 *
 * @author David Bacon
 */

final class VM_Stats implements Uninterruptible {

  static final void println() { VM.sysWrite("\n"); }
  static final void print(String s) { VM.sysWrite(s); }
  static final void println(String s) { print(s); println(); }
  static final void print(int i) { VM.sysWrite(i); }
  static final void println(int i) { print(i); println(); }
  static final void print(String s, int i) { print(s); print(i); }
  static final void println(String s, int i) { print(s,i); println(); }

  static void percentage (int numerator, int denominator, String quantity) {
    print("\t");
    if (denominator > 0) 
      print((int) ((((double) numerator) * 100.0) / ((double) denominator)));
    else
      print("0");
    print("% of ");
    println(quantity);
  }

  static void percentage (long numerator, long denominator, String quantity) {
    print("\t");
    if (denominator > 0l) 
      print((int) ((((double) numerator) * 100.0) / ((double) denominator)));
    else
      print("0");
    print("% of ");
    println(quantity);
  }

}
