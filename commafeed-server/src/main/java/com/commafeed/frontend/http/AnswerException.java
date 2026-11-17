package com.commafeed.frontend.http;

/**
 * Thrown by a resource method, or by something it calls, to answer with something other than what it returns.
 *
 * The replaced framework had an exception of this shape and the resource classes threw it; the dispatcher writes the answer it carries just
 * as the framework did.
 */
public class AnswerException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final transient Answer answer;

	public AnswerException(Answer answer) {
		super("answered with " + answer.getStatus());
		this.answer = answer;
	}

	public AnswerException(Throwable cause, Answer answer) {
		super("answered with " + answer.getStatus(), cause);
		this.answer = answer;
	}

	/** Raised with a message and nothing else, which is answered as a bad request. */
	public AnswerException(String message) {
		super(message);
		this.answer = Answer.status(400).build();
	}

	public Answer getAnswer() {
		return answer;
	}

}
