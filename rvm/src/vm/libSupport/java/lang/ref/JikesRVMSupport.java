/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang.ref;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 * @author Perry Cheng
 */
public final class JikesRVMSupport {

  /**
     Determine whether this reference has ever been enqueued.
     @param r the Reference object
     @return <code>true</codeif reference has ever been enqueued
   */
  public static final boolean wasEnqueued(Reference r) {
    return r.wasEnqueued;
  }
  
  /**
     Put this Reference object on its ReferenceQueue (if it has one)
     when its referent is no longer sufficiently reachable. The
     definition of "reachable" is defined by the semantics of the
     particular subclass of Reference. The implementation of this
     routine is determined by the the implementation of
     java.lang.ref.ReferenceQueue in GNU classpath. It is in this
     class rather than the public Reference class to ensure that Jikes
     has a safe way of enqueueing the object, one that cannot be
     overridden by the application program.
     
     @see java.lang.ref.ReferenceQueue
     @param r the Reference object
     @return <code>true</codeif the reference was enqueued
   */
  public static final boolean enqueue(Reference r) {
    if (r.queue != null && r.nextOnQueue == null)
      {
        r.wasEnqueued = true;
        r.queue.enqueue(r);
        r.queue = null;
        return true;
      }
    return false;
  }

}
