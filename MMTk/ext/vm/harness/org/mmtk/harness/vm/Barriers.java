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
package org.mmtk.harness.vm;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.*;

@Uninterruptible
public class Barriers extends org.mmtk.vm.Barriers {
  /**
   * Sets an element of an object array without invoking any write
   * barrier.  This method is called by the Map class to ensure
   * potentially-allocation-triggering write barriers do not occur in
   * allocation slow path code.
   *
   * @param dst the destination array
   * @param index the index of the element to set
   * @param value the new value for the element
   */
  public void setArrayNoBarrier(Object [] dst, int index, Object value) {
    dst[index] = value;
  }

  /**
   * Perform the actual write of the write barrier.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param target The value that the slot will be updated to
   * @param metaDataA Unused
   * @param metaDataB Unused
   * @param mode The context in which the write is occuring
   */
  public void performWriteInBarrier(ObjectReference ref, Address slot,
                                    ObjectReference target, Word metaDataA,
                                    Word metaDataB, int mode) {
    slot.store(target);
  }

  /**
   * Perform the actual write of the write barrier, writing the value as a raw Word.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param rawTarget The value that the slot will be updated to
   * @param metaDataA Unused
   * @param metaDataB Unused
   * @param mode The context in which the write is occuring
   */
  public void performRawWriteInBarrier(ObjectReference ref, Address slot,
                                       Word rawTarget, Word metaDataA,
                                       Word metaDataB, int mode) {
    slot.store(rawTarget);
  }

  /**
   * Perform the actual read of the read barrier.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param metaDataA Unused
   * @param metaDataB Unused
   * @param mode The context in which the write is occuring
   * @return the read value
   */
  public ObjectReference performReadInBarrier(ObjectReference ref, Address slot,
                                              Word metaDataA, Word metaDataB, int mode) {
    return slot.loadObjectReference();
  }

  /**
   * Perform the actual read of the read barrier, returning the value as a raw Word.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param metaDataA Unused
   * @param metaDataB Unused
   * @param mode The context in which the write is occuring
   * @return the read value
   */
  public Word performRawReadInBarrier(ObjectReference ref, Address slot,
                                      Word metaDataA, Word metaDataB, int mode) {
    return slot.loadWord();
  }

  /**
   * Atomically write a reference field of an object or array and return
   * the old value of the reference field.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param target The value that the slot will be updated to
   * @param metaDataA Unused
   * @param metaDataB Unused
   * @param mode The context in which the write is occuring
   * @return The value that was replaced by the write.
   */
  public ObjectReference performWriteInBarrierAtomic(ObjectReference ref, Address slot,
                                                     ObjectReference target, Word metaDataA,
                                                     Word metaDataB, int mode) {
    ObjectReference old;
    do {
      old = slot.prepareObjectReference();
    } while (!slot.attempt(old, target));
    return old;
  }

  /**
   * Atomically write a reference field of an object or array and return
   * the old value of the reference field.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param rawTarget The raw value that the slot will be updated to
   * @param metaDataA Unused
   * @param metaDataB Unused
   * @param mode The context in which the write is occuring
   * @return The raw value that was replaced by the write.
   */
  public Word performRawWriteInBarrierAtomic(ObjectReference ref, Address slot,
                                             Word rawTarget, Word metaDataA,
                                             Word metaDataB, int mode) {
    Word old;
    do {
      old = slot.prepareWord();
    } while (!slot.attempt(old, rawTarget));
    return old;
  }

  /**
   * Attempt an atomic compare and exchange in a write barrier sequence.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param old The old reference to be swapped out
   * @param target The value that the slot will be updated to
   * @param metaDataA Unused
   * @param metaDataB Unused
   * @param mode The context in which the write is occuring
   * @return True if the compare and swap was successful
   */
  public boolean tryCompareAndSwapWriteInBarrier(ObjectReference ref, Address slot,
                                                 ObjectReference old, ObjectReference target,
                                                 Word metaDataA, Word metaDataB, int mode) {
    return slot.attempt(old, target);
  }

  /**
   * Attempt an atomic compare and exchange in a write barrier sequence.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param rawOld The old reference to be swapped out
   * @param rawTarget The value that the slot will be updated to
   * @param metaDataA Unused
   * @param metaDataB Unused
   * @param mode The context in which the write is occuring
   * @return True if the compare and swap was successful
   */
  public boolean tryRawCompareAndSwapWriteInBarrier(ObjectReference ref, Address slot,
                                                    Word rawOld, Word rawTarget,
                                                    Word metaDataA, Word metaDataB, int mode) {
    return slot.attempt(rawOld, rawTarget);
  }
}
