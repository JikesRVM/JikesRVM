/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;
import java.util.*;

/**
 * A class to hold variables for a method at one program point.
 *
 * @author Feng Qian
 */
public final class OSR_MethodVariables extends OPT_LinkedListElement {
  
  /* which method */
  public int methId;

  /* which program point */
  public int bcIndex;

  /* a list of variables */
  public LinkedList tupleList;

  public OSR_MethodVariables(int mid, int pc, LinkedList tupleList) {
    this.methId = mid;
    this.bcIndex = pc;
    this.tupleList = tupleList;
  }


  public final LinkedList getTupleList() {
    return tupleList;
  }

  public String toString () {
    StringBuffer buf = new StringBuffer("");
    
    buf.append(" pc@"+bcIndex 
	       + VM_MemberReference.getMemberRef(methId).getMemberName());
    buf.append("\n");
    for (int i=0, n=tupleList.size(); i<n; i++) {
      buf.append(tupleList.get(i).toString());
      buf.append("\n");
    }
    return  buf.toString();
  }
}



