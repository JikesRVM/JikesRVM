/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.opt.ir.OPT_Instruction;
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
  public LinkedList list = new LinkedList();

  public final int getNumberOfElements() {
    return list.size();
  }

  /*
   * Inserts a new entry into the GCIRMap
   * @param inst      the IR instruction we care about
   * @param mvarList  the set of symbolic registers as a list
   */
  public void insert (OPT_Instruction inst, LinkedList mvarList) {
    // make a VariableMapElement and put it on the big list
    list.add(new OSR_VariableMapElement(inst, mvarList));
  }

  /**
   * Inserts a new entry at the begin of the list.
   */
  public void insertFirst (OPT_Instruction inst, LinkedList mvarList) {
    list.addFirst(new OSR_VariableMapElement(inst, mvarList));
  }

  /**
   * Creates and returns an enumerator for this object
   * @return an iterator for this object
   */
  public final ListIterator iterator() {
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
      for (int i=0, n=list.size(); i<n; i++) {
        OSR_VariableMapElement ptr = (OSR_VariableMapElement)list.get(i); 
        buf.append(ptr.toString());
      }
    }
    return  buf.toString();
  }
}



