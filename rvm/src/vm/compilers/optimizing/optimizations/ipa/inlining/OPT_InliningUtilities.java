/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.*;

/**
 * This class holds public static routines that implement various utilities
 * that have been collected from other files.
 *
 * @author Peter Sweeney
 * @modified Stephen Fink
 * @modified Sharad Singhai
 */

class OPT_InliningUtilities {
  final static boolean debug = false;

  /**
   * Is a particular method forbidden to be inlined?
   * <p> Precondition: VM_Method can not be null.
   * @param m the method in question
   */
  public static boolean methodShouldNotBeInlined (VM_Method m) {
    boolean result = false;
    try {
      result = TabooMethods.contains(m);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
    if (debug)
      VM.sysWrite("  " + m 
          + (result ? "should not be inlined\n" : "can be inlined\n"));
    return  result;
  }

  /**
   * Static initializer
   */
  public static void init () {
    TabooMethods.init();
  }
}


/**
 * The following utility class holds the set of methods that cannot be
 * inlined.
 */
class TabooMethods {
  /**
   * The set of forbidden methods
   */
  static java.util.HashSet tabooSet = new java.util.HashSet();

  /**
   * Static initializer: set up the set of forbidden methods
   */
  public static void init () {
    tabooSet.add(VM.getMember("Ljava/lang/Runtime;", "exit", "(I)V"));
    tabooSet.add(VM.getMember("Ljava/lang/System;", "exit", "(I)V"));
    tabooSet.add(VM.getMember("LVM;", "disableGC", "()V"));
    tabooSet.add(VM.getMember("LVM;", "sysExit", "(I)V"));
    tabooSet.add(VM.getMember("LVM_DynamicLinker;", "lazyMethodInvoker", "()V"));
    tabooSet.add(VM.getMember("LVM_Processor;", "getCurrentProcessor", 
			      "()LVM_Processor;"));
    tabooSet.add(VM.getMember("LVM_Runtime;", "athrow", 
			      "(Ljava/lang/Throwable;)V"));
    tabooSet.add(VM.getMember("LVM_Runtime;", "checkcast", 
			      "(Ljava/lang/Object;I)V"));
    tabooSet.add(VM.getMember("LVM_Runtime;", "checkstore", 
			      "(Ljava/lang/Object;Ljava/lang/Object;)V"));
    tabooSet.add(VM.getMember("LVM_Runtime;", "instanceOf", 
			      "(Ljava/lang/Object;I)Z"));
    tabooSet.add(VM.getMember("LVM_Runtime;", "instanceOfFinal", 
			      "(Ljava/lang/Object;I)Z"));
    tabooSet.add(VM.getMember("LVM_Runtime;", "newScalar", 
			      "(I)Ljava/lang/Object;"));
    tabooSet.add(VM.getMember("LVM_Runtime;", "quickNewScalar", 
			      "(I[Ljava/lang/Object;Z)Ljava/lang/Object;"));
    tabooSet.add(VM.getMember("LVM_Runtime;", "quickNewArray", 
			      "(II[Ljava/lang/Object;)Ljava/lang/Object;"));
    tabooSet.add(VM.getMember("LVM_Finalizer;", "addElement", "(I)V"));
    tabooSet.add(VM.getMember("LVM_Allocator;", "cloneScalar", 
			      "(I[Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"));
    tabooSet.add(VM.getMember("LVM_Allocator;", "cloneArray", 
			      "(II[Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"));
    tabooSet.add(VM.getMember("LVM_Math;", "doubleToInt", "(D)I"));
    tabooSet.add(VM.getMember("LVM_Math;", "doubleToLong", "(D)J"));
    tabooSet.add(VM.getMember("LVM_Math;", "longDivide", "(JJ)J"));
    tabooSet.add(VM.getMember("LVM_Math;", "longRemainder", "(JJ)J"));
    tabooSet.add(VM.getMember("LVM_Math;", "longToDouble", "(J)D"));
    tabooSet.add(VM.getMember("LVM_Class;", "load", "()V"));
    tabooSet.add(VM.getMember("LVM_Class;", "resolve", "()V"));
    tabooSet.add(VM.getMember("LVM_Class;", "instantiate", "()V"));
    tabooSet.add(VM.getMember("LVM_Method;", "compile", 
			      "()"+VM.INSTRUCTION_ARRAY_SIGNATURE));
    tabooSet.add(VM.getMember("LVM_DynamicTypeCheck;", 
			      "mandatoryInstanceOfInterface", 
			      "(LVM_Class;[Ljava/lang/Object;)V"));
  }

  /**
   * Does this set contain a particular method?
   * @param m the method to query
   * @return true or false
   * @exception Exception
   */
  public static boolean contains (VM_Method m) throws Exception {
    // class initializers are taboo
    if (m.isClassInitializer()) {
      if (OPT_InliningUtilities.debug)
        System.out.println("  TM:" + m.toString() + 
            ".isClassInitializer() == true");
      return  true;
    }
    return  tabooSet.contains(m);
  }
}



