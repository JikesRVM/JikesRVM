/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */

class inlineTest3
{
  static int
  run()
  {
    int i = l2i( 0x000000000fffffffL);
    int j = l2i( 0x0000000000ffffffL);
 
    return i+j;
  }

  static int l2i(long i) {

    int j = (int)i;
 
    int k = ((int)i)*2+j*5;
    int l = ((int)i)+j+k;
    int m = k-l*6;

    return m;

  }
}

