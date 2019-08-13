package net.dsf.transformation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.Test;

import com.bazaarvoice.jolt.Chainr;
import com.bazaarvoice.jolt.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schibsted.spt.data.jslt.Expression;
import com.schibsted.spt.data.jslt.Parser;

import freemarker.log.Logger;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

/**
 * E o campeão foi: freeMarker!!!
 * 
 * <pre>
 * Para 20k iterações:
 * - jsltTest   = 1163 ms
 * - joltTest   =  653 ms
 * - freeMarker =  586 ms
 * </pre>
 * 
 * No cálculo foram desconsideradas a carga dos templates e suas eventuais
 * compilações/preparações.
 * 
 * A diferença apurada entre Jolt e o FreeMarker é mínima (em algumas execuções,
 * o Jolt foi ligeiramente mais rápido). Contudo, a flexibilidade e o suporte ao
 * FreeMarker é bem maior, até por que ele é muito usado em outros cenários
 * (páginas dinâmicas, e-mails, etc).
 * 
 * @author Fabio De Santi
 *
 */
public class TransformatioBenchmarkTest {

	private static final Logger logger = Logger.getLogger(TransformatioBenchmarkTest.class.getName());

	private static Configuration cfg;

	private static final int ITERATIONS = 20000;

	private ObjectMapper mapper = new ObjectMapper();

	private static Map<String, Long> timer = new HashMap<>();

	static {
		cfg = new Configuration(Configuration.VERSION_2_3_27);

		try {
			cfg.setDirectoryForTemplateLoading(new File(ClassLoader.getSystemResource("templates").getFile()));
		} catch (IOException e) {
			logger.error("error setting up freemarker template", e);
		}

		cfg.setDefaultEncoding("UTF-8");
		cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
		cfg.setLogTemplateExceptions(false);
		cfg.setWrapUncheckedExceptions(true);
	}

	@Test
	public void freeMarkerTest() throws IOException, TemplateException {

		logger.info("FREEMARKER TEST");
		Template template = cfg.getTemplate("freemarker-0.ftl");
		JsonNode output = null;
		long ms = 0L;
		for (int i = 0; i < ITERATIONS; i++) {
			if (i == 1) /* discard first execution (classloader-related work must not be considered) */
				ms = System.currentTimeMillis();

			JsonNode input = mapper.readTree(ClassLoader.getSystemResourceAsStream("sample-input-0.json"));
			@SuppressWarnings("unchecked")
			Map<String, Object> map = mapper.convertValue(input, Map.class);

			StringWriter out = new StringWriter();
			template.process(map, out);
			output = mapper.readTree(out.toString());
		}

		printResult("FREEMARKER", output, ms);
	}

	@Test
	public void freeMarkerWithThreadPoolTest() throws IOException, InterruptedException {

		logger.info("FREEMARKER (ThreadPool) TEST");
		Template template = cfg.getTemplate("freemarker-0.ftl");
		JsonNode output = null;
		long ms = 0L;
		CountDownLatch cdl = new CountDownLatch(ITERATIONS);
		ThreadPoolExecutor tpe = (ThreadPoolExecutor) Executors.newFixedThreadPool(6);
		for (int i = 0; i < ITERATIONS; i++) {
			if (i == 1) /* discard first execution (classloader-related work must not be considered) */
				ms = System.currentTimeMillis();

			tpe.submit(() -> {
				try {
					JsonNode input = mapper.readTree(ClassLoader.getSystemResourceAsStream("sample-input-0.json"));
					@SuppressWarnings("unchecked")
					Map<String, Object> map = mapper.convertValue(input, Map.class);

					StringWriter out = new StringWriter();
					template.process(map, out);
					mapper.readTree(out.toString());
					cdl.countDown();

				} catch (IOException | TemplateException e) {
					logger.error("error processing document", e);
				}
			});
		}
		while (cdl.getCount() > 0L) {
			Thread.sleep(10L);
		}
		logger.info(String.format("Final count: %d", cdl.getCount()));

		printResult("FREEMARKER(TP)", output, ms);
	}

	@Test
	public void joltTest() {

		logger.info("JOLT TEST");
		List<Object> specs = JsonUtils.classpathToList("/sample-spec-0.json");
		Chainr chainr = Chainr.fromSpec(specs);

		Object transformedOutput = null;
		long ms = 0L;
		for (int i = 0; i < ITERATIONS; i++) {
			if (i == 1) /* discard first execution (classloader-related work must not be considered) */
				ms = System.currentTimeMillis();

			Object inputJSON = JsonUtils.classpathToObject("/sample-input-0.json");
			transformedOutput = chainr.transform(inputJSON);
		}
		printResult("JOLT", transformedOutput, ms);
	}

	@Test
	public void jsltTest() throws IOException {

		logger.info("JSLT TEST");
		JsonNode output = null;
		long ms = 0L;
		try (InputStream is = ClassLoader.getSystemResourceAsStream("sample-jslt-0.json")) {
			try (Scanner s = new Scanner(is)) {
				s.useDelimiter("\n");
				StringBuilder sb = new StringBuilder();
				while (s.hasNext()) {
					sb.append('\n');
					sb.append(s.next());
				}
				String transform = sb.toString();

				for (int i = 0; i < ITERATIONS; i++) {
					if (i == 1) /* discard first execution (classloader-related work must not be considered) */
						ms = System.currentTimeMillis();

					JsonNode input = mapper.readTree(ClassLoader.getSystemResourceAsStream("sample-input-0.json"));

					Expression jslt = Parser.compileString(transform);
					output = jslt.apply(input);
				}
			}
		}
		printResult("JSLT", output, ms);
	}

	private void printResult(String method, Object json, long startTimeMs) {

		long elapsedMs = System.currentTimeMillis() - startTimeMs;
		String result = JsonUtils.toPrettyJsonString(json);
		logger.info("RESULT (" + method + ") " + elapsedMs + " ms: " + result);
		timer.put(method, Long.valueOf(elapsedMs));

		// todos os métodos foram processados
		if (timer.size() == 4) {
			final String vsep = "\n+----------------------+--------------+";
			StringBuilder report = new StringBuilder();
			report.append("\nFINAL RESULTS (for ").append(ITERATIONS).append(" iterations):").append(vsep)
					.append("\n| Method               |    time (ms) |").append(vsep);
			timer.entrySet().forEach(e -> report.append(String.format("%n| %-20s | %12d |", e.getKey(), e.getValue())));
			report.append(vsep).append("\n\n");
			logger.info(report.toString());
		}
	}
}
