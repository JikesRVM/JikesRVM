/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
import com.ibm.JikesRVM.*;

/**
 * @author Ton Ngo
 */

interface jdpConstants {
  static final int PRINTNONE = 0;
  static final int PRINTSOURCE = 1;
  static final int PRINTASSEMBLY = 2;

  static final int PROLOGMARKER = 0x4ffffb82;     // mark end of prolog section

  static final int BKPT_INSTRUCTION =  0x7d821008;  // instruction for setting breakpoints

  static final int NATIVE_METHOD_ID = -1;           // value returned for method ID if the stack frame is native

  static final int MAX_INSTRUCTION_SIZE = 3;   // for Intel, the largest size for one instruction, as number of 4-byte words

  ////////////////////////////////////////////////////////////////////////
  // Constants for JDP commands
  ////////////////////////////////////////////////////////////////////////

  static final int UNKNOWN_COMMAND		  = -1;
  static final int AMBIGUOUS_COMMAND		  = -2;

  static final int STEP_COMMAND 		   = 0;	// "step"
  static final int STEP_BR_COMMAND		   = 1;	// "stepbr"
  static final int STEP_LINE_COMMAND		   = 2;	// "stepline"
  static final int STEP_LINE_OVER_COMMAND	   = 3;	// "steplineover"
  static final int RUN_COMMAND			   = 4;	// "run"
  static final int KILL_COMMAND			   = 5;	// "kill"
  static final int CONTINUE_COMMAND		   = 6;	// "cont"
  static final int CONTINUE_THREAD_COMMAND	   = 7;	// "cthread"
  static final int RETURN_TO_CALLER_COMMAND	   = 8; // "creturn"
  static final int THREAD_COMMAND		   = 9;	// "thread"
  static final int READ_REGISTER_COMMAND	  = 10;	// "reg"
  static final int WRITE_REGISTER_COMMAND	  = 11;	// "wreg"
  static final int REGISTER_NAMES_COMMAND	  = 12;	// "regnames"
  static final int READ_MEMORY_RAW_COMMAND	  = 13;	// "memraw"
  static final int READ_MEMORY_COMMAND		  = 14;	// "mem"
  static final int WRITE_MEMORY_COMMAND		  = 15;	// "wmem"
  static final int PRINT_COMMAND		  = 16;	// "print"
  static final int PRINT_CLASS_COMMAND		  = 17;	// "printclass"
  static final int GET_CLASS_COMMAND		  = 18;	// "getclass"
  static final int GET_INSTANCE_COMMAND		  = 19;	// "getinstance"
  static final int GET_ARRAY_COMMAND		  = 20;	// "getarray"
  static final int GET_CLASS_AND_LINE_COMMAND	  = 21;	// "getcl"
  static final int GET_CURRENT_INSTR_ADDR_COMMAND = 22; // "getcia"
  static final int GET_FRAMES_COMMAND		  = 23;	// "getframes"
  static final int GET_LOCALS_COMMAND		  = 24;	// "getlocals"
  static final int LIST_INSTRUCTIONS_COMMAND	  = 25;	// "listi"
  static final int LIST_THREADS_COMMAND		  = 26;	// "listt"
  static final int SET_BREAKPOINT_COMMAND	  = 27; // "break"
  static final int CLEAR_BREAKPOINT_COMMAND	  = 28;	// "clearbreak"
  static final int DISPLAY_CURRENT_FRAME_COMMAND  = 29;	// "stack"
  static final int SHORT_STACK_TRACE_COMMAND	  = 30;	// "where"
  static final int FULL_STACK_TRACE_COMMAND	  = 31;	// "whereframe"
  static final int PREFERENCE_COMMAND		  = 32;	// "preference"
  static final int HEX_TO_DEC_COMMAND		  = 33;	// "x2d"
  static final int DEC_TO_HEX_COMMAND		  = 34;	// "d2x"
  static final int COUNT_COMMAND		  = 35;	// "count"
  static final int ZERO_COUNT_COMMAND		  = 36;	// "zerocount"
  static final int READ_MEMORY_ALT_COMMAND	  = 37;	// "readmem"
  static final int TOGGLE_VERBOSE_COMMAND	  = 38; // "verbose"
  static final int HELP_COMMAND			  = 39;	// "help"
  static final int STEP_NEXT_SOURCE_LINE_COMMAND  = 40; // "stepnext"
  static final int NUM_COMMANDS			  = 41; // keep this up to date
}  
