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

  private static final int prologueLength = 1; // instructions
  private static final int leafLength     = 3; // instructions
  
  private static final boolean countConflicts = false; 
          static       int     conflictCount;
          static       int     conflictCountOffset;
  private static final int     countLength = 4; // instructions


  // Create a conflict resolution stub for the set of interface method signatures l.
  // 
  static INSTRUCTION[] createStub(VM_InterfaceMethodSignature.Link l, int numEntries) {
    // (1) Determine how big the stub needs to be and allocate it.
    int stubLength = prologueLength + computeSpaceNeeds(0, numEntries-1);
    if (countConflicts) {
      stubLength += countLength;
    }
    INSTRUCTION[] stub = new INSTRUCTION[stubLength];

    // (2) Convert links into more array for easier handling.
    VM_InterfaceMethodSignature.Link[] entries = new VM_InterfaceMethodSignature.Link[numEntries];
    int[] branchSources = new int[numEntries];
    int[] branchTargets = new int[numEntries];
    for (int i=0; i<entries.length; i++) {
      entries[i] = l;
      l = l.next;
    }
    if (VM.VerifyAssertions) {
      for (int i=1; i<entries.length; i++) {
	VM.assert(entries[i-1].signatureId < entries[i].signatureId);
      }
    }

    // (3) Generate the stub.
    int stubIndex = 0;
    if (countConflicts)
      stubIndex = insertStubCounter(stub, stubIndex);
    stubIndex = insertStubPrologue(stub, stubIndex);
    stubIndex = insertStubCase(stub, stubIndex, entries, branchSources, branchTargets, 0, numEntries-1);

    // (4) backpatch the forward branches.
    patchBranches(stub, branchSources, branchTargets);

    // (5) synchronize icache with generated machine code that was written through dcache
    if (VM.runningVM)    
      VM_Memory.sync(VM_Magic.objectAsAddress(stub), stub.length << LG_INSTRUCTION_WIDTH); 

    return stub;
  }


  // Compute how many instructions will be in the search tree covering low to high
  private static int computeSpaceNeeds(int low, int high) {
    int middle = (high + low)/2;
    if (low == middle && middle == high) {
      return leafLength;
    } else {
      int size = 1; // cmp
      if (low < middle) {
	size += 1; // branch
      }
      if (middle < high) {
	size += 1; // branch
      }
      size += leafLength;
      // Recurse.
      if (low < middle) {
	size += computeSpaceNeeds(low, middle-1);
      } 
      if (middle < high) {
	size += computeSpaceNeeds(middle+1, high);
      }
      return size;
    }
  }

  // Insert code to increment a counter each time the stub is invoked
  //
  private static int insertStubCounter (INSTRUCTION[] stub, int i) {
    if (conflictCountOffset == 0)
      conflictCountOffset = VM.getMember("LVM_InterfaceMethodConflictResolver;", "conflictCount", "I").getOffset();
    if (0 == (conflictCountOffset&0x8000)) {
      stub[i++] = VM_Assembler.CAU(S0, JTOC, conflictCountOffset>>16);
      stub[i++] = VM_Assembler.L  ( 0, conflictCountOffset&0xFFFF, S0);
      stub[i++] = VM_Assembler.AIr( 0, 0, 1);
      stub[i++] = VM_Assembler.ST ( 0, conflictCountOffset&0xFFFF, S0);
    } else {
      stub[i++] = VM_Assembler.CAU(S0, JTOC, (conflictCountOffset>>16)+1);
      stub[i++] = VM_Assembler.L  ( 0, conflictCountOffset|0xFFFF0000, S0);
      stub[i++] = VM_Assembler.AIr( 0, 0, 1);
      stub[i++] = VM_Assembler.ST ( 0, conflictCountOffset|0xFFFF0000, S0);
    }
    return i;
  }

  // Make a stub prologue: get TIB
  //
  private static int insertStubPrologue (INSTRUCTION[] stub, int i) {
    stub[i++] = VM_Assembler.L(S0, OBJECT_TIB_OFFSET, T0);
    return i;
  }

  // Generate a subtree covering from low to high inclusive.
  private static int insertStubCase(INSTRUCTION[] stub, int i, 
				    VM_InterfaceMethodSignature.Link[] entries,
				    int[] branchSources, int[] branchTargets,
				    int low, int high) {
    int middle = (high + low)/2;
    VM_InterfaceMethodSignature.Link l = entries[middle];
    branchTargets[middle] = i;
    if (low == middle && middle == high) {
      // a leaf case; can simply invoke the method directly.
      stub[i++] = VM_Assembler.L    (S0, l.method.getOffset(), S0);
      stub[i++] = VM_Assembler.MTCTR(S0);
      stub[i++] = VM_Assembler.BCTR ();
    } else {
      stub[i++] = VM_Assembler.CMPI (SP, l.signatureId);
      if (low < middle) {
	branchSources[(low + middle-1)/2] = i;
	stub[i++] = VM_Assembler.BLT (0); // will patch later
      }
      if (middle < high) {
	branchSources[(middle+1+high)/2] = i;
	stub[i++] = VM_Assembler.BGT (0); // will patch later
      }
      // invoke the method for middle.
      stub[i++] = VM_Assembler.L    (S0, l.method.getOffset(), S0);
      stub[i++] = VM_Assembler.MTCTR(S0);
      stub[i++] = VM_Assembler.BCTR ();
      
      // Recurse.
      if (low < middle) {
	i = insertStubCase(stub, i, entries, branchSources, branchTargets, low, middle-1);
      } 
      if (middle < high) {
	i = insertStubCase(stub, i, entries, branchSources, branchTargets, middle+1, high);
      }
    }
    return i;
  }

  // Fill in the branch offsets.
  private static void patchBranches(INSTRUCTION[] stub, int[] branchSources, int[] branchTargets) {
    for (int i = 0; i< branchSources.length; i++) {
      if (branchSources[i] != 0) {
	int branchIdx = branchSources[i];
	int branchTgt = branchTargets[i];
	if (VM.VerifyAssertions) VM.assert(branchTgt > branchIdx);
	int delta = branchTgt - branchIdx;
	stub[branchIdx] |= (delta << 2);
      }
    }
  }
	
}
