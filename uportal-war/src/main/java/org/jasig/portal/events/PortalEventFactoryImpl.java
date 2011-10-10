/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.portal.events;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.xml.namespace.QName;

import org.apache.commons.codec.binary.Base64;
import org.jasig.portal.IPortalInfoProvider;
import org.jasig.portal.events.PortalEvent.PortalEventBuilder;
import org.jasig.portal.groups.IGroupMember;
import org.jasig.portal.logging.ConditionalExceptionLogger;
import org.jasig.portal.logging.ConditionalExceptionLoggerImpl;
import org.jasig.portal.security.IPerson;
import org.jasig.portal.security.IPersonManager;
import org.jasig.portal.security.SystemPerson;
import org.jasig.portal.services.GroupService;
import org.jasig.portal.url.IPortalRequestUtils;
import org.jasig.portal.utils.IncludeExcludeUtils;
import org.jasig.portal.utils.SerializableObject;
import org.jasig.services.persondir.IPersonAttributeDao;
import org.jasig.services.persondir.IPersonAttributes;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.web.util.WebUtils;

/**
 * @author Eric Dalquist
 * @version $Revision$
 */
/**
 * @author Eric Dalquist
 * @version $Revision$
 */
public class PortalEventFactoryImpl implements IPortalEventFactory, ApplicationEventPublisherAware {
    private static final String EVENT_SESSION_MUTEX = PortalEventFactoryImpl.class.getName() + ".EVENT_SESSION_MUTEX";
    private static final String EVENT_SESSION_ID_ATTR = PortalEventFactoryImpl.class.getName() + ".EVENT_SESSION_ID_ATTR";
    
    protected final ConditionalExceptionLogger logger = new ConditionalExceptionLoggerImpl(LoggerFactory.getLogger(getClass()));
    
    private final SecureRandom sessionIdTokenGenerator = new SecureRandom();
    
    private Set<String> groupIncludes = Collections.emptySet();
    private Set<String> groupExcludes = Collections.emptySet();
    private Set<String> attributeIncludes = Collections.emptySet();
    private Set<String> attributeExcludes = Collections.emptySet();
    private IPersonAttributeDao personAttributeDao;
    private IPortalInfoProvider portalInfoProvider;
    private IPortalRequestUtils portalRequestUtils;
    private IPersonManager personManager;
    private ApplicationEventPublisher applicationEventPublisher;
    

    public void setGroupIncludes(Set<String> groupIncludes) {
        this.groupIncludes = groupIncludes;
    }

    public void setGroupExcludes(Set<String> groupExcludes) {
        this.groupExcludes = groupExcludes;
    }

    public void setAttributeIncludes(Set<String> attributeIncludes) {
        this.attributeIncludes = attributeIncludes;
    }

    public void setAttributeExcludes(Set<String> attributeExcludes) {
        this.attributeExcludes = attributeExcludes;
    }

    public void setPortalInfoProvider(IPortalInfoProvider portalInfoProvider) {
        this.portalInfoProvider = portalInfoProvider;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Autowired
    public void setPersonAttributeDao(@Qualifier("personAttributeDao") IPersonAttributeDao personAttributeDao) {
        this.personAttributeDao = personAttributeDao;
    }

    @Autowired
    public void setPortalRequestUtils(IPortalRequestUtils portalRequestUtils) {
        this.portalRequestUtils = portalRequestUtils;
    }

    @Autowired
    public void setPersonManager(IPersonManager personManager) {
        this.personManager = personManager;
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishLoginEvent(javax.servlet.http.HttpServletRequest, java.lang.Object, org.jasig.portal.security.IPerson)
     */
    @Override
    public void publishLoginEvent(HttpServletRequest request, Object source, IPerson person) {
        final PortalEventBuilder portalEventBuilder = this.createPortalEventBuilder(source, person, request);
        
        final Set<String> groups = this.getGroupsForUser(person);
        final Map<String, List<String>> attributes = this.getAttributesForUser(person);
        
        final LoginEvent loginEvent = new LoginEvent(portalEventBuilder, groups, attributes);
        this.applicationEventPublisher.publishEvent(loginEvent);
    }
    
    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishLogoutEvent(javax.servlet.http.HttpServletRequest, java.lang.Object, org.jasig.portal.security.IPerson)
     */
    @Override
    public void publishLogoutEvent(HttpServletRequest request, Object source, IPerson person) {
        final PortalEventBuilder portalEventBuilder = this.createPortalEventBuilder(source, person, request);
        
        final LogoutEvent logoutEvent = new LogoutEvent(portalEventBuilder);
        this.applicationEventPublisher.publishEvent(logoutEvent);
    }
    
    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishPortletAddedToLayoutPortalEvent(javax.servlet.http.HttpServletRequest, java.lang.Object, org.jasig.portal.security.IPerson, long, java.lang.String, java.lang.String)
     */
    @Override
    public void publishPortletAddedToLayoutPortalEvent(HttpServletRequest request, Object source, IPerson layoutOwner,
            long layoutId, String parentFolderId, String fname) {
        final PortalEventBuilder portalEventBuilder = this.createPortalEventBuilder(source, null);
        
        final PortletAddedToLayoutPortalEvent portletAddedToLayoutPortalEvent = new PortletAddedToLayoutPortalEvent(portalEventBuilder, layoutOwner, layoutId, parentFolderId, fname);
        this.applicationEventPublisher.publishEvent(portletAddedToLayoutPortalEvent);
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishPortletAddedToLayoutPortalEvent(java.lang.Object, org.jasig.portal.security.IPerson, long, java.lang.String, java.lang.String)
     */
    @Override
    public void publishPortletAddedToLayoutPortalEvent(Object source, IPerson person, long layoutId,
            String parentFolderId, String fname) {
        this.publishPortletAddedToLayoutPortalEvent(null, source, person, layoutId, parentFolderId, fname);
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishPortletMovedInLayoutPortalEvent(javax.servlet.http.HttpServletRequest, java.lang.Object, org.jasig.portal.security.IPerson, long, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void publishPortletMovedInLayoutPortalEvent(HttpServletRequest request, Object source, IPerson layoutOwner,
            long layoutId, String oldParentFolderId, String newParentFolderId, String fname) {
        final PortalEventBuilder portalEventBuilder = this.createPortalEventBuilder(source, null);
        
        final PortletMovedInLayoutPortalEvent portletMovedInLayoutPortalEvent = new PortletMovedInLayoutPortalEvent(portalEventBuilder, layoutOwner, layoutId, oldParentFolderId, newParentFolderId, fname);
        this.applicationEventPublisher.publishEvent(portletMovedInLayoutPortalEvent);
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishPortletMovedInLayoutPortalEvent(java.lang.Object, org.jasig.portal.security.IPerson, long, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void publishPortletMovedInLayoutPortalEvent(Object source, IPerson layoutOwner, long layoutId,
            String oldParentFolderId, String newParentFolderId, String fname) {
        this.publishPortletMovedInLayoutPortalEvent(null, source, layoutOwner, layoutId, oldParentFolderId, newParentFolderId, fname);
    }
    
    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishPortletDeletedFromLayoutPortalEvent(javax.servlet.http.HttpServletRequest, java.lang.Object, org.jasig.portal.security.IPerson, long, java.lang.String, java.lang.String)
     */
    @Override
    public void publishPortletDeletedFromLayoutPortalEvent(HttpServletRequest request, Object source, IPerson layoutOwner,
            long layoutId, String oldParentFolderId, String fname) {
        final PortalEventBuilder portalEventBuilder = this.createPortalEventBuilder(source, null);
        
        final PortletDeletedFromLayoutPortalEvent portletDeletedFromLayoutPortalEvent = new PortletDeletedFromLayoutPortalEvent(portalEventBuilder, layoutOwner, layoutId, oldParentFolderId, fname);
        this.applicationEventPublisher.publishEvent(portletDeletedFromLayoutPortalEvent);
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishPortletDeletedFromLayoutPortalEvent(java.lang.Object, org.jasig.portal.security.IPerson, long, java.lang.String, java.lang.String)
     */
    @Override
    public void publishPortletDeletedFromLayoutPortalEvent(Object source, IPerson layoutOwner, long layoutId,
            String oldParentFolderId, String fname) {
        this.publishPortletDeletedFromLayoutPortalEvent(null, source, layoutOwner, layoutId, oldParentFolderId, fname);
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishFolderAddedToLayoutPortalEvent(javax.servlet.http.HttpServletRequest, java.lang.Object, org.jasig.portal.security.IPerson, long, java.lang.String)
     */
    @Override
    public void publishFolderAddedToLayoutPortalEvent(HttpServletRequest request, Object source, IPerson layoutOwner,
            long layoutId, String newFolderId) {
        final PortalEventBuilder portalEventBuilder = this.createPortalEventBuilder(source, null);
        
        final FolderAddedToLayoutPortalEvent folderAddedToLayoutPortalEvent = new FolderAddedToLayoutPortalEvent(portalEventBuilder, layoutOwner, layoutId, newFolderId);
        this.applicationEventPublisher.publishEvent(folderAddedToLayoutPortalEvent);
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishFolderAddedToLayoutPortalEvent(java.lang.Object, org.jasig.portal.security.IPerson, long, java.lang.String)
     */
    @Override
    public void publishFolderAddedToLayoutPortalEvent(Object source, IPerson layoutOwner, long layoutId, String newFolderId) {
        this.publishFolderAddedToLayoutPortalEvent(null, source, layoutOwner, layoutId, newFolderId);
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishFolderMovedInLayoutPortalEvent(javax.servlet.http.HttpServletRequest, java.lang.Object, org.jasig.portal.security.IPerson, long, java.lang.String, java.lang.String)
     */
    @Override
    public void publishFolderMovedInLayoutPortalEvent(HttpServletRequest request, Object source, IPerson layoutOwner,
            long layoutId, String oldParentFolderId, String movedFolderId) {
        final PortalEventBuilder portalEventBuilder = this.createPortalEventBuilder(source, null);
        
        final FolderMovedInLayoutPortalEvent folderMovedInLayoutPortalEvent = new FolderMovedInLayoutPortalEvent(portalEventBuilder, layoutOwner, layoutId, oldParentFolderId, movedFolderId);
        this.applicationEventPublisher.publishEvent(folderMovedInLayoutPortalEvent);
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishFolderMovedInLayoutPortalEvent(java.lang.Object, org.jasig.portal.security.IPerson, long, java.lang.String, java.lang.String)
     */
    @Override
    public void publishFolderMovedInLayoutPortalEvent(Object source, IPerson person, long layoutId,
            String oldParentFolderId, String movedFolderId) {
        this.publishFolderMovedInLayoutPortalEvent(null, source, person, layoutId, oldParentFolderId, movedFolderId);
    }
    
    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishFolderDeletedFromLayoutPortalEvent(javax.servlet.http.HttpServletRequest, java.lang.Object, org.jasig.portal.security.IPerson, long, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void publishFolderDeletedFromLayoutPortalEvent(HttpServletRequest request, Object source, IPerson layoutOwner,
            long layoutId, String oldParentFolderId, String deletedFolderId, String deletedFolderName) {
        final PortalEventBuilder portalEventBuilder = this.createPortalEventBuilder(source, null);
        
        final FolderDeletedFromLayoutPortalEvent folderDeletedFromLayoutPortalEvent = new FolderDeletedFromLayoutPortalEvent(portalEventBuilder, layoutOwner, layoutId, oldParentFolderId, deletedFolderId, deletedFolderName);
        this.applicationEventPublisher.publishEvent(folderDeletedFromLayoutPortalEvent);
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishFolderDeletedFromLayoutPortalEvent(java.lang.Object, org.jasig.portal.security.IPerson, long, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void publishFolderDeletedFromLayoutPortalEvent(Object source, IPerson person, long layoutId,
            String oldParentFolderId, String deletedFolderId, String deletedFolderName) {
        this.publishFolderDeletedFromLayoutPortalEvent(null, source, person, layoutId, oldParentFolderId, deletedFolderId, deletedFolderName);
    }
    

    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishPortletActionExecutionEvent(javax.servlet.http.HttpServletRequest, java.lang.Object, long, java.lang.String)
     */
    @Override
    public void publishPortletActionExecutionEvent(HttpServletRequest request, Object source, long executionTime,
            String actionName) {
        
        final PortalEventBuilder eventBuilder = this.createPortalEventBuilder(source, request);
        final PortletActionExecutionEvent portletActionExecutionEvent = new PortletActionExecutionEvent(eventBuilder, executionTime, actionName);
        this.applicationEventPublisher.publishEvent(portletActionExecutionEvent);
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishPortletEventExecutionEvent(javax.servlet.http.HttpServletRequest, java.lang.Object, long, javax.xml.namespace.QName)
     */
    @Override
    public void publishPortletEventExecutionEvent(HttpServletRequest request, Object source, long executionTime,
            QName eventName) {

        final PortalEventBuilder eventBuilder = this.createPortalEventBuilder(source, request);
        final PortletEventExecutionEvent portletEventExecutionEvent = new PortletEventExecutionEvent(eventBuilder, executionTime, eventName);
        this.applicationEventPublisher.publishEvent(portletEventExecutionEvent);
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishPortletRenderExecutionEvent(javax.servlet.http.HttpServletRequest, java.lang.Object, long, boolean, boolean)
     */
    @Override
    public void publishPortletRenderExecutionEvent(HttpServletRequest request, Object source, long executionTime,
            boolean targeted, boolean cached) {
        
        final PortalEventBuilder eventBuilder = this.createPortalEventBuilder(source, request);
        final PortletRenderExecutionEvent portletRenderExecutionEvent = new PortletRenderExecutionEvent(eventBuilder, executionTime, targeted, cached);
        this.applicationEventPublisher.publishEvent(portletRenderExecutionEvent);
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.events.IPortalEventFactory#publishPortletResourceExecutionEvent(javax.servlet.http.HttpServletRequest, java.lang.Object, long, java.lang.String, boolean)
     */
    @Override
    public void publishPortletResourceExecutionEvent(HttpServletRequest request, Object source, long executionTime,
            String resourceId, boolean cached) {

        final PortalEventBuilder eventBuilder = this.createPortalEventBuilder(source, request);
        final PortletResourceExecutionEvent portletResourceExecutionEvent = new PortletResourceExecutionEvent(eventBuilder, executionTime, resourceId, cached);
        this.applicationEventPublisher.publishEvent(portletResourceExecutionEvent);
    }
    
    protected PortalEventBuilder createPortalEventBuilder(Object source, HttpServletRequest request) {
        final IPerson person = this.getPerson(request);
        return this.createPortalEventBuilder(source, person, request);
    }

    protected PortalEventBuilder createPortalEventBuilder(Object source, IPerson person, HttpServletRequest request) {
        final String serverName = this.portalInfoProvider.getServerName();
        final String eventSessionId = this.getPortalEventSessionId(request, person);
        return new PortalEventBuilder(source, serverName, eventSessionId, person);
    }
    
    protected IPerson getPerson(HttpServletRequest request) {
        if (request == null) {
            return SystemPerson.INSTANCE;
        }

        return this.personManager.getPerson(request);
    }

    protected String getPortalEventSessionId(HttpServletRequest request, IPerson person) {
        if (request == null) {
            try {
                request = this.portalRequestUtils.getCurrentPortalRequest();
            }
            catch (IllegalStateException e) {
                synchronized (person) {
                    String sessionId = (String)person.getAttribute(EVENT_SESSION_ID_ATTR);
                    if (sessionId == null) {
                        sessionId = createSessionId(person);
                        person.setAttribute(EVENT_SESSION_ID_ATTR, sessionId);
                    }
                    
                    return sessionId;
                }
            }
        }
        
        final HttpSession session = request.getSession();
        
        //Need to sync on session scoped object to ensure only one id exists per HttpSession
        final Object eventSessionMutex = this.getEventSessionMutex(session);
        synchronized (eventSessionMutex) {
            String eventSessionId = (String)session.getAttribute(EVENT_SESSION_ID_ATTR);
            if (eventSessionId != null) {
                return eventSessionId;
            }

            eventSessionId = createSessionId(person);
            session.setAttribute(EVENT_SESSION_ID_ATTR, eventSessionId);
            
            this.logger.info("Generated PortalEvent SessionId: {}", eventSessionId);
            
            return eventSessionId;
        }
    }

    /**
     * Creates an event session id for the person 
     */
    protected String createSessionId(IPerson person) {
        final byte[] tokenData = new byte[8];
        this.sessionIdTokenGenerator.nextBytes(tokenData);
        return System.currentTimeMillis() + "_" + person.getUserName() + "_" + Base64.encodeBase64URLSafeString(tokenData);
    }
    
    protected Set<String> getGroupsForUser(IPerson person) {
        final IGroupMember member = GroupService.getGroupMember(person.getEntityIdentifier());

        final Set<String> groupKeys = new LinkedHashSet<String>();
        for (@SuppressWarnings("unchecked") final Iterator<IGroupMember> groupItr = member.getAllContainingGroups(); groupItr.hasNext();) {
            final IGroupMember group = groupItr.next();
            final String groupKey = group.getKey();
            
            if (IncludeExcludeUtils.included(groupKey, this.groupIncludes, this.groupExcludes)) {
                groupKeys.add(groupKey);
            }
        }

        return groupKeys;
    }
    
    protected Map<String, List<String>> getAttributesForUser(IPerson person) {
        final IPersonAttributes personAttributes = this.personAttributeDao.getPerson(person.getUserName());

        final Map<String, List<String>> attributes = new LinkedHashMap<String, List<String>>();
        
        for (final Map.Entry<String, List<Object>> attributeEntry : personAttributes.getAttributes().entrySet()) {
            final String attributeName = attributeEntry.getKey();
            final List<Object> values = attributeEntry.getValue();
            
            if (IncludeExcludeUtils.included(attributeName, this.attributeIncludes, this.attributeExcludes)) {
                final List<String> stringValues = new ArrayList<String>(values == null ? 0 : values.size());
                
                for (final Object value : values) {
                    if (value instanceof CharSequence || value instanceof Number ||
                            value instanceof Date || value instanceof Calendar) {
                        stringValues.add(value.toString());
                    }
                }
                
                attributes.put(attributeName, stringValues);
            }
        }

        return attributes;
    }
    
    /**
     * Get a session scoped mutex specific to this class
     */
    protected final Object getEventSessionMutex(HttpSession session) {
        synchronized (WebUtils.getSessionMutex(session)) {
            SerializableObject mutex = (SerializableObject)session.getAttribute(EVENT_SESSION_MUTEX);
            if (mutex == null) {
                mutex = new SerializableObject();
                session.setAttribute(EVENT_SESSION_MUTEX, mutex);
            }
            
            return mutex;
        } 
    }
}
