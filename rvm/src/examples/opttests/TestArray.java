/*
 * (C) Copyright IBM Corp. 2001
 */
class TestArray {

   static int array[] = new int[10];
   static int temp;

   public static void main(String args[]) {
     run();
   }

   static boolean run() {
     try {
     temp = array[-1];
     } catch (Exception e) {
        System.out.println(e);
     }
     try {
        run1(11);
     } catch(Exception e) {
        System.out.println(e);
     }
     return true;
   }

   static void run1(int a) {
     temp = array[a];
   }
}
