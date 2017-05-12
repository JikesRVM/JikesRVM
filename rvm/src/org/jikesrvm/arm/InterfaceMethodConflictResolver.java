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
package org.jikesrvm.arm;

import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.GT;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.LT;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.ALWAYS;
import static org.jikesrvm.arm.BaselineConstants.T0;
import static org.jikesrvm.arm.BaselineConstants.T1;
import static org.jikesrvm.arm.RegisterConstants.R12;
import static org.jikesrvm.arm.RegisterConstants.LG_INSTRUCTION_WIDTH;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CodeArray;
import org.jikesrvm.compilers.common.assembler.arm.Assembler;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;

/**
 * Generates a custom IMT-conflict resolution stub.
 * We create a binary search tree.
 */
public abstract class InterfaceMethodConflictResolver {


  /**
   * Create a conflict resolution stub for the set of interface method signatures with the same IMT offset.
   * The resulting stub is branched to when the interface method is called, so it has to preseve all registers except R12
   * The stub also receives the desired method's sigID as a "hidden" parameter in R12
   * And assumes that the method's "this" pointer has been loaded into T0
   *
   * @param sigIds An array of method sigID's in ascending order
   * @param targets An array of the matching methods
   * @return The generated code
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
    assignBytecodeIndices(0, bcIndices, 0, numEntries - 1); // Recursive call generates all of them
    int[] mcIndices = new int[numEntries]; // Machine code indices for the switches (initialised to zeros)

    // (4) Generate the stub.
    asm.emitPUSH(ALWAYS, T1);                                                      // Save T1
    asm.baselineEmitLoadTIB(T1, T0); // Load TIB into T1 (T0 contains "this" pointer)
    insertStubCase(asm, sigIds, targets, bcIndices, mcIndices, 0, numEntries - 1); // Recursive call generates all of them

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
      if (low < middle)
        bcIndex = assignBytecodeIndices(bcIndex, bcIndices, low, middle - 1);
      if (middle < high)
        bcIndex = assignBytecodeIndices(bcIndex, bcIndices, middle + 1, high);

      return bcIndex;
    }
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
  private static void insertStubCase(Assembler asm, int[] sigIds, RVMMethod[] targets, int[] bcIndices, int[] mcIndices, int low,
                                     int high) {
    int middle = (high + low) / 2;
    asm.resolveForwardReferences(bcIndices[middle]);
    mcIndices[middle] = asm.getMachineCodeIndex();
    if (low == middle && middle == high) {
      // a leaf case; can simply invoke the method directly.
      RVMMethod target = targets[middle];
      if (target.isStatic()) {
        //asm.generateOffsetLoad(ALWAYS, R12, JTOC, target.getOffset()); // TODO: can this ever happen?
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      } else {
        asm.generateOffsetLoad(ALWAYS, R12, T1, target.getOffset()); // T1 contains TIB
      }
      asm.emitPOP(ALWAYS, T1); // Restore T1
      asm.emitBX(ALWAYS, R12); // Branch to function we wanted to call
    } else {
      asm.emitCMPimm(ALWAYS, R12, sigIds[middle]);
      if (low < middle)
        asm.generateUnknownBranch(LT, mcIndices[(low + middle - 1) / 2], bcIndices[(low + middle - 1) / 2]);
      if (middle < high)
        asm.generateUnknownBranch(GT, mcIndices[(middle + 1 + high) / 2], bcIndices[(middle + 1 + high) / 2]);

      // invoke the method for middle.
      RVMMethod target = targets[middle];
      if (target.isStatic()) {
        //asm.generateOffsetLoad(ALWAYS, R12, JTOC, target.getOffset()); // TODO: can this ever happen?
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      } else {
        asm.generateOffsetLoad(ALWAYS, R12, T1, target.getOffset()); // T1 contains TIB
      }
      asm.emitPOP(ALWAYS, T1); // Restore T1
      asm.emitBX(ALWAYS, R12); // Branch to function we wanted to call

      // Recurse.
      if (low < middle)
        insertStubCase(asm, sigIds, targets, bcIndices, mcIndices, low, middle - 1);
      if (middle < high)
        insertStubCase(asm, sigIds, targets, bcIndices, mcIndices, middle + 1, high);
    }
  }
}
