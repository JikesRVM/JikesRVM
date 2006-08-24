/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author Julian Dolby
 */

package TestClient;

class BadResult extends Exception {
    
    BadResult(Request req, String detail) {
        super(req.toString() + ": " + detail);
    }

}
