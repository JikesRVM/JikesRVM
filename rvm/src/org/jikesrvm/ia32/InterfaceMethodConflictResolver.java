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
package org.jikesrvm.ia32;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.assembler.ia32.Assembler;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.unboxed.Offset;

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
 */
public abstract class InterfaceMethodConflictResolver implements RegisterConstants {

  // Create a conflict resolution stub for the set of interface method signatures l.
  //
  public static ArchitectureSpecific.CodeArray createStub(int[] sigIds, RVMMethod[] targets) {
    int numEntries = sigIds.length;
    // (1) Create an assembler.
    Assembler asm = new ArchitectureSpecific.Assembler(numEntries);

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

    return asm.getMachineCodes();
  }

  // Assign ascending bytecode indices to each case (in the order they will be generated)
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

  // Make a stub prologue: get TIB into ECX
  // factor out to reduce code space in each call.
  //
  private static void insertStubPrologue(Assembler asm) {
    ObjectModel.baselineEmitLoadTIB((ArchitectureSpecific.Assembler) asm, ECX.value(), EAX.value());
  }

  // Generate a subtree covering from low to high inclusive.
  private static void insertStubCase(Assembler asm, int[] sigIds, RVMMethod[] targets, int[] bcIndices, int low,
                                     int high) {
    int middle = (high + low) / 2;
    asm.resolveForwardReferences(bcIndices[middle]);
    if (low == middle && middle == high) {
      // a leaf case; can simply invoke the method directly.
      RVMMethod target = targets[middle];
      if (target.isStatic()) { // an error case...
        asm.emitJMP_Abs(Magic.getTocPointer().plus(target.getOffset()));
      } else {
        asm.emitJMP_RegDisp(ECX, target.getOffset());
      }
    } else {
      Offset disp = ArchEntrypoints.hiddenSignatureIdField.getOffset();
      ThreadLocalState.emitCompareFieldWithImm(asm, disp, sigIds[middle]);
      if (low < middle) {
        asm.emitJCC_Cond_Label(Assembler.LT, bcIndices[(low + middle - 1) / 2]);
      }
      if (middle < high) {
        asm.emitJCC_Cond_Label(Assembler.GT, bcIndices[(middle + 1 + high) / 2]);
      }
      // invoke the method for middle.
      RVMMethod target = targets[middle];
      if (target.isStatic()) { // an error case...
        asm.emitJMP_Abs(Magic.getTocPointer().plus(target.getOffset()));
      } else {
        asm.emitJMP_RegDisp(ECX, target.getOffset());
      }
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
