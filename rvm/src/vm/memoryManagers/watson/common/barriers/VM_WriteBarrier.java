/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * This class contains the Java code that the opt compiler will
 * inline at every ref_astore and putfield of reference type to
 * implement write barriers.
 *
 * NOTE: If the code defined here contains any aastores or putfields
 *       of references, when the code is inlined by the opt compiler said
 *       stores will NOT have write barrier code inserted (infinite loop).
 *
 * @author Dave Grove
 * 
 * @see OPT_ExpandRuntimeServices (logic to inline this code)
 */
class VM_WriteBarrier implements VM_Constants, VM_Uninterruptible {

  /**
   * This method is inlined to implement the write barrier for aastores
   *
   * @param ref   The base pointer of the array
   * @param index The array index being stored into.  NOTE: This is the "natural" index; a[3] will pass 3.
   * @param value The value being stored
   */
  static void arrayStoreWriteBarrier(Object ref, int index, Object value) {
    internalWriteBarrier(ref);
  }

  /**
   * This method is inlined to implement the write barrier for resolved putfields of references
   *
   * @param ref    The base pointer of the array
   * @param offset The offset being stored into.  NOTE: This is in bytes.
   * @param value  The value being stored
   */
  static void resolvedPutfieldWriteBarrier(Object ref, int offset, Object value) {
    internalWriteBarrier(ref);
  }

  /**
   * This method is inlined to implement the write barrier for unresolved putfields of references
   *
   * @param ref   The base pointer of the array
   * @param fid   The field id that is being stored into.
   * @param value The value being stored
   */
  static void unresolvedPutfieldWriteBarrier(Object ref, int fid, Object value) {
    internalWriteBarrier(ref);
  }

  /**
   * This method is inlined to implement the write barrier for resolved putfields of references
   *
   * @param fieldOffset  The offset of static field ( from JTOC)
   * @param value        The value being stored
   */
  static void resolvedPutStaticWriteBarrier(int fieldOffset, Object value) {
    // currently there is no write barrier for statics, all statics are
    // scanned during each collection - a design decision
  }

  /**
   * This method is inlined to implement the write barrier for unresolved putfields of references
   *
   * @param fieldId  The field id that is being stored into.
   * @param value    The value being stored
   */
  static void unresolvedPutStaticWriteBarrier(int fieldId, Object value) {
    // currently there is no write barrier for statics, all statics are
    // scanned during each collection - a design decision
  }

  /**
   * The current implementation of write barriers in RVM
   * generates the same sequence in all cases and only uses the ref parameter.
   * So, we share an internal implementation method...
   */
  private static void internalWriteBarrier(Object ref) throws VM_PragmaInline {
    // force internal method to be inlined when compiled by Opt
    if (VM_AllocatorHeader.testBarrierBit(ref)) {
      doWriteBarrierInsertion(ref);
    }
  }

  /**
   * Actually do the insertion into the write barrier.
   * Put out of line due to Steve Blackburn et al experience that
   * outlining the uncommon case yields the best performance.
   */
  private static void doWriteBarrierInsertion(Object ref) throws VM_PragmaNoInline {

    // (1) mark reference as being in the write buffer 
    VM_AllocatorHeader.clearBarrierBit(ref);

    // (2) add reference to write buffer
    VM_Processor p = VM_Processor.getCurrentProcessor();
    VM_Address wbTop = p.modifiedOldObjectsTop;
    VM_Address wbMax = p.modifiedOldObjectsMax;
    wbTop = wbTop.add(4);
    VM_Magic.setMemoryAddress(wbTop, VM_Magic.objectAsAddress(ref));
    p.modifiedOldObjectsTop = wbTop;

    // (3) grow write buffer (if necessary)
    if (wbMax == wbTop) {
      VM_WriteBuffer.growWriteBuffer();
    }
  }

  /**
   * This method generates write barrier entries needed as a consequence of
   * an explcit user array copies, which trickle down to VM_Array.
   *
   * @param ref The referring (source) array.
   * @param start The first "natural" index into the array (e.g. for
   * <code>a[1]</code>, index = 1).
   * @param end The last "natural" index into the array
   * @see VM_Array
   */
  public static final void arrayCopyWriteBarrier(Object ref, int start, 
						 int end) {
    internalWriteBarrier(ref);
  }
}
