/*
 * (C) Copyright IBM Corp. 2001
 */
//OPT_BasicBlockEnumeration
/**
 * Extend java.util.Enumeration to avoid downcasts from object.
 * Also provide a preallocated empty basic block enumeration.
 *
 * @author Dave Grove
 */

public interface OPT_BasicBlockEnumeration extends java.util.Enumeration {
  // Same as nextElement but avoid the need to downcast from Object
  public OPT_BasicBlock next();

  // Single, preallocated empty OPT_BasicBlockEnumeration
  public static final OPT_BasicBlockEnumeration Empty = new OPT_EmptyBasicBlockEnumeration();
}
