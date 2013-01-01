package org.jasig.portal.security;

import javax.portlet.PortletRequest;
import javax.portlet.PortletSession;
import javax.servlet.http.HttpSession;

import org.jasig.portal.EntityIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("identitySwapperManager")
public class IdentitySwapperManagerImpl implements IdentitySwapperManager {
    public static final String IMPERSONATE_OWNER = "UP_USERS";
    public static final String IMPERSONATE_ACTIVITY = "IMPERSONATE";
    
    private static final String SWAP_TARGET_UID = IdentitySwapperManagerImpl.class.getName() + ".SWAP_TARGET_UID";
    private static final String SWAP_ORIGINAL_UID = IdentitySwapperManagerImpl.class.getName() + ".SWAP_ORIGINAL_UID";
    

    private IAuthorizationService authorizationService;
    
    @Autowired
    public void setAuthorizationService(IAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public boolean canImpersonateUser(IPerson currentUser, String targetUsername) {
        final EntityIdentifier ei = currentUser.getEntityIdentifier();
        final IAuthorizationPrincipal ap = authorizationService.newPrincipal(ei.getKey(), ei.getType());
        
        return canImpersonateUser(ap, targetUsername);
    }
    
    @Override
    public boolean canImpersonateUser(String currentUserName, String targetUsername) {
        final IAuthorizationPrincipal ap = authorizationService.newPrincipal(currentUserName, IPerson.class);
        return canImpersonateUser(ap, targetUsername);
    }

    protected boolean canImpersonateUser(final IAuthorizationPrincipal ap, String targetUsername) {
        return ap.hasPermission(IMPERSONATE_OWNER, IMPERSONATE_ACTIVITY, targetUsername);
    }

    @Override
    public void impersonateUser(PortletRequest portletRequest, IPerson currentUser, String targetUsername) {
        this.impersonateUser(portletRequest, currentUser.getName(), targetUsername);
    }

    @Override
    public void impersonateUser(PortletRequest portletRequest, String currentUserName, String targetUsername) {
        if (!canImpersonateUser(currentUserName, targetUsername)) {
            throw new RuntimeAuthorizationException(currentUserName, IMPERSONATE_OWNER, targetUsername);
        }
        
        final PortletSession portletSession = portletRequest.getPortletSession();
        portletSession.setAttribute(SWAP_TARGET_UID, targetUsername, PortletSession.APPLICATION_SCOPE);        
    }

    @Override
    public void setOriginalUser(HttpSession session, String currentUserName, String targetUsername) {
        if (!canImpersonateUser(currentUserName, targetUsername)) {
            throw new RuntimeAuthorizationException(currentUserName, IMPERSONATE_OWNER, targetUsername);
        }
        
        session.setAttribute(SWAP_ORIGINAL_UID, currentUserName);
    }
    
    public String getOriginalUsername(HttpSession session) {
        return (String) session.getAttribute(SWAP_ORIGINAL_UID);
    }
    
    public String getTargetUsername(HttpSession session) {
        return (String) session.getAttribute(SWAP_TARGET_UID);
    }

}
