/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.opt;

/**
 *  Many clients of graph methods expect their graph nodes to
 * implement a pair of scratch fields, one of int type and one of
 * object type.  This is a fairly evil thing to do, but it is deeply
 * embedded in many places, and this interface can be used for such
 * clients.  It is not recommended, to put it mildly.
 *
 * @deprecated
 *
 * @see OPT_Graph
 *
 * @author Julian Dolby
 *
 */
@Deprecated
interface OPT_GraphElement {

  /** 
   * read the scratch field of object type
   * @return the contents of the Object scratch field
   * @deprecated
   */
  @Deprecated
  Object getScratchObject ();

  /** 
   * set the scratch field of object type
   * @param obj the new contents of the Object scratch field
   * @deprecated
   */
  @Deprecated
  Object setScratchObject (Object obj);

  /** 
   * read the scratch field of int type
   * @return the contents of the int scratch field
   * @deprecated
   */
  @Deprecated
  int getScratch ();

  /** 
   * set the scratch field of int type
   * @param scratch the new contents of the int scratch field
   * @deprecated
   */
  @Deprecated
  int setScratch (int scratch);
}
