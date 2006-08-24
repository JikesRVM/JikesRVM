/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/*
 * @author unascribed
 */

class TestStackAccess
   {
   public static void main(String args[])
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestStackAccess");

      SystemOut.print("\nwant: 01234\n got: ");
      istoreload();
      SystemOut.println();

      SystemOut.print("\nwant: 0.01.02.03.04.0\n got: ");
      fstoreload();
      SystemOut.println();

      SystemOut.print("\nwant: nullnullnullnullnull\n got: ");
      astoreload();
      SystemOut.println();

      SystemOut.print("\nwant: 001012012301234\n got: ");
      lstoreload0(); lstoreload1(); lstoreload2(); lstoreload3(); lstoreload();
      SystemOut.println();

      SystemOut.print("\nwant: 0.001.0012.00123.001234.0\n got: ");
      dstoreload0(); dstoreload1(); dstoreload2(); dstoreload3(); dstoreload();
      SystemOut.println();

      SystemOut.print("\nwant: 12211\n got: ");
      dup();
      SystemOut.println();

      SystemOut.print("\nwant: x\n got: ");
      swap();
      SystemOut.println();

      //      SystemOut.print("\ntest pop  and pop2 ");
      pop();
      pop2();
      //      SystemOut.println();

      }
      
   static void 
   pop()
      {
      for ( int i = 0; i < 10000; i++)
         returnint();                  // pop
      }

   static void 
   pop2()
      {
      for ( int i = 0; i < 10000; i++)
         returnlong();                  // pop2
      }

   static int  returnint()   { return -1;}

   static long returnlong()  { return -1;}


   static void
   istoreload()
      {
      int x0 = 0;            // istore_0
      int x1 = 1;            // istore_1
      int x2 = 2;            // istore_2
      int x3 = 3;            // istore_3
      int x4 = 4;            // istore
      
      SystemOut.print(x0);   // iload_0
      SystemOut.print(x1);   // iload_1
      SystemOut.print(x2);   // iload_2
      SystemOut.print(x3);   // iload_3
      SystemOut.print(x4);   // iload
      }
   
   static void
   fstoreload()
      {
      float x0 = 0;          // fstore_0
      float x1 = 1;          // fstore_1
      float x2 = 2;          // fstore_2
      float x3 = 3;          // fstore_3
      float x4 = 4;          // fstore
      
      SystemOut.print(x0);   // fload_0
      SystemOut.print(x1);   // fload_1
      SystemOut.print(x2);   // fload_2
      SystemOut.print(x3);   // fload_3
      SystemOut.print(x4);   // fload
      }
   
   static void
   astoreload()
      {
      Object x0 = null;      // astore_0
      Object x1 = null;      // astore_1
      Object x2 = null;      // astore_2
      Object x3 = null;      // astore_3
      Object x4 = null;      // astore
      
      SystemOut.print(x0);   // aload_0
      SystemOut.print(x1);   // aload_1
      SystemOut.print(x2);   // aload_2
      SystemOut.print(x3);   // aload_3
      SystemOut.print(x4);   // aload
      }
   
   static void
   lstoreload0()
      {
      long x0 = 0;           // lstore_0
      SystemOut.print(x0);   // lload_0
      }
   
   static void
   lstoreload1()
      {
      int  x0 = 0;
      long x1 = 1;           // lstore_1
      
      SystemOut.print(x0);
      SystemOut.print(x1);   // lload_1
      }
   
   static void
   lstoreload2()
      {
      int  x0 = 0;
      int  x1 = 1;
      long x2 = 2;           // lstore_2
      
      SystemOut.print(x0);
      SystemOut.print(x1);
      SystemOut.print(x2);   // lload_2
      }
   
   
   static void
   lstoreload3()
      {
      int  x0 = 0;
      int  x1 = 1;
      int  x2 = 2;
      long x3 = 3;           // lstore_3
      
      SystemOut.print(x0);
      SystemOut.print(x1);
      SystemOut.print(x2);
      SystemOut.print(x3);   // lload_3
      }
   
   static void
   lstoreload()
      {
      int  x0 = 0;
      int  x1 = 1;
      int  x2 = 2;
      int  x3 = 3;
      long x4 = 4;           // lstore
      
      SystemOut.print(x0);
      SystemOut.print(x1);
      SystemOut.print(x2);
      SystemOut.print(x3);
      SystemOut.print(x4);   // lload
      }
   
   static void
   dstoreload0()
      {
      double x0 = 0;         // dstore_0
      SystemOut.print(x0);   // dload_0
      }
   
   static void
   dstoreload1()
      {
      int  x0 = 0;
      double x1 = 1;         // dstore_1
      
      SystemOut.print(x0);
      SystemOut.print(x1);   // dload_1
      }
   
   static void
   dstoreload2()
      {
      int  x0 = 0;
      int  x1 = 1;
      double x2 = 2;         // dstore_2
      
      SystemOut.print(x0);
      SystemOut.print(x1);
      SystemOut.print(x2);   // dload_2
      }
   
   static void
   dstoreload3()
      {
      int  x0 = 0;
      int  x1 = 1;
      int  x2 = 2;
      double x3 = 3;         // dstore_3
      
      SystemOut.print(x0);
      SystemOut.print(x1);
      SystemOut.print(x2);
      SystemOut.print(x3);   // dload_3
      }
   
   static void
   dstoreload()
      {
      int  x0 = 0;
      int  x1 = 1;
      int  x2 = 2;
      int  x3 = 3;
      double x4 = 4;          // dstore
      
      SystemOut.print(x0);
      SystemOut.print(x1);
      SystemOut.print(x2);
      SystemOut.print(x3);
      SystemOut.print(x4);   // dload
      }

   static long sa, sb;
   long         a,  b;

   int   pos   = 0;
   int   buf[] = { 1, 2 };

   int  lpos   = 0;
   long lbuf[] = { 1, 2 };
   
   static void
   dup()
      {
      TestStackAccess t = new TestStackAccess(); // dup

      SystemOut.print(t.buf[t.pos++]);           // dup_x1

      sa = sb = 1;                               // dup2
      SystemOut.print(sa + sb);

      t.a = t.b = 1;                             // dup2_x1
      SystemOut.print(t.a + t.b);

      SystemOut.print(t.lbuf[t.lpos]++);         // dup2_x2

      int x[] = new int[1];
      switch(x[0] = 1)                           // dup_x2
         {
         case 1: SystemOut.print(1);
         }
      }
      
   static void
   swap()
      {
      String s = "";
      s += "x";             // swap
      SystemOut.println(s);
      }
   }
