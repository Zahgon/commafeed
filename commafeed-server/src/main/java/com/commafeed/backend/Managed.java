package com.commafeed.backend;

/**
 * Something the server starts when it comes up and stops when it goes down.
 *
 * The replaced framework had its own interface of this shape and kept the list of the things that implement it; the bootstrap now keeps
 * that list itself.
 */
public interface Managed {

	void start() throws Exception;

	default void stop() throws Exception {
		// nothing by default: most of these have nothing to unwind
	}

}
