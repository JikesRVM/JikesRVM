/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM;

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
 *  VM_Assembler object.
 *
 *  The priority queue is implemented as a one-way linked list of forward
 *  references with strictly increasing targets.  The link for this list
 *  is "next".  A separate linked list ("other" is the link) contains all
 *  forward references with the same target.
 *
 * @author Julian Dolby
 * @author Bowen Alpern
 */
public abstract class VM_ForwardReference {

  int sourceMachinecodeIndex;
  int targetBytecodeIndex;     // optional

  /* support for priority queue of forward references */
  VM_ForwardReference next;  // has next larger targetBytecodeIndex
  VM_ForwardReference other; // has the same    targetBytecodeIndex

  protected VM_ForwardReference (int source, int btarget) {
    sourceMachinecodeIndex = source;
    targetBytecodeIndex = btarget;
  }
  
  /* no target; 
   * for use within cases of the main compiler loop
   */
  protected VM_ForwardReference (int source) {
    sourceMachinecodeIndex = source;
  }

  // rewrite source to reference current machine code (in asm's machineCodes)
  //
  public abstract void resolve (VM_Assembler asm);

  // add a new reference r to a priority queue q
  // return the updated queue
  //
  public static final VM_ForwardReference enqueue (VM_ForwardReference q, VM_ForwardReference r) {
    if (q == null) return r;
    if (r.targetBytecodeIndex < q.targetBytecodeIndex) {
      r.next = q;
      return r;
    } else if (r.targetBytecodeIndex == q.targetBytecodeIndex) {
      r.other = q.other;
      q.other = r;
      return q;
    } 
    VM_ForwardReference s = q;
    while (s.next != null && r.targetBytecodeIndex > s.next.targetBytecodeIndex) {
      s = s.next;
    }
    s.next = enqueue(s.next, r);
    return q;
  
  }

  // resolve any forward references on priority queue q to bytecode index bi
  // return queue of unresolved references
  //
  static VM_ForwardReference resolveMatching (VM_Assembler asm, VM_ForwardReference q, int bi) {
    if (q == null) return null;
    if (VM.VerifyAssertions) VM._assert(bi <= q.targetBytecodeIndex);
    if (bi != q.targetBytecodeIndex) return q;
    VM_ForwardReference r = q.next;
    while (q != null) {
      q.resolve(asm);
      q = q.other;
    }
    return r;
  }

  static class UnconditionalBranch extends VM_ForwardReference {

    UnconditionalBranch (int source, int btarget) {
      super(source, btarget);
    }

    public void resolve (VM_Assembler asm) {
      asm.patchUnconditionalBranch(sourceMachinecodeIndex);
    }

  }

  static class ConditionalBranch extends VM_ForwardReference {

    ConditionalBranch (int source, int btarget) {
      super(source, btarget);
    }

    public void resolve (VM_Assembler asm) {
      asm.patchConditionalBranch(sourceMachinecodeIndex);
    }

  }

  public static class ShortBranch extends VM_ForwardReference {

    ShortBranch (int source) {
      super(source);
    }

    public ShortBranch (int source, int btarget) {
      super(source, btarget);
    }

    public void resolve (VM_Assembler asm) {
      asm.patchShortBranch(sourceMachinecodeIndex);
    }

  }

  static class SwitchCase extends VM_ForwardReference {

    SwitchCase (int source, int btarget) {
      super(source, btarget);
    }

    public void resolve (VM_Assembler asm) {
      asm.patchSwitchCase(sourceMachinecodeIndex);
    }

  }

}
