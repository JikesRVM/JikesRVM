/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author David Bacon
 */
class VM_RCBarriers implements VM_BaselineConstants {

    static final boolean DONT_BARRIER_BLOCK_CONTROLS = true;

    private static final boolean TRACE_DYNAMIC_BARRIERS = false;

    private static void emitBufferStores (VM_Assembler asm, int spSaveAreaOffset, int Told, int Tnew, int Ttmp) {
	// Get pointer to mutation buffer
	asm.emitL    (Ttmp, VM_Entrypoints.incDecBufferTopOffset, PROCESSOR_REGISTER);
	// If increment value is non-null, store in buffer 
	asm.emitCMPI (Tnew, 0);
	asm.emitBEQ  (+2);
	asm.emitSTU  (Tnew, 4, Ttmp);
	// If decrement value is non-null, set low bit (indicating it's a decrement) and store in buffer
	asm.emitCMPI (Told, 0);
	asm.emitBEQ  (+3);
	asm.emitCAL  (Told, VM_RCBuffers.DECREMENT_FLAG, Told);
	asm.emitSTU  (Told, 4, Ttmp);
	// Store updated mutation buffer pointer
	asm.emitST   (Ttmp, VM_Entrypoints.incDecBufferTopOffset, PROCESSOR_REGISTER);

	// Check for mutation buffer overflow
	asm.emitL    (Told, VM_Entrypoints.incDecBufferMaxOffset, PROCESSOR_REGISTER);
	asm.emitCMP  (Ttmp, Told);
	asm.emitBLE  (VM_Assembler.CALL_INSTRUCTIONS + 3);
	// Buffer overflowed; call function to expand it.
	asm.emitL    (S0, VM_Entrypoints.processIncDecBufferOffset, JTOC);
	asm.emitMTLR (S0);
	asm.emitCall (spSaveAreaOffset);
    }


    static void compileArrayStoreBarrier (VM_Assembler asm, int spSaveAreaOffset) {
	//  On entry: T1 is array reference
	//            T0 is array offset
	//            T3 is value to store
	//            T2 is free

	asm.emitLWARX(T2, T0, T1);				// Load old value into T2
	asm.emitSTWCXr(T3, T0, T1);				// Atomically replace with new value (T3)
	asm.emitBNE(-2);					// Retry if reservation lost

	emitBufferStores(asm, spSaveAreaOffset, T2, T3, T0);	// T2 = old, T3 = new, T0 = temp
    }


    static void compilePutfieldBarrier (VM_Assembler asm, int spSaveAreaOffset, int fieldOffset, VM_Method method, VM_Field field) {
	// On entry: T0 = ref to store
	//           T1 = target object

	if (DONT_BARRIER_BLOCK_CONTROLS) {
	    String className = field.getDeclaringClass().getDescriptor().toString();
	    if (className.equals("LVM_BlockControl;")) {
		VM.sysWrite("Omitting barrier for method " + method + " field " + field + "\n");
		asm.emitST(T0, fieldOffset, T1);
		return;
	    }
	}

	asm.emitCAL(T1, fieldOffset, T1);			// T1 = pointer to slot

	asm.emitLWARX(T2, 0, T1);				// T2 = old ref
	asm.emitSTWCXr(T0, 0, T1);				// Atomically replace with new ref (T0)
	asm.emitBNE(-2);					// Retry if reservation lost

	emitBufferStores(asm, spSaveAreaOffset, T2, T0, T1);	// T2 = old, T0 = new, T1 = temp
    }

    static void compilePutstaticBarrier (VM_Assembler asm, int spSaveAreaOffset, int jtocOffset) {
	// On entry: T0 = new ref value to store

	asm.emitCALtoc(T1, jtocOffset);			// T1 = offset of field within JTOC

	asm.emitLWARX(T2, 0, T1);
	asm.emitSTWCXr(T0, 0, T1);
	asm.emitBNE(-2);

	emitBufferStores(asm, spSaveAreaOffset, T2, T0, T1);	// T2 = old, T0 = new, T1 = temp
    }

    static void
    compileDynamicPutfieldBarrier2 (VM_Assembler asm, int spSaveAreaOffset, VM_Method method, VM_Field field) {
	// See VM_Linker.linkPutfield for information on how the code is dynamically linked.
	// On entry: T0 = new value
	//           T1 = target base address
	//           T2 = target field offset

	if (DONT_BARRIER_BLOCK_CONTROLS) {
	    String className = field.getDeclaringClass().getDescriptor().toString();
	    if (className.equals("LVM_BlockControl;")) {
		VM.sysWrite("Omitting dynamic barrier for method " + method + " field " + field + "\n");
		asm.emitSTX(T0, T2, T1);
		return;
	    }
	}

	if (TRACE_DYNAMIC_BARRIERS)
	    VM.sysWrite(" REFCOUNTING for putfield - dynamic link from " + method + " to " + field + "\n");

	asm.emitLWARX(T3, T2, T1);				// T2 = old ref
	asm.emitSTWCXr(T0, T2, T1);				// Atomically replace with new ref (T0)
	asm.emitBNE(-2);					// Retry if reservation lost

	emitBufferStores(asm, spSaveAreaOffset, T3, T0, T1);	// T2 = old, T0 = new, T1 = temp
    }

    static void
    compileDynamicPutstaticBarrier2 (VM_Assembler asm, int spSaveAreaOffset, VM_Method method, VM_Field field) {
	// See VM_Linker.linkPutstatic for information on how the code is dynamically linked.
	// On entry: T0 = new value
	//           T2 = JTOC offset

	if (true || TRACE_DYNAMIC_BARRIERS)
	    VM.sysWrite(" REFCOUNTING for putstatic - dynamic link from " + method + " to " + field + "\n");

	asm.emitLWARX (T3, T2, JTOC);				// T3 = old ref
	asm.emitSTWCXr(T0, T2, JTOC);				// Atomically replace with new ref (T0)
	asm.emitBNE(-2);					// Retry if reservation lost

	emitBufferStores(asm, spSaveAreaOffset, T3, T0, T1);	// T3 = old, T0 = new, T1 = temp
    }

}
