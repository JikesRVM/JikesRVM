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

import static org.jikesrvm.objectmodel.JavaHeaderConstants.ADDRESS_BASED_HASHING;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.ARRAY_LENGTH_OFFSET;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.HASHCODE_BYTES;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_INT;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.mm.mminterface.AlignmentEncoding;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.Lock;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * The interface to the object model definition accessible to the
 * virtual machine. <p>
 *
 * Conceptually each Java object is composed of the following pieces:
 * <ul>
 * <li> The JavaHeader defined by {@link JavaHeader}. This portion of the
 *      object supports language-level functions such as locking, hashcodes,
 *      dynamic type checking, virtual function invocation, and array length.
 * <li> The GCHeader defined by {@link MemoryManager}. This portion
 *      of the object supports allocator-specific requirements such as
 *      mark/barrier bits, reference counts, etc.
 * <li> The MiscHeader defined by {@link MiscHeader}. This portion supports
 *      various other clients that want to add bits/words to all objects.
 *      Typical uses are profiling and instrumentation (basically this is a
 *      way to add an instance field to java.lang.Object).
 * <li> The instance fields.  Currently defined by various classloader classes.
 *      Factoring this code out and making it possible to lay out the instance
 *      fields in different ways is a todo item.
 * </ul>
 *
 * Every object's header contains the three portions outlined above.
 *
 * <pre>
 * |&lt;- lo memory                                        hi memory -&gt;|
 *
 *   SCALAR LAYOUT:
 * |&lt;---------- scalar header ---------&gt;|
 * +----------+------------+------------+------+------+------+--------+
 * | GCHeader | MiscHeader | JavaHeader | fldO | fld1 | fldx | fldN-1 |
 * +----------+------------+------------+------+------+------+--------+
 *                         ^ JHOFF             ^objref
 *                                             .
 *    ARRAY LAYOUT:                            .
 * |&lt;---------- array header -----------------&gt;|
 * +----------+------------+------------+------+------+------+------+------+
 * | GCHeader | MiscHeader | JavaHeader | len  | elt0 | elt1 | ...  |eltN-1|
 * +----------+------------+------------+------+------+------+------+------+
 *                         ^ JHOFF             ^objref
 * </pre>
 * <p>
 * Assumptions:
 * <ul>
 * <li> Each portion of the header (JavaHeader, GCHeader, MiscHeader)
 *      is some multiple of 4 bytes (possibly 0).  This simplifies access, since we
 *      can access each portion independently without having to worry about word tearing.
 * <li> The JavaHeader exports k (&gt;=0) unused contiguous bits that can be used
 *      by the GCHeader and MiscHeader.  The GCHeader gets first dibs on these bits.
 *      The GCHeader should use bits 0..i, MiscHeader should use bits i..k.
 * <li> JHOFF is a constant for a given configuration.
 * </ul>
 *
 * This model allows efficient array access: the array pointer can be
 * used directly in the base+offset subscript calculation, with no
 * additional constant required.<p>
 *
 * This model allows free null pointer checking for most reads: a
 * small offset from that reference will wrap around to either very
 * high or very low unmapped memory in the case of a null pointer. As
 * long as these segments of memory are not mapped to the current
 * process, loads/stores through such a pointer will cause a trap that
 * we can catch with a unix signal handler.<p>
 *
 * Note the key invariant that all elements of the header are
 * available at the same offset from an objref for both arrays and
 * scalar objects.<p>
 *
 * Note that this model allows for arbitrary growth of the GC header
 * to the left of the object.  A possible TODO item is to modify the
 * necessary interfaces within this class and JavaHeader to allow
 * moveObject, bytesUsed, bytesRequiredWhenCopied, etc. to tell this
 * class how many GC header bytes have been allocated. As these calls
 * would be constant within the constant of the call the optimising
 * compiler should be able to allow this at minimal cost.<p>
 *
 * Another possible TODO item is to include support for linear
 * scanning, where it is possible to move from one object to the next
 * under contiguous allocation. At the moment this is in conflict with
 * object alignment code for objects with long/double fields. We could
 * possibly include the code anyway but require that the alignment
 * code is switched off, or that all objects are aligned.  Linear
 * scanning is used in several GC algorithms including card-marking
 * and compaction.
 *
 * @see JavaHeader
 * @see MiscHeader
 * @see MemoryManager
 */
@Uninterruptible
public class ObjectModel {

  /** Should we gather stats on hash code state transitions for address-based hashing? */
  public static final boolean HASH_STATS = false;
  /** count number of Object.hashCode() operations */
  public static int hashRequests = 0;
  /** count transitions from UNHASHED to HASHED */
  public static int hashTransition1 = 0;
  /** count transitions from HASHED to HASHED_AND_MOVED */
  public static int hashTransition2 = 0;

  /** Whether to pack bytes and shorts into 32bit fields*/
  private static final boolean PACKED = true;

  /** Layout widget */
  private static final FieldLayout layout;

  static {
    if (PACKED) {
      layout = new FieldLayoutPacked(true, true);
    } else {
      layout = new FieldLayoutUnpacked(true, true);
    }
  }

  /**
   * Layout the instance fields declared in this class.
   * @param klass the class to layout
   */
  @Interruptible
  public static void layoutInstanceFields(RVMClass klass) {
    layout.layoutInstanceFields(klass);
  }

  /**
   * Given a reference, return an address which is guaranteed to be inside
   * the memory region allocated to the object.
   *
   * @param ref an object
   * @return an address that's inside the memory region allocated to the
   *  object
   */
  public static Address getPointerInMemoryRegion(ObjectReference ref) {
    return JavaHeader.getPointerInMemoryRegion(ref);
  }

  /**
   * @return the offset of the array length field from an object reference
   * (in bytes)
   */
  public static Offset getArrayLengthOffset() {
    return ARRAY_LENGTH_OFFSET;
  }

  /**
   * NB: this method exists only for documentation purposes.
   *
   * @return the offset of the first array element from the array's address
   *  (in bytes). Always {@code 0} in the current object model.
   */
  public static Offset getArrayBaseOffset() {
    return Offset.zero();
  }

  public static TIB getTIB(ObjectReference ptr) {
    return getTIB(ptr.toObject());
  }

  public static TIB getTIB(Object o) {
    return JavaHeader.getTIB(o);
  }

  public static void setTIB(ObjectReference ptr, TIB tib) {
    setTIB(ptr.toObject(), tib);
  }

  public static void setTIB(Object ref, TIB tib) {
    JavaHeader.setTIB(ref, tib);
  }

  /**
   * Sets the TIB for an object during bootimage writing.
   *
   * @param bootImage the bootimage
   * @param refAddress the object's address
   * @param tibAddr the TIB's address
   * @param type the object's type
   */
  @Interruptible
  public static void setTIB(BootImageInterface bootImage, Address refAddress, Address tibAddr, RVMType type) {
    JavaHeader.setTIB(bootImage, refAddress, tibAddr, type);
  }

  /**
   * Get the pointer just past an object.
   *
   * @param obj the object in question
   * @return first word after the object
   */
  public static Address getObjectEndAddress(Object obj) {
    TIB tib = getTIB(obj);
    RVMType type = tib.getType();
    if (type.isClassType()) {
      return getObjectEndAddress(obj, type.asClass());
    } else {
      int numElements = Magic.getArrayLength(obj);
      return getObjectEndAddress(obj, type.asArray(), numElements);
    }
  }

  /**
   * Gets the pointer just past an object.
   *
   * @param object the object in question
   * @param type the object's class
   * @return first word after the scalar object
   */
  public static Address getObjectEndAddress(Object object, RVMClass type) {
    return JavaHeader.getObjectEndAddress(object, type);
  }

  /**
   * Gets the pointer just past an object.
   *
   * @param object the object in question
   * @param type the object's class
   * @param elements the array's length
   * @return the first word after the array
   */
  public static Address getObjectEndAddress(Object object, RVMArray type, int elements) {
    return JavaHeader.getObjectEndAddress(object, type, elements);
  }

  /**
   * Get an object reference from the address the lowest word of the object was allocated.
   *
   * @param start the lowest word in the storage of an allocated object
   * @return the object reference for the object
   */
  public static ObjectReference getObjectFromStartAddress(Address start) {
    return JavaHeader.getObjectFromStartAddress(start);
  }

  /**
   * Gets an object reference from the address the lowest word of the object was allocated.
   *
   * @param start the lowest word in the storage of an allocated object
   * @return the object reference for the object
   */
  public static ObjectReference getScalarFromStartAddress(Address start) {
    return JavaHeader.getScalarFromStartAddress(start);
  }

  /**
   * Gets an object reference from the address the lowest word of the object was allocated.
   *
   * @param start the lowest word in the storage of an allocated object
   * @return the object reference for the object
   */
  public static ObjectReference getArrayFromStartAddress(Address start) {
    return JavaHeader.getArrayFromStartAddress(start);
  }

  /**
   * @param obj an object
   * @return the next object in the heap under contiguous allocation
   */
  public static ObjectReference getNextObject(ObjectReference obj) {
    TIB tib = getTIB(obj);
    RVMType type = tib.getType();
    if (type.isClassType()) {
      return getNextObject(obj, type.asClass());
    } else {
      int numElements = Magic.getArrayLength(obj);
      return getNextObject(obj, type.asArray(), numElements);
    }
  }

  /**
   * Gets the next object after this scalar under contiguous allocation.
   *
   * @param obj the current object, which must be a scalar
   * @param type the object's type
   * @return the next scalar object in the heap
   */
  public static ObjectReference getNextObject(ObjectReference obj, RVMClass type) {
    return JavaHeader.getNextObject(obj, type);
  }

  /**
   * Get the next object after this array under contiguous allocation.
   *
   * @param obj the current object, which must be an array
   * @param type the object's type
   * @param numElements the length of the array
   * @return the next scalar object in the heap
   */
  public static ObjectReference getNextObject(ObjectReference obj, RVMArray type, int numElements) {
    return JavaHeader.getNextObject(obj, type, numElements);
  }

  /**
   * Gets the reference of an object after copying to a specified region.
   *
   * @param obj the object to copy
   * @param to the target address for the copy
   * @return the reference of the copy
   */
  public static Object getReferenceWhenCopiedTo(Object obj, Address to) {
    TIB tib = getTIB(obj);
    RVMType type = tib.getType();
    if (type.isClassType()) {
      return getReferenceWhenCopiedTo(obj, to, type.asClass());
    } else {
      return getReferenceWhenCopiedTo(obj, to, type.asArray());
    }
  }

  /**
   * @param obj an object
   * @return the number of bytes used by the object
   */
  public static int bytesUsed(Object obj) {
    TIB tib = getTIB(obj);
    RVMType type = tib.getType();
    if (type.isClassType()) {
      return bytesUsed(obj, type.asClass());
    } else {
      int numElements = Magic.getArrayLength(obj);
      return bytesUsed(obj, type.asArray(), numElements);
    }
  }

  /**
   * how many bytes are used by the scalar?
   *
   * @param obj an object
   * @param type the object's type
   * @return the number of bytes used by the object
   */
  public static int bytesUsed(Object obj, RVMClass type) {
    return JavaHeader.bytesUsed(obj, type);
  }

  /**
   * how many bytes are used by the array?
   *
   * @param obj an object
   * @param type the object's type
   * @param numElements the array's length
   * @return the number of bytes used by the object
   */
  public static int bytesUsed(Object obj, RVMArray type, int numElements) {
    return JavaHeader.bytesUsed(obj, type, numElements);
  }

  /**
   * @param obj the object
   * @return number of bytes that are required when the object is copied by GC
   */
  public static int bytesRequiredWhenCopied(Object obj) {
    TIB tib = getTIB(obj);
    RVMType type = tib.getType();
    if (type.isClassType()) {
      return bytesRequiredWhenCopied(obj, type.asClass());
    } else {
      int numElements = Magic.getArrayLength(obj);
      return bytesRequiredWhenCopied(obj, type.asArray(), numElements);
    }
  }

  /**
   * how many bytes are needed when the scalar object is copied by GC?
   *
   * @param fromObj the object to copy
   * @param type the object's type
   * @return number of needed bytes when the scalar object is copied by GC
   */
  public static int bytesRequiredWhenCopied(Object fromObj, RVMClass type) {
    return JavaHeader.bytesRequiredWhenCopied(fromObj, type);
  }

  /**
   * how many bytes are needed when the array object is copied by GC?
   *
   * @param fromObj the object to copy
   * @param type the object's type
   * @param numElements the number of elements in the array
   * @return the number of bytes that are required for the copy
   */
  public static int bytesRequiredWhenCopied(Object fromObj, RVMArray type, int numElements) {
    return JavaHeader.bytesRequiredWhenCopied(fromObj, type, numElements);
  }

  /**
   * Maps from the object ref to the lowest address of the storage
   * associated with the object.
   *
   * @param obj the object reference
   * @return the lowest address in the object's memory region
   */
  @Inline
  public static Address objectStartRef(ObjectReference obj) {
    return JavaHeader.objectStartRef(obj);
  }

  /**
   * Get the reference of an object after copying to a specified region.
   *
   * @param obj the object to copy
   * @param region the target address for the copy
   * @param type the scalar's type
   * @return the reference of the copy
   */
  public static Object getReferenceWhenCopiedTo(Object obj, Address region, RVMClass type) {
    return JavaHeader.getReferenceWhenCopiedTo(obj, region, type);
  }

  /**
   * Get the reference of an object after copying to a specified region.
   *
   * @param obj the object to copy
   * @param region the target address for the copy
   * @param type the array's type
   * @return the reference of the copy
   */
  public static Object getReferenceWhenCopiedTo(Object obj, Address region, RVMArray type) {
    return JavaHeader.getReferenceWhenCopiedTo(obj, region, type);
  }

  /**
   * Copies a scalar object to the given raw storage address.
   *
   * @param fromObj the scalar to copy
   * @param toObj target address for copy
   * @param numBytes how many bytes to copy
   * @param type the scalar's type
   * @return the reference for the object's copy
   */
  public static Object moveObject(Object fromObj, Object toObj, int numBytes, RVMClass type) {
    return JavaHeader.moveObject(fromObj, toObj, numBytes, type);
  }

  /**
   * Copy an array object to the given raw storage address.
   *
   * @param fromObj the object to copy
   * @param toObj the target object
   * @param numBytes the number of bytes to copy
   * @param type the array's type
   * @return the reference for the array's copy
   */
  public static Object moveObject(Object fromObj, Object toObj, int numBytes, RVMArray type) {
    return JavaHeader.moveObject(fromObj, toObj, numBytes, type);
  }

  /**
   * Copy a scalar object to the given raw storage address.
   *
   * @param toAddress the target address
   * @param fromObj the object to copy
   * @param numBytes how many bytes to copy
   * @param type the scalar's type
   * @return the reference for the object's copy
   */
  public static Object moveObject(Address toAddress, Object fromObj, int numBytes, RVMClass type) {
    return JavaHeader.moveObject(toAddress, fromObj, numBytes, type);
  }

  /**
   * Copy an array object to the given raw storage address
   *
   * @param toAddress the target address
   * @param fromObj the object to copy
   * @param numBytes how many bytes to copy
   * @param type the array's type
   * @return the reference for the object's copy
   */
  public static Object moveObject(Address toAddress, Object fromObj, int numBytes, RVMArray type) {
    return JavaHeader.moveObject(toAddress, fromObj, numBytes, type);
  }

  /**
   * @param o an object
   * @return the type of an object
   */
  public static RVMType getObjectType(Object o) {
    return Magic.getObjectType(o);
  }

  /**
   * @param o an array
   * @return the length of an array
   */
  public static int getArrayLength(Object o) {
    return Magic.getIntAtOffset(o, getArrayLengthOffset());
  }

  /**
   * Sets the length of an array.
   *
   * @param o an array
   * @param len the length of the array
   */
  public static void setArrayLength(Object o, int len) {
    Magic.setIntAtOffset(o, getArrayLengthOffset(), len);
  }

  @Interruptible
  public static int getObjectHashCode(Object o) {
    if (HASH_STATS) hashRequests++;
    return JavaHeader.getObjectHashCode(o);
  }

  public static Offset getThinLockOffset(Object o) {
    return JavaHeader.getThinLockOffset(o);
  }

  public static Offset defaultThinLockOffset() {
    return JavaHeader.defaultThinLockOffset();
  }

  /**
   * Allocates a thin lock word for instances of the type
   * (if they already have one, then has no effect).
   *
   * @param t the type that is supposed to receive a thin
   *  lock word
   */
  public static void allocateThinLock(RVMType t) {
    JavaHeader.allocateThinLock(t);
  }

  @Entrypoint
  @Unpreemptible("Become another thread when lock is contended, don't preempt in other cases")
  public static void genericLock(Object o) {
    JavaHeader.genericLock(o);
  }

  @Entrypoint
  @Unpreemptible("No preemption normally, but may raise exceptions")
  public static void genericUnlock(Object o) {
    JavaHeader.genericUnlock(o);
  }

  /**
   * @param obj an object
   * @param thread a thread
   * @return <code>true</code> if the lock on obj is currently owned
   *         by thread <code>false</code> if it is not.
   */
  public static boolean holdsLock(Object obj, RVMThread thread) {
    return JavaHeader.holdsLock(obj, thread);
  }

  /**
   * Obtains the heavy-weight lock, if there is one, associated with the
   * indicated object.  Returns <code>null</code>, if there is no
   * heavy-weight lock associated with the object.
   *
   * @param o the object from which a lock is desired
   * @param create if true, create heavy lock if none found
   * @return the heavy-weight lock on the object (if any)
   */
  @Unpreemptible("May be interrupted for allocations of locks")
  public static Lock getHeavyLock(Object o, boolean create) {
    return JavaHeader.getHeavyLock(o, create);
  }

  /**
   * Non-atomic read of word containing available bits
   *
   * @param o the object to read
   * @return the available bits word
   */
  public static Word readAvailableBitsWord(Object o) {
    return JavaHeader.readAvailableBitsWord(o);
  }

  /**
   * Non-atomic read of byte containing available bits
   * @param o the object to read
   * @return the available bits bytes
   */
  public static byte readAvailableByte(Object o) {
    return JavaHeader.readAvailableByte(o);
  }

  /**
   * Non-atomic write of word containing available bits.
   *
   * @param o the object whose word will be written
   * @param val the available bits word
   */
  public static void writeAvailableBitsWord(Object o, Word val) {
    JavaHeader.writeAvailableBitsWord(o, val);
  }

  /**
   * Non-atomic write of byte containing available bits
   *
   * @param o the object whose available byte will be written
   * @param val the value to write to the available byte
   */
  public static void writeAvailableByte(Object o, byte val) {
    JavaHeader.writeAvailableByte(o, val);
  }

  /**
   * @param o the object whose bit will be tested
   * @param idx the index in the bits
   * @return {@code true} if argument bit is 1, {@code false} if it is 0
   */
  public static boolean testAvailableBit(Object o, int idx) {
    return JavaHeader.testAvailableBit(o, idx);
  }

  /**
   * Sets argument bit to 1 if value is true, 0 if value is false
   *
   * @param o the object whose bit will be set
   * @param idx the index in the bits
   * @param flag {@code true} for 1, {@code false} for 0
   */
  public static void setAvailableBit(Object o, int idx, boolean flag) {
    JavaHeader.setAvailableBit(o, idx, flag);
  }

  /**
   * Freezes the other bits in the byte containing the available bits
   * so that it is safe to update them using setAvailableBits.
   *
   * @param o the object whose available bytes will be initialized
   */
  @Interruptible
  public static void initializeAvailableByte(Object o) {
    JavaHeader.initializeAvailableByte(o);
  }

  /**
   * A prepare on the word containing the available bits.
   * <p>
   * Note: this method is intended to be used in conjunction
   * with the attempt method.
   *
   * @param o the object which has the available bits
   * @return the current value of the word
   * @see #attemptAvailableBits(Object, Word, Word)
   */
  public static Word prepareAvailableBits(Object o) {
    return JavaHeader.prepareAvailableBits(o);
  }

  /**
   * An attempt on the word containing the available bits.
   * <p>
   * Note: this method is intended to be used in conjunction
   * with the prepare method. If the method returns {@code false},
   * callers must update their information about the old value of
   * the available bits word before retrying again.
   *
   * @param o the object which has the available bits
   * @param oldVal the old value that the word is expected to have
   * @param newVal the new value that will be written, if possible
   * @return whether the write occurred
   */
  public static boolean attemptAvailableBits(Object o, Word oldVal, Word newVal) {
    return JavaHeader.attemptAvailableBits(o, oldVal, newVal);
  }

  /**
   * Given the smallest base address in a region, return the smallest
   * object reference that could refer to an object in the region.
   *
   * @param regionBaseAddr the smallest base address in the region
   * @return the smallest address in the region that could possibly
   *  refer to an object in the region
   */
  public static Address minimumObjectRef(Address regionBaseAddr) {
    return JavaHeader.minimumObjectRef(regionBaseAddr);
  }

  /**
   * Given the largest base address in a region, return the largest
   * object reference that could refer to an object in the region.
   *
   * @param regionHighAddr the highest base address in the region
   * @return the largest address in the region that could possibly
   *  refer to an object in the region
   */
  public static Address maximumObjectRef(Address regionHighAddr) {
    return JavaHeader.maximumObjectRef(regionHighAddr);
  }

  /**
   * Computes the header size of an instance of the given type.
   *
   * @param type the instance's type
   * @return size of the head in bytes
   */
  @Inline
  public static int computeHeaderSize(RVMType type) {
    if (type.isArrayType()) {
      return computeArrayHeaderSize(type.asArray());
    } else {
      return computeScalarHeaderSize(type.asClass());
    }
  }

  /**
   * Computes the header size of an object.
   *
   * @param ref the object whose header size is of interest
   * @return the object's header size
   */
  @Interruptible
  public static int computeHeaderSize(Object ref) {
    return computeHeaderSize(getObjectType(ref));
  }

  /**
   * Computes the header size of an instance of the given type.
   *
   * @param type the instance's type
   * @return size of the head in bytes
   */
  @Inline
  public static int computeScalarHeaderSize(RVMClass type) {
    return JavaHeader.computeScalarHeaderSize(type);
  }

  /**
   * Computes the header size of an instance of the given type.
   *
   * @param type the instance's type
   * @return size of the head in bytes
   */
  public static int computeArrayHeaderSize(RVMArray type) {
    return JavaHeader.computeArrayHeaderSize(type);
  }

  /**
   * Given a TIB, compute the header size of an instance of the TIB's class.
   *
   * @param tib a TIB
   * @return the header size of an object from the class given by the TIB
   */
  public static int computeHeaderSize(Object[] tib) {
    return computeHeaderSize(Magic.objectAsType(tib[0]));
  }

  /**
   * For a reference to an object, what is the offset in bytes to the
   * last word of the header from an out-to-in perspective for the object?
   *
   * @return offset of the last word of the header from an
   *  out-to-in perspective
   */
  public static int getHeaderEndOffset() {
    return JavaHeader.getHeaderEndOffset();
  }

  /**
   * For a reference to an object, what is the offset in bytes to the bottom
   * word of the object?
   *
   * @param t the class of the object
   * @return offset of the first word of the class from the object
   *  reference
   */
  public static int objectStartOffset(RVMClass t) {
    return JavaHeader.objectStartOffset(t);
  }

  /**
   * @param t RVMClass instance being created
   * @return the desired alignment of the alignment point returned by
   * getOffsetForAlignment in instances of the argument RVMClass.
   */
  public static int getAlignment(RVMClass t) {
    return JavaHeader.getAlignment(t);
  }

  /**
   * @param t RVMClass instance being copied
   * @param obj the object being copied
   * @return the desired alignment of the alignment point returned by
   * getOffsetForAlignment in instances of the argument RVMClass.
   */

  public static int getAlignment(RVMClass t, Object obj) {
    return JavaHeader.getAlignment(t, obj);
  }

  /**
   * @param t RVMArray instance being created
   * @return the desired alignment of the alignment point returned by
   * getOffsetForAlignment in instances of the argument RVMArray.
   */
  public static int getAlignment(RVMArray t) {
    return JavaHeader.getAlignment(t);
  }

  /**
   * @param t RVMArray instance being copied
   * @param obj the object being copied
   * @return the desired alignment of the alignment point returned by
   * getOffsetForAlignment in instances of the argument RVMArray.
   */
  public static int getAlignment(RVMArray t, Object obj) {
    return JavaHeader.getAlignment(t, obj);
  }

  /**
   * @param t RVMClass instance being created
   * @param needsIdentityHash TODO document this parameter. IIs it still needed?
   *  It's never set to true.
   * @return the offset relative to physical beginning of object
   * that must be aligned.
   */
  public static int getOffsetForAlignment(RVMClass t, boolean needsIdentityHash) {
    return JavaHeader.getOffsetForAlignment(t, needsIdentityHash);
  }

  /**
   * @param t RVMClass instance being copied
   * @param obj the object being copied
   * @return the offset relative to physical beginning of object
   * that must be aligned.
   */
  public static int getOffsetForAlignment(RVMClass t, ObjectReference obj) {
    return JavaHeader.getOffsetForAlignment(t, obj);
  }

  /**
   * @param t RVMArray instance being created
   * @param needsIdentityHash TODO document this parameter. Is it still needed?
   *  It's never set to true.
   * @return the offset relative to physical beginning of object that must
   * be aligned.
   */
  public static int getOffsetForAlignment(RVMArray t, boolean needsIdentityHash) {
    return JavaHeader.getOffsetForAlignment(t, needsIdentityHash);
  }

  /**
   * @param t RVMArray instance being copied
   * @param obj the object being copied
   * @return the offset relative to physical beginning of object that must
   * be aligned.
   */
  public static int getOffsetForAlignment(RVMArray t, ObjectReference obj) {
    return JavaHeader.getOffsetForAlignment(t, obj);
  }

  /**
   * Initialize raw storage with low memory word ptr of size bytes
   * to be an uninitialized instance of the (scalar) type specified by tib.
   *
   * @param ptr address of raw storage
   * @param tib the type information block
   * @param size number of bytes of raw storage allocated.
   * @return the object whose storage was initialized
   */
  @Inline
  public static Object initializeScalar(Address ptr, TIB tib, int size) {
    Object ref = JavaHeader.initializeScalarHeader(ptr, tib, size);
    MiscHeader.initializeHeader(ref, tib, size, true);
    setTIB(ref, tib);
    return ref;
  }

  /**
   * Allocate and initialize space in the bootimage (at bootimage writing time)
   * to be an uninitialized instance of the (scalar) type specified by klass.
   * NOTE: TIB is set by BootImageWriter2
   *
   * @param bootImage the bootimage to put the object in
   * @param klass the RVMClass object of the instance to create.
   * @param needsIdentityHash needs an identity hash value
   * @param identityHashValue the value for the identity hash
   * @return the offset of object in bootimage (in bytes)
   */
  @Interruptible
  public static Address allocateScalar(BootImageInterface bootImage, RVMClass klass, boolean needsIdentityHash, int identityHashValue) {
    TIB tib = klass.getTypeInformationBlock();
    int size = klass.getInstanceSize();
    if (needsIdentityHash) {
      if (ADDRESS_BASED_HASHING) {
        size += HASHCODE_BYTES;
      } else {
        // TODO: support rehashing or header initialisation for object models
        // that don't support an extra word for the hash code
        throw new Error("Unsupported allocation");
      }
    }
    int align = getAlignment(klass);
    int offset = getOffsetForAlignment(klass, needsIdentityHash);
    Address ptr = bootImage.allocateDataStorage(size, align, offset);
    Address ref = JavaHeader.initializeScalarHeader(bootImage, ptr, tib, size, needsIdentityHash, identityHashValue);
    MemoryManager.initializeHeader(bootImage, ref, tib, size, true);
    MiscHeader.initializeHeader(bootImage, ref, tib, size, true);
    return ref;
  }

  /**
   * Fills an alignment gap with the alignment value
   *
   * @param bootImage the bootimage being compiled
   * @param address the start address for the gap that needs to be filled
   * @param size the size of the gap to be filled
   */
  @Interruptible
  public static void fillAlignmentGap(BootImageInterface bootImage, Address address, Extent size) {
    while (size.GT(Extent.zero())) {
      bootImage.setFullWord(address, JavaHeader.ALIGNMENT_VALUE);
      address = address.plus(BYTES_IN_INT);
      size = size.minus(BYTES_IN_INT);
    }
  }

  /**
   * Initialize raw storage with low memory word ptr of size bytes
   * to be an uninitialized instance of the array type specific by tib
   * with numElems elements.
   *
   * @param ptr address of raw storage
   * @param tib the type information block
   * @param numElems number of elements in the array
   * @param size number of bytes of raw storage allocated.
   * @return the array whose header was initialized
   */
  @Inline
  public static Object initializeArray(Address ptr, TIB tib, int numElems, int size) {
    Object ref = JavaHeader.initializeArrayHeader(ptr, tib, size);
    MiscHeader.initializeHeader(ref, tib, size, false);
    setTIB(ref, tib);
    setArrayLength(ref, numElems);
    return ref;
  }

  /**
   * Allocate and initialize space in the bootimage (at bootimage writing time)
   * to be an uninitialized instance of the (array) type specified by array.
   * NOTE: TIB is set by BootimageWriter2
   *
   * @param bootImage the bootimage to put the object in
   * @param array RVMArray object of array being allocated.
   * @param numElements number of elements
   * @param needsIdentityHash needs an identity hash value
   * @param identityHashValue the value for the identity hash
   * @param alignCode TODO
   * @return Address of object in bootimage (in bytes)
   */
  @Interruptible
  public static Address allocateArray(BootImageInterface bootImage, RVMArray array, int numElements, boolean needsIdentityHash, int identityHashValue, int alignCode) {
    int align = getAlignment(array);
    return allocateArray(bootImage, array, numElements, needsIdentityHash, identityHashValue, align, alignCode);
  }

  /**
   * Allocate and initialize space in the bootimage (at bootimage writing time)
   * to be an uninitialized instance of the (array) type specified by array.
   * NOTE: TIB is set by BootimageWriter2
   *
   * @param bootImage the bootimage to put the object in
   * @param array RVMArray object of array being allocated.
   * @param numElements number of elements
   * @param needsIdentityHash needs an identity hash value
   * @param identityHashValue the value for the identity hash
   * @param align special alignment value
   * @param alignCode the alignment-encoded value
   * @return Address of object in bootimage (in bytes)
   */
  @Interruptible
  public static Address allocateArray(BootImageInterface bootImage, RVMArray array, int numElements, boolean needsIdentityHash, int identityHashValue, int align, int alignCode) {
    TIB tib = array.getTypeInformationBlock();
    int size = array.getInstanceSize(numElements);
    if (needsIdentityHash) {
      if (ADDRESS_BASED_HASHING) {
        size += HASHCODE_BYTES;
      } else {
        // TODO: support rehashing or header initialisation for object models
        // that don't support an extra word for the hash code
        throw new Error("Unsupported allocation");
      }
    }
    int offset = getOffsetForAlignment(array, needsIdentityHash);
    int padding = AlignmentEncoding.padding(alignCode);
    Address ptr = bootImage.allocateDataStorage(size + padding, align, offset);
    ptr = AlignmentEncoding.adjustRegion(alignCode, ptr);
    Address ref = JavaHeader.initializeArrayHeader(bootImage, ptr, tib, size, numElements, needsIdentityHash, identityHashValue);
    bootImage.setFullWord(ref.plus(getArrayLengthOffset()), numElements);
    MemoryManager.initializeHeader(bootImage, ref, tib, size, false);
    MiscHeader.initializeHeader(bootImage, ref, tib, size, false);
    return ref;
  }

  /**
   * Allocate and initialize space in the bootimage (at bootimage writing time)
   * to be an uninitialized instance of the (array) type specified by array.
   * NOTE: TIB is set by BootimageWriter2
   *
   * @param bootImage the bootimage to put the object in
   * @param array RVMArray object of array being allocated.
   * @param numElements number of elements
   * @return Address of object in bootimage
   */
  @Interruptible
  public static Address allocateCode(BootImageInterface bootImage, RVMArray array, int numElements) {
    TIB tib = array.getTypeInformationBlock();
    int size = array.getInstanceSize(numElements);
    int align = getAlignment(array);
    int offset = getOffsetForAlignment(array, false);
    Address ptr = bootImage.allocateCodeStorage(size, align, offset);
    Address ref = JavaHeader.initializeArrayHeader(bootImage, ptr, tib, size, numElements, false, 0);
    bootImage.setFullWord(ref.plus(getArrayLengthOffset()), numElements);
    MemoryManager.initializeHeader(bootImage, ref, tib, size, false);
    MiscHeader.initializeHeader(bootImage, ref, tib, size, false);
    return ref;
  }

  /**
   * For low level debugging of GC subsystem.
   * Dump the header word(s) of the given object reference.
   * @param ptr the object reference whose header should be dumped
   */
  public static void dumpHeader(ObjectReference ptr) {
    dumpHeader(ptr.toObject());
  }

  /**
   * For low level debugging of GC subsystem.
   * Dump the header word(s) of the given object reference.
   * @param ref the object reference whose header should be dumped
   */
  public static void dumpHeader(Object ref) {
    VM.sysWrite(" TIB=");
    VM.sysWrite(Magic.objectAsAddress(getTIB(ref)));
    JavaHeader.dumpHeader(ref);
    MiscHeader.dumpHeader(ref);
  }

  /**
   * For debugging: dumps descriptor of an object.
   *
   * @param addr the object to dump
   */
  public static void describeObject(ObjectReference addr) {
    Object obj = addr.toObject();
    RVMType type = Magic.getObjectType(obj);
    VM.sysWrite(type.getDescriptor());
  }
}


