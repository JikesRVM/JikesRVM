/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/*
 * @author unascribed
 */

class TestMath_toHex
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
      
      SystemOut.println("\nwant:  " + Long.toHexString(Double.doubleToLongBits(1.0D)) + 
        "\ngot:  " + Long.toHexString(Double.doubleToLongBits(Math.floor(1.6))));
      SystemOut.println("\nwant:  " + Long.toHexString(Double.doubleToLongBits(1.0D))  + 
        "\ngot:  " + Long.toHexString(Double.doubleToLongBits(Math.floor(1.5))));
      SystemOut.println("\nwant:  " + Long.toHexString(Double.doubleToLongBits(1.0D))  + 
        "\ngot:  " + Long.toHexString(Double.doubleToLongBits(Math.floor(1.4))));
      SystemOut.println("\nwant:  " + Long.toHexString(Double.doubleToLongBits(1.0D))  + 
        "\ngot:  " + Long.toHexString(Double.doubleToLongBits(Math.floor(1.0))));

      SystemOut.println("\nwant: " + Long.toHexString(Double.doubleToLongBits(-2.0D))  + 
        "\ngot:  " + Long.toHexString(Double.doubleToLongBits(Math.floor(-2.0))));
      SystemOut.println("\nwant: " + Long.toHexString(Double.doubleToLongBits(-2.0D))  + 
        "\ngot:  " + Long.toHexString(Double.doubleToLongBits(Math.floor(-1.6))));
      SystemOut.println("\nwant: " + Long.toHexString(Double.doubleToLongBits(-2.0D))  + 
        "\ngot:  " + Long.toHexString(Double.doubleToLongBits(Math.floor(-1.5))));
      SystemOut.println("\nwant: " + Long.toHexString(Double.doubleToLongBits(-2.0D))  + 
        "\ngot:  " + Long.toHexString(Double.doubleToLongBits(Math.floor(-1.4))));

      SystemOut.println("-- Math.ceil --");
      
      SystemOut.println("\nwant: " + Long.toHexString(Double.doubleToLongBits(2.0D))  + 
        "\ngot:  " + Long.toHexString(Double.doubleToLongBits(Math.ceil(1.6))));
      SystemOut.println("\nwant: " + Long.toHexString(Double.doubleToLongBits(2.0D))  + 
        "\ngot:  " + Long.toHexString(Double.doubleToLongBits(Math.ceil(1.5))));
      SystemOut.println("\nwant: " + Long.toHexString(Double.doubleToLongBits(2.0D))  + 
        "\ngot:  " + Long.toHexString(Double.doubleToLongBits(Math.ceil(1.4))));
      SystemOut.println("\nwant:  " + Long.toHexString(Double.doubleToLongBits(1.0D))  + 
        "\ngot:  " + Long.toHexString(Double.doubleToLongBits(Math.ceil(1.0))));

      SystemOut.println("\nwant: " + Long.toHexString(Double.doubleToLongBits(-2.0D))  + 
        "\ngot:  " + Long.toHexString(Double.doubleToLongBits(Math.ceil(-2.0))));
      SystemOut.println("\nwant: " + Long.toHexString(Double.doubleToLongBits(-1.0D))  + 
        "\ngot:  " + Long.toHexString(Double.doubleToLongBits(Math.ceil(-1.6))));
      SystemOut.println("\nwant: " + Long.toHexString(Double.doubleToLongBits(-1.0D))  + 
        "\ngot:  " + Long.toHexString(Double.doubleToLongBits(Math.ceil(-1.5))));
      SystemOut.println("\nwant: " + Long.toHexString(Double.doubleToLongBits(-1.0D))  + 
        "\ngot:  " + Long.toHexString(Double.doubleToLongBits(Math.ceil(-1.4))));
      }
   }
