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

package org.jikesrvm.tuningfork;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.jikesrvm.VM;
import org.jikesrvm.Callbacks;
import org.jikesrvm.Configuration;
import org.jikesrvm.Options;
import org.jikesrvm.Callbacks.ExitMonitor;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.util.HashSetRVM;
import org.vmmagic.pragma.Uninterruptible;

import com.ibm.tuningfork.tracegen.chunk.EventChunk;
import com.ibm.tuningfork.tracegen.chunk.EventTypeChunk;
import com.ibm.tuningfork.tracegen.chunk.EventTypeSpaceChunk;
import com.ibm.tuningfork.tracegen.chunk.FeedHeaderChunk;
import com.ibm.tuningfork.tracegen.chunk.FeedletChunk;
import com.ibm.tuningfork.tracegen.chunk.PropertyTableChunk;
import com.ibm.tuningfork.tracegen.chunk.RawChunk;
import com.ibm.tuningfork.tracegen.types.EventAttribute;
import com.ibm.tuningfork.tracegen.types.EventType;
import com.ibm.tuningfork.tracegen.types.EventTypeSpaceVersion;

/**
 * TuningFork Trace Engine (roughly functionally equivalent to the
 * Logger classes in the TuningFork JavaTraceGenerationLibrary).
 */
public final class TraceEngine {

  public enum State { STARTING_UP, RUNNING_FILE, SHUTTING_DOWN, SHUT_DOWN };

  public static final TraceEngine engine = new TraceEngine();
  private static final int IO_INTERVAL_MS = 100;
  private static final int INITIAL_EVENT_CHUNKS = 64;

  private final ChunkQueue unwrittenMetaChunks = new ChunkQueue();
  private final EventChunkQueue unwrittenEventChunks = new EventChunkQueue();
  private final EventChunkQueue availableEventChunks = new EventChunkQueue();

  private FeedletChunk activeFeedletChunk = new FeedletChunk();
  private EventTypeChunk activeEventTypeChunk = new EventTypeChunk();
  private PropertyTableChunk activePropertyTableChunk = new PropertyTableChunk();

  private int nextFeedletId = 0;
  private final HashSetRVM<Feedlet> activeFeedlets = new HashSetRVM<Feedlet>();

  private OutputStream outputStream;
  private State state = State.STARTING_UP;

  private TraceEngine() {
    /* Feed header and EventTypeSpaceChunk go first, so create & enqueue during bootimage writing */
    unwrittenMetaChunks.enqueue(new FeedHeaderChunk());
    unwrittenMetaChunks.enqueue(new EventTypeSpaceChunk(new EventTypeSpaceVersion("org.jikesrvm", 1)));

    /* Pre-allocate all EventChunks into the bootimage so we can access them later via Untraced fields */
    for (int i=0; i<INITIAL_EVENT_CHUNKS; i++) {
      availableEventChunks.enqueue(new EventChunk());
    }
  }


  public void earlyStageBooting() {
    if (Options.TuningForkTraceFile == null) {
      /* tracing not enabled on this run, shut down engine to minimize overhead */
      RVMThread.getCurrentFeedlet().enabled = false;
      state = State.SHUT_DOWN;
    } else {
      unwrittenMetaChunks.enqueue(new SpaceDescriptorChunk());
    }
  }

  public void fullyBootedVM() {
    if (state != State.SHUT_DOWN) {
      String traceFile = Options.TuningForkTraceFile;
      if (!traceFile.endsWith(".trace")) {
        traceFile = traceFile+".trace";
      }

      File f = new File(traceFile);
      try {
        outputStream = new FileOutputStream(f);
      } catch (FileNotFoundException e) {
        VM.sysWriteln("Unable to open trace file "+f.getAbsolutePath());
        VM.sysWriteln("continuing, but TuningFork trace generation is disabled.");
        state = State.SHUT_DOWN;
        return;
      }

      createDaemonThreads();
      writeInitialProperites();
    }
  }


  /**
   * Put some basic properties about this VM build & current execution into the feed.
   */
  private void writeInitialProperites() {
    addProperty("rvm version", Configuration.RVM_VERSION_STRING);
    addProperty("rvm config", Configuration.RVM_CONFIGURATION);
    addProperty("Tick Frequency", "1000000000"); /* a tick is one nanosecond */
  }


  /*
   * Support for defining EventTypes
   */

  /**
   * Define an EventType
   * @param name The name to give the event
   * @param description A human readable description of the event for display in the TuningFork UI.
   */
  public EventType defineEvent(String name, String description) {
    if (state == State.SHUT_DOWN) return null;
    EventType result = new EventType(name, description);
    internalDefineEvent(result);
    return result;
  }

  /**
   * Define an EventType
   * @param name The name to give the event
   * @param description A human readable description of the event for display in the TuningFork UI.
   * @param attribute Description of the event's single data value
   */
  public EventType defineEvent(String name, String description, EventAttribute attribute) {
    if (state == State.SHUT_DOWN) return null;
    EventType result = new EventType(name, description, attribute);
    internalDefineEvent(result);
    return result;
  }

  /**
   * Define an EventType
   * @param name The name to give the event
   * @param description A human readable description of the event for display in the TuningFork UI.
   * @param attributes Descriptions of the event's data values
   */
  public EventType defineEvent(String name, String description, EventAttribute[] attributes) {
    if (state == State.SHUT_DOWN) return null;
    EventType result = new EventType(name, description, attributes);
    internalDefineEvent(result);
    return result;
  }

  private synchronized void internalDefineEvent(EventType et) {
    if (!activeEventTypeChunk.add(et)) {
      activeEventTypeChunk.close();
      unwrittenMetaChunks.enqueue(activeEventTypeChunk);
      activeEventTypeChunk = new EventTypeChunk();
      if (!activeEventTypeChunk.add(et)) {
        if (VM.VerifyAssertions) {
          VM.sysFail("EventTypeChunk is too small to to add event type "+et);
        }
      }
    }
  }

  /*
   * Support for Properties
   */

  /**
   * Add a Property (key, value) pair to the Feed.
   * @param key the key for the property
   * @param value the value for the property
   */
  public synchronized void addProperty(String key, String value) {
    if (state == State.SHUT_DOWN) return;
    if (!activePropertyTableChunk.add(key, value)) {
      activePropertyTableChunk.close();
      unwrittenMetaChunks.enqueue(activePropertyTableChunk);
      activePropertyTableChunk = new PropertyTableChunk();
      if (!activePropertyTableChunk.add(key, value)) {
        if (VM.VerifyAssertions) {
          VM.sysFail("PropertyTableChunk is too small to to add "+key+" = " +value);
        }
      }
    }
  }

  /*
   * Support for Feedlets
   */
  public synchronized Feedlet makeFeedlet(String name, String description) {
    Feedlet f = new Feedlet(this, nextFeedletId++);
    if (state == State.SHUT_DOWN) {
      f.enabled = false;
      return f;
    }
    if (!activeFeedletChunk.add(f.getFeedletIndex(), name, description)) {
      activeFeedletChunk.close();
      unwrittenMetaChunks.enqueue(activeFeedletChunk);
      activeFeedletChunk = new FeedletChunk();
      if (!activeFeedletChunk.add(f.getFeedletIndex(), name, description)) {
        if (VM.VerifyAssertions) {
          VM.sysFail("FeedletChunk is too small to to add feedlet "+name+" (" +description+")");
        }
      }
    }

    activeFeedlets.add(f);

    /* TODO: if we have less than 2 event chunks per active feedlet, then we should
     *       allocate more here!
     *       NOTE: We must ensure they are externally kept alive (see comment in EventChunkQueue).
     */
    return f;
  }

  public synchronized void removeFeedlet(Feedlet feedlet) {
    if (activeFeedlets.contains(feedlet)) {
      activeFeedlets.remove(feedlet);
      shutdownFeedlet(feedlet);
    }
  }

  private synchronized void shutdownAllFeedlets() {
    for (Feedlet f : activeFeedlets) {
      shutdownFeedlet(f);
    }
    activeFeedlets.removeAll();
  }


  private void shutdownFeedlet(Feedlet feedlet) {
    feedlet.shutdown();
    if (!activeFeedletChunk.remove(feedlet.getFeedletIndex())) {
      activeFeedletChunk.close();
      unwrittenMetaChunks.enqueue(activeFeedletChunk);
      activeFeedletChunk = new FeedletChunk();
      if (!activeFeedletChunk.remove(feedlet.getFeedletIndex())) {
        if (VM.VerifyAssertions) {
          VM.sysFail("Unable to do single remove operation on a new feedlet chunk");
        }
      }
    }
  }

  /*
   * Daemon Threads & I/O
   */

  private void createDaemonThreads() {
    /* Create primary I/O thread */
    Thread ioThread = new Thread(new Runnable() {
      @Override
      public void run() {
        ioThreadMainLoop();
      }}, "TuningFork Primary I/O thread");
    ioThread.setDaemon(true);
    ioThread.start();

    /* Install shutdown hook that will delay VM exit until I/O completes. */
    Callbacks.addExitMonitor(new ExitMonitor(){
      @Override
      public void notifyExit(int value) {
        state = State.SHUTTING_DOWN;
        while (state == State.SHUTTING_DOWN) {
          try {
            Thread.sleep(1);
          } catch (InterruptedException e) {
          }
        }
      }});
  }

  private void ioThreadMainLoop() {
    state = State.RUNNING_FILE;
    while (true) {
      try {
        Thread.sleep(IO_INTERVAL_MS);
      } catch (InterruptedException e) {
        // Do nothing.
      }
      boolean shouldShutDown = state == State.SHUTTING_DOWN;
      synchronized(this) {
        if (shouldShutDown) {
          shutdownAllFeedlets();
        }
      }
      writeMetaChunks();
      writeEventChunks();
      if (shouldShutDown) {
        state = State.SHUT_DOWN;
        return;
      }
    }
  }

  private synchronized void writeMetaChunks() {
    try {
      while (!unwrittenMetaChunks.isEmpty()) {
        RawChunk c = unwrittenMetaChunks.dequeue();
        c.write(outputStream);
      }
      if (activeEventTypeChunk != null && activeEventTypeChunk.hasData()) {
        activeEventTypeChunk.close();
        activeEventTypeChunk.write(outputStream);
        activeEventTypeChunk.reset();
      }
      if (activeFeedletChunk != null && activeFeedletChunk.hasData()) {
        activeFeedletChunk.close();
        activeFeedletChunk.write(outputStream);
        activeFeedletChunk.reset();
      }
      if (activePropertyTableChunk != null && activePropertyTableChunk.hasData()) {
        activePropertyTableChunk.close();
        activePropertyTableChunk.write(outputStream);
        activePropertyTableChunk.reset();
      }
    } catch (IOException e) {
      VM.sysWriteln("Exception while outputing trace TuningFork trace file");
      e.printStackTrace();
    }
  }

  private synchronized void writeEventChunks() {
    while (!unwrittenEventChunks.isEmpty()) {
      EventChunk c = unwrittenEventChunks.dequeue();
      try {
        c.write(outputStream);
      } catch (IOException e) {
        VM.sysWriteln("Exception while outputing trace TuningFork trace file");
        e.printStackTrace();
      }
      availableEventChunks.enqueue(c); /* reduce; reuse; recycle...*/
    }
  }

  @Uninterruptible
  EventChunk getEventChunk() {
    return availableEventChunks.dequeue();
  }

  @Uninterruptible
  public void returnFullEventChunk(EventChunk events) {
    unwrittenEventChunks.enqueue(events);
  }

}
