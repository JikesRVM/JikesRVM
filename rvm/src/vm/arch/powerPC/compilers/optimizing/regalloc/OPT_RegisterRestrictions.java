/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * An instance of this class encapsulates restrictions on register
 * allocation.
 * 
 * @author Stephen Fink
 */
final class OPT_RegisterRestrictions extends OPT_GenericRegisterRestrictions {
  /**
   * Default Constructor
   */
  OPT_RegisterRestrictions(OPT_PhysicalRegisterSet phys) {
    super(phys);
  }
  /**
   * Is it forbidden to assign symbolic register symb to physical register r
   * in instruction s?
   */
  boolean isForbidden(OPT_Register symb, OPT_Register r,
                             OPT_Instruction s) {
    return false;
  }

}
