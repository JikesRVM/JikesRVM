/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * Test of whether we enforce member access with newInstance()
 *
 * @author Stephen Fink
 * @author Eugene Gluzberg
 */

class tNewInstance {
   public static void main(String args[]) {
      System.out.println("tNewInstance...");
      try {
         Class klass = Class.forName("OnlyPrivateConstructor");
         Object o = klass.newInstance(); 
      } catch (IllegalAccessException e2) {
         e2.printStackTrace();
         System.out.println("Test SUCCEEDED");
         return;
      } catch (Exception e) {
         e.printStackTrace();
      }
      System.out.println("Test FAILED");
   }
}

class OnlyPrivateConstructor{
   private OnlyPrivateConstructor() {}
}

