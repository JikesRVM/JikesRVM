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
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.VM;
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
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return null;
  }

  /* bool byte char short int long float double */

  /** True if the garbage collector requires write barriers on boolean putfield, arraystore or modifycheck */
  private static final boolean NEEDS_BOOLEAN_GC_WRITE_BARRIER     = Selected.Constraints.get().needsBooleanWriteBarrier();
  /** True if the VM requires write barriers on boolean putfield */
  public static final boolean  NEEDS_BOOLEAN_PUTFIELD_BARRIER     = NEEDS_BOOLEAN_GC_WRITE_BARRIER;
  /** True if the VM requires write barriers on boolean arraystore */
  public static final boolean  NEEDS_BOOLEAN_ASTORE_BARRIER       = NEEDS_BOOLEAN_GC_WRITE_BARRIER;
  /** True if the garbage collector requires read barriers on boolean getfield or arrayload */
  private static final boolean NEEDS_BOOLEAN_GC_READ_BARRIER      = Selected.Constraints.get().needsBooleanReadBarrier();
  /** True if the VM requires read barriers on boolean getfield */
  public static final boolean  NEEDS_BOOLEAN_GETFIELD_BARRIER     = NEEDS_BOOLEAN_GC_READ_BARRIER;
  /** True if the VM requires read barriers on boolean arrayload */
  public static final boolean  NEEDS_BOOLEAN_ALOAD_BARRIER        = NEEDS_BOOLEAN_GC_READ_BARRIER;
  /** True if the garbage collector does not support the bulk copy operation */
  public static final boolean BOOLEAN_BULK_COPY_SUPPORTED         = !(NEEDS_BOOLEAN_ASTORE_BARRIER || NEEDS_BOOLEAN_ALOAD_BARRIER) || Selected.Constraints.get().booleanBulkCopySupported();

  /**
   * Barrier for writes of booleans into fields of instances (ie putfield).
   *
   * @param ref the object which is the subject of the putfield
   * @param value the new value for the field
   * @param offset the offset of the field to be modified
   * @param locationMetadata an int that encodes the source location being modified
   */
  @Inline
  @Entrypoint
  public static void booleanFieldWrite(Object ref, boolean value, Offset offset, int locationMetadata) {
    if (NEEDS_BOOLEAN_GC_WRITE_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      Selected.Mutator.get().booleanWrite(src, src.toAddress().plus(offset), value, offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
  }

  /**
   * Barrier for writes of booleans into arrays (ie astore).
   *
   * @param ref the array which is the subject of the astore
   * @param index the index into the array where the new reference
   * resides.  The index is the "natural" index into the array, for
   * example a[index].
   * @param value the value to be stored.
   */
  @Inline
  @Entrypoint
  public static void booleanArrayWrite(boolean[] ref, int index, boolean value) {
    if (NEEDS_BOOLEAN_GC_WRITE_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_BOOLEAN);
      Selected.Mutator.get().booleanWrite(array, array.toAddress().plus(offset), value, offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
  }

  /**
   * Barrier for loads of booleans from fields of instances (ie getfield).
   *
   * @param ref the object which is the subject of the getfield
   * @param offset the offset of the field to be read
   * @param locationMetadata an int that encodes the source location being read
   * @return The value read from the field.
   */
  @Inline
  @Entrypoint
  public static boolean booleanFieldRead(Object ref, Offset offset, int locationMetadata) {
    if (NEEDS_BOOLEAN_GC_READ_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      return Selected.Mutator.get().booleanRead(src, src.toAddress().plus(offset), offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return false;
  }

  /**
   * Barrier for loads of booleans from fields of arrays (ie aload).
   *
   * @param ref the array containing the reference.
   * @param index the index into the array were the reference resides.
   * @return the value read from the array
   */
  @Inline
  @Entrypoint
  public static boolean booleanArrayRead(boolean[] ref, int index) {
    if (NEEDS_BOOLEAN_GC_READ_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_BOOLEAN);
      return Selected.Mutator.get().booleanRead(array, array.toAddress().plus(offset), offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return false;
  }

  /**
   * Barrier for a bulk copy of booleans (i.e. in an array copy).
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   */
  @Inline
  public static void booleanBulkCopy(boolean[] src, Offset srcOffset, boolean[] dst, Offset dstOffset, int bytes) {
    if (VM.VerifyAssertions) VM._assert(BOOLEAN_BULK_COPY_SUPPORTED);

    if (!Selected.Mutator.get().booleanBulkCopy(ObjectReference.fromObject(src), srcOffset, ObjectReference.fromObject(dst), dstOffset, bytes)) {
      Memory.arraycopy8Bit(Magic.objectAsAddress(dst).plus(dstOffset), Magic.objectAsAddress(src).plus(srcOffset), bytes);
    }
  }

  /** True if the garbage collector requires write barriers on byte putfield, arraystore or modifycheck */
  private static final boolean NEEDS_BYTE_GC_WRITE_BARRIER     = Selected.Constraints.get().needsByteWriteBarrier();
  /** True if the VM requires write barriers on byte putfield */
  public static final boolean  NEEDS_BYTE_PUTFIELD_BARRIER     = NEEDS_BYTE_GC_WRITE_BARRIER;
  /** True if the VM requires write barriers on byte arraystore */
  public static final boolean  NEEDS_BYTE_ASTORE_BARRIER       = NEEDS_BYTE_GC_WRITE_BARRIER;
  /** True if the garbage collector requires read barriers on byte getfield or arrayload */
  private static final boolean NEEDS_BYTE_GC_READ_BARRIER      = Selected.Constraints.get().needsByteReadBarrier();
  /** True if the VM requires read barriers on byte getfield */
  public static final boolean  NEEDS_BYTE_GETFIELD_BARRIER     = NEEDS_BYTE_GC_READ_BARRIER;
  /** True if the VM requires read barriers on byte arrayload */
  public static final boolean  NEEDS_BYTE_ALOAD_BARRIER        = NEEDS_BYTE_GC_READ_BARRIER;
  /** True if the garbage collector does not support the bulk copy operation */
  public static final boolean BYTE_BULK_COPY_SUPPORTED         = !(NEEDS_BYTE_ASTORE_BARRIER || NEEDS_BYTE_ALOAD_BARRIER) || Selected.Constraints.get().byteBulkCopySupported();

  /**
   * Barrier for writes of bytes into fields of instances (ie putfield).
   *
   * @param ref the object which is the subject of the putfield
   * @param value the new value for the field
   * @param offset the offset of the field to be modified
   * @param locationMetadata an int that encodes the source location being modified
   */
  @Inline
  @Entrypoint
  public static void byteFieldWrite(Object ref, byte value, Offset offset, int locationMetadata) {
    if (NEEDS_BYTE_GC_WRITE_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      Selected.Mutator.get().byteWrite(src, src.toAddress().plus(offset), value, offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
  public static void byteArrayWrite(byte[] ref, int index, byte value) {
    if (NEEDS_BYTE_GC_WRITE_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index);
      Selected.Mutator.get().byteWrite(array, array.toAddress().plus(offset), value, offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
  public static byte byteFieldRead(Object ref, Offset offset, int locationMetadata) {
    if (NEEDS_BYTE_GC_READ_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      return Selected.Mutator.get().byteRead(src, src.toAddress().plus(offset), offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return 0;
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
  public static byte byteArrayRead(byte[] ref, int index) {
    if (NEEDS_BYTE_GC_READ_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index);
      return Selected.Mutator.get().byteRead(array, array.toAddress().plus(offset), offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return 0;
  }

  /**
   * Barrier for a bulk copy of bytes (i.e. in an array copy).
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   */
  @Inline
  public static void byteBulkCopy(byte[] src, Offset srcOffset, byte[] dst, Offset dstOffset, int bytes) {
    if (VM.VerifyAssertions) VM._assert(BYTE_BULK_COPY_SUPPORTED);

    if (!Selected.Mutator.get().byteBulkCopy(ObjectReference.fromObject(src), srcOffset, ObjectReference.fromObject(dst), dstOffset, bytes)) {
      Memory.arraycopy8Bit(Magic.objectAsAddress(dst).plus(dstOffset), Magic.objectAsAddress(src).plus(srcOffset), bytes);
    }
  }


  /** True if the garbage collector requires write barriers on char putfield, arraystore or modifycheck */
  private static final boolean NEEDS_CHAR_GC_WRITE_BARRIER     = Selected.Constraints.get().needsCharWriteBarrier();
  /** True if the VM requires write barriers on char putfield */
  public static final boolean  NEEDS_CHAR_PUTFIELD_BARRIER     = NEEDS_CHAR_GC_WRITE_BARRIER;
  /** True if the VM requires write barriers on char arraystore */
  public static final boolean  NEEDS_CHAR_ASTORE_BARRIER       = NEEDS_CHAR_GC_WRITE_BARRIER;
  /** True if the garbage collector requires read barriers on char getfield or arrayload */
  private static final boolean NEEDS_CHAR_GC_READ_BARRIER      = Selected.Constraints.get().needsCharReadBarrier();
  /** True if the VM requires read barriers on char getfield */
  public static final boolean  NEEDS_CHAR_GETFIELD_BARRIER     = NEEDS_CHAR_GC_READ_BARRIER;
  /** True if the VM requires read barriers on char arrayload */
  public static final boolean  NEEDS_CHAR_ALOAD_BARRIER        = NEEDS_CHAR_GC_READ_BARRIER;
  /** True if the garbage collector does not support the bulk copy operation */
  public static final boolean CHAR_BULK_COPY_SUPPORTED         = !(NEEDS_CHAR_ASTORE_BARRIER || NEEDS_CHAR_ALOAD_BARRIER) || Selected.Constraints.get().charBulkCopySupported();

  /**
   * Barrier for writes of chars into fields of instances (ie putfield).
   *
   * @param ref the object which is the subject of the putfield
   * @param value the new value for the field
   * @param offset the offset of the field to be modified
   * @param locationMetadata an int that encodes the source location being modified
   */
  @Inline
  @Entrypoint
  public static void charFieldWrite(Object ref, char value, Offset offset, int locationMetadata) {
    if (NEEDS_CHAR_GC_WRITE_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      Selected.Mutator.get().charWrite(src, src.toAddress().plus(offset), value, offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
  public static void charArrayWrite(char[] ref, int index, char value) {
    if (NEEDS_CHAR_GC_WRITE_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_ADDRESS);
      Selected.Mutator.get().charWrite(array, array.toAddress().plus(offset), value, offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
  public static char charFieldRead(Object ref, Offset offset, int locationMetadata) {
    if (NEEDS_CHAR_GC_READ_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      return Selected.Mutator.get().charRead(src, src.toAddress().plus(offset), offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return 0;
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
  public static char charArrayRead(char[] ref, int index) {
    if (NEEDS_CHAR_GC_READ_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_CHAR);
      return Selected.Mutator.get().charRead(array, array.toAddress().plus(offset), offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return 0;
  }

  /**
   * Barrier for a bulk copy of chars (i.e. in an array copy).
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   */
  @Inline
  public static void charBulkCopy(char[] src, Offset srcOffset, char[] dst, Offset dstOffset, int bytes) {
    if (VM.VerifyAssertions) VM._assert(CHAR_BULK_COPY_SUPPORTED);

    if (!Selected.Mutator.get().charBulkCopy(ObjectReference.fromObject(src), srcOffset, ObjectReference.fromObject(dst), dstOffset, bytes)) {
      Memory.arraycopy16Bit(Magic.objectAsAddress(dst).plus(dstOffset), Magic.objectAsAddress(src).plus(srcOffset), bytes);
    }
  }


  /** True if the garbage collector requires write barriers on short putfield, arraystore or modifycheck */
  private static final boolean NEEDS_SHORT_GC_WRITE_BARRIER     = Selected.Constraints.get().needsShortWriteBarrier();
  /** True if the VM requires write barriers on short putfield */
  public static final boolean  NEEDS_SHORT_PUTFIELD_BARRIER     = NEEDS_SHORT_GC_WRITE_BARRIER;
  /** True if the VM requires write barriers on short arraystore */
  public static final boolean  NEEDS_SHORT_ASTORE_BARRIER       = NEEDS_SHORT_GC_WRITE_BARRIER;
  /** True if the garbage collector requires read barriers on short getfield or arrayload */
  private static final boolean NEEDS_SHORT_GC_READ_BARRIER      = Selected.Constraints.get().needsShortReadBarrier();
  /** True if the VM requires read barriers on short getfield */
  public static final boolean  NEEDS_SHORT_GETFIELD_BARRIER     = NEEDS_SHORT_GC_READ_BARRIER;
  /** True if the VM requires read barriers on short arrayload */
  public static final boolean  NEEDS_SHORT_ALOAD_BARRIER        = NEEDS_SHORT_GC_READ_BARRIER;
  /** True if the garbage collector does not support the bulk copy operation */
  public static final boolean SHORT_BULK_COPY_SUPPORTED         = !(NEEDS_SHORT_ASTORE_BARRIER || NEEDS_SHORT_ALOAD_BARRIER) || Selected.Constraints.get().shortBulkCopySupported();

  /**
   * Barrier for writes of shorts into fields of instances (ie putfield).
   *
   * @param ref the object which is the subject of the putfield
   * @param value the new value for the field
   * @param offset the offset of the field to be modified
   * @param locationMetadata an int that encodes the source location being modified
   */
  @Inline
  @Entrypoint
  public static void shortFieldWrite(Object ref, short value, Offset offset, int locationMetadata) {
    if (NEEDS_SHORT_GC_WRITE_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      Selected.Mutator.get().shortWrite(src, src.toAddress().plus(offset), value, offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
  public static void shortArrayWrite(short[] ref, int index, short value) {
    if (NEEDS_SHORT_GC_WRITE_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_SHORT);
      Selected.Mutator.get().shortWrite(array, array.toAddress().plus(offset), value, offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
  public static short shortFieldRead(Object ref, Offset offset, int locationMetadata) {
    if (NEEDS_SHORT_GC_READ_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      return Selected.Mutator.get().shortRead(src, src.toAddress().plus(offset), offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return 0;
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
  public static short shortArrayRead(short[] ref, int index) {
    if (NEEDS_SHORT_GC_READ_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_SHORT);
      return Selected.Mutator.get().shortRead(array, array.toAddress().plus(offset), offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return 0;
  }

  /**
   * Barrier for a bulk copy of shorts (i.e. in an array copy).
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   */
  @Inline
  public static void shortBulkCopy(short[] src, Offset srcOffset, short[] dst, Offset dstOffset, int bytes) {
    if (VM.VerifyAssertions) VM._assert(SHORT_BULK_COPY_SUPPORTED);

    if (!Selected.Mutator.get().shortBulkCopy(ObjectReference.fromObject(src), srcOffset, ObjectReference.fromObject(dst), dstOffset, bytes)) {
      Memory.arraycopy16Bit(Magic.objectAsAddress(dst).plus(dstOffset), Magic.objectAsAddress(src).plus(srcOffset), bytes);
    }
  }



  /** True if the garbage collector requires write barriers on int putfield, arraystore or modifycheck */
  private static final boolean NEEDS_INT_GC_WRITE_BARRIER     = Selected.Constraints.get().needsIntWriteBarrier();
  /** True if the VM requires write barriers on int putfield */
  public static final boolean  NEEDS_INT_PUTFIELD_BARRIER     = NEEDS_INT_GC_WRITE_BARRIER;
  /** True if the VM requires write barriers on int arraystore */
  public static final boolean  NEEDS_INT_ASTORE_BARRIER       = NEEDS_INT_GC_WRITE_BARRIER;
  /** True if the garbage collector requires read barriers on int getfield or arrayload */
  private static final boolean NEEDS_INT_GC_READ_BARRIER      = Selected.Constraints.get().needsIntReadBarrier();
  /** True if the VM requires read barriers on int getfield */
  public static final boolean  NEEDS_INT_GETFIELD_BARRIER     = NEEDS_INT_GC_READ_BARRIER;
  /** True if the VM requires read barriers on int arrayload */
  public static final boolean  NEEDS_INT_ALOAD_BARRIER        = NEEDS_INT_GC_READ_BARRIER;
  /** True if the garbage collector does not support the bulk copy operation */
  public static final boolean INT_BULK_COPY_SUPPORTED         = !(NEEDS_INT_ASTORE_BARRIER || NEEDS_INT_ALOAD_BARRIER) || Selected.Constraints.get().intBulkCopySupported();

  /**
   * Barrier for writes of ints into fields of instances (ie putfield).
   *
   * @param ref the object which is the subject of the putfield
   * @param value the new value for the field
   * @param offset the offset of the field to be modified
   * @param locationMetadata an int that encodes the source location being modified
   */
  @Inline
  @Entrypoint
  public static void intFieldWrite(Object ref, int value, Offset offset, int locationMetadata) {
    if (NEEDS_INT_GC_WRITE_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      Selected.Mutator.get().intWrite(src, src.toAddress().plus(offset), value, offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
  public static void intArrayWrite(int[] ref, int index, int value) {
    if (NEEDS_INT_GC_WRITE_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_INT);
      Selected.Mutator.get().intWrite(array, array.toAddress().plus(offset), value, offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
  public static int intFieldRead(Object ref, Offset offset, int locationMetadata) {
    if (NEEDS_INT_GC_READ_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      return Selected.Mutator.get().intRead(src, src.toAddress().plus(offset), offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return 0;
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
  public static int intArrayRead(int[] ref, int index) {
    if (NEEDS_INT_GC_READ_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_INT);
      return Selected.Mutator.get().intRead(array, array.toAddress().plus(offset), offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return 0;
  }

  /**
   * Barrier for a bulk copy of ints (i.e. in an array copy).
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   */
  @Inline
  public static void intBulkCopy(int[] src, Offset srcOffset, int[] dst, Offset dstOffset, int bytes) {
    if (VM.VerifyAssertions) VM._assert(INT_BULK_COPY_SUPPORTED);

    if (!Selected.Mutator.get().intBulkCopy(ObjectReference.fromObject(src), srcOffset, ObjectReference.fromObject(dst), dstOffset, bytes)) {
      Memory.arraycopy32Bit(Magic.objectAsAddress(dst).plus(dstOffset), Magic.objectAsAddress(src).plus(srcOffset), bytes);
    }
  }



  /** True if the garbage collector requires write barriers on long putfield, arraystore or modifycheck */
  private static final boolean NEEDS_LONG_GC_WRITE_BARRIER     = Selected.Constraints.get().needsLongWriteBarrier();
  /** True if the VM requires write barriers on long putfield */
  public static final boolean  NEEDS_LONG_PUTFIELD_BARRIER     = NEEDS_LONG_GC_WRITE_BARRIER;
  /** True if the VM requires write barriers on long arraystore */
  public static final boolean  NEEDS_LONG_ASTORE_BARRIER       = NEEDS_LONG_GC_WRITE_BARRIER;
  /** True if the garbage collector requires read barriers on long getfield or arrayload */
  private static final boolean NEEDS_LONG_GC_READ_BARRIER      = Selected.Constraints.get().needsLongReadBarrier();
  /** True if the VM requires read barriers on long getfield */
  public static final boolean  NEEDS_LONG_GETFIELD_BARRIER     = NEEDS_LONG_GC_READ_BARRIER;
  /** True if the VM requires read barriers on long arrayload */
  public static final boolean  NEEDS_LONG_ALOAD_BARRIER        = NEEDS_LONG_GC_READ_BARRIER;
  /** True if the garbage collector supports the bulk copy operation */
  public static final boolean LONG_BULK_COPY_SUPPORTED         = !(NEEDS_LONG_ASTORE_BARRIER || NEEDS_LONG_ALOAD_BARRIER) || Selected.Constraints.get().longBulkCopySupported();

  /**
   * Barrier for writes of longs into fields of instances (ie putfield).
   *
   * @param ref the object which is the subject of the putfield
   * @param value the new value for the field
   * @param offset the offset of the field to be modified
   * @param locationMetadata an int that encodes the source location being modified
   */
  @Inline
  @Entrypoint
  public static void longFieldWrite(Object ref, long value, Offset offset, int locationMetadata) {
    if (NEEDS_LONG_GC_WRITE_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      Selected.Mutator.get().longWrite(src, src.toAddress().plus(offset), value, offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
  public static void longArrayWrite(long[] ref, int index, long value) {
    if (NEEDS_LONG_GC_WRITE_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_LONG);
      Selected.Mutator.get().longWrite(array, array.toAddress().plus(offset), value, offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
  public static long longFieldRead(Object ref, Offset offset, int locationMetadata) {
    if (NEEDS_LONG_GC_READ_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      return Selected.Mutator.get().longRead(src, src.toAddress().plus(offset), offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return 0;
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
  public static long longArrayRead(long[] ref, int index) {
    if (NEEDS_LONG_GC_READ_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_LONG);
      return Selected.Mutator.get().longRead(array, array.toAddress().plus(offset), offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return 0;
  }

  /**
   * Barrier for a bulk copy of longs (i.e. in an array copy).
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   */
  @Inline
  public static void longBulkCopy(long[] src, Offset srcOffset, long[] dst, Offset dstOffset, int bytes) {
    if (VM.VerifyAssertions) VM._assert(LONG_BULK_COPY_SUPPORTED);

    if (!Selected.Mutator.get().longBulkCopy(ObjectReference.fromObject(src), srcOffset, ObjectReference.fromObject(dst), dstOffset, bytes)) {
      Memory.arraycopy64Bit(Magic.objectAsAddress(dst).plus(dstOffset), Magic.objectAsAddress(src).plus(srcOffset), bytes);
    }
  }


  /** True if the garbage collector requires write barriers on float putfield, arraystore or modifycheck */
  private static final boolean NEEDS_FLOAT_GC_WRITE_BARRIER     = Selected.Constraints.get().needsFloatWriteBarrier();
  /** True if the VM requires write barriers on float putfield */
  public static final boolean  NEEDS_FLOAT_PUTFIELD_BARRIER     = NEEDS_FLOAT_GC_WRITE_BARRIER;
  /** True if the VM requires write barriers on float arraystore */
  public static final boolean  NEEDS_FLOAT_ASTORE_BARRIER       = NEEDS_FLOAT_GC_WRITE_BARRIER;
  /** True if the garbage collector requires read barriers on float getfield or arrayload */
  private static final boolean NEEDS_FLOAT_GC_READ_BARRIER      = Selected.Constraints.get().needsFloatReadBarrier();
  /** True if the VM requires read barriers on float getfield */
  public static final boolean  NEEDS_FLOAT_GETFIELD_BARRIER     = NEEDS_FLOAT_GC_READ_BARRIER;
  /** True if the VM requires read barriers on float arrayload */
  public static final boolean  NEEDS_FLOAT_ALOAD_BARRIER        = NEEDS_FLOAT_GC_READ_BARRIER;
  /** True if the garbage collector supports the bulk copy operation */
  public static final boolean FLOAT_BULK_COPY_SUPPORTED         = !(NEEDS_FLOAT_ASTORE_BARRIER || NEEDS_FLOAT_ALOAD_BARRIER) || Selected.Constraints.get().floatBulkCopySupported();

  /**
   * Barrier for writes of floats into fields of instances (ie putfield).
   *
   * @param ref the object which is the subject of the putfield
   * @param value the new value for the field
   * @param offset the offset of the field to be modified
   * @param locationMetadata an int that encodes the source location being modified
   */
  @Inline
  @Entrypoint
  public static void floatFieldWrite(Object ref, float value, Offset offset, int locationMetadata) {
    if (NEEDS_FLOAT_GC_WRITE_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      Selected.Mutator.get().floatWrite(src, src.toAddress().plus(offset), value, offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
  public static void floatArrayWrite(float[] ref, int index, float value) {
    if (NEEDS_FLOAT_GC_WRITE_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_FLOAT);
      Selected.Mutator.get().floatWrite(array, array.toAddress().plus(offset), value, offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
  public static float floatFieldRead(Object ref, Offset offset, int locationMetadata) {
    if (NEEDS_FLOAT_GC_READ_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      return Selected.Mutator.get().floatRead(src, src.toAddress().plus(offset), offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return 0;
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
  public static float floatArrayRead(float[] ref, int index) {
    if (NEEDS_FLOAT_GC_READ_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_FLOAT);
      return Selected.Mutator.get().floatRead(array, array.toAddress().plus(offset), offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return 0;
  }

  /**
   * Barrier for a bulk copy of floats (i.e. in an array copy).
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting source index
   * @param len The number of array elements to be copied
   */
  @Inline
  public static void floatBulkCopy(float[] src, Offset srcOffset, float[] dst, Offset dstOffset, int bytes) {
    if (VM.VerifyAssertions) VM._assert(FLOAT_BULK_COPY_SUPPORTED);

    if (!Selected.Mutator.get().floatBulkCopy(ObjectReference.fromObject(src), srcOffset, ObjectReference.fromObject(dst), dstOffset, bytes)) {
      Memory.arraycopy32Bit(Magic.objectAsAddress(dst).plus(dstOffset), Magic.objectAsAddress(src).plus(srcOffset), bytes);
    }
  }


  /** True if the garbage collector requires write barriers on double putfield, arraystore or modifycheck */
  private static final boolean NEEDS_DOUBLE_GC_WRITE_BARRIER     = Selected.Constraints.get().needsDoubleWriteBarrier();
  /** True if the VM requires write barriers on double putfield */
  public static final boolean  NEEDS_DOUBLE_PUTFIELD_BARRIER     = NEEDS_DOUBLE_GC_WRITE_BARRIER;
  /** True if the VM requires write barriers on double arraystore */
  public static final boolean  NEEDS_DOUBLE_ASTORE_BARRIER       = NEEDS_DOUBLE_GC_WRITE_BARRIER;
  /** True if the garbage collector requires read barriers on double getfield or arrayload */
  private static final boolean NEEDS_DOUBLE_GC_READ_BARRIER      = Selected.Constraints.get().needsDoubleReadBarrier();
  /** True if the VM requires read barriers on double getfield */
  public static final boolean  NEEDS_DOUBLE_GETFIELD_BARRIER     = NEEDS_DOUBLE_GC_READ_BARRIER;
  /** True if the VM requires read barriers on double arrayload */
  public static final boolean  NEEDS_DOUBLE_ALOAD_BARRIER        = NEEDS_DOUBLE_GC_READ_BARRIER;
  /** True if the garbage collector supports the bulk copy operation */
  public static final boolean DOUBLE_BULK_COPY_SUPPORTED         = !(NEEDS_DOUBLE_ASTORE_BARRIER || NEEDS_DOUBLE_ALOAD_BARRIER) || Selected.Constraints.get().doubleBulkCopySupported();

  /**
   * Barrier for writes of doubles into fields of instances (ie putfield).
   *
   * @param ref the object which is the subject of the putfield
   * @param value the new value for the field
   * @param offset the offset of the field to be modified
   * @param locationMetadata an int that encodes the source location being modified
   */
  @Inline
  @Entrypoint
  public static void doubleFieldWrite(Object ref, double value, Offset offset, int locationMetadata) {
    if (NEEDS_DOUBLE_GC_WRITE_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      Selected.Mutator.get().doubleWrite(src, src.toAddress().plus(offset), value, offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
  public static void doubleArrayWrite(double[] ref, int index, double value) {
    if (NEEDS_DOUBLE_GC_WRITE_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_DOUBLE);
      Selected.Mutator.get().doubleWrite(array, array.toAddress().plus(offset), value, offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
  public static double doubleFieldRead(Object ref, Offset offset, int locationMetadata) {
    if (NEEDS_DOUBLE_GC_READ_BARRIER) {
      ObjectReference src = ObjectReference.fromObject(ref);
      return Selected.Mutator.get().doubleRead(src, src.toAddress().plus(offset), offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return 0;
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
  public static double doubleArrayRead(double[] ref, int index) {
    if (NEEDS_DOUBLE_GC_READ_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_DOUBLE);
      return Selected.Mutator.get().doubleRead(array, array.toAddress().plus(offset), offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return 0;
  }

  /**
   * Barrier for a bulk copy of doubles (i.e. in an array copy).
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   */
  @Inline
  public static void doubleBulkCopy(double[] src, Offset srcOffset, double[] dst, Offset dstOffset, int bytes) {
    if (VM.VerifyAssertions) VM._assert(DOUBLE_BULK_COPY_SUPPORTED);

    if (!Selected.Mutator.get().doubleBulkCopy(ObjectReference.fromObject(src), srcOffset, ObjectReference.fromObject(dst), dstOffset, bytes)) {
      Memory.arraycopy64Bit(Magic.objectAsAddress(dst).plus(dstOffset), Magic.objectAsAddress(src).plus(srcOffset), bytes);
    }
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
  /** True if the garbage collector supports the bulk copy operation */
  public static final boolean OBJECT_BULK_COPY_SUPPORTED         = !(NEEDS_OBJECT_ASTORE_BARRIER || NEEDS_OBJECT_ALOAD_BARRIER) || Selected.Constraints.get().objectReferenceBulkCopySupported();

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
      Selected.Mutator.get().objectReferenceWrite(src, src.toAddress().plus(offset), ObjectReference.fromObject(value), offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
  public static void objectArrayWrite(Object[] ref, int index, Object value) {
    if (NEEDS_OBJECT_GC_WRITE_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_ADDRESS);
      Selected.Mutator.get().objectReferenceWrite(array, array.toAddress().plus(offset), ObjectReference.fromObject(value), offset.toWord(), Word.zero(), ARRAY_ELEMENT);
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
      return Selected.Mutator.get().objectReferenceRead(src, src.toAddress().plus(offset), offset.toWord(), Word.fromIntZeroExtend(locationMetadata), INSTANCE_FIELD).toObject();
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
  public static Object objectArrayRead(Object[] ref, int index) {
    if (NEEDS_OBJECT_GC_READ_BARRIER) {
      ObjectReference array = ObjectReference.fromObject(ref);
      Offset offset = Offset.fromIntZeroExtend(index << MemoryManagerConstants.LOG_BYTES_IN_ADDRESS);
      return Selected.Mutator.get().objectReferenceRead(array, array.toAddress().plus(offset), offset.toWord(), Word.zero(), ARRAY_ELEMENT).toObject();
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return null;
  }

  /**
   * Barrier for a bulk copy of objects (i.e. in an array copy).
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   */
  @Inline
  public static void objectBulkCopy(Object[] src, Offset srcOffset, Object[] dst, Offset dstOffset, int bytes) {
    if (VM.VerifyAssertions) VM._assert(OBJECT_BULK_COPY_SUPPORTED);

    if (!Selected.Mutator.get().objectReferenceBulkCopy(ObjectReference.fromObject(src), srcOffset, ObjectReference.fromObject(dst), dstOffset, bytes)) {
      Memory.alignedWordCopy(Magic.objectAsAddress(dst).plus(dstOffset), Magic.objectAsAddress(src).plus(srcOffset), bytes);
    }
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
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
    } else if (VM.VerifyAssertions)
      VM._assert(false);
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
    } else if (VM.VerifyAssertions)
      VM._assert(false);
    return false;
  }
}
