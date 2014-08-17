/*******************************************************************************
 * PSHDL is a library and (trans-)compiler for PSHDL input. It generates
 *     output suitable for implementation or simulation of it.
 *
 *     Copyright (C) 2013 Karsten Becker (feedback (at) pshdl (dot) org)
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *     This License does not grant permission to use the trade names, trademarks,
 *     service marks, or product names of the Licensor, except as required for
 *     reasonable and customary use in describing the origin of the Work.
 *
 * Contributors:
 *     Karsten Becker - initial API and implementation
 ******************************************************************************/
package org.pshdl.commandline;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.pshdl.model.utils.HDLCore;
import org.pshdl.model.utils.services.IOutputProvider;
import org.pshdl.model.utils.services.IOutputProvider.MultiOption;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;

public class PSHDLCompiler {
	private final static Map<String, IOutputProvider> implementations = Maps.newLinkedHashMap();
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
						final String remoteVersion = new String(byteArray, StandardCharsets.UTF_8).trim();
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
			System.exit(1);
			return;
		}
		argList.remove(0);
		final String result = iop.invoke(parse);
		if (result != null) {
			System.out.flush();
			System.err.println(result);
			System.exit(2);
			return;
		}
		System.exit(0);
	}

	private MultiOption getOptions() {
		final Options options = new Options();
		options.addOption(new Option("version", "Print the version of this compiler"));
		options.addOption(new Option("help", "Print the usage options of this compiler"));
		options.addOption(new Option("nocheck", "Don't check for an updated version of the command line"));
		final List<MultiOption> sub = Lists.newLinkedList();

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
