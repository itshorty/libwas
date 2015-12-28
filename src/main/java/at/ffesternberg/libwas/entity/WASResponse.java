package at.ffesternberg.libwas.entity;

import java.util.Date;

/**
 * A raw Response from the WAS device
 * @author floriahu
 */
public class WASResponse {
	private String response;
	private Date timestamp;
	
	public WASResponse(){
		
	}
	
	public WASResponse(String response){
		this.response=response;
		this.timestamp=new Date();
	}

	public String getResponse() {
		return response;
	}

	public void setResponse(String response) {
		this.response = response;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}
	
	
}
