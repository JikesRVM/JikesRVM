/*
 * (C) Copyright IBM Corp. 2001
 */
//OPT_InstructionEnumeration.java
//$Id$
/**
 * Extend java.util.Enumeration to avoid downcasts from object.
 * Also provide a preallocated empty instruction enumeration.
 *
 * @author Dave Grove
 */

public interface OPT_InstructionEnumeration extends java.util.Enumeration {
   // Same as nextElement but avoid the need to downcast from Object
   public OPT_Instruction next();

   // Single preallocated empty OPT_InstructionEnumeration
    public static final OPT_InstructionEnumeration Empty = new OPT_EmptyInstructionEnumeration();
}

