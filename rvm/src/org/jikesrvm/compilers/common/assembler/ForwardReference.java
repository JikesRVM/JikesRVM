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
package org.jikesrvm.compilers.common.assembler;

import org.jikesrvm.VM;

/**
 *
 *  A forward reference has a machine-code-index source and optionally
 *  a bytecode-index target.  The idea is to fix up the instruction at
 *  the source when the machine-code-index of the target is known.
 *  There need not be an explicit target, if the reference is used (by
 *  the compiler) within the machine-code for one bytecode.
 *
 *  There are three kinds of forward reference:
 *    1) unconditional branches
 *    2) conditional branches
 *    3) switch cases
 *  Each subclass must be able to resolve itself.
 *
 *  This class also includes the machinery for maintaining a priority
 *  queue of forward references, priorities being target bytecode
 *  addresses.  The head of this priority queue is maintained by a
 *  Assembler object.
 *
 *  The priority queue is implemented as a one-way linked list of forward
 *  references with strictly increasing targets.  The link for this list
 *  is "next".  A separate linked list ("other" is the link) contains all
 *  forward references with the same target.
 */
public abstract class ForwardReference {

  final int sourceMachinecodeIndex;
  final int targetBytecodeIndex;     // optional

  /* Support for priority queue of forward references */
  /** Has next larger targetBytecodeIndex */
  ForwardReference next;
  /** Has the same targetBytecodeIndex */
  ForwardReference other;

  protected ForwardReference(int source, int btarget) {
    sourceMachinecodeIndex = source;
    targetBytecodeIndex = btarget;
  }

  /**
   * No target; for use within cases of the main compiler loop
   */
  protected ForwardReference(int source) {
    sourceMachinecodeIndex = source;
    targetBytecodeIndex = 0;
  }

  /**
   * Rewrite source to reference current machine code (in asm's machineCodes)
   */
  public abstract void resolve(AbstractAssembler asm);

  /**
   * Add a new reference r to a priority queue q
   * @return the updated queue
   */
  public static ForwardReference enqueue(ForwardReference q, ForwardReference r) {
    if (q == null) return r;
    if (r.targetBytecodeIndex < q.targetBytecodeIndex) {
      r.next = q;
      return r;
    } else if (r.targetBytecodeIndex == q.targetBytecodeIndex) {
      r.other = q.other;
      q.other = r;
      return q;
    }
    ForwardReference s = q;
    while (s.next != null && r.targetBytecodeIndex > s.next.targetBytecodeIndex) {
      s = s.next;
    }
    s.next = enqueue(s.next, r);
    return q;

  }

  /**
   * Resolve any forward references on priority queue q to bytecode index bi
   * @return queue of unresolved references
   */
  public static ForwardReference resolveMatching(AbstractAssembler asm, ForwardReference q, int bi) {
    if (q == null) return null;
    if (VM.VerifyAssertions) VM._assert(bi <= q.targetBytecodeIndex);
    if (bi != q.targetBytecodeIndex) return q;
    ForwardReference r = q.next;
    while (q != null) {
      q.resolve(asm);
      q = q.other;
    }
    return r;
  }

  public static final class UnconditionalBranch extends ForwardReference {

    public UnconditionalBranch(int source, int btarget) {
      super(source, btarget);
    }

    @Override
    public void resolve(AbstractAssembler asm) {
      asm.patchUnconditionalBranch(sourceMachinecodeIndex);
    }
  }

  public static final class ConditionalBranch extends ForwardReference {

    public ConditionalBranch(int source, int btarget) {
      super(source, btarget);
    }

    @Override
    public void resolve(AbstractAssembler asm) {
      asm.patchConditionalBranch(sourceMachinecodeIndex);
    }
  }

  // Cannot be made final; subclassed for PPC
  public static class ShortBranch extends ForwardReference {

    public ShortBranch(int source) {
      super(source);
    }

    public ShortBranch(int source, int btarget) {
      super(source, btarget);
    }

    @Override
    public void resolve(AbstractAssembler asm) {
      asm.patchShortBranch(sourceMachinecodeIndex);
    }
  }

  public static final class SwitchCase extends ForwardReference {

    public SwitchCase(int source, int btarget) {
      super(source, btarget);
    }

    @Override
    public void resolve(AbstractAssembler asm) {
      asm.patchSwitchCase(sourceMachinecodeIndex);
    }
  }

  public static final class LoadReturnAddress extends ForwardReference {

    public LoadReturnAddress(int source) {
      super(source);
    }

    public LoadReturnAddress(int source, int btarget) {
      super(source, btarget);
    }

    @Override
    public void resolve(AbstractAssembler asm) {
      asm.patchLoadReturnAddress(sourceMachinecodeIndex);
    }
  }
}
