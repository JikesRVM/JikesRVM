/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/*
 * @author Perry Cheng
 */

class Node2I2A {

  int data1;
  int data2;
  Node2I2A car;
  Node2I2A cdr;

  static double measuredObjectSize = 0.0;
  static int objectSize = 0;

  public static void computeObjectSize() {
    System.out.println("computeObjectSize entered");
    int estimateSize = 2000000;
    while (true) {
      System.gc();
      long start = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
      Node2I2A head = new Node2I2A();
      Node2I2A cur = head;
      for (int i=0; i<estimateSize; i++) {
	cur.cdr = new Node2I2A();
	cur = cur.cdr;
      }
      long end = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
      long used = end - start;
      if (used > 0) {
	measuredObjectSize = used / ((double) estimateSize);
	objectSize = (int) (measuredObjectSize + 0.5);
	if (objectSize > 16) 
	  break;
      }
      System.out.println("GC occured since used memory decreased after allocation or implausible object size obtained.  Retrying with fewer objects.");
      estimateSize = (int) (0.75 * estimateSize);
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
