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
package org.jikesrvm.mm.mminterface;

import org.jikesrvm.runtime.Magic;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

@Uninterruptible
public class Barriers implements org.mmtk.utility.Constants {
  /** True if the selected plan requires a read barrier on java.lang.ref.Reference types */
  private static final boolean NEEDS_JAVA_LANG_REFERENCE_GC_READ_BARRIER = Selected.Constraints.get().needsJavaLangReferenceReadBarrier();
  /** True if the selected plan requires a read barrier on java.lang.ref.Reference types */
  public static final boolean NEEDS_JAVA_LANG_REFERENCE_READ_BARRIER = NEEDS_JAVA_LANG_REFERENCE_GC_READ_BARRIER;

  /**
   * A java.lang.ref.Reference is being read.
   *
   * @param obj The non-null referent about to be released to the mutator.
   * @return The object to release to the mutator.
   */
  public static Object javaLangReferenceReadBarrier(Object obj) {
    if (NEEDS_JAVA_LANG_REFERENCE_GC_READ_BARRIER) {
      ObjectReference result = Selected.Mutator.get().javaLangReferenceReadBarrier(ObjectReference.fromObject(obj));
      return result.toObject();
    } else if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(false);
    return null;
  }

  /** True if the garbage collector requires write barriers on reference putfield, arraystore or modifycheck */
  private static final boolean NEEDS_OBJECT_GC_WRITE_BARRIER     = Selected.Constraints.get().needsObjectReferenceWriteBarrier();
  /** True if the VM requires write barriers on reference putfield */
  public static final boolean  NEEDS_OBJECT_PUTFIELD_BARRIER     = NEEDS_OBJECT_GC_WRITE_BARRIER;
  /** True if the VM requires write barriers on reference arraystore */
  public static final boolean  NEEDS_OBJECT_ASTORE_BARRIER       = NEEDS_OBJECT_GC_WRITE_BARRIER;
  /** True if the garbage collector requires read barriers on reference getfield or arrayload */
  private static final boolean NEEDS_OBJECT_GC_READ_BARRIER      = Selected.Constraints.get().needsObjectReferenceReadBarrier();
  /** True if the VM requires read barriers on reference getfield */
  public static final boolean  NEEDS_OBJECT_GETFIELD_BARRIER     = NEEDS_OBJECT_GC_READ_BARRIER;
  /** True if the VM requires read barriers on reference arrayload */
  public static final boolean  NEEDS_OBJECT_ALOAD_BARRIER        = NEEDS_OBJECT_GC_READ_BARRIER;

  /**
   * Barrier for writes of objects into fields of instances (ie putfield).
   *
   * @param ref the object which is the subject of the putfield
   * @param value the new value for the field
   * @param offset the offset of the field to be modified
   * @param locationMetadata an int that encodes the source location being modified
   */
  @Inline
  @Entrypoint
  public static void objectFieldWrite(Object ref, Object value, Offset offset, int locationMetadata) {
    if (NEEDS_OBJECT_GC_WRITE_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      Selected.Mutator.get().objectReferenceWrite(src,
          src.toAddress().plus(offset),
          ObjectReference.fromObject(value),
          offset.toWord(),
          Word.fromIntZeroExtend(locationMetadata),
          INSTANCE_FIELD);
    } else if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(false);
  }

  /**
   * Barrier for writes of objects into arrays (ie astore).
   *
   * @param ref the array which is the subject of the astore
   * @param index the index into the array where the new reference
   * resides.  The index is the "natural" index into the array, for
   * example a[index].
   * @param value the value to be stored.
   */
  @Inline
  @Entrypoint
  public static void objectArrayWrite(Object ref, int index, Object value) {
    if (NEEDS_OBJECT_GC_WRITE_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_ADDRESS);
      Selected.Mutator.get().objectReferenceWrite(array,
          array.toAddress().plus(offset),
          ObjectReference.fromObject(value),
          offset.toWord(),
          Word.zero(),      // don't know metadata
          ARRAY_ELEMENT);
    } else if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(false);
  }

  /**
   * Barrier for loads of objects from fields of instances (ie getfield).
   *
   * @param ref the object which is the subject of the getfield
   * @param offset the offset of the field to be read
   * @param locationMetadata an int that encodes the source location being read
   * @return The value read from the field.
   */
  @Inline
  @Entrypoint
  public static Object objectFieldRead(Object ref, Offset offset, int locationMetadata) {
    if (NEEDS_OBJECT_GC_READ_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      return Selected.Mutator.get().objectReferenceRead(src,
          src.toAddress().plus(offset),
          offset.toWord(),
          Word.fromIntZeroExtend(locationMetadata),
          INSTANCE_FIELD).toObject();
    } else if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(false);
    return null;
  }

  /**
   * Barrier for loads of objects from fields of arrays (ie aload).
   *
   * @param ref the array containing the reference.
   * @param index the index into the array were the reference resides.
   * @return the value read from the array
   */
  @Inline
  @Entrypoint
  public static Object objectArrayRead(Object ref, int index) {
    if (NEEDS_OBJECT_GC_READ_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_ADDRESS);
      return Selected.Mutator.get().objectReferenceRead(array,
          array.toAddress().plus(offset),
          offset.toWord(),
          Word.zero(), // don't know metadata
          ARRAY_ELEMENT).toObject();
    } else if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(false);
    return null;
  }

  /**
   * Barrier for a bulk copy of objects (i.e. in an array copy).
   *
   * @param src       The source object
   * @param srcOffset The offset of the first source address, in
   * bytes, relative to <code>src</code> (in principle, this could be
   * negative).
   * @param tgt        The target object
   * @param tgtOffset  The offset of the first target address, in bytes
   * relative to <code>tgt</code> (in principle, this could be
   * negative).
   * @param bytes The size of the region being copied, in bytes.
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  @Inline
  public static boolean objectBulkCopy(Object src, Offset srcOffset, Object tgt, Offset tgtOffset, int bytes) {
    if (NEEDS_OBJECT_GC_WRITE_BARRIER || NEEDS_OBJECT_GC_READ_BARRIER) {
      return Selected.Mutator.get().objectReferenceBulkCopy(ObjectReference.fromObject(src),
          srcOffset,
          ObjectReference.fromObject(tgt),
          tgtOffset,
          bytes);
    } else if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(false);
    return false;
  }


  /** True if the selected plan requires write barriers on reference putstatic */
  private static final boolean NEEDS_OBJECT_GC_PUTSTATIC_BARRIER = Selected.Constraints.get().needsObjectReferenceNonHeapWriteBarrier();
  /** True if the selected plan requires write barriers on reference putstatic */
  public static final boolean  NEEDS_OBJECT_PUTSTATIC_BARRIER    = NEEDS_OBJECT_GC_PUTSTATIC_BARRIER;
  /** True if the selected plan requires read barriers on reference getstatic */
  private static final boolean NEEDS_OBJECT_GC_GETSTATIC_BARRIER = Selected.Constraints.get().needsObjectReferenceNonHeapReadBarrier();
  /** True if the selected plan requires read barriers on reference getstatic */
  public static final boolean  NEEDS_OBJECT_GETSTATIC_BARRIER    = NEEDS_OBJECT_GC_GETSTATIC_BARRIER;

  /**
   * Barrier for writes of objects from statics (eg putstatic)
   *
   * @param value the new value to be stored
   * @param offset the offset of the field to be modified
   * @param locationMetadata an int that encodes the source location being modified
   */
  @Inline
  @Entrypoint
  public static void objectStaticWrite(Object value, Offset offset, int locationMetadata) {
    if (NEEDS_OBJECT_GC_PUTSTATIC_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(Magic.getJTOC());
      Selected.Mutator.get().objectReferenceNonHeapWrite(src.toAddress().plus(offset),
          ObjectReference.fromObject(value),
          offset.toWord(),
          Word.fromIntZeroExtend(locationMetadata));
    } else if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(false);
  }

  /**
   * Barrier for loads of objects from statics (ie getstatic)
   *
   * @param offset the offset of the field to be modified
   * @param locationMetadata an int that encodes the source location being read
   * @return the value read from the field
   */
  @Inline
  @Entrypoint
  public static Object objectStaticRead(Offset offset, int locationMetadata) {
    if (NEEDS_OBJECT_GC_GETSTATIC_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(Magic.getJTOC());
      return Selected.Mutator.get().objectReferenceNonHeapRead(
          src.toAddress().plus(offset),
          offset.toWord(),
          Word.fromIntZeroExtend(locationMetadata)).toObject();
    } else if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(false);
    return null;
  }


  /**
   * Barrier for conditional compare and exchange of reference fields.
   *
   * @param ref the object which is the subject of the compare and exchanges
   * @param offset the offset of the field to be modified
   * @param old the old value to swap out
   * @param value the new value for the field
   */
  @Inline
  public static boolean objectTryCompareAndSwap(Object ref, Offset offset, Object old, Object value) {
    if (NEEDS_OBJECT_GC_WRITE_BARRIER || NEEDS_OBJECT_GC_READ_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      return Selected.Mutator.get().objectReferenceTryCompareAndSwap(src,
          src.toAddress().plus(offset),
          ObjectReference.fromObject(old),
          ObjectReference.fromObject(value),
          offset.toWord(),
          Word.zero(), // do not have location metadata
          INSTANCE_FIELD);
    } else if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(false);
    return false;
  }
}
