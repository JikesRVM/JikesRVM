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
package org.jikesrvm.osr;

import java.util.LinkedList;
import java.util.ListIterator;
import org.jikesrvm.compilers.opt.ir.Instruction;

/**
 * VariableMap, non-encoded yet
 * VariableMap          ---> LinkedList of VariableMapElement
 * VariableMapElement   ---> (OsrPoint, LinkedList of MethodVariables)
 * MethodVariables      ---> (Method, PC, List of LocalRegTuple)
 * LocalRegTuple   ---> ( LocalNum, regOp, Type ) or ( StackNum, regOp, Type )
 * *
 */
public final class VariableMap {

  /* A list of VariableMapElement */
  public final LinkedList<VariableMapElement> list = new LinkedList<VariableMapElement>();

  public int getNumberOfElements() {
    return list.size();
  }

  /*
   * Inserts a new entry into the GCIRMap
   * @param inst      the IR instruction we care about
   * @param mvarList  the set of symbolic registers as a list
   */
  public void insert(Instruction inst, LinkedList<MethodVariables> mvarList) {
    // make a VariableMapElement and put it on the big list
    list.add(new VariableMapElement(inst, mvarList));
  }

  /**
   * Inserts a new entry at the begin of the list.
   */
  public void insertFirst(Instruction inst, LinkedList<MethodVariables> mvarList) {
    list.addFirst(new VariableMapElement(inst, mvarList));
  }

  /**
   * Creates and returns an enumerator for this object
   * @return an iterator for this object
   */
  public ListIterator<VariableMapElement> iterator() {
    return list.listIterator(0);
  }

  /**
   * @return string version of this object
   */
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder("");

    if (list.isEmpty()) {
      buf.append("empty");
    } else {
      for (VariableMapElement ptr : list) {
        buf.append(ptr.toString());
      }
    }
    return buf.toString();
  }
}



