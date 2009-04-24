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
package org.mmtk.harness.sanity;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.mmtk.policy.Space;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * A snapshot of the heap, taken by the harness without using core MMTk code.
 */
public class HeapSnapshot implements HeapVisitor {

  /** The objects in the heap, by address */
  private final Map<ObjectReference,HeapEntry> byAddress = new HashMap<ObjectReference,HeapEntry>();
  /** The objects in the heap, by ID */
  private final Map<Integer,Set<HeapEntry>> byId = new HashMap<Integer,Set<HeapEntry>>();
  /** Statistics: objects in each space */
  private final Map<String,Integer> spaceStats = new TreeMap<String,Integer>();
  /** Duplicate objects */
  private final Set<Set<HeapEntry>> duplicates = new HashSet<Set<HeapEntry>>();

  /** @return the space statistics */
  public Map<String, Integer> getSpaceStats() {
    return spaceStats;
  }

  /** @return the number of objects in the heap */
  public int size() {
    assert byAddress.size() == byId.size() : "Objects by address: "+byAddress.size()+", by id: "+byId.size();
    return byId.size();
  }

  /** @return the number of distinct object addresses in the heap */
  public int sizeByAddress() {
    return byAddress.size();
  }

  /** @return the number of distinct objects in the heap */
  public int sizeById() {
    return byId.size();
  }

  /** @return the set of live object IDs */
  public Set<Integer> getLive() {
    return byId.keySet();
  }

  /**
   * @return the set of duplicate objects (live objects that are pointed to
   * at more than one address
   */
  public Set<Set<HeapEntry>> getDuplicates() {
    return Collections.unmodifiableSet(duplicates);
  }

  /**
   * Take a snapshot of the current heap
   */
  public HeapSnapshot() {
    Traversal.traverse(this);
  }

  private void addEntryById(HeapEntry entry) {
    Set<HeapEntry> entries = byId.get(entry.getId());
    if (entries == null) {
      entries = new TreeSet<HeapEntry>();
      byId.put(entry.getId(), entries);
    } else if (!entries.contains(entry)) {
      duplicates.add(entries);
    }
    entries.add(entry);
  }

  private void addSpaceStats(ObjectReference object) {
    String name = Space.getSpaceForObject(object).getName();
    Integer count = spaceStats.get(name);
    if (count == null) {
      count = Integer.valueOf(0);
    }
    spaceStats.put(name, count+1);
  }

  /**
   * @see HeapVisitor#visitObject(ObjectReference, boolean, boolean)
   */
  @Override
  public void visitObject(ObjectReference object, boolean root, boolean marked) {
    HeapEntry entry = byAddress.get(object);
    if (!marked) {
      assert entry == null;
      entry = new HeapEntry(object);
      byAddress.put(object, entry);
      addEntryById(entry);
      addSpaceStats(object);
    }
    if (root) entry.setRootReachable();
    entry.incRefCount();
  }

  /**
   * @see HeapVisitor#visitPointer(ObjectReference, Address, ObjectReference)
   */
  @Override
  public void visitPointer(ObjectReference source, Address slot, ObjectReference target) {
    // Do nothing
  }
}
