package net.nuke24.scratch38.s0032;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Writes a greeting to a file.
 *
 * Run with: mvn scratch38-s0032:hello
 */
@Mojo(name = "hello", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class HelloWorldMojo extends AbstractMojo
{
	/** File to write the greeting to. */
	@Parameter(property = "scratch38-s0032.outputFile", defaultValue = "${project.build.directory}/hello.txt")
	private Path outputFile;
	
	/** The greeting text to write. */
	@Parameter(property = "scratch38-s0032.greeting", defaultValue = "Hello, world!")
	private String greeting;
	
	@Override
	public void execute() throws MojoExecutionException {
		try {
			Files.createDirectories(outputFile.getParent());
			try( OutputStream os = new FileOutputStream(outputFile.toFile()) ) {
				os.write(greeting.getBytes("UTF-8"));
			}
		} catch (IOException | UncheckedIOException e) {
			throw new MojoExecutionException("Failed to write greeting to " + outputFile, e);
		}
		getLog().info("Wrote greeting to " + outputFile);
	}
}
