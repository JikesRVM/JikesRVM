/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Generates a custom IMT-conflict resolution stub.
 * We create a binary search tree.
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 */
class VM_InterfaceMethodConflictResolver implements VM_BaselineConstants {

  // Create a conflict resolution stub for the set of interface method signatures l.
  // 
  static INSTRUCTION[] createStub(VM_InterfaceMethodSignature.Link l, int numEntries) {
    // (1) Create an assembler.
    VM_Assembler asm = new VM_Assembler(numEntries); // pretend each entry is a bytecode

    // (2) Convert links into array for easier handling.
    VM_InterfaceMethodSignature.Link[] entries = new VM_InterfaceMethodSignature.Link[numEntries];
    for (int i=0; i<entries.length; i++) {
      entries[i] = l;
      l = l.next;
    }
    if (VM.VerifyAssertions) {
      for (int i=1; i<entries.length; i++) {
	VM.assert(entries[i-1].signatureId < entries[i].signatureId);
      }
    }

    // (3) Assign synthetic bytecode numbers to each switch such that we'll generate them
    // in ascending order.  This lets us use the general forward branching mechanisms
    // of the VM_Assembler.
    int[] bcIndices = new int[numEntries];
    assignBytecodeIndices(0, bcIndices, 0, numEntries -1);
    
    // (4) Generate the stub.
    insertStubPrologue(asm);
    insertStubCase(asm, entries, bcIndices, 0, numEntries-1);
    
    INSTRUCTION[] stub = asm.makeMachineCode().getInstructions();

    // (5) synchronize icache with generated machine code that was written through dcache
    if (VM.runningVM)    
      VM_Memory.sync(VM_Magic.objectAsAddress(stub), stub.length << LG_INSTRUCTION_WIDTH); 

    return stub;
  }

  // Assign ascending bytecode indices to each case (in the order they will be generated)
  private static int assignBytecodeIndices(int bcIndex, int[] bcIndices, int low, int high) {
    int middle = (high + low)/2;
    bcIndices[middle] = bcIndex++;
    if (low == middle && middle == high) {
      return bcIndex;
    } else {
      // Recurse.
      if (low < middle) {
	bcIndex = assignBytecodeIndices(bcIndex, bcIndices, low, middle-1);
      } 
      if (middle < high) {
	bcIndex = assignBytecodeIndices(bcIndex, bcIndices, middle+1, high);
      }
      return bcIndex;
    }
  }

  // Make a stub prologue: get TIB into S0
  // factor out to reduce code space in each call.
  //
  private static void insertStubPrologue (VM_Assembler asm) {
    VM_ObjectModel.baselineEmitLoadTIB(asm, S0, T0);
  }

  // Generate a subtree covering from low to high inclusive.
  private static void insertStubCase(VM_Assembler asm,  
				     VM_InterfaceMethodSignature.Link[] entries,
				     int[] bcIndices, int low, int high) {
    int middle = (high + low)/2;
    VM_InterfaceMethodSignature.Link l = entries[middle];
    asm.resolveForwardReferences(bcIndices[middle]);
    if (low == middle && middle == high) {
      // a leaf case; can simply invoke the method directly.
      asm.emitL    (S0, l.method.getOffset(), S0);
      asm.emitMTCTR(S0);
      asm.emitBCTR ();
    } else {
      asm.emitCMPI (SP, l.signatureId);
      if (low < middle) {
	asm.reserveShortForwardConditionalBranch(bcIndices[(low+middle-1)/2]);
	asm.emitBLT(0);
      }
      if (middle < high) {
	asm.reserveShortForwardConditionalBranch(bcIndices[(middle+1+high)/2]);
	asm.emitBGT(0);
      }
      // invoke the method for middle.
      asm.emitL    (S0, l.method.getOffset(), S0);
      asm.emitMTCTR(S0);
      asm.emitBCTR ();
      // Recurse.
      if (low < middle) {
	insertStubCase(asm, entries, bcIndices, low, middle-1);
      } 
      if (middle < high) {
	insertStubCase(asm, entries, bcIndices, middle+1, high);
      }
    }
  }
}
