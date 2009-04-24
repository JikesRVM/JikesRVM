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
package org.jikesrvm;

import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMember;

/**
 * This interface is implemented by org.jikesrvm.PrintContainer.  The
 * interfaces is used by our java.lang.Throwable to print stack traces.
 */
@SuppressWarnings("unused")
// Suppress the unused import warning as per comment above
public abstract class PrintLN {
  //  PrintLN(PrintWriter out);
  //  PrintLN(PrintStream out);
  public boolean isSysWrite() { return false; }

  public boolean isSystemErr() { return false; }

  public abstract void flush();

  public abstract void println();

  public void println(String s) {
    print(s);
    println();
  }

  public abstract void print(String s);

  /* Here, we are writing code to make sure that we do not rely upon any
   * external memory accesses. */
  // largest power of 10 representable as a Java integer.
  // (max int is            2147483647)
  static final int max_int_pow10 = 1000000000;

  public void print(int n) {
    boolean suppress_leading_zero = true;
    if (n == 0x80000000) {
      print("-2147483648");
      return;
    } else if (n == 0) {
      print('0');
      return;
    } else if (n < 0) {
      print('-');
      n = -n;
    }
    /* We now have a positive # of the proper range.  Will need to exit from
the bottom of the loop. */
    for (int p = max_int_pow10; p >= 1; p /= 10) {
      int digit = n / p;
      n -= digit * p;
      if (digit == 0 && suppress_leading_zero) {
        continue;
      }
      suppress_leading_zero = false;
      char c = (char) ('0' + digit);
      print(c);
    }
  }

  public void printHex(int n) {
    print("0x");
    // print exactly 8 hexadec. digits.
    for (int i = 32 - 4; i >= 0; i -= 4) {
      int digit = (n >>> i) & 0x0000000F;               // fill with 0 bits.
      char c;

      if (digit <= 9) {
        c = (char) ('0' + digit);
      } else {
        c = (char) ('A' + (digit - 10));
      }
      print(c);
    }
  }

  public abstract void print(char c);

//   /** Print the name of the class to which the argument belongs.
//    *
//    * @param o Print the name of the class to which o belongs. */
//   public void printClassName(Object o) {

//   }

  /** Print the name of the class represented by the class descriptor.
   *
   * @param descriptor The class descriptor whose name we'll print. */
  public void printClassName(Atom descriptor) {
    // toByteArray does not allocate; just returns an existing descriptor.
    byte[] val = descriptor.toByteArray();

    if (VM.VerifyAssertions) {
      VM._assert(val[0] == 'L' && val[val.length - 1] == ';');
    }
    for (int i = 1; i < val.length - 1; ++i) {
      char c = (char) val[i];
      if (c == '/') {
        print('.');
      } else {
        print(c);
      }
    }
    // We could do this in an emergency.  But we don't need to.
    // print(descriptor);
  }

  /* Code related to Atom.classNameFromDescriptor() */
  public void print(RVMClass class_) {
    // getDescriptor() does no allocation.
    Atom descriptor = class_.getDescriptor();
    printClassName(descriptor);
  }

  // A kludgy alternative:
//     public void print(RVMClass c) {
//       Atom descriptor = c.getDescriptor();
//       try {
//      print(descriptor.classNameFromDescriptor());
//       } catch(OutOfMemoryError e) {
//      print(descriptor);
//       }
//     }

  // No such method:
  //public void print(RVMClass c) {
  //      VM.sysWrite(c);
  //    }

  /* Here we need to imitate the work that would normally be done by
* RVMMember.toString() (which RVMMethod.toString() inherits) */

  public void print(RVMMember m) {
    print(m.getDeclaringClass()); // RVMClass
    print('.');
    print(m.getName());
    print(' ');
    print(m.getDescriptor());
  }

  public void print(Atom a) {
    byte[] val;
    if (a != null) {
      val = a.toByteArray();
      for (byte aVal : val) {
        print((char) aVal);
      }
    } else {
      print("(null)");
    }
  }
}

