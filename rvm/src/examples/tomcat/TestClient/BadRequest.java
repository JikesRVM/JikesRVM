package TestClient;

class BadRequest extends Exception {
    
    private String errorText;

    BadRequest(String msg, String detail) {
	super(msg);
	errorText = detail;
    }

}
