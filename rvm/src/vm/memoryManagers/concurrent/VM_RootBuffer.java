/*
 * (C) Copyright IBM Corp. 2001
 */
public final class VM_RootBuffer 
    extends VM_RCGC
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    // MEMBER DATA

    private final int roots[];

    // Number of entries in roots array
    private int entries = 0;

    // If this buffer fills up, link to overflow.
    private VM_RootBuffer next; 

    // CLASS DATA

    public static VM_RootBuffer buffer = new VM_RootBuffer(null);

    private static boolean packedTail = true;

    private static int tailSize = 0;

    static VM_CycleBuffer currentCycles;

    private static final int BUFFERSIZE        = (4*GC_BLOCKSIZE - ARRAY_HEADER_SIZE) >> 2;

    private static final boolean BIG_BUFFERS = true;

    private static final int SWEEP_THRESHOLD   = BIG_BUFFERS ? 6*BUFFERSIZE : BUFFERSIZE - (BUFFERSIZE/4);
    private static final int COLLECT_THRESHOLD = BIG_BUFFERS ? 8*BUFFERSIZE : SWEEP_THRESHOLD;

    private static final int FORCE_FREQUENCY   = 1; // 8; // force cycle collection after this many epochs
    private static final int MIN_FORCE_SIZE    = 2048; // force collection if less than this in buffer

    public static final boolean ASYNC = true; // asynchronous cycle collection?

    private static final VM_GrayVisitor  grayVisitor  = ASYNC ? null : new VM_GrayVisitor();
    private static final VM_BlackVisitor blackVisitor = ASYNC ? null : new VM_BlackVisitor();
    private static final VM_WhiteVisitor whiteVisitor = ASYNC ? null : new VM_WhiteVisitor();
    private static final VM_ScanVisitor  scanVisitor  = ASYNC ? null : new VM_ScanVisitor(blackVisitor);

    private static final
	VM_AsyncBlackVisitor asyncBlackVisitor = ASYNC ? new VM_AsyncBlackVisitor()                 : null;
    private static final
	VM_AsyncWhiteVisitor asyncWhiteVisitor = ASYNC ? new VM_AsyncWhiteVisitor()                 : null;
    private static final
	VM_AsyncGrayVisitor  asyncGrayVisitor  = ASYNC ? new VM_AsyncGrayVisitor(asyncBlackVisitor) : null;
    private static final
	VM_AsyncScanVisitor  asyncScanVisitor  = ASYNC ? new VM_AsyncScanVisitor(asyncBlackVisitor) : null;

    private static final
	VM_PrintChildVisitor childPrinter      = new VM_PrintChildVisitor();

    private static final boolean TRACE_ROOTBUFFER = true;
    private static final boolean STATS = true;

    static final boolean TRACE_ASYNC = false;
    static final boolean TRACE_ASYNC_DETAIL = false;

    static final boolean TRACE_CYCLE_MARKINGS = false;
    static final boolean TRACE_ORANGE_ROOTS = false;

    static final boolean VISUALIZE_GRAPHS = false;

    static long cycleCollectTime; // time spent in mark-gray/scan/collect phase

    public static boolean trace2 = false;
    private static boolean VOID_EXTRA_ROOTS = true;
    private static int VOIDED_ROOT = 1;
    static int voidedRoots;

    // STATISTICS

    static final boolean CC_COUNT_EVENTS = VM_Allocator.RC_COUNT_EVENTS; 

    static int green;				    // green objects ignored by add() since last CC

    // Totals

    static int greenAllocated;			    // green allocated
    static int greenIgnored;			    // green objects ignored by add()
    static int blackAllocated;			    // black allocated
    static int rootCount;			    // roots added
    static int duplicates;			    // duplicate roots not buffered

    static int rootBufferSweeps;		    // sweep phases executed
    static int sweepFreeCount;			    // objects freed by sweeping
    static int sweepUnbufferedCount;		    // objects unbuffered by sweeping
    static int sweepRetainedCount;		    // objects retained by sweeping

    static int cycleCollections;		    // cycle collections executed
    static int tracedReferences;		    // references traced by mark/scan/collect phases
    static int tracedObjects;			    // objects traced by mark/scan/collect phases

    static int tracedRoots;	// objects considered as possible roots

    static int cyclesFound;	// sync cc only
    static int objectsFreed;	// sync cc only

    // METHODS

    public VM_RootBuffer(VM_RootBuffer next) {
	this.next = next;
	roots = new int[BUFFERSIZE];
	entries = 0;
    }

    public final void add (int object) {
	if (color(object) == GREEN) { // ignore statically acyclic objects
	    if (CC_COUNT_EVENTS) green++;
	    return;
	}

	if ((! VM_RCGC.cycleCollection) || (! ASYNC && VM_Scheduler.numProcessors > 1))
	    return;

	setColor(object, PURPLE);

	if (isBuffered(object)) {
	    if (CC_COUNT_EVENTS) duplicates++;
	    return;
	}

	if (CC_COUNT_EVENTS) rootCount++;

	setBufferedFlag(object);
	roots[entries++] = object;

	if (entries == BUFFERSIZE)
	    expand();
    }

    private final void expand () {
	tailSize += BUFFERSIZE;

	if (! packedTail) {
	    VM_RootBuffer prev = this;
	    for (VM_RootBuffer buf = this.next; buf != null; buf = buf.next) {
		if (buf.entries < BUFFERSIZE) {
		    prev.next = buf.next;
		    buf.next = buffer;
		    buffer = buf;
		    tailSize -= buf.entries;
		    if (TRACE_ROOTBUFFER) VM.sysWrite("||||    RootBuffer reused\n");
		    return;
		}
		prev = buf;
	    }
	    packedTail = true;
	}

	buffer = new VM_RootBuffer(this);

	if (TRACE_ROOTBUFFER) VM.sysWrite("||||    RootBuffer allocated\n");
    }

    public final void processCycles () {

	if (TRACE_ROOTBUFFER) {
	    print("||||    PROCESS CYCLES Epoch ", VM_Processor.getCurrentProcessor().localEpoch);
	    print(": ", entries + tailSize);
	    print(" roots; Allocated ", VM_Allocator.black);
	    print(" black, ", VM_Allocator.green);
	    print(" green (", green);
	    println(" ignored)");
	}

	if (CC_COUNT_EVENTS) {
	    greenAllocated += VM_Allocator.green;
	    greenIgnored += green;
	    blackAllocated += VM_Allocator.black;

	    green = 0;
	    VM_Allocator.green = 0;
	    VM_Allocator.black = 0;
	}

	if (ASYNC) asyncFreeCycles(); // if MP, free cycles found in previous epoch

	// this computation is buggy, sometimes yields negative numbers.  probably not worth it anyway, since the
	// number of rootbuffers rarely exceeds 20.
	int size2 = entries + tailSize;

	int size = 0;
	for (VM_RootBuffer b = this; b != null; b = b.next)
	    size += b.entries;

	boolean force = forceCollection(size);

	if (force || size > SWEEP_THRESHOLD) {
	    sweepRootBuffer();
	    if (force || size > COLLECT_THRESHOLD) {
		if (ASYNC)
		    asyncCollectCycles();
		else
		    collectCycles();
	    }
	}
    }


    private static final void printInfo () {
	print("\n|||| THE RECYCLER");

	print(ASYNC ? " CONCURRENT" : " SYNCHRONOUS");
	print(VM_RCGC.cycleCollection ? " [Collecting Cycles, " : " [Not Collecting Cycles, ");
	println(VM_RCGC.referenceCountTIBs ? "Reference Counting TIBs]" : "Not Reference Counting TIBs]");

	print("|||| Forcing every ", FORCE_FREQUENCY); println(" epochs");
    }


    private static boolean forceCollection (int size) {
	// TO ADD: FORCE CC IF ANY CPU BLOCKED WAITING FOR MEMORY.
	// SHOULD THERE BE OTHER CONDITIONS?

	if (size == 0)
	    return false;

	if (size < MIN_FORCE_SIZE)
	    return true;	// always handle small buffers so things don't get stuck

	VM_Processor p = VM_Processor.getCurrentProcessor();
	return p.localEpoch % FORCE_FREQUENCY == 0;
    }


    // Go through root buffer, removing any entries that are no longer candidate
    //   roots of garbage cycles (either because their reference count dropped to
    //   zero, or because they were re-linked to something else).
    public final void sweepRootBuffer () {
	// VM.sysWrite("||||    SWEEPING ROOTS\n");
	if (CC_COUNT_EVENTS) rootBufferSweeps++;

	int freed = 0; int unbuffered = 0; int retained = 0;

	for (VM_RootBuffer buf = this; buf != null; buf = buf.next) {
	    // VM.sysWrite("Sweeping buffer of ");
	    // VM.sysWrite(buf.entries, false);
	    // VM.sysWrite(" entries.\n");

	    final int bufEntries = buf.entries;
	    final int[] bufRoots = buf.roots;

	    int dst = 0;
	    for (int src = 0; src < bufEntries; src++) {
		int object = bufRoots[src];

		// if (VM.VerifyAssertions) VM.assert(isBuffered(object)); HACK!!!
		if (! isBuffered(object)) println("|||| *************** <<<<<<<<<<<< NOT BUFFERED IN SWEEP >>>>>>>>>>>>> ******************");

		if (isZeroReferenceCount(object)) {
		    freed++;
		    release(object);	// has become garbage; free it
		}
		else if (color(object) != PURPLE) {
		    unbuffered++;
		    clearBufferedFlag(object); // has been freshly pointed to; no longer a candidate
		}
		else {
		    retained++;
		    bufRoots[dst++] = object;
		}
	    }

	    if (buf != this) {
		if (dst != bufEntries)
		    packedTail = false;
		tailSize -= freed+unbuffered;
	    }

	    buf.entries = dst;
	}

	if (CC_COUNT_EVENTS) {
	    sweepFreeCount       += freed;
	    sweepUnbufferedCount += unbuffered;
	    sweepRetainedCount   += retained;
	}

	if (TRACE_ROOTBUFFER) {
	    VM.sysWrite("||||    SWEEP ROOTS: ");
	    VM.sysWrite(freed, false);
	    VM.sysWrite(" freed; ");
	    VM.sysWrite(retained, false);
	    VM.sysWrite(" retained.\n");
	    if (unbuffered != 0) {
		VM.sysWrite("<<<< --------------- ");
		VM.sysWrite(unbuffered, false);
		VM.sysWrite(" UNBUFFERED ------------------ >>>>\n");
	    }
	}
    }

    public final void collectCycles() {
	// Note: must be called after sweepRootBuffer()

	if (VM.VerifyAssertions) VM.assert(! ASYNC);
	if (CC_COUNT_EVENTS) cycleCollections++;

	markGray();

	scan();

	collectWhite();

	VM_GrayVisitor.grayVisited = 0;	// statistics
	VM_WhiteVisitor.whiteFreed = 0;	// statistics

	packedTail = (this.next == null);
	tailSize = 0;
    }


    public final void markGray() {
	if (VM.VerifyAssertions) VM.assert(! ASYNC);

	for (VM_RootBuffer buf = this; buf != null; buf = buf.next) {

	    final int bufEntries = buf.entries;

	    if (bufEntries > 0) {
		final int[] bufRoots = buf.roots;

		// Subtract counts due to internal pointers, coloring objects gray as we go
		// VM.sysWrite("* Mark Gray\n");
		for (int i = 0; i < bufEntries; i++) {
		    int object = bufRoots[i];

		    // dumpRefcountInfo("Mark gray ", object);

		    if (VM.VerifyAssertions) VM.assert(color(object) == GRAY || ! isZeroReferenceCount(object));

		    int g = VM_GrayVisitor.grayVisited; // debug
		    
		    if (color(object) != GRAY) {
			// if (VM_Processor.getCurrentProcessor().localEpoch > 0) dumpRefcountInfo("Root for gray: ", object);
			grayVisitor.markGray(object);
			if (CC_COUNT_EVENTS) tracedRoots++;
		    }
		    else if (VOID_EXTRA_ROOTS) {
			clearBufferedFlag(bufRoots[i]);
			bufRoots[i] = VOIDED_ROOT; // flag already found
			if (CC_COUNT_EVENTS) voidedRoots++;
		    }

		    debugCycles(bufRoots, i, g);
		    if (trace1) VM.sysWrite("|");
		}
	    }
	}

	if (trace1) VM.sysWrite("\n\n");
    }

    private final void scan() {
	if (VM.VerifyAssertions) VM.assert(! ASYNC);

	for (VM_RootBuffer buf = this; buf != null; buf = buf.next) {

	    final int bufEntries = buf.entries;

	    if (bufEntries > 0) {
		final int[] bufRoots = buf.roots;

		// Mark garbage white and live stuff black, re-incrementing blackened nodes
		// VM.sysWrite("* Scan\n");
		for (int i = 0; i < bufEntries; i++) {
		    if (VOID_EXTRA_ROOTS && bufRoots[i] == VOIDED_ROOT)
			continue; // found from other root?
		    scanVisitor.scan(bufRoots[i]);
		    if (trace1) VM.sysWrite("|");
		}
	    }
	}

	if (trace1) VM.sysWrite("\n\n");
    }

    private final void collectWhite () {
	if (VM.VerifyAssertions) VM.assert(! ASYNC);

	int rootsFound = 0;	// debug

	for (VM_RootBuffer buf = this; buf != null; buf = buf.next) {

	    final int bufEntries = buf.entries;

	    if (bufEntries > 0) {
		final int[] bufRoots = buf.roots;

		// Collect white garbage
		// VM.sysWrite("* Collect White\n");
		for (int i = 0; i < bufEntries; i++) {
		    if (VOID_EXTRA_ROOTS && bufRoots[i] == VOIDED_ROOT)
			continue; // found from other root?

		    clearBufferedFlag(bufRoots[i]);
		    if (color(bufRoots[i]) == WHITE) {
			rootsFound++;
			if (trace2) VM.sysWrite("\n\n");
		    }

		    whiteVisitor.collectWhite(bufRoots[i]);
		    if (trace1) VM.sysWrite("|");
		}
		if (trace1) VM.sysWrite("\n\n");
		buf.entries = 0;
	    }
	}

	cyclesFound += rootsFound;
	objectsFreed += VM_WhiteVisitor.whiteFreed;

	if (TRACE_ROOTBUFFER) {
	    VM.sysWrite("||||    COLLECT CYCLES: ");
	    VM.sysWrite(VM_GrayVisitor.grayVisited, false);
	    VM.sysWrite(" scanned; collected ");
	    VM.sysWrite(VM_WhiteVisitor.whiteFreed, false);
	    VM.sysWrite(" from ");
	    VM.sysWrite(rootsFound, false);
	    VM.sysWrite(" roots.\n");
	}
    }

    // release the object, and decref things it points to
    static void release (int object) {
	initializeReferenceCount(object); // count = 0; color = black; unbuffered
	VM_Allocator.freeObject(object);
    }


    // if object modified and gray/white/orange, scan its reachable graph of those colors black again
    static void scanBlackOnUpdate(int object) {
	if (ASYNC && VM_Scheduler.numProcessors > 1) {
	    int color = color(object);
	    if (color == GRAY || color == WHITE || color == ORANGE) {
		if (TRACE_ASYNC) VM.sysWrite("#");
		asyncBlackVisitor.scanBlack(object);
	    }
	}
    }


    private final static void asyncFreeCycles () {
	if (VM_Scheduler.numProcessors > 1) {
	    if (VM.VerifyAssertions) VM.assert(ASYNC);

	    if (currentCycles != null) {
		currentCycles.freeCycles();
		currentCycles.freeBuffer();
		currentCycles = null;
	    }
	}
    }


    public final void asyncCollectCycles () {
	// Note: must be called after sweepRootBuffer() and asyncFreeCycles()

	if (VM.VerifyAssertions) VM.assert(ASYNC);
	if (CC_COUNT_EVENTS) cycleCollections++;
	long tStart;
	if (VM_CycleBuffer.TIME_CYCLE_COLLECTION) tStart = VM_Time.cycles();

	currentCycles = new VM_CycleBuffer(); // HACK: should reuse old buffers

	asyncMarkGray();

	asyncScan();

	asyncCollectWhite();

	packedTail = (this.next == null);
	tailSize = 0;

	if (VM_CycleBuffer.TIME_CYCLE_COLLECTION) cycleCollectTime += (VM_Time.cycles() - tStart);

	currentCycles.validateCycles();

	if (VM_Scheduler.numProcessors == 1) {
	    currentCycles.freeCycles();
	    currentCycles.freeBuffer();
	    currentCycles = null;
	}
    }

    public final void asyncMarkGray() {
	if (VM.VerifyAssertions) VM.assert(ASYNC);

	if (STATS) VM_AsyncGrayVisitor.grayVisited = 0;	// statistics

	for (VM_RootBuffer buf = this; buf != null; buf = buf.next) {

	    final int bufEntries = buf.entries;

	    if (bufEntries > 0) {
		final int[] bufRoots = buf.roots;

		// Subtract counts due to internal pointers, coloring objects gray as we go
		// VM.sysWrite("* Mark Gray\n");
		for (int i = 0; i < bufEntries; i++) {
		    if (CC_COUNT_EVENTS) tracedRoots++;
		    int object = bufRoots[i];

		    // int g = VM_AsyncGrayVisitor.grayVisited; // debug
		    
		    if (VM.VerifyAssertions) VM.assert(! isZeroReferenceCount(object));

		    if (color(object) != GRAY)
			asyncGrayVisitor.markGray(object);
		    else if (VOID_EXTRA_ROOTS) {
			clearBufferedFlag(bufRoots[i]);
			bufRoots[i] = VOIDED_ROOT; // flag already found
			if (CC_COUNT_EVENTS) voidedRoots++;
		    }

		    // debugCycles(bufRoots, i, g); bogus for now
		    if (trace1) VM.sysWrite("|");
		}
	    }
	}

	if (STATS && TRACE_ROOTBUFFER) { 
	    VM.sysWrite("||||     GrayVisitor visited "); 
	    VM.sysWrite(VM_AsyncGrayVisitor.grayVisited, false);  VM.sysWrite("\n");
	}

	if (trace1) VM.sysWrite("\n\n");
    }

    private final void asyncScan() {
	if (VM.VerifyAssertions) VM.assert(ASYNC);

	for (VM_RootBuffer buf = this; buf != null; buf = buf.next) {

	    final int bufEntries = buf.entries;

	    if (bufEntries > 0) {
		final int[] bufRoots = buf.roots;

		// Mark garbage white and live stuff black
		// VM.sysWrite("* Scan\n");
		for (int i = 0; i < bufEntries; i++) {
		    int object = bufRoots[i];

		    if (VOID_EXTRA_ROOTS && bufRoots[i] == VOIDED_ROOT)
			continue; // found from other root?

		    asyncScanVisitor.scan(object);

		    if (TRACE_ORANGE_ROOTS && color(object) == ORANGE) {
			dumpRefcountInfo("ORANGE ROOT: ", object);
			childPrinter.printChildren(object);
		    }

		    if (trace1) VM.sysWrite("|");
		}
	    }
	}

	if (trace1) VM.sysWrite("\n\n");
    }


    private final void asyncCollectWhite () {
	if (VM.VerifyAssertions) VM.assert(ASYNC);
	int cycles = 0;

	for (VM_RootBuffer buf = this; buf != null; buf = buf.next) {

	    final int bufEntries = buf.entries;

	    if (bufEntries > 0) {
		final int[] bufRoots = buf.roots;

		// Find orange objects, mark them white, and put them in the cycle buffer
		// VM.sysWrite("* Collect White\n");
		for (int i = 0; i < bufEntries; i++) {
		    int object = bufRoots[i];

		    if (VOID_EXTRA_ROOTS && bufRoots[i] == VOIDED_ROOT)
			continue; // found from other root?

		    if (color(object) == ORANGE) { // if not already part of cycle, check it
			if (VISUALIZE_GRAPHS) writeGraphStart(object);
			asyncWhiteVisitor.collectWhite(object);
			if (VISUALIZE_GRAPHS) writeGraphEnd(object);
			currentCycles.addDelimiter(); // put end mark on cycle we just found
			if (STATS) cycles++;
		    }
		    else {
			// setColor(object, BLACK); // SHOULD WE DO THIS?
			if (color(object) != WHITE)
			    clearBufferedFlag(object);
		    }

		    if (trace1) VM.sysWrite("|");
		}

		buf.entries = 0; // clear buffer
	    }
	}

	if (STATS && TRACE_ROOTBUFFER) {
	    VM.sysWrite("||||     AsyncCollectWhite: found "); VM.sysWrite(cycles, false); VM.sysWrite(" cycles\n");
	}

	if (trace1) VM.sysWrite("\n\n");
    }




    // DEBUG

    public static void writeGraphStart(int object) {
	VM_Scheduler.lockOutput();
	print("@@@@graph: {    title:\"Cycle");  print(object);  println("\"  folding:0");
	VM_Scheduler.unlockOutput();
    }

    public static void writeGraphEnd(int object) {
	VM_Scheduler.lockOutput();
	println("@@@@}");
	VM_Scheduler.unlockOutput();
    }

    public static void writeGraphNode(int object) {
	VM_Scheduler.lockOutput();
	print("@@@@    node: { title: \""); print(object);
	print("\"  label: \"");  
	  VM_Magic.getObjectType(VM_Magic.addressAsObject(object)).getDescriptor().sysWrite();
 	  print("  "); print(cyclicReferenceCount(object)); print(","); print(referenceCount(object));
	print("\"  color: ");
	switch (color(object)) {
	case RED: print("red"); break;
	case GREEN: print("green"); break;
	case WHITE: print("white"); break;
	case BLACK: print("black textcolor: white"); break;
	case GRAY: print("gray"); break;
	case ORANGE: print("orange"); break;
	case PURPLE: print("purple"); break;
	case BLUE: print("blue"); break;
	}
	println(" }");
	VM_Scheduler.unlockOutput();
    }

    public static void writeGraphEdge(int fromObject, int object) {
	VM_Scheduler.lockOutput();
	print("@@@@         edge: { sourcename: \""); print(fromObject); 
	print("\"  targetname: \""); print(object);
	println("\" }");
	VM_Scheduler.unlockOutput();
    }

	

    private static void debugCycles(int[] bufRoots, int i, int g) {
	if (TRACE_ROOTBUFFER && traceBigCycles && (VM_GrayVisitor.grayVisited - g > bigCycleCount)) { // debug
	    int object = bufRoots[i];
	    VM.sysWrite("||||      ");
	    VM.sysWrite(VM_GrayVisitor.grayVisited - g, false);
	    VM.sysWrite(" traced for ");
	    VM_Allocator.printType(object); // debug

	    Object objptr = VM_Magic.addressAsObject(object);
	    VM_Type type = VM_Magic.getObjectType(objptr);

	    VM_Type typeVM_Class = findOrCreateType("LVM_Class;");

	    if (type == typeVM_Class) {
		VM.sysWrite("||||        Class of class is ");
		VM_Class cl = (VM_Class) objptr;
		cl.getDescriptor().sysWrite();
		VM.sysWrite("\n");
	    }

	    if (traceArrayCycles && type.isArrayType() && type.asArray().getElementType().isClassType()) {
		int len = VM_Magic.getArrayLength(objptr);
		VM.sysWrite("||||        Object array of ");
		VM.sysWrite(len, false);
		VM.sysWrite(" elements containing ");
		if (len > 0) {
		    int i0 = VM_Magic.getMemoryWord(object);
		    if (i0 != 0)
			VM_Allocator.printType(i0);
		}
		if (len > 1) {
		    int i1 = VM_Magic.getMemoryWord(object+4);
		    if (i1 != 0) {
			VM.sysWrite("||||        and ");
			VM_Allocator.printType(i1);
		    }
		}
	    }
	}
    }


    public static void boot () {

	printInfo();

	// Mark certain VM classes acyclic so that the cycle collector doesn't search the whole VM each time

	if (TRACE_ROOTBUFFER) println("|||| Marking selected VM classes acyclic.");

	markAcyclic("LVM_Class;");
	markAcyclic("LVM_Array;");
	markAcyclic("LVM_Primitive;");
	markAcyclic("LVM_Method;");
	markAcyclic("LVM_Field;");
	markAcyclic("LVM_Triplet;");
	markAcyclic("LVM_CompiledMethod;");

	markAcyclic("LVM_Processor;");
	markAcyclic("LVM_Thread;");
	markAcyclic("LVM_IdleThread;");
	markAcyclic("LVM_RCCollectorThread;");
    }

    // Abstraction of classloader
    private static VM_Type findOrCreateType (String str) {
	return VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom(str));
    }

    // Set acyclic bit in class object
    private static void markAcyclic(String str) {
	findOrCreateType(str).acyclic = true;
    }


    public static void printStatistics (int alloc, int allocBytes, int nzdecs) {
	if (CC_COUNT_EVENTS) {
	    greenAllocated += VM_Allocator.green;
	    greenIgnored += green;
	    blackAllocated += VM_Allocator.black;

	    print("Black Allocated: ",    blackAllocated);  percentage(blackAllocated, alloc, "allocations");
	    print("Green Allocated: ",    greenAllocated);  percentage(greenAllocated, alloc, "allocations");
	    print("Green CC savings: ",   greenIgnored);    percentage(greenIgnored, nzdecs, "non-0 decs");
	    print("Duplicates: ",         duplicates);      percentage(duplicates, nzdecs, "non-0 decs");

	    print("Roots buffered: ",     rootCount);       percentage(rootCount, nzdecs, "non-0 decs");
	    println("Root buffer sweeps: ", rootBufferSweeps);
	    print("  Sweep freed: ",      sweepFreeCount);  percentage(sweepFreeCount, nzdecs, "non-0 decs");
	    print("  Sweep unbuffered: ", sweepUnbufferedCount);  percentage(sweepUnbufferedCount, nzdecs, "non-0 decs");
	    println("  Sweep retained: ",   sweepRetainedCount);  
	    println("Cycle collections: ",  cycleCollections);
	    if (VOID_EXTRA_ROOTS) 
		print("  Roots voided:   ",   voidedRoots);    percentage(voidedRoots, nzdecs, "non-0 decs");
	    print("  Roots traced:   ",   tracedRoots);    percentage(tracedRoots, nzdecs, "non-0 decs");
	    print("  Objects traced: ",   tracedObjects);  percentage(tracedObjects, alloc, "allocations(*)");
	    print("  Refs traced:    ",   tracedReferences); percentage(tracedReferences, alloc, "allocations(*)");

	    if (! ASYNC) {
		println("Cycles found: ", cyclesFound);
		print("Cycle objects freed: ", objectsFreed);
		percentage(objectsFreed, alloc, "allocations");
	    }
	}

	if (VM_CycleBuffer.TIME_CYCLE_COLLECTION) {
	    print("Time to collect cycles: ");  print((int) (cycleCollectTime/VM_Allocator.TicksPerMicrosecond)); 
	    println(" usec");
	}

	int rootBuffers = 0;
	for (VM_RootBuffer b = buffer; b != null; b = b.next)
	    rootBuffers++;
	int rootSize = rootBuffers*BUFFERSIZE;

	println("Maximum root buffers: ", rootBuffers);
	print("Maximum root space: ", rootSize);  percentage(rootSize, allocBytes, "allocated memory");
    }
}


/////////////////////////////////////////////////////////////////////////////
// SYNCHRONOUS CYCLE COLLECTION
/////////////////////////////////////////////////////////////////////////////

class VM_GrayVisitor 
    extends VM_ChildVisitor
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    public static int grayVisited = 0;	// debug

    public final void markGray(int object) {
	if (VM.VerifyAssertions) VM.assert(! VM_RootBuffer.ASYNC);
	if (VM.VerifyAssertions) VM.assert(color(object) != GREEN && color(object) != RED && color(object) != WHITE);

	if (true) grayVisited++;		// debug

	if (color(object) != GRAY) {
	    if (VM.VerifyAssertions) VM.assert(color(object) == BLACK || color(object) == PURPLE);
	    // if (VM_Processor.getCurrentProcessor().localEpoch > 0) dumpRefcountInfo("  coloring gray and visiting: ", object);

	    if (trace1) VM.sysWrite("*");
	    setColor(object, GRAY);
	    visitChildren(object);
	    if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedObjects++;
	}
    }	

    protected final boolean visit (int object) {
	if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedReferences++;
	int c = color(object);
	if (c != GREEN && c != RED) { 	// if object isn't inherently acyclic...
	    decReferenceCount(object); 	// subtract count due to internal pointer...
	    markGray(object); 			// and gray its (ungrayed) children
	}
	return true;
    }
}

// Undoes effects of markGray: blackens nodes and increments reference counts
class VM_BlackVisitor
    extends VM_ChildVisitor
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    public final void scanBlack(int object) {
	if (VM.VerifyAssertions) VM.assert(! VM_RootBuffer.ASYNC);

	if (color(object) != BLACK) {
	    if (trace1) VM.sysWrite("X");
	    setColor(object, BLACK);
	    visitChildren(object);
	    if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedObjects++;
	}
    }

    protected final boolean visit(int object) {
	if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedReferences++;
	int c = color(object);
	if (c != GREEN && c != RED) {
	    incReferenceCount(object);
	    scanBlack(object);
	}
	return true;
    }
}


// Scan gray nodes, looking for cyclic garbage.  If found, whiten it.  
//   Otherwise, blacken and restore refcounts.
class VM_ScanVisitor
    extends VM_ChildVisitor
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    private final VM_BlackVisitor blackVisitor;

    public VM_ScanVisitor(VM_BlackVisitor blackVisitor) {
	this.blackVisitor = blackVisitor;
    }

    public final void scan(int object) {
	if (VM.VerifyAssertions) VM.assert(! VM_RootBuffer.ASYNC);

	visit(object);
    }

    protected final boolean visit(int object) {
	if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedReferences++;
	if (color(object) == GRAY) {
	    if (isZeroReferenceCount(object)) {
		if (trace1) VM.sysWrite("@");
		setColor(object, WHITE);
		visitChildren(object);
		if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedObjects++;
	    }
	    else
		blackVisitor.scanBlack(object);
	}
	return true;
    }
}


// Collect white garbage
class VM_WhiteVisitor
    extends VM_ChildVisitor
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    public static int whiteFreed;	// statistics

    public final void collectWhite (int object) {
	if (VM.VerifyAssertions) VM.assert(! VM_RootBuffer.ASYNC);

	if (color(object) == WHITE && ! isBuffered(object)) { // DEAL WITH BUFFERED DIFFERENTLY?????????????
	    if (trace1) VM.sysWrite("O");
	    if (VM_RootBuffer.trace2) { VM_Allocator.printclass(object); VM.sysWrite("  "); }
	    setColor(object, BLACK);
	    visitChildren(object);
	    if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedObjects++;
	    whiteFreed++;
	    VM_Allocator.freeObject(object);
	}
    }

    protected final boolean visit(int object) {
	if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedReferences++;
	if (color(object) != GREEN)
	    collectWhite(object);
	else {
	    VM_Allocator.decrementRC(object);
	}
	return true;
    }
}


/////////////////////////////////////////////////////////////////////////////
// ASYNCHRONOUS CYCLE COLLECTION
/////////////////////////////////////////////////////////////////////////////

class VM_AsyncGrayVisitor 
    extends VM_ChildVisitor
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    private final VM_AsyncBlackVisitor blackVisitor;

    public VM_AsyncGrayVisitor(VM_AsyncBlackVisitor blackVisitor) {
	this.blackVisitor = blackVisitor;
    }

    public static int grayVisited;	// debug

    public final void markGray(int object) {
	if (VM.VerifyAssertions) VM.assert(VM_RootBuffer.ASYNC);
	if (VM.VerifyAssertions) VM.assert(color(object) != GREEN && color(object) != RED && color(object) != WHITE);

	if (true) grayVisited++;		// debug

	if (color(object) != GRAY) {
	    setColor(object, GRAY);
	    cloneCount(object);
	    if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedObjects++;
	    visitChildren(object);
	}
    }	

    protected final boolean visit(int object) {
	if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedReferences++;

	int color = color(object);
	if (color == GREEN || color == RED)
	    return true;	// acyclic; ignore

	if (true) grayVisited++;

	if (color == GRAY) {
	    if (! isZeroCyclicReferenceCount(object)) {
		decCyclicReferenceCount(object);
		return true;
	    }
	    else {
		// Found old aborted search due to concurrent modification
		if (VM.VerifyAssertions) VM.assert(VM_Scheduler.numProcessors > 1);

		if (VM_RootBuffer.TRACE_ASYNC_DETAIL) dumpRefcountInfo("Can't dec zero CRC ", object);
		return false;
	    }
	}
	else if (color == BLACK || color == PURPLE) {

	    setColor(object, GRAY);
	    cloneCount(object);
	    decCyclicReferenceCount(object);
	    if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedObjects++;
	    return visitChildren(object);
	}
	else {
	    VM.assert(NOT_REACHED);
	    return false;
	}
    }
}


// Undoes effects of markGray: blackens nodes and increments reference counts
class VM_AsyncBlackVisitor
    extends VM_ChildVisitor
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    public final void scanBlack(int object) {
	if (VM.VerifyAssertions) VM.assert(VM_RootBuffer.ASYNC);
	visit(object);
    }

    protected final boolean visit(int object) {
	if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedReferences++;
	int color = color(object);

	if (color == GRAY || color == ORANGE || color == WHITE) {
	    if (trace1) VM.sysWrite("X");
	    clearCyclicReferenceCount(object);
	    setColor(object, BLACK);
	    visitChildren(object);
	    if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedObjects++;
	}
	return true;
    }
}


// Scan gray nodes, looking for cyclic garbage.  If found, whiten it.  
//   Otherwise, blacken and restore refcounts.
class VM_AsyncScanVisitor
    extends VM_ChildVisitor
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    private final VM_AsyncBlackVisitor blackVisitor;

    public VM_AsyncScanVisitor(VM_AsyncBlackVisitor blackVisitor) {
	this.blackVisitor = blackVisitor;
    }

    public final void scan(int object) {
	if (VM.VerifyAssertions) VM.assert(VM_RootBuffer.ASYNC);

	visit(object);
    }

    protected final boolean visit(int object) {
	if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedReferences++;
	int color = color(object);

	if (color == GRAY && isZeroCyclicReferenceCount(object)) {
	    if (false && VM_RootBuffer.TRACE_CYCLE_MARKINGS) dumpRefcountInfo("COLORING ORANGE ", object);
	    setColor(object, ORANGE);
	    visitChildren(object);
	    if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedObjects++;
	}
	else if (color == GRAY)
	    blackVisitor.scanBlack(object);

	return true;
    }
}


// Buffer white garbage for later collection
class VM_AsyncWhiteVisitor
    extends VM_ChildVisitor
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    public static int whiteFreed;	// statistics

    public final void collectWhite (int object) {
	if (VM.VerifyAssertions) VM.assert(VM_RootBuffer.ASYNC);
	visit(0, object);
    }

    protected final boolean visit(int fromObject, int object) {
	if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedReferences++;

	if (VM_RootBuffer.TRACE_CYCLE_MARKINGS) dumpRefcountInfo("WHITENING IF ORANGE ", object);
	if (VM_RootBuffer.VISUALIZE_GRAPHS) {
	    if (color(object) == ORANGE) VM_RootBuffer.writeGraphNode(object);
	    if (fromObject != 0) VM_RootBuffer.writeGraphEdge(fromObject, object);
	}

	if (color(object) == ORANGE) {
	    setColor(object, WHITE);
	    setBufferedFlag(object);
	    VM_RootBuffer.currentCycles.add(object);

	    visitChildrenWithEdges(object);
	    if (VM_RootBuffer.CC_COUNT_EVENTS) VM_RootBuffer.tracedObjects++;
	    whiteFreed++;
	}
	return true;
    }
}

class VM_PrintChildVisitor
    extends VM_ChildVisitor
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    public final void printChildren(int object) {
	visitChildren(object);
    }

    protected final boolean visit(int object) {
	dumpRefcountInfo("CHILD: ", object);

	return true;
    }
}
