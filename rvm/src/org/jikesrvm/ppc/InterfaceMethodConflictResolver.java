/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.ppc;

import static org.jikesrvm.compilers.common.assembler.ppc.AssemblerConstants.GT;
import static org.jikesrvm.compilers.common.assembler.ppc.AssemblerConstants.LT;
import static org.jikesrvm.ppc.BaselineConstants.S0;
import static org.jikesrvm.ppc.BaselineConstants.S1;
import static org.jikesrvm.ppc.BaselineConstants.T0;
import static org.jikesrvm.ppc.RegisterConstants.LG_INSTRUCTION_WIDTH;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CodeArray;
import org.jikesrvm.compilers.common.assembler.ppc.Assembler;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;

/**
 * Generates a custom IMT-conflict resolution stub.
 * We create a binary search tree.
 */
public abstract class InterfaceMethodConflictResolver {


  /**
   * Creates a conflict resolution stub for the set of interface method signatures {@code l}.
   * @param sigIds ids of elements in {@code l}
   * @param targets target methods of elements in {@code l}
   * @return code that implements the stub
   */
  public static CodeArray createStub(int[] sigIds, RVMMethod[] targets) {
    // (1) Create an assembler.
    int numEntries = sigIds.length;
    Assembler asm = new Assembler(numEntries); // pretend each entry is a bytecode

    // (2) signatures must be in ascending order (to build binary search tree).
    if (VM.VerifyAssertions) {
      for (int i = 1; i < sigIds.length; i++) {
        VM._assert(sigIds[i - 1] < sigIds[i]);
      }
    }

    // (3) Assign synthetic bytecode numbers to each switch such that we'll generate them
    // in ascending order.  This lets us use the general forward branching mechanisms
    // of the Assembler.
    int[] bcIndices = new int[numEntries];
    assignBytecodeIndices(0, bcIndices, 0, numEntries - 1);

    // (4) Generate the stub.
    insertStubPrologue(asm);
    insertStubCase(asm, sigIds, targets, bcIndices, 0, numEntries - 1);

    CodeArray stub = asm.getMachineCodes();

    // (5) synchronize icache with generated machine code that was written through dcache
    if (VM.runningVM) Memory.sync(Magic.objectAsAddress(stub), stub.length() << LG_INSTRUCTION_WIDTH);

    return stub;
  }

  /**
   * Assign ascending bytecode indices to each case (in the order they will be generated)
   * @param bcIndex byte code index for the case in the search tree that will have an index assigned now
   * @param bcIndices array of byte code indices. All entries in the array are zero at the start
   *  and will be filled in as the generation progresses.
   * @param low lower bound of the cases to have an index assigned
   * @param high upper bound of the cases to have an index assigned
   * @return byte code index to use for the next case in the search tree
   */
  private static int assignBytecodeIndices(int bcIndex, int[] bcIndices, int low, int high) {
    int middle = (high + low) / 2;
    bcIndices[middle] = bcIndex++;
    if (low == middle && middle == high) {
      return bcIndex;
    } else {
      // Recurse.
      if (low < middle) {
        bcIndex = assignBytecodeIndices(bcIndex, bcIndices, low, middle - 1);
      }
      if (middle < high) {
        bcIndex = assignBytecodeIndices(bcIndex, bcIndices, middle + 1, high);
      }
      return bcIndex;
    }
  }

  /**
   * Make a stub prologue: get TIB into S0 factor out to reduce code space in each call.
   *
   * @param asm assembler that's used for insertion of stub prologue
   */
  private static void insertStubPrologue(Assembler asm) {
    asm.baselineEmitLoadTIB(S0, T0);
  }

  /**
   * Generates a subtree covering from {@code low} to {@code high} inclusive.
   * @param asm assembler to use for generation
   * @param sigIds ids of all the InterfaceMethodSignature instances
   * @param targets  targets of all the InterfaceMethodSignature instances
   * @param bcIndices  array of byte code indices (already filled out)
   * @param low lower bound of the cases to be generated
   * @param high upper bound of the cases to be generated
   */
  private static void insertStubCase(Assembler asm, int[] sigIds, RVMMethod[] targets, int[] bcIndices, int low,
                                     int high) {
    int middle = (high + low) / 2;
    asm.resolveForwardReferences(bcIndices[middle]);
    if (low == middle && middle == high) {
      // a leaf case; can simply invoke the method directly.
      RVMMethod target = targets[middle];
      if (target.isStatic()) {
        // an error case.
        asm.emitLAddrToc(S0, target.getOffset());
      } else {
        asm.emitLAddrOffset(S0, S0, target.getOffset());
      }
      asm.emitMTCTR(S0);
      asm.emitBCCTR();
    } else {
      asm.emitCMPI(S1, sigIds[middle]);
      if (low < middle) {
        asm.emitShortBC(LT, 0, bcIndices[(low + middle - 1) / 2]);
      }
      if (middle < high) {
        asm.emitShortBC(GT, 0, bcIndices[(middle + 1 + high) / 2]);
      }
      // invoke the method for middle.
      RVMMethod target = targets[middle];
      if (target.isStatic()) {
        // an error case.
        asm.emitLAddrToc(S0, target.getOffset());
      } else {
        asm.emitLAddrOffset(S0, S0, target.getOffset());
      }
      asm.emitMTCTR(S0);
      asm.emitBCCTR();
      // Recurse.
      if (low < middle) {
        insertStubCase(asm, sigIds, targets, bcIndices, low, middle - 1);
      }
      if (middle < high) {
        insertStubCase(asm, sigIds, targets, bcIndices, middle + 1, high);
      }
    }
  }
}
