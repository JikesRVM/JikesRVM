/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author David Bacon
 */
public final class VM_CycleBuffer
    extends VM_RCGC
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    // MEMBER DATA

    private final int objects[];

    private int entries;

    private VM_CycleBuffer next;
    private VM_CycleBuffer prev;

    static private VM_CycleBuffer last;

    // CONSTANTS

    private static final int BUFFERSIZE = (GC_BLOCKSIZE - ARRAY_HEADER_SIZE) >> 2;
    
    private static final boolean FREECYCLES  = true;

    private static VM_CycleChildVisitor cycleChildVisitor = new VM_CycleChildVisitor();
    private static VM_VisualizeCycleChildVisitor visualizeCycleChildVisitor = new VM_VisualizeCycleChildVisitor();

    private static final boolean TRACE       = false;
    private static final boolean TRACEDETAIL = false;
    private static final boolean TRACEADDS   = false;
    private static final boolean STATS       = false;
    public  static final boolean TRACE_CYCLE_VALIDATION = false;

    private static final boolean CB_COUNT_EVENTS = true;
    static final boolean TIME_CYCLE_COLLECTION = true;

    static int cyclesFound;			    // number of potential garbage cycles found
    static int cyclesProcessed;                     // number of cycles processed 
    static int cyclesModified;	                    // number of cycles invalidated due to modification
    static int cyclesFreed;              // number of cycles that are independent and therefore garbage

    static long cycleFreeTime;                      // time spend freeing cycles

    static int whiteObjects;			    // number of objects in those cycles
    static int whiteFreed;			    // number of objects in cycles freed
    static int totalFreed;			    // total objects freed due to cycles
    static int noCycleFreed;			    // not a cycle, but was garbage by the time we checked it
    static int noCyclePurple;			    // not a cycle, painted purple so reconsider later
    static int noCycleIgnore;			    // not a cycle, nothing to do - ignore it

    // METHODS

    public VM_CycleBuffer() {
	objects = new int[BUFFERSIZE];
	entries = 0;
	next = null;
	last = this;
    }

    public final void add (int object) {
	last.objects[last.entries++] = object;

	if (VM.VerifyAssertions && object != 0) 
	    VM.assert(isBuffered(object) && color(object) == WHITE && VM_Allocator.isPossibleRef(object));
	if (CB_COUNT_EVENTS) { if (object == 0) cyclesFound++; else whiteObjects++; }

	if (TRACEADDS) {
	    if (object == 0) VM.sysWrite("================\n");
	    else {
		dumpRefcountInfo("  Cycle buffering ", object);
		dumpRefs(object);
	    }
	}

	if (last.entries == BUFFERSIZE) 
	    expand();
    }

    private final void expand () {
	if (TRACE) VM.sysWrite("|||| ALLOCATING CycleBuffer\n");
	VM_CycleBuffer c = new VM_CycleBuffer();
	last.next = c;
	c.prev = last;
	last = c;
    }


    public final void addDelimiter() {
	if (last.entries == 0 || last.objects[last.entries-1] != 0) {
	    add(0);
	}
    }

    public final void freeBuffer() {
	for (VM_CycleBuffer b = this, next = null; b != null; b = next) {
	    next = b.next;
	    b.next = null;
	    b.prev = null;
	}
    }

    public final void validateCycles() {
	VM_CycleBuffer cstart = this;
	int istart = 0;

	if (TRACE) println("\n\n********************************************* VALIDATING CYCLES");

	// Four pass algorithm applied to each cycle individually:
	//  Pass 1: Set color=ORANGE and CRC = RC for each object in cycle
	//  Pass 2: Decrement CRC for each ORANGE object pointed to by each object in cycle
	//  Pass 3: Recolor each object WHITE

	while (cstart != null) { // iterate over all cycles
	    if (TRACE_CYCLE_VALIDATION) VM.sysWrite("=====\n");
	    
	    VM_CycleBuffer c = cstart;
	    int i = istart;

	    for (int pass = 1; pass <= 3; pass++) { // perform 3 passes
		for (; c != null; i++) { // iterate over elements of a cycle
		    if (i >= c.entries) { i = -1; c = c.next; continue; }
		    int object = c.objects[i];
		    if (object == 0) break;

		    switch (pass) {
		    case 1:
			cloneCount(object);
			setColor(object, ORANGE); // indicate object is part of current cycle
			if (TRACE_CYCLE_VALIDATION) dumpRefcountInfo("CLONED COUNT ", object);
			break;

		    case 2:
			if (TRACE_CYCLE_VALIDATION) dumpRefcountInfo("DEC'ING COUNTS ", object);
			cycleChildVisitor.decrementChildCounts(object);
			break;

		    case 3:
			if (TRACE_CYCLE_VALIDATION) dumpRefcountInfo("RECOLORING WHITE ", object);
			setColor(object, WHITE);
			break;
		    }
		}

		if (pass != 3) {
		    c = cstart;
		    i = istart;
		}
	    }

	    cstart = c;
	    istart = i + 1;
	}
	
	if (TRACE) println("\n***************************** VALIDATION COMPLETE");

    }


    public final void freeCycles() {
	if (TRACE) println("\n***************************** FREE CYCLES START");
	if (! FREECYCLES)
	    return;

	long tStart = 0;
	if (TIME_CYCLE_COLLECTION) tStart = VM_Magic.getTimeBase();

	freeWhiteCycles();

	if (TIME_CYCLE_COLLECTION) cycleFreeTime += (VM_Magic.getTimeBase() - tStart);
	if (TRACE) println("\n***************************** FREE CYCLES END");
    }

    private static final VM_CyclePointer cp = new VM_CyclePointer();


    private final void freeWhiteCycles () {
	cp.buffer = last;
	cp.index  = last.entries;

	while (cp.buffer != null) { // loop over all cycles backwards
	    if (TRACE && cp.index < 0) println("Index < 0: ", cp.index);

	    boolean garbageCycle = SigmaDeltaTest(cp.buffer, cp.index);

	    if (garbageCycle)
		freeWhiteCycle2(cp);
	    else
		refurbishWhiteCycle(cp);
	}
    }


    private static final boolean SigmaDeltaTest (VM_CycleBuffer c, int i) {
	int externalReferenceCount = 0;
	boolean modified = false;
	int size = 0;

	for (; c != null; i--) { // loop over one cycle backwards
	    if (i < 0) { c = c.prev; if (c != null) i = c.entries; continue; }
	    int object = c.objects[i];
	    if (object == 0) break;

	    size++;

	    if (VM.VerifyAssertions) VM.assert(VM_Allocator.isPossibleRef(object));

	    if (color(object) != WHITE) {
		modified = true;
		if (TRACE) 
		    dumpRefcountInfo("MODIFIED OBJECT ", object);
		break;
	    }
	    else {
		externalReferenceCount += cyclicReferenceCount(object);
	    }
	}

	// Cycle has been scanned; now decide what to do with it.

	boolean garbageCycle = (! modified) && (externalReferenceCount == 0);

	if (VM.VerifyAssertions && VM_RootBuffer.ASYNC && VM_Scheduler.numProcessors == 1) VM.assert(! modified);

	if (CB_COUNT_EVENTS && size > 0) {
	    cyclesProcessed++;
	    if (modified) cyclesModified++;
	    if (garbageCycle) cyclesFreed++;
	}

	return garbageCycle;
    }



    private static final VM_CyclePointer xp = new VM_CyclePointer();

    private static final void freeWhiteCycle (VM_CyclePointer p) {
	VM_CycleBuffer cc = p.buffer;
	int ci = p.index;

	for (; cc != null; ci--) { // loop over cycle backwards
	    if (ci < 0) { cc = cc.prev; if (cc != null) ci = cc.entries; continue; }
	    int object = cc.objects[ci];
	    if (object == 0) { ci--; break; }

	    // HACK // if (VM.VerifyAssertions) VM.assert(isBuffered(object));

	    if (! isBuffered(object) || color(object) == GREEN || color(object) == RED) {
		dumpRefcountInfo("FREEING CYCLE ELEMENT NOT MARKED BUFFERED ", object);
		xp.buffer = cc;
		xp.index  = ci;
		findCycleStart(xp);
		visualizeCycle(xp.buffer, xp.index);
	    }

	    if (VM.VerifyAssertions) VM.assert(color(object) != GREEN && color(object) != RED);

	    releaseWhite(object);
	}

	p.buffer = cc;
	p.index = ci;
    }


    private static final void freeWhiteCycle2 (VM_CyclePointer p) {
	recolorNodes(p.buffer, p.index);
	decrementNodes(p.buffer, p.index);
	freeNodes(p);
    }

    private static final void recolorNodes (VM_CycleBuffer buffer, int index) {
	for (; buffer != null; index--) { // loop over cycle backwards
	    if (index < 0) { buffer = buffer.prev; if (buffer != null) index = buffer.entries; continue; }
	    int object = buffer.objects[index];
	    if (object == 0) { index--; break; }

	    setColor(object, BLUE);
	}
    }

    private static final void decrementNodes (VM_CycleBuffer buffer, int index) {
	for (; buffer != null; index--) { // loop over cycle backwards
	    if (index < 0) { buffer = buffer.prev; if (buffer != null) index = buffer.entries; continue; }
	    int object = buffer.objects[index];
	    if (object == 0) { index--; break; }

	    cyclicRemoveInternalPointers2(object);
	}
    }

    private static final void freeNodes (VM_CyclePointer p) {
	for (; p.buffer != null; p.index--) { // loop over cycle backwards
	    if (p.index < 0) { p.buffer = p.buffer.prev; if (p.buffer != null) p.index = p.buffer.entries; continue;}
	    int object = p.buffer.objects[p.index];
	    if (object == 0) { p.index--; break; }

	    VM_RootBuffer.release(object);
	}
    }

    private static final void refurbishWhiteCycle (VM_CyclePointer p) {
	VM_CycleBuffer cc = p.buffer;
	int ci = p.index;

	int firstObject = 0;

	for (; cc != null; ci--) { // loop over cycle again
	    if (ci < 0) { cc = cc.prev; if (cc != null) ci = cc.entries; continue; }
	    int object = cc.objects[ci];
	    if (object == 0) { ci--; break; }

	    // HACK // if (VM.VerifyAssertions) VM.assert(isBuffered(object));

	    if (! isBuffered(object) || color(object) == GREEN || color(object) == RED) {
		dumpRefcountInfo("REFURBISHED CYCLE ELEMENT NOT BUFFERED ", object);
		xp.buffer = cc;
		xp.index  = ci;
		findCycleStart(xp);
		visualizeCycle(xp.buffer, xp.index);
	    }

	    if (VM.VerifyAssertions) VM.assert(color(object) != GREEN && color(object) != RED);

	    refurbish(object);

	    firstObject = object; // first object is the last one we see since we scan in reverse
	}

	// Should only do this if externalReferenceCount != 0
	if (color(firstObject) != PURPLE) {
	    if (VM.VerifyAssertions) VM.assert (! isBuffered(firstObject));
	    VM_RootBuffer.buffer.add(firstObject);
	}

	p.buffer = cc;
	p.index = ci;
    }

    private static final void refurbish (int object) {
	if (TRACEDETAIL) 
	    VM_RootBuffer.dumpRefcountInfo("Refurbishing ", object);

	if (isZeroReferenceCount(object)) {
	    if (CB_COUNT_EVENTS) noCycleFreed++;
	    VM_RootBuffer.release(object);
	}
	else if (color(object) == PURPLE) {
	    if (CB_COUNT_EVENTS) noCyclePurple++;
	    clearBufferedFlag(object);
	    VM_RootBuffer.buffer.add(object);
	}
	else {
	    if (CB_COUNT_EVENTS) noCycleIgnore++;
	    setColor(object, BLACK);
	    // free big cyclic count
	    clearBufferedFlag(object);
	}
    }


    private static final void releaseWhite(int object) {
	if (CB_COUNT_EVENTS) { whiteFreed++; totalFreed++; }
	if (TRACEDETAIL)
	    VM_RootBuffer.dumpRefcountInfo("Releasing ", object);

	if (color(object) != ORANGE) // if not already visited...
	    cyclicRemoveInternalPointers(object);

	if (isZeroReferenceCount(object)) {
	    if (TRACEDETAIL) VM_RootBuffer.dumpRefcountInfo("Freeing ", object);
	    initializeReferenceCount(object);
	    VM_Allocator.freeObject(object);
	}
	else {
	    if (TRACEDETAIL) VM_RootBuffer.dumpRefcountInfo("Unbuffering ", object);
	    clearBufferedFlag(object);
	}
    }


    private static void cyclicRemoveInternalPointers(int object) {

	if (color(object) == ORANGE) // if already decremented, don't do it again
	    return;

	if (color(object) == WHITE)
	    setColor(object, ORANGE); // don't decrement internal pointers of cycle objects more than once

	VM_Type type = VM_Magic.getObjectType(VM_Magic.addressAsObject(object));

	if (type.isClassType()) { 
	    int[] referenceOffsets = type.asClass().getReferenceOffsets();
	    for (int i = 0, n = referenceOffsets.length; i < n; ++i) {
		int objectRef = VM_Magic.getMemoryWord(object + referenceOffsets[i]);
		if (objectRef != 0) {
		    if (TRACEDETAIL) VM_RootBuffer.dumpRefcountInfo("Decrementing ", objectRef);
		    cyclicDecrementRC(objectRef);
		}
	    }
	} 
	else if (type.isArrayType()) {
	    if (type.asArray().getElementType().isReferenceType()) { // ignore scalar arrays
		int elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(object));
		for (int i = 0; i < elements; i++) {
		    int objectRef = VM_Magic.getMemoryWord(object + i*4);
		    if (objectRef != 0) {
			if (TRACEDETAIL) VM_RootBuffer.dumpRefcountInfo("Decrementing ", objectRef);
			cyclicDecrementRC(objectRef);
		    }
		}
	    }
	} 

	if (VM_RCGC.referenceCountTIBs)
	    VM_Allocator.decrementRC(VM_Magic.getMemoryWord(object + OBJECT_TIB_OFFSET)); // or cyclicDecrementRC?
    }


    private static void cyclicDecrementRC (int object) {
	if (VM.VerifyAssertions) VM.assert(object != 0);
    
	if (VM.VerifyAssertions && object == VM_Allocator.refToWatch)
	    VM.sysWrite("#### Cyclic decrementing RC of watched object\n");

	int color = color(object);

	if (color == RED)
	    return;		// don't change reference counts of boot image objects

	boolean isZero = decReferenceCount(object);

	if (color == WHITE && ! isZeroCyclicReferenceCount(object)) { 
	    // handle dependent connected components
	    decCyclicReferenceCount(object);
	    return;
	}

	if (VM_Allocator.GC_TRACEALLOCATOR && ! isZero)
	    VM_Allocator.nonZeroDecs++;

	if (isZero) {
	    if (color != ORANGE)
		cyclicRemoveInternalPointers(object);

	    if (! isBuffered(object)) {
		if (TRACEDETAIL) VM_RootBuffer.dumpRefcountInfo("Freeing (via cyclic decrement) ", object);
		if (CB_COUNT_EVENTS) totalFreed++;
		VM_RootBuffer.release(object);
	    }
	}
	else if (color != ORANGE && color != WHITE) { 
	    // If object is not part of a cycle, we have decremented its reference count to non-zero, so add
	    // it to the buffer of potential roots and color it purple.
	    VM_RootBuffer.buffer.add(object);
	}
    }


    //////////////////////////////////////////////////////////////////////

    private static void cyclicRemoveInternalPointers2 (int object) {

	VM_Type type = VM_Magic.getObjectType(VM_Magic.addressAsObject(object));

	if (type.isClassType()) { 
	    int[] referenceOffsets = type.asClass().getReferenceOffsets();
	    for (int i = 0, n = referenceOffsets.length; i < n; ++i) {
		int objectRef = VM_Magic.getMemoryWord(object + referenceOffsets[i]);
		if (objectRef != 0) {
		    if (TRACEDETAIL) VM_RootBuffer.dumpRefcountInfo("Decrementing ", objectRef);
		    cyclicDecrementRC2(objectRef);
		}
	    }
	} 
	else if (type.isArrayType()) {
	    if (type.asArray().getElementType().isReferenceType()) { // ignore scalar arrays
		int elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(object));
		for (int i = 0; i < elements; i++) {
		    int objectRef = VM_Magic.getMemoryWord(object + i*4);
		    if (objectRef != 0) {
			if (TRACEDETAIL) VM_RootBuffer.dumpRefcountInfo("Decrementing ", objectRef);
			cyclicDecrementRC2(objectRef);
		    }
		}
	    }
	} 

	if (VM_RCGC.referenceCountTIBs)
	    VM_Allocator.decrementRC(VM_Magic.getMemoryWord(object + OBJECT_TIB_OFFSET)); // or cyclicDecrementRC?
    }


    private static void cyclicDecrementRC2 (int object) {
	if (VM.VerifyAssertions) VM.assert(object != 0);
    
	int color = color(object);

	if (color == BLUE)
	    return;

	if (color != WHITE) {
	    VM_Allocator.decrementRC(object);
	    return;
	}

	if (VM.VerifyAssertions) VM.assert(! isZeroReferenceCount(object));

	decReferenceCount(object);
	if (! isZeroCyclicReferenceCount(object)) { 
	    decCyclicReferenceCount(object);
	}
    }

    //////////////////////////////////////////////////////////////////////


    private final void dumpRefs(int object) {
	if (object == 0) {
	    VM.sysWrite("    NULL\n");
	    return;
	}

	VM_Type type = VM_Magic.getObjectType(VM_Magic.addressAsObject(object));

	if (type.isClassType()) { 
	    int[] referenceOffsets = type.asClass().getReferenceOffsets();
	    for (int i = 0, n = referenceOffsets.length; i < n; ++i) {
		int objectRef = VM_Magic.getMemoryWord(object + referenceOffsets[i]);
		if (objectRef != 0) {
		    VM_RootBuffer.dumpRefcountInfo("    Contains ref ", objectRef);
		}
	    }
	} 
	else if (type.isArrayType()) {
	    if (type.asArray().getElementType().isReferenceType()) { // ignore scalar arrays
		int elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(object));
		for (int i = 0; i < elements; i++) {
		    int objectRef = VM_Magic.getMemoryWord(object + i*4);
		    if (objectRef != 0) {
			VM_RootBuffer.dumpRefcountInfo("    Contains ref ", objectRef);
		    }
		}
	    } 
	}
    }


    static final void printStatistics () {
	if (! CB_COUNT_EVENTS) return;

	println();
	print("Cycles found:     ", cyclesFound);  println();
	print("Cycles processed: ", cyclesProcessed);  percentage(cyclesProcessed, cyclesFound, "cycles found");
	print("Cycles modified:  ", cyclesModified);  percentage(cyclesModified, cyclesProcessed, "cycles processed");
	print("Cycles freed:     ", cyclesFreed);  percentage(cyclesFreed, cyclesProcessed, "cycles processed");

	println("Cycle objects: ", whiteObjects);
	print("Cycle objects freed: ", whiteFreed);  percentage(whiteFreed, whiteObjects, "cycle objects");
	println("Cycle total freed: ", totalFreed);
	int whiteNotCycle = noCycleFreed + noCyclePurple + noCycleIgnore;
	print("False cycle objects: ", whiteNotCycle);  percentage(whiteNotCycle, whiteObjects, "cycle objects");
	print("  False freed: ", noCycleFreed);   percentage(noCycleFreed,  whiteNotCycle, "false cycle objects");
	print("  False buffered: ", noCyclePurple);  percentage(noCyclePurple, whiteNotCycle, "false cycle objects");
	print("  False ignored: ", noCycleIgnore);  percentage(noCycleIgnore, whiteNotCycle, "false cycle objects");
	print("Time to free cycles: ");  print((int) (cycleFreeTime/VM_Allocator.TicksPerMicrosecond)); println(" usec");
	println();
    }



    private static final void findCycleStart (VM_CyclePointer cp) {
	for (; cp.buffer != null; cp.index--) { // loop over cycle backwards
	    if (cp.index < 0) { cp.buffer = cp.buffer.prev; if (cp.buffer != null) cp.index = cp.buffer.entries; continue; }
	    int object = cp.buffer.objects[cp.index];
	    if (object == 0) break;
	}

	cp.index++;		// advance past 0 delimiter
	if (cp.buffer.entries >= cp.index) { // if past end of buffer, advance to next one for first real entry
	    cp.buffer = cp.buffer.next;
	    cp.index = 0;
	}
    }

    private static final void visualizeCycle(VM_CycleBuffer c, int i) {
	boolean first = true;
	for (; c != null; i++) { 
	    if (i >= c.entries) { i = -1; c = c.next; continue; }
	    int object = c.objects[i];

	    if (object == 0)
		break;

	    if (first) {
		VM_RootBuffer.writeGraphStart(object);
		first = false;
	    }
	    VM_RootBuffer.writeGraphNode(object);
	    visualizeCycleChildVisitor.writeGraphEdges(object);
	}

	VM_RootBuffer.writeGraphEnd(0);
    }

}


class VM_CyclePointer {
    public VM_CycleBuffer buffer;
    public int index;
}


class VM_CycleChildVisitor
    extends VM_ChildVisitor
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    public final void decrementChildCounts(int object) {
	if (VM.VerifyAssertions) VM.assert(VM_RootBuffer.ASYNC);
	visitChildren(object);
    }

    protected final boolean visit(int object) {
	if (VM_CycleBuffer.TRACE_CYCLE_VALIDATION) dumpRefcountInfo("DEC'ING OBJECT ", object);

	if (color(object) == ORANGE) {
	    if (! isZeroCyclicReferenceCount(object))
		decCyclicReferenceCount(object);
	    else
		dumpRefcountInfo("--**-- PROBLEM DECREMENT??? ", object);
	}
	return true;
    }
}


class VM_VisualizeCycleChildVisitor
    extends VM_ChildVisitor
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    public final void writeGraphEdges(int object) {
	visitChildrenWithEdges(object);
    }

    protected final boolean visit(int fromObject, int object) {
	VM_RootBuffer.writeGraphEdge(fromObject, object);
	return true;
    }
}


