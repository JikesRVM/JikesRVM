/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;

/**
 * This class implements a priority queue using the standard
 * (balanced partially-ordered tree, i.e., "heap") algorithm.  
 * Smaller priority objects are in the front of the queue.
 *
 * @author Michael Hind
 */
class VM_PriorityQueue {

  private static final boolean DEBUG = false;

  /**
   * the queue, we use elements 1..size
   */
  private VM_PriorityQueueNode[] queue; 

  /**
   * the index of the last valid entry
   *  size = queue.length - 1
   */
  private int size;     

  /**
   * the number of elements actually in the queue
   */
  private int numElements; 
  
  /**
   * Constructor
   * @param initialSize the initial number of elements
   */
  VM_PriorityQueue(int initialSize) {
    // We don't use element #0
    int allocSize = initialSize+1;
    queue = new VM_PriorityQueueNode[allocSize];
    
    for (int i=0; i<allocSize; i++) {
      queue[i] = new VM_PriorityQueueNode();
    }

    // We use elements 1..size
    size = initialSize;
    numElements = 0;
  }

  /**
   * Determines number of elements in the queue
   * @return number of elements in the queue
   */
  synchronized final public int numElements() {
    return numElements;
  }

  /**
   * Checks if the queue is empty
   * @return is the queue empty?
   */
  synchronized final boolean isEmpty() {
    return numElements == 0;
  }

  /**
   * Checks if the queue is full
   * @return is the queue full?
   */
  synchronized final boolean isFull() {
    return numElements == size;
  }

  /**
   * Starting at the position passed, swap with parent until heap condition
   * is satisfied, i.e., bubble up
   * @param startingElement the position to start at 
   */
  private void reheapify(int startingElement) {
    int current = startingElement;
    int parent = numElements / 2;
    // keep checking parents that violate the magic condition
    while (parent > 0 && queue[parent].priority < queue[current].priority) {
      //        System.out.println("Parent: "+ parent +", Current: "+ current);
      //        System.out.println("Contents before: "+ this);
      // exchange parrent and current values
      VM_PriorityQueueNode tmp = queue[parent];
      queue[parent] = queue[current];
      queue[current] = tmp;
      
      //        System.out.println("Contents after: "+ this);
      // go up 1 level
      current = parent;
      parent = parent / 2;
    }
  }

  /**
   * Insert the object passed with the priority value passed
   * @param _priority  the priority of the inserted object
   * @param _data the object to insert
   * @return true if the insert succeeded, false if it failed because the queue was full
   */
  synchronized public boolean insert(double _priority, Object _data) {
    if (isFull()) {
      return false;
    }

    numElements++;
    queue[numElements].data = _data;
    queue[numElements].priority = _priority;
    
    // re-heapify
    reheapify(numElements);
    return true;
  }

  /**
   * Insert the object passed with the priority value passed, but if the queue
   *   is full, a lower priority object is removed to make room for this object.
   *   If no lower priority object is found, we don't insert.
   * @param _priority  the priority to 
   * @param _data the object to insert
   * @return true if the insert succeeded, false if it failed because the queue was full
   */
  synchronized public boolean prioritizedInsert(double _priority, Object _data) {
    if (DEBUG) VM.sysWrite("prioInsert: prio: "+_priority+",size: "+
                           size +", numElements: "+ numElements +")\n");

    // the queue isn't full just use the regular insert
    if (!isFull()) {
      return insert(_priority, _data);
    }

    // search the leaves of the tree and find the lowest priority element
    //  "numElements" is the last element.  The first leaf is the next
    //  node after its parent, i.e,  numElements/2  + 1
    //  We'll go from this value up to numElements.
    int firstChild = numElements/2 + 1;
    int evictee = -1;
    double evicteePriority = _priority;  // start of with the priority of the insertor
    for (int i=firstChild; i<=numElements; i++) {
      if (queue[i].priority < evicteePriority) {
        if (DEBUG) VM.sysWrite("  candidate at entry "+ i 
                               +", prio: "+queue[i].priority +")\n");
        evictee = i;
        evicteePriority = queue[i].priority;
      }
    }

    // Did we find an evictee?
    if (evictee != -1) {
      queue[evictee].data = _data;
      queue[evictee].priority = _priority;
    
      if (DEBUG) VM.sysWrite("  evicting entry "+ evictee +")\n");

      // re-heapify
      reheapify(evictee);
      return true;
    }
    else {
      // didn't find a spot :-(
      return false;
    }
  }

  /**
   * Remove and return the front (minimum) object
   * @return the front (minimum) object or null if the queue is empty.
   */
  synchronized public Object deleteMin() {
    if (isEmpty()) return null;

    Object returnValue = queue[1].data;
    // move the "last" element to the root and reheapify by pushing it down
    queue[1].priority = queue[numElements].priority;
    queue[1].data = queue[numElements].data;
    numElements--;
    
    // reheapify!!!
    int current = 1;
    
    // The children live at 2*current and  2*current+1 
    int child1 = 2 * current;
    while (child1 <= numElements) {
      int child2 = 2 * current + 1;
      
      // find the smaller of the two children
      int smaller;
      if (child2 <= numElements && queue[child2].priority > queue[child1].priority) {
        smaller = child2;
      } else {
        smaller = child1;
      }
      
      if (queue[smaller].priority <= queue[current].priority) {
        break;
      }
      else {
        // exchange parrent and current values
        VM_PriorityQueueNode tmp = queue[smaller];
        queue[smaller] = queue[current];
        queue[current] = tmp;
        
        // go down 1 level
        current = smaller;
        child1 = 2 * current;
      }
    }
    return returnValue;
  }

  /**
   *  Return the priority of front object without removing it
   *  @return the priority of the front object
   */
  synchronized final public double rootValue() {
    if (VM.VerifyAssertions) VM._assert(!isEmpty());

    return queue[1].priority;
  }

  /**
   *  Prints the contents of the queue
   *  @return the queue contents
   */
  synchronized public String toString() {
    StringBuffer sb = new StringBuffer(" --> ");
    sb.append("Dumping Queue with "+ numElements +" elements:\n");
    if (numElements >= 1) {
      sb.append("\t");
    }

    for (int i=1; i<=numElements; i++) {
      sb.append(queue[i].toString());
      if (i<numElements)
        sb.append("\n\t");
    }
    return sb.toString();
  }
}

/**
 * A local class that holds the nodes of the priority tree
 */
class VM_PriorityQueueNode {

  /**
   * the value to compare on, larger is better
   */
  public double priority; 

  /**
   * the associated data 
   */
  public Object data;     

  public String toString() {
    return (new StringBuffer(data +" ... ["+ priority +"]")).toString();
  }

}

