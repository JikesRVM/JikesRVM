/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Trap Conventions
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
interface VM_TrapConstants {
  //--------------------------------------------------------------------------------------------//
  //                              Trap Conventions.                                             //
  //--------------------------------------------------------------------------------------------//
 
  // Compilers should generate trap instructions that conform to the following
  // values in order for traps to be correctly recognized by the trap handler
  // in libvm.C
  //
  static final int DIVIDE_BY_ZERO_MASK        = 0xFFE0FFFF; // opcode, condition mask, & immediate
  static final int DIVIDE_BY_ZERO_TRAP        = 0x0C800000; // teqi, divisor, 0
  static final int ARRAY_INDEX_MASK           = 0xFFE007FE; // extended opcode and condition mask
  static final int ARRAY_INDEX_TRAP           = 0x7CC00008; // tlle arraySize, arrayIndex
  static final int ARRAY_INDEX_REG_MASK       = 0x0000f800;
  static final int ARRAY_INDEX_REG_SHIFT      = 11;
  static final int CONSTANT_ARRAY_INDEX_MASK  = 0xFFE00000; // opcode and condition mask
  static final int CONSTANT_ARRAY_INDEX_TRAP  = 0x0CC00000; // tllei arraySize, arrayIndexConstant
  static final int CONSTANT_ARRAY_INDEX_INFO  = 0x0000ffff;
  static final int STACK_OVERFLOW_MASK        = 0xFFE007FE; // opcode and condition mask
  static final int STACK_OVERFLOW_TRAP        = 0x7E000008; // tlt stackPointer, stackLimit
  static final int WRITE_BUFFER_OVERFLOW_MASK = 0xFFE007FE; // opcode and condition mask
  static final int WRITE_BUFFER_OVERFLOW_TRAP = 0x7E800008; // tle modifiedOldObjectMax, modifiedOldObjectAddr

  /* JNI stack size checking */
  static final int JNI_STACK_TRAP_MASK        = 0x0FECFFFF; // tALWAYSi, 12, 0x0001 
  static final int JNI_STACK_TRAP             = 0x0FEC0001; 

  /* USED BY THE OPT_COMPILER */
  static final int CHECKCAST_MASK             = 0x0FECFFFF; // tALWAYSi, 12, 0x0000 
  static final int CHECKCAST_TRAP             = 0x0FEC0000; 
  static final int MUST_IMPLEMENT_MASK        = 0x0FECFFFF; // tALWAYSi, 12, 0x0002 
  static final int MUST_IMPLEMENT_TRAP        = 0x0FEC0002; 
  static final int STORE_CHECK_MASK           = 0x0FECFFFF; // tALWAYSi, 12, 0x0003 
  static final int STORE_CHECK_TRAP           = 0x0FEC0003; 
  static final int REGENERATE_MASK            = 0xFFE007FE;
  static final int REGENERATE_TRAP            = 0x7C600008; // tlne
  static final int NULLCHECK_MASK             = 0xFFE0FFFF;
  static final int NULLCHECK_TRAP             = 0x0C400001; // tllt 1
}
