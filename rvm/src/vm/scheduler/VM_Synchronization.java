/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Class to provide synchronization methods where java language 
 * synchronization is insufficient and VM_Magic.prepare and VM_Magic.attempt 
 * are at too low a level
 *
 * Do not add a method to this class without there is a compelling performance
 * or correctness need.
 *
 * 07/25/2000 Bowen Alpern and Tony Cocchi (initially stolen from VM_MagicMacros by Mauricio J. Serrano)
 *
 * @author Bowen Alpern
 * @author Anthony Cocchi
 */
class VM_Synchronization implements VM_Uninterruptible {

   static final boolean testAndSet(Object base, int offset, int newValue) {
      VM_Magic.pragmaInline();
      int oldValue;
      do {
         oldValue = VM_Magic.prepare(base, offset);
         if (oldValue != 0) return false;
      } while (!VM_Magic.attempt(base, offset, oldValue, newValue));
      return true;
   }

  // used to mark boot image objects during a parallel scan of objects during GC
  //
  static final boolean testAndMark (Object base, int offset, int value)
  {
    VM_Magic.pragmaInline();
    int oldValue;
    do 
      {
	oldValue = VM_Magic.prepare(base, offset);              // get old value
	int markBit  = oldValue & VM.OBJECT_GC_MARK_MASK;  // extract mark bit
	if ( markBit == value) return false;               // if object marked return
      }
    while (! VM_Magic.attempt(base, offset, oldValue, oldValue ^ VM.OBJECT_GC_MARK_MASK)); // try to mark
    return true;                                           // mark successful
  }


   static final int fetchAndStore(Object base, int offset, int newValue) {
      VM_Magic.pragmaInline();
      int oldValue;
      do {
         oldValue = VM_Magic.prepare(base, offset);
      } while (!VM_Magic.attempt(base, offset, oldValue, newValue));
      return oldValue;
   }


   static final int fetchAndAdd(Object base, int offset, int increment) {
      VM_Magic.pragmaInline();
      int oldValue;
      do {
         oldValue = VM_Magic.prepare(base, offset);
      } while (!VM_Magic.attempt(base, offset, oldValue, oldValue+increment));
      return oldValue;
   }

   static final int fetchAndDecrement(Object base, int offset, int decrement) {
      VM_Magic.pragmaInline();
      int oldValue;
      do {
         oldValue = VM_Magic.prepare(base, offset);
      } while (!VM_Magic.attempt(base, offset, oldValue, oldValue-decrement));
      return oldValue;
   }

   static final int fetchAndAddWithBound(Object base, int offset, int increment, int bound) {
      VM_Magic.pragmaInline();
      int oldValue, newValue;
      do {
         oldValue = VM_Magic.prepare(base, offset);
         newValue = oldValue +increment;
         if (newValue > bound) return  -1;
      } while (!VM_Magic.attempt(base, offset, oldValue, newValue));
      return oldValue;
   }

  // used to mark small heap objects during a parallel scan of objects during GC
  //
  static final int fetchAndMarkBusy (Object base, int offset) {
    return fetchAndMarkBusy(base, offset, VM_Collector.MARK_VALUE);
  } 

  // used to mark small heap objects during a parallel scan of objects during GC
  //
  static final int fetchAndMarkBusy (Object base, int offset, int value)
  {
    VM_Magic.pragmaInline();
    int oldValue;
    do 
      {
	oldValue = VM_Magic.prepare(base, offset);              // get old value
	int markBit  = oldValue & VM.OBJECT_GC_MARK_MASK;  // extract mark bit
	if ( markBit == value) return oldValue;            // return forward ptr or busy pattern
      }
    while (! VM_Magic.attempt(base, offset, oldValue, VM_Collector.BEING_FORWARDED_PATTERN)); // try to mark
    return oldValue;                                       // return status word
  }

}
