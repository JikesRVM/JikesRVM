/*
 * (C) Copyright IBM Corp. 2001
 */
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
// Size must change if the size of a SizeControl changes
/// TODO: remove last_allocated.
static final int Size = 24 + SCALAR_HEADER_SIZE;

int first_block;
int current_block;
int last_allocated;
int ndx;
int next_slot;
int lastBlockToKeep;        // GSC
}
