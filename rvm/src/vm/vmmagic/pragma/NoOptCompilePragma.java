/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package org.vmmagic.pragma; 

import com.ibm.jikesrvm.classloader.*;
/**
 * This pragma indicates that a particular method should never be 
 * compiled by the optimizing compiler. It also implies that the
 * method will never be inlined by the optimizing compiler.
 * 
 * @author Dave Grove
 */
public class NoOptCompilePragma extends PragmaException {
  private static final VM_TypeReference me = getTypeRef("Lorg/vmmagic/pragma/NoOptCompilePragma;");
  public static boolean declaredBy(VM_Method method) {
    return declaredBy(me, method);
  }
}
