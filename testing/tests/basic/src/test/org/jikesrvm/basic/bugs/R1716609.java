/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Peter Donald. 2007
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
