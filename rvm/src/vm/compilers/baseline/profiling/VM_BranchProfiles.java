/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Profile data for all conditional branches (including switches)
 * of a single VM_Method.
 */
final class VM_BranchProfiles implements VM_BytecodeConstants {
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
	if (VM.VerifyAssertions) { VM.assert(false); }
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
    byte[] bytecodes = m.getBytecodes();
    int dataIdx = 0;
    int countIdx = 0;
    
    // We didn't record the bytecode index in the profile data to record space.
    // Therefore we must now recover that information.
    // We exploit the fact that the baseline compiler generates code in 
    // a linear pass over the bytecodes to make this possible.

    int bcIndex = 0;
    while (bcIndex < bytecodes.length) {
      int code = (int)(bytecodes[bcIndex] & 0xFF);
      switch (code) {
	
      case JBC_ifeq:case JBC_ifne:case JBC_iflt:case JBC_ifge:
      case JBC_ifgt:case JBC_ifle:case JBC_if_icmpeq:case JBC_if_icmpne:
      case JBC_if_icmplt:case JBC_if_icmpge:case JBC_if_icmpgt:
      case JBC_if_icmple:case JBC_if_acmpeq:case JBC_if_acmpne:
      case JBC_ifnull:case JBC_ifnonnull: {
	int yea = cs[countIdx + VM_EdgeCounts.TAKEN];
	int nea = cs[countIdx + VM_EdgeCounts.NOT_TAKEN];
	boolean backwards = ((bytecodes[bcIndex+1] & 0x80) != 0);
	countIdx += 2;
	data[dataIdx++] = new VM_ConditionalBranchProfile(bcIndex, yea, nea, backwards);
	bcIndex += 3;
	break;
      }

      case JBC_tableswitch: {
	int saved = bcIndex++;
	bcIndex = alignSwitch(bcIndex);
	bcIndex += 4;         // skip over default
	int low = getIntOffset(bcIndex, bytecodes);
	bcIndex += 4;
	int high = getIntOffset(bcIndex, bytecodes);
	bcIndex += 4;
	bcIndex += (high - low + 1)*4;        // skip over rest of tableswitch
	int numPairs = high - low + 1;
	data[dataIdx++] = new VM_SwitchBranchProfile(saved, cs, countIdx, numPairs+1);
	countIdx += numPairs + 1;
	break;
      }

      case JBC_lookupswitch: { 
	int saved = bcIndex++;
	bcIndex = alignSwitch(bcIndex);
	bcIndex += 4;         // skip over default 
	int numPairs = getIntOffset(bcIndex, bytecodes);
	bcIndex += 4 + (numPairs*8);          // skip rest of lookupswitch
	data[dataIdx++] = new VM_SwitchBranchProfile(saved, cs, countIdx, numPairs+1);
	countIdx += numPairs + 1;
	break;
      }

      case JBC_wide: {
	int w_code = (int)(bytecodes[bcIndex+1] & 0xFF);
	bcIndex += (w_code == JBC_iinc) ? 6 : 4;
	break;
      }

      default: { 
	int size = JBC_length[code];
	if (VM.VerifyAssertions) VM.assert(size > 0);
	bcIndex += size;
	break;
      }
      }
    }

    // Make sure we are in sync
    if (VM.VerifyAssertions) VM.assert(countIdx == cs.length); 

    if (dataIdx != data.length) {
      // We had a switch statment; shrink the array.
      VM_BranchProfile[] newData = new VM_BranchProfile[dataIdx];
      for (int i=0; i<dataIdx; i++) {
	newData[i] = data[i];
      }
      data = newData;
    }
  }

  private static int alignSwitch(int bcIndex) {
    int align = bcIndex & 3;
    if (align != 0) bcIndex += 4 - align;                     // eat padding
    return bcIndex;
  }

  private static int getIntOffset(int index, byte[] bytecodes) {
    return (int)((((int)bytecodes[index]) << 24) 
		 | ((((int)bytecodes[ index + 1]) & 0xFF) << 16) 
		 | ((((int)bytecodes[index + 2]) & 0xFF) << 8) 
		 | (((int)bytecodes[ index + 3]) & 0xFF));
  }

}
