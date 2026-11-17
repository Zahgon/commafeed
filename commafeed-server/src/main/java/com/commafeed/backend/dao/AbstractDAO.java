package com.commafeed.backend.dao;

import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * The small part of the replaced framework's data access base class that the DAOs here used: the session bound to the unit of work, loading
 * a row by its identifier and persisting one.
 */
public abstract class AbstractDAO<E> {

	private final SessionFactory sessionFactory;
	private final Class<?> entityClass;

	protected AbstractDAO(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
		this.entityClass = Generics.entityTypeOf(getClass());
	}

	/** The session the unit of work around this call bound to the factory. */
	protected Session currentSession() {
		return sessionFactory.getCurrentSession();
	}

	@SuppressWarnings("unchecked")
	protected E get(Object id) {
		return (E) currentSession().get(entityClass, id);
	}

	/**
	 * Saves a row, or brings one that was read in another session up to date.
	 *
	 * This is the one call the replaced framework's base class made here, and which one it is matters: a row that is handed back after the
	 * session it was read in has closed - which is what the feed refresh does - is brought up to date by it rather than refused.
	 */
	@SuppressWarnings("deprecation")
	protected E persist(E entity) {
		currentSession().saveOrUpdate(entity);
		return entity;
	}

}
