package com.commafeed.frontend.servlet;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.commafeed.CommaFeedConfiguration;
import com.commafeed.backend.dao.FeedCategoryDAO;
import com.commafeed.backend.dao.FeedEntryStatusDAO;
import com.commafeed.backend.dao.FeedSubscriptionDAO;
import com.commafeed.backend.dao.UnitOfWork;
import com.commafeed.backend.dao.UserDAO;
import com.commafeed.backend.model.FeedCategory;
import com.commafeed.backend.model.FeedEntryStatus;
import com.commafeed.backend.model.FeedSubscription;
import com.commafeed.backend.model.User;
import com.commafeed.backend.model.UserSettings.ReadingOrder;
import com.commafeed.backend.service.FeedEntryService;
import com.commafeed.backend.service.UserService;
import com.commafeed.frontend.http.Reply;
import com.commafeed.frontend.http.Request;
import com.commafeed.frontend.http.SessionStore;
import com.commafeed.frontend.http.Status;
import com.commafeed.frontend.resource.CategoryREST;
import com.commafeed.frontend.session.SessionHelper;
import com.google.common.collect.Iterables;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

/**
 * Sends the client to the next entry it has not read, marking that entry as read on the way.
 *
 * It reads the user off the session the request's cookie names, and off nothing else: a request carrying a name and password instead is
 * sent back to the application, which is what the replaced servlet did because it only ever looked at the session.
 */
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
public class NextUnreadServlet implements Servlets.Servlet {

	private static final String PARAM_CATEGORYID = "category";
	private static final String PARAM_READINGORDER = "order";

	private final UnitOfWork unitOfWork;
	private final FeedSubscriptionDAO feedSubscriptionDAO;
	private final FeedEntryStatusDAO feedEntryStatusDAO;
	private final FeedCategoryDAO feedCategoryDAO;
	private final UserDAO userDAO;
	private final UserService userService;
	private final FeedEntryService feedEntryService;
	private final CommaFeedConfiguration config;
	private final SessionStore sessions;

	@Override
	public Reply get(Request request) {
		final String categoryId = request.getTarget().getQueryParameter(PARAM_CATEGORYID);
		String orderParam = request.getTarget().getQueryParameter(PARAM_READINGORDER);

		SessionHelper sessionHelper = new SessionHelper(request, sessions);
		Optional<Long> userId = sessionHelper.getLoggedInUserId();
		Optional<User> user = unitOfWork.call(() -> userId.map(userDAO::findById));
		user.ifPresent(value -> unitOfWork.run(() -> userService.performPostLoginActivities(value)));
		if (user.isEmpty()) {
			return redirect(config.getApplicationSettings().getPublicUrl());
		}

		final ReadingOrder order = StringUtils.equals(orderParam, "asc") ? ReadingOrder.asc : ReadingOrder.desc;

		FeedEntryStatus status = unitOfWork.call(() -> {
			FeedEntryStatus s = null;
			if (StringUtils.isBlank(categoryId) || CategoryREST.ALL.equals(categoryId)) {
				List<FeedSubscription> subs = feedSubscriptionDAO.findAll(user.get());
				List<FeedEntryStatus> statuses = feedEntryStatusDAO.findBySubscriptions(user.get(), subs, true, null, null, 0, 1, order,
						true, null, null, null);
				s = Iterables.getFirst(statuses, null);
			} else {
				FeedCategory category = feedCategoryDAO.findById(user.get(), Long.valueOf(categoryId));
				if (category != null) {
					List<FeedCategory> children = feedCategoryDAO.findAllChildrenCategories(user.get(), category);
					List<FeedSubscription> subscriptions = feedSubscriptionDAO.findByCategories(user.get(), children);
					List<FeedEntryStatus> statuses = feedEntryStatusDAO.findBySubscriptions(user.get(), subscriptions, true, null, null, 0,
							1, order, true, null, null, null);
					s = Iterables.getFirst(statuses, null);
				}
			}
			if (s != null) {
				feedEntryService.markEntry(user.get(), s.getEntry().getId(), true);
			}
			return s;
		});

		if (status == null) {
			return redirect(config.getApplicationSettings().getPublicUrl());
		}
		return redirect(status.getEntry().getUrl());
	}

	private static Reply redirect(String url) {
		return Reply.of(Status.MOVED_TEMPORARILY.getCode()).setHeader("Location", url);
	}

}
