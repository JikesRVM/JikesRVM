/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang.ref;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 *
 */
public class JikesRVMSupport {

    public static void setReferenceLock(Object o) {
	Reference.lock = o;
    }

}
