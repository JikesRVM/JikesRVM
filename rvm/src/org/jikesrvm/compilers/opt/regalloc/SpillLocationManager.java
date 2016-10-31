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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.jikesrvm.compilers.opt.ir.GenericPhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.util.GraphEdge;
import org.jikesrvm.compilers.opt.util.SpaceEffGraphNode;

/**
 * The following class manages allocation and reuse of spill locations.
 */
class SpillLocationManager {

  /**
   * The governing IR
   */
  private final IR ir;

  /**
   * Set of spill locations which were previously allocated, but may be
   * free since the assigned register is no longer live.
   */
  final HashSet<SpillLocationInterval> freeIntervals = new HashSet<SpillLocationInterval>();

  /**
   * @param ci a compound interval that we want to spill
   * @return a spill location that is valid to hold the contents of
   * the compound interval
   */
  SpillLocationInterval findOrCreateSpillLocation(CompoundInterval ci) {
    SpillLocationInterval result = null;

    Register r = ci.getRegister();
    int type = GenericPhysicalRegisterSet.getPhysicalRegisterType(r);
    int spillSize = ir.stackManager.getSpillSize(type);

    // Search the free intervals and try to find an interval to
    // reuse. First look for the preferred interval.
    if (ir.options.REGALLOC_COALESCE_SPILLS) {
      result = getSpillPreference(ci, spillSize, type);
      if (result != null) {
        if (LinearScan.DEBUG_COALESCE) {
          System.out.println("SPILL PREFERENCE " + ci + " " + result);
        }
        freeIntervals.remove(result);
      }
    }

    // Now search for any free interval.
    if (result == null) {
      Iterator<SpillLocationInterval> iter = freeIntervals.iterator();
      while (iter.hasNext()) {
        SpillLocationInterval s = iter.next();
        if (s.getSize() == spillSize && !s.intersects(ci) && s.getType() == type) {
          result = s;
          iter.remove();
          break;
        }
      }
    }

    if (result == null) {
      // Could not find an interval to reuse.  Create a new interval.
      int location = ir.stackManager.allocateNewSpillLocation(type);
      result = new SpillLocationInterval(location, spillSize, type);
    }

    // Update the spill location interval to hold the new spill
    result.addAll(ci);

    return result;
  }

  /**
   * Records that a particular interval is potentially available for
   * reuse.
   *
   * @param i the interval to free
   */
  void freeInterval(SpillLocationInterval i) {
    freeIntervals.add(i);
  }

  SpillLocationManager(IR ir) {
    this.ir = ir;
  }

  /**
   * Given the current state of the register allocator, compute the
   * available spill location to which ci has the highest preference.
   *
   * @param ci the interval to spill
   * @param spillSize the size of spill location needed
   * @param type the physical register's type
   * @return the interval to spill to.  null if no preference found.
   */
  SpillLocationInterval getSpillPreference(CompoundInterval ci, int spillSize, int type) {
    // a mapping from SpillLocationInterval to Integer
    // (spill location to weight);
    HashMap<SpillLocationInterval, Integer> map = new HashMap<SpillLocationInterval, Integer>();
    Register r = ci.getRegister();

    CoalesceGraph graph = ir.stackManager.getPreferences().getGraph();
    SpaceEffGraphNode node = graph.findNode(r);

    // Return null if no affinities.
    if (node == null) return null;

    RegisterAllocatorState regAllocState = ir.MIRInfo.regAllocState;

    // walk through all in edges of the node, searching for spill
    // location affinity
    for (Enumeration<GraphEdge> in = node.inEdges(); in.hasMoreElements();) {
      CoalesceGraph.Edge edge = (CoalesceGraph.Edge) in.nextElement();
      CoalesceGraph.Node src = (CoalesceGraph.Node) edge.from();
      Register neighbor = src.getRegister();
      if (neighbor.isSymbolic() && neighbor.isSpilled()) {
        int spillOffset = regAllocState.getSpill(neighbor);
        // if this is a candidate interval, update its weight
        for (SpillLocationInterval s : freeIntervals) {
          if (s.getOffset() == spillOffset && s.getSize() == spillSize &&
              !s.intersects(ci) && s.getType() == type) {
            int w = edge.getWeight();
            Integer oldW = map.get(s);
            if (oldW == null) {
              map.put(s, w);
            } else {
              map.put(s, oldW + w);
            }
            break;
          }
        }
      }
    }

    // walk through all out edges of the node, searching for spill
    // location affinity
    for (Enumeration<GraphEdge> in = node.inEdges(); in.hasMoreElements();) {
      CoalesceGraph.Edge edge = (CoalesceGraph.Edge) in.nextElement();
      CoalesceGraph.Node dest = (CoalesceGraph.Node) edge.to();
      Register neighbor = dest.getRegister();
      if (neighbor.isSymbolic() && neighbor.isSpilled()) {
        int spillOffset = regAllocState.getSpill(neighbor);
        // if this is a candidate interval, update its weight
        for (SpillLocationInterval s : freeIntervals) {
          if (s.getOffset() == spillOffset && s.getSize() == spillSize &&
              !s.intersects(ci) && s.getType() == type) {
            int w = edge.getWeight();
            Integer oldW = map.get(s);
            if (oldW == null) {
              map.put(s, w);
            } else {
              map.put(s, oldW + w);
            }
            break;
          }
        }
      }
    }

    // OK, now find the highest preference.
    SpillLocationInterval result = null;
    int weight = -1;
    for (Map.Entry<SpillLocationInterval, Integer> entry : map.entrySet()) {
      int w = entry.getValue();
      if (w > weight) {
        weight = w;
        result = entry.getKey();
      }
    }
    return result;
  }
}
