package net.dsf.transformation;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.io.IOUtils;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schibsted.spt.data.jslt.Expression;
import com.schibsted.spt.data.jslt.Parser;

import freemarker.core.JSONOutputFormat;
import freemarker.log.Logger;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

/**
 * Premissas:
 * <ul>
 * <li>cada serviço recebe e deve retornar uma string</li>
 * <li>cada biblioteca deverá produzir os mesmos JSON</li>
 * </ul>
 * 
 * @author Fabio De Santi
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TransformatioBenchmarkTest {

	private static final Logger logger = Logger.getLogger(TransformatioBenchmarkTest.class.getName());

	private static Configuration cfg;

	public static final int ITERATIONS = 1000000;

	private ObjectMapper mapper = new ObjectMapper();

	private static Map<String, Long> timer = new HashMap<>();

	static {
		cfg = new Configuration(Configuration.VERSION_2_3_28);

		try {
			cfg.setDirectoryForTemplateLoading(new File(ClassLoader.getSystemResource("templates").getFile()));
		} catch (IOException e) {
			logger.error("error setting up freemarker template", e);
		}

		cfg.setDefaultEncoding("UTF-8");
		cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
		cfg.setLogTemplateExceptions(false);
		cfg.setWrapUncheckedExceptions(true);
		cfg.setOutputFormat(JSONOutputFormat.INSTANCE);
	}

	private String getInput(String resourceName) throws IOException {
		return IOUtils.resourceToString(resourceName, Charset.forName("UTF-8"), ClassLoader.getSystemClassLoader());
	}

	public void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
		}
	}

	public long threadPoolFreeMarker(int poolSize, Template template) throws IOException, InterruptedException {

		long ms = 0L;
		CountDownLatch cdl = new CountDownLatch(ITERATIONS);
		ThreadPoolExecutor tpe = (ThreadPoolExecutor) Executors.newFixedThreadPool(poolSize);
		String source = getInput("sample-input.json");
		ms = System.currentTimeMillis();

		for (int l = 0; l < ITERATIONS; l++) {
			tpe.submit(() -> {
				try {
					// string to Map required by freemarker
					@SuppressWarnings("unchecked")
					Map<String, Object> map = mapper.readValue(source.getBytes(), Map.class);

					StringWriter out = new StringWriter(4096);
					template.process(map, out);
					String res = out.toString();
					if (cdl.getCount() == 1L)
						logger.info(res);

					assertNotNull(res); // our test case expects result as string
					cdl.countDown();

				} catch (IOException | TemplateException e) {
					logger.error("error processing document", e);
				}
			});
		}
		cdl.await();
		return ms;
	}

	public long threadPoolJSLT(int poolSize) throws IOException, InterruptedException {

		long ms = 0L;
		CountDownLatch cdl = new CountDownLatch(ITERATIONS);
		ThreadPoolExecutor tpe = (ThreadPoolExecutor) Executors.newFixedThreadPool(poolSize);

		String source = getInput("sample-input.json");

		try (InputStream is = ClassLoader.getSystemResourceAsStream("jslt-template.json")) {
			try (Scanner s = new Scanner(is)) {
				s.useDelimiter("\n");
				StringBuilder sb = new StringBuilder();
				while (s.hasNext()) {
					sb.append('\n');
					sb.append(s.next());
				}
				String transform = sb.toString();
				Expression jslt = Parser.compileString(transform);

				ms = System.currentTimeMillis();
				for (int l = 0; l < ITERATIONS; l++) {

					tpe.submit(() -> {
						try {
							// string to JsonNode required by JSLT
							JsonNode o = mapper.readTree(source);

							JsonNode json = jslt.apply(o);
							String res = json.toString();
							if (cdl.getCount() == 1L)
								logger.info(res);
							assertNotNull(res); // our test case expects result as string
							cdl.countDown();

						} catch (IOException e) {
						}
					});
				}
			}
		}
		cdl.await();
		return ms;
	}

	@Test
	public void testA_FreeMarkerTest() throws IOException, TemplateException, InterruptedException {

		logger.info("FREEMARKER TEST");
		Template template = cfg.getTemplate("freemarker-template.ftl");

		// discard first n executions
		threadPoolFreeMarker(1, template);
		sleep(2000L);

		long ms = threadPoolFreeMarker(1, template);
		printResult("FREEMARKER", ms);
	}

	@Test
	public void testB_FreeMarkerWithThreadPoolTest() throws IOException, InterruptedException {

		logger.info("FREEMARKER (ThreadPool) TEST");
		Template template = cfg.getTemplate("freemarker-template.ftl");

		// discard first n executions
		threadPoolFreeMarker(1, template);
		sleep(2000L);

		long ms = threadPoolFreeMarker(6, template);
		printResult("FREEMARKER(TP)", ms);
	}

	@Test
	public void testC_JSLTTest() throws IOException, InterruptedException {

		logger.info("JSLT TEST");

		// discard first n executions
		threadPoolJSLT(1);

		long ms = threadPoolJSLT(1);
		printResult("JSLT", ms);
	}

	@Test
	public void testD_JSLTWithThreadPoolTest() throws IOException, InterruptedException {

		logger.info("JSLT (ThreadPool) TEST");

		long ms = threadPoolJSLT(6);
		printResult("JSLT(TP)", ms);
	}

	private void printResult(String method, long startTimeMs) {

		long elapsedMs = System.currentTimeMillis() - startTimeMs;
		logger.info("RESULT (" + method + ") " + elapsedMs + " ms.");
		timer.put(method, Long.valueOf(elapsedMs));

		// todos os métodos foram processados
		if (timer.size() == 4) {
			final String vsep = "\n+----------------------+----------+";
			StringBuilder report = new StringBuilder();
			report.append("\nTotal executions: " + ITERATIONS).append("\n\nFINAL RESULTS:").append(vsep)
					.append("\n| Method               | time(ms) |").append(vsep);

			timer.entrySet().forEach(e -> report.append(String.format("%n| %-20s | %8d |", e.getKey(), e.getValue())));
			report.append(vsep).append("\n\n");
			logger.info(report.toString());
		}
	}
}
