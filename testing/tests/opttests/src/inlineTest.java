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

