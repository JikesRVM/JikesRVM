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
package org.jikesrvm.mm.mmtk;

import org.mmtk.utility.alloc.Allocator;

import org.jikesrvm.runtime.Magic;
import org.jikesrvm.objectmodel.JavaHeaderConstants;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.mm.mminterface.Selected;
import org.jikesrvm.mm.mminterface.DebugUtil;
import org.jikesrvm.mm.mminterface.MemoryManager;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

@Uninterruptible public final class ObjectModel extends org.mmtk.vm.ObjectModel implements org.mmtk.utility.Constants,
                                                                                           org.jikesrvm.Constants {

  protected Offset getArrayBaseOffset() { return JavaHeaderConstants.ARRAY_BASE_OFFSET; }

  /**
   * Copy an object using a plan's allocCopy to get space and install
   * the forwarding pointer.  On entry, <code>from</code> must have
   * been reserved for copying by the caller.  This method calls the
   * plan's <code>getStatusForCopy()</code> method to establish a new
   * status word for the copied object and <code>postCopy()</code> to
   * allow the plan to perform any post copy actions.
   *
   * @param from the address of the object to be copied
   * @return the address of the new object
   */
  @Inline
  public ObjectReference copy(ObjectReference from, int allocator) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(from);
    RVMType type = Magic.objectAsType(tib.getType());

    if (type.isClassType())
      return copyScalar(from, tib, type.asClass(), allocator);
    else
      return copyArray(from, tib, type.asArray(), allocator);
  }

  @Inline
  private ObjectReference copyScalar(ObjectReference from, TIB tib, RVMClass type, int allocator) {
    int bytes = org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(from.toObject(), type);
    int align = org.jikesrvm.objectmodel.ObjectModel.getAlignment(type, from.toObject());
    int offset = org.jikesrvm.objectmodel.ObjectModel.getOffsetForAlignment(type, from);
    Selected.Collector plan = Selected.Collector.get();
    allocator = plan.copyCheckAllocator(from, bytes, align, allocator);
    Address region = MemoryManager.allocateSpace(plan, bytes, align, offset,
                                                allocator, from);
    Object toObj = org.jikesrvm.objectmodel.ObjectModel.moveObject(region, from.toObject(), bytes, type);
    ObjectReference to = ObjectReference.fromObject(toObj);
    plan.postCopy(to, ObjectReference.fromObject(tib), bytes, allocator);
    return to;
  }

  @Inline
  private ObjectReference copyArray(ObjectReference from, TIB tib, RVMArray type, int allocator) {
    int elements = Magic.getArrayLength(from.toObject());
    int bytes = org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(from.toObject(), type, elements);
    int align = org.jikesrvm.objectmodel.ObjectModel.getAlignment(type, from.toObject());
    int offset = org.jikesrvm.objectmodel.ObjectModel.getOffsetForAlignment(type, from);
    Selected.Collector plan = Selected.Collector.get();
    allocator = plan.copyCheckAllocator(from, bytes, align, allocator);
    Address region = MemoryManager.allocateSpace(plan, bytes, align, offset,
                                                allocator, from);
    Object toObj = org.jikesrvm.objectmodel.ObjectModel.moveObject(region, from.toObject(), bytes, type);
    ObjectReference to = ObjectReference.fromObject(toObj);
    plan.postCopy(to, ObjectReference.fromObject(tib), bytes, allocator);
    if (type == RVMType.CodeArrayType) {
      // sync all moved code arrays to get icache and dcache in sync
      // immediately.
      int dataSize = bytes - org.jikesrvm.objectmodel.ObjectModel.computeHeaderSize(Magic.getObjectType(toObj));
      org.jikesrvm.runtime.Memory.sync(to.toAddress(), dataSize);
    }
    return to;
  }

  /**
   * Return the size of a given object, in bytes
   *
   * @param object The object whose size is being queried
   * @return The size (in bytes) of the given object.
   */
  static int getObjectSize(ObjectReference object) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(object);
    RVMType type = Magic.objectAsType(tib.getType());

    if (type.isClassType())
      return org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(object.toObject(), type.asClass());
    else
      return org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(object.toObject(), type.asArray(), Magic.getArrayLength(object.toObject()));
  }

  /**
   * Copy an object to be pointer to by the to address. This is required
   * for delayed-copy collectors such as compacting collectors. During the
   * collection, MMTk reserves a region in the heap for an object as per
   * requirements found from ObjectModel and then asks ObjectModel to
   * determine what the object's reference will be post-copy.
   *
   * @param from the address of the object to be copied
   * @param to The target location.
   * @param region The start (or an address less than) the region that was reserved for this object.
   * @return Address The address past the end of the copied object
   */
  @Inline
  public Address copyTo(ObjectReference from, ObjectReference to, Address region) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(from);
    RVMType type = tib.getType();
    int bytes;

    boolean copy = (from != to);

    if (copy) {
      if (type.isClassType()) {
        RVMClass classType = type.asClass();
        bytes = org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(from.toObject(), classType);
        org.jikesrvm.objectmodel.ObjectModel.moveObject(from.toObject(), to.toObject(), bytes, classType);
      } else {
      RVMArray arrayType = type.asArray();
        int elements = Magic.getArrayLength(from.toObject());
        bytes = org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(from.toObject(), arrayType, elements);
        org.jikesrvm.objectmodel.ObjectModel.moveObject(from.toObject(), to.toObject(), bytes, arrayType);
      }
    } else {
      bytes = getCurrentSize(to);
    }

    Address start = org.jikesrvm.objectmodel.ObjectModel.objectStartRef(to);
    Allocator.fillAlignmentGap(region, start);

    return start.plus(bytes);
  }

  /**
   * Return the reference that an object will be refered to after it is copied
   * to the specified region. Used in delayed-copy collectors such as compacting
   * collectors.
   *
   * @param from The object to be copied.
   * @param to The region to be copied to.
   * @return The resulting reference.
   */
  public ObjectReference getReferenceWhenCopiedTo(ObjectReference from, Address to) {
    return ObjectReference.fromObject(org.jikesrvm.objectmodel.ObjectModel.getReferenceWhenCopiedTo(from.toObject(), to));
  }

  /**
   * Gets a pointer to the address just past the end of the object.
   *
   * @param object The objecty.
   */
  public Address getObjectEndAddress(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.getObjectEndAddress(object.toObject());
  }

  /**
   * Return the size required to copy an object
   *
   * @param object The object whose size is to be queried
   * @return The size required to copy <code>obj</code>
   */
  public int getSizeWhenCopied(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(object.toObject());
  }

  /**
   * Return the alignment requirement for a copy of this object
   *
   * @param object The object whose size is to be queried
   * @return The alignment required for a copy of <code>obj</code>
   */
  public int getAlignWhenCopied(ObjectReference object) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(object);
    RVMType type = tib.getType();
    if (type.isArrayType()) {
      return org.jikesrvm.objectmodel.ObjectModel.getAlignment(type.asArray(), object.toObject());
    } else {
      return org.jikesrvm.objectmodel.ObjectModel.getAlignment(type.asClass(), object.toObject());
    }
  }

  /**
   * Return the alignment offset requirements for a copy of this object
   *
   * @param object The object whose size is to be queried
   * @return The alignment offset required for a copy of <code>obj</code>
   */
  public int getAlignOffsetWhenCopied(ObjectReference object) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(object);
    RVMType type = tib.getType();
    if (type.isArrayType()) {
      return org.jikesrvm.objectmodel.ObjectModel.getOffsetForAlignment(type.asArray(), object);
    } else {
      return org.jikesrvm.objectmodel.ObjectModel.getOffsetForAlignment(type.asClass(), object);
    }
  }

  /**
   * Return the size used by an object
   *
   * @param object The object whose size is to be queried
   * @return The size of <code>obj</code>
   */
  public int getCurrentSize(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.bytesUsed(object.toObject());
  }

  /**
   * Return the next object in the heap under contiguous allocation
   */
  public ObjectReference getNextObject(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.getNextObject(object);
  }

  /**
   * Return an object reference from knowledge of the low order word
   */
  public ObjectReference getObjectFromStartAddress(Address start) {
    return org.jikesrvm.objectmodel.ObjectModel.getObjectFromStartAddress(start);
  }

  /**
   * Get the type descriptor for an object.
   *
   * @param ref address of the object
   * @return byte array with the type descriptor
   */
  public byte [] getTypeDescriptor(ObjectReference ref) {
    Atom descriptor = Magic.getObjectType(ref).getDescriptor();
    return descriptor.toByteArray();
  }

  @Inline
  public int getArrayLength(ObjectReference object) {
    return Magic.getArrayLength(object.toObject());
  }

  /**
   * Is the passed object an array?
   *
   * @param object address of the object
   */
  public boolean isArray(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.getObjectType(object.toObject()).isArrayType();
  }

  /**
   * Is the passed object a primitive array?
   *
   * @param object address of the object
   */
  public boolean isPrimitiveArray(ObjectReference object) {
    Object obj = object.toObject();
    return (obj instanceof long[]   ||
            obj instanceof int[]    ||
            obj instanceof short[]  ||
            obj instanceof byte[]   ||
            obj instanceof double[] ||
            obj instanceof float[]);
  }

  /**
   * Tests a bit available for memory manager use in an object.
   *
   * @param object the address of the object
   * @param idx the index of the bit
   */
  public boolean testAvailableBit(ObjectReference object, int idx) {
    return org.jikesrvm.objectmodel.ObjectModel.testAvailableBit(object.toObject(), idx);
  }

  /**
   * Sets a bit available for memory manager use in an object.
   *
   * @param object the address of the object
   * @param idx the index of the bit
   * @param flag <code>true</code> to set the bit to 1,
   * <code>false</code> to set it to 0
   */
  public void setAvailableBit(ObjectReference object, int idx,
                                     boolean flag) {
    org.jikesrvm.objectmodel.ObjectModel.setAvailableBit(object.toObject(), idx, flag);
  }

  /**
   * Attempts to set the bits available for memory manager use in an
   * object.  The attempt will only be successful if the current value
   * of the bits matches <code>oldVal</code>.  The comparison with the
   * current value and setting are atomic with respect to other
   * allocators.
   *
   * @param object the address of the object
   * @param oldVal the required current value of the bits
   * @param newVal the desired new value of the bits
   * @return <code>true</code> if the bits were set,
   * <code>false</code> otherwise
   */
  public boolean attemptAvailableBits(ObjectReference object,
                                             Word oldVal, Word newVal) {
    return org.jikesrvm.objectmodel.ObjectModel.attemptAvailableBits(object.toObject(), oldVal,
                                               newVal);
  }

  /**
   * Gets the value of bits available for memory manager use in an
   * object, in preparation for setting those bits.
   *
   * @param object the address of the object
   * @return the value of the bits
   */
  public Word prepareAvailableBits(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.prepareAvailableBits(object.toObject());
  }

  /**
   * Sets the byte available for memory manager use in an object.
   *
   * @param object the address of the object
   * @param val the new value of the byte
   */
  public void writeAvailableByte(ObjectReference object, byte val) {
    org.jikesrvm.objectmodel.ObjectModel.writeAvailableByte(object.toObject(), val);
  }

  /**
   * Read the byte available for memory manager use in an object.
   *
   * @param object the address of the object
   * @return the value of the byte
   */
  public byte readAvailableByte(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.readAvailableByte(object.toObject());
  }

  /**
   * Sets the bits available for memory manager use in an object.
   *
   * @param object the address of the object
   * @param val the new value of the bits
   */
  public void writeAvailableBitsWord(ObjectReference object, Word val) {
    org.jikesrvm.objectmodel.ObjectModel.writeAvailableBitsWord(object.toObject(), val);
  }

  /**
   * Read the bits available for memory manager use in an object.
   *
   * @param object the address of the object
   * @return the value of the bits
   */
  public Word readAvailableBitsWord(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.readAvailableBitsWord(object.toObject());
  }

  /**
   * Gets the offset of the memory management header from the object
   * reference address.  XXX The object model / memory manager
   * interface should be improved so that the memory manager does not
   * need to know this.
   *
   * @return the offset, relative the object reference address
   */
  /* AJG: Should this be a variable rather than method? */
  public Offset GC_HEADER_OFFSET() {
    return org.jikesrvm.objectmodel.ObjectModel.GC_HEADER_OFFSET;
  }

  /**
   * Returns the lowest address of the storage associated with an object.
   *
   * @param object the reference address of the object
   * @return the lowest address of the object
   */
  @Inline
  public Address objectStartRef(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.objectStartRef(object);
  }

  /**
   * Returns an address guaranteed to be inside the storage assocatied
   * with and object.
   *
   * @param object the reference address of the object
   * @return an address inside the object
   */
  public Address refToAddress(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.getPointerInMemoryRegion(object);
  }

  /**
   * Checks if a reference of the given type in another object is
   * inherently acyclic.  The type is given as a TIB.
   *
   * @return <code>true</code> if a reference of the type is
   * inherently acyclic
   */
  @Inline
  public boolean isAcyclic(ObjectReference typeRef) {
    TIB tib = Magic.addressAsTIB(typeRef.toAddress());
    RVMType type = tib.getType();
    return type.isAcyclicReference();
  }

  /**
   * Dump debugging information for an object.
   *
   * @param object The object whose information is to be dumped
   */
  public void dumpObject(ObjectReference object) {
    DebugUtil.dumpRef(object);
  }
}

