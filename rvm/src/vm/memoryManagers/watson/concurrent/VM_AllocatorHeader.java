/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Defines header words used by memory manager.not used for 
 *
 * @see VM_ObjectModel
 * 
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove
 */
public final class VM_AllocatorHeader extends VM_CommonAllocatorHeader
  implements VM_Uninterruptible {

  /**
   * Offset of the reference count in the object header
   */
  static final int REFCOUNT_OFFSET  = VM_JavaHeaderConstants.JAVA_HEADER_END - 4;
  
  /**
   * Perform any required initialization of the GC portion of the header.
   * 
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param isScalar are we initializing a scalar (true) or array (false) object?
   */
  public static void initializeHeader(Object ref, Object[] tib, int size, boolean isScalar) {
    // Initialize reference count and enqueue mutation buffer operations
    VM_Processor proc = VM_Processor.getCurrentProcessor();
    VM_Magic.setIntAtOffset(ref, REFCOUNT_OFFSET, 1);
    VM_RCBuffers.addTibIncAndObjectDec(VM_Magic.objectAsAddress(tib), 
				       VM_Magic.objectAsAddress(ref), 
				       proc);

    // Mark acyclic objects green; others are black (0) by default [Note: change green to default?]
    if (VM_Magic.objectAsType(tib[VM_TIBLayoutConstants.TIB_TYPE_INDEX]).acyclic) { 
      VM_RCGC.setColor(VM_Magic.objectAsAddress(ref), VM_RCGC.GREEN);
      if (VM_Allocator.RC_COUNT_EVENTS) VM_Allocator.green++;
    } else {
      if (VM_Allocator.RC_COUNT_EVENTS) VM_Allocator.black++;
    }
  }

  /**
   * Perform any required initialization of the GC portion of the header.
   * 
   * @param bootImage the bootimage being written
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param isScalar are we initializing a scalar (true) or array (false) object?
   */
  public static void initializeHeader(BootImageInterface bootImage, int ref, 
				      Object[] tib, int size, boolean isScalar) {
    bootImage.setFullWord(ref + REFCOUNT_OFFSET, VM_RCGC.BOOTIMAGE_REFCOUNT);
  }

  /**
   * For low level debugging of GC subsystem. 
   * Dump the header word(s) of the given object reference.
   * @param ref the object reference whose header should be dumped 
   */
  public static void dumpHeader(Object ref) {
    VM.sysWrite(" REFCOUNT=");
    VM.sysWriteHex(VM_Magic.getIntAtOffset(ref, REFCOUNT_OFFSET));
  }
}
