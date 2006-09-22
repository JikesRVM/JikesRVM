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

class BadRequest extends Exception {
    
    private String errorText;

    BadRequest(String msg, String detail) {
        super(msg);
        errorText = detail;
    }

    public String getMessage() {
        return super.getMessage() + errorText;
    }
}
