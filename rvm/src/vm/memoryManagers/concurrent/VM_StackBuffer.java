/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * This class handles buffering of live object references on running thread stacks.
 *
 * @author Han Lee
 * @author David F. Bacon
 * 
 * @see VM_Allocator (for managing of stack buffers)
 *
 */
public class VM_StackBuffer 
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    static final int STACK_BUFFER_SIZE  = 1024;  // stack buffer size in bytes

    static final int STACK_BUFFER_NEXT_OFFSET  = STACK_BUFFER_SIZE - 4;
    static final int STACK_BUFFER_LAST_OFFSET  = STACK_BUFFER_SIZE - 8;
    static final int STACK_BUFFER_FIRST_OFFSET = -4;

    static final boolean GC_TRACESCANSTACK = false;        // for detailed tracing RCGC
    static final boolean CHECK_REF_MAPS = false;       // for checking refs in stacks
    static final boolean FILTER_STACK_REFS = true;	   // filter out illegal pointers from stack


    static void	allocateStackBuffer(VM_Thread t)
    {
	int bufaddr;
	if ((bufaddr = VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP,
					 STACK_BUFFER_SIZE)) == 0) {
	    VM.sysWrite(" In VM_RCBuffers.allocateStackBuffer, call to sysMalloc returned 0\n");
	    VM.sysExit(1800);
	}

	VM_Magic.setMemoryWord(bufaddr + STACK_BUFFER_NEXT_OFFSET, 0);
	int index = t.stackBufferCurrent;

	t.stackBuffer[index]    = bufaddr;
	t.stackBufferTop[index] = bufaddr + STACK_BUFFER_FIRST_OFFSET;
	t.stackBufferMax[index] = bufaddr + STACK_BUFFER_LAST_OFFSET;
    }


    static void	growStackBuffer(VM_Thread t)
    {
	int bufaddr;
	if ((bufaddr = VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP,
					 STACK_BUFFER_SIZE)) == 0) {
	    VM_Scheduler.gcWaitMutex.lock();
	    VM_Scheduler.assert(VM_Thread.getCurrentThread().isIdleThread == false);
	    VM_Thread.getCurrentThread().yield(VM_Scheduler.gcWaitQueue, VM_Scheduler.gcWaitMutex);
	    if ((bufaddr = VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP,
					     STACK_BUFFER_SIZE)) == 0) {
		VM.sysWrite(" In VM_RCBuffers.growStackBuffer, call to sysMalloc returned 0\n");
		VM.sysExit(1800);
		// VM_Scheduler.traceback("VM_RCBuffer::growStackBuffer");
	    }
	}

	// VM_Scheduler.trace("growStackBuffer" , "stackDepth = ", ++stackDepth);

	int index = t.stackBufferCurrent;

	// set last word in current buffer to address of next buffer
	VM_Magic.setMemoryWord(t.stackBufferTop[index] + 4, bufaddr);
	// set fptr in new buffer to null, to identify it as last
	VM_Magic.setMemoryWord(bufaddr + STACK_BUFFER_NEXT_OFFSET, 0);
	// set incDecBuffer pointers in processor object for stores into new buffer
	t.stackBufferTop[index] = bufaddr + STACK_BUFFER_FIRST_OFFSET;
	t.stackBufferMax[index] = bufaddr + STACK_BUFFER_LAST_OFFSET;
    }


    static void addToStackBuffer(int ref, VM_Thread t)
    {
	int index = t.stackBufferCurrent;
	int addr = t.stackBufferTop[index] + 4;
	VM_Magic.setMemoryWord(addr, ref);

	t.stackBufferTop[index] = addr;

	if (addr == t.stackBufferMax[index]) {
	    growStackBuffer(t);
	}
    }



    static void	freeStackBuffer(int addr)
    {
	for (int buf = addr, nextbuf = 0; buf != 0; buf = nextbuf) {
	    nextbuf = VM_Magic.getMemoryWord(buf + STACK_BUFFER_NEXT_OFFSET);

	    VM.sysCall1(VM_BootRecord.the_boot_record.sysFreeIP, buf);

	    // VM_Scheduler.trace("freeStackBuffer" , "stackDepth = ", --stackDepth);
	}
    }


    static void
    gc_processStackBuffers (boolean increment, VM_Processor p) {
	for (int i = 0; i < VM_Scheduler.threads.length; i++) {
	    VM_Thread t = VM_Scheduler.threads[i];

	    if (isLocalUserThread(t, p)) 
		processStackBuffer(t, increment);
	}
    }


    protected static boolean isLocalUserThread (VM_Thread t, VM_Processor p) {
	return ((t != null && ! t.isGCThread) &&
		((t.processorAffinity == null && p.id == VM_Scheduler.PRIMORDIAL_PROCESSOR_ID) ||
		 (t.processorAffinity != null && p.id == t.processorAffinity.id)));
    }


    protected static void processStackBuffer(VM_Thread t, boolean increment) {
	int index = increment ? ((t.stackBufferCurrent == 0) ? 1 : 0) : t.stackBufferCurrent;
	int top   = t.stackBufferTop[index];

	for (int start = t.stackBuffer[index], next = 0; start != 0; start = next) {
	    int nextAddr = start + STACK_BUFFER_NEXT_OFFSET;
	    next = VM_Magic.getMemoryWord(nextAddr);
	    // Determine size based on whether it is a full buffer (i.e. next != null)
	    int end = (next == 0) ? top : start + STACK_BUFFER_LAST_OFFSET;
		    
	    for (int bufptr = start; bufptr <= end; bufptr += 4) {
		int object = VM_Magic.getMemoryWord(bufptr);

		if (VM_Allocator.GC_FILTER_MALLOC_REFS && VM_Allocator.isMalloc(object)) { // do at scan time?
		    VM.sysWrite("Ignoring malloc ref in stack buffer: ");
		    VM.sysWrite(object);
		    VM_Allocator.printType(object);
		    continue;	// skip refs to stuff in the malloc area (they may be gone by now)
		}

		// TEMPORARY PATCH FOR BUG IN STACKMAP/dtl BYTECODE IMPLEMENTATION
		if (FILTER_STACK_REFS && ! VM_Allocator.isPossibleRefOrMalloc(object))
		    continue;

		if (VM.VerifyAssertions) VM_Allocator.checkRef("Bad ref in stack buffer", object, bufptr);
		if (VM_Allocator.RC_COUNT_EVENTS) VM_Allocator.stackRefCount++;

		if (increment) 
		    VM_Allocator.incrementRC(object);
		else 
		    VM_Allocator.decrementRC(object);
	    }
	}

	if (! increment && ! t.stackBufferSame)        // free decrement buffer if not retained
	    freeStackBuffer(t.stackBuffer[index]);
    }


    // gc_scanStacks:
    // for each thread queue on this processor, scan their stacks
    //
    static void
    gc_scanStacks () {
	VM_Processor p = VM_Processor.getCurrentProcessor();
	int lastThread = VM_Thread.maxThreadIndex;

	for (int i = 0; i < lastThread; i++) {
	    VM_Thread t = VM_Scheduler.threads[i];

	    if (isLocalUserThread(t, p)) 
		scanStackOrReuseBuffer(t);
	}
    }


    static void scanStackOrReuseBuffer(VM_Thread t) {

	int prevIndex = (t.stackBufferCurrent == 0) ? 1 : 0;

	if (t.stackBufferNeedScan) {
	    // Thread was active in epoch; scan its stack

	    allocateStackBuffer(t);
	    t.stackBufferSame = false;

	    scanStack(t);
	} 
	else {
	    // Thread was idle in epoch; don't scan, just reuse the previous buffer

	    t.stackBuffer[t.stackBufferCurrent] = t.stackBuffer[prevIndex];
	    t.stackBufferTop[t.stackBufferCurrent] = t.stackBufferTop[prevIndex];
	    t.stackBufferMax[t.stackBufferCurrent] = t.stackBufferMax[prevIndex];
	    t.stackBufferSame = true;
	    if (VM_Allocator.GC_TRACEALLOCATOR) {
		VM.sysWrite("gc_scanStacks: skipping scan of stack for thread number ");
		VM.sysWrite(t.getIndex(), false);
		VM.sysWrite("\n");
	    }
	}
	t.stackBufferCurrent = prevIndex;
    }

    // scanStack:
    //    locates and updates refs/pointers in stack frames using stack maps.
    //    moves code objects, and updates saved link registers in the stack frames.
    //
    // We must allow for the possibility that there may be more than one invocation
    // of a method on the stack.
    //
    static void scanStack(VM_Thread t) {

	int stack_ref_count = 0;

	// start stack processing at frame of caller of allocate
	// (could start at allocateScalar or allocateArray, to capture the passed
	// in TIB ptr...but assume for now that this TIB will be found by some
	// other path)

	int fp = t.contextRegisters.gprs[FRAME_POINTER];
	int ip = VM_Magic.getNextInstructionAddress(fp);

	// get the iterator from our VM_CollectorThread object

	VM_CollectorThread collector = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
	VM_GCMapIteratorGroup iteratorGroup = collector.iteratorGroup;
	iteratorGroup.newStackWalk(t);

	if (VM_Allocator.GC_TRACEALLOCATOR || GC_TRACESCANSTACK) { 
	    VM.sysWrite("||||    scanStack for thread ");
	    VM.sysWrite(t.getIndex(),false);
	    if (t.isIdleThread) VM.sysWrite(" (idleThread)");
	    VM.sysWrite("\n");
	}

	int prevFp = 0;
	// start scan using fp & ip in threads saved context registers
	ip = t.contextRegisters.getInnermostInstructionAddress();
	fp = t.contextRegisters.getInnermostFramePointer();

	// At start of loop:
	//   fp -> frame for method invocation being processed
	//   ip -> instruction pointer in the method (normally a call site)

	while (VM_Magic.getCallerFramePointer(fp) != STACKFRAME_SENTINAL_FP) {

	    if (GC_TRACESCANSTACK) {
		VM.sysWrite("----- FRAME ----- fp = ");   VM.sysWrite(fp);
		VM.sysWrite(" ip = ");                    VM.sysWrite(ip);	VM.sysWrite(".\n");
	    }
 
            int compiledMethodId = VM_Magic.getCompiledMethodID(fp);

	    if (compiledMethodId != INVISIBLE_METHOD_ID) {

		// Scan the stack frame and add refs to the stack buffer

		int refs = scanStackFrame(fp, ip, compiledMethodId, iteratorGroup, t);

		if (GC_TRACESCANSTACK) stack_ref_count += refs;
 	    }
	    else {
               // Assembler frame: no corresponding VM_Method

               if (GC_TRACESCANSTACK) VM_Scheduler.trace("----- METHOD -----", "INVISIBLE_METHOD_ID");
            }

	    // Position fp & ip for next frame to be processed
	    prevFp = fp;
	    ip = VM_Magic.getReturnAddress(fp);
	    fp = VM_Magic.getCallerFramePointer(fp);
	}

	if (GC_TRACESCANSTACK) {
	    VM.sysWrite("||||    scanStack: end of stack.  stack_ref_count = ");
	    VM.sysWrite(stack_ref_count);
	    VM.sysWrite(".\n");
	}
    }


    protected static int
    scanStackFrame(int fp, int ip, int compiledMethodId, VM_GCMapIteratorGroup iteratorGroup, VM_Thread t) {

	VM_CompiledMethod compiledMethod = VM_ClassLoader.getCompiledMethod(compiledMethodId);
	VM_Method         method         = compiledMethod.getMethod();

	if (GC_TRACESCANSTACK || CHECK_REF_MAPS ) {
	    VM_Scheduler.outputMutex.lock(); // dfb: why is sysWrite different from all other sysWrites????
	    VM.sysWrite(" ----- METHOD ----- ");
	    VM.sysWrite(method.getDeclaringClass().getDescriptor());
	    VM.sysWrite(".");
	    VM.sysWrite(method.getName()); // instead of method.toString()
	    VM.sysWrite(".\n");
	    VM_Scheduler.outputMutex.unlock();
	}

	if (CHECK_REF_MAPS) dumpStackFrame(method, fp);	// temporary - dump contents of frame

	// get stack map iterator

	int offset = ip - VM_Magic.objectAsAddress(compiledMethod.getInstructions());
	VM_GCMapIterator iterator = iteratorGroup.selectIterator(compiledMethod);
	iterator.setupIterator (compiledMethod, offset, fp);
	       
	// SCAN the map and process each reference in the stack

	int previousrefaddr = 0;
	int lastrefaddr = 0;

	if (CHECK_REF_MAPS) {
	    previousrefaddr = fp + VM_Compiler.getFirstLocalOffset(method) + 4;  
	    // bumped first, then chked
	    lastrefaddr = previousrefaddr - 
		((method.getLocalWords() + method.getOperandWords()) * 4);
	    displayTopStackLoop(method, previousrefaddr, iterator);
	}

	if (VM_Allocator.GC_STATISTICS) VM_Allocator.numberOfStackFramesProcessed++;

	int stack_ref_count = 0;

	for (int refaddr = iterator.getNextReferenceAddress();  refaddr != 0;
	         refaddr = iterator.getNextReferenceAddress()) {

	    // Debug and trace support

	    if (CHECK_REF_MAPS) {
		stack_ref_count++;
		displayRefInfo(refaddr);
		previousrefaddr = displayChkRef(previousrefaddr, refaddr);
	    }
		   
	    // If object pointer isn't null, add it to stack buffer

	    int object = VM_Magic.getMemoryWord(refaddr);

	    if (object != 0) {						
		if (VM.VerifyAssertions && ! FILTER_STACK_REFS) 
		    VM.assert(VM_Allocator.isPossibleRefOrMalloc(object));

		addToStackBuffer(object, t);		// can optimize by hoisting parts

		if (GC_TRACESCANSTACK) stack_ref_count++;
	    }
	}
	       
	if (CHECK_REF_MAPS) displayChkRefLast(previousrefaddr, lastrefaddr);

	return stack_ref_count;
    }


    static void copyStacks () {
	VM_Processor p = VM_Processor.getCurrentProcessor();
	int lastThread = VM_Thread.maxThreadIndex;

	for (int i = 0; i < lastThread; i++) {
	    VM_Thread t = VM_Scheduler.threads[i];

	    if (isLocalUserThread(t, p)) 
		copyStack(t, i);
	}
    }

    private static void copyStack (VM_Thread t, int id) {
	double startTime;
	if (false) startTime = VM_Time.now();

	// Compute base and extent of stack
	int[] myStack  = t.stack;
	int   myTop    = VM_Magic.objectAsAddress(myStack) + (myStack.length << 2);
	int   myFP     = t.contextRegisters.gprs[FRAME_POINTER];
	int   myDepth  = myTop - myFP;

	// Compute size required and allocate
	int   newSize  = myDepth >> 2;
	int[] newStack = new int[newSize];
	int   newFP    = VM_Magic.objectAsAddress(newStack);
	int   delta    = newFP - myFP;

	// Copy stack of running thread
	VM_Memory.copy(newFP, myFP, myDepth);

	// Save in thread's save area
	// t.savedStack      = newStack;
	// t.savedStackDelta = delta;

	if (false) {
	    double copyTime = VM_Time.now() - startTime;
	    VM.sysWrite("||||  Copy took ");	     VM.sysWrite((int)(copyTime*1000000.0), false);
	    VM.sysWrite(" usec for thread ");            VM.sysWrite(id, false);
	    VM.sysWrite("\n");
	}
    }

    //
    // DEBUG METHODS
    //

    private static void
    dumpStackFrame (VM_Method method, int fp) {
	// get callers frame pointer
	int location =  VM_Magic.getCallerFramePointer(fp);
	int maxOffset = VM_Compiler.getFirstLocalOffset(method);  // bumped first, then chked
	VM.sysWrite("scanStack: stack frame dump (caller_fp to fp) for method ");
	VM.sysWrite(method.toString());
	VM.sysWrite("\n");

	while ( location >= fp ) {
	    int contents = VM_Magic.getMemoryWord( location );
	    VM.sysWrite("   location ");
	    VM.sysWrite(location);
	    VM.sysWrite(" contents ");
	    VM.sysWrite(contents);
	    if (location == (fp + maxOffset))
		VM.sysWrite("    <----- First Local Offset ");
	    VM.sysWrite(".\n");
	    location = location - 4;
	}
    }

    private static void
    displayTopStackLoop (VM_Method method, int previousrefaddr, VM_GCMapIterator iterator ) {

	/****************
			 int paramwords = method.getParameterWords();
			 if (!method.isStatic()) paramwords++;

			 VM.sysWrite("parameterWords (including this) ");
			 VM.sysWrite(paramwords);
			 VM.sysWrite(" localWords ");
			 VM.sysWrite(method.getLocalWords());
			 if (iterator.getType() == VM_GCMapIterator.BASELINE) {
			 VM.sysWrite(" stack Depth ");
			 VM.sysWrite(((VM_BaselineGCMapIterator)iterator).getStackDepth());
			 }
			 VM.sysWrite(" starting address ");
			 VM.sysWrite(previousrefaddr-4);
			 VM.sysWrite("\n");

	************/
	return;
    }

    private static void
    displayRefInfo (int location) {
	int contents = VM_Magic.getMemoryWord(location);
	VM.sysWrite("scanStack: processing ref at location(hex) = ");
	VM.sysWrite(location);
	VM.sysWrite(" contents(hex) = ");
	VM.sysWrite(contents);
	VM.sysWrite(".\n");
    }

    private static int
    displayChkRef (int previousrefaddr, int refaddr) {
	previousrefaddr -= 4;
	while (previousrefaddr > refaddr) {
	    int contents = VM_Magic.getMemoryWord(previousrefaddr);
	    if (contents >= VM_Allocator.smallHeapStartAddress && contents <= VM_Allocator.largeHeapEndAddress ) {
		VM.sysWrite("scanStack: Possibly missed reference? location(hex) = ");
		VM.sysWrite(previousrefaddr);
		VM.sysWrite(" contents(hex) = ");
		VM.sysWrite(contents);
		VM.sysWrite(".\n");
		if (VM_Allocator.GC_STATISTICS) VM_Allocator.numberOfAmbiguousRefs++;
	    }
	    previousrefaddr -= 4;

	}
	return previousrefaddr;
    }

    private static void
    displayChkRefLast (int previousrefaddr, int lastrefaddr) {
	int contents;
	previousrefaddr -= 4;
	while (previousrefaddr >= lastrefaddr) {
	    contents = VM_Magic.getMemoryWord(previousrefaddr);
	    if (contents >= VM_Allocator.smallHeapStartAddress && contents <= VM_Allocator.largeHeapEndAddress ) {
		VM.sysWrite("scanStack: Possibly missed reference? location(hex) = ");
		VM.sysWrite(previousrefaddr);
		VM.sysWrite(" contents(hex) = ");
		VM.sysWrite(contents);
		VM.sysWrite(".\n");
		if (VM_Allocator.GC_STATISTICS) VM_Allocator.numberOfAmbiguousRefs++;
	    }
	    previousrefaddr -= 4;
	}
    }

    static void dumpBufferInfo(int bufptr, int object) {
	if (bufptr != 0)
	    dumpBuffer(bufptr);

	if (object != 0) {
	    VM.sysWrite("Object data:\n");
	    dumpObject(object);
	}

	if (bufptr != 0) {
	    VM.sysWrite("Type of previous object: "); VM_Allocator.printType(VM_Magic.getMemoryWord(bufptr-4));
	    VM.sysWrite("Type of next     object: "); VM_Allocator.printType(VM_Magic.getMemoryWord(bufptr+4));
	}
    }

    static void dumpBuffer(int address) {
	dump(address, 64);
    }

    static void dumpObject(int address) {
	int segment = address >> 28;
	if (segment < 2 || segment > 4) {
	    VM.sysWrite("Object in bad segment\n");
	    return;
	}	    

	dump(address, 64);
    }

    static void dump(int address, int delta) {
	for (int p = address - delta; p < address + delta; p += 4) {
	    if (p == address)
		VM.sysWrite("* ");
	    else
		VM.sysWrite("  ");

	    VM_Scheduler.writeHex(p);
	    VM.sysWrite(":  ");
	    VM_Scheduler.writeHex(VM_Magic.getMemoryWord(p));
	    VM.sysWrite("\n");
	}
	VM.sysWrite("\n");
    }
}
