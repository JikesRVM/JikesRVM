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
    int estimateSize = 200000;
    long start = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    System.out.println("start = " + start);
    Node2I2A head = new Node2I2A();
    Node2I2A cur = head;
    for (int i=0; i<estimateSize; i++) {
      cur.cdr = new Node2I2A();
      cur = cur.cdr;
    }
    long end = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    System.out.println("end = " + end);
    measuredObjectSize = (end - start) / ((double) estimateSize);
    objectSize = (int) (measuredObjectSize + 0.5);
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
