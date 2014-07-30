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
 * This interface is temporary. It serves to identify all graph classes that
 * need object scratch fields. It extends GraphNode because no clients use
 * GraphElement but not GraphNode.
 *
 * @deprecated New classes <em>MUST NOT</em> implement this interface. It is only intended as an
 *  intermediate step during removal of scratch fields.
 */
@Deprecated
public interface GraphNodeWithObjectScratch extends GraphNode {

  /**
   * Sets the scratch object field.
   * @param scratchObject an object
   * @deprecated see class JavaDoc
   */
  @Deprecated
  void setScratchObject(Object scratchObject);

  /**
   * Gets the scratch object field.
   * @return the scratch object
   * @deprecated see class JavaDoc
   */
  @Deprecated
  Object getScratchObject();

}
