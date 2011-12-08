package at.netcrawler.cli.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import at.andiwand.library.cli.CommandLine;
import at.andiwand.library.io.CachedLineReader;
import at.andiwand.library.io.FluidInputStreamReader;
import at.andiwand.library.io.IgnoreLastLineReader;
import at.andiwand.library.io.LineReader;
import at.andiwand.library.io.MatchTerminatedLineReader;
import at.andiwand.library.io.ReaderUtil;
import at.andiwand.library.util.PatternUtil;
import at.andiwand.library.util.StringUtil;


public abstract class CommandLineAgent {
	
	public static final Charset DEFAULT_CHARSET = Charset.forName("US-ASCII");
	public static final String DEFAULT_NEW_LINE = "\n";
	
	private static final String SYNCHRONIZE_COMMENT = "netCrawler-synchronize";
	private static final String COMMENT_SUFFIX_SEPARATOR = " - ";
	
	protected final CommandLine commandLine;
	protected final PushbackReader in;
	protected final Writer out;
	
	private final Pattern promtPattern;
	private final String commentPrefix;
	private final String newLine;
	
	private final Random random = new Random();
	
	private ProcessTerminator lastTerminator;
	
	public CommandLineAgent(CommandLine commandLine, Pattern promtPattern,
			String commentPrefix) {
		this(commandLine, DEFAULT_CHARSET, promtPattern, commentPrefix);
	}
	
	public CommandLineAgent(CommandLine commandLine, Charset charset,
			Pattern promtPattern, String commentPrefix) {
		this(commandLine, charset, promtPattern, commentPrefix,
				DEFAULT_NEW_LINE);
	}
	
	public CommandLineAgent(CommandLine commandLine, Charset charset,
			Pattern promtPattern, String commentPrefix, String newLine) {
		this.commandLine = commandLine;
		
		try {
			PushbackReader in = initReader(commandLine.getInputStream(),
					charset);
			Writer out = initWriter(commandLine.getOutputStream(), charset);
			
			this.in = in;
			this.out = out;
			
			this.promtPattern = promtPattern;
			this.commentPrefix = commentPrefix;
			this.newLine = newLine;
			
			initCommandLine();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private PushbackReader initReader(InputStream in, Charset charset) {
		in = hookInputStream(in);
		
		Reader reader = new FluidInputStreamReader(in, charset);
		return initReader(reader);
	}
	
	private PushbackReader initReader(Reader reader) {
		reader = hookReader(reader);
		return new PushbackReader(reader);
	}
	
	private Writer initWriter(OutputStream out, Charset charset) {
		out = hookOutputStream(out);
		
		Writer writer = new OutputStreamWriter(out, charset);
		return initWriter(writer);
	}
	
	private Writer initWriter(Writer writer) {
		writer = hookWriter(writer);
		return writer;
	}
	
	protected InputStream hookInputStream(InputStream in) {
		return in;
	}
	
	protected OutputStream hookOutputStream(OutputStream out) {
		return out;
	}
	
	protected Reader hookReader(Reader reader) {
		return reader;
	}
	
	protected Writer hookWriter(Writer writer) {
		return writer;
	}
	
	protected void initCommandLine() throws IOException {
		synchronizeStreams();
	}
	
	protected void synchronizeStreams() throws IOException {
		String comment = prepareComment(SYNCHRONIZE_COMMENT);
		
		out.write(newLine + comment + newLine);
		out.flush();
		
		find(promtPattern);
		find(PatternUtil.endsWithPattern(comment));
		flushLine();
		find(promtPattern);
	}
	
	protected final Matcher match(Pattern pattern) throws IOException {
		return ReaderUtil.match(in, pattern);
	}
	
	protected final Matcher find(Pattern pattern) throws IOException {
		return ReaderUtil.find(in, pattern);
	}
	
	protected final String readLine() throws IOException {
		return ReaderUtil.readLine(in);
	}
	
	protected final void flushLine() throws IOException {
		ReaderUtil.flushLine(in);
	}
	
	private String prepareComment(String string) {
		int suffixRandom = random.nextInt();
		String suffix = "0x"
				+ StringUtil.fillFront(Integer.toHexString(suffixRandom), '0',
						8);
		return commentPrefix + string + COMMENT_SUFFIX_SEPARATOR + suffix;
	}
	
	public final CommandLineSocket execute(String command,
			ProcessTerminator terminator) throws IOException {
		if (lastTerminator != null) {
			try {
				lastTerminator.waitFor();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		
		out.write(command + newLine);
		out.flush();
		
		flushLine();
		CommandLineSocket socket = new CommandLineSocket(in, out);
		
		return terminator.hookSocket(socket);
	}
	
	public String execute(String command) throws IOException {
		ProcessTerminator terminator = buildSimpleProcessTerminator();
		CommandLineSocket socket = execute(command, terminator);
		
		return ReaderUtil.read(socket.getReader());
	}
	
	protected ProcessTerminator buildSimpleProcessTerminator() {
		return new ProcessTerminator() {
			protected Reader hookReader(Reader reader) {
				LineReader lineReader = new MatchTerminatedLineReader(reader,
						promtPattern);
				lineReader = new IgnoreLastLineReader(lineReader);
				return new CachedLineReader(lineReader);
			}
		};
	}
}