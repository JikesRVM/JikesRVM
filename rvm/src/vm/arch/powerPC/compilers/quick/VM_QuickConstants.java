/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.quick;

import com.ibm.JikesRVM.*;

/**
 * A set of constants that are useful for the quick compiler.
 *
 * @author Chris Hoffmann
 * @version 1.0
 */
interface VM_QuickConstants 
  extends VM_BaselineConstants
{
  final static int SF0 = FIRST_SCRATCH_FPR;
  
  final static int FIRST_SCRATCH_FIXED_REGISTER = FIRST_SCRATCH_GPR;
  final static int FIRST_SCRATCH_FLOAT_REGISTER = FIRST_SCRATCH_FPR;

  final static int EMPTY_SLOT = -1;
  final static int INVALID_SLOT = -2;

  // Current settings:
  // GPR Locals: 17-19
  // GPR Stack : 20-31
  // FPR Locals: 16-22
  // FPR Stack : 23-31
  final static int NUM_FIXED_LOCAL_REGISTERS = 3;
  final static int FIRST_FIXED_LOCAL_REGISTER = FIRST_NONVOLATILE_GPR;
  final static int LAST_FIXED_LOCAL_REGISTER =
    FIRST_FIXED_LOCAL_REGISTER + NUM_FIXED_LOCAL_REGISTERS - 1;
  
  final static int NUM_FIXED_STACK_REGISTERS = 12;
  final static int FIRST_FIXED_STACK_REGISTER = LAST_FIXED_LOCAL_REGISTER+1 ;
  final static int LAST_FIXED_STACK_REGISTER =
    FIRST_FIXED_STACK_REGISTER + NUM_FIXED_STACK_REGISTERS - 1;
  

  final static int NUM_FLOAT_LOCAL_REGISTERS = 7;
  final static int FIRST_FLOAT_LOCAL_REGISTER = FIRST_NONVOLATILE_FPR;
  final static int LAST_FLOAT_LOCAL_REGISTER =
    FIRST_FLOAT_LOCAL_REGISTER + NUM_FLOAT_LOCAL_REGISTERS - 1;
  
  final static int NUM_FLOAT_STACK_REGISTERS = 9;
  final static int FIRST_FLOAT_STACK_REGISTER = LAST_FLOAT_LOCAL_REGISTER+1;
  final static int LAST_FLOAT_STACK_REGISTER =
    FIRST_FLOAT_STACK_REGISTER + NUM_FLOAT_STACK_REGISTERS - 1;

  final static int DEFAULT_FIXED_STACK_REGISTERS = 6;
  final static int DEFAULT_FLOAT_STACK_REGISTERS = 6;

}
