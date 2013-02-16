package com.bitgriff.http;



public interface HttpProgressListener {
	void sendStarted(int total);
	
	void sendProgress(int uploaded, int total);
	
	void sendError(Exception ex);
	
	void sendDone();
}
