/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/** 
 *  
 *  A forward reference has a machine-code-index source and normally a
 *  bytecode-index target.  The idea is to fix up the instruction at
 *  the source when the machine-code-index of the target is known.  There
 *  need not be an explicit target, if the reference is used within the
 *  machine-code for one bytecode.
 *  
 *  There are three kinds of forward reference:  
 *    1) unconditional branches
 *    2) conditional branches
 *    3) switch cases
 *  Each subclass must be able to resolve itself.
 *  
 *  This class also includes the machinery for maintaining a priority
 *  queue of forward references.  Let frq be such a priority queue and
 *  fr be a new forward reference.  The following code segment adds fr
 *  to the priority queue:
 *  
 *    if (frq == null) frq = fr;
 *    else frq = frq.add(fr);
 *  
 *  To resolve any forward references with target as the current bytecode:
 *  
 *    if (frq != null) frq = frq.checkResolveUpdate(currentBytecodeIndex, currentMachinecodeIndex);
 *  
 *  or:
 *  
 *    if (frq != null && currentBytecodeIndex == frq.targetBytecodeIndex) {
 *      frq = frq.resolveUpdate(currentMachinecodeIndex);
 *    }
 *
 *  The priority queue is implemented as a one-way linked list of forward
 *  references with strictly increasing targets.  The link for this list
 *  is next.  A separate linked list (other is the link) contains all
 *  forward references with the same target.
 *
 * @author Bowen Alpern
 * @author Anthony Cocchi
 * @author Derek Lieber
 */
public abstract class VM_ForwardReference {

  int targetBytecodeIndex;
  int sourceMachinecodeIndex;

  /* data structure support */
  VM_ForwardReference next;  // has next larger targetBytecodeIndex
  VM_ForwardReference other; // has the same    targetBytecodeIndex

  VM_ForwardReference (int source, int btarget) {
    sourceMachinecodeIndex = source;
    targetBytecodeIndex = btarget;
  }
  
  /* no target; 
   * for use within cases of the main compiler loop
   */
  VM_ForwardReference (int source) {
    sourceMachinecodeIndex = source;
  }

  /**  
   *  Normally, this method will be overridden by the subclass.
   *  Such methods must resolve other references with the same
   *  target index AFTER handling thier own update.
   */ 
  abstract void resolve (VM_MachineCode code, int target);

  final VM_ForwardReference checkResolveUpdate 
      (VM_MachineCode code, int btarget, int target) {
    if (targetBytecodeIndex == btarget) return resolveUpdate(code, target);
    return this;
  }

  final VM_ForwardReference resolveUpdate (VM_MachineCode code, int target) {
    if (VM.TraceAssembler) 
      System.out.print("[" + targetBytecodeIndex + "]");
    VM_ForwardReference fr = this;
    while (fr != null) {
      fr.resolve(code, target);
      fr = fr.other;
    }
    if (VM.TraceAssembler) System.out.println("");
    return next;
  }

  final VM_ForwardReference add (VM_ForwardReference fr) {
    if (targetBytecodeIndex > fr.targetBytecodeIndex) {
      fr.next = this;
      return fr;
    } else if (targetBytecodeIndex == fr.targetBytecodeIndex) {
      fr.other = this;
      fr.next  = next;
      return fr;
    } else { 
      insert(fr);
      return this;
    }
  }

  private final void insert (VM_ForwardReference fr) {
    if (VM.VerifyAssertions) VM.assert(targetBytecodeIndex < fr.targetBytecodeIndex);
    VM_ForwardReference a = this;  // fr.targetBytecodeIndex > a.targetBytecodeIndex
    while (a.next != null && a.next.targetBytecodeIndex < fr.targetBytecodeIndex) {
      a = a.next;
    }
    if (a.next == null) {
      a.next = fr;
    } else if (a.next.targetBytecodeIndex == fr.targetBytecodeIndex) {
      fr.other = a.next;
      fr.next  = a.next.next;
      a.next  = fr;
    } else { 
      fr.next = a.next;
      a.next  = fr;
    } 
  } 

  public final String toString () {
    return "[" + VM_Assembler.hex(sourceMachinecodeIndex<<2) + "->" + targetBytecodeIndex + "]" + (next == null ? "" : ( " (" + next.targetBytecodeIndex + ")" ));
  }
}
