package net.dsf.transformation;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import freemarker.log.Logger;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

public class SampleTest {

	private static final Logger logger = Logger.getLogger(SampleTest.class.getName());

	private static Configuration cfg;

	private ObjectMapper mapper = new ObjectMapper();

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
		cfg.setWhitespaceStripping(true);
	}

	@Test
	public void sampleTransformationTest() throws IOException, TemplateException {

		Template template = cfg.getTemplate("freemarker-1.ftl");

		JsonNode input = mapper.readTree(ClassLoader.getSystemResourceAsStream("sample-input-2.json"));
		@SuppressWarnings("unchecked")
		Map<String, Object> map = mapper.convertValue(input, Map.class);

		StringWriter out = new StringWriter();
		template.process(map, out);
		JsonNode output = mapper.readTree(out.toString());
		logger.info(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output));
	}

	@Test
	public void volumeTransformationTest() throws IOException, InterruptedException, TemplateException {

		Template template = cfg.getTemplate("freemarker-1.ftl");

		JsonNode input = mapper.readTree(ClassLoader.getSystemResourceAsStream("sample-input-1.json"));
		@SuppressWarnings("unchecked")
		Map<String, Object> map = mapper.convertValue(input, Map.class);
		// só para evitar poluir o teste com coisas do classloader
		template.process(map, new StringWriter());

		for (int cpu = 1; cpu <= Runtime.getRuntime().availableProcessors(); cpu++) {
			ThreadPoolExecutor tpe = (ThreadPoolExecutor) Executors.newFixedThreadPool(cpu);
			long startTime = System.currentTimeMillis();
			int execs = 1000000;
			CountDownLatch latch = new CountDownLatch(execs);
			for (int i = 0; i < execs; i++) {
				tpe.submit(() -> {
					StringWriter out = new StringWriter();
					try {
						template.process(map, out);

						mapper.readTree(out.toString());
						latch.countDown();

					} catch (TemplateException | IOException e) {
						logger.error("Failed to process event", e);
					}
				});
			}

			latch.await();
			logger.info(String.format("%d CPUs -> %d transformations: %d ms", cpu, execs,
					(System.currentTimeMillis() - startTime)));
		}
	}
}
