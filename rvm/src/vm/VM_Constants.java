/*
 * (C) Copyright IBM Corp. 2001
 */

/**
 * Constants describing vm object, stack, and register characteristics.
 * Some of these constants are architecture-specific
 * and some are (at the moment) architecture-neutral.
 */
interface VM_Constants
extends   VM_ObjectLayoutConstants,     // architecture-neutral
          VM_StackframeLayoutConstants, // architecture-neutral
          VM_RegisterConstants,         // architecture-specific
          VM_TrapConstants              // architecture-specific
   {
   // Bit pattern used to represent a null object reference.
   // Note: I don't think this is actually used consistently [--DL]
   // 
   static final int VM_NULL = 0;
   
   // For assertion checking things that should never happen.
   //
   static final boolean NOT_REACHED = false;

   // For assertion checking things that aren't ready yet.
   //
   static final boolean NOT_IMPLEMENTED = false;
  
   static final int BYTES_IN_ADDRESS_LOG = 2;
   static final int BYTES_IN_ADDRESS = 1<<BYTES_IN_ADDRESS_LOG;

   // Reflection uses an integer return from a function which logically
   // returns a triple.  The values are packed in the interger return value
   // by the following masks.
   static final int REFLECTION_GPRS_BITS = 5;
   static final int REFLECTION_GPRS_MASK = (1 << REFLECTION_GPRS_BITS) - 1;
   static final int REFLECTION_FPRS_BITS = 5;
   static final int REFLECTION_FPRS_MASK = (1 << REFLECTION_FPRS_BITS) - 1;

   }
