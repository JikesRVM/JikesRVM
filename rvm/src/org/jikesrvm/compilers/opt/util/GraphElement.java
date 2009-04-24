/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.util;


/**
 *  Many clients of graph methods expect their graph nodes to
 * implement a pair of scratch fields, one of int type and one of
 * object type.  This is a fairly evil thing to do, but it is deeply
 * embedded in many places, and this interface can be used for such
 * clients.  It is not recommended, to put it mildly.
 *
 * @deprecated
 *
 * @see Graph
 */
@Deprecated
public interface GraphElement {

  /**
   * read the scratch field of int type
   * @return the contents of the int scratch field
   * @deprecated
   */
  @Deprecated
  int getScratch();

  /**
   * set the scratch field of int type
   * @param scratch the new contents of the int scratch field
   * @deprecated
   */
  @Deprecated
  int setScratch(int scratch);
}
