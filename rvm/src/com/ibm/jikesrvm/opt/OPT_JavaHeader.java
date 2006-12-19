/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.jikesrvm.opt;

import com.ibm.jikesrvm.*;
import com.ibm.jikesrvm.opt.ir.*;

/**
 * OPT compiler extensions to VM_JavaHeader
 * 
 * @author Steve Fink
 * @author Dave Grove
 */
public class OPT_JavaHeader extends VM_JavaHeader {

  /**
   * Mutate a GET_OBJ_TIB instruction to the LIR
   * instructions required to implement it.
   * 
   * @param s the GET_OBJ_TIB instruction to lower
   * @param ir the enclosing OPT_IR
   */
  public static void lowerGET_OBJ_TIB(OPT_Instruction s, OPT_IR ir) { 
    // TODO: valid location operand.
    OPT_Operand address = GuardedUnary.getClearVal(s);
    Load.mutate(s, OPT_Operators.REF_LOAD, GuardedUnary.getClearResult(s), 
                address, new OPT_AddressConstantOperand(TIB_OFFSET), 
                null, GuardedUnary.getClearGuard(s));
  }
}
