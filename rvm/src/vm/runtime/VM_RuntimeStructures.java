/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

/**
 * Abstraction for runtime structures that are simple arrays.  Funneling all 
 * allocation through these routines allows the necessary special handling
 * for unusual array layouts.
 *
 * @author David F. Bacon
 */
class VM_RuntimeStructures implements VM_Constants {

    /**
     * Allocate an array of instructions
     * @param n The number of instructions to allocate
     * @return The instruction array
     */ 
    static INSTRUCTION[] newInstructions (int n) {
	VM_Magic.pragmaInline();

	if (VM.BuildForRealtimeGC) {
	    //-#if RVM_WITH_REALTIME_GC
	    return VM_SegmentedArray.newInstructions(n);
	    //-#endif
	}
	
	return new INSTRUCTION[n];
    }


    /**
     * Allocate a stack array
     * @param n The number of stack slots to allocate
     * @return The stack array
     */ 
    static int[] newStack (int n) {
	VM_Magic.pragmaInline();

	if (VM.BuildForRealtimeGC) {
	    //-#if RVM_WITH_REALTIME_GC
	    return VM_SegmentedArray.newStack(n);
	    //-#endif
	}
	
	return new int[n];
    }


    /**
     * Allocate a stack array that will live forever and does not move
     * @param n The number of stack slots to allocate
     * @return The stack array
     */ 
    static int[] newImmortalStack (int n) {

	if (VM.runningVM) {
	    int[] stack = (int[]) VM_Allocator.immortalHeap.allocateAlignedArray(VM_Array.arrayOfIntType, n, 4096);
	    return stack;
	}
	
	return new int[n];
    }

    /**
     * Allocate a contiguous int array
     * @param n The number of ints
     * @return The contiguous int array
     */ 
    static int[] newContiguousIntArray (int n) {
	VM_Magic.pragmaInline();

	if (VM.BuildForRealtimeGC) {
	    //-#if RVM_WITH_REALTIME_GC
	    return VM_SegmentedArray.newIntArray(n);
	    //-#endif
	}
	
	return new int[n];
    }

    /**
     * Allocate a contiguous VM_CompiledMethod array
     * @param n The number of objects
     * @return The contiguous object array
     */ 
    static VM_CompiledMethod[] newContiguousCompiledMethodArray (int n) {
	VM_Magic.pragmaInline();

	if (VM.BuildForRealtimeGC) {
	    //-#if RVM_WITH_REALTIME_GC
	    return VM_SegmentedArray.newContiguousCompiledMethodArray(n);
	    //-#endif
	}
	
	return new VM_CompiledMethod[n];
    }

    /**
     * Allocate a contiguous VM_DynamicLibrary array
     * @param n The number of objects
     * @return The contiguous object array
     */ 
    static VM_DynamicLibrary[] newContiguousDynamicLibraryArray (int n) {
	VM_Magic.pragmaInline();

	if (VM.BuildForRealtimeGC) {
	    //-#if RVM_WITH_REALTIME_GC
	    return VM_SegmentedArray.newContiguousDynamicLibraryArray(n);
	    //-#endif
	}
	
	return new VM_DynamicLibrary[n];
    }


    static Object[] newTIB (int n) {
	VM_Magic.pragmaInline();

	if (true) {
	    //-#if RVM_WITH_COPYING_GC
	    //-#if RVM_WITH_ONE_WORD_MASK_OBJECT_MODEL
	    return VM_Allocator.newTIB(n);
	    //-#endif
	    //-#endif
	}
	
	return new Object[n];
    }

}
