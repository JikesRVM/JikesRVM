/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */

class inlineTest6
{
  static int
  run()
  {
    int i = sum(100);
    int j = sum(200);
 
    return i+j;
  }

  static int sum(int i) {
    int j;
    if (i == 0)
        j = i;
    else
        j = sum(i-1) + i;
    return j;
  }
}
