/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;


/**
 *  This class is used only for the pre-allocated empty enumeration in
 * OPT_InstructionEnumeration.  It cannot be an anonymous class in
 * OPT_InstructionEnumeration because
 * OPT_InstructionEnumeration is an interface, and when javadoc
 * sees the anonymous class, it converts it into a private member of
 * the interface.  It then complains that interfaces cannot have
 * private members.  This is truly retarded, even by Java's low
 * standards.
 *
 * @author Julian Dolby 
 */
class OPT_EmptyInstructionEnumeration implements OPT_InstructionEnumeration {

    public boolean hasMoreElements() { return false; }

    public Object nextElement() { return next(); }

    public OPT_Instruction next() {
        throw new java.util.NoSuchElementException("Empty Instruction Enumeration");
    }
}

