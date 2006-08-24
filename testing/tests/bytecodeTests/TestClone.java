/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */

class TestClone implements Cloneable
   {
   String id;

   TestClone(String id)
      {
      this.id = id;
      }

   void 
   setId(String id)
      {
      this.id = id;
      }

   public String
   toString()
      {
      return id;
      }
      
   public static void 
   main(String args[])
      throws CloneNotSupportedException
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      throws CloneNotSupportedException
      {
      SystemOut.println("TestClone");
      
      //
      // clone scalar
      //
      
      TestClone a = new TestClone("a");
      TestClone b = (TestClone)a.clone();

      SystemOut.println("original: " + a);
      SystemOut.println("clone:    " + b);
      SystemOut.println("equals says: " + a.equals(b));
      SystemOut.println("==     says: " + (a == b));
      
      a.setId("aa");
      
      SystemOut.println();
      SystemOut.println("after changing object value");
      SystemOut.println();
      
      SystemOut.println("original: " + a);
      SystemOut.println("clone:    " + b);
      SystemOut.println("equals says: " + a.equals(b));
      SystemOut.println("==     says: " + (a == b));
      SystemOut.println();
      
      //
      // clone array
      //
      
      TestClone c[][] = new TestClone[2][3];
      for (int i = 0; i < 2; ++i)
         for (int j = 0; j < 3; ++j)
            c[i][j] = new TestClone(i + "" + j);

      SystemOut.println("original: ");
      for (int i = 0; i < 2; ++i)
         {
         for (int j = 0; j < 3; ++j)
            SystemOut.print(c[i][j] + " ");
         SystemOut.println();
         }

      TestClone d[][] = (TestClone[][])c.clone();
      
      SystemOut.println("clone: ");
      for (int i = 0; i < 2; ++i)
         {
         for (int j = 0; j < 3; ++j)
            SystemOut.print(d[i][j] + " ");
         SystemOut.println();
         }
      
      SystemOut.println("equals says: " + c.equals(d));
      SystemOut.println("==     says: " + (c == d));
      
      c[1][1].setId("xx");
      
      SystemOut.println();
      SystemOut.println("after changing object value");
      SystemOut.println();
      
      SystemOut.println("original: ");
      for (int i = 0; i < 2; ++i)
         {
         for (int j = 0; j < 3; ++j)
            SystemOut.print(c[i][j] + " ");
         SystemOut.println();
         }

      SystemOut.println("clone: ");
      for (int i = 0; i < 2; ++i)
         {
         for (int j = 0; j < 3; ++j)
            SystemOut.print(d[i][j] + " ");
         SystemOut.println();
         }
      
      SystemOut.println("equals says: " + c.equals(d));
      SystemOut.println("==     says: " + (c == d));
      
      c[1][1] = new TestClone("zz");
      
      SystemOut.println();
      SystemOut.println("after changing element");
      SystemOut.println();
      
      SystemOut.println("original: ");
      for (int i = 0; i < 2; ++i)
         {
         for (int j = 0; j < 3; ++j)
            SystemOut.print(c[i][j] + " ");
         SystemOut.println();
         }

      SystemOut.println("clone: ");
      for (int i = 0; i < 2; ++i)
         {
         for (int j = 0; j < 3; ++j)
            SystemOut.print(d[i][j] + " ");
         SystemOut.println();
         }
      
      SystemOut.println("equals says: " + c.equals(d));
      SystemOut.println("==     says: " + (c == d));
      
      c[1] = new TestClone[3];
      
      SystemOut.println();
      SystemOut.println("after changing row");
      SystemOut.println();
      
      SystemOut.println("original: ");
      for (int i = 0; i < 2; ++i)
         {
         for (int j = 0; j < 3; ++j)
            SystemOut.print(c[i][j] + " ");
         SystemOut.println();
         }

      SystemOut.println("clone: ");
      for (int i = 0; i < 2; ++i)
         {
         for (int j = 0; j < 3; ++j)
            SystemOut.print(d[i][j] + " ");
         SystemOut.println();
         }
      
      SystemOut.println("equals says: " + c.equals(d));
      SystemOut.println("==     says: " + (c == d));
      
      }
   }
