/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import java.util.*;
import java.io.*;

/**
 * A repository of edge counters for bytecode-level edge conditional branches.
 * 
 * @author Dave Grove
 */
public final class VM_EdgeCounts implements VM_Callbacks.ExitMonitor {

  static final int TAKEN     = 0;
  static final int NOT_TAKEN = 1;

  private static boolean registered = false;

  private static int[][] data;

  public void notifyExit(int value) { dumpCounts(); }

  public static void boot() {
    if (VM.EdgeCounterFile != null) {
      readCounts(VM.EdgeCounterFile);
    }
  }

  static synchronized void allocateCounters(VM_NormalMethod m, int numEntries) {
    if (numEntries == 0) return;
    if (!VM.BuildForAdaptiveSystem && !registered) {
      // Assumption: If edge counters were enabled in a non-adaptive system
      //             then the user must want us to dump them when the system
      //             exits.  Otherwise why would they have enabled them...
      registered = true;
      VM_Callbacks.addExitMonitor(new VM_EdgeCounts());
    }
    allocateCounters(m.getId(), numEntries);
  }

  private static synchronized void allocateCounters(int id, int numEntries) {
    if (data == null) {
      data = new int[id+500][];
    }
    if (id >= data.length) {
      int newSize = data.length*2;
      if (newSize <= id) newSize = id+500;
      int[][] tmp = new int[newSize][];
      System.arraycopy(data, 0, tmp, 0, data.length);
      VM_Magic.sync();
      data = tmp;
    }
    data[id] = new int[numEntries];
  }

  public static VM_BranchProfiles getBranchProfiles(VM_NormalMethod m) {
    int id = m.getId();
    if (data == null || id >= data.length) return null;
    if (data[id] == null) return null;
    return new VM_BranchProfiles(m, data[id]);
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
    if (data == null) return;
    for (int i=0; i<data.length; i++) {
      if (data[i] != null) {
        VM_NormalMethod m = (VM_NormalMethod)VM_MemberReference.getMemberRef(i).asMethodReference().peekResolvedMethod();
        new VM_BranchProfiles(m, data[i]).print(f);
      }
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
        StringTokenizer parser = new StringTokenizer(s, " \t\n\r\f,{}");
        String firstToken = parser.nextToken();
        if (firstToken.equals("M")) {
          int numCounts = Integer.parseInt(parser.nextToken());
          parser.nextToken(); // discard '<'
          String clName = parser.nextToken();
          VM_Atom dc = VM_Atom.findOrCreateUnicodeAtom(parser.nextToken());
          VM_Atom mn = VM_Atom.findOrCreateUnicodeAtom(parser.nextToken());
          VM_Atom md = VM_Atom.findOrCreateUnicodeAtom(parser.nextToken());
          parser.nextToken(); // discard '>'
          ClassLoader cl;
          if (clName.equals("SystemCL")) {
            cl = VM_SystemClassLoader.getVMClassLoader();
          } else if (clName.equals("AppCL")) {
            cl = VM_ClassLoader.getApplicationClassLoader();
          } else {
            VM.sysFail("Unable to match classloader "+clName);
            cl = null;
          }
          VM_TypeReference tref = VM_TypeReference.findOrCreate(cl, dc);
          VM_MemberReference key = VM_MemberReference.findOrCreate(tref, mn, md);
          int id = key.getId();
          allocateCounters(id, numCounts);
          cur = data[id];
          curIdx = 0;
        } else {
          String type = parser.nextToken(); // discard bytecode index, we don't care.
          if (type.equals("switch")) {
            parser.nextToken(); // discard '<'
            for (String nt = parser.nextToken(); !nt.equals(">"); nt = parser.nextToken()) {
              cur[curIdx++] = Integer.parseInt(nt);
            }
          } else if (type.equals("forwbranch") || type.equals("backbranch")) {
            parser.nextToken(); // discard '<'
            cur[curIdx + TAKEN] = Integer.parseInt(parser.nextToken());
            cur[curIdx + NOT_TAKEN] = Integer.parseInt(parser.nextToken());
            curIdx += 2;
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
    if (false) {
      VM_Callbacks.addExitMonitor(new VM_EdgeCounts());
      VM_BaselineCompiler.processCommandLineArg("-X:base:", "edge_counter_file=DebugEdgeCounters");
    }
  }

}
