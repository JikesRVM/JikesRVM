/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

/**
 * Generates a custom IMT-conflict resolution stub.
 * We create a binary search tree.
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 */
public class VM_InterfaceMethodConflictResolver implements VM_BaselineConstants,
                                                           VM_AssemblerConstants {

  // Create a conflict resolution stub for the set of interface method signatures l.
  // 
  public static VM_CodeArray createStub(int[] sigIds, VM_Method[] targets) {
    // (1) Create an assembler.
    int numEntries = sigIds.length;
    VM_Assembler asm = new VM_Assembler(numEntries); // pretend each entry is a bytecode

    // (2) signatures must be in ascending order (to build binary search tree).
    if (VM.VerifyAssertions) {
      for (int i=1; i<sigIds.length; i++) {
        VM._assert(sigIds[i-1] < sigIds[i]);
      }
    }

    // (3) Assign synthetic bytecode numbers to each switch such that we'll generate them
    // in ascending order.  This lets us use the general forward branching mechanisms
    // of the VM_Assembler.
    int[] bcIndices = new int[numEntries];
    assignBytecodeIndices(0, bcIndices, 0, numEntries -1);
    
    // (4) Generate the stub.
    insertStubPrologue(asm);
    insertStubCase(asm, sigIds, targets, bcIndices, 0, numEntries-1);
    
    VM_CodeArray stub = asm.makeMachineCode().getInstructions();

    // (5) synchronize icache with generated machine code that was written through dcache
    if (VM.runningVM)    
      VM_Memory.sync(VM_Magic.objectAsAddress(stub), stub.length() << LG_INSTRUCTION_WIDTH); 

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
                                     int[] sigIds, 
                                     VM_Method[] targets,
                                     int[] bcIndices, int low, int high) {
    int middle = (high + low)/2;
    asm.resolveForwardReferences(bcIndices[middle]);
    if (low == middle && middle == high) {
      // a leaf case; can simply invoke the method directly.
      VM_Method target = targets[middle];
      if (target.isStatic()) {
        // an error case.
        asm.emitLAddrToc(S0, target.getOffset());
      } else {
        asm.emitLAddr(S0, target.getOffset(), S0);
      }
      asm.emitMTCTR(S0);
      asm.emitBCCTR ();
    } else {
      asm.emitCMPI (S1, sigIds[middle]);
      if (low < middle) {
        asm.emitShortBC(LT, 0, bcIndices[(low+middle-1)/2]);
      }
      if (middle < high) {
        asm.emitShortBC(GT, 0, bcIndices[(middle+1+high)/2]);
      }
      // invoke the method for middle.
      VM_Method target = targets[middle];
      if (target.isStatic()) {
        // an error case.
        asm.emitLAddrToc(S0, target.getOffset());
      } else {
        asm.emitLAddr(S0, target.getOffset(), S0);
      }
      asm.emitMTCTR(S0);
      asm.emitBCCTR ();
      // Recurse.
      if (low < middle) {
        insertStubCase(asm, sigIds, targets, bcIndices, low, middle-1);
      } 
      if (middle < high) {
        insertStubCase(asm, sigIds, targets, bcIndices, middle+1, high);
      }
    }
  }
}
