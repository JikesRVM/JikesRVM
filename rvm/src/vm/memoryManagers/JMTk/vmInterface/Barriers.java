/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package org.mmtk.vm;

import com.ibm.JikesRVM.VM_Magic;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * $Id$ 
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 *
 * @version $Revision$
 * @date $Date$
 */
public class Barriers implements Constants, Uninterruptible {
  /**
   * Sets an element of a char array without invoking any write
   * barrier.  This method is called by the Log method, as it will be
   * used during garbage collection and needs to manipulate character
   * arrays without causing a write barrier operation.
   *
   * @param dst the destination array
   * @param index the index of the element to set
   * @param value the new value for the element
   */
  public static void setArrayNoBarrier(char [] dst, int index, char value) {
    if (Assert.runningVM())
      VM_Magic.setCharAtOffset(dst, index << LOG_BYTES_IN_CHAR, value);
    else
      dst[index] = value;
  }

  /**
   * Perform the actual write of the write barrier.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param target The value that the slot will be updated to
   * @param offset The offset from the ref (metaDataA)
   * @param locationMetadata An index of the FieldReference (metaDataB)
   * @param mode The context in which the write is occuring
   */
  public static void performWriteInBarrier(ObjectReference ref, Address slot, 
                                           ObjectReference target, int offset, 
                                           int locationMetadata, int mode) 
    throws InlinePragma {
    Object obj = ref.toObject();
    VM_Magic.setObjectAtOffset(obj, offset, target.toObject(), locationMetadata);  
  }

  /**
   * Atomically write a reference field of an object or array and return 
   * the old value of the reference field.
   * 
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param target The value that the slot will be updated to
   * @param offset The offset from the ref (metaDataA)
   * @param locationMetadata An index of the FieldReference (metaDataB)
   * @param mode The context in which the write is occuring
   * @return The value that was replaced by the write.
   */
  public static ObjectReference performWriteInBarrierAtomic(
                                           ObjectReference ref, Address slot,
                                           ObjectReference target, int offset,
                                           int locationMetadata, int mode)
    throws InlinePragma {                                
    Object obj = ref.toObject();
    Object newObject = target.toObject();
    Object oldObject;
    do {
      oldObject = VM_Magic.prepareObject(obj, offset);
    } while (!VM_Magic.attemptObject(obj, offset, oldObject, newObject));
    return ObjectReference.fromObject(oldObject); 
  }

  /**
   * Gets an element of a char array without invoking any read barrier
   * or performing bounds check.
   *
   * @param src the source array
   * @param index the natural array index of the element to get
   * @return the new value of element
   */
  public static char getArrayNoBarrier(char [] src, int index) {
    if (Assert.runningVM())
      return VM_Magic.getCharAtOffset(src, index << LOG_BYTES_IN_CHAR);
    else
      return src[index];
  }

  /**
   * Gets an element of a byte array without invoking any read barrier
   * or bounds check.
   *
   * @param src the source array
   * @param index the natural array index of the element to get
   * @return the new value of element
   */
  public static byte getArrayNoBarrier(byte [] src, int index) {
    if (Assert.runningVM())
      return VM_Magic.getByteAtOffset(src, index);
    else
      return src[index];
  }

  /**
   * Gets an element of an int array without invoking any read barrier
   * or performing bounds checks.
   *
   * @param src the source array
   * @param index the natural array index of the element to get
   * @return the new value of element
   */
  public static int getArrayNoBarrier(int [] src, int index) {
    if (Assert.runningVM())
      return VM_Magic.getIntAtOffset(src, index<<LOG_BYTES_IN_INT);
    else
      return src[index];
  }

  /**
   * Gets an element of an Object array without invoking any read
   * barrier or performing bounds checks.
   *
   * @param src the source array
   * @param index the natural array index of the element to get
   * @return the new value of element
   */
  public static Object getArrayNoBarrier(Object [] src, int index) {
    if (Assert.runningVM())
      return VM_Magic.getObjectAtOffset(src, index<<LOG_BYTES_IN_ADDRESS);
    else
      return src[index];
  }

  /**
   * Gets an element of an array of byte arrays without causing the potential
   * thread switch point that array accesses normally cause.
   *
   * @param src the source array
   * @param index the index of the element to get
   * @return the new value of element
   */
  public static byte[] getArrayNoBarrier(byte[][] src, int index) {
    if (Assert.runningVM())
      return VM_Magic.addressAsByteArray(VM_Magic.objectAsAddress(VM_Magic.getObjectAtOffset(src, index << LOG_BYTES_IN_ADDRESS)));
    else
      return src[index];
  }
}
