/*
 * (C) Copyright IBM Corp. 2001
 */

class TestBarrier {
  
  static Object[] array = new Object[10]; 
  static test     Test  = new test();

  public static void main(String args[]) {
      run(new Object());
  }


  static boolean run(Object o) {
     boolean result = false;
     Test.field = o;
     array[0] = o;
     if (o == null) 
        result = true;
     return result;
  }


  static class test {
     Object field; 
  }
}
