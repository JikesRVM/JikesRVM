/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
/**
 * Profile data for all conditional branches (including switches)
 * of a single VM_Method.
 *
 * @author Dave Grove
 */
public final class VM_BranchProfiles implements VM_BytecodeConstants {
  private VM_Method method;
  private int counterId;
  private VM_BranchProfile[] data;

  /**
   * Find the BranchProfile for a given bytecode index in the BranchProfile array
   * @param bcIndex the bytecode index of the branch instruction
   * @return the desired VM_BranchProfile, or null if it cannot be found.
   */
  public VM_BranchProfile getEntry(int bcIndex) {
    int low = 0;
    int high = data.length-1;
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
	high = mid-1;
      } else {
	low = mid+1;
      }      
    }
  }

  public void print(java.io.PrintStream ps) {
    ps.println("M "+VM_EdgeCounterDictionary.getValue(counterId).length+" "+
	       VM_EdgeCounterDictionary.getKey(counterId));
    for (int j=0; j<data.length; j++) {
      ps.println("\t"+data[j]);
    }
  }

  VM_BranchProfiles(VM_Method m, int id, int[] cs) {
    method = m;
    counterId = id;
    data = new VM_BranchProfile[cs.length/2];
    VM_BytecodeStream bcodes = m.getBytecodes();
    int dataIdx = 0;
    int countIdx = 0;
    
    // We didn't record the bytecode index in the profile data to record space.
    // Therefore we must now recover that information.
    // We exploit the fact that the baseline compiler generates code in 
    // a linear pass over the bytecodes to make this possible.

    //-#if RVM_WITH_OSR
    /* In the presence of PSEUDO bytecodes, the bcIndex has to be adjusted
     * to skip over the prologue. Also, because pesudo bytecodes happen only
     * in prologue, no worry about that when parsing the original code.
     */	
    int bciAdjustment = 0;
    if (m.isForSpecialization()) {
      bcodes.reset(m.getOsrPrologueLength());
      bciAdjustment = m.getOsrPrologueLength();
    }
    //-#endif 

    while(bcodes.hasMoreBytecodes()) {
      int bcIndex = bcodes.index();
      int code = bcodes.nextInstruction();
      switch (code) {
      case JBC_ifeq:case JBC_ifne:case JBC_iflt:case JBC_ifge:
      case JBC_ifgt:case JBC_ifle:case JBC_if_icmpeq:case JBC_if_icmpne:
      case JBC_if_icmplt:case JBC_if_icmpge:case JBC_if_icmpgt:
      case JBC_if_icmple:case JBC_if_acmpeq:case JBC_if_acmpne:
      case JBC_ifnull:case JBC_ifnonnull: {
	int yea = cs[countIdx + VM_EdgeCounts.TAKEN];
	int nea = cs[countIdx + VM_EdgeCounts.NOT_TAKEN];
	int offset = bcodes.getBranchOffset();
	boolean backwards = offset < 0;
	countIdx += 2;
//-#if RVM_WITH_OSR
        // with pseudo instruction, adjust bcIndex
	data[dataIdx++] = new VM_ConditionalBranchProfile(bcIndex - bciAdjustment, yea, nea, backwards);
//-#else
	data[dataIdx++] = new VM_ConditionalBranchProfile(bcIndex, yea, nea, backwards);
//-#endif
	break;
      }

      case JBC_tableswitch: {
	bcodes.alignSwitch();
	int def = bcodes.getDefaultSwitchOffset();
	int low = bcodes.getLowSwitchValue();
	int high = bcodes.getHighSwitchValue();
	int n = high - low + 1;
//-#if RVM_WITH_OSR
	data[dataIdx++] = new VM_SwitchBranchProfile(bcIndex - bciAdjustment, cs, countIdx, n+1);         
//-#else
	data[dataIdx++] = new VM_SwitchBranchProfile(bcIndex, cs, countIdx, n+1);
//-#endif
	countIdx += n + 1;
	bcodes.skipTableSwitchOffsets(n);
	break;
      }

      case JBC_lookupswitch: { 
	bcodes.alignSwitch();
	int def = bcodes.getDefaultSwitchOffset();
	int numPairs = bcodes.getSwitchLength();
//-#if RVM_WITH_OSR
	data[dataIdx++] = new VM_SwitchBranchProfile(bcIndex-bciAdjustment, cs, countIdx, numPairs+1);
//-#else
	data[dataIdx++] = new VM_SwitchBranchProfile(bcIndex, cs, countIdx, numPairs+1);
//-#endif
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
      VM_BranchProfile[] newData = new VM_BranchProfile[dataIdx];
      for (int i=0; i<dataIdx; i++) {
	newData[i] = data[i];
      }
      data = newData;
    }
  }
}
