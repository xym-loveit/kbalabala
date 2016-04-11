package com.kbalabala.tools.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.MessageConstraints;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.codecs.DefaultHttpRequestWriterFactory;
import org.apache.http.impl.nio.codecs.DefaultHttpResponseParser;
import org.apache.http.impl.nio.codecs.DefaultHttpResponseParserFactory;
import org.apache.http.impl.nio.conn.ManagedNHttpClientConnectionFactory;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.LineParser;
import org.apache.http.nio.NHttpMessageParser;
import org.apache.http.nio.NHttpMessageParserFactory;
import org.apache.http.nio.NHttpMessageWriterFactory;
import org.apache.http.nio.conn.ManagedNHttpClientConnection;
import org.apache.http.nio.conn.NHttpConnectionFactory;
import org.apache.http.nio.conn.NoopIOSessionStrategy;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.util.CharArrayBuffer;

/**
 * Created by laziobird on 15-11-17.
 */
public class HttpAsyncClientManager {

	private static CloseableHttpAsyncClient httpclient;

	final static RequestConfig defaultRequestConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.DEFAULT)
			.setExpectContinueEnabled(true)
			.setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.NTLM, AuthSchemes.DIGEST))
			.setProxyPreferredAuthSchemes(Arrays.asList(AuthSchemes.BASIC)).build();

	// Use custom cookie store if necessary.
	static CookieStore cookieStore = new BasicCookieStore();
	// Use custom credentials provider if necessary.
	static CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
	// Create global request configuration

	public static void startServer() {
		// Use custom message parser / writer to customize the way HTTP
		// messages are parsed from and written out to the data stream.
		NHttpMessageParserFactory<HttpResponse> responseParserFactory = new DefaultHttpResponseParserFactory() {

			@Override
			public NHttpMessageParser<HttpResponse> create(final SessionInputBuffer buffer,
					final MessageConstraints constraints) {
				LineParser lineParser = new BasicLineParser() {

					@Override
					public Header parseHeader(final CharArrayBuffer buffer) {
						try {
							return super.parseHeader(buffer);
						} catch (ParseException ex) {
							return new BasicHeader(buffer.toString(), null);
						}
					}

				};
				return new DefaultHttpResponseParser(buffer, lineParser, DefaultHttpResponseFactory.INSTANCE,
						constraints);
			}

		};
		NHttpMessageWriterFactory<HttpRequest> requestWriterFactory = new DefaultHttpRequestWriterFactory();

		// Use a custom connection factory to customize the process of
		// initialization of outgoing HTTP connections. Beside standard
		// connection
		// configuration parameters HTTP connection factory can define message
		// parser / writer routines to be employed by individual connections.
		NHttpConnectionFactory<ManagedNHttpClientConnection> connFactory = new ManagedNHttpClientConnectionFactory(
				requestWriterFactory, responseParserFactory, HeapByteBufferAllocator.INSTANCE);

		// Client HTTP connection objects when fully initialized can be bound to
		// an arbitrary network socket. The process of network socket
		// initialization,
		// its connection to a remote address and binding to a local one is
		// controlled
		// by a connection socket factory.

		// SSL context for secure connections can be created either based on
		// system or application specific properties.
		SSLContext sslcontext = SSLContexts.createSystemDefault();
		// Use custom hostname verifier to customize SSL hostname verification.
		HostnameVerifier hostnameVerifier = new DefaultHostnameVerifier();

		// Create a registry of custom connection session strategies for
		// supported
		// protocol schemes.
		Registry<SchemeIOSessionStrategy> sessionStrategyRegistry = RegistryBuilder.<SchemeIOSessionStrategy> create()
				.register("http", NoopIOSessionStrategy.INSTANCE)
				.register("https", new SSLIOSessionStrategy(sslcontext, hostnameVerifier)).build();

		// Use custom DNS resolver to override the system DNS resolution.
		DnsResolver dnsResolver = new SystemDefaultDnsResolver() {

			@Override
			public InetAddress[] resolve(final String host) throws UnknownHostException {
				if (host.equalsIgnoreCase("myhost")) {
					return new InetAddress[] { InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }) };
				} else {
					return super.resolve(host);
				}
			}

		};

		// Create I/O reactor configuration
		IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
				.setIoThreadCount(Runtime.getRuntime().availableProcessors()).setConnectTimeout(30000)
				.setSoTimeout(30000).build();

		// Create a custom I/O reactort
		ConnectingIOReactor ioReactor;
		try {
			ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);

			// Create a connection manager with custom configuration.
			PoolingNHttpClientConnectionManager connManager = new PoolingNHttpClientConnectionManager(ioReactor,
					connFactory, sessionStrategyRegistry, dnsResolver);

			// Create message constraints
			MessageConstraints messageConstraints = MessageConstraints.custom().setMaxHeaderCount(200)
					.setMaxLineLength(2000).build();
			// Create connection configuration
			ConnectionConfig connectionConfig = ConnectionConfig.custom()
					.setMalformedInputAction(CodingErrorAction.IGNORE)
					.setUnmappableInputAction(CodingErrorAction.IGNORE).setCharset(Consts.UTF_8)
					.setMessageConstraints(messageConstraints).build();
			// Configure the connection manager to use connection configuration
			// either
			// by default or for a specific host.
			connManager.setDefaultConnectionConfig(connectionConfig);
			connManager.setConnectionConfig(new HttpHost("somehost", 80), ConnectionConfig.DEFAULT);

			// Configure total max or per route limits for persistent
			// connections
			// that can be kept in the pool or leased by the connection manager.
			connManager.setMaxTotal(100);
			connManager.setDefaultMaxPerRoute(10);
			connManager.setMaxPerRoute(new HttpRoute(new HttpHost("somehost", 80)), 20);

			// Create an HttpClient with the given custom dependencies and
			// configuration.
			httpclient = HttpAsyncClients.custom().setConnectionManager(connManager).setDefaultCookieStore(cookieStore)
					.setDefaultCredentialsProvider(credentialsProvider)
					// .setProxy(new HttpHost("myproxy", 8080))
					.setDefaultRequestConfig(defaultRequestConfig).build();
			httpclient.start();
			System.out.println("Async  has  started ！");
		} catch (IOReactorException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(-1);
		}
	}

	/**
	 * 
	 * @param url
	 * @param acb
	 *            reponse监听器
	 */
	public static void getURL(String url, AsyncCallBack acb) {
		HttpGet httpget = new HttpGet(url);
		// Request configuration can be overridden at the request level.
		// They will take precedence over the one set at the client level.
		RequestConfig requestConfig = RequestConfig.copy(defaultRequestConfig).setSocketTimeout(5000)
				.setConnectTimeout(5000).setConnectionRequestTimeout(5000)
				// .setProxy(new HttpHost("myotherproxy", 8080))
				.build();
		httpget.setConfig(requestConfig);

		// Execution context can be customized locally.
		HttpClientContext localContext = HttpClientContext.create();
		// Contextual attributes set the local context level will take
		// precedence over those set at the client level.
		localContext.setCookieStore(cookieStore);
		localContext.setCredentialsProvider(credentialsProvider);

		// Please note that it may be unsafe to access HttpContext instance
		// while the request is still being executed

		// Once the request has been executed the local context can
		// be used to examine updated state and various objects affected
		// by the request execution.

		// Last executed request
		localContext.getRequest();
		// Execution route
		localContext.getHttpRoute();
		// Target auth state
		localContext.getTargetAuthState();
		// Proxy auth state
		localContext.getTargetAuthState();
		// Cookie origin
		localContext.getCookieOrigin();
		// Cookie spec used
		localContext.getCookieSpec();
		// User security token
		localContext.getUserToken();

		System.out.println("Executing request " + httpget.getRequestLine());
		acb.setHttpget(httpget);
		// 线程池异步启动任务
		httpclient.execute(httpget, acb);
	}

	public static void stopServer() {
		try {
			httpclient.close();
			System.out.println("Async  has  closed ！");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(-3);
		}
	}

	public final static void main(String[] args) throws Exception {
		// 启动异步连接池
		HttpAsyncClientManager.startServer();
		// 添加一个异步任务
		HttpAsyncClientManager.getURL("http://www.baidu.com",
				new AsyncCallBack());
		
		HttpAsyncClientManager.getURL("http://www.163.com",
				new AsyncCallBack());
		
		HttpAsyncClientManager.getURL("http://www.sina.com",
				new AsyncCallBack());		

		Thread.sleep(100000);
		// 停止异步服务前，sleep一下，也可以用futureTask Get等待，可以配置项目有英文注释
		HttpAsyncClientManager.stopServer();
	}

}
