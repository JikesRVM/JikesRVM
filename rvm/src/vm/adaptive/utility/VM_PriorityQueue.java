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

  /**
   * the queue, we use elements 1..queue.length
   */
  private VM_PriorityQueueNode[] queue; 

  /**
   * the number of elements actually in the queue
   */
  private int numElements = 0;
  
  VM_PriorityQueue() {
    queue = new VM_PriorityQueueNode[20];
    
    // We don't use element #0
    for (int i=1; i<queue.length; i++) {
      queue[i] = new VM_PriorityQueueNode();
    }
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
   */
  synchronized public void insert(double _priority, Object _data) {
    numElements++;

    if (numElements == queue.length) {
      VM_PriorityQueueNode[] tmp = new VM_PriorityQueueNode[(int)(queue.length * 1.5)];
      System.arraycopy(queue, 0, tmp, 0, queue.length);
      for (int i = queue.length; i<tmp.length; i++) {
        tmp[i] = new VM_PriorityQueueNode();
      }
      queue = tmp;
    }
    
    queue[numElements].data = _data;
    queue[numElements].priority = _priority;
    
    // re-heapify
    reheapify(numElements);
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

