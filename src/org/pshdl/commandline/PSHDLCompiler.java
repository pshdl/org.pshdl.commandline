package org.pshdl.commandline;

import java.util.*;

import org.apache.commons.cli.*;
import org.pshdl.model.utils.*;
import org.pshdl.model.utils.services.*;
import org.pshdl.model.utils.services.IOutputProvider.MultiOption;

import com.google.common.collect.*;

public class PSHDLCompiler {
	final static Map<String, IOutputProvider> implementations = Maps.newHashMap();

	public static void main(String[] args) throws Exception {
		HDLCore.defaultInit();
		final Collection<IOutputProvider> provider = HDLCore.getAllImplementations(IOutputProvider.class);
		for (final IOutputProvider iop : provider) {
			implementations.put(iop.getHookName(), iop);
		}
		new PSHDLCompiler().run(args);
	}

	@SuppressWarnings("unchecked")
	private void run(String[] args) throws Exception {
		final MultiOption options = getOptions();
		final CommandLine parse = options.parse(args);
		final List argList = parse.getArgList();
		if (argList.size() == 0) {
			options.printHelp(System.out);
			return;
		}
		if (parse.hasOption("help")) {
			options.printHelp(System.out);
			return;
		}
		if (parse.hasOption("version")) {
			System.out.println(PSHDLCompiler.class.getSimpleName() + " version: " + HDLCore.VERSION);
			return;
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
				return;
			}
		}
	}

	private MultiOption getOptions() {
		final Options options = new Options();
		options.addOption(new Option("version", "Print the version of this compiler"));
		options.addOption(new Option("help", "Print the usage options of this compiler"));
		// options.addOption(new Option("nocheck",
		// "Don't check for an updated version of the command line"));
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
