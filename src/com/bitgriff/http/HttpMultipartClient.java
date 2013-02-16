package com.bitgriff.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Http client with multipart/form-data support for files upload. 
 * Supports file upload progress reporting.
 * 
 * @author Moskvichev Andrey V.
 *
 */
public class HttpMultipartClient {
	abstract private class Part {
		private String header;
		private int length;
		
		public String getHeader() {
			if (header == null)
				header = formatHeader();
			return header;
		}
		
		/**
		 * Returns full (with part header) part length.
		 * @return part length
		 */
		public int getLength() {
			if (length == 0) {
				length = getHeader().length();
				length += getContentLength();
				length += CRLF.length();
				//length += BOUNDARY_LINE.length(); // part end
			}
			return length;
		}
		
		abstract protected String formatHeader();
		abstract protected int getContentLength();
		
		public void send(PrintStream out) throws IOException {
			//out.print(getHeader());
			sendContent(out);
			out.print(CRLF);
			//out.print(BOUNDARY_LINE);
		}
		
		abstract protected void sendContent(PrintStream out) throws IOException;
	}
	
	private class FieldPart extends Part {
		private String name;
		private String value;

		public FieldPart(String name, String value) {
			this.name = name;
			this.value = value;
		}

		@Override
		protected String formatHeader() {
			StringBuilder header = new StringBuilder();
			
			header.append(BOUNDARY_LINE);
			header.append("Content-Disposition: form-data; name=\"").append(name).append("\"").append(CRLF);
			header.append("Content-type: ").append("text/plain").append(CRLF);
			header.append(CRLF);
			return header.toString();
		}

		@Override
		protected int getContentLength() {
			return value.length();
		}

		@Override
		protected void sendContent(PrintStream out) throws IOException {
			out.print(value);
		}
	}
	
	private class FilePart extends Part {
		private String name;
		private String contentType;
		private String fileName;

		private File file;
			
		public FilePart(String name, String contentType, String fileName, File file) {
			this.name = name;
			this.contentType = contentType;
			this.fileName = fileName;
			this.file = file;
		}
		
		@Override
		protected String formatHeader() {
			StringBuilder header = new StringBuilder();
			
			header.append(BOUNDARY_LINE);
			header.append("Content-Disposition: form-data; name=\"").append(name).append("\"; filename=\"").append(fileName).append("\"").append(CRLF);
			header.append("Content-type: ").append(contentType).append(CRLF);
			header.append(CRLF);
			return header.toString();
		}

		@Override
		protected int getContentLength() {
			return (int) file.length();
		}

		@Override
		protected void sendContent(PrintStream out) throws IOException {
			byte buf[] = new byte[1024 * 256];
			int len;
			
			FileInputStream in = new FileInputStream(file);
			try {
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
			}
			finally {
				in.close();
			}
		}
	}

	final private static String CRLF = "\r\n";
	final private static String BOUNDARY = "xgeyy6u56845245ggbh576youu27o96j8564235f";
	final private static String BOUNDARY_LINE = "--"+BOUNDARY+CRLF;
	
	private class ProgressoutputStream extends OutputStream {

		private OutputStream out;
		private int total;
		private int sent;
		private int prevProgress;
		
		public ProgressoutputStream(OutputStream out, int total) {
			this.out = out;
			this.total = total;
		}
		
		@Override
		public void write(int oneByte) throws IOException {
			out.write(oneByte);
			
			//System.out.write(oneByte); // XXX debug!
			
			sent++;
			
			if ((sent - prevProgress) > 16 * 1024 || sent == total) {
				listener.sendProgress(sent, total);
				
				prevProgress = sent;
			}
		}

		@Override
		public void close() throws IOException {
			super.close();
			out.close();
		}
		
	}
	
	private List<Part> parts = new ArrayList<Part>();
	private Map<String, String> headers = new HashMap<String, String>();
	
	private HttpProgressListener listener;
	
	private URI uri;
	private int contentLength;

	public HttpMultipartClient(String uri, HttpProgressListener listener) {
		this(URI.create(uri), listener);
	}
	
	public HttpMultipartClient(URI uri, HttpProgressListener listener) {
		if (!uri.getScheme().equals("http"))
			throw new IllegalArgumentException("Invalid scheme: "+uri.getScheme());
		
		this.uri = uri;
		this.listener = listener;
	}

	public void addHeader(String header, String value) {
		headers.put(header, value);
	}
	
	public void addField(String name, String value) {
		parts.add(new FieldPart(name, value));
	}
	
	public void addFile(String name, String contentType, String fileName, File file) {
		parts.add(new FilePart(name, contentType, fileName, file));
	}
	
	public void post() {
		contentLength = 0;
		
		Socket socket = null;
		try {
			socket = openSocket();
			
			// send request
			PrintStream out = new PrintStream(new ProgressoutputStream(socket.getOutputStream(), calculateContentLength()));
			
			try {
				InputStream in = socket.getInputStream();
				try {
					listener.sendStarted(calculateContentLength());
					
					// send http header
					out.print(formatHeaders());

					// send each part
					//out.print(CRLF);
					for (Part part : parts) {
						out.print(part.getHeader());
						part.send(out);
					}

					out.print(formatEpilog());
					//out.flush();
					socket.shutdownOutput();

					// read response
					String response = receive(in);
					int code = getStatusCode(response);

					if (code != 302)// && code != 200)
						throw new RuntimeException("Invalid response. " + getStatusString(response));
					
					listener.sendDone();
					
					if (code == 200)
						return ;
				}
				finally {
					in.close();
				}
			}
			finally {
				out.close();
			}

		}
		catch (Exception ex) {
			listener.sendError(ex);
		}
		finally {
			if (socket != null)
				try { socket.close(); } catch (IOException e) { }
		}
		
		try {
			String response = sendRaw(formatGet());
			int code = getStatusCode(response);
			
			if (code != 200)
				throw new RuntimeException("Invalid response. " + getStatusString(response));
			// TODO indicate error if code is not http_ok or there is error message from server 
			
			/*
			String msg = getResponseMessage("messages status", response);
			if (!msg.startsWith("OK"))
				throw new RuntimeException(msg);
			*/
		}
		catch (Exception e) {
			listener.sendError(e);
		} 
		
	}
	
	private int getStatusCode(String response) {
		String status = getStatusString(response);
		int iCode = status.indexOf(' ');
		if (iCode == -1)
			return 0;
		
		iCode++;
		int iCodeEnd = status.indexOf(' ', iCode);
		if (iCodeEnd == -1)
			return 0;
		
		return Integer.parseInt(status.substring(iCode, iCodeEnd));
	}

	private String getStatusString(String response) {
		int iEnd = response.indexOf(CRLF);
		if (iEnd == -1)
			return null;
		
		return response.substring(0, iEnd);
	}
	
	private static String getResponseMessage(String block, String text) {
		int iMsgError = text.indexOf("\""+block+"\"");
		if (iMsgError == -1) 
			return null;
		
		iMsgError = text.indexOf(">", iMsgError) + 1;
		
		int iMsgErrorEnd = text.indexOf("<", iMsgError);
		
		String msgError = text.substring(iMsgError, iMsgErrorEnd);
		return msgError.trim();
	}

	private String sendRaw(String raw) throws UnknownHostException, IOException {
		Socket socket = openSocket();
		
		try {
			PrintStream out = new PrintStream(socket.getOutputStream());
			
			try {
				InputStream in = socket.getInputStream();
				try {
					// send GET 
					out.print(formatGet());
					socket.shutdownOutput();
					
					String response = receive(in);
					return response;
				}
				finally {
					in.close();
				}
			}
			finally {
				out.close();
			}
		}
		finally {
			socket.close();
		}
	}
	
	private Socket openSocket() throws UnknownHostException, IOException {
		String host = uri.getHost();
		int port = uri.getPort();
		if (port == -1)
			port = 80;
		
		return new Socket(host, port);
	}
	
	private String formatHeaders() {
		StringBuilder header = new StringBuilder();
		
		String host = uri.getHost();
		String path = uri.getPath() + "?" + uri.getQuery();
		
		header.append("POST ").append(path).append(" HTTP/1.1").append(CRLF);
		header.append("Host: ").append(host).append(CRLF);
		
		for (String headerName : headers.keySet()) {
			header.append(headerName).append(": ").append(headers.get(headerName)).append(CRLF);
		}
		
		header.append("Content-type: multipart/form-data; boundary=").append(BOUNDARY).append(CRLF);
		header.append("Content-length: ").append(calculateContentLength()).append(CRLF);
		header.append(CRLF);
		return header.toString();
	}
	
	private String formatGet() {
		StringBuilder header = new StringBuilder();
		
		String host = uri.getHost();
		String path = uri.getPath() + "?" + uri.getQuery();
		
		header.append("GET ").append(path).append(" HTTP/1.1").append(CRLF);
		header.append("Host: ").append(host).append(CRLF);
		
		for (String headerName : headers.keySet()) {
			header.append(headerName).append(": ").append(headers.get(headerName)).append(CRLF);
		}
		
		header.append(CRLF);
		return header.toString();
	}
	
	private String formatEpilog() {
		StringBuilder header = new StringBuilder();
		
		header.append("--").append(BOUNDARY).append("--").append(CRLF);
		header.append(CRLF);
		return header.toString();
	}
	
	private int calculateContentLength() {
		// lazy calulation
		if (contentLength != 0)
			return contentLength;
		
		contentLength = 0;
		
		contentLength += 3 * CRLF.length();
		
		for (Part part : parts) {
			contentLength += part.getLength();
		}
		
		contentLength += formatEpilog().length();
		return contentLength;
	}

	private String receive(InputStream in) throws IOException {
		byte buf[] = new byte[1024];
		int len;

		StringBuilder text = new StringBuilder();			
		while ((len = in.read(buf)) > 0) {
			text.append(new String(buf, 0, len));
		}
		return text.toString();
	}

}
