/*
 * (C) Copyright IBM Corp. 2001
 */

class inlineTest2
{
  static int
  run()
  {
    int i = l2i( 0x000000000fffffffL);
    int j = l2i( 0x0000000000ffffffL);

    return i+j;
  }

  static int l2i(long i) {
    return (int)i;
  }
}

