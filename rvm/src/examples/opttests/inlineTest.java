/*
 * (C) Copyright IBM Corp. 2001
 */

class inlineTest
{
  static int
  run()
  {
    int i = l2i( 0x000000007fffffffL);

    return i;
  }

  static int l2i(long i) {
    return (int)i;
  }
}

