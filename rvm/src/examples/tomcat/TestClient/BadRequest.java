/*
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
