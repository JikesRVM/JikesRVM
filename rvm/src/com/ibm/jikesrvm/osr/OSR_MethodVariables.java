/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */

package com.ibm.jikesrvm.osr;

import com.ibm.jikesrvm.classloader.*;
import java.util.*;

/**
 * A class to hold variables for a method at one program point.
 *
 * @author Feng Qian
 */
public final class OSR_MethodVariables {
  
  /* which method */
  public int methId;

  /* which program point */
  public int bcIndex;

  /* a list of variables */
  public LinkedList<OSR_LocalRegPair> tupleList;

  public OSR_MethodVariables(int mid, int pc, LinkedList<OSR_LocalRegPair> tupleList) {
    this.methId = mid;
    this.bcIndex = pc;
    this.tupleList = tupleList;
  }


  public final LinkedList<OSR_LocalRegPair> getTupleList() {
    return tupleList;
  }

  public String toString () {
    StringBuilder buf = new StringBuilder("");

    buf.append(" pc@").append(bcIndex).append(VM_MemberReference.getMemberRef(methId).getName());
    buf.append("\n");
    for (int i=0, n=tupleList.size(); i<n; i++) {
      buf.append(tupleList.get(i).toString());
      buf.append("\n");
    }
    return  buf.toString();
  }
}



