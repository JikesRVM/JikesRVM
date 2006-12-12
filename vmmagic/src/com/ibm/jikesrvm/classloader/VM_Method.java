/* this is a very temporary hack while we make the transition to unified vmmagic...
 * As soon as we move to using annotations, we will no longer depend on VM_Method.
 * This dependency stemmed from our need to be able to determine whether a method
 * was modified by a pragma using the "throws" idiom. */
package com.ibm.jikesrvm.classloader;

public class VM_Method {

}
