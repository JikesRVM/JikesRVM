/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author Julian Dolby
 */

package TestClient;

import java.util.*;

public class RequestSet {

    private final Vector requests = new Vector();

    public void addRequest(Request req) {
        requests.addElement( req );
    }

    Enumeration enumerator(boolean random, final int requestCount) {
        if (! random)

            return new Enumeration() {
               private int i = 0;
                    
               public boolean hasMoreElements() { 
                   return requestCount == -1 || i < requestCount;
               }
                    
               public Object nextElement() {
                   Object o = requests.elementAt(i++%requests.size());
                   return o;
               }
            };

        else 

            return new Enumeration() {
               private final Random rand = new Random(111000);
               private int i = 0;

               public boolean hasMoreElements() { 
                   return requestCount == -1 || i < requestCount;
               }
                    
               public Object nextElement() {
                   i++;
                   return requests.elementAt( rand.nextInt(requests.size()) );
               }
            };

    }
            
    void show() {
        System.err.println("Requests to be tested:");
        Enumeration e = requests.elements();
        while (e.hasMoreElements()) 
            System.err.println( e.nextElement().toString() );
    }

}


