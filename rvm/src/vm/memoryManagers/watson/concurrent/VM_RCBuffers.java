/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Buffer used by reference counting collector (currently under development).
 * incDecBuffer stores increments and decrements that occur during reference
 * assignments.
 * <p>
 * NOT INCLUDED IN ANY CURRENT BUILDS
 *
 * @author David Bacon
 */
public class VM_RCBuffers
    implements VM_Constants, VM_Uninterruptible
{

// Should rename from IncDecBuffer/RCBuffers to MutationBuffer

    static final int MAX_INCDECBUFFER_COUNT = 2;    // add explaination here...

    static int bufCount = 0;               // number of buffers allocated since last GC for inc/dec refs

    static final int INCDEC_BUFFER_SIZE = 8*1024*4; // Inc/Dec buffer size in bytes
    static final int BUFCOUNT_MAX       = 8; // Smaller number of buffers for MP's

    static final int INCDEC_BUFFER_ENTRY_SIZE   = 8;
    static final int INCDEC_BUFFER_NEXT_OFFSET  = INCDEC_BUFFER_SIZE - 4;
    static final int INCDEC_BUFFER_LAST_OFFSET  = INCDEC_BUFFER_SIZE - 12;
    static final int INCDEC_BUFFER_FIRST_OFFSET = -4;

    static final int DECREMENT_FLAG = 1;
    static final int OBJECT_MASK    = ~ DECREMENT_FLAG;

    static final boolean referenceCountTIBs = false; // NOTE: shadow of VM_RCGC.referenceCountTIBs; keep in sync

    static final boolean COUNT_BUFFERS = true;
    static int buffersUsed;
    static int maxBuffersUsed;

    // static int incDecDepth;

    static void	processIncDecBuffer()
    {
	VM_Processor p = VM_Processor.getCurrentProcessor();
       	VM_RCBuffers.growIncDecBuffer(p);

	VM_Thread myThread = VM_Thread.getCurrentThread();

	if (myThread.isIdleThread) {
	    if (VM_Scheduler.numProcessors != 1) {
		for (int i = 0; i < 100 && VM_Allocator.gcInProgress; i++) { // dfb: hack for MP's
		    VM.sysVirtualProcessorYield();
		}
	    }
	}

	// NOTE: Should really have some negative feedback here!

	final int cpus = VM_Scheduler.numProcessors;
	final int max  = cpus == 1 ? BUFCOUNT_MAX * 4 : BUFCOUNT_MAX ;

	if ((++bufCount / cpus) > max) {
	    bufCount = 0;
	    if (!VM_Allocator.gcInProgress && 
		VM_Scheduler.allProcessorsInitialized) {
		VM_Allocator.gc_collect_now = true;
	    }
	}
    }  // process

    static void	allocateIncDecBuffer(VM_Processor p)
    {
	// NOTE: Should be possible to allocate mutation buffers via regular allocation, by allocating two per
	//   processor at startup that are never freed, and the rest dynamically as needed: if they block, will
	//   force a synchronous collection anyway.

	p.incDecBuffer = VM_Allocator.mallocHeap.allocateZeroedMemory(INCDEC_BUFFER_SIZE);
	VM_Magic.setMemoryWord(p.incDecBuffer.add(INCDEC_BUFFER_NEXT_OFFSET), 0);
	p.incDecBufferTop = p.incDecBuffer.add(INCDEC_BUFFER_FIRST_OFFSET);
	p.incDecBufferMax = p.incDecBuffer.add(INCDEC_BUFFER_LAST_OFFSET);
	if (COUNT_BUFFERS) {
	    buffersUsed++;
	    if (buffersUsed > maxBuffersUsed) maxBuffersUsed = buffersUsed;
	}
    }

    static void	growIncDecBuffer(VM_Processor p)
    {
	VM_Address newBufAddr;

	newBufAddr = VM_Allocator.mallocHeap.allocateZeroedMemory(INCDEC_BUFFER_SIZE);
	if (newBufAddr.isZero()) {
	    if (!VM_Thread.getCurrentThread().isIdleThread) {
		VM_Scheduler.gcWaitMutex.lock();
		VM_Thread.getCurrentThread().yield(VM_Scheduler.gcWaitQueue, VM_Scheduler.gcWaitMutex);
	    }

	    newBufAddr = VM_Allocator.mallocHeap.allocateZeroedMemory(INCDEC_BUFFER_SIZE);
	    if (newBufAddr.isZero()) {
		VM_Scheduler.traceback("VM_RCBuffer::growIncDecBuffer");
		VM.sysExit(1800);
	    }
	}


	// VM_Scheduler.trace("growIncDecBuffer" , "incDecDepth = ", ++incDecDepth);

	// if extra word left in buffer, set it to zero
	if (p.incDecBufferTop.EQ(p.incDecBufferMax))
	    VM_Magic.setMemoryWord(p.incDecBufferTop.add(4), 0);
	// set last word in current buffer to address of next buffer
	VM_Magic.setMemoryAddress(p.incDecBufferMax.add(INCDEC_BUFFER_ENTRY_SIZE), newBufAddr);
	// set fptr in new buffer to null, to identify it as last
	VM_Magic.setMemoryWord(newBufAddr.add(INCDEC_BUFFER_NEXT_OFFSET), 0);
	// set incDecBuffer pointers in processor object for stores into new buffer
	p.incDecBufferTop = newBufAddr.add(INCDEC_BUFFER_FIRST_OFFSET);
	p.incDecBufferMax = newBufAddr.add(INCDEC_BUFFER_LAST_OFFSET);
	if (COUNT_BUFFERS) {
	    buffersUsed++;
	    if (buffersUsed > maxBuffersUsed) maxBuffersUsed = buffersUsed;
	}
    }


    // add a single entry to the mutation buffer
    static void addEntry(VM_Address entry, VM_Processor p) 
    {
	p.incDecBufferTop = p.incDecBufferTop.add(4);
	VM_Magic.setMemoryAddress(p.incDecBufferTop, entry); 

	// Check for overflow and expand if necessary
	
	if (p.incDecBufferTop.GE( p.incDecBufferMax) )
	    growIncDecBuffer(p);
    }


    // adds a reference as a decrement in incDecBuffer
    static void	addDecrement(VM_Address object, VM_Processor p)
    {
	if (!object.isZero())
	    addEntry(VM_Address.fromInt(object.toInt() | DECREMENT_FLAG), p);
    }

    // adds a reference as an increment in incDecBuffer
    static void	addIncrement(VM_Address object, VM_Processor p)
    {
	if (!object.isZero())
	    addEntry(object, p);
    }


    // When creating a new object, adds an inc for the TIB pointed to by the object header,
    // and a dec for the newly created object.  We would like to avoid reference counting 
    // all of these TIB updates, so there is an option to leave them out.  As long as classes
    // are never unloaded, this should be OK.  Issue will have to be revisited later.
    //
    static void addTibIncAndObjectDec(VM_Address tibobject, VM_Address newobject, VM_Processor p)
    {
	if (VM_RCBuffers.referenceCountTIBs)
	    addIncrementAndDecrement(tibobject, newobject, p);
	else
	    addDecrement(newobject, p);
    }

    // adds a <increment,decrement> pair of references to processors buffer
    //   note: buffer is designed so that it is always possible to write to entries with only one overflow check.
    static void	addIncrementAndDecrement(VM_Address incrementRef, VM_Address decrementRef, VM_Processor p)
    {
	// Write increment to buffer

	if (!incrementRef.isZero()) {
	    p.incDecBufferTop = p.incDecBufferTop.add(4);
	    VM_Magic.setMemoryAddress(p.incDecBufferTop, incrementRef);  
	}

	// Write decrement to buffer 

	if (!decrementRef.isZero()) {
	    p.incDecBufferTop = p.incDecBufferTop.add(4);
	    VM_Magic.setMemoryWord(p.incDecBufferTop, decrementRef.toInt() | DECREMENT_FLAG); 
	}

	// Check for overflow and expand if necessary
	
	if (p.incDecBufferTop.GE(p.incDecBufferMax)) {
	    growIncDecBuffer(p);
	}
    }


    static void	freeIncDecBuffer(VM_Address addr)
    {
	VM.assert(false);	// not used -- bufs freed incrementally in processMutationBuffer

	for (VM_Address buf = addr, nextbuf = VM_Address.zero(); !buf.isZero(); buf = nextbuf) {

	    nextbuf = VM_Magic.getMemoryAddress(buf.add(INCDEC_BUFFER_NEXT_OFFSET));

	    freeBuffer(buf);

	    // VM_Scheduler.trace("freeIncDecBuffer" , "incDecDepth = ", --incDecDepth);
	}

	// Note: there was an extra free outside the loop that seemed to free the first buffer (@addr) twice.
	//   Removed it, but check if it had some purpose.
    }

    static void freeBuffer (VM_Address buf) {
	VM_Allocator.mallocHeap.free(buf);
	if (COUNT_BUFFERS) buffersUsed--;
    }
}


