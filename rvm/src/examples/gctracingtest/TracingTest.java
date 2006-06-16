/*
 * (C) Copyright Jikes RVM project
 */
//$Id$

/**
 * @author Matther Hertz
 */
public class TracingTest {
  public static Integer[] arr;
  
  public static void main(String[] args) {
    arr = new Integer[20];
    for (int i = 0; i < 20; i++) {
      arr[i] = new Integer(i);
      System.out.println(arr[i].toString());
    }
    System.gc();
    for (int i = 0; i < 20; i++) {
      arr[i] = new Integer(i * 2);
      if (i != 0)
        System.out.println(arr[i-1].toString());
    }
    System.gc();
    arr = new Integer[10];
    for (int i = 0; i < 10; i+=2) {
      arr[i] = new Integer(i>>1);
      if (i != 0)
        System.out.println(arr[i-2].toString());
    }
    System.out.println("TracingTest Finished");
  }
}
