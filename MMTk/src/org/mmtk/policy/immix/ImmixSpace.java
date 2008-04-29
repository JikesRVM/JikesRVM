/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.policy.immix;

import static org.mmtk.policy.immix.ImmixConstants.*;

import org.mmtk.plan.Plan;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.policy.Space;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.options.LineReuseRatio;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.statistics.BooleanCounter;
import org.mmtk.utility.statistics.EventCounter;
import org.mmtk.utility.statistics.SizeCounter;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;

import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class corresponds to one immix *space*.
 * Each of the instance methods of this class may be called by any
 * thread (i.e. synchronization must be explicit in any instance or
 * class method).  This contrasts with the SquishLocal, where
 * instances correspond to *plan* instances and therefore to kernel
 * threads.  Thus unlike this class, synchronization is not necessary
 * in the instance methods of SquishLocal.
 *
 */
@Uninterruptible
public final class ImmixSpace extends Space implements Constants {

  /****************************************************************************
   *
   * Class variables
   */
  private static short reusableMarkStateThreshold = 0;

  /* statistics */
  public static BooleanCounter fullHeap = new BooleanCounter("majorGC", true, true);
  public static EventCounter callsToPin = TMP_VERBOSE_DEFRAG_STATS && TMP_SUPPORT_PINNING ? new EventCounter("callsToPin") : null;
  public static EventCounter pinnedObjects = TMP_VERBOSE_DEFRAG_STATS && TMP_SUPPORT_PINNING ? new EventCounter("pinned") : null;
  public static SizeCounter bytesAlloc = TMP_VERBOSE_ALLOC_STATS ? new SizeCounter("bytesAlloc") : null;
  public static SizeCounter bytesLine = TMP_VERBOSE_ALLOC_STATS ? new SizeCounter("bytesLine") : null;
  public static SizeCounter bytesLineApprox = TMP_VERBOSE_ALLOC_STATS ? new SizeCounter("bytesLineApprox") : null;
  public static SizeCounter tmpBytesLineApprox = TMP_VERBOSE_ALLOC_STATS ? new SizeCounter("tmpBytesLineApprox") : null;
  public static SizeCounter bytesAllocAlign = TMP_VERBOSE_ALLOC_STATS ? new SizeCounter("bytesAllocAlign") : null;
  public static SizeCounter bytesAllocClean = TMP_VERBOSE_ALLOC_STATS ? new SizeCounter("bytesAllocClean") : null;
  public static SizeCounter bytesAllocOverflow = TMP_VERBOSE_ALLOC_STATS ? new SizeCounter("bytesAllocOverflow") : null;
  public static SizeCounter bytesAllocDirty0 = TMP_VERBOSE_ALLOC_STATS ? new SizeCounter("bytesAllocDirty0") : null;
  public static SizeCounter bytesAllocDirty1 = TMP_VERBOSE_ALLOC_STATS ? new SizeCounter("bytesAllocDirty1") : null;
  public static SizeCounter bytesAllocDirty2 = TMP_VERBOSE_ALLOC_STATS ? new SizeCounter("bytesAllocDirty2") : null;
  public static SizeCounter bytesAllocDirty3 = TMP_VERBOSE_ALLOC_STATS ? new SizeCounter("bytesAllocDirty3") : null;
  public static SizeCounter bytesMarkedObjects = TMP_VERBOSE_MARK_STATS ? new SizeCounter("bytesMarkedObjects") : null;
  public static SizeCounter bytesMarkedLines = TMP_VERBOSE_MARK_STATS ? new SizeCounter("bytesMarkedLines") : null;
  public static SizeCounter bytesConsvMarkedLines = TMP_VERBOSE_MARK_STATS ? new SizeCounter("bytesConsvMarkedLines") : null;
  public static SizeCounter bytesMarkedBlocks = TMP_VERBOSE_MARK_STATS ? new SizeCounter("bytesMarkedBlocks") : null;
  public static SizeCounter bytesFreed = TMP_VERBOSE_MARK_STATS ? new SizeCounter("bytesFreed") : null;
  public static SizeCounter bytesHeld = TMP_VERBOSE_MARK_STATS ? new SizeCounter("bytesHeld") : null;
  public static int TMPreusableLineCount = 0;
  public static int TMPreusedLineCount = 0;
  public static int TMPreusableBlockCount = 0;
  public static int TMPreusedBlockCount = 0;

  /****************************************************************************
   *
   * Instance variables
   */
  private Word markState = ObjectHeader.MARK_BASE_VALUE;
  private boolean inCollection;
  private int linesConsumed = 0;

  private Lock mutatorLock = VM.newLock(getName()+"mutator");
  private Lock gcLock = VM.newLock(getName()+"gc");

  private Address allocBlockCursor = Address.zero();
  private Address allocBlockSentinel = Address.zero();
  private boolean exhaustedReusableSpace = true;

  private final ChunkList chunkMap = new ChunkList();
  private final Defrag defrag;

  /****************************************************************************
   *
   * Initialization
   */

  static {
    Options.lineReuseRatio = new LineReuseRatio();
    reusableMarkStateThreshold = (short) (Options.lineReuseRatio.getValue() * MAX_BLOCK_MARK_STATE);
  }

  /**
   * The caller specifies the region of virtual memory to be used for
   * this space.  If this region conflicts with an existing space,
   * then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param start The start address of the space in virtual memory
   * @param bytes The size of the space in virtual memory, in bytes
   */
  public ImmixSpace(String name, int pageBudget, VMRequest vmRequest) {
    super(name, false, false, vmRequest);
    if (vmRequest.isDiscontiguous())
      pr = new FreeListPageResource(pageBudget, this, Chunk.getRequiredMetaDataPages());
    else
      pr = new FreeListPageResource(pageBudget, this, start, extent, Chunk.getRequiredMetaDataPages());
    defrag = new Defrag((FreeListPageResource) pr);
  }

  /****************************************************************************
   *
   * Global prepare and release
   */

  /**
   * Prepare for a new collection increment.
   */
  public void prepare(boolean majorGC) {
    if (majorGC) markState = ObjectHeader.deltaMarkState(markState, true);
    chunkMap.reset();
    defrag.prepare(chunkMap, this);
    inCollection = true;

    if (TMP_CHECK_REUSE_EFFICIENCY) {
      float ratio = (TMPreusableLineCount > 0) ? ((float)TMPreusedLineCount/(float) TMPreusableLineCount) : (float) 1.0;
      Log.write("<"); Log.write(TMPreusedLineCount); Log.write("/"); Log.write(TMPreusableLineCount); Log.write(" "); Log.write(ratio);
      ratio = (TMPreusableBlockCount > 0) ? ((float)TMPreusedBlockCount/(float) TMPreusableBlockCount) : (float) 1.0;
      Log.write(" "); Log.write(TMPreusedBlockCount); Log.write("/"); Log.write(TMPreusableBlockCount);Log.write(" "); Log.write(ratio); Log.write(">");
      TMPreusableLineCount = 0;
      TMPreusedLineCount = 0;
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(VM.activePlan.collectorCount() <= MAX_COLLECTORS);
  }

  /**
   * A new collection increment has completed.  Release global resources.
   */
  public void globalRelease() {
    chunkMap.reset();
    defrag.globalRelease();

    if (TMP_VERBOSE_MARK_STATS && Options.verbose.getValue() >= 1) {
      Log.write("[ mobj: "); bytesMarkedObjects.printCurrentVolume();
      Log.write(", mlin: "); bytesMarkedLines.printCurrentVolume();
      Log.write(", cmln: "); bytesConsvMarkedLines.printCurrentVolume();
      Log.write(", mblk: "); bytesMarkedBlocks.printCurrentVolume();
      Log.write(", free: "); bytesFreed.printCurrentVolume();
      Log.write(", held: "); bytesHeld.printCurrentVolume();
      Log.write("]"); Log.flush();
    }
    inCollection = false;

    /* set up reusable space */
    if (allocBlockCursor.isZero()) allocBlockCursor = chunkMap.getHeadChunk();
    allocBlockSentinel = allocBlockCursor;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(ImmixSpace.isRecycleAllocChunkAligned(allocBlockSentinel));
    exhaustedReusableSpace = false;

    /* really just want this to happen once after options are booted, but no harm in re-doing it */
    reusableMarkStateThreshold = (short) (Options.lineReuseRatio.getValue() * MAX_BLOCK_MARK_STATE);
    Defrag.defragReusableMarkStateThreshold = (short) (Options.defragLineReuseRatio.getValue() * MAX_BLOCK_MARK_STATE);

    linesConsumed = 0;
  }

  /**
   * Determine the collection kind.
   *
   * @param emergencyCollection Is this collection an emergency (last did not yield enough)?
   * @param collectWholeHeap Is this a whole heap collection?
   * @param collectionAttempt Which attempt is this to collect?
   * @param requiredAtStart How much space is required?
   * @param userTriggered Is this a user-triggered collection?
   */
  public void setCollectionKind(boolean emergencyCollection, boolean collectWholeHeap, int collectionAttempt, int requiredAtStart, boolean userTriggered) {
    defrag.setCollectionKind(emergencyCollection, collectWholeHeap, collectionAttempt, requiredAtStart, userTriggered, exhaustedReusableSpace);
  }

 /****************************************************************************
  *
  * Collection state access methods
  */

  /**
   * Return true if this space is currently being collected.
   *
   * @return True if this space is currently being collected.
   */
  @Inline
  public boolean inImmixCollection() {
    return inCollection;
  }

  /**
   * Return true if this space is currently being defraged.
   *
   * @return True if this space is currently being defraged.
   */
  @Inline
  public boolean inImmixDefragCollection() {
    return inCollection && defrag.inDefrag();
  }

  /**
   * Return the number of pages allocated since the last collection
   *
   * @return The number of pages allocated since the last collection
   */
  public int getPagesAllocated() {
    return linesConsumed>>(LOG_BYTES_IN_PAGE-LOG_BYTES_IN_LINE);
  }

  /**
   * Return the reusable mark state threshold, which determines how
   * eagerly lines should be recycled (by default these values are
   * set so that all lines are recycled).
   *
   * @param forDefrag The query is the context of a defragmenting collection
   * @return The reusable mark state threshold
   */
  @Inline
  public static short getReusuableMarkStateThreshold(boolean forDefrag) {
    return forDefrag ? Defrag.defragReusableMarkStateThreshold : reusableMarkStateThreshold;
  }

  /****************************************************************************
   *
   * Allocation
   */

  /**
   * Return a pointer to a set of new usable blocks, or null if none are available.
   * Use different block selection heuristics depending on whether the allocation
   * request is "hot" or "cold".
   *
   * @param hot True if the requesting context is for hot allocations (used for
   * allocations from high allocation volume sites).
   * @return The pointer into the alloc table containing usable blocks.
   */
  public Address getSpace(boolean hot, boolean copy, int lineUseCount) {
    Address rtn;
    if (copy)
      defrag.getBlock();

    linesConsumed += lineUseCount;
    if (TMP_VERBOSE_ALLOC_STATS)
      tmpBytesLineApprox.inc(lineUseCount*BYTES_IN_LINE);

    rtn = acquire(PAGES_IN_BLOCK);

    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(Block.isAligned(rtn));
      VM.assertions._assert(!(copy && Block.isDefragSource(rtn)));
    }

    if (!rtn.isZero()) {
      Block.setBlockAsInUse(rtn);
      Chunk.updateHighWater(rtn);
    }
    return rtn;
  }

 /**
  * This hook is called by page resources each time a space grows.  The space may
  * tap into the hook to monitor heap growth.  The call is made from within the
  * page resources' critical region, immediately before yielding the lock.
  *
  * @param start The start of the newly allocated space
  * @param bytes The size of the newly allocated space
  * @param newChunk True if the new space encroached upon or started a new chunk or chunks.
  */
  @Override
  public void growSpace(Address start, Extent bytes, boolean newChunk) {
    super.growSpace(start, bytes, newChunk);
     if (newChunk) {
      Address chunk = chunkAlign(start.plus(bytes), true);
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(chunkAlign(start.plus(bytes), true).EQ(chunk));
      Chunk.clearMetaData(chunk);
      chunkMap.addNewChunkToMap(chunk);
    }
  }

  public Address acquireReusableBlocks() {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(!inCollection || (TMP_SUPPORT_DEFRAG && !TMP_DEFRAG_TO_IMMORTAL));
      VM.assertions._assert(ImmixSpace.isRecycleAllocChunkAligned(allocBlockCursor));
      VM.assertions._assert(ImmixSpace.isRecycleAllocChunkAligned(allocBlockSentinel));
    }
    Address rtn;
    if (TMP_DEFRAG_WITHOUT_BUDGET && defrag.inDefrag())
      return Address.zero();

    lock();
    if (exhaustedReusableSpace)
      rtn = Address.zero();
    else {
      rtn = allocBlockCursor;
      Address lastAllocChunk = chunkAlign(allocBlockCursor, true);
      allocBlockCursor = allocBlockCursor.plus(BYTES_IN_RECYCLE_ALLOC_CHUNK);
      if (allocBlockCursor.GT(Chunk.getHighWater(lastAllocChunk)))
        allocBlockCursor = chunkMap.nextChunk(lastAllocChunk);

      if (allocBlockCursor.isZero() || allocBlockCursor.EQ(allocBlockSentinel))
        exhaustedReusableSpace = true;
    }
    unlock();
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(ImmixSpace.isRecycleAllocChunkAligned(rtn));
    return rtn;
  }

  /**
   * Release a block.  A block is free, so call the underlying page allocator
   * to release the associated storage.
   *
   * @param block The address of the block to be released
   */
  @Inline
  public void release(Address block) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Block.isAligned(block));
    Block.setBlockAsUnallocated(block);
    ((FreeListPageResource) pr).releasePages(block);
  }

 /**
  * Release one or more contiguous chunks associated with a discontiguous
  * space. This hook is called by the page level allocators whenever a
  * complete discontiguous chunk is released.
  *
  * @param chunk THe address of the start of the contiguous chunk or chunks
  * @return The number of chunks freed
  */
  @Override
  public int releaseDiscontiguousChunks(Address chunk) {
    chunkMap.removeChunkFromMap(chunk);
    return super.releaseDiscontiguousChunks(chunk);
  }

  /****************************************************************************
  *
  * Header manipulation
  */

 /**
  * Perform any required post allocation initialization
  *
  * @param object the object ref to the storage to be initialized
  */
  @Inline
  public void postAlloc(ObjectReference object, int bytes, boolean straddle) {
    if (VM.VERIFY_ASSERTIONS && TMP_EXACT_ALLOC_TIME_STRADDLE_CHECK) {
      Address start = VM.objectModel.objectStartRef(object);
      Address end = VM.objectModel.getObjectEndAddress(object).minus(1);
      boolean sanityStraddle = start.toWord().xor(end.toWord()).toInt() >= BYTES_IN_LINE;
      VM.assertions._assert(sanityStraddle == straddle);
    }
    boolean setStraddleBit = (TMP_INEXACT_ALLOC_TIME_STRADDLE_CHECK && bytes > BYTES_IN_LINE) || (TMP_EXACT_ALLOC_TIME_STRADDLE_CHECK && straddle);
    if (setStraddleBit)
      ObjectHeader.markAsStraddling(object);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(ObjectHeader.isNewObject(object));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!ObjectHeader.isForwardedOrBeingForwarded(object));
  }

 /**
  * Perform any required post copy (i.e. in-GC allocation) initialization.
  * This is relevant (for example) when Squish is used as the mature space in
  * a copying GC.
  *
  * @param object the object ref to the storage to be initialized
  * @param majorGC Is this copy happening during a major gc?
  */
  @Inline
  public void postCopy(ObjectReference object, int bytes, boolean majorGC, boolean straddle) {
    boolean setStraddleBit = (TMP_INEXACT_ALLOC_TIME_STRADDLE_CHECK && bytes > BYTES_IN_LINE) || (TMP_EXACT_ALLOC_TIME_STRADDLE_CHECK && straddle);
    ObjectHeader.writeMarkState(object, markState, setStraddleBit);
    if (!MARK_LINE_AT_SCAN_TIME && majorGC)
      markLines(object);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!ObjectHeader.isForwardedOrBeingForwarded(object));
    if (VM.VERIFY_ASSERTIONS && Plan.NEEDS_LOG_BIT_IN_HEADER) VM.assertions._assert(ObjectHeader.isUnloggedObject(object));
  }

  /****************************************************************************
   *
   * Object tracing
   */

  /**
   * Trace a reference to an object.  If the object header is not already
   * marked, mark the object and enqueue it for subsequent processing.
   *
   * @param trace The trace performing the transitive closure
   * @param object The object to be traced.
   * @param allocator The allocator to which any copying should be directed
   * @return The object, which may have been moved.
   */
  @Inline
  public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object, int allocator) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(defrag.determined(true));

    ObjectReference rtn = object;
    if (isDefragSource(object))
      rtn = traceObjectWithOpportunisticCopy(trace, object, allocator, false);
    else
      traceObjectWithoutMoving(trace, object);

    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(!rtn.isNull());
      if (TMP_DEFRAG_TO_IMMORTAL)
        VM.assertions._assert(getSpaceForObject(rtn) != this);
      else
        VM.assertions._assert(defrag.spaceExhausted() || !isDefragSource(rtn) || (TMP_SUPPORT_PINNING && ObjectHeader.isPinnedObject(rtn)));
    }
    return rtn;
  }

  /**
   * Trace a reference to an object in the context of a non-moving collection.  This
   * call is optimized for the simpler non-moving case.
   *
   * @param trace The trace performing the transitive closure
   * @param object The object to be traced.
   * @return The object (there is no object forwarding in this
   * trace method, so we always return the same object: this could be a
   * void method but for compliance to a more general interface).
   */
  @Inline
  public ObjectReference fastTraceObject(TransitiveClosure trace, ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(defrag.determined(false));
    traceObjectWithoutMoving(trace, object);
    return object;
  }

  /**
   * Trace a reference to an object during a nursery collection for
   * a sticky mark bits implementation of immix.  If the object header
   * is not already marked, mark the object and enqueue it for subsequent
   * processing.
   *
   * @param trace The trace performing the transitive closure
   * @param object The object to be traced.
   * @param allocator The allocator to which any copying should be directed
   * @return Either the object or a forwarded object, depending on
   * the policy in place.
   */
  @Inline
  public ObjectReference nurseryTraceObject(TransitiveClosure trace, ObjectReference object, int allocator) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!defrag.inDefrag());
    if (TMP_PREFER_COPY_ON_NURSERY_GC)
      return traceObjectWithOpportunisticCopy(trace, object, allocator, true);
    else
      return fastTraceObject(trace, object);
  }

  /**
   * Trace a reference to an object.  This interface is not supported by immix, since
   * we require the allocator to be identified except for the special case of the fast
   * trace.
   *
   * @param trace The trace performing the transitive closure
   * @param object The object to be traced.
   * @return null and fail.
   */
  public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object) {
    VM.assertions.fail("unsupported interface");
    return null;
  }

  /**
   * Trace a reference to an object in the context of a non-moving collection.  This
   * call is optimized for the simpler non-moving case.
   *
   * @param trace The trace performing the transitive closure
   * @param object The object to be traced.
   */
  @Inline
  private void traceObjectWithoutMoving(TransitiveClosure trace, ObjectReference object) {
    Word markValue = Plan.NEEDS_LOG_BIT_IN_HEADER ? markState.or(ObjectHeader.UNLOGGED_BIT) : markState;
    Word oldMarkState = ObjectHeader.testAndMark(object, markValue);
    if (VM.VERIFY_ASSERTIONS)  VM.assertions._assert(!defrag.inDefrag() || defrag.spaceExhausted() || !isDefragSource(object));
    if (oldMarkState != markValue) {
      if (!MARK_LINE_AT_SCAN_TIME)
        ImmixSpace.markLines(object);
      trace.processNode(object);
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!ObjectHeader.isForwardedOrBeingForwarded(object));
    if (VM.VERIFY_ASSERTIONS  && Plan.NEEDS_LOG_BIT_IN_HEADER) VM.assertions._assert(ObjectHeader.isUnloggedObject(object));
  }

  /**
   * Trace a reference to an object, forwarding the object if appropriate
   * If the object is not already marked, mark the object and enqueue it
   * for subsequent processing.
   *
   * @param trace The trace performing the transitive closure
   * @param object The object to be traced.
   * @param allocator The allocator to which any copying should be directed
   * @return Either the object or a forwarded object, if it was forwarded.
   */
  @Inline
  private ObjectReference traceObjectWithOpportunisticCopy(TransitiveClosure trace, ObjectReference object, int allocator, boolean nurseryCollection) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(nurseryCollection || (defrag.determined(true) && isDefragSource(object)));

    /* now race to be the (potential) forwarder */
    Word priorForwardingWord = ObjectHeader.attemptToBeForwarder(object);
    if (ObjectHeader.isForwardedOrBeingForwarded(priorForwardingWord)) {
      /* We lost the race; the object is either forwarded or being forwarded by another thread. */
      /* Note that the concurrent attempt to forward the object may fail, so the object may remain in-place */
      ObjectReference rtn = ObjectHeader.spinAndGetForwardedObject(object, priorForwardingWord);
      if (VM.VERIFY_ASSERTIONS && rtn == object) VM.assertions._assert((nurseryCollection && ObjectHeader.testMarkState(object, markState)) || defrag.spaceExhausted() || ObjectHeader.isPinnedObject(object));
      if (VM.VERIFY_ASSERTIONS && rtn != object) VM.assertions._assert(nurseryCollection || !isDefragSource(rtn));
      if (VM.VERIFY_ASSERTIONS && Plan.NEEDS_LOG_BIT_IN_HEADER) VM.assertions._assert(ObjectHeader.isUnloggedObject(rtn));
      return rtn;
    } else {
      /* the object is unforwarded, either because this is the first thread to reach it, or because the object can't be forwarded */
      if (ObjectHeader.testMarkState(priorForwardingWord, markState)) {
        /* the object has not been forwarded, but has the correct mark state; unlock and return unmoved object */
        /* Note that in a sticky mark bits collector, the mark state does not change at each GC, so correct mark state does not imply another thread got there first */
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(nurseryCollection || defrag.spaceExhausted() || ObjectHeader.isPinnedObject(object));
        ObjectHeader.setForwardingWordAndEnsureUnlogged(object, priorForwardingWord); // return to uncontested state
        if (VM.VERIFY_ASSERTIONS && Plan.NEEDS_LOG_BIT_IN_HEADER) VM.assertions._assert(ObjectHeader.isUnloggedObject(object));
        return object;
      } else {
        /* we are the first to reach the object; either mark in place or forward it */
        ObjectReference newObject;
        if (ObjectHeader.isPinnedObject(object) || (!TMP_DEFRAG_TO_IMMORTAL && defrag.spaceExhausted())) {
          /* mark in place */
          ObjectHeader.setMarkStateUnlogAndUnlock(object, priorForwardingWord, markState);
          newObject = object;
          if (TMP_VERBOSE_DEFRAG_STATS)
            Defrag.defragBytesSkipped.inc(VM.objectModel.getCurrentSize(newObject));
          if (VM.VERIFY_ASSERTIONS && Plan.NEEDS_LOG_BIT_IN_HEADER) VM.assertions._assert(ObjectHeader.isUnloggedObject(newObject));
        } else {
          /* forward */
          newObject = ObjectHeader.forwardObject(object, allocator);
          if (TMP_VERBOSE_DEFRAG_STATS)
            Defrag.defragBytesCopied.inc(VM.objectModel.getCurrentSize(newObject));
          if (VM.VERIFY_ASSERTIONS && Plan.NEEDS_LOG_BIT_IN_HEADER) VM.assertions._assert(ObjectHeader.isUnloggedObject(newObject));
        }
        if (!MARK_LINE_AT_SCAN_TIME)
          ImmixSpace.markLines(newObject);
        trace.processNode(newObject);
        if (VM.VERIFY_ASSERTIONS) {
          if (!((getSpaceForObject(newObject) != this) ||
                (newObject == object) ||
                (nurseryCollection && willNotMoveThisNurseryGC(newObject)) ||
                (defrag.inDefrag() && willNotMoveThisGC(newObject))
               )) {
            Log.write("   object: "); Log.writeln(object);
            Log.write("newObject: "); Log.writeln(newObject);
            Log.write("    space: "); Log.writeln(ObjectReference.fromObject(this));
            Log.write(" space(o): "); Log.writeln(ObjectReference.fromObject(getSpaceForObject(newObject)));
            Log.write(" nursery?: "); Log.writeln(nurseryCollection);
            Log.write("  mature?: "); Log.writeln(ObjectHeader.isMatureObject(object));
            Log.write("  wnmngc?: "); Log.writeln(willNotMoveThisNurseryGC(newObject));
            Log.write("  pinned?: "); Log.writeln(ObjectHeader.isPinnedObject(object));
            VM.assertions._assert(false);
          }
        }
        return newObject;
      }
    }
  }

  /**
   * Mark the line/s associated with a given object.  This is distinct from the
   * above tracing code because line marks are stored separately from the
   * object headers (thus both must be set), and also because we found empirically
   * that it was more efficient to perform the line mark of the object during
   * the scan phase (which occurs after the trace phase), presumably because
   * the latency of the associated memory operations was better hidden in the
   * context of that code
   *
   * @param object The object which is live and for which the associated lines
   * must be marked.
   */
  public static void markLines(ObjectReference object) {
    Address address = VM.objectModel.objectStartRef(object);
    if (TMP_USE_LINE_MARKS) {
      Line.mark(address);
      if (!TMP_ALLOC_TIME_STRADDLE_CHECK || ObjectHeader.isStraddlingObject(object))
        Line.markMultiLine(address, object);
    } else
      Block.markBlock(address);
    if (TMP_USE_BLOCK_LIVE_BYTE_COUNTS) {
      int size = 1;
      if (TMP_USE_SIZE_FOR_LIVE_COUNTS) {
        if (TMP_USE_OBJ_END_TO_GET_SIZE)
          size = VM.objectModel.getObjectEndAddress(object).diff(address).toInt();
        else
          size = VM.objectModel.getCurrentSize(object);
      }
      Block.incrementLiveCount(address, size);
    }

    if (TMP_VERBOSE_MARK_STATS) {
      bytesMarkedObjects.inc(VM.objectModel.getCurrentSize(object));
    }
  }

  /****************************************************************************
  *
  * Establish available lines
  */
 int getAvailableLines(int[] spillAvailHistogram) {
   int availableLines;
   if (allocBlockCursor.isZero() || exhaustedReusableSpace) {
     availableLines = 0;
   } else {
     if (allocBlockCursor.EQ(allocBlockSentinel)) {
       if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!exhaustedReusableSpace);
       allocBlockCursor = chunkMap.getHeadChunk();
       allocBlockSentinel = allocBlockCursor;
     }
     availableLines = getUsableLinesInRegion(allocBlockCursor, allocBlockSentinel, spillAvailHistogram);
   }
   return availableLines;
 }

 private int getUsableLinesInRegion(Address start, Address end, int[] spillAvailHistogram) {
   int usableLines = 0;
   Address blockCursor = Chunk.isAligned(start) ? start.plus(Chunk.FIRST_USABLE_BLOCK_INDEX<<LOG_BYTES_IN_BLOCK) : start;
   Address blockStateCursor = Block.getBlockMarkStateAddress(blockCursor);
   Address chunkCursor = Chunk.align(blockCursor);
   Address highwater = Chunk.getHighWater(chunkCursor);
   if (Chunk.getByteOffset(end) < Chunk.FIRST_USABLE_BLOCK_INDEX<<LOG_BYTES_IN_BLOCK)
     end = Chunk.align(end).plus(Chunk.FIRST_USABLE_BLOCK_INDEX<<LOG_BYTES_IN_BLOCK);

   if (!TMP_EAGER_SPILL_AVAIL_HISTO_CONSTRUCTION && TMP_DECREASE_AVAIL_WHEN_CALC_THRESHOLD)
     for (int i = 0; i <= MAX_CONSV_SPILL_COUNT; i++) spillAvailHistogram[i] = 0;

   do {
     short markState = blockStateCursor.loadShort();
     if (markState != 0 && markState <= reusableMarkStateThreshold) {
       int usable = LINES_IN_BLOCK - markState;
       if (!TMP_EAGER_SPILL_AVAIL_HISTO_CONSTRUCTION && TMP_DECREASE_AVAIL_WHEN_CALC_THRESHOLD) {
         short bucket = (short) (TMP_TRIAL_MARK_HISTO ? markState/TMP_MARK_HISTO_SCALING : Block.getConservativeSpillCount(blockCursor));
         if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(bucket >= 0 && bucket <= MAX_CONSV_SPILL_COUNT);
         spillAvailHistogram[bucket] += usable;
        }
       usableLines += usable;
       if (TMP_CLEAR_SPILLS_WHEN_ESTABLISHING_AVAILABLE) Block.clearConservativeSpillCount(blockCursor);
     }
     blockCursor = blockCursor.plus(BYTES_IN_BLOCK);
     if (blockCursor.GT(highwater)) {
       chunkCursor = chunkMap.nextChunk(chunkCursor);
       blockCursor = chunkCursor.plus(Chunk.FIRST_USABLE_BLOCK_INDEX<<LOG_BYTES_IN_BLOCK);
       blockStateCursor = Block.getBlockMarkStateAddress(blockCursor);
       highwater = Chunk.getHighWater(chunkCursor);
     } else
       blockStateCursor = blockStateCursor.plus(Block.BYTES_IN_BLOCK_STATE_ENTRY);
   } while (blockCursor.NE(end));

   return usableLines;
 }

  /****************************************************************************
   *
   * Object state
   */

  /**
   * Generic test of the liveness of an object
   *
   * @param object The object in question
   * @return True if this object is known to be live (i.e. it is marked)
   */
  @Inline
  public boolean isLive(ObjectReference object) {
    if (TMP_SUPPORT_DEFRAG && defrag.inDefrag() && isDefragSource(object))
      return ObjectHeader.isForwardedOrBeingForwarded(object) || ObjectHeader.testMarkState(object, markState);
    else
      return ObjectHeader.testMarkState(object, markState);
  }

  /**
   * Test the liveness of an object during copying sticky mark bits collection
   *
   * @param object The object in question
   * @return True if this object is known to be live (i.e. it is marked)
   */
  @Inline
  public boolean copyNurseryIsLive(ObjectReference object) {
    return ObjectHeader.isForwardedOrBeingForwarded(object) || ObjectHeader.testMarkState(object, markState);
  }

  /**
   * Test the liveness of an object during defragmentation
   *
   * @param object The object in question
   * @return True if this object is known to be live (i.e. it is marked)
   */
  @Inline
  public boolean fastIsLive(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!TMP_SUPPORT_DEFRAG || !defrag.inDefrag());
    return ObjectHeader.testMarkState(object, markState);
  }

  @Inline
  public boolean willNotMoveThisGC(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(getSpaceForObject(object) == this && defrag.inDefrag());
    return (TMP_SUPPORT_PINNING && ObjectHeader.isPinnedObject(object)) || willNotMoveThisGC(VM.objectModel.refToAddress(object));
  }

  @Inline
  public boolean willNotMoveThisNurseryGC(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(getSpaceForObject(object) == this);
    return ObjectHeader.isMatureObject(object);
  }

  @Inline
  private boolean isDefragSource(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(getSpaceForObject(object) == this);
    return isDefragSource(VM.objectModel.refToAddress(object));
  }

  @Inline
  public boolean willNotMoveThisGC(Address address) {
    return !TMP_SUPPORT_DEFRAG || !defrag.inDefrag() || (!TMP_DEFRAG_TO_IMMORTAL && defrag.spaceExhausted()) || !isDefragSource(address);
  }

  @Inline
  public boolean isDefragSource(Address address) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(getSpaceForObject(address.toObjectReference()) == this);
    return Block.isDefragSource(address);
  }


  /****************************************************************************
   *
   * Locks
   */

  /**
   * Acquire the appropriate lock depending on whether the context is
   * GC or mutator.
   */
  private void lock() {
    if (inCollection)
      gcLock.acquire();
    else
      mutatorLock.acquire();
  }

   /**
    * Release the appropriate lock depending on whether the context is
    * GC or mutator.
    */
  private void unlock() {
    if (inCollection)
      gcLock.release();
    else
       mutatorLock.release();
  }


 /****************************************************************************
  *
  * Misc
  */
  public static boolean isRecycleAllocChunkAligned(Address ptr) {
    return ptr.toWord().and(RECYCLE_ALLOC_CHUNK_MASK).EQ(Word.zero());
  }

  ChunkList getChunkMap() { return chunkMap; }
  Defrag getDefrag() { return defrag; }
}
