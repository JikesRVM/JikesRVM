/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * An array of VM_SizeControls - one for each slotsize (see VM_GCConstants.java)
 * is associated with each Virtual Processor. This object supports
 * allocation as well as collection. During allocation, next_slot is the
 * address of the slot to be allocated to the next request satisfied by
 * the current size.  If this is zero, then a "chunk" (see VM_BlockControl)
 * crossing is required. first_block is where scan for free slots
 * starts after a garbage collection.  current_block is the chunk from
 * which allocations of this size are satisfied currently - i.e., it 
 * is the address of the VM_BlockControl for the chunk into which next_slot
 * points.
 *
 * @see VM_Allocator
 * @author Dick Attanasio
 *
 */
class VM_SizeControl implements VM_Constants {
  static final VM_Class TYPE = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("LVM_SizeControl;")).asClass();

  int first_block;
  int current_block;
  /// TODO: remove last_allocated.
  int last_allocated;
  int ndx;
  int next_slot;
  int lastBlockToKeep;        // GSC
}
