/*
 * (C) Copyright IBM Corp. 2001
 */
// TestMath

class TestMath
   {
   public static void main(String args[])
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestMath");

      SystemOut.println("-- Math.floor --");
      
      SystemOut.println("\nwant: 1.0 \ngot:  " + Math.floor(1.6));
      SystemOut.println("\nwant: 1.0 \ngot:  " + Math.floor(1.5));
      SystemOut.println("\nwant: 1.0 \ngot:  " + Math.floor(1.4));
      SystemOut.println("\nwant: 1.0 \ngot:  " + Math.floor(1.0));

      SystemOut.println("\nwant: -2.0 \ngot:  " + Math.floor(-2.0));
      SystemOut.println("\nwant: -2.0 \ngot:  " + Math.floor(-1.6));
      SystemOut.println("\nwant: -2.0 \ngot:  " + Math.floor(-1.5));
      SystemOut.println("\nwant: -2.0 \ngot:  " + Math.floor(-1.4));

      SystemOut.println("-- Math.ceil --");
      
      SystemOut.println("\nwant: 2.0 \ngot:  " + Math.ceil(1.6));
      SystemOut.println("\nwant: 2.0 \ngot:  " + Math.ceil(1.5));
      SystemOut.println("\nwant: 2.0 \ngot:  " + Math.ceil(1.4));
      SystemOut.println("\nwant: 1.0 \ngot:  " + Math.ceil(1.0));

      SystemOut.println("\nwant: -2.0 \ngot:  " + Math.ceil(-2.0));
      SystemOut.println("\nwant: -1.0 \ngot:  " + Math.ceil(-1.6));
      SystemOut.println("\nwant: -1.0 \ngot:  " + Math.ceil(-1.5));
      SystemOut.println("\nwant: -1.0 \ngot:  " + Math.ceil(-1.4));
      }
   }
