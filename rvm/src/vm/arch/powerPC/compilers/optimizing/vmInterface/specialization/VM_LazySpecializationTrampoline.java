/*
 * (C) Copyright IBM Corp. 2001
 */

class VM_LazySpecializationTrampoline 
    implements VM_BaselineConstants, VM_DynamicBridge 
{
    /**
     *  The DynamicBridge protocol saves all registers on the stack,
     * so the local variables are below all the registers on the
     * stack.  The offsets are subtracted rather than added because
     * the stack grows downwards.  The `thisArgOffset' is the first
     * local because it is the first argument, and arguments are
     * (initially) put into the first local slots.  
     */
    private final static int thisArgOffset =  0
	- (LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1)*8  
	- (LAST_NONVOLATILE_GPR - FIRST_VOLATILE_GPR + 1)*4;

    static void lazySpecializationTrampoline() throws VM_ResolutionException {
	VM_Magic.pragmaNoInline();

	// get top of calling stack frame:
	// this is the method that performed the call we are handling
	int topOfCallerFrame =
	    VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());

	// stacks grow downward and thisArgOffset is negative,
	// so this is this frame's `this', just below register saves
	Object thisObject =
	    VM_Magic.addressAsObject(
	       VM_Magic.getMemoryWord(topOfCallerFrame + thisArgOffset));

	// this identifies which method called us
	int callingCompiledMethodId = 
	    VM_Magic.getCompiledMethodID(topOfCallerFrame);

	// this is which call instruction within that method called us
	int callingInstruction = 
	    VM_Magic.getNextInstructionAddress(topOfCallerFrame);

	//
	// A comment above notes that the dynamic bridge protocol
	// saves all the registers.  Actually, the dynamic bridge
	// protocol saves _almost_ all registers, not including register
	// 0.  So we cannot find our hidden parameter in the saved
	// registers.  Damn.  We extract the call site number from the
	// call site machine code instead.   The instruction before
	// the call instruction itself is the store of the hidden
	// argument, so we extract the offset by masking the immediate
	// from that instruction, which we know is an LIL.
	//
	// 0xffff is the bitmask needed to extract the constant from
	// an LIL instruction, and it is shifted over 2 to convert it
	// from a byte index as needed for an offset load into a logical
	// call site number.
	// 
	// If the store is one instruction before the call, why do we
	// subtract 8, which is two instructions?  Because the call
	// instruction, being obtained from the return address, is
	// really the instruction after the call. (I think)
	//
	int callSiteStore = VM_Magic.getMemoryWord(callingInstruction-8);
	int callSiteNumber = (callSiteStore & 0xffff)>>>2;

	// compiled method info of method that called us
	VM_CompiledMethod callingCompiledMethod = 
	    VM_ClassLoader.getCompiledMethod(callingCompiledMethodId);
	VM_CompilerInfo callingCompilerInfo =
	    callingCompiledMethod.getCompilerInfo();

	// call instruction address relative to beginning of code array
	int callingInstructionOffset = 
	    callingInstruction - 
	    VM_Magic.objectAsAddress(callingCompiledMethod.getInstructions());
      
	// obtain symbolic method reference
	VM_DynamicLink link = new VM_DynamicLink();
	callingCompilerInfo.getDynamicLink(link, callingInstructionOffset);
	VM_Method invokedMethod = link.methodRef();

	// get the class of the receiver object
	VM_Type invokedClass;
	if (invokedMethod.isStatic() || invokedMethod.isObjectInitializer())
	    invokedClass = invokedMethod.getDeclaringClass();
	else
	    invokedClass = VM_Magic.getObjectType(thisObject).asClass();

	if (! invokedMethod.getDeclaringClass().isInstantiated())
	    invokedMethod.getDeclaringClass().instantiate();

	// static method calls may be the first ``real'' use of a class,
	// in which case they trigger class initialization.
	if (! invokedMethod.getDeclaringClass().isInitialized())
	    invokedMethod.getDeclaringClass().initialize();
    
	if (OPT_SpecializationManager.DEBUG)
	    VM.sysWrite("called from " + callingCompiledMethod.getMethod() + "\n");
	
	if (callSiteNumber != 0) {
	    // find specialization info given class, method and call site
	    INSTRUCTION[] instructions =
		OPT_SpecializationManager.specializeAndInstall(invokedClass,
		     invokedMethod.getName(), 
		     invokedMethod.getDescriptor(),
		     callSiteNumber);
	    
	    // restore parameters and branch
	    VM_Magic.dynamicBridgeTo(instructions);           
	}

	else {
	    // a call site of 0 indicates a use of the generic method.

	    // if we get here, it means that we are making an unspecialized
	    // call to a method with specialized versions for which the
	    // generic code has not yet been compiled.  so compile it.

	    // first, find out where we are going...
	    if (link.isInvokeSpecial()) {
		invokedMethod = VM_Class.findSpecialMethod(invokedMethod);
	    } else if (link.isInvokeStatic()) { 
		// do nothing.
	    } else { // invokevirtual or invokeinterface
		invokedMethod = 
		    ((VM_Class)invokedClass).findVirtualMethod(
			  invokedMethod.getName(), 
			  invokedMethod.getDescriptor());
		if (invokedMethod == null)
		    throw new VM_ResolutionException(
		      invokedClass.getDescriptor(), 
		      new IncompatibleClassChangeError(
		       invokedClass.getDescriptor().classNameFromDescriptor()));
	    }
	    invokedMethod = invokedMethod.resolve();

	    // ...then, compile the appropriate method
	    VM_CompiledMethod code = 
		VM_RuntimeOptCompilerInfrastructure.
	            optCompileWithFallBack(invokedMethod);

	    VM_ClassLoader.setCompiledMethod(code.getId(), code);
	    
	    int tableIndex = 
		OPT_SpecializationManager.getJTOCoffset(
		    new OPT_ConcreteMethodKey(invokedClass, invokedMethod));

	    INSTRUCTION[][] table = (INSTRUCTION[][])
		VM_Statics.getSlotContentsAsObject( tableIndex );

	    table[0] = code.getInstructions();

	    VM_Magic.dynamicBridgeTo( table[0] );
	}	    
	 
	// does not return here
	if (VM.VerifyAssertions) VM.assert(NOT_REACHED);  
    }

    static private INSTRUCTION[] lazySpecializerInstructions = null;

    static public INSTRUCTION[] getLazySpecializerInstructions() {
	if (lazySpecializerInstructions == null) {
	    VM_Member member = 
		VM.getMember("LVM_LazySpecializationTrampoline;",
			     "lazySpecializationTrampoline", 
			     "()V");
	    
	    lazySpecializerInstructions = ((VM_Method)member).compile();
	}

	return lazySpecializerInstructions;
    }
}
