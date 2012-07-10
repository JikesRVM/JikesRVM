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
package org.jikesrvm.classloader;

import java.lang.reflect.Member;

import org.jikesrvm.VM;

public class BootImageMemberLookupError extends Error {
  final Object o;
  final RVMMember rvmMember;
  final Member jdkMember;
  BootImageMemberLookupError(RVMMember rvmMember, Member jdkMember, Object o, Throwable t) {
    super(t);
    if (VM.VerifyAssertions && VM.runningVM) {
      VM._assert(VM.NOT_REACHED);
    }
    this.rvmMember = rvmMember;
    this.jdkMember = jdkMember;
    this.o = o;
  }

  @Override
  public String getMessage() {
    return "Unable to find (RVM): " + rvmMember + " in JDK by reflection (" +
    jdkMember + ") for object "+ (o != null ? o.getClass().toString() : "") + " : " + o;
  }
}
