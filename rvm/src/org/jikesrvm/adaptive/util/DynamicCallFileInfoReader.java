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
package org.jikesrvm.adaptive.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;
import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.database.callgraph.PartialCallGraph;
import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.MethodReference;

/**
 * Utility to read dynamic call graph annotations from file in ascii format.
 * Takes a single argument: the name of the file containing the ascii
 * annotations.  Each line of the file corresponds to an annotation
 * for one method and has the following format:
 * <p>
 * <pre>
 * CallSite < classloader, classname, method, signature> method_size byte_code_index <callee_classloader, classname, method, signature> method_size weight: weight
 * </pre>
 * Where the types and meanings of the fields is as follows:
 * <ul>
 * <li><code>&lt;signature></code> <i>string</i> The method signature</li>
 * </ul>
 *
 *
 * @see CompilerAdvice
 */
public class DynamicCallFileInfoReader {

  /**
   * Read annoations from a specified file. Reads all annotations at
   * once and returns a collection of compiler advice attributes.
   *
   * @param file The annoation file to be read
   */
  public static void readDynamicCallFile(String file, boolean boot) {
    BufferedReader fileIn = null;

    if (file == null) return;// null;

    if ((!VM.runningVM) && (Controller.dcg == null)) {
      Controller.dcg = new PartialCallGraph(300);
    } else if (Controller.dcg == null) {
      System.out.println("dcg is null ");
      return;
    } else {
      Controller.dcg.reset();  // clear any values accumulated to this point
    }
    try {
      fileIn = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
      try {
        for (String s = fileIn.readLine(); s != null; s = fileIn.readLine()) {
          if (Controller.options.BULK_COMPILATION_VERBOSITY > 1) {
            VM.sysWriteln(s);
          } else if (Controller.options.BULK_COMPILATION_VERBOSITY == 1) {
            VM.sysWrite(".");
          }
          StringTokenizer parser = new StringTokenizer(s, " \n,");
          readOneCallSiteAttribute(parser, boot);
        }
      } catch (IOException e) {
        e.printStackTrace();
        VM.sysFail("Error parsing input dynamic call graph file" + file);
      }
      fileIn.close();
    } catch (java.io.FileNotFoundException e) {
      System.out.println("IO: Couldn't read compiler advice attribute file: " + file + e);
    } catch (java.io.UnsupportedEncodingException e) {
      System.out.println("IO: UTF-16 is not supported: " + e);
    } catch (IOException e) {
      VM.sysFail("Error closing input dynamic call graph file" + file);
    }
  }

  private static void readOneCallSiteAttribute(StringTokenizer parser, boolean boot) {
    String firstToken = parser.nextToken();
    if (firstToken.equals("CallSite")) {
      try {
        MemberReference callerKey = MemberReference.parse(parser, boot);
        if (callerKey == null) return;
        MethodReference callerRef = callerKey.asMethodReference();
        RVMMethod caller, callee;
        caller = getMethod(callerRef);

        @SuppressWarnings("unused") // serves as doco - token skipped
        int callerSize = Integer.parseInt(parser.nextToken());
        int bci = Integer.parseInt(parser.nextToken());
        MemberReference calleeKey = MemberReference.parse(parser, boot);
        if (calleeKey == null) return;
        MethodReference calleeRef = calleeKey.asMethodReference();
        callee = getMethod(calleeRef);

        @SuppressWarnings("unused") // serves as doco - token skipped
        int calleeSize = Integer.parseInt(parser.nextToken());
        parser.nextToken(); // skip "weight:"
        float weight = Float.parseFloat(parser.nextToken());
        if ((caller == null) || (callee == null)) {
          Controller.dcg.incrementUnResolvedEdge(callerRef, bci, calleeRef, weight);
        } else {
          Controller.dcg.incrementEdge(caller, bci, callee, weight);
        }
      } catch (Exception e) {
        VM.sysWriteln("Caught exception: "+e);
      }
    } else {
      VM.sysFail("Format error in dynamic call graph file");
    }
  }

  /**
   * Establish the RVMMethod for a given MethodReference gracefully.
   *
   * @param ref The MethodReference
   * @return The RVMMethod, or null on failure.
   */
  private static RVMMethod getMethod(MethodReference ref) {
    if (ref.getType().getClassLoader() == RVMClassLoader.getApplicationClassLoader()) {
      try {
        return ref.resolve();
      } catch (NoClassDefFoundError e) {
        if (Controller.options.BULK_COMPILATION_VERBOSITY >= 1)
          VM.sysWriteln("Warning: could not define class: " + ref.getType());
        return null;
      } catch (NoSuchMethodError e) {
        if (Controller.options.BULK_COMPILATION_VERBOSITY >= 1)
          VM.sysWriteln("Warning: could not load method: " + ref);
        return null;
      }
    } else {
      return ref.getResolvedMember();
    }
  }
}





