package com.commafeed.frontend.http;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sampled profile of what the process is running, written the way the replaced connector wrote it.
 *
 * The replaced framework registered this surface on its second connector beside the others, and the menu it writes still points at it, so
 * it is answered here as well. What a client reads of it is the media type and the shape: a text section naming every frame the sampling
 * saw, each at an address of its own, and then the counted stacks as words of eight bytes, least significant first - a header of five
 * words, then a count, a number of frames and that many addresses per stack, and three words to end it. The numbers inside are a sampling
 * of threads and are different in every run, which is why nothing compares them.
 */
public final class CpuProfile {

	/** How long the replaced servlet sampled for, and how often, when it was not told. */
	public static final int SECONDS = 10;
	public static final int FREQUENCY = 100;

	/** The five words the replaced servlet opened the counted section with. */
	private static final long[] HEADER = { 0, 3, 0, 1, 0 };

	private CpuProfile() {
	}

	/** Samples the threads in the given state and writes what was seen. */
	public static byte[] sample(int seconds, int frequency, Thread.State wanted) {
		Map<List<String>, Long> counted = new LinkedHashMap<>();
		long period = Math.max(1, 1000L / Math.max(1, frequency));
		long until = System.currentTimeMillis() + Math.max(0, seconds) * 1000L;
		while (System.currentTimeMillis() < until) {
			count(counted, wanted);
			try {
				Thread.sleep(period);
			} catch (InterruptedException stopped) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		return written(counted);
	}

	private static void count(Map<List<String>, Long> counted, Thread.State wanted) {
		for (Map.Entry<Thread, StackTraceElement[]> thread : Thread.getAllStackTraces().entrySet()) {
			if (thread.getKey().getState() != wanted || thread.getValue().length == 0) {
				continue;
			}
			List<String> frames = new ArrayList<>(thread.getValue().length);
			for (StackTraceElement frame : thread.getValue()) {
				frames.add(frame.toString());
			}
			counted.merge(frames, 1L, Long::sum);
		}
	}

	private static byte[] written(Map<List<String>, Long> counted) {
		Map<String, Long> addresses = new LinkedHashMap<>();
		StringBuilder symbols = new StringBuilder("--- symbol\nbinary=unknown\n");
		for (List<String> frames : counted.keySet()) {
			for (String frame : frames) {
				if (!addresses.containsKey(frame)) {
					addresses.put(frame, (long) (addresses.size() + 1));
					symbols.append(String.format("0x%016x %s%n", addresses.get(frame), frame).replace("\r\n", "\n"));
				}
			}
		}
		symbols.append("---\n--- profile\n");
		List<Long> words = new ArrayList<>();
		for (long word : HEADER) {
			words.add(word);
		}
		for (Map.Entry<List<String>, Long> stack : counted.entrySet()) {
			words.add(stack.getValue());
			words.add((long) stack.getKey().size());
			for (String frame : stack.getKey()) {
				words.add(addresses.get(frame));
			}
		}
		words.add(0L);
		words.add(1L);
		words.add(0L);
		ByteBuffer buffer = ByteBuffer.allocate(words.size() * 8).order(ByteOrder.LITTLE_ENDIAN);
		for (long word : words) {
			buffer.putLong(word);
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] text = symbols.toString().getBytes(StandardCharsets.UTF_8);
		out.write(text, 0, text.length);
		byte[] profile = buffer.array();
		out.write(profile, 0, profile.length);
		return out.toByteArray();
	}

}
