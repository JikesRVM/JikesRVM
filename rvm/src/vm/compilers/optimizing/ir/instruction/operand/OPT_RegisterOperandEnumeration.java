/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Extend java.util.Enumeration to avoid downcasts from object.
 *
 * @author Dave Grove
 */
public interface OPT_RegisterOperandEnumeration extends java.util.Enumeration {
  /** Same as nextElement but avoid the need to downcast from Object */
  public OPT_RegisterOperand next();
}

