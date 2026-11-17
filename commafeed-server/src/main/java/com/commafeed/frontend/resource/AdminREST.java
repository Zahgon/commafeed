package com.commafeed.frontend.resource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.annotation.Timed;
import com.commafeed.CommaFeedApplication;
import com.commafeed.CommaFeedConfiguration;
import com.commafeed.CommaFeedConfiguration.ApplicationSettings;
import com.commafeed.backend.dao.UserDAO;
import com.commafeed.backend.dao.UserRoleDAO;
import com.commafeed.backend.model.User;
import com.commafeed.backend.model.UserRole;
import com.commafeed.backend.model.UserRole.Role;
import com.commafeed.backend.service.PasswordEncryptionService;
import com.commafeed.backend.service.UserService;
import com.commafeed.frontend.auth.SecurityCheck;
import com.commafeed.frontend.http.Answer;
import com.commafeed.frontend.http.MediaType;
import com.commafeed.frontend.http.Status;
import com.commafeed.frontend.http.annotation.Consumes;
import com.commafeed.frontend.http.annotation.GET;
import com.commafeed.frontend.http.annotation.POST;
import com.commafeed.frontend.http.annotation.Path;
import com.commafeed.frontend.http.annotation.PathParam;
import com.commafeed.frontend.http.annotation.Produces;
import com.commafeed.frontend.model.UserModel;
import com.commafeed.frontend.model.request.AdminSaveUserRequest;
import com.commafeed.frontend.model.request.IDRequest;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Tag(name = "Admin")
public class AdminREST {

	private final UserDAO userDAO;
	private final UserRoleDAO userRoleDAO;
	private final UserService userService;
	private final PasswordEncryptionService encryptionService;
	private final CommaFeedConfiguration config;
	private final MetricRegistry metrics;

	@Path("/user/save")
	@POST
	@Operation(
			summary = "Save or update a user",
			description = "Save or update a user. If the id is not specified, a new user will be created")
	@Timed
	public Answer adminSaveUser(@Parameter(hidden = true) @SecurityCheck(Role.ADMIN) User user,
			@Parameter(required = true) AdminSaveUserRequest req) {
		Preconditions.checkNotNull(req);
		Preconditions.checkNotNull(req.getName());

		Long id = req.getId();
		if (id == null) {
			Preconditions.checkNotNull(req.getPassword());

			Set<Role> roles = Sets.newHashSet(Role.USER);
			if (req.isAdmin()) {
				roles.add(Role.ADMIN);
			}
			try {
				userService.register(req.getName(), req.getPassword(), req.getEmail(), roles, true);
			} catch (Exception e) {
				return Answer.status(Status.CONFLICT).entity(e.getMessage()).build();
			}
		} else {
			if (req.getId().equals(user.getId()) && !req.isEnabled()) {
				return Answer.status(Status.FORBIDDEN).entity("You cannot disable your own account.").build();
			}

			User u = userDAO.findById(id);
			u.setName(req.getName());
			if (StringUtils.isNotBlank(req.getPassword())) {
				u.setPassword(encryptionService.getEncryptedPassword(req.getPassword(), u.getSalt()));
			}
			u.setEmail(req.getEmail());
			u.setDisabled(!req.isEnabled());
			userDAO.saveOrUpdate(u);

			Set<Role> roles = userRoleDAO.findRoles(u);
			if (req.isAdmin() && !roles.contains(Role.ADMIN)) {
				userRoleDAO.saveOrUpdate(new UserRole(u, Role.ADMIN));
			} else if (!req.isAdmin() && roles.contains(Role.ADMIN)) {
				if (CommaFeedApplication.USERNAME_ADMIN.equals(u.getName())) {
					return Answer.status(Status.FORBIDDEN).entity("You cannot remove the admin role from the admin user.").build();
				}
				for (UserRole userRole : userRoleDAO.findAll(u)) {
					if (userRole.getRole() == Role.ADMIN) {
						userRoleDAO.delete(userRole);
					}
				}
			}

		}
		return Answer.ok().build();

	}

	@Path("/user/get/{id}")
	@GET
	@Operation(
			summary = "Get user information",
			description = "Get user information",
			responses = { @ApiResponse(content = @Content(schema = @Schema(implementation = UserModel.class))) })
	@Timed
	public Answer adminGetUser(@Parameter(hidden = true) @SecurityCheck(Role.ADMIN) User user,
			@Parameter(description = "user id", required = true) @PathParam("id") Long id) {
		Preconditions.checkNotNull(id);
		User u = userDAO.findById(id);
		UserModel userModel = new UserModel();
		userModel.setId(u.getId());
		userModel.setName(u.getName());
		userModel.setEmail(u.getEmail());
		userModel.setEnabled(!u.isDisabled());
		userModel.setAdmin(userRoleDAO.findAll(u).stream().anyMatch(r -> r.getRole() == Role.ADMIN));
		return Answer.ok(userModel).build();
	}

	@Path("/user/getAll")
	@GET
	@Operation(
			summary = "Get all users",
			description = "Get all users",
			responses = { @ApiResponse(content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserModel.class)))) })
	@Timed
	public Answer adminGetUsers(@Parameter(hidden = true) @SecurityCheck(Role.ADMIN) User user) {
		Map<Long, UserModel> users = new HashMap<>();
		for (UserRole role : userRoleDAO.findAll()) {
			User u = role.getUser();
			Long key = u.getId();
			UserModel userModel = users.get(key);
			if (userModel == null) {
				userModel = new UserModel();
				userModel.setId(u.getId());
				userModel.setName(u.getName());
				userModel.setEmail(u.getEmail());
				userModel.setEnabled(!u.isDisabled());
				userModel.setCreated(u.getCreated());
				userModel.setLastLogin(u.getLastLogin());
				users.put(key, userModel);
			}
			if (role.getRole() == Role.ADMIN) {
				userModel.setAdmin(true);
			}
		}
		return Answer.ok(users.values()).build();
	}

	@Path("/user/delete")
	@POST
	@Operation(summary = "Delete a user", description = "Delete a user, and all his subscriptions")
	@Timed
	public Answer adminDeleteUser(@Parameter(hidden = true) @SecurityCheck(Role.ADMIN) User user,
			@Parameter(required = true) IDRequest req) {
		Preconditions.checkNotNull(req);
		Preconditions.checkNotNull(req.getId());

		User u = userDAO.findById(req.getId());
		if (u == null) {
			return Answer.status(Status.NOT_FOUND).build();
		}
		if (user.getId().equals(u.getId())) {
			return Answer.status(Status.FORBIDDEN).entity("You cannot delete your own user.").build();
		}
		userService.unregister(u);
		return Answer.ok().build();
	}

	@Path("/settings")
	@GET
	@Operation(
			summary = "Retrieve application settings",
			description = "Retrieve application settings",
			responses = { @ApiResponse(content = @Content(schema = @Schema(implementation = ApplicationSettings.class))) })
	@Timed
	public Answer getApplicationSettings(@Parameter(hidden = true) @SecurityCheck(Role.ADMIN) User user) {
		return Answer.ok(config.getApplicationSettings()).build();
	}

	@Path("/metrics")
	@GET
	@Operation(summary = "Retrieve server metrics")
	@Timed
	public Answer getMetrics(@Parameter(hidden = true) @SecurityCheck(Role.ADMIN) User user) {
		return Answer.ok(metrics).build();
	}

}
