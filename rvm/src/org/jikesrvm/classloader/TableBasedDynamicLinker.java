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

import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.vmmagic.pragma.Entrypoint;

/**
 * Dynamic linking via indirection tables. <p>
 *
 * The main idea for dynamic linking is that we maintain
 * arrays of member offsets indexed by the member's
 * dynamic linking id. The generated code at a dynamically linked
 * site will load the appropriate value from the offset table and
 * check to see if the value is valid. If it is, then no dynamic linking
 * is required.  If the value is invalid, then resolveDynamicLink
 * is invoked to perform any required dynamic class loading.
 * After member resolution and class loading completes, we can
 * store the offset value in the offset table.
 * Thus when resolve method returns, execution can be restarted
 * by reloading/indexing the offset table. <p>
 *
 * NOTE: We believe that only use of invokespecial that could possibly
 * require dynamic linking is that of invoking an object initializer.
 */
public class TableBasedDynamicLinker implements Constants {

  /**
   * Linking table keyed by member reference IDs. Value indicates offset of
   * member or whether the member needs linking.
   */
  @Entrypoint
  private static int[] memberOffsets;

  static {
    memberOffsets = MemoryManager.newContiguousIntArray(32000);
    if (NEEDS_DYNAMIC_LINK != 0) {
      java.util.Arrays.fill(memberOffsets, NEEDS_DYNAMIC_LINK);
    }
  }

  /**
   * Cause dynamic linking of the RVMMember whose member reference id is given.
   * Invoked directly from (baseline) compiled code.
   * @param memberId the dynamicLinkingId of the method to link.
   * @return returns the offset of the member.
   */
  @Entrypoint
  public static int resolveMember(int memberId) throws NoClassDefFoundError {
    MemberReference ref = MemberReference.getMemberRef(memberId);
    return resolveMember(ref);
  }

  /**
   * Cause dynamic linking of the argument MemberReference
   * @param ref reference to the member to link
   * @return returns the offset of the member.
   */
  public static int resolveMember(MemberReference ref) throws NoClassDefFoundError {
    RVMMember resolvedMember = ref.resolveMember();
    RVMClass declaringClass = resolvedMember.getDeclaringClass();
    RuntimeEntrypoints.initializeClassForDynamicLink(declaringClass);
    int offset = resolvedMember.getOffset().toInt();
    if (VM.VerifyAssertions) VM._assert(offset != NEEDS_DYNAMIC_LINK);
    memberOffsets[ref.getId()] = offset;
    return offset;
  }

  /**
   * Method invoked from MemberReference to
   * ensure that there is space in the dynamic linking table for
   * the given member reference.
   */
  static synchronized void ensureCapacity(int id) {
    if (id >= memberOffsets.length) {
      int oldLen = memberOffsets.length;
      int[] tmp1 = MemoryManager.newContiguousIntArray((oldLen * 3) / 2);
      System.arraycopy(memberOffsets, 0, tmp1, 0, oldLen);
      if (NEEDS_DYNAMIC_LINK != 0) {
        java.util.Arrays.fill(tmp1, oldLen, tmp1.length, NEEDS_DYNAMIC_LINK);
      }
      Magic.sync(); // be sure array initialization is visible before we publish the reference!
      memberOffsets = tmp1;
    }
  }
}
