/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
class TestInstanceOf
   {
   public static void main(String args[])
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestInstanceOf");

      Object o1     = new TestInstanceOf();   // source: a reference
      Object o2[]   = new TestInstanceOf[2];  // source: an array of references
      Object o3[][] = new Object[2][];        // source: an array of arrays
             o3[0]  = new TestInstanceOf[4];
             o3[1]  = new TestInstanceOf[4];
      int    o4[]   = new int [2];            // source: an array of primitives

      SystemOut.print("\nwant: true false false false\n got: ");  test(o1);
      SystemOut.print("\nwant: false true false false\n got: ");  test(o2);
      SystemOut.print("\nwant: false false false false\n got: "); test(o3);
      SystemOut.print("\nwant: false false false true\n got: ");  test(o4);

          o1 = (TestInstanceOf)o1;   //  ok
      SystemOut.println("\nwant: class cast exception");
      try {
          o1 = (String)o1;           //  exception
          }
      catch (ClassCastException e)
          {
          SystemOut.println(" got: class cast exception");
          }
      }

   static void test(Object o)
      {
      boolean b1 = o instanceof TestInstanceOf    ;  // target: a reference
      boolean b2 = o instanceof TestInstanceOf[]  ;  // target: an array of references
      boolean b3 = o instanceof TestInstanceOf[][];  // target: an array of arrays
      boolean b4 = o instanceof int []            ;  // target: an array of primitives

      SystemOut.println(b1 + " " + b2 + " " + b3 + " " + b4);
      }
   }
