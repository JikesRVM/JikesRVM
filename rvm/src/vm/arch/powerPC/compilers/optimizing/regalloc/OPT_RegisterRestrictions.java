/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

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
}
