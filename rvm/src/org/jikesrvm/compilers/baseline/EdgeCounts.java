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

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.StringTokenizer;
import org.jikesrvm.VM;
import org.jikesrvm.Callbacks;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Entrypoint;

/**
 * A repository of edge counters for bytecode-level edge conditional branches.
 */
public final class EdgeCounts implements Callbacks.ExitMonitor {
  /**
   * Adjustment to offset in data from the bytecode index for taken
   * branch counts
   */
  public static final int TAKEN = 0;
  /**
   * Adjustment to offset in data from the bytecode index for not
   * taken branch counts
   */
  public static final int NOT_TAKEN = 1;

  /** For a non-adaptive system, have we registered the exit call back yet? */
  private static boolean registered = false;

  /**
   * Array of edge count data. The first index is the ID of the
   * method, the second index is the bytecode index within the method
   * plus either TAKEN or NOT_TAKEN. The value is the count of the
   * number of times a particular branch event occurs.
   */
  @Entrypoint
  private static int[][] data;

  public void notifyExit(int value) { dumpCounts(); }

  /**
   * Attempt to use edge counts from an input file.  If the source
   * file is not null, then clear any existing counts and read in new
   * counts from the file provided.
   *
   * @param inputFileName The name of the edge count file (possibly null)
   */
  public static void loadCountsFromFileIfAvailable(String inputFileName) {
    if (inputFileName != null) {
      /* first clear all counts */
      for (int i = 0; i < data.length; i++) {
        int[] d = data[i];
        if (d != null) {
          for (int j = 0; j < d.length; j++) {
            d[j] = 0;
          }
        }
      }
      /* then read in the provided counts */
      if (Controller.options.BULK_COMPILATION_VERBOSITY >= 1) { VM.sysWrite("Loading edge count file: ", inputFileName, " "); }
      readCounts(inputFileName);
      if (Controller.options.BULK_COMPILATION_VERBOSITY >= 1) { VM.sysWriteln(); }
    }
  }

  public static synchronized void allocateCounters(NormalMethod m, int numEntries) {
    if (numEntries == 0) return;
    if (!VM.BuildForAdaptiveSystem && !registered) {
      // Assumption: If edge counters were enabled in a non-adaptive system
      // then the user must want us to dump them when the system
      // exits.  Otherwise why would they have enabled them...
      registered = true;
      Callbacks.addExitMonitor(new EdgeCounts());
    }
    allocateCounters(m.getId(), numEntries);
  }

  private static synchronized void allocateCounters(int id, int numEntries) {
    if (data == null) {
      data = new int[id + 500][];
    }
    if (id >= data.length) {
      int newSize = data.length * 2;
      if (newSize <= id) newSize = id + 500;
      int[][] tmp = new int[newSize][];
      System.arraycopy(data, 0, tmp, 0, data.length);
      Magic.sync();
      data = tmp;
    }
    data[id] = new int[numEntries];
  }

  public static BranchProfiles getBranchProfiles(NormalMethod m) {
    int id = m.getId();
    if (data == null || id >= data.length) return null;
    if (data[id] == null) return null;
    return new BranchProfiles(m, data[id]);
  }

  /**
   * Dump all the profile data to the file BaselineCompiler.options.PROFILE_EDGE_COUNTER_FILE
   */
  public static void dumpCounts() {
    dumpCounts(BaselineCompiler.options.PROFILE_EDGE_COUNTER_FILE);
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
      VM.sysWrite("\n\nEdgeCounts.dumpCounts: Error opening output file!!\n\n");
      return;
    }
    if (data == null) return;
    for (int i = 0; i < data.length; i++) {
      if (data[i] != null) {
        NormalMethod m =
            (NormalMethod) MemberReference.getMemberRef(i).asMethodReference().peekResolvedMethod();
        if (m != null) {
          new BranchProfiles(m, data[i]).print(f);
        }
      }
    }
  }

  public static void readCounts(String fn) {
    LineNumberReader in = null;
    try {
      in = new LineNumberReader(new FileReader(fn));
    } catch (IOException e) {
      e.printStackTrace();
      VM.sysFail("Unable to open input edge counter file " + fn);
    }
    try {
      int[] cur = null;
      int curIdx = 0;
      for (String s = in.readLine(); s != null; s = in.readLine()) {
        StringTokenizer parser = new StringTokenizer(s, " \t\n\r\f,{}");
        String firstToken = parser.nextToken();
        if (firstToken.equals("M")) {
          int numCounts = Integer.parseInt(parser.nextToken());
          MemberReference key = MemberReference.parse(parser);
          int id = key.getId();
          allocateCounters(id, numCounts);
          cur = data[id];
          curIdx = 0;
          if (Controller.options.BULK_COMPILATION_VERBOSITY >= 1) { VM.sysWrite("M"); }
        } else {
          String type = parser.nextToken(); // discard bytecode index, we don't care.
          if (type.equals("switch")) {
            parser.nextToken(); // discard '<'
            for (String nt = parser.nextToken(); !nt.equals(">"); nt = parser.nextToken()) {
              cur[curIdx++] = Integer.parseInt(nt);
            }
            if (Controller.options.BULK_COMPILATION_VERBOSITY >= 1) { VM.sysWrite("S"); }
          } else if (type.equals("forwbranch") || type.equals("backbranch")) {
            parser.nextToken(); // discard '<'
            cur[curIdx + TAKEN] = Integer.parseInt(parser.nextToken());
            cur[curIdx + NOT_TAKEN] = Integer.parseInt(parser.nextToken());
            curIdx += 2;
            if (Controller.options.BULK_COMPILATION_VERBOSITY >= 1) { VM.sysWrite("B"); }
          } else {
            VM.sysFail("Format error in edge counter input file");
          }
        }
      }
      in.close();
    } catch (IOException e) {
      e.printStackTrace();
      VM.sysFail("Error parsing input edge counter file" + fn);
    }
    // Enable debug of input by dumping file as we exit the VM.
    if (false) {
      Callbacks.addExitMonitor(new EdgeCounts());
      BaselineCompiler.processCommandLineArg("-X:base:", "edge_counter_file=DebugEdgeCounters");
    }
  }

}
