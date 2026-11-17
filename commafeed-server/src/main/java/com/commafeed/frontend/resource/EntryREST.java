package com.commafeed.frontend.resource;

import java.util.List;

import com.codahale.metrics.annotation.Timed;
import com.commafeed.backend.dao.FeedEntryTagDAO;
import com.commafeed.backend.model.User;
import com.commafeed.backend.service.FeedEntryService;
import com.commafeed.backend.service.FeedEntryTagService;
import com.commafeed.frontend.auth.SecurityCheck;
import com.commafeed.frontend.http.Answer;
import com.commafeed.frontend.http.MediaType;
import com.commafeed.frontend.http.annotation.Consumes;
import com.commafeed.frontend.http.annotation.GET;
import com.commafeed.frontend.http.annotation.POST;
import com.commafeed.frontend.http.annotation.Path;
import com.commafeed.frontend.http.annotation.Produces;
import com.commafeed.frontend.model.request.MarkRequest;
import com.commafeed.frontend.model.request.MultipleMarkRequest;
import com.commafeed.frontend.model.request.StarRequest;
import com.commafeed.frontend.model.request.TagRequest;
import com.google.common.base.Preconditions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Path("/entry")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Tag(name = "Feed entries")
public class EntryREST {

	private final FeedEntryTagDAO feedEntryTagDAO;
	private final FeedEntryService feedEntryService;
	private final FeedEntryTagService feedEntryTagService;

	@Path("/mark")
	@POST
	@Operation(summary = "Mark a feed entry", description = "Mark a feed entry as read/unread")
	@Timed
	public Answer markEntry(@Parameter(hidden = true) @SecurityCheck User user,
			@Valid @Parameter(description = "Mark Request", required = true) MarkRequest req) {
		Preconditions.checkNotNull(req);
		Preconditions.checkNotNull(req.getId());

		feedEntryService.markEntry(user, Long.valueOf(req.getId()), req.isRead());
		return Answer.ok().build();
	}

	@Path("/markMultiple")
	@POST
	@Operation(summary = "Mark multiple feed entries", description = "Mark feed entries as read/unread")
	@Timed
	public Answer markEntries(@Parameter(hidden = true) @SecurityCheck User user,
			@Valid @Parameter(description = "Multiple Mark Request", required = true) MultipleMarkRequest req) {
		Preconditions.checkNotNull(req);
		Preconditions.checkNotNull(req.getRequests());

		for (MarkRequest r : req.getRequests()) {
			Preconditions.checkNotNull(r.getId());
			feedEntryService.markEntry(user, Long.valueOf(r.getId()), r.isRead());
		}

		return Answer.ok().build();
	}

	@Path("/star")
	@POST
	@Operation(summary = "Star a feed entry", description = "Mark a feed entry as read/unread")
	@Timed
	public Answer starEntry(@Parameter(hidden = true) @SecurityCheck User user,
			@Valid @Parameter(description = "Star Request", required = true) StarRequest req) {
		Preconditions.checkNotNull(req);
		Preconditions.checkNotNull(req.getId());
		Preconditions.checkNotNull(req.getFeedId());

		feedEntryService.starEntry(user, Long.valueOf(req.getId()), req.getFeedId(), req.isStarred());

		return Answer.ok().build();
	}

	@Path("/tags")
	@GET
	@Operation(summary = "Get list of tags for the user", description = "Get list of tags for the user")
	@Timed
	public Answer getTags(@Parameter(hidden = true) @SecurityCheck User user) {
		List<String> tags = feedEntryTagDAO.findByUser(user);
		return Answer.ok(tags).build();
	}

	@Path("/tag")
	@POST
	@Operation(summary = "Set feed entry tags")
	@Timed
	public Answer tagEntry(@Parameter(hidden = true) @SecurityCheck User user,
			@Valid @Parameter(description = "Tag Request", required = true) TagRequest req) {
		Preconditions.checkNotNull(req);
		Preconditions.checkNotNull(req.getEntryId());

		feedEntryTagService.updateTags(user, req.getEntryId(), req.getTags());

		return Answer.ok().build();
	}

}
