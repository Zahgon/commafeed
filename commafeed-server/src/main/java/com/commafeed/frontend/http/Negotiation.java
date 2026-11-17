package com.commafeed.frontend.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Decides what an answer is written as from what the client offered, and whether the server can read what the client sent.
 *
 * Five things here are behaviour the replaced runtime had and a client can see:
 *
 * <ul>
 * <li>a quality value only ranks what is acceptable, it never excludes it, so a type offered at {@code q=0} is still written;</li>
 * <li>an offer the parser cannot read at all - a quality value that is not a number between zero and one, or an empty offer among several -
 * is a bad request rather than something unacceptable, and the container then writes its own page about it;</li>
 * <li>an offer that is a bare word with no subtype reads fine but covers nothing, so it is unacceptable rather than bad;</li>
 * <li>the parameters of the offer that was chosen are written into the answer's content type, so a client asking for a charset is answered
 * with that charset on the type;</li>
 * <li>an entity whose declared type no method reads is refused, and a request that declares no type at all declares the one nothing
 * reads.</li>
 * </ul>
 */
public final class Negotiation {

	private static final MediaType WILDCARD = MediaType.of(MediaType.WILDCARD);

	private Negotiation() {
	}

	/** What the client said it accepts, read in the order it wrote it. */
	public static List<Offer> offers(Request request) {
		List<String> values = request.getHeaders("accept");
		if (values.isEmpty() || values.size() == 1 && values.get(0).trim().isEmpty()) {
			return List.of(new Offer(WILDCARD, 1));
		}
		List<Offer> offers = new ArrayList<>();
		List<MediaType> readable = new ArrayList<>();
		boolean bad = false;
		for (String value : values) {
			for (String entry : value.split(",", -1)) {
				String text = entry.trim();
				if (text.isEmpty()) {
					bad = true;
					continue;
				}
				MediaType type = MediaType.offer(text);
				if (type == null) {
					bad = true;
					continue;
				}
				Double weight = weightOf(type);
				if (weight == null) {
					bad = true;
					continue;
				}
				readable.add(type);
				offers.add(new Offer(type, weight));
			}
		}
		if (bad) {
			throw new BadOffer(readable);
		}
		return offers;
	}

	/** The weight an offer carries, or null when what it carries is not one. */
	private static Double weightOf(MediaType type) {
		String quality = type.getParameter("q");
		if (quality == null) {
			return 1d;
		}
		try {
			double weight = Double.parseDouble(quality);
			return weight < 0 || weight > 1 ? null : weight;
		} catch (NumberFormatException notANumber) {
			return null;
		}
	}

	/**
	 * The type the answer is written as, or null when the client accepts none of them.
	 *
	 * The offer that covers it carries its parameters over, which is how a charset a client asked for reaches the answer's content type.
	 */
	public static MediaType choose(List<MediaType> produces, List<Offer> offers) {
		MediaType chosen = null;
		Offer best = null;
		for (MediaType written : produces) {
			for (Offer offer : offers) {
				if (!offer.getType().covers(written)) {
					continue;
				}
				boolean better = best == null || offer.getWeight() > best.getWeight();
				if (!better && offer.getWeight() == best.getWeight()) {
					better = specificity(offer.getType()) > specificity(best.getType());
				}
				if (better) {
					best = offer;
					chosen = written;
				}
			}
			if (chosen != null) {
				break;
			}
		}
		if (chosen == null) {
			return null;
		}
		MediaType answer = chosen;
		for (Map.Entry<String, String> parameter : best.getType().getParameters().entrySet()) {
			if ("q".equals(parameter.getKey())) {
				continue;
			}
			String value = parameter.getValue();
			if ("charset".equals(parameter.getKey())) {
				// a charset is written back in the spelling the replaced runtime wrote it in
				value = value.toLowerCase(Locale.ROOT);
			}
			answer = answer.withParameter(parameter.getKey(), value);
		}
		return answer;
	}

	private static int specificity(MediaType type) {
		if (type.isWildcardType()) {
			return 0;
		}
		return type.isWildcardSubtype() ? 1 : 2;
	}

	/**
	 * Whether a method that reads these types reads the one the request declared.
	 *
	 * The declared type is read as it was written and not as a pattern: a request that says its entity is of any type at all is not a
	 * request whose entity a method reading one type can read, which is why such a request is refused rather than accepted.
	 */
	public static boolean reads(List<MediaType> consumes, MediaType declared) {
		if (consumes.isEmpty()) {
			return true;
		}
		for (MediaType read : consumes) {
			if (read.covers(declared)) {
				return true;
			}
		}
		return false;
	}

	/** Raised when the offers themselves cannot be read. */
	public static final class BadOffer extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final transient List<MediaType> readable;

		BadOffer(List<MediaType> readable) {
			super("the offers cannot be read");
			this.readable = readable;
		}

		/** The offers that did read, which is what the container's own page was written from. */
		public List<MediaType> getReadable() {
			return readable;
		}

	}

	/** One acceptable type and the weight the client gave it. */
	public static final class Offer {

		private final MediaType type;
		private final double weight;

		Offer(MediaType type, double weight) {
			this.type = type;
			this.weight = weight;
		}

		public MediaType getType() {
			return type;
		}

		public double getWeight() {
			return weight;
		}

	}

}
