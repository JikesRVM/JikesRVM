/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package test.org.jikesrvm.basic.bugs;

import org.vmmagic.unboxed.Word;

/**
 * [ 1716609 ] Sanitize operator descriptions for unboxed types.
 * https://sourceforge.net/tracker/?func=detail&atid=712768&aid=1716609&group_id=128805
 */
public class R1716609 {
  public static void main(String[] args) {
    Word x = Word.fromIntSignExtend(32);
    Word y = Word.one().lsh(x.toInt());
    if (y.EQ(Word.one())) { System.out.println(1); } else { System.out.println(0); }
  }
}
