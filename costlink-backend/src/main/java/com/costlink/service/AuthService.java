package com.costlink.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.costlink.config.JwtTokenProvider;
import com.costlink.dto.LoginRequest;
import com.costlink.dto.LoginResponse;
import com.costlink.entity.User;
import com.costlink.exception.BusinessException;
import com.costlink.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.admin.users:admin}")
    private String adminUsers;

    @Value("${app.ldap.enabled:false}")
    private boolean ldapEnabled;

    @Value("${app.ldap.url:ldap://localhost:389}")
    private String ldapUrl;

    @Value("${app.ldap.base:dc=example,dc=com}")
    private String ldapBase;

    @Value("${app.ldap.user-dn-pattern:uid={0},ou=users}")
    private String ldapUserDnPattern;

    @Value("${app.ldap.bind-dn:}")
    private String ldapBindDn;

    @Value("${app.ldap.bind-password:}")
    private String ldapBindPassword;

    @Value("${app.ldap.user-search-filter:(uid={0})}")
    private String ldapUserSearchFilter;

    @Value("${app.ldap.user-search-base:}")
    private String ldapUserSearchBase;

    @Value("${app.ldap.attr-display-name:cn}")
    private String attrDisplayName;

    @Value("${app.ldap.attr-email:mail}")
    private String attrEmail;

    private static class LdapUserInfo {
        String dn;
        String displayName;
        String email;
    }

    public LoginResponse login(LoginRequest request) {
        if (ldapEnabled) {
            LdapUserInfo ldapUserInfo = authenticateWithLdap(request.getUsername(), request.getPassword());
            if (ldapUserInfo == null) {
                throw new BusinessException("用户名或密码错误");
            }

            User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
            );
            if (user == null) {
                user = createLdapUser(request.getUsername(), ldapUserInfo);
            } else {
                updateLdapUserAttributes(user, ldapUserInfo);
            }

            String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
            return new LoginResponse(token, new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getAlipayAccount(),
                user.getDepartment()
            ));
        }

        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );

        if (user == null) {
            user = createUser(request.getUsername(), request.getPassword());
        } else if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());

        return new LoginResponse(token, new LoginResponse.UserInfo(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getRole(),
            user.getAlipayAccount(),
            user.getDepartment()
        ));
    }

    private User createUser(String username, String password) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setDisplayName(username);
        user.setRole(isAdminUser(username) ? "ADMIN" : "USER");
        userMapper.insert(user);
        return user;
    }

    private User createLdapUser(String username, LdapUserInfo ldapUserInfo) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setDisplayName(ldapUserInfo.displayName != null ? ldapUserInfo.displayName : username);
        user.setEmail(ldapUserInfo.email);
        user.setRole(isAdminUser(username) ? "ADMIN" : "USER");
        userMapper.insert(user);
        return user;
    }

    private void updateLdapUserAttributes(User user, LdapUserInfo ldapUserInfo) {
        boolean needsUpdate = false;

        if (ldapUserInfo.displayName != null && !ldapUserInfo.displayName.equals(user.getDisplayName())) {
            user.setDisplayName(ldapUserInfo.displayName);
            needsUpdate = true;
        }

        if (ldapUserInfo.email != null && !ldapUserInfo.email.equals(user.getEmail())) {
            user.setEmail(ldapUserInfo.email);
            needsUpdate = true;
        }

        if (needsUpdate) {
            userMapper.updateById(user);
            log.debug("Updated LDAP user attributes for: {}", user.getUsername());
        }
    }

    private LdapUserInfo authenticateWithLdap(String username, String password) {
        if (username == null || username.isBlank() || password == null) {
            return null;
        }

        if (isServiceAccountMode()) {
            return authenticateWithServiceAccount(username, password);
        } else {
            return authenticateWithSimpleBind(username, password);
        }
    }

    private boolean isServiceAccountMode() {
        return ldapBindDn != null && !ldapBindDn.isBlank();
    }

    private LdapUserInfo authenticateWithServiceAccount(String username, String password) {
        String providerUrl = normalizeLdapUrl(ldapUrl);
        DirContext serviceCtx = null;

        try {
            // Step 1: Bind with service account
            serviceCtx = createLdapContext(providerUrl, ldapBindDn, ldapBindPassword);
            log.debug("Service account bind successful");

            // Step 2: Search for user DN and attributes
            LdapUserInfo userInfo = searchUser(serviceCtx, username);
            if (userInfo == null) {
                log.debug("User not found in LDAP: {}", username);
                return null;
            }
            log.debug("Found user DN: {}", userInfo.dn);

            // Step 3: Verify user password by rebinding
            DirContext userCtx = null;
            try {
                userCtx = createLdapContext(providerUrl, userInfo.dn, password);
                log.debug("User authentication successful: {}", username);
                return userInfo;
            } catch (NamingException e) {
                log.debug("User authentication failed: {}", username);
                return null;
            } finally {
                closeContext(userCtx);
            }
        } catch (NamingException e) {
            log.error("Service account bind failed", e);
            return null;
        } finally {
            closeContext(serviceCtx);
        }
    }

    private LdapUserInfo authenticateWithSimpleBind(String username, String password) {
        String providerUrl = normalizeLdapUrl(ldapUrl);
        String userDn = buildUserDn(username);

        DirContext ctx = null;
        try {
            // Step 1: Authenticate with user credentials
            ctx = createLdapContext(providerUrl, userDn, password);
            log.debug("Simple bind authentication successful: {}", username);

            // Step 2: Fetch user attributes
            LdapUserInfo userInfo = new LdapUserInfo();
            userInfo.dn = userDn;

            try {
                fetchUserAttributes(ctx, userDn, userInfo);
            } catch (NamingException e) {
                log.debug("Failed to fetch user attributes, using defaults: {}", e.getMessage());
            }

            return userInfo;
        } catch (NamingException e) {
            log.debug("Simple bind authentication failed: {}", username);
            return null;
        } finally {
            closeContext(ctx);
        }
    }

    private DirContext createLdapContext(String providerUrl, String principal, String credentials) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, providerUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, principal);
        env.put(Context.SECURITY_CREDENTIALS, credentials);
        env.put("com.sun.jndi.ldap.connect.timeout", "3000");
        env.put("com.sun.jndi.ldap.read.timeout", "3000");
        return new InitialDirContext(env);
    }

    private LdapUserInfo searchUser(DirContext ctx, String username) throws NamingException {
        String searchBase = ldapUserSearchBase != null && !ldapUserSearchBase.isBlank()
            ? ldapUserSearchBase
            : ldapBase;

        String filter = ldapUserSearchFilter.replace("{0}", escapeLdapFilterValue(username));

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(new String[]{attrDisplayName, attrEmail});

        NamingEnumeration<SearchResult> results = ctx.search(searchBase, filter, controls);

        if (results.hasMore()) {
            SearchResult result = results.next();
            LdapUserInfo userInfo = new LdapUserInfo();
            userInfo.dn = result.getNameInNamespace();

            Attributes attrs = result.getAttributes();
            userInfo.displayName = getAttributeValue(attrs, attrDisplayName);
            userInfo.email = getAttributeValue(attrs, attrEmail);

            return userInfo;
        }

        return null;
    }

    private void fetchUserAttributes(DirContext ctx, String userDn, LdapUserInfo userInfo) throws NamingException {
        Attributes attrs = ctx.getAttributes(userDn, new String[]{attrDisplayName, attrEmail});
        userInfo.displayName = getAttributeValue(attrs, attrDisplayName);
        userInfo.email = getAttributeValue(attrs, attrEmail);
    }

    private String getAttributeValue(Attributes attrs, String attrName) throws NamingException {
        Attribute attr = attrs.get(attrName);
        if (attr != null && attr.size() > 0) {
            Object value = attr.get();
            return value != null ? value.toString() : null;
        }
        return null;
    }

    private void closeContext(DirContext ctx) {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (NamingException ignored) {
            }
        }
    }

    private String escapeLdapFilterValue(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() * 2);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\5c"); break;
                case '*': sb.append("\\2a"); break;
                case '(': sb.append("\\28"); break;
                case ')': sb.append("\\29"); break;
                case '\u0000': sb.append("\\00"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private String buildUserDn(String username) {
        String escaped = escapeLdapRdnValue(username);
        String relativeDn = (ldapUserDnPattern == null ? "" : ldapUserDnPattern.trim()).replace("{0}", escaped);
        String base = ldapBase == null ? "" : ldapBase.trim();
        if (base.isBlank()) {
            return relativeDn;
        }
        if (relativeDn.isBlank()) {
            return "uid=" + escaped + "," + base;
        }
        return relativeDn + "," + base;
    }

    private String normalizeLdapUrl(String url) {
        if (url == null || url.isBlank()) {
            return "ldap://localhost:389";
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("ldap://") || trimmed.startsWith("ldaps://")) {
            return trimmed;
        }
        return "ldap://" + trimmed;
    }

    private String escapeLdapRdnValue(String value) {
        String v = value == null ? "" : value;
        StringBuilder sb = new StringBuilder(v.length() * 2);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            boolean isFirst = i == 0;
            boolean isLast = i == v.length() - 1;
            if ((isFirst || isLast) && c == ' ') {
                sb.append('\\').append(' ');
                continue;
            }
            if (isFirst && c == '#') {
                sb.append('\\').append('#');
                continue;
            }
            if (c == ',' || c == '+' || c == '"' || c == '\\' || c == '<' || c == '>' || c == ';' || c == '=') {
                sb.append('\\').append(c);
                continue;
            }
            if (c == '\u0000') {
                sb.append("\\00");
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private boolean isAdminUser(String username) {
        List<String> admins = Arrays.asList(adminUsers.split(","));
        return admins.stream().map(String::trim).anyMatch(admin -> admin.equalsIgnoreCase(username));
    }

    public User getCurrentUser(Long userId) {
        return userMapper.selectById(userId);
    }
}
