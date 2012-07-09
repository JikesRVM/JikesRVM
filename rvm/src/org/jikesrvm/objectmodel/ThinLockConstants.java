/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.objectmodel;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Word;

/**
 * Constants used to implement thin locks.
 * <p>
 * A portion of a word, either in the object header
 * or in some other location, is used to provide light weight
 * synchronization operations. This class defines
 * how the bits available for thin locks are allocated.
 * Either a lock is in fat state, in which case it looks like
 * 1Z..Z where Z..Z is the id of a heavy lock, or it is in
 * thin state in which case it looks like 0I..IC..C where
 * I is the thread id of the thread that owns the lock and
 * C is the recursion count of the lock.
 * <pre>
 * aaaaTTTTTTTTTTbbbbb
 * JavaHeader.NUM_THIN_LOCK_BITS = # of T's
 * JavaHeader.THIN_LOCK_SHIFT = # of b's
 * </pre>
 */
public interface ThinLockConstants extends SizeConstants {

  // biased locking / thin locking status bits:
  // 00 -> thin biasable, and biased if TID is non-zero
  // 01 -> thin unbiasable
  // 10 -> fat unbiasable

  int TL_NUM_BITS_STAT = 2;
  int TL_NUM_BITS_TID = RVMThread.LOG_MAX_THREADS;
  int TL_NUM_BITS_RC = JavaHeader.NUM_THIN_LOCK_BITS - TL_NUM_BITS_TID - TL_NUM_BITS_STAT;

  int TL_THREAD_ID_SHIFT = JavaHeader.THIN_LOCK_SHIFT;
  int TL_LOCK_COUNT_SHIFT = TL_THREAD_ID_SHIFT + TL_NUM_BITS_TID;
  int TL_STAT_SHIFT = TL_LOCK_COUNT_SHIFT + TL_NUM_BITS_RC;
  int TL_LOCK_ID_SHIFT = JavaHeader.THIN_LOCK_SHIFT;
  int TL_DEDICATED_U16_OFFSET = JavaHeader.THIN_LOCK_DEDICATED_U16_OFFSET;
  int TL_DEDICATED_U16_SHIFT = JavaHeader.THIN_LOCK_DEDICATED_U16_SHIFT;

  Word TL_LOCK_COUNT_UNIT = Word.fromIntSignExtend(1 << TL_LOCK_COUNT_SHIFT);

  Word TL_LOCK_COUNT_MASK = Word.fromIntSignExtend(-1).rshl(BITS_IN_ADDRESS - TL_NUM_BITS_RC).lsh(TL_LOCK_COUNT_SHIFT);
  Word TL_THREAD_ID_MASK = Word.fromIntSignExtend(-1).rshl(BITS_IN_ADDRESS - TL_NUM_BITS_TID).lsh(TL_THREAD_ID_SHIFT);
  Word TL_LOCK_ID_MASK =
      Word.fromIntSignExtend(-1).rshl(BITS_IN_ADDRESS - (TL_NUM_BITS_RC + TL_NUM_BITS_TID)).lsh(TL_LOCK_ID_SHIFT);
  Word TL_STAT_MASK = Word.fromIntSignExtend(-1).rshl(BITS_IN_ADDRESS - TL_NUM_BITS_TID).lsh(TL_STAT_SHIFT);
  Word TL_UNLOCK_MASK = Word.fromIntSignExtend(-1).rshl(BITS_IN_ADDRESS - JavaHeader
      .NUM_THIN_LOCK_BITS).lsh(JavaHeader.THIN_LOCK_SHIFT).not();

  Word TL_STAT_BIASABLE = Word.fromIntSignExtend(0).lsh(TL_STAT_SHIFT);
  Word TL_STAT_THIN = Word.fromIntSignExtend(1).lsh(TL_STAT_SHIFT);
  Word TL_STAT_FAT = Word.fromIntSignExtend(2).lsh(TL_STAT_SHIFT);
}

