/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * This class contains the Java code that the opt compiler will
 * inline at every ref_astore and putfield of reference type to
 * implement write barriers.
 *
 * NOTE: If the code defined here contains any aastores or putfields
 *       of references, they will NOT have write barrier code inserted (infinite loop).
 *
 * @author Dave Grove
 * 
 * @see OPT_SpecialInline (logic to inline this code to implement write barrier)
 * @see VM_Barrier (baseline compiler implementation of write barrier)
 *
 */

class VM_OptWriteBarrier implements VM_Constants {

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
   * The current implementation of write barriers in Jalapeno
   * generates the same sequence in all cases and only uses the ref parameter.
   * So, we share an internal implementation method...
   */
  private static void internalWriteBarrier(Object ref) {
    // force internal method to be inlined when compiled by Opt
    VM_Magic.pragmaInline();

    int statusWord = VM_Magic.getIntAtOffset(ref, OBJECT_STATUS_OFFSET);

    // Check to see if we need to put this reference in the write buffer
    if ((statusWord & OBJECT_BARRIER_MASK) != 0) {
      // We do.

      // (1) mark reference as being in the write buffer 
      statusWord = statusWord ^ OBJECT_BARRIER_MASK;
      VM_Magic.setIntAtOffset(ref, OBJECT_STATUS_OFFSET, statusWord);
      
      // (2) add reference to write buffer
      VM_Processor p = VM_Processor.getCurrentProcessor();
      int wbTop = p.modifiedOldObjectsTop;
      int wbMax = p.modifiedOldObjectsMax;
      wbTop += 4;
      VM_Magic.setMemoryWord(wbTop, VM_Magic.objectAsAddress(ref));
      p.modifiedOldObjectsTop = wbTop;

      // (3) grow write buffer (if necessary)
      if (wbMax == wbTop) {
	VM_WriteBuffer.growWriteBuffer();
      }
    }
  }

}
