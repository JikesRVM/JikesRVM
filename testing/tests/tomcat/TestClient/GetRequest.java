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

import java.io.IOException;
import HTTPClient.*;

public class GetRequest extends Request {

    public String toString() {
        return "GET: " + url;
    }

    public void setUrl(String url) {
        super.setUrl( url );
    }
    
    public void setDesired(String fileName) {
        super.setDesired( fileName );
    }

    HTTPResponse doGet(HTTPConnection server) 
        throws IOException, ModuleException
    {
        return server.Get( url.getPath() );
    }

}

