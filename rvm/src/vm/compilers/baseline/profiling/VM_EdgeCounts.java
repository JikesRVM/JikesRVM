/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.*;
import java.io.*;

/**
 * A repository of edge counters for bytecode-level edge conditional branches.
 * 
 * @author Dave Grove
 */
final class VM_EdgeCounts implements VM_Callbacks.ExitMonitor {

  static final int TAKEN     = 0;
  static final int NOT_TAKEN = 1;

  private static boolean registered = false;
  
  public void notifyExit(int value) { dumpCounts(); }

  public static int findOrCreateId(VM_Method m) {
    if (!VM.BuildForAdaptiveSystem && !registered) {
      // Assumption: If edge counters were enabled in a non-adaptive system
      //             then the user must want use to dump them when the system
      //             exits.  Otherwise why would they have enabled them...
      registered = true;
      VM_Callbacks.addExitMonitor(new VM_EdgeCounts());
    }
    VM_Triplet key = m.getDictionaryKey();
    return VM_EdgeCounterDictionary.findOrCreateId(key, null);
  }

  public static int findId(VM_Method m) {
    if (!VM.BuildForAdaptiveSystem && !registered) {
      // Assumption: If edge counters were enabled in a non-adaptive system
      //             then the user must want use to dump them when the system
      //             exits.  Otherwise why would they have enabled them...
      registered = true;
      VM_Callbacks.addExitMonitor(new VM_EdgeCounts());
    }
    VM_Triplet key = m.getDictionaryKey();
    return VM_EdgeCounterDictionary.findId(key);
  }

  public static VM_BranchProfiles getBranchProfiles(VM_Method m) {
    if (!m.getDeclaringClass().isLoaded() || m.getBytecodes() == null) return null;
    int id = findId(m);
    if (id == -1) return null;
    int[] cs = VM_EdgeCounterDictionary.getValue(id);
    if (cs == null) return null;
    return new VM_BranchProfiles(m, id, cs);
  }

  /**
   * Dump all the profile data to the file VM_BaselineCompiler.options.EDGE_COUNTER_FILE
   */
  public static void dumpCounts() {
    dumpCounts(VM_BaselineCompiler.options.EDGE_COUNTER_FILE);
  }

  /**
   * Dump all profile data to the given file
   * @param fn output file name
   */
  public static void dumpCounts(String fn) {
    PrintStream f;
    try {
      f = new PrintStream(new FileOutputStream(fn));
    } catch (IOException e) {
      VM.sysWrite("\n\nVM_EdgeCounts.dumpCounts: Error opening output file!!\n\n");
      return;
    }
    int n = VM_EdgeCounterDictionary.getNumValues();
    for (int i=0; i<n; i++) {
      VM_Triplet key = VM_EdgeCounterDictionary.getKey(i);
      int mid = VM_MethodDictionary.findId(key);
      if (mid == -1) continue; // only should happen when we've read in a file of offline data.
      VM_Method m = VM_MethodDictionary.getValue(mid);
      if (!m.isLoaded()) continue; // ditto -- came from offline data
      new VM_BranchProfiles(m, i, VM_EdgeCounterDictionary.getValue(i)).print(f);
    }
  }

  public static void readCounts(String fn) {
    LineNumberReader in = null;
    try {
      in = new LineNumberReader(new FileReader(fn));
    } catch (IOException e) {
      e.printStackTrace();
      VM.sysFail("Unable to open input edge counter file "+fn);
    }
    try {
      int[] cur = null;
      int curIdx = 0;
      for (String s = in.readLine(); s != null; s = in.readLine()) {
	StringTokenizer parser = new StringTokenizer(s, " \t\n\r\f:{},");
	String firstToken = parser.nextToken();
	if (firstToken.equals("M")) {
	  int numCounts = Integer.parseInt(parser.nextToken());
	  VM_Atom dc = VM_Atom.findOrCreateUnicodeAtom(parser.nextToken());
	  VM_Atom mn = VM_Atom.findOrCreateUnicodeAtom(parser.nextToken());
	  VM_Atom md = VM_Atom.findOrCreateUnicodeAtom(parser.nextToken());
	  VM_Triplet key = new VM_Triplet(dc, mn, md);
	  int id = VM_EdgeCounterDictionary.findOrCreateId(key, new int[numCounts]);
	  cur = VM_EdgeCounterDictionary.getValue(id);
	  curIdx = 0;
	} else {
	  String bracket = parser.nextToken(); // discard bytecode index, we don't care.
	  if (bracket.equals("<")) {
	    // conditional branch
	    float freq = (float)Long.parseLong(parser.nextToken());
	    float takenProb = Float.parseFloat(parser.nextToken());
	    int yea = (int)(0x000000007fffffffL & (long)(0.5f + freq * takenProb));
	    int nea = (int)(0x000000007fffffffL & (long)(0.5F + freq * (1.0f - takenProb)));
	    cur[curIdx + TAKEN] = yea;
	    cur[curIdx + NOT_TAKEN] = nea;
	    curIdx += 2;
	  } else if (bracket.equals("[")) {
	    float freq = (float)Long.parseLong(parser.nextToken());
	    for (String nt = parser.nextToken(); !nt.equals("]"); nt = parser.nextToken()) {
	      float takenProb = Float.parseFloat(nt);
	      int yea = (int)(0x000000007fffffffL & (long)(0.5f + freq * takenProb));
	      cur[curIdx++] = yea;
	    }
	  } else {
	    VM.sysFail("Format error in edge counter input file");
	  }
	}
      }
    } catch (IOException e) {
      e.printStackTrace();
      VM.sysFail("Error parsing input edge counter file"+fn);
    }

    // Enable debug of input by dumping file as we exit the VM.
    if (true) {
      VM_Callbacks.addExitMonitor(new VM_EdgeCounts());
      VM_BaselineCompiler.processCommandLineArg("-X:base:", "edge_counter_file=DebugEdgeCounters");
    }
  }

}
