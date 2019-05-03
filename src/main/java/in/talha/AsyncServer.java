package in.talha;

import static com.mongodb.client.model.Filters.eq;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

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

import com.mongodb.Block;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClients;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;

public class AsyncServer {
	public static class AsyncServlet extends HttpServlet {
		private static final String COLCTN = "test";
		private static final String DB = "testdb";
		private static final String SUCCESSFULLY_INSERTED = "Successfully inserted";
		private static final String UNABLE_TO_PERFORM_THE_OPRATION = "Unable to perform the opration";
		private static final String NA = "NA";
		private static final String LST = "lst";
		private static final String STATUS = "status";
		private static final String SUCCESS = "Success";
		private static final String CNT = "Count";
		static HashMap<String, Object> dataCache = new HashMap<String, Object>();

		@Override
		protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
				throws ServletException, IOException {

			final AsyncContext ctxt = req.startAsync();

			ctxt.start(new Runnable() {

				public void run() {
					int i;
					System.err.println("In Async run GET");
					resp.setStatus(HttpStatus.OK_200);
					resp.setContentType("application/json");
					try {
						final String cntxtPath = req.getRequestURI();
						final String id = cntxtPath.split("/")[2];
						MongoCollection<Document> collection = getTable();
						dataCache.put(id, NA);
						
						dataCache.put(id + CNT, NA);
						Block<Document> printDocumentBlock = new Block<Document>() {
							public void apply(final Document document) {
								String json = document.toJson();
								System.out.println(">>>" + json);
								if (NA.equals(dataCache.get(id)))
									dataCache.put(id, json);
								else {
									dataCache.put(id, dataCache.get(id) + "," + json);
								}
							}
						};

						SingleResultCallback<Void> callbackWhenFinished = new SingleResultCallback<Void>() {

							public void onResult(final Void result, final Throwable t) {
								System.out.println("Operation Finished!");
							}
						};

						collection.count(eq("id", id), new SingleResultCallback<Long>() {

							public void onResult(final Long count, final Throwable t) {
								System.out.println(count);
								dataCache.put(id + CNT, count);
							}
						});
						collection.find(eq("id", id)).maxTime(200, TimeUnit.MILLISECONDS).forEach(printDocumentBlock,
								callbackWhenFinished);
						while (NA.equals(dataCache.get(id + CNT))) {

						}
						Long count = (Long) dataCache.get(id + CNT);
						System.out.println(CNT + count);
						while (NA.equals(dataCache.get(id)) && count != 0) {

						}

						System.out.println("hm : " + dataCache.get(id));
						JSONObject jsnob = new JSONObject();
						JSONArray jArr = null;
						if (dataCache.get(id) != null && !NA.equals(dataCache.get(id))
								&& (Long) dataCache.get(id + CNT) > 0)
							jArr = new JSONArray("[" + dataCache.get(id) + "]");
						if (jArr == null) {
							jArr = new JSONArray();
						}
						jsnob.put(LST, jArr);
						jsnob.put(STATUS, SUCCESS);
						System.out.println("jsnob: " + jsnob);
						resp.getWriter().println(jsnob);
					} catch (Exception e) {
						e.printStackTrace();
						try {
							JSONObject jsnob = new JSONObject();
							jsnob.put(STATUS, UNABLE_TO_PERFORM_THE_OPRATION);
							resp.getWriter().println(jsnob);
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

				public void run() {
					System.err.println("In Async run POST");

					resp.setStatus(HttpStatus.OK_200);
					resp.setContentType("application/json");
					try {
						String body = getBody(req);
						Document doc = Document.parse(body);
						MongoCollection<Document> collection = getTable();

						SingleResultCallback<Void> singleResultCallback = new SingleResultCallback<Void>() {

							public void onResult(final Void result, final Throwable t) {
								System.out.println(SUCCESS);

							}
						};
						collection.insertOne(doc, singleResultCallback);
						JSONObject jsnob = new JSONObject();
						jsnob.put(STATUS, SUCCESSFULLY_INSERTED);
						resp.getWriter().println(jsnob);
					} catch (Exception e) {
						e.printStackTrace();
						try {
							JSONObject jsnob = new JSONObject();
							jsnob.put(STATUS, UNABLE_TO_PERFORM_THE_OPRATION);
							resp.getWriter().println(jsnob);
						} catch (Exception e1) {
							e1.printStackTrace();
						}
					}

					ctxt.complete();
				}
			});
		}

		private static MongoCollection<Document> getTable() {
			MongoClient mongo = MongoClients.create();

			MongoDatabase db = mongo.getDatabase(DB);
			MongoCollection<Document> collection = db.getCollection(COLCTN);
			return collection;
		}
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