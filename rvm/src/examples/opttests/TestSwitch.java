/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */

class TestSwitch
{
  public static void main(String args[])
  {
    // VM.boot();
    run();
  }
  
  public static boolean run()
  {
    System.out.println("TestSwitch");
    
    int j;
    
    // tableswitch
    System.out.print("\nwant: 99101199\n got: ");
    for (int i = 9; i < 13; i += 1)
      {
        switch (i)
          {
          case 10: j = 10; break;
          case 11: j = 11; break;
          default: j = 99; break;
          }
        System.out.print(j);
      }
    System.out.println();
    
    // lookupswitch
    System.out.print("\nwant: 99102030405099\n got: ");
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
        System.out.print(j);
      }
    System.out.println();

    return true;
  }
}
