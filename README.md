tpclient - tls proxy client
=====

Java doesn't know how to encrypt the connection to a HTTP proxy using TLS, this helper provides that.

***

## Background

It's becoming more common for all connections to be encrypted, including to http proxies
even if you're going to then use the CONNECT verb to create an end to end encrypted connection
over the proxy. The support Java provides is that the connection from your http client to the proxy,
over which the the CONNECT verb is sent along with any headers, does not use TLS.

curl started supporting this in 2016 https://daniel.haxx.se/blog/2016/11/26/https-proxy-with-curl/

## Demo / Test Environment Setup
To demonstrate what this project delivers consider the following scenario:

tinyproxy is running listening for proxy requests on localhost:8888 but tinyproxy can't serve https natively so ... we need something in front of it that does that

enter stunnel. This listens on port 3128 with tls, and relay any connection to tinyproxy

App --- https ----> stunnel --- http ----> tinyproxy --- tcp ----> target host

App opens an https connection, which is terminated by stunnel which passes all IO to tinyproxy, so the App can write the CONNECT verb, tinyproxy then makes a TCP connection to the target host, and sends the 200 OK back all the way to the App.

Useful blog that describes what we're setting up here - https://bencane.com/2017/04/15/using-stunnel-and-tinyproxy-to-hide-http-traffic/

On macOS brew install stunnel and brew install tinyproxy work can be used to set up the demo environment

brew services start tinyproxy gets it u and running on port localhost:8888 by default

Ensure tinyproxy is working by directly proxing through it. The verbose logging shows the connection to localhost:8888 and the use of the CONNECT verb.

```
$ curl -v -x "localhost:8888" https://httpbin.org/ip
*   Trying ::1...
* TCP_NODELAY set
* Connected to localhost (::1) port 8888 (#0)
* allocate connect buffer!
* Establish HTTP proxy tunnel to httpbin.org:443
> CONNECT httpbin.org:443 HTTP/1.1
> Host: httpbin.org:443
> User-Agent: curl/7.64.1
> Proxy-Connection: Keep-Alive
>
< HTTP/1.0 200 Connection established
< Proxy-agent: tinyproxy/1.11.1
<
* Proxy replied 200 to CONNECT request
* CONNECT phase completed!
* ALPN, offering h2
* ALPN, offering http/1.1
* successfully set certificate verify locations:
*   CAfile: /etc/ssl/cert.pem
  CApath: none
* TLSv1.2 (OUT), TLS handshake, Client hello (1):
* CONNECT phase completed!
* CONNECT phase completed!
* TLSv1.2 (IN), TLS handshake, Server hello (2):
* TLSv1.2 (IN), TLS handshake, Certificate (11):
* TLSv1.2 (IN), TLS handshake, Server key exchange (12):
* TLSv1.2 (IN), TLS handshake, Server finished (14):
* TLSv1.2 (OUT), TLS handshake, Client key exchange (16):
* TLSv1.2 (OUT), TLS change cipher, Change cipher spec (1):
* TLSv1.2 (OUT), TLS handshake, Finished (20):
* TLSv1.2 (IN), TLS change cipher, Change cipher spec (1):
* TLSv1.2 (IN), TLS handshake, Finished (20):
* SSL connection using TLSv1.2 / ECDHE-RSA-AES128-GCM-SHA256
* ALPN, server accepted to use h2
* Server certificate:
*  subject: CN=httpbin.org
*  start date: Oct 21 00:00:00 2022 GMT
*  expire date: Nov 19 23:59:59 2023 GMT
*  subjectAltName: host "httpbin.org" matched cert's "httpbin.org"
*  issuer: C=US; O=Amazon; OU=Server CA 1B; CN=Amazon
*  SSL certificate verify ok.
* Using HTTP2, server supports multi-use
* Connection state changed (HTTP/2 confirmed)
* Copying HTTP/2 data in stream buffer to connection buffer after upgrade: len=0
* Using Stream ID: 1 (easy handle 0x7fe6eb00d600)
> GET /ip HTTP/2
> Host: httpbin.org
> User-Agent: curl/7.64.1
> Accept: */*
>
* Connection state changed (MAX_CONCURRENT_STREAMS == 128)!
< HTTP/2 200
< date: Fri, 13 Jan 2023 22:09:46 GMT
< content-type: application/json
< content-length: 32
< server: gunicorn/19.9.0
< access-control-allow-origin: *
< access-control-allow-credentials: true
<
{
  "origin": "45.133.63.176"
}
* Connection #0 to host localhost left intact
* Closing connection 0
```

Edit /usr/local/etc/stunnel/stunnel.conf to look like

```
[tinyproxy]
accept = 0.0.0.0:3128
connect = 127.0.0.1:8888
cert = /usr/local/etc/stunnel/stunnel.pem
key = /usr/local/etc/stunnel/stunnel.pem
```

Run stunnel from the command line, it will detach and run in the background

We can now ask curl to make an https connection to send the CONNECT verb, we tell it to use stunnel as the proxy (port 3128) which as we know from above provides the TLS negotiation and then forwards to tinyproxy

```
$ curl -v --proxy-cacert /usr/local/etc/stunnel/stunnel.pem -x "https://localhost:3128" https://httpbin.org/ip
*   Trying ::1...
* TCP_NODELAY set
* Connection failed
* connect to ::1 port 3128 failed: Connection refused
*   Trying 127.0.0.1...
* TCP_NODELAY set
* Connected to localhost (127.0.0.1) port 3128 (#0)
* ALPN, offering http/1.1
* successfully set certificate verify locations:
*   CAfile: /usr/local/etc/stunnel/stunnel.pem
  CApath: none
* TLSv1.2 (OUT), TLS handshake, Client hello (1):
* TLSv1.2 (IN), TLS handshake, Server hello (2):
* TLSv1.2 (IN), TLS handshake, Certificate (11):
* TLSv1.2 (IN), TLS handshake, Server key exchange (12):
* TLSv1.2 (IN), TLS handshake, Server finished (14):
* TLSv1.2 (OUT), TLS handshake, Client key exchange (16):
* TLSv1.2 (OUT), TLS change cipher, Change cipher spec (1):
* TLSv1.2 (OUT), TLS handshake, Finished (20):
* TLSv1.2 (IN), TLS change cipher, Change cipher spec (1):
* TLSv1.2 (IN), TLS handshake, Finished (20):
* SSL connection using TLSv1.2 / ECDHE-RSA-AES256-GCM-SHA384
* ALPN, server did not agree to a protocol
* Proxy certificate:
*  subject: C=PL; ST=Mazovia Province; L=Warsaw; O=Stunnel Developers; OU=Provisional CA; CN=localhost
*  start date: Nov  2 07:02:07 2022 GMT
*  expire date: Nov  2 07:02:07 2023 GMT
*  common name: localhost (matched)
*  issuer: C=PL; ST=Mazovia Province; L=Warsaw; O=Stunnel Developers; OU=Provisional CA; CN=localhost
*  SSL certificate verify ok.
* allocate connect buffer!
* Establish HTTP proxy tunnel to httpbin.org:443
> CONNECT httpbin.org:443 HTTP/1.1
> Host: httpbin.org:443
> User-Agent: curl/7.64.1
> Proxy-Connection: Keep-Alive
>
< HTTP/1.0 200 Connection established
< Proxy-agent: tinyproxy/1.11.1
<
* Proxy replied 200 to CONNECT request
* CONNECT phase completed!
* ALPN, offering h2
* ALPN, offering http/1.1
* successfully set certificate verify locations:
*   CAfile: /etc/ssl/cert.pem
  CApath: none
* TLSv1.2 (OUT), TLS handshake, Client hello (1):
* CONNECT phase completed!
* CONNECT phase completed!
* TLSv1.2 (IN), TLS handshake, Server hello (2):
* TLSv1.2 (IN), TLS handshake, Certificate (11):
* TLSv1.2 (IN), TLS handshake, Server key exchange (12):
* TLSv1.2 (IN), TLS handshake, Server finished (14):
* TLSv1.2 (OUT), TLS handshake, Client key exchange (16):
* TLSv1.2 (OUT), TLS change cipher, Change cipher spec (1):
* TLSv1.2 (OUT), TLS handshake, Finished (20):
* TLSv1.2 (IN), TLS change cipher, Change cipher spec (1):
* TLSv1.2 (IN), TLS handshake, Finished (20):
* SSL connection using TLSv1.2 / ECDHE-RSA-AES128-GCM-SHA256
* ALPN, server accepted to use h2
* Server certificate:
*  subject: CN=httpbin.org
*  start date: Oct 21 00:00:00 2022 GMT
*  expire date: Nov 19 23:59:59 2023 GMT
*  subjectAltName: host "httpbin.org" matched cert's "httpbin.org"
*  issuer: C=US; O=Amazon; OU=Server CA 1B; CN=Amazon
*  SSL certificate verify ok.
* Using HTTP2, server supports multi-use
* Connection state changed (HTTP/2 confirmed)
* Copying HTTP/2 data in stream buffer to connection buffer after upgrade: len=0
* Using Stream ID: 1 (easy handle 0x7f922d80d600)
> GET /ip HTTP/2
> Host: httpbin.org
> User-Agent: curl/7.64.1
> Accept: */*
>
* Connection state changed (MAX_CONCURRENT_STREAMS == 128)!
< HTTP/2 200
< date: Fri, 13 Jan 2023 22:28:18 GMT
< content-type: application/json
< content-length: 32
< server: gunicorn/19.9.0
< access-control-allow-origin: *
< access-control-allow-credentials: true
<
{
  "origin": "45.133.63.176"
}
* Connection #0 to host localhost left intact
* Closing connection 0
```

We've now installed and configured the secure HTTP CONNECT solution, and proven it's working with curl. Next is to show how to make Java used it.

## Java Implementation

### Direct Unencrypted Proxy
We prove we can use standard core Java libraries to talk directly to tinyproxy running on port 8888, i.e. bypassing stunnel and any need to encrypt. This test ensure tinyproxy is running correctly.

### Encrypted Proxy
We use our custom socket factory to transparently connect via an encrypted proxy, in our case provided by stunnel front-ending tinyproxy. In this instance there is no use of the native Java proxy capabilities, we don't, and can't use those because they lack the ability to make a TLS connection to the proxy.


