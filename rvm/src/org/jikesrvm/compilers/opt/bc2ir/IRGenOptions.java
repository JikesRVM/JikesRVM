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
package org.jikesrvm.compilers.opt.bc2ir;


/**
 * This class contains flags to control IR generation.
 *
 * @see BC2IR
 */
public final class IRGenOptions {
  //////////////////////////////////////////
  // Flags that control IR generation policies
  //////////////////////////////////////////
  /**
   * Do we allow locals to live on the stack?
   */
  static final boolean LOCALS_ON_STACK = true;

  /**
   * Do we eliminate copies to local variables?
   */
  static final boolean ELIM_COPY_LOCALS = true;

  /**
   * Do we allow constants to live in local variables?
   */
  static final boolean CP_IN_LOCALS = true;

  /**
   * How many return addresses will we allow in the local variables of
   * a basic block before we decide that we should bail out to prevent
   * exponential blowup in code space &amp; compile time?
   */
  static final int MAX_RETURN_ADDRESSES = 3;

  /** Control on constant folding during IR generation */
  static final boolean CF_TABLESWITCH = true;
  /** Control on constant folding during IR generation */
  static final boolean CF_LOOKUPSWITCH = true;
  /** Control on constant folding during IR generation */
  static final boolean CF_CHECKCAST = true;
  /** Control on constant folding during IR generation */
  static final boolean CF_CHECKSTORE = true;
  /** Control on constant folding during IR generation */
  static final boolean CF_INSTANCEOF = true;
  /** Control on constant folding during IR generation */
  static final boolean CF_INTIF = true;
  /** Control on constant folding during IR generation */
  static final boolean CF_INTIFCMP = true;
  /** Control on constant folding during IR generation */
  static final boolean CF_REFIF = true;
  /** Control on constant folding during IR generation */
  static final boolean CF_REFIFCMP = true;
  /** Control on constant folding during IR generation */
  static final boolean CF_LONGCMP = true;
  /** Control on constant folding during IR generation */
  static final boolean CF_FLOATCMP = true;
  /** Control on constant folding during IR generation */
  static final boolean CF_DOUBLECMP = true;

  //////////////////////////////////////////
  // Debugging support (messaging controls)
  //////////////////////////////////////////
  /**
   * Master debug flag for IR gen. Turns on all other IR gen debug flags.
   */
  static final boolean DBG_ALL = false;

  /**
   * Debug flag: basic blocks
   */
  static final boolean DBG_BB = DBG_ALL || false;

  /**
   * Debug flag: bytecode parsing
   */
  static final boolean DBG_BCPARSE = DBG_ALL || false;

  /**
   * Debug flag: control flow
   */
  static final boolean DBG_CF = DBG_ALL || false;

  /**
   * Debug flag: print instructions as they are generated
   */
  static final boolean DBG_INSTR = DBG_ALL || false;

  /**
   * Debug flag: elim copy to locals
   */
  static final boolean DBG_ELIMCOPY = DBG_ALL || false;

  /**
   * Debug flag: elim null checks
   */
  static final boolean DBG_ELIMNULL = DBG_ALL || false;

  /**
   * Debug flag: stack rectification
   */
  static final boolean DBG_STACK = DBG_ALL || false;

  /**
   * Debug flag: local var rectification
   */
  static final boolean DBG_LOCAL = DBG_ALL || false;

  /**
   * Debug flag: block regeneration
   */
  static final boolean DBG_REGEN = DBG_ALL || false;

  /**
   * Debug flag: operand lattice functions
   */
  public static final boolean DBG_OPERAND_LATTICE = DBG_ALL || false;

  /**
   * Debug flag: cfg
   */
  static final boolean DBG_CFG = DBG_ALL || false;

  /**
   * Debug flag: flattening
   */
  static final boolean DBG_FLATTEN = DBG_ALL || false;

  /**
   * Debug flag: exception handlers
   */
  static final boolean DBG_EX = DBG_ALL || false;

  /**
   * Debug flag: basic block set operations
   */
  static final boolean DBG_BBSET = DBG_ALL || false;

  /**
   * Debug flag: type analysis
   */
  public static final boolean DBG_TYPE = DBG_ALL || false;

  /**
   * Debug flag: jsr inlining
   */
  static final boolean DBG_INLINE_JSR = DBG_ALL || false;

  private IRGenOptions() {
    // prevent instantiation
  }

}
