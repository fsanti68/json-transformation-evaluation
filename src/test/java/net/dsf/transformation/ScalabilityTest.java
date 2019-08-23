package net.dsf.transformation;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import freemarker.log.Logger;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

public class ScalabilityTest {

	private static final Logger logger = Logger.getLogger(ScalabilityTest.class.getName());

	private static Configuration cfg;

	private static int CPU_COUNT = Runtime.getRuntime().availableProcessors();

	private static Map<String, Long[]> results = new HashMap<>();

	private TransformatioBenchmarkTest benchmark = new TransformatioBenchmarkTest();

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
		cfg.setWhitespaceStripping(true);
	}

	private void save(String k, int cpu, long ms) {
		Long[] v = results.get(k);
		v[cpu - 1] = ms;
		results.put(k, v);

		if (results.size() == 2 && cpu == CPU_COUNT) {
			StringBuilder report = new StringBuilder();
			StringBuilder sep = new StringBuilder();
			sep.append("\n------------").append(String.join("", Collections.nCopies(CPU_COUNT, "-------+")));
			report.append(sep.toString());
			report.append("\n| Library  |");
			for (int i = 0; i < CPU_COUNT; i++)
				report.append(String.format("  %3d  |", i + 1));
			for (String rs : results.keySet()) {
				report.append(sep.toString());
				report.append(String.format("\n| %-8s |", rs));
				v = results.get(rs);
				for (int i = 0; i < CPU_COUNT; i++)
					report.append(String.format(" %5d |", v[i]));
			}
			report.append(sep.toString());
			logger.info(report.toString());
		}
	}

	@Test
	public void scaleFreeMarkerTransformationTest() throws IOException, InterruptedException, TemplateException {

		Template template = cfg.getTemplate("freemarker-template.ftl");
		results.put("fmkr", new Long[CPU_COUNT]);

		// discard classloader & JIT influences
		benchmark.threadPoolFreeMarker(1, template);
		benchmark.sleep(2000L);
		for (int cpu = 1; cpu <= CPU_COUNT; cpu++) {
			long start = benchmark.threadPoolFreeMarker(cpu, template);
			long time = System.currentTimeMillis() - start;
			logger.info(String.format("[FreeMarker] %d CPUs -> %d transformations: %d ms", cpu,
					TransformatioBenchmarkTest.ITERATIONS, time));
			save("fmkr", cpu, time);
		}
	}

	@Test
	public void scaleJSLTTransformationTest() throws IOException, InterruptedException {

		results.put("jslt", new Long[CPU_COUNT]);

		// discard classloader & JIT influences
		benchmark.threadPoolJSLT(1);
		benchmark.sleep(2000L);
		for (int cpu = 1; cpu <= CPU_COUNT; cpu++) {
			long start = benchmark.threadPoolJSLT(cpu);
			long time = System.currentTimeMillis() - start;
			logger.info(String.format("[JSLT] %d CPUs -> %d transformations: %d ms", cpu,
					TransformatioBenchmarkTest.ITERATIONS, time));
			save("jslt", cpu, time);
		}
	}
}
