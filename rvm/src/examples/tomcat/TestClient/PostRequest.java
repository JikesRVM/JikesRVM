/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author Julian Dolby
 */

package TestClient;

import java.io.IOException;
import HTTPClient.*;

public class PostRequest extends Request {

    private NVPair[] parameters = new NVPair[0];

    public void addElement(String name, String value) {
        NVPair p = new NVPair(name, value);
        NVPair[] newParameters = new NVPair[ parameters.length + 1 ];
        newParameters[ parameters.length ] = p;
        for(int i = 0; i < parameters.length; i++)
            newParameters[i] = parameters[i];
        
        parameters = newParameters;
    }

    public void setUrl(String url) {
        super.setUrl( url );
    }

    public void setDesired(String fileName) {
        super.setDesired( fileName );
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("POST: " + url + "\n");
        for(int i = 0; i < parameters.length; i++)
            buf.append("with " + parameters[i]);

        return buf.toString();
    }

    HTTPResponse doGet(HTTPConnection server) 
        throws IOException, ModuleException
    {
        return server.Post( url.getPath(), parameters );
    }

}

