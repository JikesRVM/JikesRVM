/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

import  java.util.*;
import  java.io.*;

/**
 * This class (a singleton) maintains a mapping from VM_Method to
 * OPT_InlineOracle.  
 *
 * @author Stephen Fink
 */
public class OPT_InlineOracleDictionary {

  /** 
   * Returns the OPT_InlineOracle associated with a VM_Method.
   * If no such oracle found, returns null.
   * @param m the method in question.
   * @return the OPT_InlineOracle associated with a VM_Method.
   */
  static OPT_InlineOracle getOracle (VM_Method m) {
    OPT_InlineOracle result = (OPT_InlineOracle)hash.get(m);
    if (result == null) {
      return  defaultOracle;
    } 
    else {
      return  result;
    }
  }

  /** 
   * Associate an OPT_InlineOracle with a VM_Method.
   */
  static void putOracle (OPT_InlineOracle o, VM_Method m) {
    hash.put(o, m);
  }

  /**
   * Register a particular oracle as the default oracle for 
   * <em> all </em> methods.
   * @param oracle the default oracle
   */
  public static void registerDefault (OPT_InlineOracle oracle) {
    defaultOracle = oracle;
  }

  /** 
   * Populate the dictionary from a file.
   * @param fileName the name of the file
   */
  static void populate (String fileName) {
    // the first line of the file must hold the class name of
    // the dictionary populator implementation stored in this file
    LineNumberReader in = null;
    String popType = null;
    try {
      in = new LineNumberReader(new FileReader(fileName));
      popType = in.readLine();
    } catch (Exception e) {
      e.printStackTrace();
      throw  new OPT_OptimizingCompilerException("Inline", "Dictionary error");
    }
    Class popClass;
    try {
      popClass = Class.forName(popType);
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      throw  new OPT_OptimizingCompilerException("Inline", "Class NOT found", 
          popType.toString());
    }
    // verify that the populator class knows how to populate the 
    // InlineOracleDictionary
    Class popInterface;
    try {
      popInterface = Class.forName("OPT_InlineOracleDictionaryPopulator");
    } catch (Exception e) {
      e.printStackTrace();
      throw  new OPT_OptimizingCompilerException("Inline", 
          "Could not instantiate OPT_InlineOracleDictionaryPopulator");
    }
    if (!OPT_InlineTools.implementsInterface(popClass, popInterface)) {
      throw  new OPT_OptimizingCompilerException("Inline", 
          "Class cannot populate the InlineOracleDictionary", 
          popType.toString());
    }
    // populate the dictionary
    OPT_InlineOracleDictionaryPopulator populator;
    try {
      populator = (OPT_InlineOracleDictionaryPopulator)popClass.newInstance();
    } catch (Exception e) {
      e.printStackTrace();
      throw  new OPT_OptimizingCompilerException("Inline", 
          "Could not instantiate ", 
          popType.toString());
    }
    populator.populateInlineOracleDictionary(in);
    try {
      in.close();
    } catch (Exception e) {
      e.printStackTrace();
      throw  new OPT_OptimizingCompilerException("Inline", "Dictionary error");
    }
  }

  /** 
   * Singleton pattern: prevent instantiation 
   */
  private OPT_InlineOracleDictionary () {
  }
  /**
   * The main mapping.
   */
  private static java.util.HashMap hash = new java.util.HashMap();
  /**
   * The default oracle, for methods not mapped in the hash map.
   */ 
  private static OPT_InlineOracle defaultOracle;
}



