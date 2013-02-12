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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.vm.ObjectModel;
import org.mmtk.policy.Space;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * A snapshot of the heap, taken by the harness without using core MMTk code.
 */
public class HeapSnapshot implements HeapVisitor {

  private static final boolean VERBOSE = true;

  /** The objects in the heap, by address */
  private final Map<ObjectReference,HeapEntry> byAddress = new HashMap<ObjectReference,HeapEntry>();

  /** The objects in the heap, by ID */
  private final Map<Integer,HeapEntry> byId = new HashMap<Integer,HeapEntry>();

  /** Statistics: objects in each space */
  private final Map<String,Integer> spaceStats = new TreeMap<String,Integer>();

  /** Duplicate objects */
  private final Map<Integer,Set<HeapEntry>> duplicates = new HashMap<Integer,Set<HeapEntry>>();

  /**
   * Take a snapshot of the current heap
   */
  public HeapSnapshot() {
    Traversal.traverse(this);
  }

  /** @return the space statistics */
  public Map<String, Integer> getSpaceStats() {
    return spaceStats;
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

  /** @return the set of live object references */
  public Set<ObjectReference> getLiveObjects() {
    return byAddress.keySet();
  }

  /**
   * Get an entry - fails with a runtime exception if the object is duplicated.
   * @param id Entry ID
   * @return The entry
   */
  public HeapEntry getEntry(int id) {
    return byId.get(id);
  }

  /**
   * @return the set of duplicate objects (live objects that are pointed to
   * at more than one address
   */
  public Set<Set<HeapEntry>> getDuplicates() {
    return Collections.unmodifiableSet(new HashSet<Set<HeapEntry>>(duplicates.values()));
  }

  private void addDuplicate(HeapEntry original, HeapEntry dup) {
    Set<HeapEntry> entries = duplicates.get(original.getId());
    if (entries == null) {
      entries = new TreeSet<HeapEntry>();
      entries.addAll(Arrays.asList(original,dup));
      duplicates.put(original.getId(), entries);
    } else {
      entries.add(dup);
    }
  }

  private void addEntryById(HeapEntry entry) {
    if (VERBOSE) Trace.trace(Item.SANITY,"Found object %d at address %s",entry.getId(),entry.getObject());
    HeapEntry oldEntry = byId.get(entry.getId());
    if (oldEntry == null) {
      byId.put(entry.getId(), entry);
    } else if (!oldEntry.equals(entry)) {
      addDuplicate(oldEntry,entry);
      Trace.printf(Item.SANITY,"Duplicate object found in heap %n%s",entry.toString());
    }
  }

  private void addSpaceStats(ObjectReference object) {
    String name = Space.getSpaceForObject(object).getName();
    Integer count = spaceStats.get(name);
    if (count == null) {
      count = Integer.valueOf(0);
    }
    spaceStats.put(name, count+1);
  }

  @Override
  public void visitObject(ObjectReference object, boolean root, boolean marked) {
    if (VERBOSE) {
      Trace.trace(Item.SANITY,"Visiting %sobject at address %s",root ? "root ":"",object);
    }
    int id = ObjectModel.getId(object);
    if (!ObjectModel.hasValidId(object)) {
      System.err.printf("### Invalid %sobject id=%d found at address %s%n",root ? "root ":"", id, object);
    }
    if (VERBOSE) {
      Trace.trace(Item.SANITY,"Visiting %sobject %d at address %s",root ? "root ":"",id,object);
    }
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

  @Override
  public void visitPointer(ObjectReference source, Address slot, ObjectReference target) {
    // Do nothing
  }
}
