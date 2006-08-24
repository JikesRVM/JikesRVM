/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */

class ExceptionTest4 {


  static int testa[] = new int[3];
  public static void main(String[] args) {

    run();
  }

 public static boolean run() {
    System.out.println(divide(1,0));
    return true;
  }

  static int divide(int a, int b) {
     try {
         return a/b;
     }
     catch(ArithmeticException e) {
         return a + 1;
     }
  }

}
