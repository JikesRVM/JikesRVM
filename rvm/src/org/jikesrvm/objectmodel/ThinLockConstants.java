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
package org.jikesrvm.objectmodel;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Word;

/**
 * Constants used to implement thin locks.
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

  int NUM_BITS_TID = RVMThread.LOG_MAX_THREADS;
  int NUM_BITS_RC = JavaHeader.NUM_THIN_LOCK_BITS - NUM_BITS_TID;

  int TL_LOCK_COUNT_SHIFT = JavaHeader.THIN_LOCK_SHIFT;
  int TL_THREAD_ID_SHIFT = TL_LOCK_COUNT_SHIFT + NUM_BITS_RC;
  int TL_LOCK_ID_SHIFT = JavaHeader.THIN_LOCK_SHIFT;

  int TL_LOCK_COUNT_UNIT = 1 << TL_LOCK_COUNT_SHIFT;

  Word TL_LOCK_COUNT_MASK = Word.fromIntSignExtend(-1).rshl(BITS_IN_ADDRESS - NUM_BITS_RC).lsh(TL_LOCK_COUNT_SHIFT);
  Word TL_THREAD_ID_MASK = Word.fromIntSignExtend(-1).rshl(BITS_IN_ADDRESS - NUM_BITS_TID).lsh(TL_THREAD_ID_SHIFT);
  Word TL_LOCK_ID_MASK =
      Word.fromIntSignExtend(-1).rshl(BITS_IN_ADDRESS - (NUM_BITS_RC + NUM_BITS_TID - 1)).lsh(TL_LOCK_ID_SHIFT);
  Word TL_FAT_LOCK_MASK = Word.one().lsh(JavaHeader.THIN_LOCK_SHIFT + NUM_BITS_RC + NUM_BITS_TID - 1);
  Word TL_UNLOCK_MASK = Word.fromIntSignExtend(-1).rshl(BITS_IN_ADDRESS - JavaHeader
      .NUM_THIN_LOCK_BITS).lsh(JavaHeader.THIN_LOCK_SHIFT).not();
}

