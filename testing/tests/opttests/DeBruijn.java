/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * Generates DeBruijn sequences of arbitrary length.
 *
 * Tests a conjecture that I have about DeBruijn sequences:
 * You can always construct a DeBruijn sequence by simply adding a 1 when it
 * will not give you a subsequence that you have already encountered.
 *
 * I'm not sure how to prove this -- any suggestions ?
 *
 * @author John Whaley
 */

class DeBruijn {
  static boolean run() {
    String str = calc(5);
    System.out.println("DeBruijn returned: " + str);
    return true;
  }

  public static String calc(int length) {

    if (length > 32) {
      //      System.out.println("Number is too LARGE!");
      return "Number is too LARGE!";
    }

    length = 1 << length;

    boolean[] table = new boolean [length];

    int mask = length - 1;

    String str = "";

    for (int i=0, val=mask; i<length; ++i) {
      val <<= 1;
      val &= mask;
      if (table[val]) {
        ++val;
        if (table[val]) {
          //      System.out.println("John is wrong!");
          return "John is wrong!";
        } else {
          table[val] = true;
          //      System.out.print("1");
          str = str +"1";
        }
      } else {
        table[val] = true;
        //      System.out.print("0");
        str = str + "0";
      }
    }
    //    System.out.println();
    return str;
  }

}
