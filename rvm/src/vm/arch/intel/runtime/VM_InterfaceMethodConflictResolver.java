/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$ 

/**
 * An interface conflict resolution stub uses a hidden parameter to
 * distinguish among multiple interface methods of a class that map to
 * the same slot in the class's IMT. </p>
 * 
 * <p><STRONG>Assumption:</STRONG>
 * Register EAX contains the "this" parameter of the
 * method being called invoked.
 *
 * <p><STRONG>Assumption:</STRONG>
 * Register ECX is available as a scratch register (we need one!)
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 */
class VM_InterfaceMethodConflictResolver implements VM_Constants {

  // Create a conflict resolution stub for the set of interface method signatures l.
  // 
  static INSTRUCTION[] createStub(VM_InterfaceMethodSignature.Link l, int numEntries) {
    // (1) Create an assembler.
    VM_Assembler asm = new VM_Assembler(numEntries*2); // wild guess on size of machine code
    
    // (2) Convert links into more array for easier handling.
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
    
    return asm.getMachineCodes();
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

  // Make a stub prologue: get TIB into ECX
  // factor out to reduce code space in each call.
  //
  private static void insertStubPrologue (VM_Assembler asm) {
    asm.emitMOV_Reg_RegDisp(ECX, EAX, OBJECT_TIB_OFFSET);
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
      asm.emitJMP_RegDisp(ECX, l.method.getOffset());
    } else {
      int disp = VM_Entrypoints.hiddenSignatureIdField.getOffset();
      VM_ProcessorLocalState.emitCompareFieldWithImm(asm, disp, l.signatureId);
      if (low < middle) {
	asm.emitJCC_Cond_Label(asm.LT, bcIndices[(low+middle-1)/2]);
      }
      if (middle < high) {
	asm.emitJCC_Cond_Label(asm.GT, bcIndices[(middle+1+high)/2]);
      }
      // invoke the method for middle.
      asm.emitJMP_RegDisp(ECX, l.method.getOffset());
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
