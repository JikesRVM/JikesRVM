/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Extend java.util.Enumeration to avoid downcasts from object.
 * Also provide a preallocated empty basic block enumeration.
 *
 * @author Dave Grove
 */
public interface OPT_BasicBlockEnumeration extends java.util.Enumeration {
  /**
   * Same as nextElement but avoid the need to downcast from Object.
   */
  public OPT_BasicBlock next();

  /**
   * Single preallocated empty OPT_BasicBlockEnumeration.
   * WARNING: Think before you use this; getting two possible concrete
   * types may prevent inlining of hasMoreElements and next(), thus
   * blocking scalar replacement.  Only use Empty when we have no hope
   * of scalar replacing the alternative (real) enumeration object.
   */
  public static final OPT_BasicBlockEnumeration Empty = new OPT_EmptyBasicBlockEnumeration();
}
