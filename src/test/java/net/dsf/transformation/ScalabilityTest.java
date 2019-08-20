package net.dsf.transformation;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import freemarker.log.Logger;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

public class ScalabilityTest {

	private static final Logger logger = Logger.getLogger(ScalabilityTest.class.getName());

	private static Configuration cfg;

	private TransformatioBenchmarkTest benchmark = new TransformatioBenchmarkTest();

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
	public void scaleFreeMarkerTransformationTest() throws IOException, InterruptedException, TemplateException {

		Template template = cfg.getTemplate("freemarker-template.ftl");

		// discard classloader & JIT influences
		benchmark.threadPoolFreeMarker(1, template);
		benchmark.sleep(2000L);
		for (int cpu = 1; cpu <= Runtime.getRuntime().availableProcessors(); cpu++) {
			long start = benchmark.threadPoolFreeMarker(cpu, template);
			logger.info(String.format("[FreeMarker] %d CPUs -> %d transformations: %d ms", cpu,
					TransformatioBenchmarkTest.ITERATIONS, (System.currentTimeMillis() - start)));
		}
	}

	@Test
	public void scaleJSLTTransformationTest() throws IOException, InterruptedException {

		// discard classloader & JIT influences
		benchmark.threadPoolJSLT(1);
		benchmark.sleep(2000L);
		for (int cpu = 1; cpu <= Runtime.getRuntime().availableProcessors(); cpu++) {
			long start = benchmark.threadPoolJSLT(cpu);
			logger.info(String.format("[JSLT] %d CPUs -> %d transformations: %d ms", cpu,
					TransformatioBenchmarkTest.ITERATIONS, (System.currentTimeMillis() - start)));
		}
	}
}
