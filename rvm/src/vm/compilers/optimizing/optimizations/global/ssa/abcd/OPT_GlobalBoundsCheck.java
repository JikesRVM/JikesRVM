/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.*;
import  java.math.*;
import instructionFormats.*;

/**
 * ABCD: Array Bound Check elimination on Demand
 *
 * @author Rastislav Bodik
 * @modified Stephen Fink
 *
 * <ul>
 * <li> TODO: bound checks with a constant arg
 * <li> TODO: order in which bound checks are optimized
 * <li> TODO: profiling and speculative PRE
 * <li> TODO: handle arraylength that is a known constant
 * <li> TODO: exploit x!=c tests (eg i=N; while (i!=0) A[--i];
 * <li> TODO: exploit array SSA and GVN
 * </ul>
 */
final class OPT_GlobalBoundsCheck extends OPT_OptimizationPlanCompositeElement {

  /**
   * Construct this phase as a composite of several other phases.
   */
  OPT_GlobalBoundsCheck () {
    super("Global Bounds Check", new OPT_OptimizationPlanElement[] {
      // 1. Set up IR state to control SSA translation as needed
      new OPT_OptimizationPlanAtomicElement(new ABCDPreparation()), 
      // 2. Get the desired SSA form
      new OPT_OptimizationPlanAtomicElement(new OPT_EnterSSA()), 
      // 3. GVN
      new OPT_OptimizationPlanAtomicElement(new OPT_GlobalValueNumber()), 
          // 4. Perform bounds-check elimination
      new OPT_OptimizationPlanAtomicElement(new ABCD())
    });
  }

  /**
   * Redefine shouldPerform so that none of the subphases will occur
   * unless we pass through this test.
   */
  boolean shouldPerform (OPT_Options options) {
    return  options.GLOBAL_BOUNDS_CHECK;
  }

  /**
   * define the compiler phase that actually performs the optimization.
   */
  private static final class ABCD extends OPT_CompilerPhase
      implements OPT_Operators {

    final boolean shouldPerform (OPT_Options options) {
      return  options.GLOBAL_BOUNDS_CHECK;
    }

    final String getName () {
      return  "GBoundsCheck";
    }

    final String getPaddedName () {
      return  "GBoundsCheck\t";
    }

    final boolean printingEnabled (OPT_Options options, boolean before) {
      return  false;
    }
    /**
     * add transitive edges?
     */
    private boolean addEdges = false;           
    /**
     * resolved + pending constraints
     * key:   the constraint var <= arr.length - c
     * value: answer (of type ABCD_Answer)
     */
    private java.util.HashMap answers;
    /**
     * Transitive closure edges added to the iG on demand
     * key: instruction (representing the USE of a variable)
     */
    private java.util.HashMap closures;

    /**
     * value: 
     */
    class ABCD_ClosureValue {
      /**
       * edge label
       */
      int c;                    
      /**
       * source of edge
       */
      OPT_Instruction src;      
      /**
       * for phi's: index of phi's argument
       */
      int ix;                   
      ABCD_ClosureValue next = null;

      ABCD_ClosureValue (int cc, int ixx, OPT_Instruction srcc, 
          ABCD_ClosureValue nextt) {
        c = cc;
        ix = ixx;
        src = srcc;
        next = nextt;
      }
    }
    /**
     * The sentinel for reducing cycles in constraints
     * May need to be replaced with a constraints, for higher precision
     */
    private OPT_Instruction currentLoopHeader;
    /**
     * the method being optimized
     */
    private OPT_IR ir;          

    /**
     *  Encodes the constraint: var <= arr.length + c.
     *  The constraint is sometimes viewed as query (to reflect the demand 
     *  nature of the algorithm) and sometimes as an edge in the IG graph. 
     */
    final class ABCD_ConstraintKey {
      public OPT_RegisterOperand var;
      public OPT_RegisterOperand arr;

      ABCD_ConstraintKey (OPT_RegisterOperand cvar, OPT_RegisterOperand carr) {
        var = cvar;
        arr = carr;
      }

      public int hashCode () {
        return  var.register.hashCode() + arr.register.hashCode();
      }

      public boolean equals (Object o) {
        return  (o instanceof ABCD_ConstraintKey) 
            && ((ABCD_ConstraintKey)o).var.similar(var)
            && ((ABCD_ConstraintKey)o).arr.similar(arr);
      }

      public ABCD_ConstraintKey copy () {
        return  new ABCD_ConstraintKey(var, arr);
      }

      public String toString () {
        String s = var.asRegister().register.toString();
        s += "<=" + arr.asRegister().register.toString();
        return  s;
      }
    }

    final class ABCD_Constraint {
      public ABCD_ConstraintKey key;
      public int c;

      ABCD_Constraint (OPT_RegisterOperand var, int cc, 
          OPT_RegisterOperand arr) {
        key = new ABCD_ConstraintKey(var, arr);
        c = cc;
      }

      public int hashCode () {
        return  key.hashCode() + c;
      }

      public boolean equals (Object o) {
        return  (o instanceof ABCD_Constraint) 
            && ((ABCD_Constraint)o).key.equals(key)
            && ((ABCD_Constraint)o).c == c;
      }

      public ABCD_Constraint copy () {
        return  new ABCD_Constraint(key.var, c, key.arr);
      }

      public OPT_Instruction def () {
        if (key.var.register == null)
          return  null;
        OPT_Operand o = key.var.register.defList;
        if (o != null)
          return  o.instruction; 
        else 
          return  null;
      }

      public String toString () {
        String s = key.toString();
        if (c >= 0)
          s += "+";
        s += c;
        return  s;
      }
    }

    /**
     * An answer to constraints, stored in java.util.HashMap 'answers'
     */
    static class ABCD_Answer {
      /** 
       * not yet resolved 
       */
      static final char PENDING_ANSWER = 0;     
      /**
       * cycle reduced 
       */
      static final char TRUE_CYC_ANSWER = 1;    
      /**
       * proven
       */
      static final char TRUE_ANSWER = 2;        
      /**
       * could not be proven 
       */
      static final char FALSE_ANSWER = 3;       
      /**
       * could not be proven due to 
       * an amplifying loop
       */
      static final char BAD_FALSE_ANSWER = 4;   
      /**
       * the constraint for which we keep here the answer
       */
      public int c;      
      /**
       * the answer value
       */
      public char a;    
      /**
       * guard variables 
       * on which this answer depends
       */
      public java.util.HashSet guards = new java.util.HashSet();  

      /**
       * constructor for pending constraints
       */
      ABCD_Answer (ABCD_Constraint constraint) {
        a = PENDING_ANSWER;
        c = constraint.c;
      }

      /**
       * constructor for resolved constraints
       */
      ABCD_Answer (ABCD_Constraint constraint, char answer) {
        a = answer;
        c = constraint.c;
      }

      /**
       * Note that this answer depends on a guard operand
       */
      void noteGuard (OPT_RegisterOperand guard) {
        guards.add(guard);
      }

      /**
       * Return the set of guards this answer depends on
       */
      public java.util.HashSet getGuards () {
        return  guards;
      }

      boolean isPending () {
        return  a == PENDING_ANSWER;
      }

      boolean isReduced () {
        return  a == TRUE_CYC_ANSWER;
      }

      boolean isResolved () {
        return  a == TRUE_ANSWER || a == FALSE_ANSWER || a == BAD_FALSE_ANSWER;
      }

      boolean isWeakerThan (ABCD_Constraint constraint) {
        return  c > constraint.c;
      }

      boolean isWeakerEqThan (ABCD_Constraint constraint) {
        return  c >= constraint.c;
      }
      private int id = era;         
      static private int era = 0;   

      boolean isCurrent () {
        return  id == era;
      }

      static void nextEra () {
        era++;
      }

      void makePending (ABCD_Constraint constraint) {
        // reanalyze: switch back to pending and 
        // decrease the constant c (when strengthening the true answer)
        // increase the constant c (when strengthening the false answer)
        if (a == TRUE_ANSWER && constraint.c < c || (a == FALSE_ANSWER
            || a == BAD_FALSE_ANSWER) && constraint.c > c)
          c = constraint.c;
        a = PENDING_ANSWER;
      }

      boolean getResult () {
        if (a == TRUE_ANSWER || a == TRUE_CYC_ANSWER)
          return  true;
        if (a == FALSE_ANSWER || a == BAD_FALSE_ANSWER)
          return  false;
        // should not happen: throw exception!
        throw  new OPT_OptimizingCompilerException();
      }

      /**
       * All-path merge:
       * for this meet operator, the lattice is FALSE, TRUE_CYC, TRUE
       * @param result
       */
      void andMerge (ABCD_Answer result) {
        //
        if (result.a == PENDING_ANSWER)
          throw  new OPT_OptimizingCompilerException();
        if (a == BAD_FALSE_ANSWER || result.a == BAD_FALSE_ANSWER)
          a = BAD_FALSE_ANSWER; 
        else if (a == FALSE_ANSWER || result.a == FALSE_ANSWER)
          a = FALSE_ANSWER; 
        else if (a == TRUE_CYC_ANSWER || result.a == TRUE_CYC_ANSWER) {
          a = TRUE_ANSWER;
          // inherit the guards from result 
          guards.addAll(result.guards);
        } 
        else {
          // must be TRUE/TRUE
          a = TRUE_CYC_ANSWER;
          // inherit the guards from result 
          guards.addAll(result.guards);
        }
      }

      /**
       * Best-path merge:
       * for this meet operator, the lattice is TRUE, TRUE_CYC, FALSE
       * @param result
       */
      void orMerge (ABCD_Answer result) {
        if (result.a == PENDING_ANSWER)
          throw  new OPT_OptimizingCompilerException();
        if (a == TRUE_ANSWER || result.a == TRUE_ANSWER) {
          a = TRUE_ANSWER;
          // inherit the guards from result 
          guards.addAll(result.guards);
        } 
        else if (a == TRUE_CYC_ANSWER || result.a == TRUE_CYC_ANSWER) {
          a = TRUE_CYC_ANSWER;
          // inherit the guards from result 
          guards.addAll(result.guards);
        } 
        else if (a == FALSE_ANSWER || result.a == FALSE_ANSWER) {
          a = FALSE_ANSWER;
        } 
        else {
          // must be BAD_FALSE/BAD_FALSE
          a = BAD_FALSE_ANSWER;
        }
      }

      public String toString () {
        String s;
        switch (a) {
          case PENDING_ANSWER:
            s = new String("PENDING   ");
            break;
          case TRUE_CYC_ANSWER:
            s = new String("TRUE-CYC  ");
            break;
          case TRUE_ANSWER:
            s = new String("TRUE      ");
            break;
          case FALSE_ANSWER:
            s = new String("FALSE     ");
            break;
          case BAD_FALSE_ANSWER:
            s = new String("BAD-FALSE ");
            break;
          default:
            s = new String("BAD VALUE ");
            break;
        }
        s = s + c + " (" + id + ")   ";
        for (java.util.Iterator it = guards.iterator(); it.hasNext();) {
          s = s + it.next();
        }
        return  s;
      }
    }

    /** 
     * Perform the optimization
     *
     * <p> PRECONDITIONS: 
     *	<ul>
     *	 <li> Ir in SSA form
     *   <li> OPT_ABCD_Setup called before going to SSA form
     *   <li> Global value numbers computed
     *   <li> Dominator tree computed
     *  </ul>
     *
     * @param ir the IR to optimize 
     */
    public void perform (OPT_IR irr) {
      ir = irr;
      // if the IR has no bounds checks, don't bother
      if (!OPT_IRSummary.hasBoundsCheck(irr))
        return;
      // if IR may be too big, give up
      if (ir.getNumberOfSymbolicRegisters() > 200) {
        OPT_PiNodes.cleanUp(ir);
        return;
      }
      if (ir.options.DEBUG_ABCD) {
        System.out.println("\n-----------------------------------------");
        System.out.println("GBoundsCheck: method = " + 
            ir.method.getDeclaringClass()
            + "  " + ir.method.name);
      }
      // flush cache on each method
      answers = new java.util.HashMap();
      closures = new java.util.HashMap();
      // recompute register lists
      OPT_DefUse.clearDU(ir);
      OPT_DefUse.computeDU(ir);
      if (ir.options.DEBUG_ABCD) {
        System.out.println("\nafter SSA with PI nodes");
        ir.printInstructions();
        System.out.println();
      }
      // iterate over all bound checks
      // TODO: find a suitable iteration order or
      // do better caching.
      java.util.Stack redundant = new java.util.Stack();
      for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); 
          e.hasMoreElements(); /* nothing */
      ) {
        OPT_BasicBlock bb = e.next();
        for (OPT_Instruction instr = bb.firstRealInstruction(), 
            nextInstr = null; 
            instr != null && instr.getBasicBlock() == bb; instr = nextInstr) {
          nextInstr = instr.nextInstructionInCodeOrder();
          OPT_Operator operator = instr.operator;
          if (operator != BOUNDS_CHECK)
            continue;
          if (ir.options.DEBUG_ABCD)
            System.out.println("\nGBoundsCheck: check: " + instr.bcIndex
                + "  " + instr);
          OPT_Operand index = BoundsCheck.getIndex(instr);
          // (VIVEK) TODO: extend to constant index
          if (!index.isRegister())
            continue;
          // uncomment the following line to turn off caching across
          // bound checks
          answers = new java.util.HashMap();
          // create the constraint (ie, query) to be proven
          ABCD_Constraint constraint = new ABCD_Constraint(index.asRegister(), 
              -1, BoundsCheck.getRef(instr).asRegister());
          try {
            ABCD_Answer.nextEra();
            closureSentinel = instr;            // see addClosureEdges()
            closureSentinelIdom = instr;
            closureSentinelIx = 0;
            closureConstraint = constraint;
            ABCD_Answer result = prove(constraint);
            // try any transitive edge emanating from the bound check
            // TODO: put the same at phi's arguments
            if (!result.getResult()) {
              ABCD_ClosureValue edges = (ABCD_ClosureValue)closures.get(instr);
              while (edges != null) {
                if (ir.options.DEBUG_ABCD)
                  System.out.println(depthString() + "trying edge (" + 
                      edges.c + ") " + edges.src + " => " + instr);
                constraint.key.var = (OPT_RegisterOperand)
                    edges.src.getOperand(0);
                constraint.c = -1 - edges.c;
                result = prove(constraint);
                if (result.getResult())
                  break;        // got TRUE/TRUE_CYC
                edges = edges.next;
              }
            }
            if (ir.options.DEBUG_ABCD)
              System.out.println("Result = " + result + "\n");
            if (result.getResult()) {
              // Remove bounds_check instruction
              // needs to delay removal because isBcPiNode() 
              // relies on having the bc's before the pi's
              redundant.push(new RedundantCheck(instr, result.getGuards()));
            }
          } catch (OPT_ABCDUnsupportedException ex) {
            // something went wrong.  give up.
            OPT_PiNodes.cleanUp(ir);
            return;
          }
        }
      }
      try {
        while (!redundant.empty()) {
          RedundantCheck rc = (RedundantCheck)redundant.pop();
          insertCompensatingGuards(rc);
          OPT_DefUse.removeInstructionAndUpdateDU(rc.check);
        }
      } catch (EmptyStackException e) {}
      OPT_PiNodes.cleanUp(ir);
    }           // perform()

    /**
     * Given a redundant bounds check and a set of guards on which it
     * depends, insert guard_move and guard_combine instructions as
     * needed to preserve code motion restrictions imposed by the
     * guards.
     * <p> Note: this routine preserves register lists.
     */ 
    private void insertCompensatingGuards (RedundantCheck rc) {
      OPT_Instruction check = rc.check;
      java.util.HashSet guards = rc.guards;
      OPT_Operand originalGuard = BoundsCheck.getGuard(check);
      OPT_Operand resultGuard = BoundsCheck.getGuardResult(check);
      int n = guards.size();
      java.util.Iterator it = guards.iterator();
      if (n == 0)
        return; 
      else if (n == 1) {
        // We depend on one guard, g1, and the BoundsCheck is 
        //   x1 = BC ...., x2
        //   insert the following instruction:
        //	x1 = GUARD_COMBINE x2, g1
        OPT_Operand g = (OPT_Operand)it.next();
        OPT_Instruction s = Binary.create(GUARD_COMBINE, 
            resultGuard.copy().asRegister(), 
            originalGuard.copy(), g.copy());
        check.insertBefore(s);
        OPT_DefUse.updateDUForNewInstruction(s);
      } 
      else {                    // n > 1
        //  suppose the guards we depend on are v1, v2, v3, v4,
        //  and the redundant bounds check is x1 = BC ..., x2
        //  Then insert the following code sequence:
        //	t1 = GUARD_COMBINE v1, v2
        //	t2 = GUARD_COMBINE t1, v3
        //	t3 = GUARD_COMBINE t2, v4
        //	x1 = GUARD_COMBINE x2, t3
        OPT_Operand g1 = (OPT_Operand)it.next();
        OPT_Operand g3 = null;
        while (it.hasNext()) {
          OPT_Operand g2 = (OPT_Operand)it.next();
          g3 = ir.regpool.makeTempValidation();
          OPT_Instruction s = Binary.create(GUARD_COMBINE, 
              g3.copy().asRegister(), 
              g1.copy(), g2.copy());
          check.insertBefore(s);
          OPT_DefUse.updateDUForNewInstruction(s);
          g1 = g2;
          g2 = g3;
        }
        OPT_Instruction s = Binary.create(GUARD_COMBINE, 
            resultGuard.copy().asRegister(), 
            originalGuard.copy(), g3.copy());
        check.insertBefore(s);
        OPT_DefUse.updateDUForNewInstruction(s);
      }
    }
    private int depth = 0;      // recursion depth

    private String depthString () {
      String s = new String("ABCD: ");
      int i = depth;
      while (i-- > 0)
        s += ". ";
      return  s;
    }

    /**
     * 
     */
    public ABCD_Answer prove (ABCD_Constraint constraint) {
      if (ir.options.DEBUG_ABCD)
        System.out.println(depthString() + "prove\t" + constraint);
      // Lookup the cache of pending and cached constraints
      ABCD_Answer answer = (ABCD_Answer)answers.get(constraint.key);
      // If neither cached nor pending, insert a pending constraint 
      if (answer == null) {
        if (ir.options.DEBUG_ABCD)
          System.out.println(depthString() + "inserting pending");
        answers.put(constraint.key, answer = new ABCD_Answer(constraint));
      } 
      // Is a result cached?
      else if (answer.isResolved()) {
        // If the cached result is TRUE for the same or stronger constraint
        // then we have a "prior" pozitive proof.
        // If the cached result is FALSE for the same or weaker constraint
        // then we have a "prior" negative proof.
        if (answer.getResult() && !answer.isWeakerThan(constraint) || 
            !answer.getResult() && answer.isWeakerEqThan(constraint)) {
          if (ir.options.DEBUG_ABCD)
            System.out.println(depthString() + "found cached result = "
                + answer);
          return  answer;
        } 
        else {
          // we need to strengthen the cached result, by reanalyzing
          if (ir.options.DEBUG_ABCD)
            System.out.println(depthString() + "reanalyzing " + constraint
                + " found only " + answer);
          answer.makePending(constraint);
        }
      } 
      // Does the cached result say that the query was successfully reduced?
      else if (answer.isReduced()) {
        // if the cached constraint is no stronger and is from same 
        // analysis (ie same traversal), use the cached TRUE_CYC result
        if (!answer.isWeakerThan(constraint) && answer.isCurrent()) {
          if (ir.options.DEBUG_ABCD)
            System.out.println(depthString() + "found cached reduced for "
                + constraint + " " + answer);
          return  answer;
        } 
        else {
          // the answer is either old and we need to resolve TRUE_CYC
          // to TRUE or FALSE, 
          // or the answer is weaker and we need to reanalyze.
          if (ir.options.DEBUG_ABCD)
            System.out.println(depthString() + "reanalyzing " + constraint
                + " found only " + answer);
          answer.makePending(constraint);
        }
      } 
      // If a pending constraint is found, then a cycle was detected
      else if (answer.isPending()) {
        // if the encountered pending constraint is weaker, then loop 
        // amplifies the constraint (ie generates a stronger constraint)
        if (answer.isWeakerThan(constraint)) {
          if (ir.options.DEBUG_ABCD)
            System.out.println(depthString() + "false: loop amplifies\t"
                + constraint + " answer: " + answer);
          return  new ABCD_Answer(constraint, ABCD_Answer.BAD_FALSE_ANSWER);
        } 
        // otherwise, we managed to "reduce" the loop.
        else {
          if (ir.options.DEBUG_ABCD)
            System.out.println(depthString() + "true-cyc: loop reduced, found "
                + answer);
          return  new ABCD_Answer(constraint, ABCD_Answer.TRUE_CYC_ANSWER);
        }
      } 
      else 
        // should not happen
        throw  new OPT_OptimizingCompilerException();
      depth++;
      ABCD_Answer result = substitute(constraint);
      // ** On demand cosntruction of the closure edges
      //
      // if failed to prove, add transitive closure edges 
      // between the current node and the phi or bc on top
      // of stack
      if (!result.getResult()) {
        if (constraint.def() != null && addEdges) {
          if (ir.options.DEBUG_ABCD)
            System.out.println(depthString() + "add edges" + constraint.def());
          addClosureEdges(constraint.def(), closureConstraint.c - constraint.c);
        }
      }
      depth--;
      // reset the constant value (is unnecessarily 
      // propagated from substitute (ie inherited from the descendant query)
      result.c = constraint.c;
      // cache the result ('ans' points to the answer cache)
      if (ir.options.DEBUG_ABCD)
        System.out.println(depthString() + "result = " + result);
      answers.put(constraint.key, result);
      return  result;
    }
    // Sentinel is the key to the hashtable
    // SentinelIdom is the real dominator tree sentinel
    /**
     * the phi or bc
     */
    private OPT_Instruction closureSentinel = null;  
    /**
     * index of phi arg
     */
    private int closureSentinelIx;              
    /**
     * node that idoms phi's arg
     */
    private OPT_Instruction closureSentinelIdom = null; 
    private ABCD_Constraint closureConstraint = null;          

    private void addClosureEdges (OPT_Instruction def, int dist) {
      if (false && ir.options.DEBUG_ABCD) {
        System.out.println(depthString() + "adding edges\t" + def);
        System.out.println(depthString() + " sent\t" + closureSentinel);
        System.out.println(depthString() + " sen ix\t" + closureSentinelIx);
        System.out.println(depthString() + " idom\t" + closureSentinelIdom);
        System.out.println(depthString() + " constr\t" + closureConstraint
            + "\n");
      }
      // The algorithm:
      //    Let DEF be the definition node of constraint.key.var
      //    Let TARGET be the location of the current target, ie
      //        the base constraint to be proven or the basic block of
      //        the argument of the most recent phi-node
      // 
      //    result = FALSE
      //    for each use U of DEF
      //       if U dominates TARGET 
      //           substitute constraint (with opposite sign)
      //           result = result || prove substituted constraint
      for (OPT_RegisterOperand u = def.getOperand(0).asRegister().
          register.useList; 
          u != null; u = u.getNext()) {
        OPT_Instruction use = u.instruction;
        // null when the instruction was removed
        if (use.prevInstructionInCodeOrder() == null)
          continue;
        int delta = forwSubstitute(use);
        // does USE strictly dominate the sentinel?
        if ((isDominatedBy(closureSentinelIdom, use) 
        // if use is in the same BB as sentinel
        // but past the sentinel, allow the edge 
        // if the use is stronger (this will 
        // guarantee that a bc will not be redundant
        // wrongly on itself)
        || (closureSentinelIdom.getBasicBlock() == use.getBasicBlock()
            // TODO: not sure the following rule is correct
        && dist - delta < 0)) 
        // avoid useless edges: 
        // a better check would be 'use is not PENDING'
        && use != closureSentinelIdom) {
          if (delta != 12345678) {
            ABCD_ClosureValue e, edge = (ABCD_ClosureValue)
                closures.get(closureSentinel);
            // is there such an edge already?
            for (e = edge; e != null; e = e.next) {
              if (e.src == use && e.ix == closureSentinelIx)
                break;
            }
            if (e != null) {
              // found such an edge, tighten the index
              if (e.c > dist - delta)
                e.c = dist - delta;
            } 
            else {
              // not found
              closures.put(closureSentinel, new ABCD_ClosureValue(dist
                  - delta, closureSentinelIx, use, edge));
              if (ir.options.DEBUG_ABCD)
                System.out.println(depthString() + "*** (" + (dist - delta)
                    + ")  def of " + use.getOperand(0) + " -> " 
                    + closureSentinel);
            }
            depth++;
            addClosureEdges(use, dist - delta);
            depth--;
          }
        }
      }
    }

    /**
     */
    private int forwSubstitute (OPT_Instruction instr) {
      OPT_Operator opr = instr.operator;
      if (opr == INT_ADD) {
        // check patterns  x = a + b:   is a or b a constant?
        OPT_Operand a = Binary.getVal1(instr);
        OPT_Operand b = Binary.getVal2(instr);
        if (a.isIntConstant() || b.isIntConstant()) {
          if (a.isIntConstant() || b.isRegister()) {
            return  ((OPT_IntConstantOperand)a).value;
          } 
          else if (a.isRegister() || b.isIntConstant()) {
            return  ((OPT_IntConstantOperand)b).value;
          }
        }
      }
      //
      // MOVE 
      // PI    (some moves represent pi nodes: handle the or character 
      //       of the demand analysis here)
      // (SJF: add some cases here?)
      //
      if (Move.conforms(instr) || instr.operator == PI) {
        return  0;
      }
      // any other opcode
      return  12345678;         // TODO: this is the worst hack!
    }

    private boolean isDominatedBy (OPT_Instruction a, OPT_Instruction b) {
      OPT_BasicBlock aBB = a.getBasicBlock();
      OPT_BasicBlock bBB = b.getBasicBlock();
      if (aBB == bBB) {
        do {
          a = a.prevInstructionInCodeOrder();
          if (a == b)
            return  true;
        } while (a != null && aBB == bBB);
        return  false;
      }
      int aNumber = aBB.getNumber();
      int bNumber = bBB.getNumber();
      OPT_DominatorTree dt = ir.HIRInfo.dominatorTree;
      return  dt.dominates(bNumber, aNumber);
    }

    /**
     * 
     */
    private ABCD_Answer substitute (ABCD_Constraint constraint) {
      // assume we can prove the constraint at each predecessor
      ABCD_Answer result = new ABCD_Answer(constraint, ABCD_Answer.TRUE_ANSWER);
      // Find the (unique) SSA definition of constraint.var
      OPT_RegisterOperand var = constraint.key.var;
      OPT_Instruction def = null;
      OPT_Operator opr = null;
      if (var.register.defList != null) {
        def = var.register.defList.instruction;
        opr = def.operator;
      }
      if (def == null) {
        if (ir.options.DEBUG_ABCD)
          System.out.println(depthString() + "false: no def\t" + constraint);
        result = new ABCD_Answer(constraint, ABCD_Answer.FALSE_ANSWER);
      } 
      // The main case statement
      else if (opr == ARRAYLENGTH) {
        // Here's where the constraint is answered
        // (another place is at check's pi nodes below)
        //
        // check if the constraint is a tautology:
        // to be a tautology, it must be of the form
        // arr_1.length <= arr_2.length + c
        // where c >= 0 and arr_1 == arr_2
        // 
        // When valu enumbering computed, use it
        if (ir.HIRInfo.valueNumbers != null) {
          if (ir.options.DEBUG_ABCD)
            System.out.println(depthString() + "arraylength reached GVN: "
                + constraint.key.arr + "(" 
                + ir.HIRInfo.valueNumbers.getValueNumber(constraint.key.arr)
                + ") vs. " + def.getOperand(1) + "(" 
                + ir.HIRInfo.valueNumbers.getValueNumber(def.getOperand(1))
                + ")");
          if (ir.HIRInfo.valueNumbers.getValueNumber(constraint.key.arr)
              == ir.HIRInfo.valueNumbers.getValueNumber(def.getOperand(1))
              && constraint.c >= 0) {
            if (ir.options.DEBUG_ABCD)
              System.out.println(depthString() + "true: arraylength reached\t"
                  + constraint);
            result = new ABCD_Answer(constraint, ABCD_Answer.TRUE_ANSWER);
          } 
          else {
            if (ir.options.DEBUG_ABCD)
              System.out.println(depthString() + "false: arraylength reached\t"
                  + constraint);
            result = new ABCD_Answer(constraint, ABCD_Answer.FALSE_ANSWER);
          }
        } 
        else {
          if (constraint.key.arr.similar(def.getOperand(1)) 
              && constraint.c >= 0) {
            if (ir.options.DEBUG_ABCD)
              System.out.println(depthString() + "true: arraylength reached\t"
                  + constraint);
            result = new ABCD_Answer(constraint, ABCD_Answer.TRUE_ANSWER);
          } 
          else {
            if (ir.options.DEBUG_ABCD)
              System.out.println(depthString() + "false: arraylength reached\t"
                  + constraint);
            result = new ABCD_Answer(constraint, ABCD_Answer.FALSE_ANSWER);
          }
        }
      } 
      else if (opr == PHI) {                    // phi or mu node
        // see addClosureEdges()
        OPT_Instruction tmp = closureSentinel;
        int tmpIx = closureSentinelIx;
        OPT_Instruction tmpIdom = closureSentinelIdom;
        ABCD_Constraint tmpCon = closureConstraint;
        int nops = def.getNumberOfOperands();
        for (int i = nops - 1; i > 0; i--) {
          // substitute in the constraint and prove it recursively
          ABCD_Constraint new_con = constraint.copy();
          // TODO: support int constants
          if (!def.getOperand(i).isRegister())
            throw new OPT_ABCDUnsupportedException();
          new_con.key.var = def.getOperand(i).asRegister();
          closureSentinel = def;
          closureSentinelIx = i;
          closureSentinelIdom = getPhiPred(def, i).lastRealInstruction();
          /* SJF (4/19/2000): the predecessor basic block might have no real
           instructions.  What should happen in this case?? */
          if (closureSentinelIdom == null) {
            throw  new OPT_ABCDUnsupportedException();
          }
          closureConstraint = new_con;
          result.andMerge(prove(new_con));
        }
        closureSentinel = tmp;
        closureSentinelIx = tmpIx;
        closureSentinelIdom = tmpIdom;
        closureConstraint = tmpCon;
      } 
      else if (opr == INT_ADD) {
        // check patterns  x = a + b:   is a or b a constant?
        OPT_Operand a = Binary.getVal1(def);
        OPT_Operand b = Binary.getVal2(def);
        if (a.isIntConstant() || b.isIntConstant()) {
          ABCD_Constraint new_con = constraint.copy();
          if (a.isIntConstant() || b.isRegister()) {
            new_con.c -= ((OPT_IntConstantOperand)a).value;
            new_con.key.var = b.asRegister();
          } 
          else if (a.isRegister() || b.isIntConstant()) {
            new_con.c -= ((OPT_IntConstantOperand)b).value;
            new_con.key.var = a.asRegister();
          } 
          else {
            if (ir.options.DEBUG_ABCD)
              System.out.println(depthString() + "false: bad add\t" + 
                  constraint);
            result = new ABCD_Answer(constraint, ABCD_Answer.FALSE_ANSWER);
          }
          // modify the constraint and prove it recursively
          result = prove(new_con);
        } 
        else {
          if (ir.options.DEBUG_ABCD)
            System.out.println(depthString() + "false: bad add\t" + constraint);
          result = new ABCD_Answer(constraint, ABCD_Answer.FALSE_ANSWER);
        }
      } 
      //
      // MOVE 
      // PI    (some moves represent pi nodes: handle the or character 
      //       of the demand analysis here)
      //
      else if (Move.conforms(def) || def.operator == PI) {
        // substitute in the constraint and prove it recursively
        ABCD_Constraint newCon1 = constraint.copy();
        OPT_Instruction bcInstr;
        OPT_Operand val = null;
        if (Move.conforms(def)) {
          val = Move.getVal(def);
        } 
        else {                  // PI
          val = GuardedUnary.getVal(def);
        }
        if (!val.isRegister()) {
          // we are assuming here the most conservative case of 
          // array length (ie 0)
          if (val.isIntConstant()) {
            int rhs = val.asIntConstant().value;
            if (rhs <= constraint.c)
              result = new ABCD_Answer(constraint, ABCD_Answer.TRUE_ANSWER); 
            else 
              result = new ABCD_Answer(constraint, ABCD_Answer.FALSE_ANSWER);
          } 
          else {
            if (ir.options.DEBUG_ABCD)
              System.out.println(depthString() + "false: bad constant move\t"
                  + constraint);
            result = new ABCD_Answer(constraint, ABCD_Answer.FALSE_ANSWER);
          }
        } 
        // now check if def is a bounds check's PI node :
        else if (null != (bcInstr = isBcPiNode(def)) && 
        // Here's another place where the constraint is answered
        constraint.key.arr.similar(
            BoundsCheck.getRef(bcInstr)) && constraint.c >= -1) {
          if (ir.options.DEBUG_ABCD)
            System.out.println(depthString() + "true: bc pi reached\t"
                + constraint);
          result = new ABCD_Answer(constraint, ABCD_Answer.TRUE_ANSWER);
          // we will attempt to prove the constraint recursively
          // using the result of a PI-Node.  So, the resulting
          // answer depends on the guard node controlling the PI.
          result.noteGuard(GuardedUnary.getGuard(def).asRegister());
        } 
        // now check if def is an IF's PI node and also handle regular move ops
        else {
          ABCD_Constraint newCon2 = null;
          // perform substitution
          newCon1.key.var = val.asRegister();
          OPT_Instruction ifInstr = isNotTakenPiNode(def);
          // when if_instr is non-null, it points to pi-node's conditional
          if (ifInstr != null) {
            if (ir.options.DEBUG_ABCD)
              System.out.println(depthString() + "pi-node(f)\t\t\t" + 
                  ifInstr);
            // passing newCon1, not constraint, because we need
            // the operand of the move in the constraint
            newCon2 = substituteIf(newCon1, ifInstr, true);
          }
          ifInstr = isTakenPiNode(def);
          if (ifInstr != null) {
            if (ir.options.DEBUG_ABCD)
              System.out.println(depthString() + "pi-node(t)\t\t\t" + 
                  ifInstr);
            newCon2 = substituteIf(newCon1, ifInstr, false);
          }
          if (newCon2 != null) {
            result = prove(newCon2);
            // we will attempt to prove the constraint recursively
            // using the result of a PI-Node.  So, the resulting
            // answer depends on the guard node controlling the PI.
            result.noteGuard(GuardedUnary.getGuard(def).asRegister());
            // shortcircuiting: call prove on the first argument 
            // only when not true already 
            if (!result.isResolved() || !result.getResult())
              // not resolved == TRUE_CYC (otherwise must be TRUE/FALSE)
              result.orMerge(prove(newCon1));
          } 
          else {
            // the default for all Moves (Not PIs)
            result = prove(newCon1);
          }
        }
      } 
      else {                    // any other opcode
        if (ir.options.DEBUG_ABCD)
          System.out.println(depthString() 
              + "false: bad opcode\t" + constraint);
        result = new ABCD_Answer(constraint, ABCD_Answer.FALSE_ANSWER);
      }
      return  result;
    }

    /**
     * @returns a new query, if the IF instructions "adds" an edge to the PI
     * @returns node. Null otherwise.
     */
    private ABCD_Constraint substituteIf (ABCD_Constraint con, 
        OPT_Instruction ifInstr, 
        boolean fallthru) {
      OPT_RegisterOperand var = con.key.var;
      OPT_Operand val1 = IfCmp.getVal1(ifInstr);
      OPT_Operand val2 = IfCmp.getVal2(ifInstr);
      OPT_ConditionOperand cond =
	(OPT_ConditionOperand)IfCmp.getCond(ifInstr).copy();
      ABCD_Constraint newCon = con.copy();
      // TODO: exploit this constant
      if (val1.isConstant() || val2.isConstant())
        return  null;
      // normalize the conditional
      if (var.similar(val1)) {
        newCon.key.var = val2.asRegister();
      } 
      else if (var.similar(val2)) {
        newCon.key.var = val1.asRegister();
        cond.flipOperands();
      } 
      else {
        System.out.println("ABCD: substituteIf(): Internal error" + ifInstr);
        throw  new OPT_OptimizingCompilerException();
      }
      if (fallthru) {
        // fall-through branch: negate the condition
        cond.flipCode();
      }
      switch (cond.value) {
        case OPT_ConditionOperand.EQUAL:
          break;
        case OPT_ConditionOperand.LESS_EQUAL:
          break;
        case OPT_ConditionOperand.LESS:
          newCon.c++;
          break;
        case OPT_ConditionOperand.NOT_EQUAL:
          newCon = null;
          break;
        case OPT_ConditionOperand.GREATER:
          newCon = null;
          break;
        case OPT_ConditionOperand.GREATER_EQUAL:
          newCon = null;
          break;
        default:
          if (ir.options.DEBUG_ABCD)
            System.out.println("ABCD: *** Unfriendly If " + ifInstr);
      }
      return  newCon;
    }

    /**
     * If def is a NOT-TAKEN Pi node, return the conditional branch
     * instruction that is not taken.
     * else return null;
     */
    private OPT_Instruction isNotTakenPiNode (OPT_Instruction def) {
      if (def.operator != PI)
        return  null;
      OPT_Operand g = GuardedUnary.getGuard(def);
      if (g.asRegister().isNotTaken()) {
        // return the (unique) instruction that defs the guard
        return  g.asRegister().register.defList.instruction;
      } 
      else {
        return  null;
      }
    }

    /**
     * If def is a TAKEN Pi node, return the conditional branch
     * instruction that is taken.
     * else return null;
     */
    private OPT_Instruction isTakenPiNode (OPT_Instruction def) {
      if (def.operator != PI)
        return  null;
      OPT_Operand g = GuardedUnary.getGuard(def);
      if (g.asRegister().isTaken()) {
        // return the (unique) instruction that defs the guard
        return  g.asRegister().register.defList.instruction;
      } 
      else {
        return  null;
      }
    }

    /**
     *  is the argument a pi-node inserted after a bounds check?
     *  if so, return the bounds-check instruction
     *  else return null;
     */
    private OPT_Instruction isBcPiNode (OPT_Instruction def) {
      if (def.operator != PI)
        return  null;
      OPT_Operand g = GuardedUnary.getGuard(def);
      if (g.asRegister().isBoundsCheck()) {
        // return the (unique) instruction that defs the guard
        return  g.asRegister().register.defList.instruction;
      } 
      else {
        return  null;
      }
    }

    /**
     * return basic block that is the i-th predecessor
     * of phi's basic block, according to the order of 
     * phi arguments.
     */
    private OPT_BasicBlock getPhiPred (OPT_Instruction phi, int i) {
      OPT_BasicBlock defBB;
      if (phi.getOperand(i).asRegister().register.defList == null) {
        // the operand does not have definition, it should
        // be a formal argument of the method
        // TODO: check this is true!
        defBB = null;
        // its definition dominates the entire method
      } 
      else {
        defBB = phi.getOperand(i).asRegister().register.defList.
            instruction.getBasicBlock();
      }
      int nops = phi.getNumberOfOperands();
      OPT_BasicBlockEnumeration f = phi.getBasicBlock().getIn();
      for (int j = 1; j < i; j++) {
        if (!f.hasMoreElements())
          throw  new OPT_OptimizingCompilerException();
        f.next();
      }
      OPT_BasicBlock pred = f.next();
      if (defBB != null && !OPT_LTDominatorInfo.isDominatedBy(pred, defBB))
        throw  new OPT_OptimizingCompilerException();
      if (pred == null)
        throw  new OPT_OptimizingCompilerException();
      return  pred;
    }

    private ABCD_Answer reduce (OPT_Instruction loop, 
        ABCD_Constraint constraint) {
      // interrupt reduction of the current loop, by remembering the 
      // current loop sentinel (there is one recursive invocation of 
      // reduce() for each nested loop).
      OPT_Instruction tmpLoopHeader = currentLoopHeader;
      currentLoopHeader = loop;
      depth++;
      if (ir.options.DEBUG_ABCD)
        System.out.println(depthString() + "reduce\t" + constraint + "\t"
            + loop);
      ABCD_Answer result = prove(constraint);
      if (ir.options.DEBUG_ABCD)
        System.out.println(depthString() + result);
      depth--;
      currentLoopHeader = tmpLoopHeader;
      return  result;
    }

    // a structure to hold information needed to remove a redundant
    // bounds check
    class RedundantCheck {
      OPT_Instruction check;                    // the redundant bounds-check
      java.util.HashSet guards;      // set of guards needed to prove redundancy

      RedundantCheck (OPT_Instruction check, java.util.HashSet guards) {
        this.check = check;
        this.guards = guards;
      }
    }
  }

  /**
   * The following class sets up the IR to prepare SSA form for
   * ABCD
   */
  private static class ABCDPreparation extends OPT_CompilerPhase {

    final boolean shouldPerform (OPT_Options options) {
      return  options.GLOBAL_BOUNDS_CHECK;
    }

    final String getName () {
      return  "ABCD Preparation";
    }

    final boolean printingEnabled (OPT_Options options, boolean before) {
      return  false;
    }

    final public void perform (OPT_IR ir) {
      // register in the IR the SSA properties we need for load
      // elimination
      ir.desiredSSAOptions.setScalarsOnly(true);
      ir.desiredSSAOptions.setBackwards(false);
      ir.desiredSSAOptions.setInsertUsePhis(false);
      ir.desiredSSAOptions.setHeapTypes(null);
    }
  }
}


/**
 * An exception class to throw when ABCD barfs for some reason.
 */
class OPT_ABCDUnsupportedException extends OPT_OptimizingCompilerException {}



