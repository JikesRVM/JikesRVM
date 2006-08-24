/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
class TestArrayAccess
   {
   public static void main(String args[])
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestArrayAccess");

      SystemOut.println("\n--boolean_array--");       boolean_array();
      SystemOut.println("\n--byte_array--");          byte_array();
      SystemOut.println("\n--char_array--");          char_array();
      SystemOut.println("\n--short_array--");         short_array();
      SystemOut.println("\n--int_array--");           int_array();
      SystemOut.println("\n--long_array--");          long_array();

      SystemOut.println("\n--float_array--");         float_array();
      SystemOut.println("\n--double_array--");        double_array();
      SystemOut.println("\n--object_array--");        object_array();

      SystemOut.println("\n--array_array--");         array_array();
      SystemOut.println("\n--multi_int_array--");     multi_int_array();
      SystemOut.println("\n--multi_object_array--");  multi_object_array();
      SystemOut.println("\n--multi_partial_array--"); multi_partial_array();
      }

   static void
   boolean_array()
      {
      boolean array[] = new boolean[2];                                      // newarray type=4 eltsize=1
      boolean x0      = false;                                               // iconst_0
      boolean x1      = true;                                                // iconst_1

      array[0] = x0;                                                         // bastore
      array[1] = x1;                                                         // bastore

      SystemOut.print("\nwant: false\n got: "); SystemOut.println(array[0]); // baload
      SystemOut.print("\nwant: true\n got: ");  SystemOut.println(array[1]); // baload
      }

   static void
   byte_array()
      {
      byte array[] = new byte[2];                                            // newarray type=8 eltsize=1
      byte x0      = 127;
      byte x1      = -1;

      array[0] = x0;                                                         // bastore
      array[1] = x1;                                                         // bastore

      SystemOut.print("\nwant: 127\n got: "); SystemOut.println(array[0]);   // baload
      SystemOut.print("\nwant: -1\n got: ");  SystemOut.println(array[1]);   // baload (note sign extension)
      }

   static void
   char_array()
      {
      char array[] = new char[2];                                            // newarray type=5 eltsize=2
      char x0      = 0x7f41;
      char x1      = 0xff41;

      array[0] = x0;                                                         // castore
      array[1] = x1;                                                         // castore

      SystemOut.print("\nwant: 7f41\n got: ");  SystemOut.println(Integer.toHexString(array[0])); // caload
      SystemOut.print("\nwant: ff41\n got: ");  SystemOut.println(Integer.toHexString(array[1])); // caload (note zero extension)
      }

   static void
   short_array()
      {
      short array[] = new short[2];                                          // newarray type=9 eltsize=2
      short x0      = 32767;
      short x1      = -1;

      array[0] = x0;                                                         // sastore
      array[1] = x1;                                                         // sastore

      SystemOut.print("\nwant: 32767\n got: "); SystemOut.println(array[0]); // saload
      SystemOut.print("\nwant: -1\n got: ");    SystemOut.println(array[1]); // saload (note sign extension)
      }

   static void
   int_array()
      {
      int array[] = new int[2];                                              // newarray type=10 eltsize=4
      int x0      = 0;
      int x1      = 1;

      array[0] = x0;                                                         // iastore
      array[1] = x1;                                                         // iastore

      SystemOut.print("\nwant: 0\n got: "); SystemOut.println(array[0]);     // iaload
      SystemOut.print("\nwant: 1\n got: "); SystemOut.println(array[1]);     // iaload
      }

   static void
   long_array()
      {
      long array[] = new long[2];                                            // newarray type=11 eltsize=8
      long x0      = 0;
      long x1      = 1;

      array[0] = x0;                                                         // lastore
      array[1] = x1;                                                         // lastore

      SystemOut.print("\nwant: 0\n got: "); SystemOut.println(array[0]);     // laload
      SystemOut.print("\nwant: 1\n got: "); SystemOut.println(array[1]);     // laload
      }

   static void
   float_array()
      {
      float array[] = new float[2];                                          // newarray type=6 eltsize=4
      float x0      = 0;
      float x1      = 1;

      array[0] = x0;                                                         // fastore
      array[1] = x1;                                                         // fastore

      SystemOut.print("\nwant: 0.0\n got: "); SystemOut.println(array[0]);   // faload
      SystemOut.print("\nwant: 1.0\n got: "); SystemOut.println(array[1]);   // faload
      }

   static void
   double_array()
      {
      double array[] = new double[2];                                        // newarray type=7 eltsize=8
      double x0      = 0;
      double x1      = 1;

      array[0] = x0;                                                         // dastore
      array[1] = x1;                                                         // dastore

      SystemOut.print("\nwant: 0.0\n got: "); SystemOut.println(array[0]);   // daload
      SystemOut.print("\nwant: 1.0\n got: "); SystemOut.println(array[1]);   // daload
      }

   static void
   object_array()
      {
      Object array[] = new Object[2];   // anewarray
      Object x0      = null;
      Object x1      = null;

      array[0] = x0;                    // aastore
      array[1] = x1;                    // aastore

      SystemOut.print("\nwant: null\n got: "); SystemOut.println(array[0]);   // aaload
      SystemOut.print("\nwant: null\n got: "); SystemOut.println(array[1]);   // aaload
      }

   static void
   array_array()
      {
      Object array[] = new Object[2];   // anewarray
      Object x0[]    = new Object[2];   // anewarray
      Object x1[]    = null;

      array[0] = x0;                    // aastore
      array[1] = x1;                    // aastore

      SystemOut.print("\nwant: [Ljava.lang.Object;\n got: "); SystemOut.println(array[0].getClass().getName()); // aaload
      SystemOut.print("\nwant: null\n got: ");                SystemOut.println(array[1]);                      // aaload
      }
   
   static void
   multi_int_array()
      {
      int outer  = 2;
      int middle = 3;
      int inner  = 4;
      
      int ary[][][] = new int[outer][middle][inner]; // multianewarray
      
      int n = 0;
      for (int i = 0; i < outer; ++i)
         for (int j = 0; j < middle; ++j)
            for (int k = 0; k < inner; ++k)
               ary[i][j][k] = n++;

      for (int i = 0; i < outer; ++i)
         for (int j = 0; j < middle; ++j)
            for (int k = 0; k < inner; ++k)
               SystemOut.println("ary["+i+"]["+j+"]["+k+"]="+ary[i][j][k]);

      SystemOut.println();
      }
   
   static void
   multi_object_array()
      {
      int outer  = 2;
      int middle = 3;
      int inner  = 4;
      
      Integer ary[][][] = new Integer[outer][middle][inner]; // multianewarray
      
      int n = 0;
      for (int i = 0; i < outer; ++i)
         for (int j = 0; j < middle; ++j)
            for (int k = 0; k < inner; ++k)
               ary[i][j][k] = new Integer(n++);

      for (int i = 0; i < outer; ++i)
         for (int j = 0; j < middle; ++j)
            for (int k = 0; k < inner; ++k)
               SystemOut.println("ary["+i+"]["+j+"]["+k+"]="+ary[i][j][k]);

      SystemOut.println();
      }
   
   static void
   multi_partial_array()
      {
      int outer  = 2;
      int middle = 3;
      
      int ary[][][] = new int [outer][middle][]; // multianewarray
      
      for (int i = 0; i < outer; ++i)
         for (int j = 0; j < middle; ++j)
            SystemOut.println("ary["+i+"]["+j+"]="+ary[i][j]);

      SystemOut.println();
      }
   }
