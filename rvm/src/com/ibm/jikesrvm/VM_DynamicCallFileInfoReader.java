/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright 
 * Department of Computer Science,
 * University of Texas at Austin 2005
 * All rights reserved.
 */

package com.ibm.jikesrvm;

import com.ibm.jikesrvm.adaptive.VM_Controller;
import com.ibm.jikesrvm.adaptive.VM_PartialCallGraph;
import com.ibm.jikesrvm.classloader.*;
import java.io.IOException;
import java.util.StringTokenizer;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

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
 * @author Xianglong Huang
 *
 * @see VM_CompilerAdvice
 */
public class VM_DynamicCallFileInfoReader {

  /**
   * Read annoations from a specified file. Reads all annotations at
   * once and returns a collection of compiler advice attributes.
   * 
   * @param file The annoation file to be read
   */
  public static void readDynamicCallFile(String file, boolean boot) {
    BufferedReader fileIn = null;
    
    if (file == null) return;// null;
    
    if ((!VM.runningVM) && (VM_Controller.dcg == null)) {
      VM_Controller.dcg = new VM_PartialCallGraph(300); 
    } else if (VM_Controller.dcg == null) {
      System.out.println("dcg is null ");
      return;
    }
    try {
      fileIn = new BufferedReader(new 
        InputStreamReader(new FileInputStream(file), 
                          "UTF-8"));
      try {
        for (String s = fileIn.readLine(); s != null; s = fileIn.readLine()) {
          StringTokenizer parser = new StringTokenizer(s, " \n,");
          readOneCallSiteAttribute(parser, boot);
        }
      } catch (IOException e) {
        e.printStackTrace();
        VM.sysFail("Error parsing input dynamic call graph file"+file);
      }
      fileIn.close();
    } catch (java.io.FileNotFoundException e) {
      System.out.println("IO: Couldn't read compiler advice attribute file: " 
                         + file + e);
    } catch (java.io.UnsupportedEncodingException e) {
      System.out.println("IO: UTF-16 is not supported: " 
                         + e);
    } catch (IOException e) {
      VM.sysFail("Error closing input dynamic call graph file"+file);
    }
    
  }
  private static void readOneCallSiteAttribute(StringTokenizer parser, boolean boot) {
    String firstToken = parser.nextToken();
    if (firstToken.equals("CallSite")) {
      VM_MemberReference callerKey = VM_MemberReference.parse(parser, boot);
      if (callerKey == null) return;
      VM_MethodReference callerRef = callerKey.asMethodReference();
      VM_Method caller, callee;
      if (callerRef.getType().getClassLoader() == VM_ClassLoader.getApplicationClassLoader()) {
        caller = callerRef.resolve();
      } else 
        caller = callerRef.getResolvedMember();
      //if (caller == null) continue;
      @SuppressWarnings("unused") // serves as doco - token skipped 
      int callerSize = Integer.parseInt(parser.nextToken());
      int bci = Integer.parseInt(parser.nextToken());
      VM_MemberReference calleeKey = VM_MemberReference.parse(parser, boot);
      if (calleeKey == null) return;
      VM_MethodReference calleeRef = calleeKey.asMethodReference();
      //if (callee == null) continue;
      if (calleeRef.getType().getClassLoader() == VM_ClassLoader.getApplicationClassLoader()) {
        callee = calleeRef.resolve();
      } else 
        callee = calleeRef.getResolvedMember();
      @SuppressWarnings("unused") // serves as doco - token skipped 
      int calleeSize = Integer.parseInt(parser.nextToken());
      parser.nextToken(); // skip "weight:"
      float weight = Float.parseFloat(parser.nextToken());
      if ((caller == null) || (callee == null)) {
        VM_Controller.dcg.incrementUnResolvedEdge(callerRef, bci, calleeRef, weight);
      } else {
        VM_Controller.dcg.incrementEdge(caller, bci, callee, weight);
      }
    } else {
      VM.sysFail("Format error in dynamic call graph file");
    }
  }    
}





