/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
/**
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
