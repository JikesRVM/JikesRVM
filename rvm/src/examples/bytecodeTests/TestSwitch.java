/*
 * (C) Copyright IBM Corp. 2001
 */
// TestSwitch

class TestSwitch
   {
   public static void main(String args[])
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestSwitch");
      
      int j;

      // tableswitch
      SystemOut.print("\nwant: 99101112\n got: ");
      for (int i = 9; i < 13; i += 1)
         {
         switch (i)
            {
            case 10: j = 10; break;
            case 11: j = 11; break;
            case 12: j = 12; break;
            case 13: j = 13; break;
            case 14: j = 14; break;
            case 15: j = 15; break;
            case 16: j = 16; break;
            case 17: j = 17; break;
            case 18: j = 18; break;
            default: j = 99; break;
            }
         SystemOut.print(j);
         }
      SystemOut.println();
      
      // lookupswitch
      SystemOut.print("\nwant: 99102030405099\n got: ");
      for (int i = 0; i < 70; i += 10)
         {
         switch (i)
            {
            case 10: j = 10;  break;
            case 20: j = 20;  break;
            case 30: j = 30;  break;
            case 40: j = 40;  break;
            case 50: j = 50;  break;
            default: j = 99;  break;
            }
         SystemOut.print(j);
         }
      SystemOut.println();
      }
   }
