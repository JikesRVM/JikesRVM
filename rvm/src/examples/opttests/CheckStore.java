/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
class CheckStore {

  public static void main(String[] args) {
     run();
  }
  
  public static boolean run() {
    System.out.println("CheckStore prints: ");
    try {
      run1(new Float(0));
    } catch (ArrayStoreException e) {
      System.out.println(" so1.");
    }
    try {
      run2(1, new Integer(1));
    } catch (ArrayStoreException e) {
      System.out.println(" so2.");
    }
    return true;
  }


  static void run1(Object input) {

    Object[] n = new Integer[1];
    n[0] = new Integer(0);
    n[0] = input;
  }

  static Object[] global = new Object[2];

  static void run3(Object input) {

    Object[] n = new Object[1];
    n[0] = input;
    global[0] = new Integer(0);
  }


  static void run2(int a, Object elem) {
     Object[] array;
     if (a < 0) 
        array = new Object[2];
     else
        array = new String[2];
     array[0] = elem;
  }

}
