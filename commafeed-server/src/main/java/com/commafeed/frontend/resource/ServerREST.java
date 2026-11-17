package com.commafeed.frontend.resource;

import org.apache.commons.lang3.StringUtils;

import com.codahale.metrics.annotation.Timed;
import com.commafeed.CommaFeedConfiguration;
import com.commafeed.backend.HttpGetter;
import com.commafeed.backend.HttpGetter.HttpResult;
import com.commafeed.backend.feed.FeedUtils;
import com.commafeed.backend.model.User;
import com.commafeed.frontend.auth.SecurityCheck;
import com.commafeed.frontend.http.Answer;
import com.commafeed.frontend.http.MediaType;
import com.commafeed.frontend.http.Status;
import com.commafeed.frontend.http.annotation.Consumes;
import com.commafeed.frontend.http.annotation.GET;
import com.commafeed.frontend.http.annotation.Path;
import com.commafeed.frontend.http.annotation.Produces;
import com.commafeed.frontend.http.annotation.QueryParam;
import com.commafeed.frontend.model.ServerInfo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Path("/server")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Tag(name = "Server")
public class ServerREST {

	private final HttpGetter httpGetter;
	private final CommaFeedConfiguration config;

	@Path("/get")
	@GET
	@Operation(
			summary = "Get server infos",
			description = "Get server infos",
			responses = { @ApiResponse(content = @Content(schema = @Schema(implementation = ServerInfo.class))) })
	@Timed
	public Answer getServerInfos() {
		ServerInfo infos = new ServerInfo();
		infos.setAnnouncement(config.getApplicationSettings().getAnnouncement());
		infos.setVersion(config.getVersion());
		infos.setGitCommit(config.getGitCommit());
		infos.setAllowRegistrations(config.getApplicationSettings().getAllowRegistrations());
		infos.setGoogleAnalyticsCode(config.getApplicationSettings().getGoogleAnalyticsTrackingCode());
		infos.setSmtpEnabled(StringUtils.isNotBlank(config.getApplicationSettings().getSmtpHost()));
		infos.setDemoAccountEnabled(config.getApplicationSettings().getCreateDemoAccount());
		infos.setWebsocketEnabled(config.getApplicationSettings().getWebsocketEnabled());
		infos.setWebsocketPingInterval(config.getApplicationSettings().getWebsocketPingInterval().toMilliseconds());
		infos.setTreeReloadInterval(config.getApplicationSettings().getTreeReloadInterval().toMilliseconds());
		return Answer.ok(infos).build();
	}

	@Path("/proxy")
	@GET
	@Operation(summary = "proxy image")
	@Produces("image/png")
	@Timed
	public Answer getProxiedImage(@Parameter(hidden = true) @SecurityCheck User user,
			@Parameter(description = "image url", required = true) @QueryParam("u") String url) {
		if (!config.getApplicationSettings().getImageProxyEnabled()) {
			return Answer.status(Status.FORBIDDEN).build();
		}

		url = FeedUtils.imageProxyDecoder(url);
		try {
			HttpResult result = httpGetter.getBinary(url, 20000);
			return Answer.ok(result.getContent()).build();
		} catch (Exception e) {
			return Answer.status(Status.SERVICE_UNAVAILABLE).entity(e.getMessage()).build();
		}
	}
}
