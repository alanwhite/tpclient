package xyz.arwhite.net;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class TLSProxiedSocketFactory extends SSLSocketFactory {

	private String tlsProxyHost;
	private int tlsProxyPort;
	private SSLSocketFactory sslSocketFactory;

	public TLSProxiedSocketFactory(String tlsProxyHost, int tlsProxyPort) throws NoSuchAlgorithmException {
		this.tlsProxyHost = tlsProxyHost;
		this.tlsProxyPort = tlsProxyPort;
		
		this.sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
	}
	
	public TLSProxiedSocketFactory(String tlsProxyHost, int tlsProxyPort, String cacertPEMFile) 
			throws FileNotFoundException, IOException, CertificateException, 
			KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
		
		this.tlsProxyHost = tlsProxyHost;
		this.tlsProxyPort = tlsProxyPort;
		
		try( var pemReader = new BufferedReader(
				new InputStreamReader(new FileInputStream(cacertPEMFile))) ) {
			var certFactory = CertificateFactory.getInstance("X.509");
			
			List<String> certStrings = new ArrayList<>();
			StringBuilder certData = new StringBuilder();
			String line = pemReader.readLine();
			while( line != null ) {
				if ( line.contains("BEGIN CERTIFICATE") ) 
					certData = new StringBuilder();
				else if ( line.contains("END CERTIFICATE")) 
					certStrings.add(certData.toString());
				else
					certData.append(line);
				
				line = pemReader.readLine();
			}
			
			if ( certStrings.isEmpty() )
				throw(new IllegalArgumentException("no certs found in trust PEM"));
			
			KeyStore proxyTrustStore = KeyStore.getInstance("pkcs12");
			proxyTrustStore.load(null,null);
			
			for ( String certString : certStrings ) {
				byte[] certBytes = Base64.getDecoder().decode(certString);
				var cert = certFactory.generateCertificate(new ByteArrayInputStream(certBytes));
				proxyTrustStore.setCertificateEntry(Integer.toString(cert.hashCode()), cert);
			}
			
			var tmf = TrustManagerFactory.getInstance("PKIX");
			tmf.init(proxyTrustStore);
			
			var sslContext = SSLContext.getInstance("TLSv1.3");
			sslContext.init(null, tmf.getTrustManagers(), null);
			
			sslSocketFactory = sslContext.getSocketFactory();
		} 
	}
	
	@Override
	public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {

		SSLSocket proxy = (SSLSocket) sslSocketFactory.createSocket(this.tlsProxyHost, this.tlsProxyPort);

		proxy.startHandshake();

		String connect = "CONNECT " + host + ":" + port + " HTTP/1.1\n";
		proxy.getOutputStream().write(connect.getBytes());

		//				if ( !proxyUser.isBlank() ) {
		//					var creds = proxyUser + ":" + proxyPass;
		//					String auth = "Proxy-Authorization: Basic " 
		//							+ Base64.getEncoder().encodeToString(creds.getBytes()) + "\n";
		//					remoteSocket.getOutputStream().write(auth.getBytes());
		//				}

		proxy.getOutputStream().write("\n".getBytes());

		var reader = new BufferedReader(
				new InputStreamReader(proxy.getInputStream(), "US-ASCII"));
		var httpResponse = reader.readLine();
		var words = httpResponse.split(" ");
		if ( words.length < 2 || !"200".equals(words[1]) ) {
			throw(new IOException("Unhandled response from proxy "+httpResponse));
		}

		// drain and ignore any response headers
		int len = -1;
		while ( len != 0 ) {
			var line = reader.readLine();
			len = line.length();
		}

		// wrap the established connection in another SSL socket
		SSLSocketFactory regularFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
		SSLSocket targetSocket = (SSLSocket)regularFactory.createSocket(proxy, host, port, true);
		
		return targetSocket;

	}

	@Override
	public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
		return createSocket(null, host, port, true);
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
			throws IOException, UnknownHostException {
		return createSocket(null, host, port, true);
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		return createSocket(null, host.getHostName(), port, true);
	}

	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
			throws IOException {
		return createSocket(null, address.getHostName(), port, true);
	}

	@Override
	public String[] getDefaultCipherSuites() {
		return sslSocketFactory.getDefaultCipherSuites();
	}

	@Override
	public String[] getSupportedCipherSuites() {
		return sslSocketFactory.getSupportedCipherSuites();
	}

}
