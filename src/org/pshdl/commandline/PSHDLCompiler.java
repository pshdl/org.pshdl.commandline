package org.pshdl.commandline;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.prefs.*;

import org.apache.commons.cli.*;
import org.pshdl.model.utils.*;
import org.pshdl.model.utils.services.*;
import org.pshdl.model.utils.services.IOutputProvider.MultiOption;

import com.google.common.collect.*;
import com.google.common.io.*;

public class PSHDLCompiler {
	private final static Map<String, IOutputProvider> implementations = Maps.newHashMap();
	private final static Preferences prefs = Preferences.userNodeForPackage(PSHDLCompiler.class);

	public static void main(String[] args) throws Exception {
		HDLCore.defaultInit();
		final Collection<IOutputProvider> provider = HDLCore.getAllImplementations(IOutputProvider.class);
		for (final IOutputProvider iop : provider) {
			implementations.put(iop.getHookName(), iop);
		}
		new PSHDLCompiler().run(args);
	}

	@SuppressWarnings("rawtypes")
	private void run(String[] args) throws Exception {
		final MultiOption options = getOptions();
		final CommandLine parse = options.parse(args);
		final List argList = parse.getArgList();
		if (parse.hasOption("help") || (args.length == 0)) {
			options.printHelp(System.out);
			return;
		}
		if (parse.hasOption("version")) {
			System.out.println(PSHDLCompiler.class.getSimpleName() + " version: " + HDLCore.VERSION);
			return;
		}
		final long lastCheck = prefs.getLong("LAST_CHECK", -1);
		final long oneWeek = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS);
		final long oneWeekAgo = System.currentTimeMillis() - oneWeek;
		if ((oneWeekAgo > lastCheck) && !parse.hasOption("nocheck")) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						final InputStream stream = new URL("http://api.pshdl.org/api/v0.1/compiler/version?localVersion=" + HDLCore.VERSION).openStream();
						final byte[] byteArray = ByteStreams.toByteArray(stream);
						final String remoteVersion = new String(byteArray).trim();
						if (!remoteVersion.equals(HDLCore.VERSION)) {
							System.err.println("A new version of this compiler is available: " + remoteVersion + " local version: " + HDLCore.VERSION);
						} else {
							prefs.putLong("LAST_CHECK", System.currentTimeMillis());
						}
					} catch (final Exception e) {
					}
				}
			}).start();
		}
		final String arg = argList.get(0).toString();
		final IOutputProvider iop = implementations.get(arg);
		if (iop == null) {
			System.out.println("No such provider: " + arg + " please try one of: " + implementations.keySet().toString());
		} else {
			argList.remove(0);
			final String result = iop.invoke(parse);
			if (result != null) {
				System.out.flush();
				System.err.println(result);
				System.exit(0);
				return;
			}
		}
		System.exit(0);
	}

	private MultiOption getOptions() {
		final Options options = new Options();
		options.addOption(new Option("version", "Print the version of this compiler"));
		options.addOption(new Option("help", "Print the usage options of this compiler"));
		options.addOption(new Option("nocheck", "Don't check for an updated version of the command line"));
		final List<MultiOption> sub = new LinkedList<IOutputProvider.MultiOption>();

		final StringBuilder sb = new StringBuilder();
		for (final Iterator<IOutputProvider> iterator = implementations.values().iterator(); iterator.hasNext();) {
			final IOutputProvider iop = iterator.next();
			sub.add(iop.getUsage());
			sb.append(iop.getHookName());
			if (iterator.hasNext()) {
				sb.append("|");
			}
		}
		return new MultiOption("Compiler [OPTIONS] <" + sb.toString() + "> [GENERATOR_OPTIONS]", null, options, sub.toArray(new MultiOption[sub.size()]));
	}
}
