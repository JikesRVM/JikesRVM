/*
 * (C) Copyright 
 * Department of Computer Science,
 * University of Texas at Austin 2005
 * All rights reserved.
 */
//$Id$

package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import java.io.IOException;
import java.util.StringTokenizer;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.ArrayList;
import java.util.NoSuchElementException;

/**
 * Utility to read compiler advice annotations from file in ascii format.
 * Takes a single argument: the name of the file containing the ascii
 * annotations.  Each line of the file corresponds to an annotation
 * for one method and has the following format:
 * <p>
 * <pre>
 * <class> <method> <signature> <advice> <optLevel>
 * </pre>
 * Where the types and meanings of the fields is as follows:
 * <ul>
 * <li><code>&lt;class></code> <i>string</i> The name of the class</li>
 * <li><code>&lt;method></code> <i>string</i> The name of the method</li>
 * <li><code>&lt;signature></code> <i>string</i> The method signature</li>
 * <li><code>&lt;advice></code> <i>int</i> The compiler type to be used --
 * an integer value corresponding to the compiler enumeration in 
   VM_CompiledMethod</li>
 * <li><code>&lt;optLevel></code> <i>int</i> (Optional) The opt level to use 
   if compiler is optimizing compiler</li>
 * </ul>
 *
 * @author Xianglong Huang
 * @version $Revision$
 * @date $Date$
 *
 * @see VM_CompilerAdvice
 * @see VM_CompilerAdviceAttribute
 */
class VM_CompilerAdviceInfoReader {

  /**
   * Read annoations from a specified file. Reads all annotations at
   * once and returns a collection of compiler advice attributes.
   * 
   * @param file The annoation file to be read
   * @return A list of compileration advice attributes
   */
  public static List readCompilerAdviceFile(String file) {
    List compilerAdviceInfo = new ArrayList();;
    BufferedReader fileIn = null;
    
    if (file == null) return null;
    
    try {
      fileIn = new BufferedReader(new 
        InputStreamReader(new FileInputStream(file), "UTF-8"));
    
      try {
        for (String s = fileIn.readLine(); s != null; s = fileIn.readLine()) {
          StringTokenizer parser = new StringTokenizer(s, " \n,");
          compilerAdviceInfo.add(readOneAttribute(parser));
          
        }
      } catch (IOException e) {
        e.printStackTrace();
        VM.sysFail("Error parsing input compilation advice file "+file);
      }
    
      fileIn.close();
    } catch (java.io.FileNotFoundException e) {
      System.out.println("IO: Couldn't read compiler advice attribute file: " 
			 + file + e);
      return null;
    } catch (java.io.UnsupportedEncodingException e) {
      System.out.println("IO: UTF-8 is not supported: " 
			 + e);
      return null;
    } catch (java.io.IOException e) {
      System.out.println("IO: Couldn't close compiler advice attribute file: " 
			 + file + e);
      return null;
    }
    
    return compilerAdviceInfo;
  }
  
  /**
   * Actual reading is done here.  This method reads one attribute
   * from a single line of an input stream.  There are six elements
   * per line corresponding to each call site. First three are
   * strings, <i>class name</i>, <i>method name</i>, <i>method
   * signature</i>, followed by one number, 
   * <i>compiler advice</i>.
   *
   * @param st an input stream
   * @return an compileration advice atribute
   */
  private static VM_CompilerAdviceAttribute readOneAttribute(StringTokenizer st)
  {
    String className, methodName, methodSignature;
    int compiler, optLevel = -1;
    
    try {
      VM_Atom cls = VM_Atom.findOrCreateUnicodeAtom(st.nextToken());
      VM_Atom mth = VM_Atom.findOrCreateUnicodeAtom(st.nextToken());
      VM_Atom sig = VM_Atom.findOrCreateUnicodeAtom(st.nextToken());
      compiler = Integer.parseInt(st.nextToken());
      optLevel = Integer.parseInt(st.nextToken());
      // this is the attribute which will be returned
      VM_CompilerAdviceAttribute newAttrib;
      
      if (optLevel >= 0)
        newAttrib =
          new VM_CompilerAdviceAttribute(cls, mth, sig, compiler, optLevel);
      else
        newAttrib =
          new VM_CompilerAdviceAttribute(cls, mth, sig, compiler);
      
      return newAttrib;
    } catch (NoSuchElementException e) {
      return null;
    }

  }
}
