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
package org.jikesrvm.compilers.baseline;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.BytecodeConstants;
import org.jikesrvm.classloader.BytecodeStream;
import org.jikesrvm.classloader.NormalMethod;

/**
 * Profile data for all conditional branches (including switches)
 * of a single RVMMethod.
 */
public final class BranchProfiles implements BytecodeConstants {
  private final NormalMethod method;
  private final int numCounters;
  private final BranchProfile[] data;

  /**
   * Find the BranchProfile for a given bytecode index in the BranchProfile array
   * @param bcIndex the bytecode index of the branch instruction
   * @return the desired BranchProfile, or null if it cannot be found.
   */
  public BranchProfile getEntry(int bcIndex) {
    int low = 0;
    int high = data.length - 1;
    while (true) {
      int mid = (low + high) >> 1;
      int bci = data[mid].getBytecodeIndex();
      if (bci == bcIndex) {
        return data[mid];
      }
      if (low >= high) {
        // search failed
        if (VM.VerifyAssertions) { VM._assert(false); }
        return null;
      }
      if (bci > bcIndex) {
        high = mid - 1;
      } else {
        low = mid + 1;
      }
    }
  }

  public void print(java.io.PrintStream ps) {
    ps.println("M " + numCounters + " " + method.getMemberRef());
    for (BranchProfile profile : data) {
      ps.println("\t" + profile);
    }
  }

  BranchProfiles(NormalMethod m, int[] cs) {
    method = m;
    numCounters = cs.length;

    // Originally we only allocate half of the number of edges for branch
    // profiles, like data = new BranchProfile[cs.length/2]
    // The conditional branch, tableswitch and lookupswitch all have at
    // least two edges, supposingly. Then we found that the lookupswitch
    // bytecode could have only one edge, so the number of branch profiles
    // is not necessarily less than half of the number of edges.
    BranchProfile[] data = new BranchProfile[cs.length];
    BytecodeStream bcodes = m.getBytecodes();
    int dataIdx = 0;
    int countIdx = 0;

    // We didn't record the bytecode index in the profile data to record space.
    // Therefore we must now recover that information.
    // We exploit the fact that the baseline compiler generates code in
    // a linear pass over the bytecodes to make this possible.
    while (bcodes.hasMoreBytecodes()) {
      int bcIndex = bcodes.index();
      int code = bcodes.nextInstruction();

      switch (code) {
        case JBC_ifeq:
        case JBC_ifne:
        case JBC_iflt:
        case JBC_ifge:
        case JBC_ifgt:
        case JBC_ifle:
        case JBC_if_icmpeq:
        case JBC_if_icmpne:
        case JBC_if_icmplt:
        case JBC_if_icmpge:
        case JBC_if_icmpgt:
        case JBC_if_icmple:
        case JBC_if_acmpeq:
        case JBC_if_acmpne:
        case JBC_ifnull:
        case JBC_ifnonnull: {
          if (countIdx >= cs.length) {
            VM.sysWriteln("Warning: control flow instance encountered outside scope of available edge count array");
            break;
          }
          int yea = cs[countIdx + EdgeCounts.TAKEN];
          int nea = cs[countIdx + EdgeCounts.NOT_TAKEN];
          int offset = bcodes.getBranchOffset();
          boolean backwards = offset < 0;
          countIdx += 2;
          data[dataIdx++] = new ConditionalBranchProfile(bcIndex, yea, nea, backwards);
          break;
        }

        case JBC_tableswitch: {
          if (countIdx >= cs.length) {
            VM.sysWriteln("Warning: control flow instance encountered outside scope of available edge count array");
            break;
          }
          bcodes.alignSwitch();
          bcodes.getDefaultSwitchOffset();
          int low = bcodes.getLowSwitchValue();
          int high = bcodes.getHighSwitchValue();
          int n = high - low + 1;
          data[dataIdx++] = new SwitchBranchProfile(bcIndex, cs, countIdx, n + 1);
          countIdx += n + 1;
          bcodes.skipTableSwitchOffsets(n);
          break;
        }

        case JBC_lookupswitch: {
          if (countIdx >= cs.length) {
            VM.sysWriteln("Warning: control flow instance encountered outside scope of available edge count array");
            break;
          }
          bcodes.alignSwitch();
          bcodes.getDefaultSwitchOffset();
          int numPairs = bcodes.getSwitchLength();
          data[dataIdx++] = new SwitchBranchProfile(bcIndex, cs, countIdx, numPairs + 1);
          countIdx += numPairs + 1;
          bcodes.skipLookupSwitchPairs(numPairs);
          break;
        }

        default:
          bcodes.skipInstruction();
          break;
      }
    }

    // Make sure we are in sync
    if (VM.VerifyAssertions) VM._assert(countIdx == cs.length);

    if (dataIdx != data.length) {
      // We had a switch statment; shrink the array.
      BranchProfile[] newData = new BranchProfile[dataIdx];
      for (int i = 0; i < dataIdx; i++) {
        newData[i] = data[i];
      }
      data = newData;
    }
    this.data = data;
  }
}
