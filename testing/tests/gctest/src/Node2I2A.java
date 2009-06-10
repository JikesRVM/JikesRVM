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
class Node2I2A {

  int data1;
  int data2;
  Node2I2A car;
  Node2I2A cdr;

  static double measuredObjectSize = 0.0;
  static int objectSize = 0;
  static Object fakeLock = new Object();

  // This should require no more than 28 Megs even with 3 word headers
  //
  public static void computeObjectSize() {
    int estimateSize = 1000000;
    while (true) {
      System.gc();
      long start = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
      Node2I2A head = new Node2I2A();
      Node2I2A cur = head;
      for (int i=0; i<estimateSize; i++) {
        cur.cdr = new Node2I2A();
        cur = cur.cdr;
      }
      synchronized(fakeLock) {
        // This seemingly useless lock operation prevents the optimizing
        // compiler from doing redundant load elimination of the internal fields
        // used to compute freeMemory in the watson semispace collector.
        // Fairly amusing...at the HIR level there is of course nothing in the above loop
        // that would cause the compiler to think that the fields of VM_ContiguousHeap get changed.
        // Arguably the fields in question should be marked volatile, but injecting this
        // fake lock operation here causes us to obey the java memory model and not do the
        // redundant load elimination.
      }
      long end = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
      long used = end - start;
      if (used > 0) {
        measuredObjectSize = used / ((double) estimateSize);
        objectSize = (int) (measuredObjectSize + 0.5); // round to byte
        objectSize = (objectSize + 2) / 4 * 4; // round to word
        if (objectSize > 16)
          break;
      }
      estimateSize = (int) (0.75 * estimateSize);
      System.out.println("GC occured since used memory decreased after allocation or implausible object size obtained.  Retrying with " + estimateSize + " objects.");
    }
  }

  public static Node2I2A createTree(int nodes) {
    if (nodes == 0) return null;
    int children = nodes - 1;
    int left = children / 2;
    int right = children - left;
    Node2I2A self = new Node2I2A();
    self.car = createTree(left);
    self.cdr = createTree(right);
    return self;
  }
}
