/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */

class ExceptionTest5 {


  static int testa[] = new int[3];
  public static void main(String[] args) {

    run();
  }

 public static boolean run() {
    try {
      if (testa.length <= 3) 
         throw new IndexOutOfBoundsException("I am IndexOBE");
      testa[3] = 0;
    } catch (NullPointerException n) {
        System.out.println( n + ", but caught by NullPointCheckException");
    } catch (ArithmeticException a) {
        System.out.println( a + ", but caught by ArithMeticException");
    } catch (IndexOutOfBoundsException e5) {
      System.out.println(" IndexOutOfBoundsException caught");
    }
    System.out.println(" At End");

    return true;
  }

}
