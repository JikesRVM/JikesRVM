/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * A repository of edge counters for bytecode-level edge conditional branches.
 * 
 * @author Dave Grove
 */
final class VM_EdgeCounts implements VM_Callbacks.ExitMonitor,
				     VM_BytecodeConstants {

  static final int TAKEN     = 0;
  static final int NOT_TAKEN = 1;
  
  private static int[][] counts;

  public void notifyExit(int value) { dumpCounts(); }

  /**
   * Allocate a counter array for the given method with numSlots entries.
   * Intended to be called only by the baseline compiler.
   * 
   * @param mid the method id 
   * @param numSlots the number of counters required for the method.
   */
  static synchronized void allocateCounters(int mid, int numSlots) {
    if (counts == null) {
      counts = new int[16000][];
      if (!VM.BuildForAdaptiveSystem) {
	// Assumption: If edge counters were enabled in a non-adaptive system
	//             then the user must want use to dump them when the system
	//             exits.  Otherwise there is no reason to do this.
	VM_Callbacks.addExitMonitor(new VM_EdgeCounts());
      }
    }
    if (mid >= counts.length) {
      int size = counts.length*2;
      if (size <= mid) size = mid + 1000;
      int[][]tmp = new int[size][];
      for (int i=0; i<counts.length; i++) {
	tmp[i] = counts[i];
      }
      counts = tmp;
    }
    counts[mid] = new int[numSlots];
  }

  /**
   * Dump all the profile data to stderr
   */
  public static void dumpCounts() {
    if (counts == null) return;
    VM.sysWrite("*** EDGE COUNTERS ***\n");
    int n = Math.min(counts.length, VM_MethodDictionary.getNumValues());
    for (int i=0; i<n; i++) {
      VM_Method m = VM_MethodDictionary.getValue(i);
      if (m == null) continue;
      VM_BranchProfile[] data = getBranchProfiles(m);
      if (data != null) {
	System.err.println(m.toString());
	for (int j=0; j<data.length; j++) {
	  System.err.println("\t"+data[j]);
	}
      }
    }
  }

  /**
   * Find the BranchProfile for a given bytecode index in the BranchProfile array
   * @param data the VM_BranchProfile[] to search
   * @param bcIndex the bytecode index of the branch instruction
   * @return the desired VM_BranchProfile, or null if it cannot be found.
   */
  public static VM_BranchProfile getBranchProfile(VM_BranchProfile[] data, int bcIndex) {
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

  /**
   * Get the branch profiles for the given VM_Method.
   * Return null if no profile information is available.
   */
  public static VM_BranchProfile[] getBranchProfiles(VM_Method m) {
    int id = m.getDictionaryId();
    if (counts == null || counts.length <= id) return null;
    int[] cs = counts[id];
    if (cs == null) return null;
    byte[] bytecodes = m.getBytecodes();
    VM_BranchProfile[] data = new VM_BranchProfile[cs.length/2];
    int dataIdx = 0;
    int countIdx = 0;
    
    // We didn't record the bytecode index in the profile data to record space.
    // Therefore we must now recover that information.
    // We exploitthe fact that the baseline compiler generates code in 
    // a linear pass over the bytecodes to do this.

    int bcIndex = 0;
    while (bcIndex < bytecodes.length) {
      int code = (int)(bytecodes[bcIndex] & 0xFF);
      switch (code) {
	
      case JBC_ifeq:case JBC_ifne:case JBC_iflt:case JBC_ifge:
      case JBC_ifgt:case JBC_ifle:case JBC_if_icmpeq:case JBC_if_icmpne:
      case JBC_if_icmplt:case JBC_if_icmpge:case JBC_if_icmpgt:
      case JBC_if_icmple:case JBC_if_acmpeq:case JBC_if_acmpne:
      case JBC_ifnull:case JBC_ifnonnull: {
	int yea = cs[countIdx + TAKEN];
	int nea = cs[countIdx + NOT_TAKEN];
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

    if (dataIdx != data.length) {
      // We had a switch statment; shrink the array.
      VM_BranchProfile[] newData = new VM_BranchProfile[dataIdx];
      for (int i=0; i<dataIdx; i++) {
	newData[i] = data[i];
      }
      return newData;
    } else {
      return data;
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
