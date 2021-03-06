package in.talha;

import static com.mongodb.client.model.Filters.eq;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bson.Document;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.json.JSONArray;
import org.json.JSONObject;

import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClients;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;

public class AsyncServer_WaitNotify {

	static MongoCollection<Document> collection;

	public static class AsyncServlet extends HttpServlet {

		@Override
		protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
				throws ServletException, IOException {

			final AsyncContext ctxt = req.startAsync();
			ctxt.start(new Runnable() {

				@Override
				public void run() {
					System.err.println("In Async run GET");
					resp.setStatus(HttpStatus.OK_200);
					resp.setContentType("application/json");
					try {
						final String cntxtPath = req.getRequestURI();
						final String id = cntxtPath.split("/")[2];

						//
						List<Document> resList = new ArrayList<Document>();
						final AtomicReference<Throwable> error = new AtomicReference<>();

						SingleResultCallback<List<Document>> callbackWhenFinishedData = new SingleResultCallback<List<Document>>() {
							
							@Override
							
							public void onResult(final List<Document> result, final Throwable t) {
								synchronized(this) {
								if (t == null) {
									error.set(t);
								} else {
									System.out.println("success");
								}
								
								notify();
							}
							}
					}
						;

						collection.find(eq("id", id)).maxTime(200, TimeUnit.MILLISECONDS).into(resList,
								callbackWhenFinishedData);
						 synchronized (callbackWhenFinishedData) {
						        	callbackWhenFinishedData.wait();
						    }
						Throwable realError = error.get();
						if (realError != null)
							resp.getWriter().println("{\"status\":\"Unable to perform the opration\"}");
						System.out.println("size : " + resList.size());
						JSONObject jsnob = new JSONObject();
						JSONArray jArr = new JSONArray();
						for (int i = 0; i < resList.size(); i++) {
							jArr.put(new JSONObject(resList.get(i).toJson()));
						}
						jsnob.put("lst", jArr);
						jsnob.put("status", "Success");
						System.out.println("jsnob: " + jsnob);
						resp.getWriter().println(jsnob);
					} catch (Exception e) {
						e.printStackTrace();
						try {
							resp.getWriter().println("{\"status\":\"Unable to perform the opration\"}");
						} catch (IOException e1) {
							e1.printStackTrace();
						}

					}
					ctxt.complete();
				}
			});

		}

		@Override
		protected void doPost(final HttpServletRequest req, final HttpServletResponse resp)
				throws ServletException, IOException {
			final AsyncContext ctxt = req.startAsync();

			ctxt.start(new Runnable() {

				@Override
				public void run() {
					System.err.println("In Async run POST");
					System.err.println("In Async run POST");
					resp.setStatus(HttpStatus.OK_200);
					resp.setContentType("application/json");
					try {
						String body = getBody(req);
						Document doc = Document.parse(body);

						SingleResultCallback<Void> singleResultCallback = new SingleResultCallback<Void>() {

							@Override
							public void onResult(final Void result, final Throwable t) {
								System.out.println("Success");

							}
						};
						collection.insertOne(doc, singleResultCallback);
						JSONObject jsnob = new JSONObject();
						jsnob.put("status", "Successfully inserted");
						resp.getWriter().println(jsnob);
					} catch (Exception e) {
						e.printStackTrace();
						try {
							JSONObject jsnob = new JSONObject();
							jsnob.put("status", "Unable to perform the opration");
							resp.getWriter().println(jsnob);
						} catch (Exception e1) {
							e1.printStackTrace();
						}
					}

					ctxt.complete();
				}
			});
		}

	}

	private static MongoCollection<Document> getTable() {
		MongoClient mongo = MongoClients.create();

		MongoDatabase db = mongo.getDatabase("testdb");
		MongoCollection<Document> collection = db.getCollection("test");
		return collection;
	}

	public static String getBody(HttpServletRequest request) throws Exception {

		String body = null;
		StringBuilder stringBuilder = new StringBuilder();
		BufferedReader bufferedReader = null;

		try {
			InputStream inputStream = request.getInputStream();
			if (inputStream != null) {
				bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
				char[] charBuffer = new char[128];
				int bytesRead = -1;
				while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
					stringBuilder.append(charBuffer, 0, bytesRead);
				}
			} else {
				stringBuilder.append("");
			}
		} catch (Exception ex) {
			throw ex;
		} finally {
			if (bufferedReader != null) {
				try {
					bufferedReader.close();
				} catch (IOException ex) {
					throw ex;
				}
			}
		}

		body = stringBuilder.toString();
		return body;
	}

	public static void main(String[] args) throws Exception {
		collection = getTable();
		Server server = new Server(9090);
		ServletContextHandler context = new ServletContextHandler();
		context.setContextPath("/");
		ServletHolder asyncHolder = context.addServlet(AsyncServlet.class, "/database/*");
		asyncHolder.setAsyncSupported(true);
		server.setHandler(context);
		server.start();
		server.join();
	}
}