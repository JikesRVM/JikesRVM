/*
 * (C) Copyright IBM Corp. 2002
 */

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
     * Get an object's TIB pointer.  NOTE: Should be part of common ObjectModel code.
     *   @param object The object
     *   @return The object's TIB pointer
     */
    static Object[] getTIB (Object object) {
	VM_Magic.pragmaInline();
	//return (Object[]) VM_Magic.getObjectAtOffset(object, OBJECT_TIB_OFFSET);
	return (Object[]) VM_Magic.addressAsObject(VM_Magic.getMemoryWord(VM_Magic.objectAsAddress(object)+OBJECT_TIB_OFFSET));
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
}
