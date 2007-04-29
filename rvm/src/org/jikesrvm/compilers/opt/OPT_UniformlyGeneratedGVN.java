/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import java.util.Enumeration;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_ADD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_SUB;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_ADD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SUB;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_ADD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_SUB;

/**
 * This class implements index equivalence via global value numbering 
 * and 'uniformly generated expressions'.  See EURO-PAR 01 paper for
 * more details.
 *
 * @author Vivek Sarkar
 * @author Stephen Fink
 */
class OPT_UniformlyGeneratedGVN {
  static final boolean DEBUG = false;

  /** 
   * Compute Index Equivalence with uniformly generated global value
   * numbers.
   *
   * <p> PRECONDITIONS: SSA form, register lists computed, SSA bit
   * computed.
   *                    
   * <p> POSTCONDITION: ir.HIRInfo.uniformlyGeneratedValueNumbers 
   * holds results of the analysis. Does not modify the IR in any other way.
   *
   * @param ir the governing IR
   */
  public static void perform (OPT_IR ir) {

    // create 'standard' global value numbers.
    OPT_GlobalValueNumberState gvn = null;
    gvn = new OPT_GlobalValueNumberState(ir);

    // Merge classes related by a constant
    for (Enumeration<OPT_Instruction> e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      OPT_Instruction s = e.nextElement();
      // Check if s is a fixed-point add/subtract instruction with 
      // a constant second operand
      if ( s.operator == INT_ADD || s.operator == LONG_ADD ||
           s.operator == REF_ADD || s.operator == REF_SUB || 
           s.operator == INT_SUB || s.operator == LONG_SUB ) {
        OPT_Operand val2 = Binary.getVal2(s);
        if (val2.isConstant()) {
          OPT_Operand lhs = Binary.getResult(s);
          OPT_Operand rhs = Binary.getVal1(s);
          gvn.mergeClasses(gvn.valueGraph.getVertex(lhs), 
                           gvn.valueGraph.getVertex(rhs));
        }
      }
    }      

    if (DEBUG){
      System.out.println("@@@@ START OF INDEX EQUIVALENCE VALUE NUMBERS FOR " 
                         + ir.method + " @@@@");
      gvn.printValueNumbers();
      System.out.println("@@@@ END OF INDEX EQUIVALENCE VALUE NUMBERS FOR " 
                         + ir.method + " @@@@");
    }

    ir.HIRInfo.uniformlyGeneratedValueNumbers = gvn;
  }
}
