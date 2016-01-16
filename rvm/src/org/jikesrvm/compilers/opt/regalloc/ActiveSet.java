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
package org.jikesrvm.compilers.opt.regalloc;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.GenericPhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.util.GraphEdge;
import org.jikesrvm.compilers.opt.util.SpaceEffGraphNode;

/**
 * "Active set" for linear scan register allocation.
 * This version is maintained sorted in order of increasing
 * live interval end point.
 */
final class ActiveSet extends IncreasingEndMappedIntervalSet {
  /** Support for Set serialization */
  static final long serialVersionUID = 2570397490575294294L;
  /**
   * Governing ir
   */
  private final IR ir;

  private final RegisterAllocatorState regAllocState;

  /**
   * Manager of spill locations;
   */
  private final SpillLocationManager spillManager;

  /**
   * An object to help estimate spill costs
   */
  private final transient SpillCostEstimator spillCost;

  /**
   * Have we spilled anything?
   */
  private boolean spilled;

  ActiveSet(IR ir, SpillLocationManager sm, SpillCostEstimator cost) {
    super();
    spilled = false;
    this.ir = ir;
    this.regAllocState = ir.MIRInfo.regAllocState;
    this.spillManager = sm;
    this.spillCost = cost;
  }

  boolean spilledSomething() {
    return spilled;
  }

  /**
   *  For each new basic interval, we scan the list of active basic
   *  intervals in order of increasing end point.  We remove any "expired"
   *  intervals - those
   *  intervals that no longer overlap the new interval because their
   *  end point precedes the new interval's start point - and makes the
   *  corresponding register available for allocation
   *
   *  @param newInterval the new interval
   */
  void expireOldIntervals(BasicInterval newInterval) {

    for (Iterator<BasicInterval> e = iterator(); e.hasNext();) {
      MappedBasicInterval bi = (MappedBasicInterval) e.next();

      // break out of the loop when we reach an interval that is still
      // alive
      int newStart = newInterval.getBegin();
      if (bi.endsAfter(newStart)) break;

      if (LinearScan.VERBOSE_DEBUG) System.out.println("Expire " + bi);

      // note that the bi interval no longer is live
      freeInterval(bi);

      // remove bi from the active set
      e.remove();

    }
  }

  void freeInterval(MappedBasicInterval bi) {
    CompoundInterval container = bi.container;
    Register r = container.getRegister();

    if (r.isPhysical()) {
      r.deallocateRegister();
      return;
    }

    if (container.isSpilled(regAllocState)) {
      // free the spill location iff this is the last interval in the
      // compound interval.
      BasicInterval last = container.last();
      if (last.sameRange(bi)) {
        spillManager.freeInterval(container.getSpillInterval());
      }
    } else {
      // free the assigned register
      if (VM.VerifyAssertions) VM._assert(container.getAssignment(regAllocState).isAllocated());
      container.getAssignment(regAllocState).deallocateRegister();
    }

  }

  void allocate(BasicInterval newInterval, CompoundInterval container) {

    if (LinearScan.DEBUG) {
      System.out.println("Allocate " + newInterval + " " + container.getRegister());
    }

    Register r = container.getRegister();

    if (container.isSpilled(regAllocState)) {
      // We previously decided to spill the compound interval.  No further
      // action is needed.
      if (LinearScan.VERBOSE_DEBUG) System.out.println("Previously spilled " + container);
    } else {
      if (container.isAssigned(regAllocState)) {
        // The compound interval was previously assigned to a physical
        // register.
        Register phys = container.getAssignment(regAllocState);
        if (!currentlyActive(phys)) {
          // The assignment of newInterval to phys is still OK.
          // Update the live ranges of phys to include the new basic
          // interval
          if (LinearScan.VERBOSE_DEBUG) {
            System.out.println("Previously assigned to " +
                               phys +
                               " " +
                               container +
                               " phys interval " +
                               regAllocState.getInterval(phys));
          }
          updatePhysicalInterval(phys, newInterval);
          if (LinearScan.VERBOSE_DEBUG) {
            System.out.println(" now phys interval " + regAllocState.getInterval(phys));
          }
          // Mark the physical register as currently allocated
          phys.allocateRegister();
        } else {
          // The previous assignment is not OK, since the physical
          // register is now in use elsewhere.
          if (LinearScan.DEBUG) {
            System.out.println("Previously assigned, " + phys + " " + container);
          }
          // first look and see if there's another free register for
          // container.
          if (LinearScan.VERBOSE_DEBUG) System.out.println("Looking for free register");
          Register freeR = findAvailableRegister(container);
          if (LinearScan.VERBOSE_DEBUG) System.out.println("Free register? " + freeR);

          if (freeR == null) {
            // Did not find a free register to assign.  So, spill one of
            // the two intervals concurrently allocated to phys.

            CompoundInterval currentAssignment = getCurrentInterval(phys);
            // choose which of the two intervals to spill
            double costA = spillCost.getCost(container.getRegister());
            double costB = spillCost.getCost(currentAssignment.getRegister());
            if (LinearScan.VERBOSE_DEBUG) {
              System.out.println("Current assignment " + currentAssignment + " cost " + costB);
            }
            if (LinearScan.VERBOSE_DEBUG) {
              System.out.println("Cost of spilling" + container + " cost " + costA);
            }
            CompoundInterval toSpill = (costA < costB) ? container : currentAssignment;
            // spill it.
            Register p = toSpill.getAssignment(regAllocState);
            toSpill.spill(spillManager, regAllocState);
            spilled = true;
            if (LinearScan.VERBOSE_DEBUG) {
              System.out.println("Spilled " + toSpill + " from " + p);
            }
            CompoundInterval physInterval = regAllocState.getInterval(p);
            physInterval.removeAll(toSpill);
            if (LinearScan.VERBOSE_DEBUG) System.out.println("  after spill phys" + regAllocState.getInterval(p));
            if (toSpill != container) updatePhysicalInterval(p, newInterval);
            if (LinearScan.VERBOSE_DEBUG) {
              System.out.println(" now phys interval " + regAllocState.getInterval(p));
            }
          } else {
            // found a free register for container! use it!
            if (LinearScan.DEBUG) {
              System.out.println("Switch container " + container + "from " + phys + " to " + freeR);
            }
            CompoundInterval physInterval = regAllocState.getInterval(phys);
            if (LinearScan.DEBUG) {
              System.out.println("Before switch phys interval" + physInterval);
            }
            physInterval.removeAll(container);
            if (LinearScan.DEBUG) {
              System.out.println("Intervals of " + phys + " now " + physInterval);
            }

            container.assign(freeR);
            updatePhysicalInterval(freeR, container, newInterval);
            if (LinearScan.VERBOSE_DEBUG) {
              System.out.println("Intervals of " + freeR + " now " + regAllocState.getInterval(freeR));
            }
            // mark the free register found as currently allocated.
            freeR.allocateRegister();
          }
        }
      } else {
        // This is the first attempt to allocate the compound interval.
        // Attempt to find a free physical register for this interval.
        Register phys = findAvailableRegister(r);
        if (phys != null) {
          // Found a free register.  Perform the register assignment.
          container.assign(phys);
          if (LinearScan.DEBUG) {
            System.out.println("First allocation " + phys + " " + container);
          }
          updatePhysicalInterval(phys, newInterval);
          if (LinearScan.VERBOSE_DEBUG) System.out.println("  now phys" + regAllocState.getInterval(phys));
          // Mark the physical register as currently allocated.
          phys.allocateRegister();
        } else {
          // Could not find a free physical register.  Some member of the
          // active set must be spilled.  Choose a spill candidate.
          CompoundInterval spillCandidate = getSpillCandidate(container);
          if (VM.VerifyAssertions) {
            VM._assert(!spillCandidate.isSpilled(regAllocState));
            VM._assert((spillCandidate.getRegister().getType() == r.getType()) ||
                       (spillCandidate.getRegister().isNatural() && r.isNatural()));
            VM._assert(!ir.stackManager.getRestrictions().mustNotSpill(spillCandidate.getRegister()));
            if (spillCandidate.getAssignment(regAllocState) != null) {
              VM._assert(!ir.stackManager.getRestrictions().
                  isForbidden(r, spillCandidate.getAssignment(regAllocState)));
            }
          }
          if (spillCandidate != container) {
            // spill a previously allocated interval.
            phys = spillCandidate.getAssignment(regAllocState);
            spillCandidate.spill(spillManager, regAllocState);
            spilled = true;
            if (LinearScan.VERBOSE_DEBUG) {
              System.out.println("Spilled " + spillCandidate + " from " + phys);
            }
            CompoundInterval physInterval = regAllocState.getInterval(phys);
            if (LinearScan.VERBOSE_DEBUG) {
              System.out.println(" assigned " + phys + " to " + container);
            }
            physInterval.removeAll(spillCandidate);
            if (LinearScan.VERBOSE_DEBUG) System.out.println("  after spill phys" + regAllocState.getInterval(phys));
            updatePhysicalInterval(phys, newInterval);
            if (LinearScan.VERBOSE_DEBUG) {
              System.out.println(" now phys interval " + regAllocState.getInterval(phys));
            }
            container.assign(phys);
          } else {
            // spill the new interval.
            if (LinearScan.VERBOSE_DEBUG) System.out.println("spilled " + container);
            container.spill(spillManager, regAllocState);
            spilled = true;
          }
        }
      }
    }
  }

  /**
   * Updates the interval representing the allocations of a physical
   * register p to include a new interval i.
   *
   * @param p a physical register
   * @param i the new interval
   */
  private void updatePhysicalInterval(Register p, BasicInterval i) {
    // Get a representation of the intervals already assigned to p.
    CompoundInterval physInterval = regAllocState.getInterval(p);
    if (physInterval == null) {
      // no interval yet.  create one.
      regAllocState.setInterval(p, new CompoundInterval(i, p));
    } else {
      // incorporate i into the set of intervals assigned to p
      CompoundInterval ci = new CompoundInterval(i, p);
      if (VM.VerifyAssertions) VM._assert(!ci.intersects(physInterval));
      physInterval.addAll(ci);
    }
  }

  /**
   * Update the interval representing the allocations of a physical
   * register p to include a new compound interval c.  Include only
   * those basic intervals in c up to and including basic interval stop.
   *
   * @param p a physical register
   * @param c the new interval
   * @param stop the last interval to be included
   */
  private void updatePhysicalInterval(Register p, CompoundInterval c, BasicInterval stop) {
    // Get a representation of the intervals already assigned to p.
    CompoundInterval physInterval = regAllocState.getInterval(p);
    if (physInterval == null) {
      // no interval yet.  create one.
      regAllocState.setInterval(p, c.copy(p, stop));
    } else {
      // incorporate c into the set of intervals assigned to p
      if (VM.VerifyAssertions) VM._assert(!c.intersects(physInterval));
      // copy to a new BasicInterval so "equals" will work as expected,
      // since "stop" may be a MappedBasicInterval.
      stop = new BasicInterval(stop.getBegin(), stop.getEnd());
      physInterval.addNonIntersectingInterval(c, stop);
    }
  }

  /**
   * @param r a physical register
   * @return whether the particular physical register is currently allocated to an
   * interval in the active set
   */
  boolean currentlyActive(Register r) {
    for (Iterator<BasicInterval> e = iterator(); e.hasNext();) {
      MappedBasicInterval i = (MappedBasicInterval) e.next();
      CompoundInterval container = i.container;
      if (regAllocState.getMapping(container.getRegister()) == r) {
        return true;
      }
    }
    return false;
  }

  /**
   * @param r a physical register
   * @return the interval that the physical register is allocated to
   * @throws OptimizingCompilerException if the register is not currently
   *  allocated to any interval
   */
  CompoundInterval getCurrentInterval(Register r) {
    for (Iterator<BasicInterval> e = iterator(); e.hasNext();) {
      MappedBasicInterval i = (MappedBasicInterval) e.next();
      CompoundInterval container = i.container;
      if (regAllocState.getMapping(container.getRegister()) == r) {
        return container;
      }
    }
    OptimizingCompilerException.UNREACHABLE("getCurrentInterval", "Not Currently Active ", r.toString());
    return null;
  }

  /**
   * @param ci interval to allocate
   * @return a free physical register to allocate to the compound
   * interval, {@code null} if no free physical register is found
   */
  Register findAvailableRegister(CompoundInterval ci) {

    if (ir.options.FREQ_FOCUS_EFFORT && ci.isInfrequent()) {
      // don't bother trying to find an available register
      return null;
    }

    Register r = ci.getRegister();
    GenericRegisterRestrictions restrict = ir.stackManager.getRestrictions();

    // first attempt to allocate to the preferred register
    if (ir.options.REGALLOC_COALESCE_MOVES) {
      Register p = getPhysicalPreference(ci);
      if (p != null) {
        if (LinearScan.DEBUG_COALESCE) {
          System.out.println("REGISTER PREFERENCE " + ci + " " + p);
        }
        return p;
      }
    }

    GenericPhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    int type = GenericPhysicalRegisterSet.getPhysicalRegisterType(r);

    // next attempt to allocate to a volatile
    if (!restrict.allVolatilesForbidden(r)) {
      for (Enumeration<Register> e = phys.enumerateVolatiles(type); e.hasMoreElements();) {
        Register p = e.nextElement();
        if (allocateToPhysical(ci, p)) {
          return p;
        }
      }
    }

    // next attempt to allocate to a Nonvolatile.  we allocate the
    // novolatiles backwards.
    for (Enumeration<Register> e = phys.enumerateNonvolatilesBackwards(type); e.hasMoreElements();) {
      Register p = e.nextElement();
      if (allocateToPhysical(ci, p)) {
        return p;
      }
    }

    // no allocation succeeded.
    return null;
  }

  /**
   * @param symb symbolic register to allocate
   * @return a free physical register to allocate to the symbolic
   * register, {@code null} if no free physical register is found
   */
  Register findAvailableRegister(Register symb) {

    GenericRegisterRestrictions restrict = ir.stackManager.getRestrictions();

    // first attempt to allocate to the preferred register
    if (ir.options.REGALLOC_COALESCE_MOVES) {
      Register p = getPhysicalPreference(symb);
      if (p != null) {
        if (LinearScan.DEBUG_COALESCE) {
          System.out.println("REGISTER PREFERENCE " + symb + " " + p);
        }
        return p;
      }
    }

    GenericPhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    int type = GenericPhysicalRegisterSet.getPhysicalRegisterType(symb);

    // next attempt to allocate to a volatile
    if (!restrict.allVolatilesForbidden(symb)) {
      for (Enumeration<Register> e = phys.enumerateVolatiles(type); e.hasMoreElements();) {
        Register p = e.nextElement();
        if (allocateNewSymbolicToPhysical(symb, p)) {
          return p;
        }
      }
    }

    // next attempt to allocate to a Nonvolatile.  we allocate the
    // novolatiles backwards.
    for (Enumeration<Register> e = phys.enumerateNonvolatilesBackwards(type); e.hasMoreElements();) {
      Register p = e.nextElement();
      if (allocateNewSymbolicToPhysical(symb, p)) {
        return p;
      }
    }

    // no allocation succeeded.
    return null;
  }

  /**
   * Given the current state of the register allocator, compute the
   * available physical register to which a symbolic register has the
   * highest preference.
   *
   * @param r the symbolic register in question.
   * @return the preferred register, {@code null} if no preference found.
   */
  private Register getPhysicalPreference(Register r) {
    // a mapping from Register to Integer
    // (physical register to weight);
    HashMap<Register, Integer> map = new HashMap<Register, Integer>();

    CoalesceGraph graph = ir.stackManager.getPreferences().getGraph();
    SpaceEffGraphNode node = graph.findNode(r);

    // Return null if no affinities.
    if (node == null) return null;

    // walk through all in edges of the node, searching for affinity
    for (Enumeration<GraphEdge> in = node.inEdges(); in.hasMoreElements();) {
      CoalesceGraph.Edge edge = (CoalesceGraph.Edge) in.nextElement();
      CoalesceGraph.Node src = (CoalesceGraph.Node) edge.from();
      Register neighbor = src.getRegister();
      if (neighbor.isSymbolic()) {
        // if r is assigned to a physical register r2, treat the
        // affinity as an affinity for r2
        Register r2 = regAllocState.getMapping(r);
        if (r2 != null && r2.isPhysical()) {
          neighbor = r2;
        }
      }
      if (neighbor.isPhysical()) {
        // if this is a candidate interval, update its weight
        if (allocateNewSymbolicToPhysical(r, neighbor)) {
          int w = edge.getWeight();
          Integer oldW = map.get(neighbor);
          if (oldW == null) {
            map.put(neighbor, w);
          } else {
            map.put(neighbor, oldW + w);
          }
          break;
        }
      }
    }
    // walk through all out edges of the node, searching for affinity
    for (Enumeration<GraphEdge> in = node.outEdges(); in.hasMoreElements();) {
      CoalesceGraph.Edge edge = (CoalesceGraph.Edge) in.nextElement();
      CoalesceGraph.Node dest = (CoalesceGraph.Node) edge.to();
      Register neighbor = dest.getRegister();
      if (neighbor.isSymbolic()) {
        // if r is assigned to a physical register r2, treat the
        // affinity as an affinity for r2
        Register r2 = regAllocState.getMapping(r);
        if (r2 != null && r2.isPhysical()) {
          neighbor = r2;
        }
      }
      if (neighbor.isPhysical()) {
        // if this is a candidate interval, update its weight
        if (allocateNewSymbolicToPhysical(r, neighbor)) {
          int w = edge.getWeight();
          Integer oldW = map.get(neighbor);
          if (oldW == null) {
            map.put(neighbor, w);
          } else {
            map.put(neighbor, oldW + w);
          }
          break;
        }
      }
    }
    // OK, now find the highest preference.
    Register result = null;
    int weight = -1;
    for (Map.Entry<Register, Integer> entry : map.entrySet()) {
      int w = entry.getValue();
      if (w > weight) {
        weight = w;
        result = entry.getKey();
      }
    }
    return result;
  }

  /**
   * Given the current state of the register allocator, compute the
   * available physical register to which an interval has the highest
   * preference.
   *
   * @param ci the interval in question
   * @return the preferred register, {@code null} if no preference found
   */
  private Register getPhysicalPreference(CompoundInterval ci) {
    // a mapping from Register to Integer
    // (physical register to weight);
    HashMap<Register, Integer> map = new HashMap<Register, Integer>();
    Register r = ci.getRegister();

    CoalesceGraph graph = ir.stackManager.getPreferences().getGraph();
    SpaceEffGraphNode node = graph.findNode(r);

    // Return null if no affinities.
    if (node == null) return null;

    // walk through all in edges of the node, searching for affinity
    for (Enumeration<GraphEdge> in = node.inEdges(); in.hasMoreElements();) {
      CoalesceGraph.Edge edge = (CoalesceGraph.Edge) in.nextElement();
      CoalesceGraph.Node src = (CoalesceGraph.Node) edge.from();
      Register neighbor = src.getRegister();
      if (neighbor.isSymbolic()) {
        // if r is assigned to a physical register r2, treat the
        // affinity as an affinity for r2
        Register r2 = regAllocState.getMapping(r);
        if (r2 != null && r2.isPhysical()) {
          neighbor = r2;
        }
      }
      if (neighbor.isPhysical()) {
        // if this is a candidate interval, update its weight
        if (allocateToPhysical(ci, neighbor)) {
          int w = edge.getWeight();
          Integer oldW = map.get(neighbor);
          if (oldW == null) {
            map.put(neighbor, w);
          } else {
            map.put(neighbor, oldW + w);
          }
          break;
        }
      }
    }
    // walk through all out edges of the node, searching for affinity
    for (Enumeration<GraphEdge> in = node.outEdges(); in.hasMoreElements();) {
      CoalesceGraph.Edge edge = (CoalesceGraph.Edge) in.nextElement();
      CoalesceGraph.Node dest = (CoalesceGraph.Node) edge.to();
      Register neighbor = dest.getRegister();
      if (neighbor.isSymbolic()) {
        // if r is assigned to a physical register r2, treat the
        // affinity as an affinity for r2
        Register r2 = regAllocState.getMapping(r);
        if (r2 != null && r2.isPhysical()) {
          neighbor = r2;
        }
      }
      if (neighbor.isPhysical()) {
        // if this is a candidate interval, update its weight
        if (allocateToPhysical(ci, neighbor)) {
          int w = edge.getWeight();
          Integer oldW = map.get(neighbor);
          if (oldW == null) {
            map.put(neighbor, w);
          } else {
            map.put(neighbor, oldW + w);
          }
          break;
        }
      }
    }
    // OK, now find the highest preference.
    Register result = null;
    int weight = -1;
    for (Map.Entry<Register, Integer> entry : map.entrySet()) {
      int w = entry.getValue();
      if (w > weight) {
        weight = w;
        result = entry.getKey();
      }
    }
    return result;
  }

  /**
   * Checks whether it's ok to allocate an interval to a physical
   * register.
   *
   * @param i the interval to allocate
   * @param p the physical register
   * @return {@code true} if it's ok to allocate the interval to the
   *  given physical register, {@code false} otherwise
   */
  private boolean allocateToPhysical(CompoundInterval i, Register p) {
    GenericRegisterRestrictions restrict = ir.stackManager.getRestrictions();
    Register r = i.getRegister();
    GenericPhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    if (p != null && !phys.isAllocatable(p)) return false;

    if (LinearScan.VERBOSE_DEBUG && p != null) {
      if (!p.isAvailable()) System.out.println("unavailable " + i + p);
      if (restrict.isForbidden(r, p)) System.out.println("forbidden" + i + p);
    }

    if ((p != null) && p.isAvailable() && !restrict.isForbidden(r, p)) {
      CompoundInterval pInterval = regAllocState.getInterval(p);
      if (pInterval == null) {
        // no assignment to p yet
        return true;
      } else {
        if (!i.intersects(pInterval)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * NOTE: This routine assumes we're processing the first interval of
   * register symb; so p.isAvailable() is the key information needed.
   *
   * @param symb the symbolic register
   * @param p the physical register
   * @return whether it's ok to allocate the symbolic register to the physical
   * register
   */
  private boolean allocateNewSymbolicToPhysical(Register symb, Register p) {
    GenericRegisterRestrictions restrict = ir.stackManager.getRestrictions();
    GenericPhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    if (p != null && !phys.isAllocatable(p)) return false;

    if (LinearScan.VERBOSE_DEBUG && p != null) {
      if (!p.isAvailable()) System.out.println("unavailable " + symb + p);
      if (restrict.isForbidden(symb, p)) System.out.println("forbidden" + symb + p);
    }

    return (p != null) && p.isAvailable() && !restrict.isForbidden(symb, p);
  }

  /**
   * @param newInterval a new interval
   * @return an interval that can be spilled. This may be chosen from the
   * existing candidates and the new interval
   */
  private CompoundInterval getSpillCandidate(CompoundInterval newInterval) {
    if (LinearScan.VERBOSE_DEBUG) System.out.println("GetSpillCandidate from " + this);

    if (ir.options.FREQ_FOCUS_EFFORT && newInterval.isInfrequent()) {
      // if it's legal to spill this infrequent interval, then just do so!
      // don't spend any more effort.
      GenericRegisterRestrictions restrict = ir.stackManager.getRestrictions();
      if (!restrict.mustNotSpill(newInterval.getRegister())) {
        return newInterval;
      }
    }

    return spillMinUnitCost(newInterval);
  }

  /**
   * Chooses the interval with the minimal unit cost (defined as the number
   * of defs and uses).
   *
   * @param newInterval a new interval
   * @return the interval with the minimal number of defs and uses
   */
  private CompoundInterval spillMinUnitCost(CompoundInterval newInterval) {
    if (LinearScan.VERBOSE_DEBUG) {
      System.out.println(" interval caused a spill: " + newInterval);
    }
    GenericRegisterRestrictions restrict = ir.stackManager.getRestrictions();
    Register r = newInterval.getRegister();
    double minCost = spillCost.getCost(r);
    if (LinearScan.VERBOSE_DEBUG) {
      System.out.println(" spill cost: " + r + " " + minCost);
    }
    CompoundInterval result = newInterval;
    if (restrict.mustNotSpill(result.getRegister())) {
      if (LinearScan.VERBOSE_DEBUG) {
        System.out.println(" must not spill: " + result.getRegister());
      }
      result = null;
      minCost = Double.MAX_VALUE;
    }
    for (Iterator<BasicInterval> e = iterator(); e.hasNext();) {
      MappedBasicInterval b = (MappedBasicInterval) e.next();
      CompoundInterval i = b.container;
      Register newR = i.getRegister();
      if (LinearScan.VERBOSE_DEBUG) {
        if (i.isSpilled(regAllocState)) {
          System.out.println(" not candidate, already spilled: " + newR);
        }
        if ((r.getType() != newR.getType()) || (r.isNatural() && newR.isNatural())) {
          System.out.println(" not candidate, type mismatch : " + r.getType() + " " + newR + " " + newR.getType());
        }
        if (restrict.mustNotSpill(newR)) {
          System.out.println(" not candidate, must not spill: " + newR);
        }
      }
      if (!newR.isPhysical() &&
          !i.isSpilled(regAllocState) &&
          (r.getType() == newR.getType() || (r.isNatural() && newR.isNatural())) &&
          !restrict.mustNotSpill(newR)) {
        // Found a potential spill interval. Check if the assignment
        // works if we spill this interval.
        if (checkAssignmentIfSpilled(newInterval, i)) {
          double iCost = spillCost.getCost(newR);
          if (LinearScan.VERBOSE_DEBUG) {
            System.out.println(" potential candidate " + i + " cost " + iCost);
          }
          if (iCost < minCost) {
            if (LinearScan.VERBOSE_DEBUG) System.out.println(" best candidate so far" + i);
            minCost = iCost;
            result = i;
          }
        } else {
          if (LinearScan.VERBOSE_DEBUG) {
            System.out.println(" not a candidate, insufficient range: " + i);
          }
        }
      }
    }
    if (VM.VerifyAssertions) {
      VM._assert(result != null);
    }
    return result;
  }

  /**
   * Checks if it would be possible to assign an interval to the physical
   * register of another interval if that other interval were spilled.
   *
   * @param spill the interval that's a candidate for spilling
   * @param i the interval that we want to assign to the register of the spill interval
   * @return {@code true} if the allocation would fit,  {@code false} otherwise
   */
  private boolean checkAssignmentIfSpilled(CompoundInterval i, CompoundInterval spill) {
    Register r = spill.getAssignment(regAllocState);

    GenericRegisterRestrictions restrict = ir.stackManager.getRestrictions();
    if (restrict.isForbidden(i.getRegister(), r)) return false;

    // 1. Speculatively simulate the spill.
    CompoundInterval rI = regAllocState.getInterval(r);
    CompoundInterval cache = rI.removeIntervalsAndCache(spill);

    // 2. Check the fit.
    boolean result = !rI.intersects(i);

    // 3. Undo the simulated spill.
    rI.addAll(cache);

    return result;
  }

  /**
   * Finds a basic interval for a register so that the interval contains
   * the instruction.
   *
   * @param r the register whose interval is desired
   * @param s the reference instruction
   * @return {@code null} if there is no basic interval for the given register
   *  that contains the instruction, the interval otherwise. If there are
   *  multiple intervals, the first one will be returned.
   */
  BasicInterval getBasicInterval(Register r, Instruction s) {
    CompoundInterval c = regAllocState.getInterval(r);
    if (c == null) return null;
    return c.getBasicInterval(regAllocState, s);
  }

}
