/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.jikesrvm.osr;

import com.ibm.jikesrvm.opt.ir.OPT_Instruction;
import java.util.*;

/**
 * OSR_VariableMap, non-encoded yet
 * OSR_VariableMap          ---> LinkedList of OSR_VariableMapElement
 * OSR_VariableMapElement   ---> (OsrPoint, LinkedList of MethodVariables)
 * OSR_MethodVariables      ---> (Method, PC, List of LocalRegTuple)
 * LocalRegTuple   ---> ( LocalNum, regOp, Type ) or ( StackNum, regOp, Type )
 * *
 *  @author Feng Qian
 */
public final class OSR_VariableMap {

  /* A list of OSR_VariableMapElement */
  public LinkedList<OSR_VariableMapElement> list = new LinkedList<OSR_VariableMapElement>();

  public final int getNumberOfElements() {
    return list.size();
  }

  /*
   * Inserts a new entry into the GCIRMap
   * @param inst      the IR instruction we care about
   * @param mvarList  the set of symbolic registers as a list
   */
  public void insert (OPT_Instruction inst, LinkedList<OSR_MethodVariables> mvarList) {
    // make a VariableMapElement and put it on the big list
    list.add(new OSR_VariableMapElement(inst, mvarList));
  }

  /**
   * Inserts a new entry at the begin of the list.
   */
  public void insertFirst (OPT_Instruction inst, LinkedList<OSR_MethodVariables> mvarList) {
    list.addFirst(new OSR_VariableMapElement(inst, mvarList));
  }

  /**
   * Creates and returns an enumerator for this object
   * @return an iterator for this object
   */
  public final ListIterator<OSR_VariableMapElement> iterator() {
    return list.listIterator(0);
  }

  /**
   * @return string version of this object
   */
  public String toString () {
    StringBuffer buf = new StringBuffer("");

    if (list.size() == 0)
      buf.append("empty"); 
    else {
      for (OSR_VariableMapElement ptr : list) {
        buf.append(ptr.toString());
      }
    }
    return  buf.toString();
  }
}



