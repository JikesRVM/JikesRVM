/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

import java.lang.reflect.Member;

import org.jikesrvm.VM;

public class BootImageMemberLookupError extends Error {
  final Object o;
  final VM_Member rvmMember;
  final Member jdkMember;
  BootImageMemberLookupError(VM_Member rvmMember, Member jdkMember, Object o, Throwable t) {
    super(t);
    if (VM.runningVM) {
      VM._assert(VM.NOT_REACHED);
    }
    this.rvmMember = rvmMember;
    this.jdkMember = jdkMember;
    this.o = o;
  }

  public String getMessage() {
    return "Unable to find (RVM): " + rvmMember + " in JDK by reflection (" +
    jdkMember + ") for object "+ (o != null ? o.getClass().toString() : "") + " : " + o;
  }
}
